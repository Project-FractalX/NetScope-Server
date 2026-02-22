package com.netscope.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netscope.model.NetworkMethodDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public class NetScopeInvoker {

    private static final Logger logger = LoggerFactory.getLogger(NetScopeInvoker.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Invokes a method or reads a field value.
     * Returns JSON string of the result, or a status object for void methods.
     */
    public String invoke(NetworkMethodDefinition def, String argumentsJson) throws Exception {
        if (def.isField()) {
            return readField(def);
        }
        return invokeMethod(def, argumentsJson);
    }

    /**
     * Writes a value to an exposed field attribute.
     * Rejects writes to final fields.
     * Returns JSON of the previous value.
     */
    public String write(NetworkMethodDefinition def, String valueJson) throws Exception {
        if (!def.isField()) {
            throw new UnsupportedOperationException(
                "write() is only supported for field attributes, not methods");
        }
        if (def.isFinal()) {
            throw new IllegalStateException(
                "Cannot write to final field: " + def.getBeanName() + "." + def.getMethodName());
        }

        Field field = def.getField();
        field.setAccessible(true);

        // Capture previous value before overwriting
        Object target = def.isStatic() ? null : def.getBean();
        Object previous = field.get(target);
        String previousJson = previous == null ? "null" : objectMapper.writeValueAsString(previous);

        // Deserialize the new value to the field's declared type
        Object newValue;
        if (valueJson == null || valueJson.equals("null")) {
            newValue = null;
        } else {
            newValue = objectMapper.readValue(valueJson, field.getType());
        }

        field.set(target, newValue);
        logger.debug("NetScope: wrote {}.{} = {}", def.getBeanName(), def.getMethodName(), valueJson);

        return previousJson;
    }

    private String readField(NetworkMethodDefinition def) throws Exception {
        Field field = def.getField();
        field.setAccessible(true);

        // Static fields: pass null as the instance
        Object target = def.isStatic() ? null : def.getBean();
        Object value  = field.get(target);

        if (value == null) {
            return "null";
        }
        return objectMapper.writeValueAsString(value);
    }

    private String invokeMethod(NetworkMethodDefinition def, String argumentsJson) throws Exception {
        Method method = def.getMethod();
        method.setAccessible(true);

        Object[] args = resolveArguments(method, argumentsJson);

        // Static methods: pass null as the instance
        Object target = def.isStatic() ? null : def.getBean();
        Object result = method.invoke(target, args);

        if (def.isVoidReturn()) {
            return "{\"status\":\"accepted\"}";
        }
        if (result == null) {
            return "null";
        }
        return objectMapper.writeValueAsString(result);
    }

    private Object[] resolveArguments(Method method, String argumentsJson) throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();

        if (paramTypes.length == 0) {
            return new Object[0];
        }

        if (argumentsJson == null || argumentsJson.isBlank() || argumentsJson.equals("[]")) {
            throw new IllegalArgumentException(
                "Method requires " + paramTypes.length + " argument(s) but none were provided");
        }

        List<Object> rawArgs = objectMapper.readValue(argumentsJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Object.class));

        if (rawArgs.size() != paramTypes.length) {
            throw new IllegalArgumentException(
                "Expected " + paramTypes.length + " argument(s) but got " + rawArgs.size());
        }

        Object[] typedArgs = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            typedArgs[i] = objectMapper.convertValue(rawArgs.get(i), paramTypes[i]);
        }

        return typedArgs;
    }
}
