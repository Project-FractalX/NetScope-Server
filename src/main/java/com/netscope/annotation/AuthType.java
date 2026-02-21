package com.netscope.annotation;

/**
 * Authentication type for @NetworkSecured methods.
 *
 * Usage:
 *   @NetworkSecured(auth = AuthType.OAUTH)
 *   @NetworkSecured(auth = AuthType.API_KEY)
 *   @NetworkSecured(auth = AuthType.BOTH)
 */
public enum AuthType {
    /** OAuth 2.0 JWT Bearer token via 'authorization' metadata header */
    OAUTH,
    /** API key via 'x-api-key' metadata header */
    API_KEY,
    /** Accept either OAuth or API key (default) */
    BOTH
}
