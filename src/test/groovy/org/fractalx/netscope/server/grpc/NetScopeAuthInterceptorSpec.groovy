package org.fractalx.netscope.server.grpc

import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerCall.Listener
import spock.lang.Specification

class NetScopeAuthInterceptorSpec extends Specification {

    def interceptor = new NetScopeAuthInterceptor()

    // ── Helper to call interceptCall and capture the context values ───────────

    def capturedToken
    def capturedApiKey

    /**
     * Runs interceptCall with the given headers and captures the context values
     * set by the interceptor (by peeking at them during next.startCall).
     */
    void intercept(Metadata headers) {
        def call    = Mock(ServerCall)
        def handler = Mock(ServerCallHandler) {
            startCall(_, _) >> {
                capturedToken  = NetScopeAuthInterceptor.ACCESS_TOKEN_CTX.get()
                capturedApiKey = NetScopeAuthInterceptor.API_KEY_CTX.get()
                Mock(Listener)
            }
        }
        interceptor.interceptCall(call, headers, handler)
    }

    // ── No headers ────────────────────────────────────────────────────────────

    def "no headers: token and apiKey context values are empty strings"() {
        given:
        def headers = new Metadata()
        when:
        intercept(headers)
        then:
        capturedToken  == ""
        capturedApiKey == ""
    }

    // ── Authorization header ──────────────────────────────────────────────────

    def "Bearer token: extracted and stored without 'Bearer ' prefix"() {
        given:
        def headers = new Metadata()
        headers.put(NetScopeAuthInterceptor.AUTHORIZATION_KEY, "Bearer my-jwt-token")
        when:
        intercept(headers)
        then:
        capturedToken == "my-jwt-token"
    }

    def "Bearer token with extra whitespace: trimmed"() {
        given:
        def headers = new Metadata()
        headers.put(NetScopeAuthInterceptor.AUTHORIZATION_KEY, "Bearer   spaced-token  ")
        when:
        intercept(headers)
        then:
        capturedToken == "spaced-token"
    }

    def "Raw token without Bearer prefix: stored as-is (trimmed)"() {
        given:
        def headers = new Metadata()
        headers.put(NetScopeAuthInterceptor.AUTHORIZATION_KEY, "raw-token-value")
        when:
        intercept(headers)
        then:
        capturedToken == "raw-token-value"
    }

    def "Raw token with whitespace: trimmed"() {
        given:
        def headers = new Metadata()
        headers.put(NetScopeAuthInterceptor.AUTHORIZATION_KEY, "  raw-token  ")
        when:
        intercept(headers)
        then:
        capturedToken == "raw-token"
    }

    def "Blank authorization header: treated as absent, token is empty string"() {
        given:
        def headers = new Metadata()
        headers.put(NetScopeAuthInterceptor.AUTHORIZATION_KEY, "   ")
        when:
        intercept(headers)
        then:
        capturedToken == ""
    }

    // ── API key header ────────────────────────────────────────────────────────

    def "x-api-key header: stored in context"() {
        given:
        def headers = new Metadata()
        headers.put(NetScopeAuthInterceptor.API_KEY_HEADER, "my-api-key")
        when:
        intercept(headers)
        then:
        capturedApiKey == "my-api-key"
    }

    def "x-api-key header with whitespace: trimmed"() {
        given:
        def headers = new Metadata()
        headers.put(NetScopeAuthInterceptor.API_KEY_HEADER, "  padded-key  ")
        when:
        intercept(headers)
        then:
        capturedApiKey == "padded-key"
    }

    def "no x-api-key header: apiKey context value is empty string"() {
        given:
        def headers = new Metadata()
        when:
        intercept(headers)
        then:
        capturedApiKey == ""
    }

    // ── Both headers together ─────────────────────────────────────────────────

    def "both headers set: both values available in context"() {
        given:
        def headers = new Metadata()
        headers.put(NetScopeAuthInterceptor.AUTHORIZATION_KEY, "Bearer token-abc")
        headers.put(NetScopeAuthInterceptor.API_KEY_HEADER, "key-xyz")
        when:
        intercept(headers)
        then:
        capturedToken  == "token-abc"
        capturedApiKey == "key-xyz"
    }

    // ── Static key constants ──────────────────────────────────────────────────

    def "AUTHORIZATION_KEY metadata key name is 'authorization'"() {
        expect:
        NetScopeAuthInterceptor.AUTHORIZATION_KEY.name() == "authorization"
    }

    def "API_KEY_HEADER metadata key name is 'x-api-key'"() {
        expect:
        NetScopeAuthInterceptor.API_KEY_HEADER.name() == "x-api-key"
    }

    def "ACCESS_TOKEN_CTX and API_KEY_CTX keys are distinct"() {
        expect:
        NetScopeAuthInterceptor.ACCESS_TOKEN_CTX != NetScopeAuthInterceptor.API_KEY_CTX
    }
}
