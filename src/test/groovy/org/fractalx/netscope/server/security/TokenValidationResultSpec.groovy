package org.fractalx.netscope.server.security

import spock.lang.Specification

import java.time.Instant

/**
 * Tests for the static inner class OAuth2TokenValidator.TokenValidationResult.
 */
class TokenValidationResultSpec extends Specification {

    // Convenience alias
    static def valid(subject, scopes, exp) {
        OAuth2TokenValidator.TokenValidationResult.valid(subject, scopes, exp)
    }

    static def invalid(error) {
        OAuth2TokenValidator.TokenValidationResult.invalid(error)
    }

    static Date inFuture()  { new Date(Instant.now().plusSeconds(3600).toEpochMilli()) }
    static Date inPast()    { new Date(Instant.now().minusSeconds(10).toEpochMilli()) }

    // ── valid() factory ───────────────────────────────────────────────────────

    def "valid(): isValid returns true"() {
        expect:
        valid("sub", Set.of("read"), inFuture()).isValid()
    }

    def "valid(): subject is preserved"() {
        expect:
        valid("user-123", Set.of(), inFuture()).getSubject() == "user-123"
    }

    def "valid(): scopes are preserved"() {
        expect:
        valid("sub", Set.of("read", "write"), inFuture()).getScopes() == Set.of("read", "write")
    }

    def "valid(): error is null"() {
        expect:
        valid("sub", Set.of(), inFuture()).getError() == null
    }

    def "valid(): not expired when expiration is in the future"() {
        expect:
        !valid("sub", Set.of(), inFuture()).isExpired()
    }

    def "valid(): expired when expiration is in the past"() {
        expect:
        valid("sub", Set.of(), inPast()).isExpired()
    }

    def "valid(): null expiration maps to Instant.MAX — never expires"() {
        when:
        def r = valid("sub", Set.of(), null)
        then:
        !r.isExpired()
    }

    def "valid(): null scopes default to empty set — never null"() {
        when:
        def r = valid("sub", null, null)
        then:
        r.getScopes() != null
        r.getScopes().isEmpty()
    }

    // ── invalid() factory ─────────────────────────────────────────────────────

    def "invalid(): isValid returns false"() {
        expect:
        !invalid("bad token").isValid()
    }

    def "invalid(): error message is preserved"() {
        expect:
        invalid("token expired").getError() == "token expired"
    }

    def "invalid(): subject is null"() {
        expect:
        invalid("err").getSubject() == null
    }

    def "invalid(): scopes returns empty set — never null"() {
        when:
        def r = invalid("err")
        then:
        r.getScopes() != null
        r.getScopes().isEmpty()
    }

    def "invalid(): isExpired returns false when expiration is null"() {
        expect:
        !invalid("err").isExpired()
    }

    // ── hasAllScopes ──────────────────────────────────────────────────────────

    def "hasAllScopes: true when all required scopes are present"() {
        given:
        def r = valid("sub", Set.of("read", "write", "admin"), null)
        expect:
        r.hasAllScopes(["read", "write"])
        r.hasAllScopes(["admin"])
    }

    def "hasAllScopes: false when any required scope is missing"() {
        given:
        def r = valid("sub", Set.of("read"), null)
        expect:
        !r.hasAllScopes(["read", "write"])
    }

    def "hasAllScopes: true for empty required set (vacuously true)"() {
        given:
        def r = valid("sub", Set.of(), null)
        expect:
        r.hasAllScopes([])
    }

    def "hasAllScopes: false when token has no scopes but scopes required"() {
        given:
        def r = valid("sub", Set.of(), null)
        expect:
        !r.hasAllScopes(["read"])
    }

    // ── hasAnyScope ───────────────────────────────────────────────────────────

    def "hasAnyScope: true when at least one required scope is present"() {
        given:
        def r = valid("sub", Set.of("read"), null)
        expect:
        r.hasAnyScope(["read", "write"])
    }

    def "hasAnyScope: false when none of the required scopes are present"() {
        given:
        def r = valid("sub", Set.of("read"), null)
        expect:
        !r.hasAnyScope(["write", "admin"])
    }

    def "hasAnyScope: false for empty required set"() {
        given:
        def r = valid("sub", Set.of("read"), null)
        expect:
        !r.hasAnyScope([])
    }

    def "hasAnyScope: false when token has no scopes"() {
        given:
        def r = valid("sub", Set.of(), null)
        expect:
        !r.hasAnyScope(["read"])
    }
}
