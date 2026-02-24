package org.fractalx.netscope.grpc;

import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import org.fractalx.netscope.core.AmbiguousInvocationException;
import org.fractalx.netscope.core.NetScopeInvoker;
import org.fractalx.netscope.core.NetScopeScanner;
import org.fractalx.netscope.grpc.proto.*;
import org.fractalx.netscope.model.NetworkMethodDefinition;
import org.fractalx.netscope.model.NetworkMethodDefinition.ParameterInfo;
import org.fractalx.netscope.security.OAuth2AuthorizationService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    // ── Proto ↔ JSON helpers ──────────────────────────────────────────────────

    private String toArgumentsJson(com.google.protobuf.ListValue listValue) {
        if (listValue == null || listValue.getValuesCount() == 0) return "[]";
        try {
            Value arrayValue = Value.newBuilder().setListValue(listValue).build();
            return JsonFormat.printer().omittingInsignificantWhitespace().print(arrayValue);
        } catch (Exception e) {
            logger.warn("Could not serialize arguments ListValue to JSON: {}", e.getMessage());
            return "[]";
        }
    }

    private String toValueJson(Value value) {
        if (value == null) return "null";
        try {
            return JsonFormat.printer().omittingInsignificantWhitespace().print(value);
        } catch (Exception e) {
            logger.warn("Could not serialize Value to JSON: {}", e.getMessage());
            return "null";
        }
    }

    private Value toProtoValue(String json) {
        if (json == null || json.equals("null")) {
            return Value.newBuilder().setNullValue(com.google.protobuf.NullValue.NULL_VALUE).build();
        }
        try {
            Value.Builder b = Value.newBuilder();
            JsonFormat.parser().merge(json, b);
            return b.build();
        } catch (Exception e) {
            logger.warn("Could not parse result as JSON, returning as string: {}", e.getMessage());
            return Value.newBuilder().setStringValue(json).build();
        }
    }

    // ── RPC handlers ──────────────────────────────────────────────────────────

    @Override
    public void invokeMethod(InvokeRequest request, StreamObserver<InvokeResponse> responseObserver) {
        try {
            String accessToken = NetScopeAuthInterceptor.ACCESS_TOKEN_CTX.get();
            String apiKey      = NetScopeAuthInterceptor.API_KEY_CTX.get();

            NetworkMethodDefinition method = resolve(request, responseObserver);
            if (method == null) return;

            try {
                authService.authorize(method, accessToken, apiKey);
            } catch (io.grpc.StatusRuntimeException e) {
                responseObserver.onError(e);
                return;
            }

            String resultJson = invoker.invoke(method, toArgumentsJson(request.getArguments()));
            responseObserver.onNext(InvokeResponse.newBuilder()
                    .setResult(toProtoValue(resultJson)).build());
            responseObserver.onCompleted();

        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            logger.error("Error invoking {}.{}", request.getBeanName(), request.getMemberName(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Invocation error: " + e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void setAttribute(SetAttributeRequest request,
                             StreamObserver<SetAttributeResponse> responseObserver) {
        try {
            String accessToken = NetScopeAuthInterceptor.ACCESS_TOKEN_CTX.get();
            String apiKey      = NetScopeAuthInterceptor.API_KEY_CTX.get();

            Optional<NetworkMethodDefinition> defOpt =
                    scanner.findMethod(request.getBeanName(), request.getAttributeName(), List.of());

            if (defOpt.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Attribute not found: "
                                + request.getBeanName() + "." + request.getAttributeName())
                        .asRuntimeException());
                return;
            }

            NetworkMethodDefinition def = defOpt.get();

            if (!def.isField()) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription(request.getAttributeName() + " is a method, not an attribute. "
                                + "Use InvokeMethod to call methods.")
                        .asRuntimeException());
                return;
            }

            if (!def.isWriteable()) {
                responseObserver.onError(Status.FAILED_PRECONDITION
                        .withDescription("Attribute is final and cannot be written: "
                                + def.getBeanName() + "." + def.getMethodName())
                        .asRuntimeException());
                return;
            }

            try {
                authService.authorize(def, accessToken, apiKey);
            } catch (io.grpc.StatusRuntimeException e) {
                responseObserver.onError(e);
                return;
            }

            String previousJson = invoker.write(def, toValueJson(request.getValue()));
            responseObserver.onNext(SetAttributeResponse.newBuilder()
                    .setPreviousValue(toProtoValue(previousJson)).build());
            responseObserver.onCompleted();

        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            logger.error("Error writing attribute {}.{}",
                    request.getBeanName(), request.getAttributeName(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Write error: " + e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getDocs(DocsRequest request, StreamObserver<DocsResponse> responseObserver) {
        try {
            DocsResponse.Builder response = DocsResponse.newBuilder();
            for (NetworkMethodDefinition member : scanner.scan()) {
                MemberKind kind = member.isField() ? MemberKind.FIELD : MemberKind.METHOD;
                MethodInfo.Builder info = MethodInfo.newBuilder()
                        .setBeanName(member.getBeanName())
                        .setMemberName(member.getMethodName())
                        .setSecured(member.isSecured())
                        .setReturnType(member.getReturnType())
                        .setDescription(member.getDescription())
                        .addAllRequiredScopes(member.getRequiredScopes())
                        .setKind(kind)
                        .setWriteable(member.isWriteable())
                        .setIsStatic(member.isStatic())
                        .setIsFinal(member.isFinal());
                for (ParameterInfo p : member.getParameters()) {
                    info.addParameters(org.fractalx.netscope.grpc.proto.ParameterInfo.newBuilder()
                            .setName(p.getName()).setType(p.getType()).setIndex(p.getIndex())
                            .build());
                }
                response.addMethods(info.build());
            }
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to get docs: " + e.getMessage()).asRuntimeException());
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
                    NetworkMethodDefinition method = resolve(request, responseObserver);
                    if (method == null) return;
                    authService.authorize(method, accessToken, apiKey);
                    String resultJson = invoker.invoke(method, toArgumentsJson(request.getArguments()));
                    responseObserver.onNext(InvokeResponse.newBuilder()
                            .setResult(toProtoValue(resultJson)).build());
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

    // ── Overload resolution ───────────────────────────────────────────────────

    /**
     * Resolves the correct NetworkMethodDefinition for a request, using two strategies:
     *
     *   1. Exact lookup — uses parameter_types from the request if provided.
     *   2. Type inference — when the call is ambiguous, narrows candidates by matching
     *      each argument's protobuf Value kind against the Java parameter type.
     *      Falls back to INVALID_ARGUMENT only when inference still leaves multiple matches.
     *
     * Returns null and writes the error to responseObserver on failure.
     */
    private NetworkMethodDefinition resolve(InvokeRequest request,
                                            StreamObserver<InvokeResponse> responseObserver) {
        Optional<NetworkMethodDefinition> methodOpt;
        try {
            methodOpt = scanner.findMethod(request.getBeanName(), request.getMemberName(),
                    request.getParameterTypesList());
        } catch (AmbiguousInvocationException e) {
            // Exact lookup was ambiguous — try to narrow by argument value types
            methodOpt = inferOverload(e.getCandidates(), request.getArguments().getValuesList());
            if (methodOpt.isEmpty()) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription(e.getMessage()).asRuntimeException());
                return null;
            }
        }

        if (methodOpt.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Member not found: "
                            + request.getBeanName() + "." + request.getMemberName())
                    .asRuntimeException());
            return null;
        }
        return methodOpt.get();
    }

    /**
     * Narrows overload candidates by checking whether each candidate's parameter types
     * are compatible with the protobuf Value kinds of the supplied arguments.
     *
     * Compatible mappings:
     *   string_value  → String, CharSequence
     *   number_value  → int, long, double, float, short, byte (and boxed), Number, BigDecimal, BigInteger
     *   bool_value    → boolean, Boolean
     *   struct_value  → any non-primitive, non-String, non-collection type (POJOs, Maps)
     *   list_value    → List, Collection, Set, arrays
     *   null_value    → any reference type (not primitives)
     *   Object        → compatible with any value kind
     *
     * Returns the single matching candidate, or empty if zero or multiple still match.
     */
    private Optional<NetworkMethodDefinition> inferOverload(
            List<NetworkMethodDefinition> candidates, List<Value> argValues) {

        List<NetworkMethodDefinition> matching = candidates.stream()
                .filter(def -> isCompatible(def.getParameters(), argValues))
                .collect(Collectors.toList());

        return matching.size() == 1 ? Optional.of(matching.get(0)) : Optional.empty();
    }

    private boolean isCompatible(ParameterInfo[] params, List<Value> argValues) {
        if (params.length != argValues.size()) return false;
        for (int i = 0; i < params.length; i++) {
            if (!kindCompatible(params[i].getType(), argValues.get(i).getKindCase())) {
                return false;
            }
        }
        return true;
    }

    private boolean kindCompatible(String javaType, Value.KindCase kind) {
        if (javaType.equals("Object")) return true;  // Object accepts anything
        return switch (kind) {
            case STRING_VALUE -> isStringType(javaType);
            case NUMBER_VALUE -> isNumericType(javaType);
            case BOOL_VALUE   -> isBoolType(javaType);
            case STRUCT_VALUE -> isStructType(javaType);
            case LIST_VALUE   -> isListType(javaType);
            case NULL_VALUE   -> !isPrimitive(javaType);
            default           -> false;
        };
    }

    private boolean isStringType(String t) {
        return t.equals("String") || t.equals("CharSequence");
    }

    private boolean isNumericType(String t) {
        return switch (t) {
            case "int", "Integer", "long", "Long", "double", "Double",
                 "float", "Float", "short", "Short", "byte", "Byte",
                 "Number", "BigDecimal", "BigInteger" -> true;
            default -> false;
        };
    }

    private boolean isBoolType(String t) {
        return t.equals("boolean") || t.equals("Boolean");
    }

    private boolean isListType(String t) {
        return switch (t) {
            case "List", "ArrayList", "LinkedList",
                 "Collection", "Set", "HashSet", "LinkedHashSet" -> true;
            default -> t.endsWith("[]");
        };
    }

    private boolean isStructType(String t) {
        return !isStringType(t) && !isNumericType(t) && !isBoolType(t) && !isListType(t);
    }

    private boolean isPrimitive(String t) {
        return switch (t) {
            case "int", "long", "double", "float", "boolean", "short", "byte", "char" -> true;
            default -> false;
        };
    }
}
