package com.tyron.builder.api.internal.tasks;

import com.tyron.builder.api.Task;
import com.tyron.builder.execution.TaskSelectionException;
import com.tyron.builder.execution.plan.DefaultExecutionPlan;
import com.tyron.builder.execution.plan.ExecutionNodeAccessHierarchies;
import com.tyron.builder.execution.plan.TaskDependencyResolver;
import com.tyron.builder.execution.plan.TaskNodeFactory;
import com.tyron.builder.internal.execution.BuildOutputCleanupRegistry;
import com.tyron.builder.execution.taskgraph.TaskExecutionGraphInternal;
import com.tyron.builder.internal.operations.BuildOperationDescriptor;
import com.tyron.builder.internal.operations.BuildOperationExecutor;
import com.tyron.builder.api.internal.project.ProjectInternal;
import com.tyron.builder.internal.reflect.service.DefaultServiceRegistry;
import com.tyron.builder.internal.reflect.service.ServiceRegistry;
import com.tyron.builder.internal.resources.ResourceLockCoordinationService;
import com.tyron.builder.internal.work.WorkerLeaseService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TaskExecutor {
    private final ProjectInternal project;
    private final TaskNodeFactory taskNodeFactory;
    private final TaskDependencyResolver taskDependencyResolver;
    private final ExecutionNodeAccessHierarchies executionNodeAccessHierarchy;
    private final ResourceLockCoordinationService resourceLockService;
    private final WorkerLeaseService defaultWorkerLeaseService;

    private final List<Throwable> failures = new ArrayList<>();

    public TaskExecutor(ProjectInternal project) {
        this.project = project;
        ServiceRegistry services = project.getGradle().getServices();
        this.taskNodeFactory = services.get(TaskNodeFactory.class);
        this.taskDependencyResolver = services.get(TaskDependencyResolver.class);
        this.executionNodeAccessHierarchy = services.get(ExecutionNodeAccessHierarchies.class);
        this.resourceLockService = services.get(ResourceLockCoordinationService.class);
        this.defaultWorkerLeaseService = services.get(WorkerLeaseService.class);
    }

    public void execute(String... paths) {
        Task[] tasks = new Task[paths.length];
        for (int i = 0; i < tasks.length; i++) {
            Task task = project.getTasks().resolveTask(paths[i]);

            if (task == null) {
                throw new TaskSelectionException("Task '" + paths[i] + "' not found in project '" + project.getPath() + "'");
            }
            tasks[i] = task;
        }
        execute(tasks);
    }

    public void execute(Task... tasks) {
        failures.clear();

        DefaultExecutionPlan executionPlan = new DefaultExecutionPlan(
                "myPlan",
                taskNodeFactory,
                taskDependencyResolver,
                executionNodeAccessHierarchy.getOutputHierarchy(),
                executionNodeAccessHierarchy.getDestroyableHierarchy(),
                resourceLockService
        );
        executionPlan.addEntryTasks(Arrays.asList(tasks));
        executionPlan.determineExecutionPlan();

        TaskExecutionGraphInternal taskGraph = project.getGradle().getTaskGraph();
        taskGraph.populate(executionPlan);

        BuildOutputCleanupRegistry buildOutputCleanupRegistry = project.getServices().get(BuildOutputCleanupRegistry.class);
        buildOutputCleanupRegistry.resolveOutputs();

        DefaultServiceRegistry services = (DefaultServiceRegistry) project.getServices();

        resourceLockService.withStateLock(() -> {

            BuildOperationExecutor buildOperationExecutor =
                    project.getServices().get(BuildOperationExecutor.class);
            BuildOperationDescriptor.Builder builder =
                    BuildOperationDescriptor.displayName("Running tasks " + Arrays.toString(tasks));
            buildOperationExecutor.start(builder);

            taskGraph.execute(executionPlan, failures);
        });
    }

    public List<Throwable> getFailures() {
        return failures;
    }
}
