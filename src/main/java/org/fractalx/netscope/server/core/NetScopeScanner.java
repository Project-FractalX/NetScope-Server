package org.fractalx.netscope.server.core;

import org.fractalx.netscope.server.annotation.NetworkPublic;
import org.fractalx.netscope.server.annotation.NetworkSecured;
import org.fractalx.netscope.server.config.NetScopeConfig;
import org.fractalx.netscope.server.model.NetworkMethodDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class NetScopeScanner {

    private static final Logger logger = LoggerFactory.getLogger(NetScopeScanner.class);

    private final ApplicationContext context;
    private final NetScopeConfig config;

    // Canonical cache — methods: "BeanName.method(T1,T2)", fields: "BeanName.field"
    private final Map<String, NetworkMethodDefinition> cache = new ConcurrentHashMap<>();

    // Alias cache — same key format, keyed by interface name instead of concrete class name
    private final Map<String, NetworkMethodDefinition> aliasCache = new ConcurrentHashMap<>();

    // Method index — "BeanName.method" → all overloads (for lookup without parameter_types)
    private final Map<String, List<NetworkMethodDefinition>> methodIndex = new ConcurrentHashMap<>();

    // Alias method index — same as methodIndex but keyed by interface name
    private final Map<String, List<NetworkMethodDefinition>> aliasMethodIndex = new ConcurrentHashMap<>();

    private volatile boolean scanned = false;

    public NetScopeScanner(ApplicationContext context, NetScopeConfig config) {
        this.context = context;
        this.config  = config;
    }

    public List<NetworkMethodDefinition> scan() {
        if (!scanned) doScan();
        return new ArrayList<>(cache.values());
    }

    /**
     * Looks up a member by bean name, member name, and optional parameter types.
     *
     * - Fields: always found by "BeanName.fieldName" with no parameter types
     * - Methods with parameter_types: exact key "BeanName.method(T1,T2)"
     * - Methods without parameter_types: unambiguous index lookup;
     *   throws AmbiguousInvocationException if multiple overloads exist
     *
     * Both the concrete class name and any user-defined interface name are accepted
     * as beanName.
     */
    public Optional<NetworkMethodDefinition> findMethod(String beanName, String memberName,
                                                        List<String> parameterTypes) {
        if (!scanned) doScan();

        String baseKey = beanName + "." + memberName;

        // 1. Direct lookup — hits fields (no parens) and aliased fields
        NetworkMethodDefinition def = cache.get(baseKey);
        if (def == null) def = aliasCache.get(baseKey);
        if (def != null) return Optional.of(def);

        // 2. Exact method lookup when caller supplies parameter types
        if (parameterTypes != null && !parameterTypes.isEmpty()) {
            String exactKey = baseKey + "(" + String.join(",", parameterTypes) + ")";
            def = cache.get(exactKey);
            if (def == null) def = aliasCache.get(exactKey);
            return Optional.ofNullable(def);
        }

        // 3. Unambiguous index lookup — no parameter types provided
        List<NetworkMethodDefinition> candidates = methodIndex.getOrDefault(baseKey, List.of());
        if (candidates.isEmpty()) {
            candidates = aliasMethodIndex.getOrDefault(baseKey, List.of());
        }
        if (candidates.size() == 1) return Optional.of(candidates.get(0));
        if (candidates.size() > 1) {
            throw new AmbiguousInvocationException(beanName, memberName, candidates);
        }

        return Optional.empty();
    }

    // ── Scanning ──────────────────────────────────────────────────────────────

    private synchronized void doScan() {
        if (scanned) return;

        logger.info("NetScope: scanning for @NetworkPublic and @NetworkSecured members...");
        int count = 0;

        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = context.getBean(beanName);
            } catch (Exception e) {
                continue;
            }

            Class<?> clazz = getTargetClass(bean);

            // ── Scan METHODS (including inherited + interface) ────────────────
            for (Method method : getAllMethods(clazz)) {
                NetworkMethodDefinition def = null;

                NetworkPublic pub = method.getAnnotation(NetworkPublic.class);
                if (pub != null) {
                    def = new NetworkMethodDefinition(bean, method, false, null,
                            pub.description());
                }

                NetworkSecured sec = method.getAnnotation(NetworkSecured.class);
                if (sec != null) {
                    def = new NetworkMethodDefinition(bean, method, true, sec.auth(),
                            sec.description());
                }

                if (def != null) {
                    // Methods use parameterized key to support overloading
                    String key = methodKey(def.getBeanName(), def.getMethodName(), method);
                    if (cache.putIfAbsent(key, def) == null) {
                        // Also add to method index for overload-unaware lookups
                        String baseKey = def.getBeanName() + "." + def.getMethodName();
                        methodIndex.computeIfAbsent(baseKey, k -> new ArrayList<>()).add(def);
                        logger.info("  [method] {}.{}({}) → {} | auth={} | static={} | final={}",
                                def.getBeanName(), def.getMethodName(), paramSignature(method),
                                def.isSecured() ? "SECURED" : "PUBLIC",
                                def.getAuthType(), def.isStatic(), def.isFinal());
                        count++;
                    }
                }
            }

            // ── Scan FIELDS (including inherited) ────────────────────────────
            for (Field field : getAllFields(clazz)) {
                NetworkMethodDefinition def = null;

                NetworkPublic pub = field.getAnnotation(NetworkPublic.class);
                if (pub != null) {
                    field.setAccessible(true);
                    def = new NetworkMethodDefinition(bean, field, false, null,
                            pub.description());
                }

                NetworkSecured sec = field.getAnnotation(NetworkSecured.class);
                if (sec != null) {
                    field.setAccessible(true);
                    def = new NetworkMethodDefinition(bean, field, true, sec.auth(),
                            sec.description());
                }

                if (def != null) {
                    // Fields use plain key — they cannot be overloaded
                    String key = def.getBeanName() + "." + def.getMethodName();
                    if (cache.putIfAbsent(key, def) == null) {
                        logger.info("  [field]  {}.{} → {} | auth={} | static={} | final={} | writeable={}",
                                def.getBeanName(), def.getMethodName(),
                                def.isSecured() ? "SECURED" : "PUBLIC",
                                def.getAuthType(),
                                def.isStatic(), def.isFinal(), def.isWriteable());
                        count++;
                    }
                }
            }

            // ── Register interface aliases (lookup-only, not in GetDocs) ─────
            String concreteName = clazz.getSimpleName();
            for (Class<?> iface : collectInterfaces(clazz)) {
                if (!isUserInterface(iface)) continue;
                String ifaceName = iface.getSimpleName();
                if (ifaceName.equals(concreteName)) continue;

                int aliasCount = 0;
                for (Map.Entry<String, NetworkMethodDefinition> entry : cache.entrySet()) {
                    String cacheKey = entry.getKey();
                    if (!cacheKey.startsWith(concreteName + ".")) continue;

                    String memberSuffix = cacheKey.substring(concreteName.length() + 1);
                    String aliasKey = ifaceName + "." + memberSuffix;
                    NetworkMethodDefinition def = entry.getValue();

                    if (aliasCache.putIfAbsent(aliasKey, def) == null) {
                        aliasCount++;
                        if (!def.isField()) {
                            // Also index the alias by base name for overload-unaware lookups
                            String aliasBaseKey = ifaceName + "." + def.getMethodName();
                            aliasMethodIndex
                                    .computeIfAbsent(aliasBaseKey, k -> new ArrayList<>())
                                    .add(def);
                        }
                    }
                }
                if (aliasCount > 0) {
                    logger.info("  [alias]  {} → {} ({} member(s))", ifaceName, concreteName, aliasCount);
                }
            }
        }

        scanned = true;
        logger.info("NetScope: scan complete — {} member(s) registered", count);
    }

    // ── Key helpers ───────────────────────────────────────────────────────────

    /**
     * Full cache key for a method, including parameter type signature.
     * Example: "CustomerServiceImpl.process(String,int)"
     */
    private String methodKey(String beanName, String methodName, Method method) {
        return beanName + "." + methodName + "(" + paramSignature(method) + ")";
    }

    /** Comma-separated simple type names of method parameters. */
    private String paramSignature(Method method) {
        return Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
    }

    // ── Class hierarchy helpers ───────────────────────────────────────────────

    /**
     * Collects all methods from the class hierarchy then all reachable interfaces.
     * Subclass methods come first so putIfAbsent lets the most-specific declaration win.
     */
    private List<Method> getAllMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            methods.addAll(Arrays.asList(current.getDeclaredMethods()));
            current = current.getSuperclass();
        }
        for (Class<?> iface : collectInterfaces(clazz)) {
            methods.addAll(Arrays.asList(iface.getDeclaredMethods()));
        }
        return methods;
    }

    /**
     * Collects all fields from the class hierarchy.
     * Subclass fields come first so putIfAbsent lets the subclass shadow win.
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    /** All interfaces reachable from clazz, depth-first, deduplicated. */
    private Set<Class<?>> collectInterfaces(Class<?> clazz) {
        Set<Class<?>> visited = new LinkedHashSet<>();
        collectInterfaces(clazz, visited);
        return visited;
    }

    private void collectInterfaces(Class<?> clazz, Set<Class<?>> visited) {
        if (clazz == null || clazz == Object.class) return;
        for (Class<?> iface : clazz.getInterfaces()) {
            if (visited.add(iface)) {
                collectInterfaces(iface, visited);
            }
        }
        collectInterfaces(clazz.getSuperclass(), visited);
    }

    /**
     * Excludes java.*, javax.*, jakarta.*, org.springframework.*, and other
     * JVM/framework internals so they are never registered as aliases.
     */
    private boolean isUserInterface(Class<?> iface) {
        String pkg = iface.getPackageName();
        return !pkg.startsWith("java.")
            && !pkg.startsWith("javax.")
            && !pkg.startsWith("jakarta.")
            && !pkg.startsWith("org.springframework.")
            && !pkg.startsWith("com.sun.")
            && !pkg.startsWith("sun.");
    }

    /** Unwrap Spring proxies (both CGLIB and JDK dynamic) to get the real class. */
    private Class<?> getTargetClass(Object bean) {
        return AopUtils.getTargetClass(bean);
    }
}
