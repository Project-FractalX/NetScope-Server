# NetScope by FractalX

**NetScope** is a **Java Spring Boot library** that automatically exposes Spring bean methods as network-accessible APIs via **both REST and gRPC**.

* Expose **any Spring bean method** dynamically over REST and gRPC
* Control access via **global or per-method API keys**
* **Automatic API documentation** with interactive UI
* **Zero manual configuration** - just annotate your methods
* **Production-ready** with full authentication support

NetScope is perfect for **microservice communication**, **RPC**, **distributed systems**, and **dynamic API exposure**.

**NetScope is developed and maintained by [FractalX](https://github.com/project-FractalX).**

---

## 🚀 Features

### Core Capabilities
* **Dual Protocol Support**: Every method works with both REST HTTP and gRPC
* **Two Annotations**:
  * `@NetworkPublic` → Fully open network endpoint
  * `@NetworkRestricted(key="...")` → Protected endpoint requiring authentication
* **Dynamic Discovery**: Automatically finds and exposes annotated methods
* **Interactive Documentation**:
  * `/netscope/docs` → JSON API documentation
  * `/netscope/docs/ui` → Web-based testing interface
* **Flexible Security**: Global or per-method API key authentication
* **Streaming Support**: gRPC bidirectional streaming for real-time communication

### Protocol Features

| Feature | REST | gRPC |
|---------|------|------|
| Method Invocation | ✅ | ✅ |
| Authentication | ✅ | ✅ |
| Documentation | ✅ | ✅ |
| Streaming | ❌ | ✅ |
| Protocol Reflection | ❌ | ✅ |
| Binary Efficiency | ❌ | ✅ |

---

## 📦 Installation

### 1. Install to Local Maven Repository

```bash
# Clone the repository
git clone https://github.com/project-FractalX/netscope-grpc.git
cd netscope-grpc

# Install to local .m2 repository
mvn clean install
```

### 2. Add Dependency to Your Project

```xml
<dependency>
    <groupId>com.netscope</groupId>
    <artifactId>netscope-grpc</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 3. Ensure Spring Boot Web Starter

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

---

## ⚙️ Configuration

### application.properties

```properties
# REST Server (Spring Boot default)
server.port=8080

# gRPC Server
netscope.grpc.enabled=true
netscope.grpc.port=9090
netscope.grpc.maxInboundMessageSize=4194304

# Security
netscope.security.enabled=true
netscope.security.apiKey=super-secret-global-key
```

### application.yaml

```yaml
server:
  port: 8080

netscope:
  grpc:
    enabled: true
    port: 9090
    maxInboundMessageSize: 4194304  # 4MB
  security:
    enabled: true
    apiKey: super-secret-global-key
```

---

## 💻 Usage

### 1. Annotate Your Methods

```java
package com.example.service;

import com.netscope.annotation.NetworkPublic;
import com.netscope.annotation.NetworkRestricted;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    // Publicly accessible via REST and gRPC
    @NetworkPublic
    public String getCustomers() {
        return "All customers";
    }

    // Requires global API key
    @NetworkRestricted
    public int getLoyaltyPoints(String customerId) {
        // Business logic
        return 1250;
    }

    // Requires method-specific API key
    @NetworkRestricted(key = "inventory-service-key")
    public int checkStock(String itemId) {
        // Business logic
        return 42;
    }

    // REST-only endpoint
    @NetworkPublic(enableGrpc = false)
    public String getWebOnlyData() {
        return "REST only";
    }

    // gRPC-only endpoint
    @NetworkRestricted(key = "grpc-key", enableRest = false)
    public String getGrpcOnlyData() {
        return "gRPC only";
    }
}
```

---

## 🌐 REST API Usage

### Public Endpoint

```bash
curl http://localhost:8080/netscope/CustomerService/getCustomers
```

### Restricted Endpoint (Global Key)

```bash
curl -H "X-API-KEY: super-secret-global-key" \
     http://localhost:8080/netscope/CustomerService/getLoyaltyPoints \
     -d '["CUST123"]'
```

### Restricted Endpoint (Method-Specific Key)

```bash
curl -H "X-API-KEY: inventory-service-key" \
     http://localhost:8080/netscope/CustomerService/checkStock \
     -d '["ITEM456"]'
```

### API Documentation

```bash
# Get JSON documentation
curl http://localhost:8080/netscope/docs

# Open interactive UI in browser
open http://localhost:8080/netscope/docs/ui
```

---

## 🔌 gRPC API Usage

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

        // Create stub
        NetScopeServiceGrpc.NetScopeServiceBlockingStub stub = 
            NetScopeServiceGrpc.newBlockingStub(channel);

        // Call public method
        GenericRequest publicRequest = GenericRequest.newBuilder()
            .setBeanName("CustomerService")
            .setMethodName("getCustomers")
            .setArgumentsJson("[]")
            .build();

        GenericResponse response = stub.invokeMethod(publicRequest);
        System.out.println("Result: " + response.getResultJson());

        // Call restricted method
        GenericRequest restrictedRequest = GenericRequest.newBuilder()
            .setBeanName("CustomerService")
            .setMethodName("getLoyaltyPoints")
            .setArgumentsJson("[\"CUST123\"]")
            .setApiKey("super-secret-global-key")
            .build();

        GenericResponse response2 = stub.invokeMethod(restrictedRequest);
        System.out.println("Points: " + response2.getResultJson());

        // Get documentation
        DocsRequest docsRequest = DocsRequest.newBuilder().build();
        DocsResponse docs = stub.getDocs(docsRequest);
        
        for (MethodInfo method : docs.getMethodsList()) {
            System.out.println(method.getBeanName() + "." + method.getMethodName());
        }

        channel.shutdown();
    }
}
```

### Python Client Example

```python
import grpc
from netscope_pb2 import GenericRequest, DocsRequest
from netscope_pb2_grpc import NetScopeServiceStub

# Create channel
channel = grpc.insecure_channel('localhost:9090')
stub = NetScopeServiceStub(channel)

# Call public method
request = GenericRequest(
    bean_name="CustomerService",
    method_name="getCustomers",
    arguments_json="[]"
)
response = stub.InvokeMethod(request)
print(f"Result: {response.result_json}")

# Call restricted method
request = GenericRequest(
    bean_name="CustomerService",
    method_name="getLoyaltyPoints",
    arguments_json='["CUST123"]',
    api_key="super-secret-global-key"
)
response = stub.InvokeMethod(request)
print(f"Points: {response.result_json}")

# Get documentation
docs_request = DocsRequest()
docs = stub.GetDocs(docs_request)
for method in docs.methods:
    print(f"{method.bean_name}.{method.method_name}")
```

### Using grpcurl (Command Line)

```bash
# List services
grpcurl -plaintext localhost:9090 list

# Describe service
grpcurl -plaintext localhost:9090 describe netscope.NetScopeService

# Call public method
grpcurl -plaintext -d '{
  "bean_name": "CustomerService",
  "method_name": "getCustomers",
  "arguments_json": "[]"
}' localhost:9090 netscope.NetScopeService/InvokeMethod

# Call restricted method
grpcurl -plaintext -d '{
  "bean_name": "CustomerService",
  "method_name": "getLoyaltyPoints",
  "arguments_json": "[\"CUST123\"]",
  "api_key": "super-secret-global-key"
}' localhost:9090 netscope.NetScopeService/InvokeMethod

# Get documentation
grpcurl -plaintext -d '{}' localhost:9090 netscope.NetScopeService/GetDocs
```

---

## 🔐 Security Patterns

### 1. Global Security

All restricted methods use the same key:

```properties
netscope.security.enabled=true
netscope.security.apiKey=global-master-key
```

### 2. Per-Service Keys

Different keys for different services:

```java
@Service
public class PaymentService {
    @NetworkRestricted(key = "payment-service-key")
    public Receipt processPayment(Payment payment) {
        // ...
    }
}

@Service
public class UserService {
    @NetworkRestricted(key = "user-service-key")
    public User getUser(String id) {
        // ...
    }
}
```

### 3. Mixed Security

Public and restricted methods in the same service:

```java
@Service
public class ProductService {
    @NetworkPublic
    public List<Product> getPublicProducts() {
        // Anyone can call
    }

    @NetworkRestricted
    public List<Product> getAdminProducts() {
        // Requires authentication
    }
}
```

---

## 📊 Advanced Features

### Streaming (gRPC Only)

```java
// Client-side streaming example
StreamObserver<GenericRequest> requestStream = 
    asyncStub.invokeMethodStream(new StreamObserver<GenericResponse>() {
        @Override
        public void onNext(GenericResponse response) {
            System.out.println("Response: " + response.getResultJson());
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
    GenericRequest request = GenericRequest.newBuilder()
        .setBeanName("DataService")
        .setMethodName("processData")
        .setArgumentsJson("[" + i + "]")
        .build();
    requestStream.onNext(request);
}
requestStream.onCompleted();
```

### Custom URL Mapping

```java
@Configuration
public class CustomMappingConfig {
    @Bean
    public UrlMappingStrategy customUrlMappingStrategy() {
        return (beanClass, method) -> {
            return "/api/v1/" + 
                   beanClass.getSimpleName().toLowerCase() + "/" + 
                   method.getName();
        };
    }
}
```

---

## 🎯 Use Cases

### 1. Microservice Communication

Replace REST with gRPC for efficient inter-service communication:

```java
@Service
public class OrderService {
    @NetworkRestricted(key = "internal-service-key", enableRest = false)
    public Order createOrder(OrderRequest request) {
        // Internal service communication via gRPC only
    }
}
```

### 2. External API + Internal RPC

Public REST API for external clients, gRPC for internal:

```java
@Service
public class UserService {
    @NetworkPublic(enableGrpc = false)  // REST only for external
    public UserProfile getPublicProfile(String userId) {
        // Public REST API
    }

    @NetworkRestricted(key = "internal", enableRest = false)  // gRPC only
    public UserDetails getInternalDetails(String userId) {
        // Internal service communication
    }
}
```

### 3. Dynamic Service Registry

Automatically discover all available services:

```java
@Service
public class ServiceDiscovery {
    @Autowired
    private NetScopeScanner scanner;

    public List<String> listAllServices() {
        return scanner.scan().stream()
            .map(m -> m.getBeanName() + "." + m.getMethodName())
            .collect(Collectors.toList());
    }
}
```

---

## 🛠️ Development & Testing

### Running Tests

```bash
mvn test
```

### Building the Library

```bash
mvn clean package
```

### Installing Locally

```bash
mvn clean install
```

### Protocol Buffer Compilation

The proto files are automatically compiled during the Maven build. Generated files are in `target/generated-sources/protobuf/`.

---

## 📋 System Requirements

* Java 21+
* Spring Boot 3.2+
* Maven 3.6+ (for building)
* gRPC 1.61+ (included as dependency)

---

## 🔧 Troubleshooting

### gRPC Server Not Starting

Check if the port is available:
```bash
lsof -i :9090
```

Enable gRPC explicitly:
```properties
netscope.grpc.enabled=true
```

### Authentication Failures

Verify your API key configuration:
```bash
curl -v -H "X-API-KEY: your-key" http://localhost:8080/netscope/docs
```

Check logs for authentication details.

### Method Not Found

Ensure your service is a Spring bean (`@Service`, `@Component`, etc.) and the method has the correct annotation.

---

## 📚 API Reference

### Annotations

#### @NetworkPublic
```java
@NetworkPublic(
    path = "",                  // Custom REST path (optional)
    method = RequestMethod.GET, // HTTP method (default: GET)
    enableRest = true,          // Enable REST endpoint (default: true)
    enableGrpc = true           // Enable gRPC endpoint (default: true)
)
```

#### @NetworkRestricted
```java
@NetworkRestricted(
    key = "",                   // Per-method API key (optional, uses global if empty)
    method = RequestMethod.GET, // HTTP method (default: GET)
    path = "",                  // Custom REST path (optional)
    enableRest = true,          // Enable REST endpoint (default: true)
    enableGrpc = true           // Enable gRPC endpoint (default: true)
)
```

### gRPC Service

```protobuf
service NetScopeService {
    rpc InvokeMethod (GenericRequest) returns (GenericResponse);
    rpc GetDocs (DocsRequest) returns (DocsResponse);
    rpc InvokeMethodStream (stream GenericRequest) returns (stream GenericResponse);
}
```

---

## 🤝 Contributing

External contributions are NOT welcomed yet! We're working on establishing our contribution guidelines.

---

## 👥 Authors

* **Sathnindu Kottage** - *Initial work* - [sathninduk](https://github.com/sathninduk)
* See [CONTRIBUTORS.md](CONTRIBUTORS.md) for the full list of contributors

---

## 📄 License

NetScope is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.

---

## 🌟 Why NetScope?

* **Zero Boilerplate**: No manual controller or service definitions
* **Polyglot**: Works with any gRPC-supported language
* **Production Ready**: Full authentication and security support
* **Developer Friendly**: Interactive documentation UI
* **Flexible**: Choose REST, gRPC, or both for each method
* **Type Safe**: Automatic parameter marshaling and type conversion
* **Discoverable**: Built-in service discovery and documentation

---

## 🔗 Links

* **GitHub**: https://github.com/project-FractalX/netscope
* **Issues**: https://github.com/project-FractalX/netscope/issues
* **Documentation**: https://netscope.fractalx.dev (coming soon)

---

**NetScope - Making every Java method a network API** 🚀
