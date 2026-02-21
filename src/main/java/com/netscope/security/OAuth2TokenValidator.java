package com.netscope.security;

import com.netscope.config.NetScopeConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OAuth2TokenValidator {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2TokenValidator.class);

    private final NetScopeConfig.SecurityConfig.OAuthConfig oauthConfig;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private final Map<String, TokenValidationResult> tokenCache = new ConcurrentHashMap<>();

    public OAuth2TokenValidator(NetScopeConfig config) {
        this.oauthConfig = config.getSecurity().getOauth();
        this.jwtProcessor = buildProcessor();
        logger.info("NetScope: OAuth 2.0 token validator activated (issuer: {})",
                oauthConfig.getIssuerUri());
    }

    public TokenValidationResult validate(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return TokenValidationResult.invalid("Missing access token");
        }

        // Cache hit
        TokenValidationResult cached = tokenCache.get(accessToken);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }

        try {
            JWTClaimsSet claims = jwtProcessor.process(accessToken, null);

            // Validate issuer
            if (oauthConfig.getIssuerUri() != null) {
                if (!oauthConfig.getIssuerUri().equals(claims.getIssuer())) {
                    return TokenValidationResult.invalid("Invalid issuer: " + claims.getIssuer());
                }
            }

            // Validate audience
            if (!oauthConfig.getAudiences().isEmpty()) {
                List<String> aud = claims.getAudience();
                boolean valid = aud != null &&
                        aud.stream().anyMatch(a -> oauthConfig.getAudiences().contains(a));
                if (!valid) {
                    return TokenValidationResult.invalid("Invalid audience");
                }
            }

            Set<String> scopes = extractScopes(claims);
            TokenValidationResult result = TokenValidationResult.valid(
                    claims.getSubject(), scopes, claims.getExpirationTime());

            // Cache and clean
            tokenCache.put(accessToken, result);
            if (tokenCache.size() > 1000) {
                tokenCache.entrySet().removeIf(e -> e.getValue().isExpired());
            }

            return result;

        } catch (Exception e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            return TokenValidationResult.invalid("Invalid token: " + e.getMessage());
        }
    }

    private Set<String> extractScopes(JWTClaimsSet claims) {
        Set<String> scopes = new HashSet<>();
        try {
            String scopeStr = claims.getStringClaim("scope");
            if (scopeStr != null) scopes.addAll(Arrays.asList(scopeStr.split("\\s+")));
            List<String> scp = claims.getStringListClaim("scp");
            if (scp != null) scopes.addAll(scp);
        } catch (Exception ignored) {}
        return scopes;
    }

    private ConfigurableJWTProcessor<SecurityContext> buildProcessor() {
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        try {
            if (oauthConfig.getJwkSetUri() != null) {
                JWKSource<SecurityContext> keySource =
                        new RemoteJWKSet<>(new URL(oauthConfig.getJwkSetUri()));
                JWSKeySelector<SecurityContext> keySelector =
                        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
                processor.setJWSKeySelector(keySelector);
            }
        } catch (Exception e) {
            logger.error("Failed to configure JWT processor: {}", e.getMessage());
        }
        return processor;
    }

    // ── Result ────────────────────────────────────────────────────────────────

    public static class TokenValidationResult {
        private final boolean valid;
        private final String error;
        private final String subject;
        private final Set<String> scopes;
        private final Instant expiration;

        private TokenValidationResult(boolean valid, String error, String subject,
                                      Set<String> scopes, Instant expiration) {
            this.valid = valid;
            this.error = error;
            this.subject = subject;
            this.scopes = scopes != null ? scopes : Set.of();
            this.expiration = expiration;
        }

        public static TokenValidationResult valid(String subject, Set<String> scopes, Date exp) {
            return new TokenValidationResult(true, null, subject, scopes,
                    exp != null ? exp.toInstant() : Instant.MAX);
        }

        public static TokenValidationResult invalid(String error) {
            return new TokenValidationResult(false, error, null, null, null);
        }

        public boolean isValid() { return valid; }
        public String getError() { return error; }
        public String getSubject() { return subject; }
        public Set<String> getScopes() { return scopes; }
        public boolean isExpired() { return expiration != null && Instant.now().isAfter(expiration); }
        public boolean hasAllScopes(Collection<String> required) { return scopes.containsAll(required); }
        public boolean hasAnyScope(Collection<String> required) {
            return required.stream().anyMatch(scopes::contains);
        }
    }
}
