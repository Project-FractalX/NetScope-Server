package com.netscope.security

import com.netscope.config.NetScopeConfig
import spock.lang.Specification
import spock.lang.Unroll

class ApiKeyValidatorSpec extends Specification {

    ApiKeyValidator validatorWith(List<String> keys) {
        def config = new NetScopeConfig()
        config.getSecurity().getApiKey().setEnabled(true)
        config.getSecurity().getApiKey().setKeys(keys)
        new ApiKeyValidator(config)
    }

    // ── Null / blank inputs ───────────────────────────────────────────────────

    def "null API key returns false"() {
        given:
        def v = validatorWith(["valid-key"])
        expect:
        !v.isValid(null)
    }

    def "empty string API key returns false"() {
        given:
        def v = validatorWith(["valid-key"])
        expect:
        !v.isValid("")
    }

    def "whitespace-only API key returns false"() {
        given:
        def v = validatorWith(["valid-key"])
        expect:
        !v.isValid("   ")
        !v.isValid("\t")
        !v.isValid("  \n  ")
    }

    // ── Valid matches ─────────────────────────────────────────────────────────

    def "configured API key returns true"() {
        given:
        def v = validatorWith(["secret-123"])
        expect:
        v.isValid("secret-123")
    }

    def "any key from multi-key list returns true"() {
        given:
        def v = validatorWith(["key-a", "key-b", "key-c"])
        expect:
        v.isValid("key-a")
        v.isValid("key-b")
        v.isValid("key-c")
    }

    // ── Invalid keys ──────────────────────────────────────────────────────────

    def "unknown key returns false"() {
        given:
        def v = validatorWith(["valid-key"])
        expect:
        !v.isValid("wrong-key")
    }

    def "empty configured key list rejects everything"() {
        given:
        def v = validatorWith([])
        expect:
        !v.isValid("any-key")
        !v.isValid("valid-key")
    }

    // ── Case sensitivity ──────────────────────────────────────────────────────

    def "key matching is exact — wrong case returns false"() {
        given:
        def v = validatorWith(["MyKey"])
        expect:
        !v.isValid("mykey")
        !v.isValid("MYKEY")
        !v.isValid("Mykey")
    }

    def "key matching is exact — correct case returns true"() {
        given:
        def v = validatorWith(["MyKey"])
        expect:
        v.isValid("MyKey")
    }

    // ── Partial matches ───────────────────────────────────────────────────────

    @Unroll
    def "partial key '#partial' does not match 'valid-key'"() {
        given:
        def v = validatorWith(["valid-key"])
        expect:
        !v.isValid(partial)
        where:
        partial << ["valid", "key", "valid-ke", "alid-key", "valid-key "]
    }
}
