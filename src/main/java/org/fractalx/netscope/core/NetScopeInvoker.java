package org.fractalx.netscope.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fractalx.netscope.model.NetworkMethodDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class NetScopeInvoker {

    private static final Logger logger = LoggerFactory.getLogger(NetScopeInvoker.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Optional Reactor support — detected at class load time ───────────────
    private static final Class<?> MONO_CLASS;
    private static final Class<?> FLUX_CLASS;
    private static final Method   MONO_BLOCK;
    private static final Method   FLUX_COLLECT_LIST;

    static {
        Class<?> mono = null, flux = null;
        Method   monoBlock = null, fluxCollectList = null;
        try {
            mono            = Class.forName("reactor.core.publisher.Mono");
            flux            = Class.forName("reactor.core.publisher.Flux");
            monoBlock       = mono.getMethod("block");
            fluxCollectList = flux.getMethod("collectList");
            logger.debug("NetScope: Project Reactor detected — Mono/Flux return types will be unwrapped");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            logger.debug("NetScope: Project Reactor not on classpath — reactive unwrapping disabled");
        }
        MONO_CLASS        = mono;
        FLUX_CLASS        = flux;
        MONO_BLOCK        = monoBlock;
        FLUX_COLLECT_LIST = fluxCollectList;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Invokes a method or reads a field value.
     * Reactive return types (Mono, Flux, CompletableFuture) are automatically unwrapped.
     */
    public String invoke(NetworkMethodDefinition def, String argumentsJson) throws Exception {
        if (def.isField()) {
            return readField(def);
        }
        return invokeMethod(def, argumentsJson);
    }

    /**
     * Writes a value to an exposed field attribute.
     * Rejects writes to final fields. Returns JSON of the previous value.
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

        Object target   = def.isStatic() ? null : def.getBean();
        Object previous = field.get(target);
        String previousJson = previous == null ? "null" : objectMapper.writeValueAsString(previous);

        Object newValue = (valueJson == null || valueJson.equals("null"))
                ? null
                : objectMapper.readValue(valueJson, field.getType());

        field.set(target, newValue);
        logger.debug("NetScope: wrote {}.{} = {}", def.getBeanName(), def.getMethodName(), valueJson);
        return previousJson;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String readField(NetworkMethodDefinition def) throws Exception {
        Field field = def.getField();
        field.setAccessible(true);
        Object target = def.isStatic() ? null : def.getBean();
        Object value  = field.get(target);
        return value == null ? "null" : objectMapper.writeValueAsString(value);
    }

    private String invokeMethod(NetworkMethodDefinition def, String argumentsJson) throws Exception {
        Method method = def.getMethod();
        method.setAccessible(true);

        Object[] args   = resolveArguments(method, argumentsJson);
        Object   target = def.isStatic() ? null : def.getBean();
        Object   result = method.invoke(target, args);

        // Raw void return — no need to unwrap
        if (def.isVoidReturn()) {
            return "{\"status\":\"accepted\"}";
        }

        // Unwrap reactive types before serializing
        result = unwrapReactive(result);

        if (result == null) {
            return "null";
        }
        return objectMapper.writeValueAsString(result);
    }

    /**
     * Unwraps reactive/async return types to their resolved values:
     *   CompletableFuture<T> → T  (always available)
     *   Mono<T>              → T  (requires Project Reactor on classpath)
     *   Flux<T>              → List<T> (requires Project Reactor on classpath)
     *
     * Non-reactive values are returned as-is.
     */
    private Object unwrapReactive(Object result) throws Exception {
        if (result == null) return null;

        // CompletableFuture — standard Java, always available
        if (result instanceof CompletableFuture<?> future) {
            try {
                return future.get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                throw cause instanceof Exception ? (Exception) cause : new RuntimeException(cause);
            }
        }

        // Project Reactor — optional dependency
        if (MONO_CLASS != null && MONO_CLASS.isInstance(result)) {
            return MONO_BLOCK.invoke(result);   // Mono<T>.block() → T (or null for empty/void)
        }
        if (FLUX_CLASS != null && FLUX_CLASS.isInstance(result)) {
            Object monoList = FLUX_COLLECT_LIST.invoke(result);  // Flux<T>.collectList() → Mono<List<T>>
            return MONO_BLOCK.invoke(monoList);                  // .block() → List<T>
        }

        return result;
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
