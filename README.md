# NetScope by FractalX

**NetScope** is a **Java Spring Boot library** that allows you to:

* Expose **any Spring bean method as a network-accessible API** dynamically
* Control network-level access via **global or per-method API keys**
* Automatically generate **API documentation** in JSON
* Provide a **Swagger-like interactive UI** for testing endpoints

NetScope is ideal for **internal microservice communication**, **RPC**, or **dynamic API exposure**, without writing controllers manually.

**NetScope is developed and maintained by [FractalX](https://github.com/project-FractalX).**

---

## Features

* Annotate methods with:

    * `@NetworkPublic` → fully open network endpoint
    * `@NetworkRestricted(key="optional-per-method-key")` → protected endpoint
* **Dynamic controller** automatically maps URL → method
* `/netscope/docs` → JSON listing all exposed methods
* `/netscope/docs/ui` → interactive Swagger-like UI
* Global network security via `application.properties` / YAML
* Fully compatible with Spring Boot 3+ and Java 21+

---

## Installation

1. Add the library dependency after installing to your local Maven repository (`.m2`):

```xml
<dependency>
    <groupId>com.netscope</groupId>
    <artifactId>netscope-core</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

2. Ensure you have Spring Boot Web starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

---

## Configuration

### `application.properties`

```properties
# Enable network-level security
netscope.security.enabled=true

# Global API key for all @NetworkRestricted methods without a specific key
netscope.security.apiKey=super-secret-api-key
```

### `application.yaml`

```yaml
netscope:
  security:
    enabled: true
    apiKey: super-secret-api-key
```

---

## Usage

### 1. Annotate your methods

```java
package com.example.service;

import com.netscope.annotation.NetworkPublic;
import com.netscope.annotation.NetworkRestricted;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    @NetworkPublic
    public String getCustomers() {
        return "All customers";
    }

    // Uses global API key from application.properties
    @NetworkRestricted
    public int getLoyaltyPoints(String customerId) {
        return 123;
    }

    // Custom per-method API key
    @NetworkRestricted(key="inventory-service-key")
    public int checkStock(String itemId) {
        return 42;
    }
}
```

---

### 2. Access dynamically exposed endpoints

| HTTP Path                                    | Description                | Security                         |
| -------------------------------------------- | -------------------------- | -------------------------------- |
| `/netscope/CustomerService/getCustomers`     | Calls `getCustomers()`     | Public                           |
| `/netscope/CustomerService/getLoyaltyPoints` | Calls `getLoyaltyPoints()` | Requires global API key          |
| `/netscope/CustomerService/checkStock`       | Calls `checkStock()`       | Requires `inventory-service-key` |

**Example curl:**

```bash
# Public method
curl http://localhost:8080/netscope/CustomerService/getCustomers

# Restricted method with global key
curl -H "X-API-KEY: super-secret-api-key" \
     http://localhost:8080/netscope/CustomerService/getLoyaltyPoints

# Restricted method with custom key
curl -H "X-API-KEY: inventory-service-key" \
     http://localhost:8080/netscope/CustomerService/checkStock
```

---

### 3. API Documentation

**JSON endpoint:** `/netscope/docs`

Example response:

```json
[
  {
    "beanName": "CustomerService",
    "methodName": "getCustomers",
    "path": "/netscope/CustomerService/getCustomers",
    "httpMethod": "GET",
    "restricted": false
  },
  {
    "beanName": "CustomerService",
    "methodName": "getLoyaltyPoints",
    "path": "/netscope/CustomerService/getLoyaltyPoints",
    "httpMethod": "GET",
    "restricted": true
  },
  {
    "beanName": "CustomerService",
    "methodName": "checkStock",
    "path": "/netscope/CustomerService/checkStock",
    "httpMethod": "GET",
    "restricted": true
  }
]
```

---

### 4. Interactive UI

**UI endpoint:** `/netscope/docs/ui`

* Clickable interface for testing all exposed methods
* Shows **bean name, method, path, HTTP method, restricted status**
* `@NetworkRestricted` methods require the **correct X-API-KEY** to execute

---

### 5. Optional Customizations

* Implement `UrlMappingStrategy` to **customize URL patterns**
* Add interceptors for JWT / OAuth if you want advanced authentication
* Highlight restricted methods in the UI without exposing keys

---

### 6. Quick Start

1. Add the dependency
2. Annotate methods with `@NetworkPublic` or `@NetworkRestricted(key="...")`
3. Configure API keys in `application.properties` or YAML
4. Start the Spring Boot app
5. Access endpoints via `/netscope/{bean}/{method}`
6. Check `/netscope/docs` or `/netscope/docs/ui`

---

### 7. Key Advantages

* Fully automatic: **no manual controllers needed**
* Works for **any Spring-managed bean** (`@Service`, `@Component`, `@Controller`)
* Network-level access control is **built into the library**
* Lightweight Swagger-like docs UI
* Fully compatible with Spring Boot 3+ and Java 21+

---

NetScope makes **any method a network API**, with **optional per-method or global network restrictions**, **dynamic discovery**, and **interactive documentation** — perfect for internal microservices, dynamic RPC, or distributed systems APIs.

---

---

## Contributing
Contributions are welcome! Please fork the repository and submit a pull request with your changes.

---

## Authors
- Sathnindu Kottage - Initial work - [sathninduk](https://github.com/sathninduk)
- Contributors - See the [contributors](CONTRIBUTORS.md) file for a full list of contributors.

---

## License
NetScope is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.