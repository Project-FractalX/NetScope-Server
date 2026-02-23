# FractalX NetScope Server — gRPC Remote Access for Spring Beans

**NetScope** lets you expose any Spring bean method or field as a gRPC endpoint by adding a single annotation. Authentication (OAuth 2.0 JWT and/or API key) is handled automatically.

---

## Table of Contents

- [How it works](#how-it-works)
- [Quick Start](#quick-start)
- [Installation](#installation)
- [Annotating your beans](#annotating-your-beans)
- [Configuration](#configuration)
- [Calling the service](#calling-the-service)
  - [grpcurl](#grpcurl)
  - [Java client](#java-client)
  - [Python client](#python-client)
  - [Bidirectional streaming](#bidirectional-streaming)
- [Passing arguments](#passing-arguments)
  - [Primitives](#primitives)
  - [Objects (POJOs)](#objects-pojos)
  - [Overloaded methods](#overloaded-methods)
- [Authentication](#authentication)
- [Reading and writing fields](#reading-and-writing-fields)
- [Live introspection (GetDocs)](#live-introspection-getdocs)
- [gRPC status codes](#grpc-status-codes)
- [OAuth 2.0 provider examples](#oauth-20-provider-examples)
- [Troubleshooting](#troubleshooting)
- [Security best practices](#security-best-practices)

---

## How it works

```
Your Spring bean                  NetScope                    gRPC caller
─────────────────                 ────────                    ───────────
@NetworkPublic                    scans beans on startup      grpcurl / Java / Python
public String getVersion()   →    registers endpoint      →   InvokeMethod("AppService", "getVersion")
                                  validates auth              ← "2.0.0"
```

Three steps to use it:

1. **Annotate** — add `@NetworkPublic` or `@NetworkSecured` to the methods/fields you want to expose
2. **Configure** — set a port and (optionally) your auth provider in `application.yml`
3. **Call** — use any gRPC client, grpcurl, or the generated stub to invoke your beans remotely

---

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>com.netscope</groupId>
    <artifactId>netscope</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Annotate a bean

```java
@Service
public class AppService {

    @NetworkPublic(description = "Current version")
    public String getVersion() {
        return "2.0.0";
    }

    @NetworkSecured(auth = AuthType.API_KEY, description = "Restart the service")
    public String restart() {
        // ...
        return "restarted";
    }
}
```

### 3. Set a port

```yaml
netscope:
  grpc:
    port: 9090
```

### 4. Call it

```bash
grpcurl -plaintext \
  -d '{"bean_name": "AppService", "member_name": "getVersion"}' \
  localhost:9090 netscope.NetScopeService/InvokeMethod
```

```json
{ "result": "2.0.0" }
```

---

## Installation

Build and install the library to your local Maven repository:

```bash
mvn clean install
```

Then add the dependency shown in the Quick Start above.

---

## Annotating your beans

### `@NetworkPublic` — no authentication required

```java
@Service
public class AppService {

    @NetworkPublic(description = "Current app version")
    public String getVersion() {
        return "2.0.0";
    }

    @NetworkPublic(description = "Feature flag — readable and writable remotely")
    private boolean maintenanceMode = false;

    @NetworkPublic(description = "Build ID — readable only because it is final")
    public static final String BUILD_ID = "abc123";
}
```

### `@NetworkSecured` — requires a credential

The `auth` parameter controls which credential type is accepted:

| `auth` value | Accepted credential |
|---|---|
| `AuthType.OAUTH` | OAuth 2.0 JWT Bearer token |
| `AuthType.API_KEY` | API key |
| `AuthType.BOTH` | Either OAuth or API key |

```java
@Service
public class CustomerService {

    @NetworkSecured(auth = AuthType.OAUTH, description = "Look up a customer")
    public Customer getCustomer(String customerId) { ... }

    @NetworkSecured(auth = AuthType.API_KEY, description = "Delete a customer")
    public void deleteCustomer(String customerId) { ... }

    @NetworkSecured(auth = AuthType.BOTH, description = "Request counter — readable and writable")
    private int requestCount = 0;
}
```

### What can be annotated

| Target | Behaviour |
|---|---|
| Method | Callable via `InvokeMethod` |
| Overloaded methods | All overloads registered; correct one is chosen automatically from argument types |
| `Mono` / `Flux` / `CompletableFuture` return | Unwrapped automatically before the result is returned |
| Non-final field | Readable via `InvokeMethod`, writable via `SetAttribute` |
| Final field | Readable via `InvokeMethod` only — write attempts are rejected |
| Static field or method | Supported |
| Inherited field or method | Scanned automatically up the full superclass chain |
| Interface method | Annotate on the interface — implementing classes don't need to repeat it |

### Interface name aliases

If your bean implements a user-defined interface, you can use **either** the concrete class name or the interface name as `bean_name`:

```java
public interface CustomerService {
    @NetworkPublic
    CustomerResDTO getCustomers();
}

@Service
public class CustomerServiceImpl implements CustomerService {
    // No need to repeat @NetworkPublic here
    public CustomerResDTO getCustomers() { ... }
}
```

```bash
# Both work:
"bean_name": "CustomerServiceImpl"
"bean_name": "CustomerService"
```

Startup log confirms the alias was registered:
```
[method] CustomerServiceImpl.getCustomers → PUBLIC
[alias]  CustomerService → CustomerServiceImpl (1 member)
```

> **Note:** `GetDocs` returns members under the concrete class name only. Standard Java/Spring interfaces (`Serializable`, `ApplicationContextAware`, etc.) are never aliased.

---

## Configuration

### Minimal (OAuth + API key)

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

### Full reference

```yaml
netscope:
  grpc:
    enabled: true
    port: 9090
    maxInboundMessageSize: 4194304        # bytes (default 4 MB)
    maxConcurrentCallsPerConnection: 100
    keepAliveTime: 300                    # seconds
    keepAliveTimeout: 20
    permitKeepAliveWithoutCalls: false
    maxConnectionIdle: 0                  # 0 = unlimited
    maxConnectionAge: 0
    enableReflection: true

  security:
    enabled: true
    issuerUri: https://auth.example.com
    jwkSetUri: https://auth.example.com/.well-known/jwks.json
    audiences:
      - https://api.example.com
    tokenCacheDuration: 300               # seconds to cache validated tokens
    clockSkew: 60                         # seconds of allowed clock drift
    apiKey: your-secret-api-key
```

---

## Calling the service

All four RPCs share the same service:

| RPC | Use for |
|---|---|
| `InvokeMethod` | Call a method or read a field |
| `SetAttribute` | Write a value to a non-final field |
| `GetDocs` | List all exposed members and their signatures |
| `InvokeMethodStream` | Bidirectional streaming — many requests, many responses |

### grpcurl

> Arguments are plain JSON. Strings, numbers, booleans, objects, and arrays are written naturally — no type wrappers needed.

```bash
# Call a public method
grpcurl -plaintext \
  -d '{"bean_name": "AppService", "member_name": "getVersion"}' \
  localhost:9090 netscope.NetScopeService/InvokeMethod

# Call a method with arguments (OAuth)
grpcurl -plaintext \
  -H 'authorization: Bearer eyJhbGci...' \
  -d '{
    "bean_name": "CustomerService",
    "member_name": "getCustomer",
    "arguments": ["CUST001"]
  }' localhost:9090 netscope.NetScopeService/InvokeMethod

# Call a method with multiple arguments
grpcurl -plaintext \
  -H 'authorization: Bearer eyJhbGci...' \
  -d '{
    "bean_name": "OrderService",
    "member_name": "placeOrder",
    "arguments": ["CUST001", 99.99, true]
  }' localhost:9090 netscope.NetScopeService/InvokeMethod

# Read all exposed members
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

// Attach an OAuth token
Metadata headers = new Metadata();
headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
            "Bearer " + accessToken);
stub = MetadataUtils.attachHeaders(stub, headers);

// Call a method
InvokeRequest request = InvokeRequest.newBuilder()
    .setBeanName("CustomerService")
    .setMemberName("getCustomer")
    .setArguments(ListValue.newBuilder()
        .addValues(Value.newBuilder().setStringValue("CUST001")))
    .build();

InvokeResponse response = stub.invokeMethod(request);
System.out.println(response.getResult());

channel.shutdown();
```

### Python client

```python
import grpc
from google.protobuf import struct_pb2
from netscope_pb2 import InvokeRequest
from netscope_pb2_grpc import NetScopeServiceStub

channel = grpc.insecure_channel('localhost:9090')
stub = NetScopeServiceStub(channel)

metadata = [('authorization', f'Bearer {access_token}')]

request = InvokeRequest(bean_name="CustomerService", member_name="getCustomer")
request.arguments.values.append(struct_pb2.Value(string_value="CUST001"))

response = stub.InvokeMethod(request, metadata=metadata)
print(response.result)
```

### Bidirectional streaming

Send multiple requests over a single connection and receive responses as they complete.

```java
NetScopeServiceGrpc.NetScopeServiceStub asyncStub = NetScopeServiceGrpc.newStub(channel);

StreamObserver<InvokeRequest> requestStream =
    asyncStub.invokeMethodStream(new StreamObserver<>() {
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

## Passing arguments

### Primitives

Arguments are a JSON array. Each element maps to a method parameter in order.

```bash
# String
"arguments": ["CUST001"]

# Number
"arguments": [42]

# Boolean
"arguments": [true]

# Multiple mixed arguments
"arguments": ["CUST001", 99.99, true]

# Null
"arguments": [null]
```

### Objects (POJOs)

Pass a Java object as a JSON object. NetScope deserializes it into the target class automatically — field names must match the Java class fields (or `@JsonProperty` aliases).

```bash
grpcurl -plaintext \
  -H 'authorization: Bearer eyJhbGci...' \
  -d '{
    "bean_name": "OrderService",
    "member_name": "createOrder",
    "arguments": [
      {"customerId": "CUST001", "amount": 99.99, "express": true}
    ]
  }' localhost:9090 netscope.NetScopeService/InvokeMethod
```

**Java client:**

```java
Struct orderStruct = Struct.newBuilder()
    .putFields("customerId", Value.newBuilder().setStringValue("CUST001").build())
    .putFields("amount",     Value.newBuilder().setNumberValue(99.99).build())
    .putFields("express",    Value.newBuilder().setBoolValue(true).build())
    .build();

InvokeRequest request = InvokeRequest.newBuilder()
    .setBeanName("OrderService")
    .setMemberName("createOrder")
    .setArguments(ListValue.newBuilder()
        .addValues(Value.newBuilder().setStructValue(orderStruct)))
    .build();
```

**Python client:**

```python
order = struct_pb2.Struct()
order.update({"customerId": "CUST001", "amount": 99.99, "express": True})

request = InvokeRequest(bean_name="OrderService", member_name="createOrder")
request.arguments.values.append(struct_pb2.Value(struct_value=order))
```

**Tips for complex types:**
- **Nested object** — use a nested JSON object inside the parent: `{"address": {"street": "123 Main St", "city": "NY"}}`
- **List of objects** — use a JSON array: `[{"id": 1}, {"id": 2}]`

### Overloaded methods

NetScope automatically picks the right overload by matching argument types. Most of the time you don't need to do anything extra.

```bash
# NetScope infers: process(String) because the argument is a string
"member_name": "process",
"arguments": ["ORD001"]
```

**When automatic inference isn't enough** — if two overloads are equally compatible (e.g. `process(int)` vs `process(long)`, both numeric), add `parameter_types`:

```bash
grpcurl -plaintext \
  -H 'authorization: Bearer eyJhbGci...' \
  -d '{
    "bean_name": "OrderService",
    "member_name": "process",
    "parameter_types": ["int"],
    "arguments": [42]
  }' localhost:9090 netscope.NetScopeService/InvokeMethod
```

Use the exact type names shown by `GetDocs` (`ParameterInfo.type`) for `parameter_types`.

---

## Authentication

Credentials go in **gRPC metadata headers**, not in the request body.

| Header | Example value | Used for |
|---|---|---|
| `authorization` | `Bearer eyJhbGci...` | OAuth 2.0 JWT |
| `x-api-key` | `your-api-key` | API key |

```bash
# OAuth
grpcurl -plaintext -H 'authorization: Bearer eyJhbGci...' ...

# API key
grpcurl -plaintext -H 'x-api-key: your-api-key' ...
```

---

## Reading and writing fields

### Read a field

Use `InvokeMethod` the same way you'd call a method — just omit `arguments`:

```bash
grpcurl -plaintext \
  -d '{"bean_name": "AppService", "member_name": "maintenanceMode"}' \
  localhost:9090 netscope.NetScopeService/InvokeMethod
```

```json
{ "result": false }
```

### Write a field

Use `SetAttribute`. The response includes the previous value.

```bash
grpcurl -plaintext \
  -H 'x-api-key: your-api-key' \
  -d '{
    "bean_name": "AppService",
    "attribute_name": "maintenanceMode",
    "value": true
  }' localhost:9090 netscope.NetScopeService/SetAttribute
```

```json
{ "previousValue": false }
```

> `final` fields are read-only. `SetAttribute` on a final field returns `FAILED_PRECONDITION`.

---

## Live introspection (GetDocs)

`GetDocs` returns every exposed member with its full signature:

```bash
grpcurl -plaintext -d '{}' localhost:9090 netscope.NetScopeService/GetDocs
```

Each entry includes:

| Field | Meaning |
|---|---|
| `bean_name` | Spring bean name to use in requests |
| `member_name` | Method or field name |
| `kind` | `METHOD` or `FIELD` |
| `return_type` | Java return type |
| `parameters` | Parameter names, types, and positions |
| `secured` | `true` if authentication is required |
| `writeable` | `true` for non-final fields |
| `is_static` | `true` for static members |
| `is_final` | `true` for final fields |
| `description` | Text from the annotation's `description` |

---

## gRPC status codes

| Status | When |
|---|---|
| `OK` | Success |
| `NOT_FOUND` | Bean or member name not found in the registry |
| `UNAUTHENTICATED` | Missing or invalid credential |
| `PERMISSION_DENIED` | Wrong credential type (e.g. API key sent to an OAuth-only method) |
| `FAILED_PRECONDITION` | Attempt to write a `final` field |
| `INVALID_ARGUMENT` | Wrong number of arguments; `SetAttribute` called on a method; ambiguous overload that couldn't be resolved automatically |
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

## Troubleshooting

### `NOT_FOUND: Member not found`

- Bean name and member name are **case-sensitive**
- The method or field must have `@NetworkPublic` or `@NetworkSecured`
- The bean must be a Spring-managed component (`@Service`, `@Component`, etc.)
- You can use either the concrete class name (`CustomerServiceImpl`) or the interface name (`CustomerService`) as `bean_name` — check the startup log for registered aliases

### `UNAUTHENTICATED`

- Confirm the `authorization` or `x-api-key` header is present in the request **metadata** (not the body)
- Check that the JWT is not expired and its issuer/audience match your `application.yml`

### `FAILED_PRECONDITION: Attribute is final`

- `SetAttribute` cannot write to `final` fields; use `InvokeMethod` to read them

### `INVALID_ARGUMENT` on `SetAttribute`

- You used a method name — `SetAttribute` is for fields only; use `InvokeMethod` for methods

### `INVALID_ARGUMENT: Ambiguous method`

- Multiple overloads matched and automatic inference couldn't pick one (e.g. `process(int)` vs `process(long)` — both accept a numeric argument)
- Add `parameter_types` with the exact type name from `GetDocs`: `"parameter_types": ["int"]`

### Enable debug logging

```yaml
logging:
  level:
    com.netscope: DEBUG
    io.grpc: INFO
```

---

## Security best practices

- Always enable TLS in production
- Use short-lived JWT tokens (15–60 minutes) with token refresh on the client
- Prefer `AuthType.OAUTH` for user-facing endpoints and `AuthType.API_KEY` for service-to-service calls
- Never annotate fields that hold credentials or secrets with `@NetworkPublic`
- Mark attributes that must not be modified remotely as `final`

---

## License

Apache License 2.0

## Authors

- **Sathnindu Kottage** — [@sathninduk](https://github.com/sathninduk)
- **FractalX Team** — [https://github.com/project-FractalX](https://github.com/project-FractalX)
