# 🚀 High-Performance Order Processing API with Redis Multi-Tier Caching

[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot 3.2.3](https://img.shields.io/badge/Spring_Boot-3.2.3-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-7.2-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)](LICENSE)

An enterprise-grade, high-throughput e-commerce order processing pipeline engineered with **Spring Boot 3**, **Redis multi-tier caching**, **JPA Optimistic Locking (@Version)**, and **strictly-once Idempotency controls**.

---

## 🏛 Architecture Overview

```
   +-------------------------------------------------------------------------+
   |                             CLIENT LAYER                                |
   |               HTTP POST /api/v1/orders [Idempotency-Key]                |
   +------------------------------------+------------------------------------+
                                        |
                                        v
   +------------------------------------+------------------------------------+
   |                          SPRING BOOT API                                |
   |                                                                         |
   |   1. Idempotency Check (Redis SETNX key TTL 24h)                       |
   |        |--> Existing key? Return previous OrderResponse (Zero duplicate)|
   |        +--> New key? Acquire lock & proceed                             |
   |                                                                         |
   |   2. Stock Validation & Optimistic Decrement                            |
   |        |--> Query Product stock                                         |
   |        +--> Check @Version (Guarantees zero overselling during spikes)  |
   |                                                                         |
   |   3. Persistence & Cache Management                                     |
   |        |--> Persist Order & OrderItems in MySQL (ACID Transaction)      |
   |        |--> Invalidate stale product cache (@CacheEvict)                |
   |        +--> Cache Order details in Redis (@Cacheable)                   |
   +-----------------------+--------------------------+----------------------+
                           |                          |
                           v                          v
             +-------------+------------+   +---------+-----------+
             |      REDIS CACHE (7.2)   |   |   MYSQL RELATIONAL  |
             |  - Product Cache (15m)   |   |  - Orders           |
             |  - Idempotency Locks     |   |  - OrderItems       |
             |  - Sub-15ms Reads        |   |  - Products         |
             +--------------------------+   +---------------------+
```

---

## ⚡ Core Technical Innovations

1. **JPA Optimistic Locking (`@Version`) Against Flash-Sale Overselling**:
   - Stock decrements utilize version-stamped optimistic locks. When two concurrent checkout requests attempt to claim the final inventory item, the second transaction encounters an `OptimisticLockingFailureException`, preventing race conditions and phantom sales without holding heavy pessimistic database locks.

2. **Redis-Backed Idempotency Engine (`SETNX`)**:
   - Every checkout endpoint requires an `Idempotency-Key` HTTP header.
   - Using Redis atomic `setIfAbsent` with a 24-hour expiration window, network retries or accidental double-clicks return the original transaction record instantly without re-billing or double-decrementing inventory.

3. **Multi-Tier Cache-Aside Architecture**:
   - High-velocity product catalog reads bypass relational databases, serving payload directly from Redis with sub-millisecond latencies.
   - Coordinated `@CacheEvict` guarantees cache coherence immediately upon stock changes.

4. **Production Observability & RFC 7807 Compliance**:
   - Centralized `@RestControllerAdvice` formats error payloads uniformly conforming to modern enterprise API standards.
   - Spring Boot Actuator exposes health probes, memory pools, and Prometheus metrics.

---

## 🧪 Automated Test Coverage

The project includes unit and mock integration test suites using **JUnit 5**, **Mockito**, and **Spring MockMvc**:
- **Concurrency & Stock Tests**: Verifies atomic stock deduction and exception generation when requested quantity exceeds inventory.
- **Idempotency Guard Tests**: Verifies duplicate request interception and replay handling.
- **Controller Validation Tests**: Verifies Jakarta Bean Validation annotations (`@Positive`, `@Email`, `@NotEmpty`).

---

## 🛠 Quickstart & Running

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose (optional, for containerized run)

### Local Dev (In-Memory H2 & Local Redis)
```bash
# 1. Clone repository
git clone https://github.com/Yoshith-1808/high-performance-order-api.git
cd high-performance-order-api

# 2. Build & run tests
mvn clean test

# 3. Start Spring Boot application
mvn spring-boot:run
```
The application starts at `http://localhost:8080`.
- **Interactive Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **H2 Database Console**: [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
- **Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

### Running with Docker Compose
```bash
docker-compose up --build
```

---

## 📖 Sample API Usage

### 1. Create a Product
```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Mechanical Keyboard RGB",
    "sku": "KB-MECH-001",
    "price": 129.99,
    "stockQuantity": 50
  }'
```

### 2. Place an Idempotent Order
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: e82e1d74-0f2c-47b2-b7e3-0d2948bb0a11" \
  -d '{
    "customerEmail": "developer@yoshith.io",
    "items": [
      {
        "productId": 1,
        "quantity": 2
      }
    ]
  }'
```

---

## 👨‍💻 Author
**Yoshith Nagidi**
- Java Backend Architect & Distributed Systems Engineer
- GitHub: [@Yoshith-1808](https://github.com/Yoshith-1808)
- LinkedIn: [Yoshith Nagidi](https://linkedin.com/in/yoshith-nagidi-0877b8252)
