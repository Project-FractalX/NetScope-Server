package com.netscope.core

import com.netscope.model.NetworkMethodDefinition
import spock.lang.Specification

class AmbiguousInvocationExceptionSpec extends Specification {

    static class OverloadedBean {
        String process(String s) { s }
        String process(int i) { "$i" }
        String process(String s, int i) { s * i }
    }

    def bean = new OverloadedBean()

    NetworkMethodDefinition defFor(String methodName, Class<?>... paramTypes) {
        def m = OverloadedBean.getDeclaredMethod(methodName, paramTypes)
        new NetworkMethodDefinition(bean, m, false, null, "")
    }

    // ── Message format ────────────────────────────────────────────────────────

    def "message contains the member name"() {
        given:
        def d1 = defFor("process", String)
        def d2 = defFor("process", int)
        when:
        def ex = new AmbiguousInvocationException("OverloadedBean", "process", [d1, d2])
        then:
        ex.message.contains("process")
    }

    def "message contains the bean name"() {
        given:
        def d1 = defFor("process", String)
        def d2 = defFor("process", int)
        when:
        def ex = new AmbiguousInvocationException("OverloadedBean", "process", [d1, d2])
        then:
        ex.message.contains("OverloadedBean")
    }

    def "message instructs caller to use parameter_types"() {
        given:
        def d1 = defFor("process", String)
        def d2 = defFor("process", int)
        when:
        def ex = new AmbiguousInvocationException("OverloadedBean", "process", [d1, d2])
        then:
        ex.message.contains("parameter_types")
    }

    def "message lists all overload signatures in Available section"() {
        given:
        def d1 = defFor("process", String)
        def d2 = defFor("process", int)
        when:
        def ex = new AmbiguousInvocationException("OverloadedBean", "process", [d1, d2])
        then:
        ex.message.contains("Available:")
        ex.message.contains("String")
        ex.message.contains("int")
    }

    def "message formats multi-param overload signature correctly"() {
        given:
        def d1 = defFor("process", String)
        def d2 = defFor("process", String, int)
        when:
        def ex = new AmbiguousInvocationException("OverloadedBean", "process", [d1, d2])
        then:
        // The second candidate has two params: "String s, int i"
        ex.message.contains("String") && ex.message.contains("int")
    }

    // ── getCandidates ─────────────────────────────────────────────────────────

    def "getCandidates returns all provided candidates"() {
        given:
        def d1 = defFor("process", String)
        def d2 = defFor("process", int)
        when:
        def ex = new AmbiguousInvocationException("OverloadedBean", "process", [d1, d2])
        then:
        ex.getCandidates().size() == 2
        ex.getCandidates().containsAll([d1, d2])
    }

    def "getCandidates returns single-element list when only one candidate"() {
        given:
        def d1 = defFor("process", String)
        when:
        def ex = new AmbiguousInvocationException("OverloadedBean", "process", [d1])
        then:
        ex.getCandidates().size() == 1
        ex.getCandidates()[0].is(d1)
    }

    def "getCandidates returns immutable list — add throws UnsupportedOperationException"() {
        given:
        def d1 = defFor("process", String)
        def ex = new AmbiguousInvocationException("OverloadedBean", "process", [d1])
        when:
        ex.getCandidates().add(d1)
        then:
        thrown(UnsupportedOperationException)
    }

    def "getCandidates returns immutable list — remove throws UnsupportedOperationException"() {
        given:
        def d1 = defFor("process", String)
        def d2 = defFor("process", int)
        def ex = new AmbiguousInvocationException("OverloadedBean", "process", [d1, d2])
        when:
        ex.getCandidates().remove(0)
        then:
        thrown(UnsupportedOperationException)
    }

    // ── Inheritance ───────────────────────────────────────────────────────────

    def "extends RuntimeException"() {
        given:
        def d1 = defFor("process", String)
        when:
        def ex = new AmbiguousInvocationException("OverloadedBean", "process", [d1])
        then:
        ex instanceof RuntimeException
    }
}
