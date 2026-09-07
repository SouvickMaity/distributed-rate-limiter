# 🚦 Distributed Token Bucket Rate Limiter

A **distributed API rate limiter** built using **Spring Cloud Gateway, Redis, Lua scripting, and Java**.

The system protects downstream APIs by limiting the number of requests a client can make within a given time period. Redis provides centralized shared state, while a Redis Lua script ensures that token refill and consumption happen **atomically**, making the rate limiter safe for concurrent requests and multiple Gateway instances.

---

## 🏗️ Architecture

```text
                    Client
                       │
                       │ HTTP Request
                       ▼
            ┌──────────────────────┐
            │  Spring Cloud Gateway │
            └──────────┬───────────┘
                       │
                       ▼
            ┌──────────────────────┐
            │ Token Bucket Filter  │
            └──────────┬───────────┘
                       │
                       │ clientId / IP
                       ▼
            ┌──────────────────────┐
            │  RateLimiterService  │
            └──────────┬───────────┘
                       │
                       ▼
            ┌──────────────────────┐
            │ RedisTokenBucket     │
            │ Service              │
            └──────────┬───────────┘
                       │
                       │ Lua Script
                       ▼
                 ┌───────────┐
                 │   Redis   │
                 └─────┬─────┘
                       │
             ┌─────────┴─────────┐
             │                   │
          Allowed              Rejected
             │                   │
             ▼                   ▼
       Downstream API          HTTP 429
             │
             ▼
          Client
```

---

## ✨ Features

* 🚦 Token Bucket rate limiting
* ⚡ Redis-backed distributed rate limiting
* 🔒 Atomic operations using Redis Lua scripts
* 🌐 Per-client/IP rate limiting
* 🔄 Automatic token refill
* 📈 Configurable bucket capacity
* 🧵 Safe under concurrent requests
* ☁️ Supports multiple Gateway instances
* 📊 Rate-limit response headers
* ❌ Returns `HTTP 429 Too Many Requests`
* 🔌 Redis connection pooling using Jedis
* 🚀 Lightweight Gateway-level request filtering

---

## 🛠️ Tech Stack

| Technology           | Purpose                   |
| -------------------- | ------------------------- |
| Java                 | Core programming language |
| Spring Boot          | Application framework     |
| Spring Cloud Gateway | API Gateway               |
| Redis                | Distributed token storage |
| Lua                  | Atomic Redis operations   |
| Jedis                | Redis Java client         |
| Maven                | Dependency management     |

---

# 🪣 Token Bucket Algorithm

The rate limiter uses the **Token Bucket algorithm**.

Each client gets an independent bucket.

For example:

```text
Capacity = 10 tokens
Refill Rate = 5 tokens/second
```

Initially:

```text
┌─────────────────────────────┐
│  ● ● ● ● ● ● ● ● ● ●       │
│        10 Tokens             │
└─────────────────────────────┘
```

Every accepted request consumes one token:

```text
Request 1 → 9 tokens
Request 2 → 8 tokens
Request 3 → 7 tokens
...
Request 10 → 0 tokens
```

When the bucket becomes empty:

```text
Request 11
    │
    ▼
No token available
    │
    ▼
HTTP 429 Too Many Requests
```

---

# 🔄 Token Refill

Tokens are automatically regenerated according to the configured refill rate.

With:

```text
Capacity   = 10
RefillRate = 5 tokens/sec
```

the bucket can generate:

```text
1 second → 5 tokens
2 seconds → 10 tokens
3 seconds → 10 tokens maximum
```

The bucket can never exceed its configured capacity.

```text
tokens = min(capacity, tokens + tokensToAdd)
```

---

# 🔐 Why Redis Lua?

The rate limiter performs several operations:

```text
1. Read current tokens
2. Read last refill timestamp
3. Calculate elapsed time
4. Calculate new tokens
5. Cap tokens at capacity
6. Consume one token
7. Store updated token count
8. Store updated timestamp
```

If these operations were performed separately, concurrent requests could cause race conditions.

For example:

```text
Request A ──┐
            ├── Read tokens = 1
Request B ──┘

Request A → consume token
Request B → consume token
```

Both requests might incorrectly be accepted.

Instead, the complete operation is executed inside a Redis Lua script:

```text
Request
   │
   ▼
Redis Lua Script
   │
   ├── Read
   ├── Refill
   ├── Consume
   ├── Update
   └── Return result
```

Redis executes the Lua script atomically, preventing concurrent requests from corrupting the bucket state.

---

# 🔑 Redis Data Model

Each client gets two Redis keys.

### Token key

```text
rate_limiter:tokens:{clientId}
```

Example:

```text
rate_limiter:tokens:192.168.1.10
```

### Last refill key

```text
rate_limiter:last_refill:{clientId}
```

Example:

```text
rate_limiter:last_refill:192.168.1.10
```

Redis may therefore contain:

```text
rate_limiter:tokens:192.168.1.10
        ↓
        7

rate_limiter:last_refill:192.168.1.10
        ↓
        1757238000000
```

---

# 🌐 Client Identification

The Gateway identifies clients using their IP address.

The filter first checks:

```http
X-Forwarded-For
```

If available:

```text
X-Forwarded-For: 192.168.1.10
```

the first IP is used as the client ID.

Otherwise, the Gateway falls back to:

```text
Remote Address
```

Example:

```text
Client IP
192.168.1.10
      │
      ▼
rate_limiter:tokens:192.168.1.10
```

---

# 🚀 Request Flow

Suppose the client sends:

```http
GET /api/users
```

### Step 1 — Request reaches Gateway

```text
Client
   │
   ▼
GET /api/users
```

### Step 2 — Gateway matches the route

```java
.path("/api/**")
```

### Step 3 — Rate limiter identifies the client

```text
clientId = 192.168.1.10
```

### Step 4 — Gateway calls the rate limiter

```text
TokenBucketRateLimiterFilter
             │
             ▼
     RateLimiterService
             │
             ▼
 RedisTokenBucketService
```

### Step 5 — Redis Lua script executes

```text
Read tokens
     ↓
Calculate elapsed time
     ↓
Refill tokens
     ↓
Cap at capacity
     ↓
Consume token
     ↓
Return remaining tokens
```

### Step 6 — Request decision

If:

```text
remainingTokens >= 0
```

the request is allowed.

Otherwise:

```text
remainingTokens = -1
```

and the request is rejected.

---

# ✅ Successful Request

Example:

```text
Capacity = 10

Before request:
tokens = 5

Request arrives
      ↓
Consume 1 token
      ↓
tokens = 4
      ↓
Request forwarded
```

Response:

```http
HTTP/1.1 200 OK

X-RateLimit-Limit: 10
X-RateLimit-Remaining: 4
```

---

# ❌ Rate Limit Exceeded

When the bucket is empty:

```text
tokens = 0
```

the Lua script returns:

```text
-1
```

The Gateway then returns:

```http
HTTP/1.1 429 Too Many Requests
```

Response:

```json
{
  "error": "Rate limit exceeded",
  "clientId": "192.168.1.10"
}
```

The downstream API is **not called**.

---

# 📊 Rate Limit Headers

The Gateway provides:

```http
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 4
```

### `X-RateLimit-Limit`

Maximum bucket capacity.

```text
10
```

### `X-RateLimit-Remaining`

Number of currently available tokens.

```text
4
```

These headers allow clients to understand their current rate-limit status.

---

# ☁️ Distributed Architecture

One major advantage of using Redis is that multiple Gateway instances can share the same rate-limit state.

```text
                 ┌─────────────┐
                 │    Redis    │
                 └──────┬──────┘
                        │
          ┌─────────────┼─────────────┐
          │             │             │
          ▼             ▼             ▼
     Gateway 1     Gateway 2     Gateway 3
          │             │             │
          └─────────────┼─────────────┘
                        │
                     Client
```

For example:

```text
Request 1 → Gateway 1 → Redis
Request 2 → Gateway 2 → Redis
Request 3 → Gateway 3 → Redis
```

All three requests use the same Redis bucket:

```text
rate_limiter:tokens:192.168.1.10
```

Therefore, rate limiting remains consistent across Gateway instances.

---

# ⚙️ Configuration

Example configuration:

```yaml
rate-limiter:
  capacity: 10
  refill-rate: 5
  api-server-url: http://localhost:8080
  timeout: 5000

spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000
```

### Configuration Explanation

| Property         | Description                 |                 Example |
| ---------------- | --------------------------- | ----------------------: |
| `capacity`       | Maximum tokens              |                    `10` |
| `refill-rate`    | Tokens generated per second |                     `5` |
| `api-server-url` | Downstream API              | `http://localhost:8080` |
| `timeout`        | Redis connection timeout    |                `2000ms` |

---

# 🧩 Redis Connection Pool

The application uses a `JedisPool` instead of creating a new Redis connection for every request.

Current configuration:

```text
Max Total Connections = 300
Max Idle Connections = 100
Min Idle Connections = 20
Block When Exhausted = true
Max Wait = 1000ms
```

Architecture:

```text
Gateway Requests
       │
       ▼
   JedisPool
       │
 ┌─────┼─────┐
 ▼     ▼     ▼
Redis Redis Redis
Connection Pool
```

Connection pooling reduces the overhead of repeatedly establishing Redis connections.

---

# 📁 Project Structure

```text
src/
└── main/
    └── java/
        └── com/
            └── souvick/
                └── rate_limiter/
                    │
                    ├── config/
                    │   ├── GatewayConfig.java
                    │   ├── RateLimiterProperties.java
                    │   └── RedisProperties.java
                    │
                    ├── filter/
                    │   └── TokenBucketRateLimiterFilter.java
                    │
                    └── service/
                        ├── RateLimiterService.java
                        └── RedisTokenBucketService.java
```

---

# 🔬 Example

Assume:

```text
Capacity = 10
Refill Rate = 5/sec
```

A client sends 10 requests immediately:

```text
Request 1  → ✅
Request 2  → ✅
Request 3  → ✅
Request 4  → ✅
Request 5  → ✅
Request 6  → ✅
Request 7  → ✅
Request 8  → ✅
Request 9  → ✅
Request 10 → ✅
Request 11 → ❌ 429
```

After approximately one second:

```text
5 tokens regenerated
```

Therefore:

```text
Request 12 → ✅
Request 13 → ✅
Request 14 → ✅
Request 15 → ✅
Request 16 → ✅
Request 17 → ❌
```

---

# 🧪 Load Testing

The Gateway can be tested using tools such as **Autocannon**, Apache JMeter, or k6.

Example with Autocannon:

```bash
npx autocannon -c 100 -d 30 http://localhost:8081/api/users
```

Where:

```text
-c 100
```

means:

```text
100 concurrent connections
```

and:

```text
-d 30
```

means:

```text
30 seconds
```

During testing, requests exceeding the configured token rate should receive:

```text
HTTP 429
```

---

# 🔒 Concurrency Safety

The implementation is designed to handle concurrent requests using:

### Redis

Provides centralized shared state.

### Lua

Makes token calculation and consumption atomic.

### JedisPool

Provides efficient Redis connection management.

Therefore:

```text
Concurrent Requests
        │
        ▼
      Redis
        │
        ▼
 Atomic Lua Script
        │
        ▼
Consistent Bucket State
```

---

# 🚀 Running the Project

## 1. Start Redis

Make sure Redis is running on:

```text
localhost:6379
```

Using Docker:

```bash
docker run -d \
  --name redis \
  -p 6379:6379 \
  redis
```

---

## 2. Clone the Repository

```bash
git clone <your-repository-url>
cd <your-project>
```

---

## 3. Configure Redis

Update:

```yaml
spring:
  redis:
    host: localhost
    port: 6379
```

---

## 4. Configure Rate Limiter

```yaml
rate-limiter:
  capacity: 10
  refill-rate: 5
  api-server-url: http://localhost:8080
```

---

## 5. Start the Application

Using Maven:

```bash
./mvnw spring-boot:run
```

Windows:

```bash
mvnw.cmd spring-boot:run
```

---

# 📈 Future Improvements

Possible improvements include:

* [ ] Redis key expiration for inactive clients
* [ ] User/API-key based rate limiting
* [ ] Endpoint-specific rate limits
* [ ] Different limits based on user roles
* [ ] Distributed tracing
* [ ] Prometheus metrics
* [ ] Grafana dashboard
* [ ] Retry-After response header
* [ ] Dynamic rate-limit configuration
* [ ] Redis Cluster support
* [ ] Better proxy/IP trust configuration
* [ ] Integration and load tests
* [ ] Docker Compose deployment

---

# 🎯 Design Goals

The project focuses on:

```text
High Concurrency
       +
Atomic Operations
       +
Distributed State
       +
Efficient Redis Access
       +
Gateway-Level Protection
```

The result is a lightweight and scalable mechanism for protecting downstream APIs from excessive traffic.

---

# 👨‍💻 Author

**Souvick**

Built with:

```text
Java
Spring Boot
Spring Cloud Gateway
Redis
Lua
Jedis
```

---

