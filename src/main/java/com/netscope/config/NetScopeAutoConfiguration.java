package com.netscope.config;

import com.netscope.core.NetScopeInvoker;
import com.netscope.core.NetScopeScanner;
import com.netscope.grpc.NetScopeGrpcServer;
import com.netscope.grpc.NetScopeGrpcServiceImpl;
import com.netscope.security.ApiKeyValidator;
import com.netscope.security.OAuth2AuthorizationService;
import com.netscope.security.OAuth2TokenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class NetScopeAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(NetScopeAutoConfiguration.class);
    static final String NS_CONFIG = "netscope.internal.config";

    public NetScopeAutoConfiguration() {
        logger.info("NetScope Auto-Configuration initialized");
    }

    // ── Config ────────────────────────────────────────────────────────────────

    @Bean(NS_CONFIG)
    @ConfigurationProperties(prefix = "netscope")
    @ConditionalOnMissingBean(name = NS_CONFIG)
    public NetScopeConfig netScopeInternalConfig() {
        return new NetScopeConfig();
    }

    // ── Core ──────────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public NetScopeScanner netScopeScanner(
            ApplicationContext context,
            @Qualifier(NS_CONFIG) NetScopeConfig config) {
        return new NetScopeScanner(context, config);
    }

    @Bean
    @ConditionalOnMissingBean
    public NetScopeInvoker netScopeInvoker() {
        return new NetScopeInvoker();
    }

    // ── Security: OAuth 2.0 ───────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "netscope.security.oauth.enabled", havingValue = "true")
    public OAuth2TokenValidator oAuth2TokenValidator(
            @Qualifier(NS_CONFIG) NetScopeConfig config) {
        return new OAuth2TokenValidator(config);
    }

    // ── Security: API Key ─────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "netscope.security.api-key.enabled", havingValue = "true")
    public ApiKeyValidator apiKeyValidator(
            @Qualifier(NS_CONFIG) NetScopeConfig config) {
        return new ApiKeyValidator(config);
    }

    // ── Authorization Service (wires both validators together) ────────────────

    /**
     * If EITHER or BOTH security methods are enabled, wire the authorization service.
     * Validators that are disabled will simply be null — handled gracefully.
     */
    @Bean
    @ConditionalOnMissingBean
    public OAuth2AuthorizationService oAuth2AuthorizationService(
            @Qualifier(NS_CONFIG) NetScopeConfig config,
            // Spring injects null if bean doesn't exist (required=false via Optional pattern)
            org.springframework.beans.factory.ObjectProvider<OAuth2TokenValidator> oauthProvider,
            org.springframework.beans.factory.ObjectProvider<ApiKeyValidator> apiKeyProvider) {
        return new OAuth2AuthorizationService(
                config,
                oauthProvider.getIfAvailable(),   // null if OAuth disabled
                apiKeyProvider.getIfAvailable()    // null if API key disabled
        );
    }

    // ── gRPC Server ───────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "netscope.grpc.enabled", havingValue = "true", matchIfMissing = true)
    public NetScopeGrpcServiceImpl netScopeGrpcService(
            NetScopeScanner scanner,
            NetScopeInvoker invoker,
            OAuth2AuthorizationService authService) {
        return new NetScopeGrpcServiceImpl(scanner, invoker, authService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "netscope.grpc.enabled", havingValue = "true", matchIfMissing = true)
    public NetScopeGrpcServer netScopeGrpcServer(
            @Qualifier(NS_CONFIG) NetScopeConfig config,
            NetScopeGrpcServiceImpl grpcService) {
        return new NetScopeGrpcServer(config, grpcService);
    }
}
