# NetScope gRPC

**NetScope** is a Spring Boot library that automatically exposes annotated bean **methods and fields** as gRPC endpoints with built-in OAuth 2.0 and API key authentication.

---

## Features

- Expose bean methods and fields over gRPC with a single annotation
- Dual authentication: OAuth 2.0 JWT and/or API key per member
- Read and write field attributes remotely via dedicated RPCs
- Inherited field scanning across the full class hierarchy
- Static and final field awareness
- Bidirectional streaming support
- Live introspection via `GetDocs` RPC

---

## Installation

### 1. Build and install locally

```bash
mvn clean install
```

### 2. Add dependency

```xml
<dependency>
    <groupId>com.netscope</groupId>
    <artifactId>netscope-grpc</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

---

## Configuration

```yaml
netscope:
  grpc:
    enabled: true
    port: 9090
    maxInboundMessageSize: 4194304        # 4 MB
    maxConcurrentCallsPerConnection: 100
    keepAliveTime: 300                    # seconds
    keepAliveTimeout: 20
    permitKeepAliveWithoutCalls: false
    maxConnectionIdle: 0                  # 0 = infinite
    maxConnectionAge: 0
    enableReflection: true

  security:
    enabled: true
    issuerUri: https://auth.example.com
    jwkSetUri: https://auth.example.com/.well-known/jwks.json
    audiences:
      - https://api.example.com
    tokenCacheDuration: 300               # seconds
    clockSkew: 60
    apiKey: your-secret-api-key
```

### Minimal configuration

```yaml
netscope:
  grpc:
    port: 9090
  security:
    issuerUri: https://your-auth-server.com
    jwkSetUri: https://your-auth-server.com/.well-known/jwks.json
    audiences:
      - your-api-audience
    apiKey: your-api-key
```

---

## Annotations

### `@NetworkPublic`

No authentication required. Usable on methods and fields.

```java
@NetworkPublic(description = "Current app version")
public String getVersion() {
    return "2.0.0";
}

@NetworkPublic(description = "Build identifier")
public static final String BUILD_ID = "abc123";   // readable, not writable (final)

@NetworkPublic(description = "Feature flag")
private boolean maintenanceMode = false;           // readable and writable
```

### `@NetworkSecured`

Requires authentication. The `auth` parameter is mandatory and selects which credential type is accepted.

| `auth` value | Accepted credential |
|---|---|
| `AuthType.OAUTH` | OAuth 2.0 JWT Bearer token only |
| `AuthType.API_KEY` | API key only |
| `AuthType.BOTH` | Either OAuth or API key |

```java
@NetworkSecured(auth = AuthType.OAUTH, description = "Get customer by ID")
public Customer getCustomer(String customerId) { ... }

@NetworkSecured(auth = AuthType.API_KEY, description = "Delete customer")
public void deleteCustomer(String customerId) { ... }  // returns {"status":"accepted"}

@NetworkSecured(auth = AuthType.BOTH, description = "Internal request counter")
private int requestCount = 0;                          // readable and writable

@NetworkSecured(auth = AuthType.OAUTH, description = "Secret token")
private static final String SECRET = "tok_abc";        // readable, not writable (final)
```

### Annotating fields vs methods

| Annotation target | Behaviour |
|---|---|
| Method | Callable via `InvokeMethod` RPC |
| Non-final field | Readable via `InvokeMethod`, writable via `SetAttribute` |
| Final field | Readable via `InvokeMethod` only — writes are rejected |
| Static field/method | Supported; no bean instance required |
| Inherited field | Scanned automatically up the full superclass chain |

---

## gRPC API

### Protocol Buffer definition

```protobuf
enum MemberKind {
  METHOD = 0;
  FIELD  = 1;
}

// Invoke a method or read a field value
message InvokeRequest {
  string bean_name                    = 1;
  string member_name                  = 2;
  google.protobuf.ListValue arguments = 3;  // empty for fields and no-arg methods
  repeated string parameter_types     = 4;
}

message InvokeResponse {
  google.protobuf.Value result = 1;  // any JSON type: object, array, primitive, null
}

// Write a value to a non-final field
message SetAttributeRequest {
  string bean_name      = 1;
  string attribute_name = 2;
  google.protobuf.Value value = 3;
}

message SetAttributeResponse {
  google.protobuf.Value previous_value = 1;  // value before the write
}

// Introspect all exposed members
message MethodInfo {
  string bean_name                  = 1;
  string member_name                = 2;
  bool   secured                    = 3;
  string return_type                = 4;
  repeated ParameterInfo parameters = 5;
  repeated string required_scopes   = 6;
  string description                = 7;
  MemberKind kind                   = 8;  // METHOD or FIELD
  bool writeable                    = 9;  // true for non-final fields
  bool is_static                    = 10;
  bool is_final                     = 11;
}

service NetScopeService {
  rpc InvokeMethod       (InvokeRequest)           returns (InvokeResponse);
  rpc SetAttribute       (SetAttributeRequest)      returns (SetAttributeResponse);
  rpc GetDocs            (DocsRequest)              returns (DocsResponse);
  rpc InvokeMethodStream (stream InvokeRequest)     returns (stream InvokeResponse);
}
```

### Authentication headers

Credentials are passed as gRPC metadata headers — never in the request message body.

| Header | Value | Used for |
|---|---|---|
| `authorization` | `Bearer <jwt>` | OAuth 2.0 token |
| `x-api-key` | `<key>` | API key |

---

## Usage examples

### grpcurl

```bash
# Read a public field
grpcurl -plaintext -d '{
  "bean_name": "AppService",
  "member_name": "appVersion"
}' localhost:9090 netscope.NetScopeService/InvokeMethod

# Invoke a secured method (OAuth)
grpcurl -plaintext \
  -H 'authorization: Bearer eyJhbGci...' \
  -d '{
    "bean_name": "CustomerService",
    "member_name": "getCustomer",
    "arguments": [{"string_value": "CUST001"}]
  }' localhost:9090 netscope.NetScopeService/InvokeMethod

# Write a field value (API key auth)
grpcurl -plaintext \
  -H 'x-api-key: your-api-key' \
  -d '{
    "bean_name": "AppService",
    "attribute_name": "maintenanceMode",
    "value": {"bool_value": true}
  }' localhost:9090 netscope.NetScopeService/SetAttribute

# Introspect all exposed members
grpcurl -plaintext -d '{}' localhost:9090 netscope.NetScopeService/GetDocs
```

### Java client

```java
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("localhost", 9090)
    .usePlaintext()
    .build();

NetScopeServiceGrpc.NetScopeServiceBlockingStub stub =
    NetScopeServiceGrpc.newBlockingStub(channel);

// Add OAuth token as metadata
Metadata headers = new Metadata();
headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
            "Bearer " + accessToken);
stub = MetadataUtils.attachHeaders(stub, headers);

// Invoke a method
InvokeRequest methodRequest = InvokeRequest.newBuilder()
    .setBeanName("CustomerService")
    .setMemberName("getCustomer")
    .setArguments(ListValue.newBuilder()
        .addValues(Value.newBuilder().setStringValue("CUST001")))
    .build();

InvokeResponse response = stub.invokeMethod(methodRequest);
System.out.println(response.getResult());

// Read a field attribute
InvokeRequest fieldRead = InvokeRequest.newBuilder()
    .setBeanName("AppService")
    .setMemberName("maintenanceMode")
    .build();

InvokeResponse fieldResponse = stub.invokeMethod(fieldRead);
System.out.println(fieldResponse.getResult());  // e.g. false

// Write a field attribute
SetAttributeRequest writeRequest = SetAttributeRequest.newBuilder()
    .setBeanName("AppService")
    .setAttributeName("maintenanceMode")
    .setValue(Value.newBuilder().setBoolValue(true))
    .build();

SetAttributeResponse writeResponse = stub.setAttribute(writeRequest);
System.out.println("Previous: " + writeResponse.getPreviousValue());  // false

channel.shutdown();
```

### Python client

```python
import grpc
from google.protobuf import struct_pb2
from netscope_pb2 import InvokeRequest, SetAttributeRequest
from netscope_pb2_grpc import NetScopeServiceStub

channel = grpc.insecure_channel('localhost:9090')
stub = NetScopeServiceStub(channel)

# OAuth metadata
metadata = [('authorization', f'Bearer {access_token}')]

# Invoke a method
request = InvokeRequest(
    bean_name="CustomerService",
    member_name="getCustomer"
)
request.arguments.values.append(struct_pb2.Value(string_value="CUST001"))
response = stub.InvokeMethod(request, metadata=metadata)
print(response.result)

# Write a field
write = SetAttributeRequest(
    bean_name="AppService",
    attribute_name="maintenanceMode",
    value=struct_pb2.Value(bool_value=True)
)
wr = stub.SetAttribute(write, metadata=[('x-api-key', 'your-api-key')])
print("Previous:", wr.previous_value)
```

### Streaming

```java
NetScopeServiceGrpc.NetScopeServiceStub asyncStub =
    NetScopeServiceGrpc.newStub(channel);

StreamObserver<InvokeRequest> requestStream =
    asyncStub.invokeMethodStream(new StreamObserver<InvokeResponse>() {
        @Override public void onNext(InvokeResponse r)  { System.out.println(r.getResult()); }
        @Override public void onError(Throwable t)      { t.printStackTrace(); }
        @Override public void onCompleted()              { System.out.println("done"); }
    });

for (int i = 0; i < 10; i++) {
    requestStream.onNext(InvokeRequest.newBuilder()
        .setBeanName("DataService")
        .setMemberName("processItem")
        .setArguments(ListValue.newBuilder()
            .addValues(Value.newBuilder().setNumberValue(i)))
        .build());
}
requestStream.onCompleted();
```

---

## gRPC status codes

| Status | When |
|---|---|
| `OK` | Successful invocation or write |
| `NOT_FOUND` | Bean or member name not found |
| `UNAUTHENTICATED` | Missing or invalid credentials |
| `PERMISSION_DENIED` | Wrong credential type for the member |
| `FAILED_PRECONDITION` | Attempt to write a `final` field |
| `INVALID_ARGUMENT` | Wrong number of arguments, or calling `SetAttribute` on a method |
| `INTERNAL` | Unexpected server error |

---

## OAuth 2.0 provider examples

### Keycloak

```yaml
netscope:
  security:
    issuerUri: https://keycloak.example.com/realms/myrealm
    jwkSetUri: https://keycloak.example.com/realms/myrealm/protocol/openid-connect/certs
    audiences:
      - account
```

### Auth0

```yaml
netscope:
  security:
    issuerUri: https://your-tenant.auth0.com/
    jwkSetUri: https://your-tenant.auth0.com/.well-known/jwks.json
    audiences:
      - https://your-api.example.com
```

### Azure AD

```yaml
netscope:
  security:
    issuerUri: https://login.microsoftonline.com/{tenant-id}/v2.0
    jwkSetUri: https://login.microsoftonline.com/{tenant-id}/discovery/v2.0/keys
    audiences:
      - api://{client-id}
```

---

## Monitoring and logging

```yaml
logging:
  level:
    com.netscope: DEBUG
    io.grpc: INFO
    io.grpc.netty.shaded.io.netty.handler.codec.http2: ERROR
```

---

## Troubleshooting

### `NOT_FOUND: Member not found`
- Bean name and member name are case-sensitive
- The field or method must have `@NetworkPublic` or `@NetworkSecured`
- The bean must be a Spring-managed component (`@Service`, `@Component`, etc.)

### `UNAUTHENTICATED`
- Check that the `authorization` or `x-api-key` header is present in the request metadata
- Verify the JWT is not expired and its issuer/audience match configuration

### `FAILED_PRECONDITION: Attribute is final`
- `SetAttribute` cannot write to `final` fields; use `InvokeMethod` to read them

### `INVALID_ARGUMENT` on `SetAttribute`
- You called `SetAttribute` with a method name — use `InvokeMethod` for methods

---

## Security best practices

- Always enable TLS in production
- Use short-lived JWT tokens (15–60 minutes) and implement refresh on the client
- Prefer `AuthType.OAUTH` for user-facing endpoints and `AuthType.API_KEY` for service-to-service
- Never annotate fields that hold credentials or internal secrets as `@NetworkPublic`
- Use `final` to mark attributes that must never be remotely modified

---

## License

Apache License 2.0

## Authors

- **Sathnindu Kottage** - [@sathninduk](https://github.com/sathninduk)
- **FractalX Team** - [https://github.com/project-FractalX](https://github.com/project-FractalX)
