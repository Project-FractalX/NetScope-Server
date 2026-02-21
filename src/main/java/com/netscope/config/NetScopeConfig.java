package com.netscope.config;

import java.util.ArrayList;
import java.util.List;

/**
 * NetScope configuration — plain POJO, no Spring annotations on the class.
 * Bound via @Bean + @ConfigurationProperties in NetScopeAutoConfiguration.
 */
public class NetScopeConfig {

    private final GrpcConfig grpc = new GrpcConfig();
    private final SecurityConfig security = new SecurityConfig();
    private final DiscoveryConfig discovery = new DiscoveryConfig();

    public GrpcConfig getGrpc() { return grpc; }
    public SecurityConfig getSecurity() { return security; }
    public DiscoveryConfig getDiscovery() { return discovery; }

    // ── gRPC ─────────────────────────────────────────────────────────────────

    public static class GrpcConfig {
        private boolean enabled = true;
        private int port = 9090;
        private int maxInboundMessageSize = 4194304;
        private int maxConcurrentCallsPerConnection = 100;
        private long keepAliveTime = 300;
        private long keepAliveTimeout = 20;
        private boolean permitKeepAliveWithoutCalls = false;
        private long maxConnectionIdle = 0;
        private long maxConnectionAge = 0;
        private boolean enableReflection = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public int getPort() { return port; }
        public void setPort(int v) { this.port = v; }
        public int getMaxInboundMessageSize() { return maxInboundMessageSize; }
        public void setMaxInboundMessageSize(int v) { this.maxInboundMessageSize = v; }
        public int getMaxConcurrentCallsPerConnection() { return maxConcurrentCallsPerConnection; }
        public void setMaxConcurrentCallsPerConnection(int v) { this.maxConcurrentCallsPerConnection = v; }
        public long getKeepAliveTime() { return keepAliveTime; }
        public void setKeepAliveTime(long v) { this.keepAliveTime = v; }
        public long getKeepAliveTimeout() { return keepAliveTimeout; }
        public void setKeepAliveTimeout(long v) { this.keepAliveTimeout = v; }
        public boolean isPermitKeepAliveWithoutCalls() { return permitKeepAliveWithoutCalls; }
        public void setPermitKeepAliveWithoutCalls(boolean v) { this.permitKeepAliveWithoutCalls = v; }
        public long getMaxConnectionIdle() { return maxConnectionIdle; }
        public void setMaxConnectionIdle(long v) { this.maxConnectionIdle = v; }
        public long getMaxConnectionAge() { return maxConnectionAge; }
        public void setMaxConnectionAge(long v) { this.maxConnectionAge = v; }
        public boolean isEnableReflection() { return enableReflection; }
        public void setEnableReflection(boolean v) { this.enableReflection = v; }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    public static class SecurityConfig {
        private boolean enabled = false;

        // ── OAuth 2.0 ──────────────────────────────────────────────────────
        private OAuthConfig oauth = new OAuthConfig();

        // ── API Key ────────────────────────────────────────────────────────
        private ApiKeyConfig apiKey = new ApiKeyConfig();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public OAuthConfig getOauth() { return oauth; }
        public void setOauth(OAuthConfig v) { this.oauth = v; }
        public ApiKeyConfig getApiKey() { return apiKey; }
        public void setApiKey(ApiKeyConfig v) { this.apiKey = v; }

        /** OAuth 2.0 / JWT settings */
        public static class OAuthConfig {
            private boolean enabled = false;
            private String issuerUri;
            private List<String> audiences = new ArrayList<>();
            private String jwkSetUri;
            private long tokenCacheDuration = 300;
            private long clockSkew = 60;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean v) { this.enabled = v; }
            public String getIssuerUri() { return issuerUri; }
            public void setIssuerUri(String v) { this.issuerUri = v; }
            public List<String> getAudiences() { return audiences; }
            public void setAudiences(List<String> v) { this.audiences = v; }
            public String getJwkSetUri() { return jwkSetUri; }
            public void setJwkSetUri(String v) { this.jwkSetUri = v; }
            public long getTokenCacheDuration() { return tokenCacheDuration; }
            public void setTokenCacheDuration(long v) { this.tokenCacheDuration = v; }
            public long getClockSkew() { return clockSkew; }
            public void setClockSkew(long v) { this.clockSkew = v; }
        }

        /** API Key settings */
        public static class ApiKeyConfig {
            private boolean enabled = false;
            /** List of valid API keys */
            private List<String> keys = new ArrayList<>();
            /** Optional: header name to check (for future HTTP bridge use) */
            private String headerName = "x-api-key";

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean v) { this.enabled = v; }
            public List<String> getKeys() { return keys; }
            public void setKeys(List<String> v) { this.keys = v; }
            public String getHeaderName() { return headerName; }
            public void setHeaderName(String v) { this.headerName = v; }
        }
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    public static class DiscoveryConfig {
        private List<String> basePackages = new ArrayList<>();
        private boolean enabled = true;
        private boolean includeParameterNames = true;
        private boolean includeReturnTypes = true;

        public List<String> getBasePackages() { return basePackages; }
        public void setBasePackages(List<String> v) { this.basePackages = v; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public boolean isIncludeParameterNames() { return includeParameterNames; }
        public void setIncludeParameterNames(boolean v) { this.includeParameterNames = v; }
        public boolean isIncludeReturnTypes() { return includeReturnTypes; }
        public void setIncludeReturnTypes(boolean v) { this.includeReturnTypes = v; }
    }
}
