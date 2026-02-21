package com.netscope.grpc;

import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import com.netscope.core.NetScopeInvoker;
import com.netscope.core.NetScopeScanner;
import com.netscope.grpc.proto.*;
import com.netscope.model.NetworkMethodDefinition;
import com.netscope.security.OAuth2AuthorizationService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class NetScopeGrpcServiceImpl extends NetScopeServiceGrpc.NetScopeServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(NetScopeGrpcServiceImpl.class);

    private final NetScopeScanner scanner;
    private final NetScopeInvoker invoker;
    private final OAuth2AuthorizationService authService;

    public NetScopeGrpcServiceImpl(NetScopeScanner scanner,
                                   NetScopeInvoker invoker,
                                   OAuth2AuthorizationService authService) {
        this.scanner = scanner;
        this.invoker = invoker;
        this.authService = authService;
        logger.info("NetScope gRPC service initialized");
    }

    /**
     * Converts a protobuf ListValue into a JSON array string for the invoker.
     */
    private String toArgumentsJson(com.google.protobuf.ListValue listValue) {
        if (listValue == null || listValue.getValuesCount() == 0) {
            return "[]";
        }
        try {
            Value arrayValue = Value.newBuilder().setListValue(listValue).build();
            return JsonFormat.printer().omittingInsignificantWhitespace().print(arrayValue);
        } catch (Exception e) {
            logger.warn("Could not serialize arguments ListValue to JSON: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * Converts a JSON string into a google.protobuf.Value.
     * Handles objects {}, arrays [], strings, numbers, booleans, and null.
     */
    private Value toProtoValue(String json) {
        if (json == null || json.equals("null")) {
            return Value.newBuilder().setNullValue(
                    com.google.protobuf.NullValue.NULL_VALUE).build();
        }
        try {
            Value.Builder valueBuilder = Value.newBuilder();
            JsonFormat.parser().merge(json, valueBuilder);
            return valueBuilder.build();
        } catch (Exception e) {
            // Fallback: return as plain string value
            logger.warn("Could not parse result as JSON, returning as string: {}", e.getMessage());
            return Value.newBuilder().setStringValue(json).build();
        }
    }

    @Override
    public void invokeMethod(InvokeRequest request, StreamObserver<InvokeResponse> responseObserver) {
        try {
            String accessToken = NetScopeAuthInterceptor.ACCESS_TOKEN_CTX.get();
            String apiKey      = NetScopeAuthInterceptor.API_KEY_CTX.get();

            Optional<NetworkMethodDefinition> methodOpt =
                    scanner.findMethod(request.getBeanName(), request.getMethodName());

            if (methodOpt.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Method not found: "
                                + request.getBeanName() + "." + request.getMethodName())
                        .asRuntimeException());
                return;
            }

            NetworkMethodDefinition method = methodOpt.get();

            try {
                authService.authorize(method, accessToken, apiKey);
            } catch (io.grpc.StatusRuntimeException e) {
                responseObserver.onError(e);
                return;
            }

            String resultJson = invoker.invoke(method, toArgumentsJson(request.getArguments()));

            // Convert JSON string → native protobuf Value
            InvokeResponse response = InvokeResponse.newBuilder()
                    .setResult(toProtoValue(resultJson))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            logger.error("Error invoking {}.{}", request.getBeanName(), request.getMethodName(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Invocation error: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getDocs(DocsRequest request, StreamObserver<DocsResponse> responseObserver) {
        try {
            List<NetworkMethodDefinition> methods = scanner.scan();
            DocsResponse.Builder response = DocsResponse.newBuilder();

            for (NetworkMethodDefinition method : methods) {
                MethodInfo.Builder info = MethodInfo.newBuilder()
                        .setBeanName(method.getBeanName())
                        .setMethodName(method.getMethodName())
                        .setSecured(method.isSecured())
                        .setReturnType(method.getReturnType())
                        .setDescription(method.getDescription())
                        .addAllRequiredScopes(method.getRequiredScopes());

                for (NetworkMethodDefinition.ParameterInfo p : method.getParameters()) {
                    info.addParameters(ParameterInfo.newBuilder()
                            .setName(p.getName()).setType(p.getType()).setIndex(p.getIndex())
                            .build());
                }
                response.addMethods(info.build());
            }

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to get docs: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public StreamObserver<InvokeRequest> invokeMethodStream(
            StreamObserver<InvokeResponse> responseObserver) {

        final String accessToken = NetScopeAuthInterceptor.ACCESS_TOKEN_CTX.get();
        final String apiKey      = NetScopeAuthInterceptor.API_KEY_CTX.get();

        return new StreamObserver<>() {
            @Override
            public void onNext(InvokeRequest request) {
                try {
                    Optional<NetworkMethodDefinition> methodOpt =
                            scanner.findMethod(request.getBeanName(), request.getMethodName());
                    if (methodOpt.isEmpty()) {
                        responseObserver.onError(Status.NOT_FOUND
                                .withDescription("Method not found: "
                                        + request.getBeanName() + "." + request.getMethodName())
                                .asRuntimeException());
                        return;
                    }
                    NetworkMethodDefinition method = methodOpt.get();
                    authService.authorize(method, accessToken, apiKey);
                    String resultJson = invoker.invoke(method, toArgumentsJson(request.getArguments()));
                    responseObserver.onNext(InvokeResponse.newBuilder()
                            .setResult(toProtoValue(resultJson))
                            .build());
                } catch (io.grpc.StatusRuntimeException e) {
                    responseObserver.onError(e);
                } catch (Exception e) {
                    responseObserver.onError(Status.INTERNAL
                            .withDescription("Invocation error: " + e.getMessage())
                            .asRuntimeException());
                }
            }

            @Override public void onError(Throwable t) { logger.error("Stream error", t); }
            @Override public void onCompleted() { responseObserver.onCompleted(); }
        };
    }
}
