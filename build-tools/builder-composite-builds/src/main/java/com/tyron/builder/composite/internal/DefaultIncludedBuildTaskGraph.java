package com.tyron.builder.composite.internal;

import com.google.common.annotations.VisibleForTesting;
import com.tyron.builder.execution.plan.PlanExecutor;
import com.tyron.builder.api.internal.TaskInternal;
import com.tyron.builder.internal.concurrent.CompositeStoppable;
import com.tyron.builder.internal.concurrent.ExecutorFactory;
import com.tyron.builder.internal.concurrent.ManagedExecutor;
import com.tyron.builder.internal.operations.BuildOperationContext;
import com.tyron.builder.internal.operations.BuildOperationDescriptor;
import com.tyron.builder.internal.operations.BuildOperationExecutor;
import com.tyron.builder.internal.operations.RunnableBuildOperation;
import com.tyron.builder.internal.work.WorkerLeaseService;
import com.tyron.builder.internal.build.BuildLifecycleController;
import com.tyron.builder.internal.build.BuildState;
import com.tyron.builder.internal.build.BuildStateRegistry;
import com.tyron.builder.internal.build.ExecutionResult;
import com.tyron.builder.internal.build.ExportedTaskNode;
import com.tyron.builder.internal.buildtree.BuildTreeWorkGraph;
import com.tyron.builder.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.inject.Inject;


public class DefaultIncludedBuildTaskGraph implements BuildTreeWorkGraphController, Closeable {
    private enum State {
        NotPrepared, Preparing, ReadyToRun, Running, Finished
    }

    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildStateRegistry buildRegistry;
    private final WorkerLeaseService workerLeaseService;
    private final PlanExecutor planExecutor;
    private final int monitoringPollTime;
    private final TimeUnit monitoringPollTimeUnit;
    private final ManagedExecutor executorService;
    private final ThreadLocal<DefaultBuildTreeWorkGraph> current = new ThreadLocal<>();

    @Inject
    public DefaultIncludedBuildTaskGraph(
            ExecutorFactory executorFactory,
            BuildOperationExecutor buildOperationExecutor,
            BuildStateRegistry buildRegistry,
            WorkerLeaseService workerLeaseService,
            PlanExecutor planExecutor
    ) {
        this(executorFactory, buildOperationExecutor, buildRegistry, workerLeaseService, planExecutor, 30, TimeUnit.SECONDS);
    }

    @VisibleForTesting
    DefaultIncludedBuildTaskGraph(
            ExecutorFactory executorFactory,
            BuildOperationExecutor buildOperationExecutor,
            BuildStateRegistry buildRegistry,
            WorkerLeaseService workerLeaseService,
            PlanExecutor planExecutor,
            int monitoringPollTime,
            TimeUnit monitoringPollTimeUnit
    ) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.buildRegistry = buildRegistry;
        this.executorService = executorFactory.create("included builds");
        this.workerLeaseService = workerLeaseService;
        this.planExecutor = planExecutor;
        this.monitoringPollTime = monitoringPollTime;
        this.monitoringPollTimeUnit = monitoringPollTimeUnit;
    }

    private DefaultBuildControllers createControllers() {
        return new DefaultBuildControllers(executorService, workerLeaseService, planExecutor, monitoringPollTime, monitoringPollTimeUnit);
    }

    @Override
    public <T> T withNewWorkGraph(Function<? super BuildTreeWorkGraph, T> action) {
        DefaultBuildTreeWorkGraph previous = current.get();
        DefaultBuildTreeWorkGraph workGraph = new DefaultBuildTreeWorkGraph();
        current.set(workGraph);
        try {
            try {
                return action.apply(workGraph);
            } finally {
                workGraph.close();
            }
        } finally {
            current.set(previous);
        }
    }

    @Override
    public IncludedBuildTaskResource locateTask(TaskIdentifier taskIdentifier) {
        return withState(workGraph -> {
            BuildState build = buildRegistry.getBuild(taskIdentifier.getBuildIdentifier());
            ExportedTaskNode taskNode = build.getWorkGraph().locateTask(taskIdentifier);
            return new TaskBackedResource(workGraph, build, taskNode);
        });
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(executorService);
    }

    private <T> T withState(Function<DefaultBuildTreeWorkGraph, T> action) {
        DefaultBuildTreeWorkGraph workGraph = current.get();
        if (workGraph == null) {
            throw new IllegalStateException("No work graph available for this thread.");
        }
        workGraph.assertIsOwner();
        return action.apply(workGraph);
    }

    private class DefaultBuildTreeWorkGraphBuilder implements BuildTreeWorkGraph.Builder {
        private final DefaultBuildTreeWorkGraph owner;

        public DefaultBuildTreeWorkGraphBuilder(DefaultBuildTreeWorkGraph owner) {
            this.owner = owner;
        }

        @Override
        public void withWorkGraph(BuildState target, Consumer<? super BuildLifecycleController.WorkGraphBuilder> action) {
            owner.controllers.getBuildController(target).populateWorkGraph(action);
        }
    }

    private class DefaultBuildTreeWorkGraph implements BuildTreeWorkGraph, AutoCloseable {
        private final Thread owner;
        private final BuildControllers controllers;
        private State state = State.NotPrepared;

        public DefaultBuildTreeWorkGraph() {
            owner = Thread.currentThread();
            controllers = createControllers();
        }

        public void queueForExecution(BuildState build, ExportedTaskNode taskNode) {
            assertIsOwner();
            assertCanQueueTask();
            controllers.getBuildController(build).queueForExecution(taskNode);
        }

        @Override
        public void scheduleWork(Consumer<? super Builder> action) {
            assertIsOwner();
            expectInState(State.NotPrepared);
            state = State.Preparing;
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    action.accept(new DefaultBuildTreeWorkGraphBuilder(DefaultBuildTreeWorkGraph.this));
                    controllers.populateWorkGraphs();
                    context.setResult(new CalculateTreeTaskGraphBuildOperationType.Result() {
                    });
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Calculate build tree task graph")
                            .details(new CalculateTreeTaskGraphBuildOperationType.Details() {
                            });
                }
            });
            state = State.ReadyToRun;
        }

        @Override
        public ExecutionResult<Void> runWork() {
            assertIsOwner();
            expectInState(State.ReadyToRun);
            state = State.Running;
            try {
                return controllers.execute();
            } finally {
                state = State.Finished;
            }
        }

        @Override
        public void close() {
            assertIsOwner();
            controllers.close();
        }

        private void assertCanQueueTask() {
            expectInState(State.Preparing);
        }

        private void expectInState(State expectedState) {
            if (state != expectedState) {
                throw unexpectedState();
            }
        }

        private IllegalStateException unexpectedState() {
            return new IllegalStateException("Work graph is in an unexpected state: " + state);
        }

        private void assertIsOwner() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Current thread is not the owner of this work graph.");
            }
        }
    }

    private static class TaskBackedResource implements IncludedBuildTaskResource {
        private final DefaultBuildTreeWorkGraph workGraph;
        private final BuildState build;
        private final ExportedTaskNode taskNode;

        public TaskBackedResource(DefaultBuildTreeWorkGraph workGraph, BuildState build, ExportedTaskNode taskNode) {
            this.workGraph = workGraph;
            this.build = build;
            this.taskNode = taskNode;
        }

        @Override
        public void queueForExecution() {
            workGraph.queueForExecution(build, taskNode);
        }

        @Override
        public void onComplete(Runnable action) {
            taskNode.onComplete(action);
        }

        @Override
        public TaskInternal getTask() {
            return taskNode.getTask();
        }

        @Override
        public State getTaskState() {
            return taskNode.getTaskState();
        }
    }
}
