package org.fractalx.netscope.server.config

import org.fractalx.netscope.server.annotation.AuthType
import spock.lang.Specification

class NetScopeConfigSpec extends Specification {

    // ── GrpcConfig defaults ───────────────────────────────────────────────────

    def "GrpcConfig default: enabled = true"() {
        expect:
        new NetScopeConfig().getGrpc().isEnabled()
    }

    def "GrpcConfig default: port = 9090"() {
        expect:
        new NetScopeConfig().getGrpc().getPort() == 9090
    }

    def "GrpcConfig default: maxInboundMessageSize = 4194304 (4MB)"() {
        expect:
        new NetScopeConfig().getGrpc().getMaxInboundMessageSize() == 4194304
    }

    def "GrpcConfig default: maxConcurrentCallsPerConnection = 100"() {
        expect:
        new NetScopeConfig().getGrpc().getMaxConcurrentCallsPerConnection() == 100
    }

    def "GrpcConfig default: keepAliveTime = 300"() {
        expect:
        new NetScopeConfig().getGrpc().getKeepAliveTime() == 300L
    }

    def "GrpcConfig default: keepAliveTimeout = 20"() {
        expect:
        new NetScopeConfig().getGrpc().getKeepAliveTimeout() == 20L
    }

    def "GrpcConfig default: permitKeepAliveWithoutCalls = false"() {
        expect:
        !new NetScopeConfig().getGrpc().isPermitKeepAliveWithoutCalls()
    }

    def "GrpcConfig default: maxConnectionIdle = 0"() {
        expect:
        new NetScopeConfig().getGrpc().getMaxConnectionIdle() == 0L
    }

    def "GrpcConfig default: maxConnectionAge = 0"() {
        expect:
        new NetScopeConfig().getGrpc().getMaxConnectionAge() == 0L
    }

    def "GrpcConfig default: enableReflection = true"() {
        expect:
        new NetScopeConfig().getGrpc().isEnableReflection()
    }

    // ── GrpcConfig setters ────────────────────────────────────────────────────

    def "GrpcConfig setters override defaults"() {
        given:
        def cfg = new NetScopeConfig().getGrpc()
        when:
        cfg.setEnabled(false)
        cfg.setPort(50051)
        cfg.setMaxInboundMessageSize(1024)
        cfg.setMaxConcurrentCallsPerConnection(50)
        cfg.setKeepAliveTime(600)
        cfg.setKeepAliveTimeout(30)
        cfg.setPermitKeepAliveWithoutCalls(true)
        cfg.setMaxConnectionIdle(120)
        cfg.setMaxConnectionAge(3600)
        cfg.setEnableReflection(false)
        then:
        !cfg.isEnabled()
        cfg.getPort() == 50051
        cfg.getMaxInboundMessageSize() == 1024
        cfg.getMaxConcurrentCallsPerConnection() == 50
        cfg.getKeepAliveTime() == 600L
        cfg.getKeepAliveTimeout() == 30L
        cfg.isPermitKeepAliveWithoutCalls()
        cfg.getMaxConnectionIdle() == 120L
        cfg.getMaxConnectionAge() == 3600L
        !cfg.isEnableReflection()
    }

    // ── SecurityConfig defaults ───────────────────────────────────────────────

    def "SecurityConfig default: enabled = true"() {
        expect:
        new NetScopeConfig().getSecurity().isEnabled()
    }

    def "SecurityConfig setter works"() {
        given:
        def sec = new NetScopeConfig().getSecurity()
        when:
        sec.setEnabled(false)
        then:
        !sec.isEnabled()
    }

    // ── OAuthConfig defaults ──────────────────────────────────────────────────

    def "OAuthConfig default: enabled = false"() {
        expect:
        !new NetScopeConfig().getSecurity().getOauth().isEnabled()
    }

    def "OAuthConfig default: issuerUri = null"() {
        expect:
        new NetScopeConfig().getSecurity().getOauth().getIssuerUri() == null
    }

    def "OAuthConfig default: audiences = empty list"() {
        expect:
        new NetScopeConfig().getSecurity().getOauth().getAudiences().isEmpty()
    }

    def "OAuthConfig default: jwkSetUri = null"() {
        expect:
        new NetScopeConfig().getSecurity().getOauth().getJwkSetUri() == null
    }

    def "OAuthConfig default: tokenCacheDuration = 300"() {
        expect:
        new NetScopeConfig().getSecurity().getOauth().getTokenCacheDuration() == 300L
    }

    def "OAuthConfig default: clockSkew = 60"() {
        expect:
        new NetScopeConfig().getSecurity().getOauth().getClockSkew() == 60L
    }

    def "OAuthConfig setters work"() {
        given:
        def oauth = new NetScopeConfig().getSecurity().getOauth()
        when:
        oauth.setEnabled(true)
        oauth.setIssuerUri("https://auth.example.com")
        oauth.setAudiences(["app1", "app2"])
        oauth.setJwkSetUri("https://auth.example.com/.well-known/jwks.json")
        oauth.setTokenCacheDuration(600)
        oauth.setClockSkew(30)
        then:
        oauth.isEnabled()
        oauth.getIssuerUri() == "https://auth.example.com"
        oauth.getAudiences() == ["app1", "app2"]
        oauth.getJwkSetUri() == "https://auth.example.com/.well-known/jwks.json"
        oauth.getTokenCacheDuration() == 600L
        oauth.getClockSkew() == 30L
    }

    def "SecurityConfig.setOauth replaces OAuthConfig"() {
        given:
        def sec = new NetScopeConfig().getSecurity()
        def newOauth = new NetScopeConfig.SecurityConfig.OAuthConfig()
        newOauth.setEnabled(true)
        when:
        sec.setOauth(newOauth)
        then:
        sec.getOauth().isEnabled()
    }

    // ── ApiKeyConfig defaults ─────────────────────────────────────────────────

    def "ApiKeyConfig default: enabled = false"() {
        expect:
        !new NetScopeConfig().getSecurity().getApiKey().isEnabled()
    }

    def "ApiKeyConfig default: keys = empty list"() {
        expect:
        new NetScopeConfig().getSecurity().getApiKey().getKeys().isEmpty()
    }

    def "ApiKeyConfig setters work"() {
        given:
        def apiKey = new NetScopeConfig().getSecurity().getApiKey()
        when:
        apiKey.setEnabled(true)
        apiKey.setKeys(["key1", "key2"])
        then:
        apiKey.isEnabled()
        apiKey.getKeys() == ["key1", "key2"]
    }

    def "SecurityConfig.setApiKey replaces ApiKeyConfig"() {
        given:
        def sec = new NetScopeConfig().getSecurity()
        def newApiKey = new NetScopeConfig.SecurityConfig.ApiKeyConfig()
        newApiKey.setEnabled(true)
        when:
        sec.setApiKey(newApiKey)
        then:
        sec.getApiKey().isEnabled()
    }

}
