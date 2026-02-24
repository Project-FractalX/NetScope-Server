package org.fractalx.netscope.server.security;

import org.fractalx.netscope.server.annotation.AuthType;
import org.fractalx.netscope.server.config.NetScopeConfig;
import org.fractalx.netscope.server.model.NetworkMethodDefinition;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authorizes gRPC calls based on the AuthType declared on the method/field.
 *
 *   AuthType.OAUTH   → only OAuth JWT accepted
 *   AuthType.API_KEY → only API key accepted
 *   AuthType.BOTH    → either accepted
 */
public class OAuth2AuthorizationService {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthorizationService.class);

    private final NetScopeConfig config;
    private final OAuth2TokenValidator oauthValidator;
    private final ApiKeyValidator apiKeyValidator;

    public OAuth2AuthorizationService(NetScopeConfig config,
                                      OAuth2TokenValidator oauthValidator,
                                      ApiKeyValidator apiKeyValidator) {
        this.config          = config;
        this.oauthValidator  = oauthValidator;
        this.apiKeyValidator = apiKeyValidator;

        if (oauthValidator == null && apiKeyValidator == null) {
            logger.warn("NetScope: Security DISABLED — all methods accessible without authentication");
        } else {
            if (oauthValidator  != null) logger.info("NetScope: OAuth 2.0 authentication ENABLED");
            if (apiKeyValidator != null) logger.info("NetScope: API key authentication ENABLED");
        }
    }

    public void authorize(NetworkMethodDefinition def, String accessToken, String apiKey) {

        // @NetworkPublic — always allow
        if (!def.isSecured()) return;

        // Security globally disabled — allow everything
        if (!config.getSecurity().isEnabled()) return;

        AuthType authType = def.getAuthType();
        boolean hasToken  = accessToken != null && !accessToken.isBlank();
        boolean hasApiKey = apiKey != null && !apiKey.isBlank();

        logger.debug("Authorizing {}.{} | authType={} | hasToken={} | hasApiKey={}",
                def.getBeanName(), def.getMethodName(), authType, hasToken, hasApiKey);

        switch (authType) {

            case OAUTH -> {
                // Only OAuth accepted — reject API key even if provided
                if (!hasToken) {
                    throw Status.UNAUTHENTICATED
                            .withDescription("Method '" + def.getMethodName()
                                    + "' requires OAuth token (authorization header)")
                            .asRuntimeException();
                }
                validateOAuth(def, accessToken);
            }

            case API_KEY -> {
                // Only API key accepted — reject OAuth token even if provided
                if (!hasApiKey) {
                    throw Status.UNAUTHENTICATED
                            .withDescription("Method '" + def.getMethodName()
                                    + "' requires API key (x-api-key header)")
                            .asRuntimeException();
                }
                validateApiKey(apiKey);
            }

            case BOTH -> {
                // Accept either — try whichever is provided
                if (!hasToken && !hasApiKey) {
                    throw Status.UNAUTHENTICATED
                            .withDescription("Method '" + def.getMethodName()
                                    + "' requires authentication. "
                                    + "Provide an OAuth token or API key.")
                            .asRuntimeException();
                }
                // Try OAuth first if provided
                if (hasToken && tryOAuth(def, accessToken)) return;
                // Try API key if provided
                if (hasApiKey && tryApiKey(apiKey)) return;

                // Both were provided but both failed
                throw Status.UNAUTHENTICATED
                        .withDescription("Authentication failed: "
                                + "invalid OAuth token and invalid API key")
                        .asRuntimeException();
            }
        }
    }

    // ── OAuth helpers ─────────────────────────────────────────────────────────

    private void validateOAuth(NetworkMethodDefinition def, String token) {
        if (oauthValidator == null) {
            throw Status.UNAUTHENTICATED
                    .withDescription("OAuth is not configured on this server")
                    .asRuntimeException();
        }
        OAuth2TokenValidator.TokenValidationResult result = oauthValidator.validate(token);
        if (!result.isValid()) {
            throw Status.UNAUTHENTICATED
                    .withDescription("Invalid OAuth token: " + result.getError())
                    .asRuntimeException();
        }
        logger.info("Authorized {}.{} via OAuth (subject={})",
                def.getBeanName(), def.getMethodName(), result.getSubject());
    }

    /** Returns true if OAuth succeeds, false if it fails (no exception) */
    private boolean tryOAuth(NetworkMethodDefinition def, String token) {
        if (oauthValidator == null) return false;
        OAuth2TokenValidator.TokenValidationResult result = oauthValidator.validate(token);
        if (result.isValid()) {
            logger.info("Authorized {}.{} via OAuth (subject={})",
                    def.getBeanName(), def.getMethodName(), result.getSubject());
            return true;
        }
        return false;
    }

    // ── API key helpers ───────────────────────────────────────────────────────

    private void validateApiKey(String apiKey) {
        if (apiKeyValidator == null) {
            throw Status.UNAUTHENTICATED
                    .withDescription("API key auth is not configured on this server")
                    .asRuntimeException();
        }
        if (!apiKeyValidator.isValid(apiKey)) {
            throw Status.UNAUTHENTICATED
                    .withDescription("Invalid API key")
                    .asRuntimeException();
        }
    }

    /** Returns true if API key is valid, false if not (no exception) */
    private boolean tryApiKey(String apiKey) {
        if (apiKeyValidator == null) return false;
        return apiKeyValidator.isValid(apiKey);
    }
}
