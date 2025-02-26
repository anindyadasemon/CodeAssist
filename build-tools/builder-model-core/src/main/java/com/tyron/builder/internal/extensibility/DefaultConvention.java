package com.tyron.builder.internal.extensibility;

import static com.tyron.builder.api.reflect.TypeOf.typeOf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

import com.google.common.collect.Maps;
import com.tyron.builder.api.Action;
import com.tyron.builder.api.internal.plugins.DslObject;
import com.tyron.builder.internal.Describables;
import com.tyron.builder.internal.instantiation.InstanceGenerator;
import com.tyron.builder.api.internal.plugins.ExtensionContainerInternal;
import com.tyron.builder.api.plugins.Convention;
import com.tyron.builder.api.plugins.ExtensionsSchema;
import com.tyron.builder.api.plugins.ExtraPropertiesExtension;
import com.tyron.builder.api.reflect.TypeOf;
import com.tyron.builder.internal.metaobject.AbstractDynamicObject;
import com.tyron.builder.internal.metaobject.BeanDynamicObject;
import com.tyron.builder.internal.metaobject.DynamicInvokeResult;
import com.tyron.builder.internal.metaobject.DynamicObject;
import com.tyron.builder.util.ConfigureUtil;

import groovy.lang.Closure;

public class DefaultConvention implements Convention, ExtensionContainerInternal {
    private static final TypeOf<ExtraPropertiesExtension> EXTRA_PROPERTIES_EXTENSION_TYPE = typeOf(ExtraPropertiesExtension.class);
    private final DefaultConvention.ExtensionsDynamicObject extensionsDynamicObject = new ExtensionsDynamicObject();
    private final ExtensionsStorage extensionsStorage = new ExtensionsStorage();
    private final ExtraPropertiesExtension extraProperties = new DefaultExtraPropertiesExtension();
    private final InstanceGenerator instanceGenerator;

    private Map<String, Object> plugins;
    private Map<Object, BeanDynamicObject> dynamicObjects;

    public DefaultConvention(InstanceGenerator instanceGenerator) {
        this.instanceGenerator = instanceGenerator;
        add(EXTRA_PROPERTIES_EXTENSION_TYPE, ExtraPropertiesExtension.EXTENSION_NAME, extraProperties);
    }

    @Override
    public Map<String, Object> getPlugins() {
        if (plugins == null) {
            plugins = Maps.newLinkedHashMap();
        }
        return plugins;
    }

    @Override
    public DynamicObject getExtensionsAsDynamicObject() {
        return extensionsDynamicObject;
    }

    @Override
    public <T> T getPlugin(Class<T> type) {
        T value = findPlugin(type);
        if (value == null) {
            throw new IllegalStateException(
                    format("Could not find any convention object of type %s.", type.getSimpleName()));
        }
        return value;
    }

    @Override
    public <T> T findPlugin(Class<T> type) throws IllegalStateException {
        if (plugins == null) {
            return null;
        }
        List<T> values = new ArrayList<T>();
        for (Object object : plugins.values()) {
            if (type.isInstance(object)) {
                values.add(type.cast(object));
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() > 1) {
            throw new IllegalStateException(
                    format("Found multiple convention objects of type %s.", type.getSimpleName()));
        }
        return values.get(0);
    }

    @Override
    public void add(String name, Object extension) {
        if (extension instanceof Class) {
            create(name, (Class<?>) extension);
        } else {
//            addWithDefaultPublicType(name, extension);
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public <T> void add(Class<T> publicType, String name, T extension) {
        add(typeOf(publicType), name, extension);
    }

    @Override
    public <T> void add(TypeOf<T> publicType, String name, T extension) {
        extensionsStorage.add(publicType, name, extension);
    }

    @Override
    public <T> T create(String name, Class<T> instanceType, Object... constructionArguments) {
        T instance = instantiate(instanceType, name, constructionArguments);
        addWithDefaultPublicType(name, instance);
        return instance;
    }

    @Override
    public <T> T create(Class<T> publicType, String name, Class<? extends T> instanceType, Object... constructionArguments) {
        return create(typeOf(publicType), name, instanceType, constructionArguments);
    }

    @Override
    public <T> T create(TypeOf<T> publicType, String name, Class<? extends T> instanceType, Object... constructionArguments) {
        T instance = instantiate(instanceType, name, constructionArguments);
        add(publicType, name, instance);
        return instance;
    }

    @Override
    public ExtraPropertiesExtension getExtraProperties() {
        return extraProperties;
    }

    @Override
    public ExtensionsSchema getExtensionsSchema() {
        return extensionsStorage.getSchema();
    }

    @Override
    public <T> T getByType(Class<T> type) {
        return getByType(typeOf(type));
    }

    @Override
    public <T> T getByType(TypeOf<T> type) {
        return extensionsStorage.getByType(type);
    }

    @Override
    public <T> T findByType(Class<T> type) {
        return findByType(typeOf(type));
    }

    @Override
    public <T> T findByType(TypeOf<T> type) {
        return extensionsStorage.findByType(type);
    }

    @Override
    public Object getByName(String name) {
        return extensionsStorage.getByName(name);
    }

    @Override
    public Object findByName(String name) {
        return extensionsStorage.findByName(name);
    }

    @Override
    public <T> void configure(Class<T> type, Action<? super T> action) {
        configure(typeOf(type), action);
    }

    @Override
    public <T> void configure(TypeOf<T> type, Action<? super T> action) {
        extensionsStorage.configureExtension(type, action);
    }

    @Override
    public <T> void configure(String name, Action<? super T> action) {
        extensionsStorage.configureExtension(name, action);
    }

    @Override
    public Map<String, Object> getAsMap() {
        return extensionsStorage.getAsMap();
    }

    public Object propertyMissing(String name) {
        return getByName(name);
    }

    public void propertyMissing(String name, Object value) {
        checkExtensionIsNotReassigned(name);
        add(name, value);
    }

    private void addWithDefaultPublicType(String name, Object extension) {
        add (TypeOf.<Object>typeOf(extension.getClass()), name, extension);
        add(new DslObject(extension).getPublicType(), name, extension);
    }

    private <T> T instantiate(Class<? extends T> instanceType, String name, Object[] constructionArguments) {
        return instanceGenerator.newInstanceWithDisplayName(instanceType, Describables.withTypeAndName("extension", name), constructionArguments);
    }

    private class ExtensionsDynamicObject extends AbstractDynamicObject {
        @Override
        public String getDisplayName() {
            return "extensions";
        }

        @Override
        public boolean hasProperty(String name) {
            if (extensionsStorage.hasExtension(name)) {
                return true;
            }
            if (plugins == null) {
                return false;
            }
            for (Object object : plugins.values()) {
                if (asDynamicObject(object).hasProperty(name)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Map<String, Object> getProperties() {
            Map<String, Object> properties = new HashMap<String, Object>();
            if (plugins != null) {
                List<Object> reverseOrder = new ArrayList<>(plugins.values());
                Collections.reverse(reverseOrder);
                for (Object object : reverseOrder) {
                    properties.putAll(asDynamicObject(object).getProperties());
                }
            }
            properties.putAll(extensionsStorage.getAsMap());
            return properties;
        }

        @Override
        public DynamicInvokeResult tryGetProperty(String name) {
            Object extension = extensionsStorage.findByName(name);
            if (extension != null) {
                return DynamicInvokeResult.found(extension);
            }
            if (plugins == null) {
                return DynamicInvokeResult.notFound();
            }
            for (Object object : plugins.values()) {
                DynamicObject dynamicObject = asDynamicObject(object).withNotImplementsMissing();
                DynamicInvokeResult result = dynamicObject.tryGetProperty(name);
                if (result.isFound()) {
                    return result;
                }
            }
            return DynamicInvokeResult.notFound();
        }

        public Object propertyMissing(String name) {
            return getProperty(name);
        }

        @Override
        public DynamicInvokeResult trySetProperty(String name, Object value) {
            checkExtensionIsNotReassigned(name);
            if (plugins == null) {
                return DynamicInvokeResult.notFound();
            }
            for (Object object : plugins.values()) {
                BeanDynamicObject dynamicObject = asDynamicObject(object).withNotImplementsMissing();
                DynamicInvokeResult result = dynamicObject.trySetProperty(name, value);
                if (result.isFound()) {
                    return result;
                }
            }
            return DynamicInvokeResult.notFound();
        }

        public void propertyMissing(String name, Object value) {
            setProperty(name, value);
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, Object... args) {
            if (isConfigureExtensionMethod(name, args)) {
                return DynamicInvokeResult.found(configureExtension(name, args));
            }
            if (plugins == null) {
                return DynamicInvokeResult.notFound();
            }
            for (Object object : plugins.values()) {
                BeanDynamicObject dynamicObject = asDynamicObject(object).withNotImplementsMissing();
                DynamicInvokeResult result = dynamicObject.tryInvokeMethod(name, args);
                if (result.isFound()) {
                    return result;
                }
            }
            return DynamicInvokeResult.notFound();
        }

        public Object methodMissing(String name, Object args) {
            return invokeMethod(name, (Object[]) args);
        }

        @Override
        public boolean hasMethod(String name, Object... args) {
            if (isConfigureExtensionMethod(name, args)) {
                return true;
            }
            if (plugins == null) {
                return false;
            }
            for (Object object : plugins.values()) {
                BeanDynamicObject dynamicObject = asDynamicObject(object);
                if (dynamicObject.hasMethod(name, args)) {
                    return true;
                }
            }
            return false;
        }

        private BeanDynamicObject asDynamicObject(Object object) {
            if (dynamicObjects == null) {
                dynamicObjects = Maps.newIdentityHashMap();
            }
            BeanDynamicObject dynamicObject = dynamicObjects.get(object);
            if (dynamicObject == null) {
                dynamicObject = new BeanDynamicObject(object);
                dynamicObjects.put(object, dynamicObject);
            }
            return dynamicObject;
        }
    }

    private void checkExtensionIsNotReassigned(String name) {
        if (extensionsStorage.hasExtension(name)) {
            throw new IllegalArgumentException(
                    format("There's an extension registered with name '%s'. You should not reassign it via a property setter.", name));
        }
    }

    private boolean isConfigureExtensionMethod(String name, Object[] args) {
        return args.length == 1 && args[0] instanceof Closure && extensionsStorage.hasExtension(name);
    }

    private Object configureExtension(String name, Object[] args) {
        Closure closure = (Closure) args[0];
        Action<Object> action = ConfigureUtil.configureUsing(closure);
        return extensionsStorage.configureExtension(name, action);
    }
}
