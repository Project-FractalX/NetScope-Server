package com.netscope.config;

import com.netscope.core.*;
import com.netscope.mapping.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
public class NetScopeAutoConfiguration {

    private final ApplicationContext context;

    public NetScopeAutoConfiguration(ApplicationContext context) {
        this.context = context;
    }

    @Bean
    public UrlMappingStrategy urlMappingStrategy() {
        return new DefaultUrlMappingStrategy();
    }

    @Bean
    public NetScopeScanner netScopeScanner(ApplicationContext context, UrlMappingStrategy urlMappingStrategy) {
        return new NetScopeScanner(context, urlMappingStrategy);
    }

    @Bean
    @Lazy
    public NetScopeDocController netScopeDocController(NetScopeScanner scanner) {
        return new NetScopeDocController(scanner);
    }

    @Bean
    @Lazy
    public NetScopeDynamicController netScopeDynamicController(
            NetScopeScanner scanner,
            NetScopeSecurityConfig securityConfig) {
        return new NetScopeDynamicController(scanner, securityConfig);
    }

    @Bean
    public SmartInitializingSingleton netScopeRegistrar(NetScopeScanner scanner) {
        return () -> {
            var methods = scanner.scan();
            RequestMappingHandlerMapping mapping = (RequestMappingHandlerMapping) context.getBean("requestMappingHandlerMapping");
            NetScopeRegistrar registrar = new NetScopeRegistrar(mapping);
            registrar.register(methods);
        };
    }
}