package com.netscope.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.lang.reflect.Method;

public class NetworkMethodDefinition {

    @JsonIgnore
    private final Object bean;

    @JsonIgnore
    private final Method method;

    private final String beanName;
    private final String methodName;
    private final String path;
    private final String httpMethod;
    private final boolean restricted;
    private final String apiKey;

    public NetworkMethodDefinition(Object bean, Method method, String path, boolean restricted) {
        this.beanName = bean.getClass().getSimpleName();
        this.methodName = method.getName();
        this.path = path;
        this.restricted = restricted;

        // Store HTTP method
        if (method.isAnnotationPresent(com.netscope.annotation.NetworkRestricted.class)) {
            this.httpMethod = method.getAnnotation(com.netscope.annotation.NetworkRestricted.class).method().name();
            this.apiKey = method.getAnnotation(com.netscope.annotation.NetworkRestricted.class).key();
        } else if (method.isAnnotationPresent(com.netscope.annotation.NetworkPublic.class)) {
            this.httpMethod = method.getAnnotation(com.netscope.annotation.NetworkPublic.class).method().name();
            this.apiKey = "";
        } else {
            this.httpMethod = "GET";
            this.apiKey = "";
        }

        this.bean = bean;
        this.method = method;
    }

    // getters (no bean/method)
    public String getBeanName() { return beanName; }
    public String getMethodName() { return methodName; }
    public String getPath() { return path; }
    public String getHttpMethod() { return httpMethod; }
    public boolean isRestricted() { return restricted; }
    public String getApiKey() { return apiKey; }

    // Optional: expose the Method and Bean for internal use, but not for serialization
    @JsonIgnore
    public Object getBean() { return bean; }

    @JsonIgnore
    public Method getMethod() { return method; }
}
