package com.netscope.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netscope.model.NetworkMethodDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class NetScopeInvoker {

    private static final Logger logger = LoggerFactory.getLogger(NetScopeInvoker.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Invokes a method or reads a field.
     * Returns JSON string of the result, or a status object for void methods.
     */
    public String invoke(NetworkMethodDefinition def, String argumentsJson) throws Exception {

        // ── FIELD: just read and return the value ─────────────────────────────
        if (def.isField()) {
            return readField(def);
        }

        // ── METHOD: invoke and return result ──────────────────────────────────
        return invokeMethod(def, argumentsJson);
    }

    private String readField(NetworkMethodDefinition def) throws Exception {
        Field field = def.getField();
        field.setAccessible(true);
        Object value = field.get(def.getBean());

        if (value == null) {
            return "null";
        }

        // Primitives and strings — return as JSON primitive
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return objectMapper.writeValueAsString(value);
        }

        // Complex objects — serialize as JSON object
        return objectMapper.writeValueAsString(value);
    }

    private String invokeMethod(NetworkMethodDefinition def, String argumentsJson) throws Exception {
        Method method = def.getMethod();
        method.setAccessible(true);

        Object[] args = resolveArguments(method, argumentsJson);
        Object result = method.invoke(def.getBean(), args);

        // ── void method → return accepted status ─────────────────────────────
        if (def.isVoidReturn()) {
            return "{\"status\":\"accepted\"}";
        }

        // ── null result ───────────────────────────────────────────────────────
        if (result == null) {
            return "null";
        }

        // ── primitives and strings — return as JSON primitive ─────────────────
        if (result instanceof String || result instanceof Number || result instanceof Boolean) {
            return objectMapper.writeValueAsString(result);
        }

        // ── complex object or list — serialize as JSON ────────────────────────
        return objectMapper.writeValueAsString(result);
    }

    private Object[] resolveArguments(Method method, String argumentsJson) throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();

        if (paramTypes.length == 0) {
            return new Object[0];
        }

        if (argumentsJson == null || argumentsJson.isBlank() || argumentsJson.equals("[]")) {
            if (paramTypes.length > 0) {
                throw new IllegalArgumentException(
                    "Method requires " + paramTypes.length + " argument(s) but none were provided");
            }
            return new Object[0];
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
