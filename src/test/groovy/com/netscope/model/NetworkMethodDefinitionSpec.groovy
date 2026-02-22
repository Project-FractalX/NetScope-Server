package com.netscope.model

import com.netscope.annotation.AuthType
import spock.lang.Specification

import java.lang.reflect.Field
import java.lang.reflect.Method

class NetworkMethodDefinitionSpec extends Specification {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    static class TestBean {
        String mutableField = "hello"
        final String finalField = "immutable"
        static String staticField = "static-val"

        String greet() { "hi" }
        void voidMethod() {}
        Void voidClassMethod() { null }
        static String staticMethod() { "static" }
        final String finalMethod() { "final" }
        String withParams(String name, int count) { name }
    }

    def bean = new TestBean()

    // ── METHOD constructor ────────────────────────────────────────────────────

    def "method constructor: beanName from target class simple name"() {
        given:
        Method m = TestBean.getDeclaredMethod("greet")
        when:
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        then:
        d.getBeanName() == "TestBean"
    }

    def "method constructor: methodName from method name"() {
        given:
        Method m = TestBean.getDeclaredMethod("greet")
        when:
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        then:
        d.getMethodName() == "greet"
    }

    def "method constructor: secured flag propagated"() {
        given:
        Method m = TestBean.getDeclaredMethod("greet")
        when:
        def pub = new NetworkMethodDefinition(bean, m, false, null, "")
        def sec = new NetworkMethodDefinition(bean, m, true, AuthType.OAUTH, "")
        then:
        !pub.isSecured()
        sec.isSecured()
    }

    def "method constructor: authType propagated"() {
        given:
        Method m = TestBean.getDeclaredMethod("greet")
        when:
        def d = new NetworkMethodDefinition(bean, m, true, AuthType.API_KEY, "")
        then:
        d.getAuthType() == AuthType.API_KEY
    }

    def "method constructor: returns and exposes bean and method"() {
        given:
        Method m = TestBean.getDeclaredMethod("greet")
        when:
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        then:
        d.getBean() is bean
        d.getMethod() is m
        d.getField() == null
    }

    def "method constructor: sourceType is METHOD, isField is false"() {
        given:
        Method m = TestBean.getDeclaredMethod("greet")
        when:
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        then:
        d.getSourceType() == NetworkMethodDefinition.SourceType.METHOD
        !d.isField()
    }

    def "method constructor: voidReturn true for void return type"() {
        given:
        Method m = TestBean.getDeclaredMethod("voidMethod")
        when:
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        then:
        d.isVoidReturn()
        d.getReturnType() == "void"
    }

    def "method constructor: voidReturn true for Void class return type"() {
        given:
        Method m = TestBean.getDeclaredMethod("voidClassMethod")
        when:
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        then:
        d.isVoidReturn()
        d.getReturnType() == "Void"
    }

    def "method constructor: voidReturn false for non-void return"() {
        given:
        Method m = TestBean.getDeclaredMethod("greet")
        when:
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        then:
        !d.isVoidReturn()
        d.getReturnType() == "String"
    }

    def "method constructor: detects static method"() {
        given:
        Method m = TestBean.getDeclaredMethod("staticMethod")
        when:
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        then:
        d.isStatic()
    }

    def "method constructor: detects final method"() {
        given:
        Method m = TestBean.getDeclaredMethod("finalMethod")
        when:
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        then:
        d.isFinal()
    }

    def "method constructor: non-static non-final defaults"() {
        given:
        Method m = TestBean.getDeclaredMethod("greet")
        when:
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        then:
        !d.isStatic()
        !d.isFinal()
    }

    def "method constructor: parameter info captured correctly"() {
        given:
        Method m = TestBean.getDeclaredMethod("withParams", String, int)
        when:
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        then:
        d.getParameters().length == 2
        d.getParameters()[0].getType() == "String"
        d.getParameters()[0].getIndex() == 0
        d.getParameters()[1].getType() == "int"
        d.getParameters()[1].getIndex() == 1
    }

    def "method constructor: null description defaults to empty string"() {
        given:
        Method m = TestBean.getDeclaredMethod("greet")
        when:
        def d = new NetworkMethodDefinition(bean, m, false, null, null)
        then:
        d.getDescription() == ""
    }

    def "method constructor: non-null description preserved"() {
        given:
        Method m = TestBean.getDeclaredMethod("greet")
        when:
        def d = new NetworkMethodDefinition(bean, m, false, null, "my description")
        then:
        d.getDescription() == "my description"
    }

    // ── FIELD constructor ─────────────────────────────────────────────────────

    def "field constructor: beanName from target class simple name"() {
        given:
        Field f = TestBean.getDeclaredField("mutableField")
        when:
        def d = new NetworkMethodDefinition(bean, f, false, null, "")
        then:
        d.getBeanName() == "TestBean"
    }

    def "field constructor: methodName is the field name"() {
        given:
        Field f = TestBean.getDeclaredField("mutableField")
        when:
        def d = new NetworkMethodDefinition(bean, f, false, null, "")
        then:
        d.getMethodName() == "mutableField"
    }

    def "field constructor: sourceType is FIELD, isField is true"() {
        given:
        Field f = TestBean.getDeclaredField("mutableField")
        when:
        def d = new NetworkMethodDefinition(bean, f, false, null, "")
        then:
        d.getSourceType() == NetworkMethodDefinition.SourceType.FIELD
        d.isField()
    }

    def "field constructor: returns and exposes bean and field"() {
        given:
        Field f = TestBean.getDeclaredField("mutableField")
        when:
        def d = new NetworkMethodDefinition(bean, f, false, null, "")
        then:
        d.getBean() is bean
        d.getField() is f
        d.getMethod() == null
    }

    def "field constructor: voidReturn is always false"() {
        given:
        Field f = TestBean.getDeclaredField("mutableField")
        when:
        def d = new NetworkMethodDefinition(bean, f, false, null, "")
        then:
        !d.isVoidReturn()
    }

    def "field constructor: returnType is field type simple name"() {
        given:
        Field f = TestBean.getDeclaredField("mutableField")
        when:
        def d = new NetworkMethodDefinition(bean, f, false, null, "")
        then:
        d.getReturnType() == "String"
    }

    def "field constructor: parameters array is empty"() {
        given:
        Field f = TestBean.getDeclaredField("mutableField")
        when:
        def d = new NetworkMethodDefinition(bean, f, false, null, "")
        then:
        d.getParameters().length == 0
    }

    def "field constructor: detects final field"() {
        given:
        Field f = TestBean.getDeclaredField("finalField")
        when:
        def d = new NetworkMethodDefinition(bean, f, false, null, "")
        then:
        d.isFinal()
    }

    def "field constructor: detects static field"() {
        given:
        Field f = TestBean.getDeclaredField("staticField")
        when:
        def d = new NetworkMethodDefinition(bean, f, false, null, "")
        then:
        d.isStatic()
    }

    def "field constructor: mutable non-static field is not final and not static"() {
        given:
        Field f = TestBean.getDeclaredField("mutableField")
        when:
        def d = new NetworkMethodDefinition(bean, f, false, null, "")
        then:
        !d.isFinal()
        !d.isStatic()
    }

    def "field constructor: secured and authType propagated"() {
        given:
        Field f = TestBean.getDeclaredField("mutableField")
        when:
        def d = new NetworkMethodDefinition(bean, f, true, AuthType.BOTH, "desc")
        then:
        d.isSecured()
        d.getAuthType() == AuthType.BOTH
        d.getDescription() == "desc"
    }

    def "field constructor: null description defaults to empty string"() {
        given:
        Field f = TestBean.getDeclaredField("mutableField")
        when:
        def d = new NetworkMethodDefinition(bean, f, false, null, null)
        then:
        d.getDescription() == ""
    }

    // ── isWriteable ───────────────────────────────────────────────────────────

    def "isWriteable: true for non-final field"() {
        given:
        Field f = TestBean.getDeclaredField("mutableField")
        when:
        def d = new NetworkMethodDefinition(bean, f, false, null, "")
        then:
        d.isWriteable()
    }

    def "isWriteable: false for final field"() {
        given:
        Field f = TestBean.getDeclaredField("finalField")
        when:
        def d = new NetworkMethodDefinition(bean, f, false, null, "")
        then:
        !d.isWriteable()
    }

    def "isWriteable: false for method (methods are never writeable)"() {
        given:
        Method m = TestBean.getDeclaredMethod("greet")
        when:
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        then:
        !d.isWriteable()
    }

    // ── Scope helpers (always empty/false) ────────────────────────────────────

    def "getRequiredScopes always returns empty list"() {
        given:
        Method m = TestBean.getDeclaredMethod("greet")
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        expect:
        d.getRequiredScopes().isEmpty()
    }

    def "isRequireAllScopes always returns false"() {
        given:
        Method m = TestBean.getDeclaredMethod("greet")
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        expect:
        !d.isRequireAllScopes()
    }

    // ── ParameterInfo ─────────────────────────────────────────────────────────

    def "ParameterInfo stores name, type, and index correctly"() {
        when:
        def p = new NetworkMethodDefinition.ParameterInfo("myArg", "Integer", 3)
        then:
        p.getName() == "myArg"
        p.getType() == "Integer"
        p.getIndex() == 3
    }

    def "ParameterInfo: index starts at 0 for first parameter"() {
        given:
        Method m = TestBean.getDeclaredMethod("withParams", String, int)
        def d = new NetworkMethodDefinition(bean, m, false, null, "")
        expect:
        d.getParameters()[0].getIndex() == 0
    }
}
