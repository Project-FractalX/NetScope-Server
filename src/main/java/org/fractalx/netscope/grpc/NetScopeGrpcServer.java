package org.fractalx.netscope.grpc;

import org.fractalx.netscope.config.NetScopeConfig;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class NetScopeGrpcServer {

    private static final Logger logger = LoggerFactory.getLogger(NetScopeGrpcServer.class);

    private final NetScopeConfig config;
    private final NetScopeGrpcServiceImpl grpcService;
    private Server server;

    public NetScopeGrpcServer(NetScopeConfig config, NetScopeGrpcServiceImpl grpcService) {
        this.config = config;
        this.grpcService = grpcService;
    }

    @PostConstruct
    public void start() throws IOException {
        NetScopeConfig.GrpcConfig grpcConfig = config.getGrpc();

        if (!grpcConfig.isEnabled()) {
            logger.info("NetScope gRPC server is disabled");
            return;
        }

        // Register auth interceptor — reads credentials from metadata headers
        NetScopeAuthInterceptor authInterceptor = new NetScopeAuthInterceptor();

        ServerBuilder<?> builder = ServerBuilder.forPort(grpcConfig.getPort())
                .addService(grpcService)
                .intercept(authInterceptor)           // ← auth interceptor
                .maxInboundMessageSize(grpcConfig.getMaxInboundMessageSize());

        if (grpcConfig.isEnableReflection()) {
            builder.addService(ProtoReflectionService.newInstance());
        }

        if (grpcConfig.getKeepAliveTime() > 0)
            builder.keepAliveTime(grpcConfig.getKeepAliveTime(), TimeUnit.SECONDS);
        if (grpcConfig.getKeepAliveTimeout() > 0)
            builder.keepAliveTimeout(grpcConfig.getKeepAliveTimeout(), TimeUnit.SECONDS);

        builder.permitKeepAliveWithoutCalls(grpcConfig.isPermitKeepAliveWithoutCalls());

        if (grpcConfig.getMaxConnectionIdle() > 0)
            builder.maxConnectionIdle(grpcConfig.getMaxConnectionIdle(), TimeUnit.SECONDS);
        if (grpcConfig.getMaxConnectionAge() > 0)
            builder.maxConnectionAge(grpcConfig.getMaxConnectionAge(), TimeUnit.SECONDS);

        server = builder.build().start();

        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║           NetScope gRPC Server Started                     ║");
        logger.info("╠════════════════════════════════════════════════════════════╣");
        logger.info("║  Port         : {}                                      ║", grpcConfig.getPort());
        logger.info("║  Reflection   : {}                                  ║", grpcConfig.isEnableReflection() ? "Enabled " : "Disabled");
        logger.info("║  OAuth 2.0    : {}                                  ║", config.getSecurity().getOauth().isEnabled()  ? "Enabled " : "Disabled");
        logger.info("║  API Key      : {}                                  ║", config.getSecurity().getApiKey().isEnabled() ? "Enabled " : "Disabled");
        logger.info("║  Auth via     : gRPC metadata headers                      ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { NetScopeGrpcServer.this.stop(); }
            catch (InterruptedException e) { logger.error("Shutdown error", e); }
        }));
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        if (server != null) {
            logger.info("Stopping NetScope gRPC server...");
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            logger.info("NetScope gRPC server stopped");
        }
    }

    public int getPort() { return server != null ? server.getPort() : -1; }
}
