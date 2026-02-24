package org.fractalx.netscope.server.grpc;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC server interceptor that reads credentials from metadata headers
 * and stores them in the gRPC Context for downstream use.
 *
 * Supported headers:
 *   authorization: Bearer <jwt>   ← OAuth 2.0 (standard HTTP Authorization header)
 *   x-api-key: <key>              ← API key
 */
public class NetScopeAuthInterceptor implements ServerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(NetScopeAuthInterceptor.class);

    // Metadata keys (gRPC headers are lowercase)
    public static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> API_KEY_HEADER =
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

    // Context keys — used to pass credentials to the service handler
    public static final Context.Key<String> ACCESS_TOKEN_CTX =
            Context.key("netscope.access_token");

    public static final Context.Key<String> API_KEY_CTX =
            Context.key("netscope.api_key");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        // Read Authorization header — support "Bearer <token>" and raw token
        String accessToken = null;
        String authHeader = headers.get(AUTHORIZATION_KEY);
        if (authHeader != null && !authHeader.isBlank()) {
            accessToken = authHeader.startsWith("Bearer ")
                    ? authHeader.substring(7).trim()
                    : authHeader.trim();
        }

        // Read x-api-key header
        String apiKey = headers.get(API_KEY_HEADER);
        if (apiKey != null) apiKey = apiKey.trim();

        logger.debug("NetScope interceptor → auth={} api_key={}",
                accessToken != null ? "[present]" : "[absent]",
                apiKey      != null ? "[present]" : "[absent]");

        // Store credentials in Context so the service handler can read them
        Context ctx = Context.current()
                .withValue(ACCESS_TOKEN_CTX, accessToken != null ? accessToken : "")
                .withValue(API_KEY_CTX,      apiKey      != null ? apiKey      : "");

        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
