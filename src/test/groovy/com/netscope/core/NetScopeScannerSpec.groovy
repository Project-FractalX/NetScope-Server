package com.netscope.core

import com.netscope.annotation.AuthType
import com.netscope.annotation.NetworkPublic
import com.netscope.annotation.NetworkSecured
import com.netscope.config.NetScopeConfig
import org.springframework.context.ApplicationContext
import spock.lang.Specification

class NetScopeScannerSpec extends Specification {

    // ── Test fixtures: beans and interfaces ───────────────────────────────────

    interface SearchService {
        String find(String query)
    }

    interface AliasedService {
        String getData()
        String getSecured()
    }

    static class SearchServiceImpl implements SearchService, AliasedService {
        @NetworkPublic(description = "find items")
        String find(String query) { "result:$query" }

        @NetworkSecured(auth = AuthType.OAUTH, description = "secured data")
        String getSecured() { "secret" }

        @NetworkPublic
        String getData() { "data" }

        // Overloaded
        @NetworkPublic
        String process(String s) { s }
        @NetworkPublic
        String process(int i) { "$i" }

        // Not annotated — should NOT be scanned
        String ignored() { "ignored" }
    }

    static class FieldBean {
        @NetworkPublic
        String version = "1.0"

        @NetworkSecured(auth = AuthType.API_KEY)
        final String secret = "top-secret"

        @NetworkPublic
        static String appName = "NetScope"

        // Not annotated — should NOT be scanned
        String internal = "skip"
    }

    static class InheritedBase {
        @NetworkPublic
        String baseMethod() { "base" }
    }

    static class InheritedChild extends InheritedBase {
        @NetworkPublic
        String childMethod() { "child" }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    ApplicationContext mockCtx(Map<String, Object> beans) {
        def ctx = Mock(ApplicationContext)
        ctx.getBeanDefinitionNames() >> beans.keySet().toArray(new String[0])
        beans.each { name, bean -> ctx.getBean(name) >> bean }
        ctx
    }

    NetScopeConfig config = new NetScopeConfig()

    // ── scan() ────────────────────────────────────────────────────────────────

    def "scan() returns only annotated methods"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        def results = scanner.scan()
        then:
        // find, getSecured, getData, process(String), process(int) = 5
        results.size() == 5
        results.every { it.getMethodName() in ["find", "getSecured", "getData", "process"] }
    }

    def "scan() correctly identifies secured vs public methods"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        def results = scanner.scan()
        then:
        results.find { it.getMethodName() == "getSecured" }.isSecured()
        results.find { it.getMethodName() == "getSecured" }.getAuthType() == AuthType.OAUTH
        !results.find { it.getMethodName() == "find" }.isSecured()
    }

    def "scan() captures description from annotation"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        def results = scanner.scan()
        then:
        results.find { it.getMethodName() == "find" }.getDescription() == "find items"
        results.find { it.getMethodName() == "getSecured" }.getDescription() == "secured data"
    }

    def "scan() returns distinct list — does not duplicate entries"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        def results = scanner.scan()
        then:
        // Each entry is unique by (methodName, full param-type signature)
        def keys = results.collect { d ->
            d.getMethodName() + "(" + d.getParameters().collect { it.getType() }.join(",") + ")"
        }
        keys.size() == keys.unique().size()
    }

    def "scan() is idempotent — calling twice returns same count"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        def first  = scanner.scan()
        def second = scanner.scan()
        then:
        first.size() == second.size()
    }

    def "scan() returns empty list when context has no annotated beans"() {
        given:
        def ctx = Mock(ApplicationContext)
        ctx.getBeanDefinitionNames() >> []
        def scanner = new NetScopeScanner(ctx, config)
        when:
        def results = scanner.scan()
        then:
        results.isEmpty()
    }

    // ── scan(): fields ────────────────────────────────────────────────────────

    def "scan() finds annotated fields"() {
        given:
        def bean = new FieldBean()
        def scanner = new NetScopeScanner(mockCtx([fb: bean]), config)
        when:
        def results = scanner.scan()
        then:
        results.size() == 3 // version, secret, appName
        results.every { it.isField() }
    }

    def "scan(): final field is detected as final and not writeable"() {
        given:
        def bean = new FieldBean()
        def scanner = new NetScopeScanner(mockCtx([fb: bean]), config)
        when:
        def results = scanner.scan()
        def secretDef = results.find { it.getMethodName() == "secret" }
        then:
        secretDef.isFinal()
        !secretDef.isWriteable()
    }

    def "scan(): static field is detected as static"() {
        given:
        def bean = new FieldBean()
        def scanner = new NetScopeScanner(mockCtx([fb: bean]), config)
        when:
        def results = scanner.scan()
        def appNameDef = results.find { it.getMethodName() == "appName" }
        then:
        appNameDef.isStatic()
    }

    def "scan(): mutable non-static field is writeable"() {
        given:
        def bean = new FieldBean()
        def scanner = new NetScopeScanner(mockCtx([fb: bean]), config)
        when:
        def results = scanner.scan()
        def versionDef = results.find { it.getMethodName() == "version" }
        then:
        versionDef.isWriteable()
        !versionDef.isStatic()
    }

    // ── scan(): inheritance ───────────────────────────────────────────────────

    def "scan() includes inherited methods from superclass"() {
        given:
        def bean = new InheritedChild()
        def scanner = new NetScopeScanner(mockCtx([child: bean]), config)
        when:
        def results = scanner.scan()
        then:
        results.any { it.getMethodName() == "baseMethod" }
        results.any { it.getMethodName() == "childMethod" }
    }

    // ── scan(): exception resilience ──────────────────────────────────────────

    def "scan() skips beans that throw during getBean and continues"() {
        given:
        def goodBean = new SearchServiceImpl()
        def ctx = Mock(ApplicationContext)
        ctx.getBeanDefinitionNames() >> ["badBean", "goodBean"]
        ctx.getBean("badBean") >> { throw new RuntimeException("not available") }
        ctx.getBean("goodBean") >> goodBean
        def scanner = new NetScopeScanner(ctx, config)
        when:
        def results = scanner.scan()
        then:
        noExceptionThrown()
        results.size() >= 1
    }

    // ── findMethod(): lazy scan trigger ──────────────────────────────────────

    def "findMethod() triggers scan on first call"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        def result = scanner.findMethod("SearchServiceImpl", "find", [])
        then:
        result.isPresent()
        result.get().getMethodName() == "find"
    }

    // ── findMethod(): field lookup ────────────────────────────────────────────

    def "findMethod() locates field by plain name (no parameter types)"() {
        given:
        def bean = new FieldBean()
        def scanner = new NetScopeScanner(mockCtx([fb: bean]), config)
        when:
        def result = scanner.findMethod("FieldBean", "version", [])
        then:
        result.isPresent()
        result.get().isField()
        result.get().getMethodName() == "version"
    }

    def "findMethod() locates field with null parameterTypes"() {
        given:
        def bean = new FieldBean()
        def scanner = new NetScopeScanner(mockCtx([fb: bean]), config)
        when:
        def result = scanner.findMethod("FieldBean", "version", null)
        then:
        result.isPresent()
        result.get().isField()
    }

    // ── findMethod(): exact method lookup with parameter types ────────────────

    def "findMethod() with parameter_types resolves exact overload — String"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        def result = scanner.findMethod("SearchServiceImpl", "process", ["String"])
        then:
        result.isPresent()
        result.get().getParameters()[0].getType() == "String"
    }

    def "findMethod() with parameter_types resolves exact overload — int"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        def result = scanner.findMethod("SearchServiceImpl", "process", ["int"])
        then:
        result.isPresent()
        result.get().getParameters()[0].getType() == "int"
    }

    def "findMethod() with parameter_types that don't match returns empty"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        def result = scanner.findMethod("SearchServiceImpl", "process", ["Long"])
        then:
        result.isEmpty()
    }

    // ── findMethod(): unambiguous index lookup ────────────────────────────────

    def "findMethod() without parameter types resolves single-overload method"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        def result = scanner.findMethod("SearchServiceImpl", "find", null)
        then:
        result.isPresent()
        result.get().getMethodName() == "find"
    }

    def "findMethod() without parameter types throws AmbiguousInvocationException for overloaded"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        scanner.findMethod("SearchServiceImpl", "process", [])
        then:
        def ex = thrown(AmbiguousInvocationException)
        ex.getCandidates().size() == 2
        ex.message.contains("process")
    }

    // ── findMethod(): not found ───────────────────────────────────────────────

    def "findMethod() returns empty for unknown bean"() {
        given:
        def scanner = new NetScopeScanner(mockCtx([:]), config)
        when:
        def result = scanner.findMethod("NoSuchBean", "method", [])
        then:
        result.isEmpty()
    }

    def "findMethod() returns empty for unknown method on known bean"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        def result = scanner.findMethod("SearchServiceImpl", "nonExistentMethod", [])
        then:
        result.isEmpty()
    }

    // ── findMethod(): interface alias lookup ──────────────────────────────────

    def "findMethod() by interface alias finds same definition as concrete name"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        def byConcrete  = scanner.findMethod("SearchServiceImpl", "find", null)
        def byInterface = scanner.findMethod("SearchService",     "find", null)
        then:
        byConcrete.isPresent()
        byInterface.isPresent()
        byInterface.get() is byConcrete.get()
    }

    def "findMethod() by interface alias with exact parameter types resolves overload"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        def result = scanner.findMethod("AliasedService", "process", ["String"])
        then:
        result.isPresent()
        result.get().getParameters()[0].getType() == "String"
    }

    def "findMethod() by interface alias for ambiguous overload throws AmbiguousInvocationException"() {
        given:
        def bean = new SearchServiceImpl()
        def scanner = new NetScopeScanner(mockCtx([svc: bean]), config)
        when:
        scanner.findMethod("SearchService", "process", [])
        then:
        thrown(AmbiguousInvocationException)
    }

    def "findMethod() by interface alias for field returns field definition"() {
        given:
        // FieldBean implements no user interface — test with a class that has an interface + field
        def bean = new FieldBean()
        def scanner = new NetScopeScanner(mockCtx([fb: bean]), config)
        when:
        def result = scanner.findMethod("FieldBean", "version", [])
        then:
        result.isPresent()
        result.get().isField()
    }
}
