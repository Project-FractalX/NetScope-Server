package org.fractalx.netscope.server.annotation;

/**
 * Authentication type for {@code @NetworkSecured} methods.
 *
 * <pre>
 * {@code @NetworkSecured(auth = AuthType.OAUTH)}
 * {@code @NetworkSecured(auth = AuthType.API_KEY)}
 * {@code @NetworkSecured(auth = AuthType.BOTH)}
 * </pre>
 */
public enum AuthType {
    /** OAuth 2.0 JWT Bearer token via 'authorization' metadata header */
    OAUTH,
    /** API key via 'x-api-key' metadata header */
    API_KEY,
    /** Accept either OAuth or API key (default) */
    BOTH
}
