package com.tyron.builder.util;

import static com.tyron.builder.util.internal.CollectionUtils.toStringList;

import com.tyron.builder.api.Action;
import com.tyron.builder.api.BuildProject;
import com.tyron.builder.internal.Actions;
import com.tyron.builder.internal.metaobject.ConfigureDelegate;
import com.tyron.builder.internal.metaobject.DynamicInvokeResult;
import com.tyron.builder.internal.metaobject.DynamicObject;
import com.tyron.builder.internal.metaobject.DynamicObjectUtil;
import com.tyron.builder.util.internal.ClosureBackedAction;

import org.codehaus.groovy.runtime.GeneratedClosure;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

import groovy.lang.Closure;

/**
 * Contains utility methods to configure objects with Groovy Closures.
 * <p>
 * Plugins should avoid using this class and methods that use {@link groovy.lang.Closure} as this makes the plugin harder to use in other languages. Instead, plugins should create methods that use {@link Action}.
 * Here's an example pseudocode:
 * <pre class='autoTested'>
 *     interface MyOptions {
 *         RegularFileProperty getOptionsFile()
 *     }
 *     abstract class MyExtension {
 *         private final MyOptions options
 *
 *         {@literal @}Inject abstract ObjectFactory getObjectFactory()
 *
 *         public MyExtension() {
 *             this.options = getObjectFactory().newInstance(MyOptions)
 *         }
 *
 *         public void options(Action{@literal <?} extends MyOptions{@literal >}  action) {
 *              action.execute(options)
 *         }
 *     }
 *     extensions.create("myExtension", MyExtension)
 *     myExtension {
 *         options {
 *             optionsFile = layout.projectDirectory.file("options.properties")
 *         }
 *     }
 * </pre>
 * <p>
 * Gradle automatically generates a Closure-taking method at runtime for each method with an {@link Action} as a single argument as long as the object is created with {@link org.gradle.api.model.ObjectFactory#newInstance(Class, Object...)}.
 * <p>
 * As a last resort, to apply some configuration represented by a Groovy Closure, a plugin can use {@link BuildProject#configure(Object, Closure)}.
 *
 * @deprecated Will be removed in Gradle 8.0.
 */
@Deprecated
public class ConfigureUtil {

    public static <T> T configureByMap(Map<?, ?> properties, T delegate) {
        if (properties.isEmpty()) {
            return delegate;
        }
        DynamicObject dynamicObject = DynamicObjectUtil.asDynamicObject(delegate);

        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            String name = entry.getKey().toString();
            Object value = entry.getValue();

            DynamicInvokeResult result = dynamicObject.trySetProperty(name, value);
            if (result.isFound()) {
                continue;
            }

            result = dynamicObject.tryInvokeMethod(name, value);
            if (!result.isFound()) {
                throw dynamicObject.setMissingProperty(name);
            }
        }

        return delegate;
    }

    public static <T> T configureByMap(Map<?, ?> properties, T delegate, Collection<?> mandatoryKeys) {
        if (!mandatoryKeys.isEmpty()) {
            Collection<String> missingKeys = toStringList(mandatoryKeys);
            missingKeys.removeAll(toStringList(properties.keySet()));
            if (!missingKeys.isEmpty()) {
                throw new IncompleteInputException("Input configuration map does not contain following mandatory keys: " + missingKeys, missingKeys);
            }
        }
        return configureByMap(properties, delegate);
    }

    /**
     * Incomplete input exception.
     */
    @Deprecated
    public static class IncompleteInputException extends RuntimeException {
        private final Collection missingKeys;

        public IncompleteInputException(String message, Collection missingKeys) {
            super(message);
            this.missingKeys = missingKeys;
        }

        public Collection getMissingKeys() {
            return missingKeys;
        }
    }

    /**
     * <p>Configures {@code target} with {@code configureClosure}, via the {@link Configurable} interface if necessary.</p>
     *
     * <p>If {@code target} does not implement {@link Configurable} interface, it is set as the delegate of a clone of
     * {@code configureClosure} with a resolve strategy of {@code DELEGATE_FIRST}.</p>
     *
     * <p>If {@code target} does implement the {@link Configurable} interface, the {@code configureClosure} will be passed to
     * {@code delegate}'s {@link Configurable#configure(Closure)} method.</p>
     *
     * @param configureClosure The configuration closure
     * @param target The object to be configured
     * @return The delegate param
     */
    public static <T> T configure(@Nullable Closure configureClosure, T target) {
        if (configureClosure == null) {
            return target;
        }

        if (target instanceof Configurable) {
            ((Configurable) target).configure(configureClosure);
        } else {
            configureTarget(configureClosure, target, new ConfigureDelegate(configureClosure, target));
        }

        return target;
    }

    /**
     * Creates an action that uses the given closure to configure objects of type T.
     */
    public static <T> Action<T> configureUsing(@Nullable final Closure configureClosure) {
        if (configureClosure == null) {
            return Actions.doNothing();
        }

        return new WrappedConfigureAction<T>(configureClosure);
    }

    /**
     * Called from an object's {@link Configurable#configure} method.
     */
    public static <T> T configureSelf(@Nullable Closure configureClosure, T target) {
        if (configureClosure == null) {
            return target;
        }

        configureTarget(configureClosure, target, new ConfigureDelegate(configureClosure, target));
        return target;
    }

    /**
     * Called from an object's {@link Configurable#configure} method.
     */
    public static <T> T configureSelf(@Nullable Closure configureClosure, T target, ConfigureDelegate closureDelegate) {
        if (configureClosure == null) {
            return target;
        }

        configureTarget(configureClosure, target, closureDelegate);
        return target;
    }

    private static <T> void configureTarget(Closure configureClosure, T target, ConfigureDelegate closureDelegate) {
        if (!(configureClosure instanceof GeneratedClosure)) {
            new ClosureBackedAction<T>(configureClosure, Closure.DELEGATE_FIRST, false).execute(target);
            return;
        }

        // Hackery to make closure execution faster, by short-circuiting the expensive property and method lookup on Closure
        Closure withNewOwner = configureClosure.rehydrate(target, closureDelegate, configureClosure.getThisObject());
        new ClosureBackedAction<T>(withNewOwner, Closure.OWNER_ONLY, false).execute(target);
    }

    /**
     * Wrapper configure action.
     *
     * @param <T> the action type.
     */
    @Deprecated
    public static class WrappedConfigureAction<T> implements Action<T> {
        private final Closure configureClosure;

        WrappedConfigureAction(Closure configureClosure) {
            this.configureClosure = configureClosure;
        }

        @Override
        public void execute(T t) {
            configure(configureClosure, t);
        }

        public Closure getConfigureClosure() {
            return configureClosure;
        }
    }
}
