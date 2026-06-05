# Payments Service

A production-quality idempotent payments service demonstrating **exactly-once payment processing** in a distributed system. Built with Java 21, Spring Boot 3, PostgreSQL, and Apache Kafka.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client (curl / app)                      │
└────────────────────────────┬────────────────────────────────────┘
                             │ POST /payments
                             │ Idempotency-Key: <uuid>
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     PaymentController                           │
│                   (Spring MVC REST API)                         │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   IdempotencyService                            │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  SHA-256(request body) → lookup idempotency_keys table  │   │
│  │                                                         │   │
│  │  Same key + same hash  ──► return cached response       │   │
│  │  Same key + diff hash  ──► 409 Conflict                 │   │
│  │  New key               ──► process + store in same tx   │   │
│  └─────────────────────────────────────────────────────────┘   │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     PaymentService                              │
│              (single @Transactional boundary)                   │
│                                                                 │
│   1. INSERT payments          (with @Version optimistic lock)   │
│   2. INSERT outbox            (atomic with payment)             │
│   3. INSERT idempotency_keys  (atomic with payment)             │
└────────────────────────────┬────────────────────────────────────┘
                             │  all three writes commit together
                             ▼
                    ┌────────────────┐
                    │   PostgreSQL   │
                    │  (4 tables)    │
                    └───────┬────────┘
                            │
          ┌─────────────────┘
          │  @Scheduled every 1s
          ▼
┌─────────────────────────┐
│      OutboxRelay        │  reads unpublished rows
│  (transactional outbox) │  send().get() ← blocks for broker ACK
│                         │  marks published=true only on success
└────────────┬────────────┘
             │
             ▼
    ┌─────────────────┐
    │  Apache Kafka   │  topic: payment-events
    │                 │  retry: payment-events-retry-{0,1}
    │                 │  dlt:   payment-events-dlt
    └────────┬────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    LedgerConsumer                               │
│              (@KafkaListener + @RetryableTopic)                 │
│                                                                 │
│   1. Check processed_events for event_id                        │
│   2. If exists → skip (duplicate, already applied)             │
│   3. If new    → INSERT processed_events + apply ledger update  │
│                  (both in one @Transactional)                   │
│   4. On repeated failure → routed to DLT                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## Why the Outbox Pattern?

Naively publishing to Kafka inside a payment transaction creates a distributed transaction problem:

```
BEGIN
  INSERT INTO payments ...
  kafka.send("payment-events", ...)   ← what if this fails?
COMMIT
```

If Kafka is unavailable when you call `send()`, you either:
- **Roll back** the payment → lose the transaction
- **Commit** the payment without the event → the ledger never hears about it

The **transactional outbox** solves this by writing the event to a database table (`outbox`) in the **same transaction** as the payment. The database commit is the single source of truth. A separate `OutboxRelay` poller then publishes from the database to Kafka asynchronously:

```
BEGIN
  INSERT INTO payments ...
  INSERT INTO outbox (payload, published=false) ...   ← same tx
COMMIT

-- separately, every 1 second:
SELECT * FROM outbox WHERE published = false
  → kafka.send(...).get()  ← blocks for broker ACK
  → UPDATE outbox SET published = true
```

**Result:** payment and event are always consistent. If Kafka is down, events accumulate in the outbox and are retried when Kafka recovers. No events are lost, and no payment is recorded without an event.

---

## Database Schema

```sql
payments          -- system of record; @Version for optimistic locking
idempotency_keys  -- SHA-256 keyed cache; prevents double-charge on retries
outbox            -- transactional outbox; bridge between DB and Kafka
processed_events  -- consumer-side dedup; at-least-once → effectively-once
```

---

## State Machine

```
PENDING ──► AUTHORIZED ──► CAPTURED ──► SETTLED
   │              │             │
   └──────────────┴─────────────┴──► FAILED
```

Illegal transitions are rejected with `422 Unprocessable Entity`.

---

## Endpoints

| Method | Path | Header | Description |
|--------|------|--------|-------------|
| `POST` | `/payments` | `Idempotency-Key: <uuid>` | Create a payment |
| `GET` | `/payments/{id}` | — | Get payment by ID |
| `GET` | `/payments?accountId={id}` | — | List payments for an account |
| `PATCH` | `/payments/{id}/status` | — | Transition payment status |
| `GET` | `/actuator/health` | — | Health check |
| `GET` | `/actuator/prometheus` | — | Prometheus metrics |

---

## Running Locally

### Prerequisites
- Java 21 (Temurin)
- Maven 3.9+
- Docker Desktop

### Start infrastructure + app

```bash
# Build the jar first
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  mvn clean package -DskipTests

# Start Postgres + Kafka + app
docker compose up --build
```

### Health check

```bash
curl http://localhost:8080/actuator/health
```

---

## curl Examples

### Create a payment

```bash
curl -s -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"accountId":"a1b2c3d4-0000-0000-0000-000000000001","amount":5000,"currency":"USD"}' \
  | jq .
```

### Retry with the same key (idempotent — returns cached response)

```bash
KEY=$(uuidgen)

curl -s -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" \
  -d '{"accountId":"a1b2c3d4-0000-0000-0000-000000000001","amount":5000,"currency":"USD"}' | jq .

# Same key, same body → identical response, no double-charge
curl -s -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" \
  -d '{"accountId":"a1b2c3d4-0000-0000-0000-000000000001","amount":5000,"currency":"USD"}' | jq .
```

### Same key, different body → 409 Conflict

```bash
KEY=$(uuidgen)

curl -s -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" \
  -d '{"accountId":"a1b2c3d4-0000-0000-0000-000000000001","amount":5000,"currency":"USD"}' | jq .

curl -s -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" \
  -d '{"accountId":"a1b2c3d4-0000-0000-0000-000000000001","amount":9999,"currency":"GBP"}' | jq .
# → 409 Conflict
```

### Get payment by ID

```bash
curl -s http://localhost:8080/payments/<payment-id> | jq .
```

### List payments for an account

```bash
curl -s "http://localhost:8080/payments?accountId=a1b2c3d4-0000-0000-0000-000000000001" | jq .
```

### Transition payment status

```bash
curl -s -X PATCH http://localhost:8080/payments/<payment-id>/status \
  -H "Content-Type: application/json" \
  -d '{"status":"AUTHORIZED"}' | jq .
```

### Prometheus metrics

```bash
curl -s http://localhost:8080/actuator/prometheus | grep payments
```

---

## Running Tests

### Unit tests only

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  mvn test -Dtest="IdempotencyServiceTest,OptimisticLockTest,PaymentStatusTest"
```

### Full suite (requires Docker Desktop running)

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  mvn test
```

> **Note for Docker Desktop 29.x on Mac:** Testcontainers requires a custom Docker client strategy because Docker Desktop 29.x dropped support for the legacy API version used by the default Testcontainers client. The `DockerDesktop29Strategy` class and `~/.testcontainers.properties` configuration handle this automatically.

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Amounts stored as `BIGINT` (cents) | Floating-point arithmetic is unsafe for money |
| `@Version` on Payment entity | Optimistic locking prevents lost updates under concurrent writes without database-level pessimistic locks |
| Idempotency key + SHA-256 hash | Hash catches payload tampering — same key with different body is a client bug, not a retry |
| Outbox in same transaction as payment | Eliminates the dual-write problem; Kafka delivery is best-effort, database commit is truth |
| `send().get()` in OutboxRelay | Synchronous broker ACK before marking published; trades throughput for delivery guarantee |
| Manual ack mode → `@RetryableTopic` | Consumer controls offset commit; failed events retry before landing on DLT |
| `processed_events` table | Consumer-side dedup closes the at-least-once gap; insert is idempotent via primary key constraint |
