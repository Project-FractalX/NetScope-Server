package org.fractalx.netscope.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.fractalx.netscope.annotation.AuthType;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.List;

/**
 * Represents a method or field exposed via NetScope gRPC.
 */
public class NetworkMethodDefinition {

    public enum SourceType { METHOD, FIELD }

    @JsonIgnore private final Object bean;
    @JsonIgnore private final Method method;   // null if field
    @JsonIgnore private final Field field;     // null if method

    private final String beanName;
    private final String methodName;      // for fields: field name
    private final boolean secured;
    private final AuthType authType;      // OAUTH, API_KEY, or BOTH (null if public)
    private final boolean voidReturn;
    private final ParameterInfo[] parameters;
    private final String returnType;
    private final String description;
    private final SourceType sourceType;
    private final boolean isStatic;
    private final boolean isFinal;

    /** Constructor for METHOD */
    public NetworkMethodDefinition(Object bean, Method method,
                                   boolean secured, AuthType authType, String description) {
        this.bean        = bean;
        this.method      = method;
        this.field       = null;
        this.sourceType  = SourceType.METHOD;
        this.beanName    = AopUtils.getTargetClass(bean).getSimpleName();
        this.methodName  = method.getName();
        this.secured     = secured;
        this.authType    = authType;
        this.voidReturn  = method.getReturnType() == void.class
                        || method.getReturnType() == Void.class;
        this.returnType  = method.getReturnType().getSimpleName();
        this.description = description != null ? description : "";
        this.isStatic    = Modifier.isStatic(method.getModifiers());
        this.isFinal     = Modifier.isFinal(method.getModifiers());

        Parameter[] params = method.getParameters();
        this.parameters = new ParameterInfo[params.length];
        for (int i = 0; i < params.length; i++) {
            this.parameters[i] = new ParameterInfo(
                params[i].getName(), params[i].getType().getSimpleName(), i);
        }
    }

    /** Constructor for FIELD */
    public NetworkMethodDefinition(Object bean, Field field,
                                   boolean secured, AuthType authType, String description) {
        this.bean        = bean;
        this.method      = null;
        this.field       = field;
        this.sourceType  = SourceType.FIELD;
        this.beanName    = AopUtils.getTargetClass(bean).getSimpleName();
        this.methodName  = field.getName();
        this.secured     = secured;
        this.authType    = authType;
        this.voidReturn  = false;
        this.returnType  = field.getType().getSimpleName();
        this.description = description != null ? description : "";
        this.isStatic    = Modifier.isStatic(field.getModifiers());
        this.isFinal     = Modifier.isFinal(field.getModifiers());
        this.parameters  = new ParameterInfo[0];  // fields take no parameters
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Object getBean()              { return bean; }
    public Method getMethod()            { return method; }
    public Field getField()              { return field; }
    public String getBeanName()          { return beanName; }
    public String getMethodName()        { return methodName; }
    public boolean isSecured()           { return secured; }
    public AuthType getAuthType()        { return authType; }
    public boolean isVoidReturn()        { return voidReturn; }
    public ParameterInfo[] getParameters() { return parameters; }
    public String getReturnType()        { return returnType; }
    public String getDescription()       { return description; }
    public SourceType getSourceType()    { return sourceType; }
    public boolean isField()             { return sourceType == SourceType.FIELD; }
    public boolean isStatic()            { return isStatic; }
    public boolean isFinal()             { return isFinal; }

    /**
     * A field attribute is writeable when it is not declared final.
     * Methods are never writeable (invoke them instead).
     */
    public boolean isWriteable()         { return isField() && !isFinal; }

    /** Returns empty list — scopes removed, auth is controlled via AuthType */
    public List<String> getRequiredScopes() { return List.of(); }
    public boolean isRequireAllScopes()     { return false; }

    // ── Nested: ParameterInfo ─────────────────────────────────────────────────

    public static class ParameterInfo {
        private final String name;
        private final String type;
        private final int index;

        public ParameterInfo(String name, String type, int index) {
            this.name  = name;
            this.type  = type;
            this.index = index;
        }

        public String getName()  { return name; }
        public String getType()  { return type; }
        public int    getIndex() { return index; }
    }
}
