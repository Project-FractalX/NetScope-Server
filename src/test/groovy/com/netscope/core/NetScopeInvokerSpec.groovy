package com.netscope.core

import com.netscope.model.NetworkMethodDefinition
import spock.lang.Specification

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture

class NetScopeInvokerSpec extends Specification {

    // ── Test bean with various method and field types ─────────────────────────

    static class InvokerBean {
        String name = "initial"
        final String immutable = "cannot-change"
        static String shared = "static-value"

        String greet()                          { "hello" }
        void   voidOp()                         {}
        Void   voidClassOp()                    { null }
        String echo(String s)                   { s }
        String repeat(String s, int n)          { s * n }
        Object returnNull()                     { null }

        CompletableFuture<String> asyncSuccess() {
            CompletableFuture.completedFuture("async-ok")
        }
        CompletableFuture<String> asyncFailure() {
            def f = new CompletableFuture<String>()
            f.completeExceptionally(new IllegalStateException("future-failed"))
            f
        }
        CompletableFuture<String> asyncException() {
            def f = new CompletableFuture<String>()
            f.completeExceptionally(new Error("not-an-exception"))
            f
        }
    }

    def invoker = new NetScopeInvoker()
    def bean    = new InvokerBean()

    NetworkMethodDefinition methodDef(String name, Class<?>... types) {
        Method m = InvokerBean.getDeclaredMethod(name, types)
        new NetworkMethodDefinition(bean, m, false, null, "")
    }

    NetworkMethodDefinition fieldDef(String name) {
        Field f = InvokerBean.getDeclaredField(name)
        new NetworkMethodDefinition(bean, f, false, null, "")
    }

    // ── invoke() — routing ────────────────────────────────────────────────────

    def "invoke() delegates to readField for field definitions"() {
        given:
        bean.name = "routed"
        def def_ = fieldDef("name")
        when:
        def result = invoker.invoke(def_, null)
        then:
        result == '"routed"'
    }

    def "invoke() delegates to invokeMethod for method definitions"() {
        given:
        def def_ = methodDef("greet")
        when:
        def result = invoker.invoke(def_, null)
        then:
        result == '"hello"'
    }

    // ── readField ─────────────────────────────────────────────────────────────

    def "readField: returns JSON-encoded string value"() {
        given:
        bean.name = "world"
        def def_ = fieldDef("name")
        when:
        def result = invoker.invoke(def_, null)
        then:
        result == '"world"'
    }

    def "readField: returns literal 'null' for null field value"() {
        given:
        bean.name = null
        def def_ = fieldDef("name")
        when:
        def result = invoker.invoke(def_, null)
        then:
        result == "null"
    }

    def "readField: works for static field"() {
        given:
        InvokerBean.shared = "static-read"
        def def_ = fieldDef("shared")
        when:
        def result = invoker.invoke(def_, null)
        then:
        result == '"static-read"'
    }

    // ── invokeMethod — return types ───────────────────────────────────────────

    def "invokeMethod: non-void returns JSON-encoded result"() {
        given:
        def def_ = methodDef("greet")
        when:
        def result = invoker.invoke(def_, null)
        then:
        result == '"hello"'
    }

    def "invokeMethod: void return type returns accepted status JSON"() {
        given:
        def def_ = methodDef("voidOp")
        when:
        def result = invoker.invoke(def_, null)
        then:
        result == '{"status":"accepted"}'
    }

    def "invokeMethod: Void class return type returns accepted status JSON"() {
        given:
        def def_ = methodDef("voidClassOp")
        when:
        def result = invoker.invoke(def_, null)
        then:
        result == '{"status":"accepted"}'
    }

    def "invokeMethod: null result returns literal 'null'"() {
        given:
        def def_ = methodDef("returnNull")
        when:
        def result = invoker.invoke(def_, null)
        then:
        result == "null"
    }

    // ── invokeMethod — argument resolution ────────────────────────────────────

    def "invokeMethod: no-arg method with null argumentsJson invokes correctly"() {
        given:
        def def_ = methodDef("greet")
        when:
        def result = invoker.invoke(def_, null)
        then:
        result == '"hello"'
    }

    def "invokeMethod: no-arg method with blank argumentsJson invokes correctly"() {
        given:
        def def_ = methodDef("greet")
        when:
        def result = invoker.invoke(def_, "")
        then:
        result == '"hello"'
    }

    def "invokeMethod: single argument converted from JSON array"() {
        given:
        def def_ = methodDef("echo", String)
        when:
        def result = invoker.invoke(def_, '["hello"]')
        then:
        result == '"hello"'
    }

    def "invokeMethod: multiple arguments converted from JSON array"() {
        given:
        def def_ = methodDef("repeat", String, int)
        when:
        def result = invoker.invoke(def_, '["ab",3]')
        then:
        result == '"ababab"'
    }

    def "invokeMethod: throws IllegalArgumentException when required args but null json"() {
        given:
        def def_ = methodDef("echo", String)
        when:
        invoker.invoke(def_, null)
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("none were provided")
    }

    def "invokeMethod: throws IllegalArgumentException when required args but blank json"() {
        given:
        def def_ = methodDef("echo", String)
        when:
        invoker.invoke(def_, "   ")
        then:
        thrown(IllegalArgumentException)
    }

    def "invokeMethod: throws IllegalArgumentException when args=[] and params required"() {
        given:
        def def_ = methodDef("echo", String)
        when:
        invoker.invoke(def_, "[]")
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("none were provided")
    }

    def "invokeMethod: throws IllegalArgumentException when arg count mismatch — too few"() {
        given:
        def def_ = methodDef("repeat", String, int)
        when:
        invoker.invoke(def_, '["only-one"]')
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Expected 2")
    }

    def "invokeMethod: throws IllegalArgumentException when arg count mismatch — too many"() {
        given:
        def def_ = methodDef("echo", String)
        when:
        invoker.invoke(def_, '["a","b","c"]')
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Expected 1")
    }

    // ── invokeMethod — CompletableFuture unwrapping ───────────────────────────

    def "invokeMethod: unwraps completed CompletableFuture and returns its value"() {
        given:
        def def_ = methodDef("asyncSuccess")
        when:
        def result = invoker.invoke(def_, null)
        then:
        result == '"async-ok"'
    }

    def "invokeMethod: propagates Exception from failed CompletableFuture"() {
        given:
        def def_ = methodDef("asyncFailure")
        when:
        invoker.invoke(def_, null)
        then:
        def ex = thrown(IllegalStateException)
        ex.message == "future-failed"
    }

    def "invokeMethod: wraps non-Exception Throwable from failed CompletableFuture"() {
        given:
        def def_ = methodDef("asyncException")
        when:
        invoker.invoke(def_, null)
        then:
        thrown(RuntimeException)
    }

    // ── write() ───────────────────────────────────────────────────────────────

    def "write(): throws UnsupportedOperationException for method definition"() {
        given:
        def def_ = methodDef("greet")
        when:
        invoker.write(def_, '"value"')
        then:
        thrown(UnsupportedOperationException)
    }

    def "write(): throws IllegalStateException for final field"() {
        given:
        def def_ = fieldDef("immutable")
        when:
        invoker.write(def_, '"new"')
        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("final")
        ex.message.contains("immutable")
    }

    def "write(): sets new value and returns previous JSON-encoded value"() {
        given:
        bean.name = "old-value"
        def def_ = fieldDef("name")
        when:
        def previous = invoker.write(def_, '"new-value"')
        then:
        previous == '"old-value"'
        bean.name == "new-value"
    }

    def "write(): null valueJson sets field to null and returns previous"() {
        given:
        bean.name = "had-value"
        def def_ = fieldDef("name")
        when:
        def previous = invoker.write(def_, null)
        then:
        previous == '"had-value"'
        bean.name == null
    }

    def "write(): string 'null' sets field to null"() {
        given:
        bean.name = "had-value"
        def def_ = fieldDef("name")
        when:
        invoker.write(def_, "null")
        then:
        bean.name == null
    }

    def "write(): returns literal 'null' when previous value was null"() {
        given:
        bean.name = null
        def def_ = fieldDef("name")
        when:
        def previous = invoker.write(def_, '"new"')
        then:
        previous == "null"
        bean.name == "new"
    }

    def "write(): works for static field"() {
        given:
        InvokerBean.shared = "old-static"
        def def_ = fieldDef("shared")
        when:
        def previous = invoker.write(def_, '"updated"')
        then:
        previous == '"old-static"'
        InvokerBean.shared == "updated"
    }
}
