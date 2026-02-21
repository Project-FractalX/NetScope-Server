# NetScope gRPC v2.0 - Pure gRPC with OAuth 2.0

**NetScope** is a **pure gRPC Spring Boot library** that automatically exposes Spring bean methods as gRPC endpoints with **OAuth 2.0 authentication**.

## 🚀 What's New in v2.0

- ✅ **Pure gRPC** - REST support completely removed
- ✅ **OAuth 2.0 Authentication** - JWT-based security with scope validation
- ✅ **Simplified Response** - `result_json` as the main response field
- ✅ **gRPC Status Codes** - Proper status code management
- ✅ **Fully Configurable** - All parameters via application.properties/yaml
- ✅ **Production Ready** - Enterprise-grade security and performance

---

## 📦 Installation

### 1. Install to Local Maven Repository

```bash
mvn clean install
```

### 2. Add Dependency

```xml
<dependency>
    <groupId>com.netscope</groupId>
    <artifactId>netscope-grpc</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

---

## ⚙️ Configuration

All settings are configurable via `application.properties` or `application.yaml`:

### Complete Configuration Example

```yaml
netscope:
  # gRPC Server Configuration
  grpc:
    enabled: true
    port: 9090
    maxInboundMessageSize: 4194304  # 4MB
    maxConcurrentCallsPerConnection: 100
    keepAliveTime: 300              # seconds
    keepAliveTimeout: 20            # seconds
    permitKeepAliveWithoutCalls: false
    maxConnectionIdle: 0            # 0 = infinite
    maxConnectionAge: 0             # 0 = infinite
    enableReflection: true

  # OAuth 2.0 Security Configuration
  security:
    enabled: true
    issuerUri: https://auth.example.com
    jwkSetUri: https://auth.example.com/.well-known/jwks.json
    audiences:
      - https://api.example.com
      - my-api-audience
    tokenCacheDuration: 300         # seconds
    clockSkew: 60                   # seconds
    allowPublicMethods: true
    requireHttps: false

  # Service Discovery Configuration
  discovery:
    enabled: true
    basePackages:
      - com.example.service
      - com.myapp.api
    includeParameterNames: true
    includeReturnTypes: true
```

### Minimal Configuration

```yaml
netscope:
  grpc:
    port: 9090
  security:
    issuerUri: https://your-auth-server.com
    jwkSetUri: https://your-auth-server.com/.well-known/jwks.json
    audiences:
      - your-api-audience
```

---

## 💻 Usage

### 1. Annotate Your Methods

```java
package com.example.service;

import com.netscope.annotation.NetworkPublic;
import com.netscope.annotation.NetworkSecured;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    // Public endpoint - no authentication required
    @NetworkPublic(description = "Get all customer IDs")
    public List<String> listCustomerIds() {
        return List.of("CUST001", "CUST002", "CUST003");
    }

    // Secured endpoint - requires OAuth token with specific scope
    @NetworkSecured(
        scopes = {"read:customers"},
        description = "Get customer details by ID"
    )
    public Customer getCustomer(String customerId) {
        // Business logic
        return new Customer(customerId, "John Doe");
    }

    // Multiple scopes - requires ALL scopes
    @NetworkSecured(
        scopes = {"read:customers", "read:orders"},
        requireAllScopes = true,
        description = "Get customer with full details"
    )
    public CustomerFull getCustomerFull(String customerId) {
        // Business logic
        return new CustomerFull(/*...*/);
    }

    // Multiple scopes - requires ANY scope
    @NetworkSecured(
        scopes = {"admin", "super:user"},
        requireAllScopes = false,
        description = "Delete customer"
    )
    public boolean deleteCustomer(String customerId) {
        // Admin operation
        return true;
    }
}
```

---

## 🔐 OAuth 2.0 Authentication

### Token Format

NetScope expects **JWT Bearer tokens** with the following structure:

```json
{
  "iss": "https://auth.example.com",
  "sub": "user123",
  "aud": ["https://api.example.com"],
  "exp": 1708027200,
  "scope": "read:customers write:customers admin"
}
```

### Scope Formats Supported

Both formats are supported:

1. **Space-separated string** (OAuth 2.0 standard):
   ```json
   {
     "scope": "read:customers write:customers admin"
   }
   ```

2. **Array format** (Azure AD, Auth0):
   ```json
   {
     "scp": ["read:customers", "write:customers", "admin"]
   }
   ```

---

## 📡 gRPC API Usage

### Protocol Buffer Definition

```protobuf
message InvokeRequest {
  string bean_name = 1;           // e.g., "CustomerService"
  string method_name = 2;         // e.g., "getCustomer"
  string arguments_json = 3;      // JSON array: ["CUST001"]
  string access_token = 4;        // OAuth 2.0 JWT token
  repeated string parameter_types = 5;
}

message InvokeResponse {
  string result_json = 1;         // Direct JSON result
}
```

### Java Client Example

```java
import com.netscope.grpc.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class NetScopeClient {
    public static void main(String[] args) {
        // Create channel
        ManagedChannel channel = ManagedChannelBuilder
            .forAddress("localhost", 9090)
            .usePlaintext()
            .build();

        NetScopeServiceGrpc.NetScopeServiceBlockingStub stub = 
            NetScopeServiceGrpc.newBlockingStub(channel);

        // Get OAuth token (from your auth server)
        String accessToken = getAccessToken();

        // Call secured method
        InvokeRequest request = InvokeRequest.newBuilder()
            .setBeanName("CustomerService")
            .setMethodName("getCustomer")
            .setArgumentsJson("[\"CUST001\"]")
            .setAccessToken(accessToken)
            .build();

        InvokeResponse response = stub.invokeMethod(request);
        System.out.println("Result: " + response.getResultJson());

        channel.shutdown();
    }

    private static String getAccessToken() {
        // Implement OAuth 2.0 client credentials or authorization code flow
        // This depends on your OAuth provider (Keycloak, Auth0, Azure AD, etc.)
        return "eyJhbGciOiJSUzI1NiIs...";
    }
}
```

### Python Client Example

```python
import grpc
from netscope_pb2 import InvokeRequest
from netscope_pb2_grpc import NetScopeServiceStub

# Create channel
channel = grpc.insecure_channel('localhost:9090')
stub = NetScopeServiceStub(channel)

# Get OAuth token
access_token = get_access_token()  # Your OAuth implementation

# Call method
request = InvokeRequest(
    bean_name="CustomerService",
    method_name="getCustomer",
    arguments_json='["CUST001"]',
    access_token=access_token
)

response = stub.InvokeMethod(request)
print(f"Result: {response.result_json}")
```

### Using grpcurl

```bash
# Public method (no token)
grpcurl -plaintext -d '{
  "bean_name": "CustomerService",
  "method_name": "listCustomerIds",
  "arguments_json": "[]"
}' localhost:9090 netscope.NetScopeService/InvokeMethod

# Secured method (with token)
grpcurl -plaintext -d '{
  "bean_name": "CustomerService",
  "method_name": "getCustomer",
  "arguments_json": "[\"CUST001\"]",
  "access_token": "eyJhbGciOiJSUzI1NiIs..."
}' localhost:9090 netscope.NetScopeService/InvokeMethod
```

---

## 📊 gRPC Status Codes

NetScope uses standard gRPC status codes:

| Status | When It's Used |
|--------|----------------|
| `OK` | Successful invocation |
| `NOT_FOUND` | Method not found |
| `UNAUTHENTICATED` | Missing or invalid token |
| `PERMISSION_DENIED` | Insufficient scopes |
| `INVALID_ARGUMENT` | Invalid method arguments |
| `INTERNAL` | Server error during invocation |

---

## 🔧 OAuth 2.0 Setup Examples

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

### Custom JWT Issuer

```yaml
netscope:
  security:
    issuerUri: https://your-auth-server.com
    jwkSetUri: https://your-auth-server.com/.well-known/jwks.json
    audiences:
      - your-api-audience
    clockSkew: 60
    tokenCacheDuration: 300
```

---

## 🎯 Advanced Features

### Scope Validation Modes

#### Require ALL Scopes (AND)
```java
@NetworkSecured(
    scopes = {"read:customers", "read:orders"},
    requireAllScopes = true  // Must have BOTH scopes
)
```

#### Require ANY Scope (OR)
```java
@NetworkSecured(
    scopes = {"admin", "super:admin"},
    requireAllScopes = false  // Must have AT LEAST ONE
)
```

### Streaming Support

```java
// Client-side streaming
StreamObserver<InvokeRequest> requestStream = 
    asyncStub.invokeMethodStream(new StreamObserver<InvokeResponse>() {
        @Override
        public void onNext(InvokeResponse response) {
            System.out.println("Result: " + response.getResultJson());
        }

        @Override
        public void onError(Throwable t) {
            System.err.println("Error: " + t.getMessage());
        }

        @Override
        public void onCompleted() {
            System.out.println("Stream completed");
        }
    });

// Send multiple requests
for (int i = 0; i < 10; i++) {
    InvokeRequest request = InvokeRequest.newBuilder()
        .setBeanName("DataService")
        .setMethodName("processData")
        .setArgumentsJson("[" + i + "]")
        .setAccessToken(token)
        .build();
    requestStream.onNext(request);
}
requestStream.onCompleted();
```

### Package Filtering

Limit scanning to specific packages:

```yaml
netscope:
  discovery:
    basePackages:
      - com.example.api
      - com.example.service
```

### Disable Security (Development Only)

```yaml
netscope:
  security:
    enabled: false  # WARNING: Only for development!
```

---

## 🔍 Monitoring & Logging

### Enable Debug Logging

```yaml
logging:
  level:
    com.netscope: DEBUG
    io.grpc: INFO
```

### Suppress gRPC Transport Errors

```yaml
logging:
  level:
    io.grpc.netty.shaded.io.netty.handler.codec.http2: ERROR
```

---

## 🚨 Security Best Practices

1. **Always use HTTPS in production**
   ```yaml
   netscope:
     security:
       requireHttps: true
   ```

2. **Use short-lived tokens**
   - Recommended: 15-60 minutes
   - Configure in your OAuth server

3. **Implement token refresh**
   - Clients should handle token expiration
   - Refresh before expiration

4. **Use specific scopes**
   - Don't use broad scopes like `*` or `admin`
   - Define fine-grained permissions

5. **Validate audiences**
   ```yaml
   netscope:
     security:
       audiences:
         - https://your-specific-api.com
   ```

6. **Monitor failed authentications**
   - Set up alerts for UNAUTHENTICATED/PERMISSION_DENIED

---

## 📈 Performance Tuning

### Connection Settings

```yaml
netscope:
  grpc:
    maxConcurrentCallsPerConnection: 100
    maxConnectionIdle: 300
    maxConnectionAge: 3600
    keepAliveTime: 300
    keepAliveTimeout: 20
```

### Message Size

```yaml
netscope:
  grpc:
    maxInboundMessageSize: 10485760  # 10MB for large payloads
```

### Token Caching

```yaml
netscope:
  security:
    tokenCacheDuration: 300  # Cache validated tokens for 5 minutes
```

---

## 🛠️ Troubleshooting

### Issue: "UNAUTHENTICATED: Authentication failed"

**Check:**
1. Token is valid and not expired
2. Token issuer matches configuration
3. Token audience matches configuration
4. JWK Set URI is accessible

### Issue: "PERMISSION_DENIED: Insufficient scopes"

**Check:**
1. Token contains required scopes
2. Scope format (space-separated vs array)
3. `requireAllScopes` setting

### Issue: "NOT_FOUND: Method not found"

**Check:**
1. Bean name is exact (case-sensitive)
2. Method name is exact (case-sensitive)
3. Method has `@NetworkPublic` or `@NetworkSecured` annotation
4. Service is a Spring bean (`@Service`, `@Component`)

---

## 📚 Migration from v1.x

### Changes

| v1.x | v2.0 |
|------|------|
| REST + gRPC | gRPC Only |
| API Keys | OAuth 2.0 |
| `@NetworkRestricted(key="...")` | `@NetworkSecured(scopes={...})` |
| Multi-field response | `result_json` only |
| HTTP status codes | gRPC status codes |

### Migration Steps

1. **Update annotations:**
   ```java
   // Before
   @NetworkRestricted(key = "my-key")
   
   // After
   @NetworkSecured(scopes = {"read:resource"})
   ```

2. **Update configuration:**
   ```yaml
   # Before
   netscope:
     security:
       apiKey: my-secret-key
   
   # After
   netscope:
     security:
       issuerUri: https://auth.example.com
       jwkSetUri: https://auth.example.com/.well-known/jwks.json
   ```

3. **Update clients:**
   - Replace API key with OAuth token
   - Use only `result_json` field from response

---

## 📄 License

Apache License 2.0

## 👥 Authors

- **Sathnindu Kottage** - [@sathninduk](https://github.com/sathninduk)
- **FractalX Team** - [https://github.com/project-FractalX](https://github.com/project-FractalX)

---

**NetScope v2.0 - Pure gRPC with Enterprise Security** 🚀
