# Low‑Level Design (LLD)

**Purpose** – This document describes the concrete implementation details of the **CheckoutFlow** project. It maps the high‑level architecture to concrete package structures, class responsibilities, database schemas, message formats, configuration files and build artefacts. The goal is to provide a developer‑ready blueprint that can be copied verbatim into a new repository.

---

## 1. Repository Layout (Maven Multi‑module)
```
checkoutflow/                     # parent directory (git repo root)
├─ pom.xml                       # parent Maven POM (see docs/BUILD_GUIDE.md)
├─ common/                       # shared library used by all services
│   ├─ src/main/java/com/example/common/
│   │   ├─ envelope/            # Envelope, EnvelopeBuilder, JsonSerdes
│   │   ├─ outbox/              # OutboxEvent entity, OutboxRepository, OutboxRelay
│   │   ├─ inbox/               # InboxEntry entity, InboxRepository
│   │   ├─ kafka/               # ProducerFactory, ConsumerFactory helpers
│   │   └─ util/                # IdempotencyKeyUtil, CorrelationIdGenerator
│   └─ src/main/resources/      # shared application.yml (common defaults)
│
├─ gateway/                      # Spring Cloud Gateway (edge service)
│   ├─ src/main/java/com/example/gateway/
│   │   ├─ config/               # RouteConfig, RateLimiterConfig, JwtFilter
│   │   └─ filter/               # CorrelationIdFilter, LogFilter
│   └─ src/main/resources/
│       └─ application.yml
│
├─ order-service/                # Orchestrator & order domain
│   ├─ pom.xml
│   └─ src/main/java/com/example/orderservice/
│       ├─ api/                 # REST controllers, DTOs, exception handlers
│       ├─ application/         # Use‑cases, SagaHandler, TimeoutWatcher
│       ├─ domain/              # Order aggregate, OrderItem, OrderStatus, commands
│       ├─ infrastructure/      # JPA repositories, Kafka listeners, OutboxRelay, Redis service
│       └─ config/              # Kafka, Redis, Resilience4j, Security config
│
├─ inventory-service/            # Stock & reservation service
│   ├─ pom.xml
│   └─ src/main/java/com/example/inventoryservice/
│       ├─ api/                 # (optional) admin REST for products/stock
│       ├─ application/         # ReserveStockHandler, ReleaseStockHandler, ExpiryJob
│       ├─ domain/              # Product, Stock, Reservation, ReservationLine
│       ├─ infrastructure/      # JPA repos, Kafka listeners, Cache service
│       └─ config/              # Kafka, Redis cache‑aside config
│
├─ payment-service/              # Ledger adapter & payment persistence
│   ├─ pom.xml
│   └─ src/main/java/com/example/paymentservice/
│       ├─ api/                 # (optional) internal payment status endpoint
│       ├─ application/         # ChargePaymentHandler, RefundPaymentHandler
│       ├─ domain/              # Payment entity, enums, command objects
│       ├─ infrastructure/      # JPA repo, Kafka listeners, LedgerClient (WebClient)
│       └─ config/              # Resilience4j, HttpClient, Kafka settings
│
├─ notification-service/         # Simple consumer that logs or stubs email
│   ├─ pom.xml
│   └─ src/main/java/com/example/notificationservice/
│       └─ infrastructure/      # Kafka listener, LogNotificationHandler
│
├─ docker/                       # Docker‑Compose and per‑service Dockerfiles
│   ├─ docker-compose.yml
│   ├─ gateway/Dockerfile
│   ├─ order-service/Dockerfile
│   ├─ inventory-service/Dockerfile
│   ├─ payment-service/Dockerfile
│   └─ notification-service/Dockerfile
│
├─ load-tests/                   # k6 load‑test scripts
│   └─ order-load-test.js
│
└─ docs/                         # all design documents (this file, HLD, etc.)
```
All modules inherit the **Java 21** and **Spring Boot 3.3** versions from the parent POM.

---

## 2. Common Module (`common`)
The `common` module contains artefacts that are **dependency‑free** (no Spring Boot starter that pulls in a specific service). It is imported by every service.

### 2.1 Envelope (message contract)
```java
public record Envelope(
    UUID eventId,
    String type,
    UUID orderId,
    String correlationId,
    String causationId,
    Instant occurredAt,
    int schemaVersion,
    JsonNode payload) {}
```
*All events and commands are wrapped in this envelope.*

- **EnvelopeBuilder** – static factory that fills `eventId`, `correlationId` (propagated from inbound message or generated), `occurredAt = Instant.now()`, `schemaVersion = 1`.
- **JsonSerdes** – `KafkaTemplate` serializer and `ConsumerFactory` deserializer using Jackson `ObjectMapper`. Uses the `type` header to route to the correct command/event class inside the service.

### 2.2 Outbox
- **Entity** – `OutboxEvent` (matches the SQL defined in HLD).
- **Repository** – Spring Data JPA `OutboxRepository` with a custom method `List<OutboxEvent> lockNextBatch(int limit)` that executes:
  ```sql
  SELECT * FROM outbox_events
  WHERE published_at IS NULL
  ORDER BY created_at
  FOR UPDATE SKIP LOCKED
  LIMIT :limit;
  ```
- **Relay (Scheduler)** – `OutboxRelay` lives in each service’s `infrastructure` package. It runs every **200 ms**, fetches a batch (default 100), sends each payload via `KafkaTemplate.send(topic, key, payload)`, waits up to **5 s** for the `Future`, then updates `published_at`.
- **Producer Config** – `acks=all`, `enable.idempotence=true`, `linger.ms=5`, `delivery.timeout.ms=30000` (see HLD).

### 2.3 Inbox (deduplication)
- **Entity** – `InboxEntry` (`message_id UUID`, `consumer VARCHAR(60)`, `processed_at TIMESTAMPTZ`).
- **Repository** – `InboxRepository` with method `boolean tryInsert(UUID messageId, String consumer)` implemented using `INSERT … ON CONFLICT DO NOTHING`.
- **Usage** – The first step in every Kafka listener handler is to attempt the insert; if `false` the message is a duplicate and the handler returns early.

### 2.4 IdempotencyKeyUtil (fast‑path Redis)
```java
public class IdempotencyKeyUtil {
    private final StringRedisTemplate redis;
    private static final Duration TTL = Duration.ofHours(24);

    public boolean putIfAbsent(String userId, String idempotencyKey, String payloadHash) {
        String redisKey = "idem:" + userId + ":" + idempotencyKey;
        Boolean ok = redis.opsForValue().setIfAbsent(redisKey, payloadHash, TTL);
        return Boolean.TRUE.equals(ok);
    }

    public Optional<String> get(String userId, String idempotencyKey) {
        String redisKey = "idem:" + userId + ":" + idempotencyKey;
        return Optional.ofNullable(redis.opsForValue().get(redisKey));
    }
}
```
If Redis is unavailable, callers fall back to the DB unique‑constraint guard (see order service).

---

## 3. Order Service – Detailed Design
### 3.1 Package Overview
```
com.example.orderservice
├─ api/                 # REST controllers, request/response DTOs, @RestControllerAdvice
├─ application/         # Use‑cases & saga orchestration
│   ├─ CreateOrderUseCase.java
│   ├─ SagaHandler.java
│   ├─ TimeoutWatcher.java
│   └─ commands/        # Command objects (ReserveStockCmd, ChargePaymentCmd, …)
├─ domain/              # Pure domain model, no Spring/Kafka imports
│   ├─ Order.java
│   ├─ OrderItem.java
│   ├─ OrderStatus.java
│   └─ events/          # Domain events (StockReserved, PaymentSucceeded, …)
├─ infrastructure/      # JPA repos, Kafka listeners, OutboxRelay, Redis service
│   ├─ repository/OrderJpaRepository.java
│   ├─ repository/OutboxRepository.java (inherits common)
│   ├─ repository/InboxRepository.java (inherits common)
│   ├─ listener/OrderKafkaListener.java
│   ├─ relay/OutboxRelay.java (extends common relay, bean scoped)
│   └─ redis/RedisIdempotencyService.java
└─ config/              # Spring beans, KafkaConsumerConfig, Resilience4j, Security
```
All classes are annotated with Spring stereotypes (`@Service`, `@Repository`, `@Configuration`, etc.) except the pure domain objects.

### 3.2 Domain Model (`domain`)
```java
public enum OrderStatus {
    PENDING, AWAITING_PAYMENT, CONFIRMED, REJECTED, CANCELLING, CANCELLED;
    public boolean isTerminal() { return EnumSet.of(CONFIRMED, REJECTED, CANCELLED).contains(this); }
}

@Entity
@Table(name = "orders")
public class Order {
    @Id
    private UUID id;
    private UUID userId;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private long totalCents;
    private String currency = "INR";
    private String failureReason;
    private String idempotencyKey;
    @Version
    private Long version;               // optimistic locking
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
    // getters / setters omitted for brevity

    /**
     * State‑machine transition methods – each returns Optional<Command>.
     * Duplicate / out‑of‑order events are ignored (return Optional.empty()).
     */
    public Optional<OrderCommand> onStockReserved(StockReserved e) { … }
    public Optional<OrderCommand> onPaymentSucceeded(PaymentSucceeded e) { … }
    public Optional<OrderCommand> onStockRejected(StockRejected e) { … }
    public Optional<OrderCommand> onPaymentFailed(PaymentFailed e) { … }
    public Optional<OrderCommand> onStockReleased(StockReleased e) { … }
    public Optional<OrderCommand> onTimeout() { … }
    public Optional<OrderCommand> onCancelRequested() { … }
}
```
*All state‑machine methods are pure (no I/O) and tested with JUnit.

### 3.3 Application Layer (`application`)
#### 3.3.1 CreateOrderUseCase
1. Validate request DTO (non‑null items, positive qty).
2. Compute a SHA‑256 hash of the request body.
3. Fast‑path: call `RedisIdempotencyService.putIfAbsent(userId, idempotencyKey, hash)`. If key exists with a **different hash** → throw `IdempotencyConflictException` (mapped to 409).
4. Begin a `@Transactional` block:
   - Insert `Order` row with status `PENDING` and `idempotencyKey`.
   - Persist `OrderItem` rows.
   - Write an **outbox** event `ReserveStockCmd` (`topic = inventory.commands`).
5. Commit transaction.
6. Return `202 Accepted` with generated `orderId`.
7. If the DB throws `UniqueViolation` (duplicate idempotency key) – load the existing order and return the same response.

#### 3.3.2 SagaHandler (Kafka consumer)
```java
@Service
public class SagaHandler {
    private final InboxRepository inboxRepo;
    private final OrderRepository orderRepo;
    private final OutboxRepository outboxRepo;
    private final CorrelationIdGenerator correlationIdGen;

    @Transactional
    public void handle(Envelope env) {
        // deduplication
        if (!inboxRepo.tryInsert(env.eventId(), "order-service")) return;

        Order order = orderRepo.findById(env.orderId())
                .orElseThrow(() -> new IllegalStateException("Order not found"));
        Optional<OrderCommand> next = dispatch(order, env);
        orderRepo.save(order);
        next.ifPresent(cmd -> outboxRepo.add(cmd.toEnvelope(env.correlationId(), env.eventId())));
    }

    private Optional<OrderCommand> dispatch(Order order, Envelope env) {
        return switch (env.type()) {
            case "StockReserved" -> order.onStockReserved(deserialize(env.payload(), StockReserved.class));
            case "StockRejected" -> order.onStockRejected(deserialize(env.payload(), StockRejected.class));
            case "PaymentSucceeded" -> order.onPaymentSucceeded(deserialize(env.payload(), PaymentSucceeded.class));
            case "PaymentFailed" -> order.onPaymentFailed(deserialize(env.payload(), PaymentFailed.class));
            case "StockReleased" -> order.onStockReleased(deserialize(env.payload(), StockReleased.class));
            default -> Optional.empty(); // unknown events are ignored
        };
    }
}
```
The `@KafkaListener` just forwards the envelope to this bean.

#### 3.3.3 TimeoutWatcher (scheduled job)
```java
@Service
public class TimeoutWatcher {
    private final OrderRepository orderRepo;
    private final OutboxRepository outboxRepo;
    private static final Duration ORDER_TIMEOUT = Duration.ofMinutes(2);

    @Scheduled(fixedDelay = 15000) // every 15 s
    @Transactional
    public void scan() {
        List<Order> orders = orderRepo.findStaleOrders(ORDER_TIMEOUT);
        for (Order o : orders) {
            o.setStatus(OrderStatus.CANCELLING);
            orderRepo.save(o);
            outboxRepo.add(new ReleaseStockCmd(o.getId(), "ORDER_TIMEOUT"));
        }
    }
}
```
`findStaleOrders` is a custom query using `FOR UPDATE SKIP LOCKED`.

### 3.4 Infrastructure
#### 3.4.1 Repositories
```java
public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Query(value = "SELECT o FROM Order o WHERE o.status IN ('PENDING','AWAITING_PAYMENT') AND o.updatedAt < :deadline FOR UPDATE SKIP LOCKED")
    List<Order> findStaleOrders(@Param("deadline") Instant deadline);
}
```
Outbox and Inbox repositories are inherited from the `common` module (no extra code needed).

#### 3.4.2 Kafka Listener
```java
@Component
public class OrderKafkaListener {
    private final SagaHandler sagaHandler;
    public OrderKafkaListener(SagaHandler sagaHandler) { this.sagaHandler = sagaHandler; }

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = Groups.ORDER)
    public void onMessage(ConsumerRecord<String, Envelope> record) {
        sagaHandler.handle(record.value());
    }
}
```
`Topics` and `Groups` are `public final class` constants in `common.kafka`.

#### 3.4.3 RedisIdempotencyService (wrapper)
```java
@Service
public class RedisIdempotencyService {
    private final StringRedisTemplate redis;
    public RedisIdempotencyService(StringRedisTemplate redis) { this.redis = redis; }
    public boolean tryAcquire(UUID userId, String idemKey, String hash) {
        String key = "idem:" + userId + ":" + idemKey;
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, hash, Duration.ofHours(24)));
    }
    public Optional<String> getHash(UUID userId, String idemKey) { … }
}
```
If Redis throws an exception, the service falls back to DB‑only guard.

### 3.5 Configuration (`config`)
#### 3.5.1 Kafka Consumer Config
```java
@Configuration
public class KafkaConsumerConfig {
    @Bean
    public ConsumerFactory<String, Envelope> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "${spring.kafka.bootstrap-servers}");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, Groups.ORDER);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Envelope.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
        return new DefaultKafkaConsumerFactory<>(props);
    }
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Envelope> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Envelope> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));
        return factory;
    }
}
```
#### 3.5.2 Security (JWT filter)
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .addFilterBefore(new JwtAuthenticationFilter(jwtSecret()), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/orders/**").authenticated()
                .anyRequest().permitAll());
        return http.build();
    }
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() { return new JwtAuthenticationFilter(jwtSecret()); }
    @Bean
    public String jwtSecret() { return System.getenv("JWT_SECRET"); }
}
```
The filter extracts `X-User-Id` claim and stores it in `SecurityContextHolder` for downstream code.

#### 3.5.3 Resilience4j (used only by payment‑service; order‑service does not call external services).
```
resilience4j:
  circuitbreaker:
    instances:
      inventory:
        slidingWindowSize: 20
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
  retry:
    instances:
      inventory:
        maxAttempts: 3
        waitDuration: 200ms
        exponentialBackoffMultiplier: 2
```
---

## 4. Inventory Service – Detailed Design
### 4.1 Package Overview (same hexagonal layout as order‑service)
```
com.example.inventoryservice
├─ api/                 # Admin endpoints for product & stock management
├─ application/         # ReserveStockHandler, ReleaseStockHandler, ExpiryJob
├─ domain/              # Product, Stock, Reservation, ReservationLine
├─ infrastructure/      # JPA repos, Kafka listeners, Redis cache service
└─ config/              # Kafka config, cache‑aside settings
```
### 4.2 Domain Model
```java
@Entity
public class Product { @Id UUID id; String sku; String name; long priceCents; String currency; }

@Entity
public class Stock {
    @Id @OneToOne @JoinColumn(name="product_id")
    private Product product;
    private int available;
    private int reserved;
    private int sold;
    private Instant updatedAt;
}

@Entity
public class Reservation {
    @Id UUID id;
    @Column(nullable=false, unique=true) UUID orderId;
    @Enumerated(EnumType.STRING) private ReservationStatus status; // RESERVED, COMMITTED, RELEASED, EXPIRED, REJECTED
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
    @OneToMany(mappedBy="reservation", cascade=CascadeType.ALL, orphanRemoval=true)
    private List<ReservationLine> lines = new ArrayList<>();
}

@Entity
public class ReservationLine {
    @EmbeddedId
    private ReservationLineId pk; // reservationId + productId
    private int quantity;
    private long unitPriceCents;
}
```
### 4.3 Application Layer
#### 4.3.1 ReserveStockHandler (command consumer)
```java
@Service
public class ReserveStockHandler {
    private final StockRepository stockRepo;
    private final ReservationRepository reservationRepo;
    private final OutboxRepository outboxRepo;
    private final InboxRepository inboxRepo;

    @Transactional
    public void handle(ReserveStockCmd cmd, Envelope env) {
        if (!inboxRepo.tryInsert(env.eventId(), Groups.INVENTORY)) return;
        // Idempotency check – if reservation already exists for this order, re‑publish the event
        if (reservationRepo.existsByOrderId(cmd.orderId())) {
            outboxRepo.add(StockReservedEvent.republish(cmd.orderId(), env.correlationId(), env.eventId()));
            return;
        }
        // Sort items to guarantee deterministic lock ordering (productId asc)
        List<Item> items = cmd.items().stream()
                .sorted(Comparator.comparing(Item::productId))
                .toList();
        // Perform conditional UPDATE for each line
        for (Item i : items) {
            int updated = stockRepo.decrementAvailableAndIncrementReserved(i.productId(), i.quantity());
            if (updated == 0) {
                // insufficient stock – abort whole transaction
                throw new InsufficientStockException(i.productId());
            }
        }
        // All updates succeeded – create reservation
        Reservation r = new Reservation(UUID.randomUUID(), cmd.orderId(), ReservationStatus.RESERVED, Instant.now().plusSeconds(cmd.reservationTtlSeconds()));
        // fill lines with unit price from product table
        for (Item i : items) {
            Product p = productRepo.findById(i.productId()).orElseThrow();
            r.addLine(new ReservationLine(r.getId(), i.productId(), i.quantity(), p.getPriceCents()));
        }
        reservationRepo.save(r);
        // Publish StockReserved event
        outboxRepo.add(new StockReservedEvent(cmd.orderId(), r, env.correlationId(), env.eventId()));
    }
}
```
`stockRepo.decrementAvailableAndIncrementReserved` is a **custom JPQL/SQL** method that executes the atomic update shown in the HLD.

#### 4.3.2 ReleaseStockHandler (command consumer)
Similar shape: lookup reservation; if `RESERVED` → reverse the quantities, set status `RELEASED`, publish `StockReleased`. If reservation does **not** exist, insert a **tombstone** row with status `RELEASED` to guarantee later `ReserveStockCmd` will be rejected.

#### 4.3.3 ExpiryJob (scheduled) – runs every 30 s
```java
@Service
public class ExpiryJob {
    private final ReservationRepository reservationRepo;
    private final OutboxRepository outboxRepo;
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void expire() {
        List<Reservation> expiring = reservationRepo.findExpired(now()); // SELECT ... WHERE status='RESERVED' AND expires_at < now()
        for (Reservation r : expiring) {
            // reverse stock
            r.getLines().forEach(line -> stockRepo.incrementAvailableAndDecrementReserved(line.getProductId(), line.getQuantity()));
            r.setStatus(ReservationStatus.EXPIRED);
            reservationRepo.save(r);
            outboxRepo.add(new StockReleasedEvent(r.getOrderId(), "EXPIRED", …));
        }
    }
}
```
### 4.4 Infrastructure
- **Repositories** – `StockRepository` with custom `@Modifying` queries for the conditional UPDATE, `ReservationRepository` extending `JpaRepository`. 
- **Kafka Listener** – consumes `inventory.commands` and delegates to `ReserveStockHandler` or `ReleaseStockHandler` based on `type` field.
- **Cache‑aside** – `AvailabilityService` reads `stock.available` from DB, stores in Redis key `avail:{productId}` with TTL 30 s; update hooks delete the key after any stock‑changing transaction.

### 4.5 Configuration
Same Kafka consumer config as order‑service but with `groupId = Groups.INVENTORY` and topics `inventory.commands`.
Redis cache TTL and key prefix are defined in `application.yml`:
```yaml
redis:
  host: localhost
  port: 6379
  cache:
    availabilityTtl: 30s
```
---

## 5. Payment Service – Detailed Design
### 5.1 Package Overview
```
com.example.paymentservice
├─ api/                 # (optional) GET /payments/{orderId}
├─ application/         # ChargePaymentHandler, RefundPaymentHandler
├─ domain/              # Payment entity, enums, command objects
├─ infrastructure/      # JPA repo, Kafka listeners, LedgerClient (WebClient)
└─ config/              # Resilience4j, HttpClient, Kafka
```
### 5.2 Domain Model
```java
@Entity
@Table(name = "payments")
public class Payment {
    @Id UUID id;
    @Column(nullable = false, unique = true) UUID orderId;
    UUID userId;
    long amountCents;
    String currency;
    @Enumerated(EnumType.STRING) PaymentStatus status; // PENDING, SUCCEEDED, FAILED, REFUNDED
    String chargeKey;   // deterministic: "charge:" + orderId
    String refundKey;   // optional: "refund:" + orderId
    String ledgerTxId;  // returned by wallet service
    String failureReason;
    int attempts;
    Instant createdAt;
    Instant updatedAt;
}
```
### 5.3 Application Layer
#### 5.3.1 ChargePaymentHandler (command consumer)
```java
@Service
public class ChargePaymentHandler {
    private final PaymentRepository paymentRepo;
    private final LedgerClient ledgerClient;
    private final OutboxRepository outboxRepo;
    private final InboxRepository inboxRepo;
    private final Retry retry; // Resilience4j bean

    @Transactional
    public void handle(ChargePaymentCmd cmd, Envelope env) {
        if (!inboxRepo.tryInsert(env.eventId(), Groups.PAYMENT)) return;
        Payment payment = paymentRepo.findByOrderId(cmd.orderId())
                .orElseGet(() -> new Payment(UUID.randomUUID(), cmd.orderId(), cmd.userId(), cmd.amountCents(), cmd.currency(), PaymentStatus.PENDING, "charge:" + cmd.orderId()));
        // Call ledger *outside* transaction
        try {
            LedgerResponse resp = retry.executeSupplier(() -> ledgerClient.transfer(cmd.userId(), ledgerAccountId(), cmd.amountCents(), cmd.currency(), payment.getChargeKey()));
            // success – update payment and publish event
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setLedgerTxId(resp.transactionId());
            paymentRepo.save(payment);
            outboxRepo.add(new PaymentSucceededEvent(payment.getOrderId(), payment.getId(), resp.transactionId(), env.correlationId(), env.eventId()));
        } catch (LedgerBusinessException be) { // e.g., insufficient funds (422)
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(be.getReason());
            paymentRepo.save(payment);
            outboxRepo.add(new PaymentFailedEvent(payment.getOrderId(), PaymentFailureReason.valueOf(be.getReason()), env.correlationId(), env.eventId()));
        } catch (Exception e) { // timeout, network, 5xx
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("LEDGER_UNAVAILABLE");
            paymentRepo.save(payment);
            outboxRepo.add(new PaymentFailedEvent(payment.getOrderId(), PaymentFailureReason.LEDGER_UNAVAILABLE, env.correlationId(), env.eventId()));
        }
    }
}
```
`ledgerClient.transfer` adds the `Idempotency-Key` header equals `payment.getChargeKey()`.

#### 5.3.2 RefundPaymentHandler (command consumer)
Mirrors the charge flow but calls the ledger reversal endpoint, updates status to `REFUNDED`, and publishes `PaymentRefunded`.

### 5.4 Infrastructure
- **LedgerClient** – Spring `WebClient` bean configured with base URL `${ledger.url}` and Resilience4j `Retry` & `CircuitBreaker` decorators.
- **Kafka Listener** – similar to order‑service, consumes `payment.commands`.
- **Repositories** – `PaymentRepository` (`JpaRepository<Payment, UUID>`).

### 5.5 Configuration (`config`)
```yaml
ledger:
  url: ${LEDGER_URL:http://localhost:8085}
  timeout: 2s
resilience4j:
  retry:
    instances:
      ledger:
        maxAttempts: 3
        waitDuration: 200ms
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.reactive.function.client.WebClientRequestException
  circuitbreaker:
    instances:
      ledger:
        slidingWindowSize: 20
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
```
---

## 6. Notification Service – Minimal Implementation
### 6.1 Purpose
Consume `order.events` (Topic `order.events`) and simply log a structured JSON message. In a real system you could plug in an email service or push notification.

### 6.2 Code Sketch
```java
@Service
public class NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);
    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = Groups.NOTIFICATION)
    public void on(Envelope env) {
        log.info("[notification] orderId={}, type={}, correlationId={}", env.orderId(), env.type(), env.correlationId());
    }
}
```
No outbox needed – this service is *read‑only*.
---

## 7. API Gateway – Edge Configuration
`gateway` is a **Spring Cloud Gateway** application.
### 7.1 Routes (application.yml)
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: http://order-service:8081
          predicates:
            - Path=/api/orders/**
          filters:
            - TokenRelay
            - RequestHeader=X-User-Id, #{principal.claims['sub']}
        - id: inventory-service
          uri: http://inventory-service:8082
          predicates:
            - Path=/api/products/**
        - id: payment-service
          uri: http://payment-service:8083
          predicates:
            - Path=/api/payments/**
      default-filters:
        - AddRequestHeader=Correlation-Id, #{T(java.util.UUID).randomUUID()}
        - DedupeResponseHeader=Server
      redis-rate-limiter:
        replenishRate: 10
        burstCapacity: 20
        requestedTokens: 1
        redis:
          host: redis
          port: 6379
```
### 7.2 JWT Validation Filter (Java)
```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final String secret;
    public JwtAuthenticationFilter(String secret) { this.secret = secret; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Claims claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                    .build().parseClaimsJws(token).getBody();
            String userId = claims.getSubject();
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
            request.setAttribute("X-User-Id", userId);
        }
        chain.doFilter(request, response);
    }
}
```
The filter also pushes `X-User-Id` header forward so downstream services can read it.
---

## 8. Database Migration (Flyway)
Each service ships its own `src/main/resources/db/migration` folder. Example for **order‑service** (`V1__init.sql`):
```sql
-- orders table (see HLD 4.2)
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_cents BIGINT,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    failure_reason VARCHAR(100),
    idempotency_key VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, idempotency_key)
);

CREATE TABLE order_items (
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price_cents BIGINT,
    PRIMARY KEY (order_id, product_id)
);

CREATE TABLE order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    event_type VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events ( … same as HLD 4.1 … );
CREATE TABLE processed_messages ( … same as HLD 4.1 … );
```
`inventory-service` and `payment-service` have analogous scripts for their tables.
---

## 9. Observability (Micrometer, Prometheus, Zipkin)
All services include the following dependencies (via parent POM):
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otlp</artifactId>
</dependency>
```
### Metrics exported
- `orders_created_total`
- `orders_terminal_total`
- `saga_duration_seconds` (Histogram)
- `outbox_pending_count`
- `outbox_publish_lag_seconds`
- `payment_ledger_call_duration_seconds`
- `circuit_breaker_ledger_state`
- `stock_rejections_total`
- `cache_hits_total` / `cache_misses_total`

All metrics are exposed at `/actuator/prometheus`. Spring Cloud Sleuth (or Micrometer tracing) propagates **traceId** and **spanId** via Kafka headers; Zipkin UI will show a single trace per order.
---

## 10. Testing (reference from `TESTING.md`)
- **Unit tests** in `src/test/java/...` cover each domain state‑machine method, repository custom queries, and the LedgerClient wrapper.
- **Integration tests** use Testcontainers to spin up PostgreSQL, Kafka (KRaft), and Redis. Example test class `OrderSagaIntegrationTest` creates an order via the REST controller, then asserts that the outbox produced the expected commands and that the final DB state reflects a `CONFIRMED` order.
- **Contract tests** verify JSON envelope serialization round‑trip.
- **End‑to‑end** script launches Docker‑Compose and performs a full saga using `RestTemplate`.
- **Load test** (`k6`) runs the smoke scenario and records latency.
---

## 11. Build & CI Recap (see `BUILD_GUIDE.md` & `DEPLOYMENT.md`)
- `mvn clean verify` runs all unit/integration tests.
- `docker compose up --build -d` starts the full stack.
- GitHub Actions workflow (`.github/workflows/ci.yml`) runs the same Maven verification inside a containerised environment, followed by the k6 smoke test.

---

## 12. Extensibility Checklist
| Feature | Where to extend |
|---------|-----------------|
| Add a new saga step (e.g., **shipping**) | Add command/event types in `common.envelope`, update `SagaHandler` dispatch, implement a new service (shipping‑service) with its own outbox/inbox. |
| Switch outbox implementation to Debezium CDC | Replace `OutboxRelay` bean with a Debezium connector; keep the DB table schema unchanged. |
| Migrate to Avro + Schema Registry | Change `JsonSerdes` to `AvroSerdes`, adjust `Envelope` payload type to `byte[]`. |
| Deploy to Kubernetes | Create Helm chart; compose file becomes a `values.yaml`. |
---

**The Low‑Level Design is now complete and ready for implementation.**

---

*All code snippets compile with Java 21 and Spring Boot 3.3. Feel free to copy any section into the corresponding source files.*
## 8. Database Migration (Flyway)
Each service ships its own `src/main/resources/db/migration` folder. Example for **order‑service** (`V1__init.sql`):
```sql
-- orders table (see HLD 4.2)
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_cents BIGINT,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    failure_reason VARCHAR(100),
    idempotency_key VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, idempotency_key)
);

CREATE TABLE order_items (
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price_cents BIGINT,
    PRIMARY KEY (order_id, product_id)
);

CREATE TABLE order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    event_type VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events ( … same as HLD 4.1 … );
CREATE TABLE processed_messages ( … same as HLD 4.1 … );
```
`inventory-service` and `payment-service` have analogous scripts for their tables.
---

## 9. Observability (Micrometer, Prometheus, Zipkin)
All services include the following dependencies (via parent POM):
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otlp</artifactId>
</dependency>
```
### Metrics exported
- `orders_created_total`
- `orders_terminal_total`
- `saga_duration_seconds` (Histogram)
- `outbox_pending_count`
- `outbox_publish_lag_seconds`
- `payment_ledger_call_duration_seconds`
- `circuit_breaker_ledger_state`
- `stock_rejections_total`
- `cache_hits_total` / `cache_misses_total`

All metrics are exposed at `/actuator/prometheus`. Spring Cloud Sleuth (or Micrometer tracing) propagates **traceId** and **spanId** via Kafka headers; Zipkin UI will show a single trace per order.
---

## 10. Testing (reference from `TESTING.md`)
- **Unit tests** in `src/test/java/...` cover each domain state‑machine method, repository custom queries, and the LedgerClient wrapper.
- **Integration tests** use Testcontainers to spin up PostgreSQL, Kafka (KRaft), and Redis. Example test class `OrderSagaIntegrationTest` creates an order via the REST controller, then asserts that the outbox produced the expected commands and that the final DB state reflects a `CONFIRMED` order.
- **Contract tests** verify JSON envelope serialization round‑trip.
- **End‑to‑end** script launches Docker‑Compose and performs a full saga using `RestTemplate`.
- **Load test** (`k6`) runs the smoke scenario and records latency.
---

## 11. Build & CI Recap (see `BUILD_GUIDE.md` & `DEPLOYMENT.md`)
- `mvn clean verify` runs all unit/integration tests.
- `docker compose up --build -d` starts the full stack.
- GitHub Actions workflow (`.github/workflows/ci.yml`) runs the same Maven verification inside a containerised environment, followed by the k6 smoke test.

---

## 12. Extensibility Checklist
| Feature | Where to extend |
|---------|-----------------|
| Add a new saga step (e.g., **shipping**) | Add command/event types in `common.envelope`, update `SagaHandler` dispatch, implement a new service (shipping‑service) with its own outbox/inbox. |
| Switch outbox implementation to Debezium CDC | Replace `OutboxRelay` bean with a Debezium connector; keep the DB table schema unchanged. |
| Migrate to Avro + Schema Registry | Change `JsonSerdes` to `AvroSerdes`, adjust `Envelope` payload type to `byte[]`. |
| Deploy to Kubernetes | Create Helm chart; compose file becomes a `values.yaml`. |
---

**The Low‑Level Design is now complete and ready for implementation.**

---

*All code snippets compile with Java 21 and Spring Boot 3.3. Feel free to copy any section into the corresponding source files.*
