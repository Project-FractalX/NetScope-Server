package org.fractalx.netscope.server.security

import org.fractalx.netscope.server.annotation.AuthType
import org.fractalx.netscope.server.config.NetScopeConfig
import org.fractalx.netscope.server.model.NetworkMethodDefinition
import io.grpc.Status
import io.grpc.StatusRuntimeException
import spock.lang.Specification

class OAuth2AuthorizationServiceSpec extends Specification {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    static class Svc {
        void op() {}
    }

    def bean = new Svc()
    def config = new NetScopeConfig()
    def oauthValidator  = Mock(OAuth2TokenValidator)
    def apiKeyValidator = Mock(ApiKeyValidator)

    def setup() {
        config.getSecurity().setEnabled(true)
    }

    NetworkMethodDefinition defWith(boolean secured, AuthType authType) {
        def m = Svc.getDeclaredMethod("op")
        new NetworkMethodDefinition(bean, m, secured, authType, "")
    }

    static def validResult()   { OAuth2TokenValidator.TokenValidationResult.valid("user", Set.of(), null) }
    static def invalidResult() { OAuth2TokenValidator.TokenValidationResult.invalid("bad") }

    // ── @NetworkPublic — never requires auth ──────────────────────────────────

    def "public def passes with no credentials and no validators called"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        def def_ = defWith(false, null)
        when:
        svc.authorize(def_, null, null)
        then:
        noExceptionThrown()
        0 * oauthValidator._
        0 * apiKeyValidator._
    }

    // ── Security globally disabled ────────────────────────────────────────────

    def "secured def passes when security.enabled is false"() {
        given:
        config.getSecurity().setEnabled(false)
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        def def_ = defWith(true, AuthType.OAUTH)
        when:
        svc.authorize(def_, null, null)
        then:
        noExceptionThrown()
        0 * oauthValidator._
    }

    // ── OAUTH ─────────────────────────────────────────────────────────────────

    def "OAUTH: null token throws UNAUTHENTICATED"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        when:
        svc.authorize(defWith(true, AuthType.OAUTH), null, "some-api-key")
        then:
        def ex = thrown(StatusRuntimeException)
        ex.status.code == Status.Code.UNAUTHENTICATED
        ex.message.contains("authorization")
    }

    def "OAUTH: blank token throws UNAUTHENTICATED"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        when:
        svc.authorize(defWith(true, AuthType.OAUTH), "   ", null)
        then:
        thrown(StatusRuntimeException)
    }

    def "OAUTH: valid token passes"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        oauthValidator.validate("good-token") >> validResult()
        when:
        svc.authorize(defWith(true, AuthType.OAUTH), "good-token", null)
        then:
        noExceptionThrown()
    }

    def "OAUTH: invalid token throws UNAUTHENTICATED"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        oauthValidator.validate("bad-token") >> invalidResult()
        when:
        svc.authorize(defWith(true, AuthType.OAUTH), "bad-token", null)
        then:
        def ex = thrown(StatusRuntimeException)
        ex.status.code == Status.Code.UNAUTHENTICATED
    }

    def "OAUTH: null oauthValidator throws UNAUTHENTICATED with 'not configured' message"() {
        given:
        def svc = new OAuth2AuthorizationService(config, null, apiKeyValidator)
        when:
        svc.authorize(defWith(true, AuthType.OAUTH), "any-token", null)
        then:
        def ex = thrown(StatusRuntimeException)
        ex.status.code == Status.Code.UNAUTHENTICATED
        ex.message.contains("not configured")
    }

    // ── API_KEY ───────────────────────────────────────────────────────────────

    def "API_KEY: null key throws UNAUTHENTICATED"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        when:
        svc.authorize(defWith(true, AuthType.API_KEY), "some-oauth-token", null)
        then:
        def ex = thrown(StatusRuntimeException)
        ex.status.code == Status.Code.UNAUTHENTICATED
        ex.message.contains("x-api-key")
    }

    def "API_KEY: blank key throws UNAUTHENTICATED"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        when:
        svc.authorize(defWith(true, AuthType.API_KEY), null, "  ")
        then:
        thrown(StatusRuntimeException)
    }

    def "API_KEY: valid key passes"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        apiKeyValidator.isValid("good-key") >> true
        when:
        svc.authorize(defWith(true, AuthType.API_KEY), null, "good-key")
        then:
        noExceptionThrown()
    }

    def "API_KEY: invalid key throws UNAUTHENTICATED"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        apiKeyValidator.isValid("bad-key") >> false
        when:
        svc.authorize(defWith(true, AuthType.API_KEY), null, "bad-key")
        then:
        thrown(StatusRuntimeException)
    }

    def "API_KEY: null apiKeyValidator throws UNAUTHENTICATED with 'not configured' message"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, null)
        when:
        svc.authorize(defWith(true, AuthType.API_KEY), null, "some-key")
        then:
        def ex = thrown(StatusRuntimeException)
        ex.status.code == Status.Code.UNAUTHENTICATED
        ex.message.contains("not configured")
    }

    // ── BOTH ──────────────────────────────────────────────────────────────────

    def "BOTH: no credentials at all throws UNAUTHENTICATED"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        when:
        svc.authorize(defWith(true, AuthType.BOTH), null, null)
        then:
        def ex = thrown(StatusRuntimeException)
        ex.status.code == Status.Code.UNAUTHENTICATED
    }

    def "BOTH: both blank throw UNAUTHENTICATED"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        when:
        svc.authorize(defWith(true, AuthType.BOTH), "  ", "  ")
        then:
        thrown(StatusRuntimeException)
    }

    def "BOTH: valid OAuth token passes"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        oauthValidator.validate("good-token") >> validResult()
        when:
        svc.authorize(defWith(true, AuthType.BOTH), "good-token", null)
        then:
        noExceptionThrown()
    }

    def "BOTH: valid API key passes when no OAuth token"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        apiKeyValidator.isValid("good-key") >> true
        when:
        svc.authorize(defWith(true, AuthType.BOTH), null, "good-key")
        then:
        noExceptionThrown()
    }

    def "BOTH: invalid OAuth but valid API key passes"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        oauthValidator.validate("bad-token") >> invalidResult()
        apiKeyValidator.isValid("good-key") >> true
        when:
        svc.authorize(defWith(true, AuthType.BOTH), "bad-token", "good-key")
        then:
        noExceptionThrown()
    }

    def "BOTH: valid OAuth short-circuits — API key never checked"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        oauthValidator.validate("good-token") >> validResult()
        when:
        svc.authorize(defWith(true, AuthType.BOTH), "good-token", "ignored-key")
        then:
        noExceptionThrown()
        0 * apiKeyValidator._
    }

    def "BOTH: both credentials invalid throws UNAUTHENTICATED"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        oauthValidator.validate("bad-token") >> invalidResult()
        apiKeyValidator.isValid("bad-key") >> false
        when:
        svc.authorize(defWith(true, AuthType.BOTH), "bad-token", "bad-key")
        then:
        def ex = thrown(StatusRuntimeException)
        ex.status.code == Status.Code.UNAUTHENTICATED
        ex.message.contains("invalid OAuth token and invalid API key")
    }

    def "BOTH: null oauthValidator falls through to API key"() {
        given:
        def svc = new OAuth2AuthorizationService(config, null, apiKeyValidator)
        apiKeyValidator.isValid("good-key") >> true
        when:
        svc.authorize(defWith(true, AuthType.BOTH), "any-token", "good-key")
        then:
        noExceptionThrown()
    }

    def "BOTH: null apiKeyValidator with valid OAuth passes"() {
        given:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, null)
        oauthValidator.validate("good-token") >> validResult()
        when:
        svc.authorize(defWith(true, AuthType.BOTH), "good-token", "some-key")
        then:
        noExceptionThrown()
    }

    def "BOTH: null oauthValidator and null apiKeyValidator — only token provided throws"() {
        given:
        def svc = new OAuth2AuthorizationService(config, null, null)
        when:
        // both validators null, only token provided — tryOAuth returns false, tryApiKey returns false
        svc.authorize(defWith(true, AuthType.BOTH), "tok", "key")
        then:
        thrown(StatusRuntimeException)
    }

    // ── Constructor logging (both null) ───────────────────────────────────────

    def "constructor with both validators null logs warning but does not throw"() {
        when:
        def svc = new OAuth2AuthorizationService(config, null, null)
        then:
        noExceptionThrown()
        svc != null
    }

    def "constructor with both validators present does not throw"() {
        when:
        def svc = new OAuth2AuthorizationService(config, oauthValidator, apiKeyValidator)
        then:
        noExceptionThrown()
    }
}
