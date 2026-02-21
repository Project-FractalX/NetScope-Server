package com.netscope.core;

import com.netscope.annotation.AuthType;
import com.netscope.annotation.NetworkPublic;
import com.netscope.annotation.NetworkSecured;
import com.netscope.config.NetScopeConfig;
import com.netscope.model.NetworkMethodDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NetScopeScanner {

    private static final Logger logger = LoggerFactory.getLogger(NetScopeScanner.class);

    private final ApplicationContext context;
    private final NetScopeConfig config;

    // Cache: "BeanName.memberName" → definition
    private final Map<String, NetworkMethodDefinition> cache = new ConcurrentHashMap<>();
    private volatile boolean scanned = false;

    public NetScopeScanner(ApplicationContext context, NetScopeConfig config) {
        this.context = context;
        this.config  = config;
    }

    public List<NetworkMethodDefinition> scan() {
        if (!scanned) {
            doScan();
        }
        return new ArrayList<>(cache.values());
    }

    public Optional<NetworkMethodDefinition> findMethod(String beanName, String memberName) {
        if (!scanned) doScan();
        return Optional.ofNullable(cache.get(beanName + "." + memberName));
    }

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

            // ── Scan METHODS ─────────────────────────────────────────────────
            for (Method method : clazz.getDeclaredMethods()) {
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
                    String key = def.getBeanName() + "." + def.getMethodName();
                    cache.put(key, def);
                    logger.info("  [{}] {}.{} → {} | auth={}",
                            def.isField() ? "field" : "method",
                            def.getBeanName(), def.getMethodName(),
                            def.isSecured() ? "SECURED" : "PUBLIC",
                            def.getAuthType());
                    count++;
                }
            }

            // ── Scan FIELDS ──────────────────────────────────────────────────
            for (Field field : clazz.getDeclaredFields()) {
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
                    String key = def.getBeanName() + "." + def.getMethodName();
                    cache.put(key, def);
                    logger.info("  [field] {}.{} → {} | auth={}",
                            def.getBeanName(), def.getMethodName(),
                            def.isSecured() ? "SECURED" : "PUBLIC",
                            def.getAuthType());
                    count++;
                }
            }
        }

        scanned = true;
        logger.info("NetScope: scan complete — {} member(s) registered", count);
    }

    /** Unwrap Spring proxies to get the real class */
    private Class<?> getTargetClass(Object bean) {
        try {
            Class<?> clazz = bean.getClass();
            // Handle CGLIB proxies
            if (clazz.getName().contains("$$")) {
                return clazz.getSuperclass();
            }
            return clazz;
        } catch (Exception e) {
            return bean.getClass();
        }
    }
}
