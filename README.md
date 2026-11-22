# FraudShield

**A streaming fraud-detection rules engine in pure Java.** Transactions flow through Kafka, are evaluated in real time against windowed fraud rules, and surface as alerts and investigator cases.

**Author:** Dhanush

---

## Tech stack

Java 21, Spring Boot 3.4, Spring Web and Validation, Spring Data JPA (Hibernate), PostgreSQL 16, Flyway, Apache Kafka 3.8 (KRaft, no ZooKeeper), Micrometer, Prometheus, Grafana, Docker Compose, JUnit 5, Mockito, Testcontainers, GitHub Actions.

## Getting started

**Prerequisites:** Docker with Compose v2. For local development also Java 21 and Maven 3.9+.

```bash
# start PostgreSQL, Kafka, the app, Prometheus and Grafana
docker compose up --build

# in another terminal: exercise the whole flow
./scripts/smoke-test.sh
```

| Service | URL |
| --- | --- |
| API | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| Prometheus | http://localhost:9091 |
| Grafana (admin / admin) | http://localhost:3000 (dashboard "FraudShield - Overview") |

**Running the app from your IDE instead:** `docker compose up postgres kafka`, then `mvn spring-boot:run`. Kafka advertises `localhost:9092` for host clients and `kafka:19092` for containers.

### API

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/v1/transactions` | Ingest one transaction. `202` new, `200` duplicate replay, `400` invalid, `409` concurrent duplicate |
| GET | `/api/v1/transactions/{eventId}` | Fetch one transaction (`404` if unknown) |
| GET | `/api/v1/transactions?customerId=...` | Latest 100 transactions of a customer, newest first |
| POST | `/api/v1/simulator/transactions` | Body `{"count": 1..10000}`. Generates labelled traffic |

```bash
curl -X POST localhost:8080/api/v1/transactions -H 'Content-Type: application/json' -d '{
  "eventId":"demo-1","customerId":"c-1","accountId":"a-1","merchantId":"m-1",
  "amount":42.50,"currency":"USD","status":"APPROVED",
  "eventTime":"2026-01-01T12:00:00Z","latitude":37.77,"longitude":-122.42,"countryCode":"US"}'

curl -X POST localhost:8080/api/v1/simulator/transactions -H 'Content-Type: application/json' -d '{"count":1000}'
```

## The simulator

Detection quality can only be measured if you know the right answer, so the simulator labels everything it generates.

- **Legitimate traffic**: $5 to $500, about 2% declined, each customer tied to a home city and given at most two events per run. By construction these customers cannot trip any of the four planned rules, so every alert on them is a genuine false positive.
- **Fraud cluster** (one per 100 events, at least one when `count >= 10`): three small declined attempts, one normal purchase, then a $7,500 purchase in Sydney seconds later. That triggers repeated failures, amount spike, impossible travel and velocity.
- Customer ids contain a per-run id, so separate runs never share a window.
- Seeded (`fraudshield.simulator.seed`), so amounts are reproducible.

## Design decisions and trade-offs

| Decision | Why | Trade-off |
| --- | --- | --- |
| Publish to Kafka **after** commit | Never emit an event for a rolled-back write | If the process dies between commit and publish, the event is stored but not streamed |
| Key by `customerId` | Per-customer ordering and co-location for windowed state | A very hot customer would skew one partition |
| `Persistable` entity (`persist`, not `merge`) | A concurrent duplicate fails on the primary key instead of silently overwriting | Slightly more entity code |
| `200 DUPLICATE` for replays | Safe client retries | Callers must read `outcome` to tell the cases apart |
| Flyway owns the schema, Hibernate `validate` | Schema is versioned and reviewable | Entity changes need a migration |

## Testing

```bash
mvn verify          # unit + slice + integration tests (Docker required for Testcontainers)
```

| Test | Type | Verifies |
| --- | --- | --- |
| `TransactionSimulatorTest` | unit | Cluster counts, every fraud cluster trips all four patterns, legitimate customers cannot |
| `TransactionControllerTest` | web slice | 202/200/400/404/409 behaviour and validation |
| `SimulatorControllerTest` | web slice | Input bounds return 400, not 500 |
| `TransactionRepositoryTest` | Testcontainers (PostgreSQL) | Flyway migration, schema validation, ordering and scoping |
| `TransactionIngestIntegrationTest` | Testcontainers (PostgreSQL + Kafka) | Stored and published once, keyed by customer; replay publishes nothing |

## Known limitations

- **No transactional outbox.** Database write and Kafka publish are separate systems, so delivery is at-least-once at best and can lose an event on a crash in the gap. An outbox is the standard fix.
- Consumers must deduplicate by `eventId`.
- No authentication or authorization yet.
- Single-node Kafka and PostgreSQL; this is a development setup, not a production topology.

## Project structure

```
src/main/java/com/portfolio/fraudshield
  api/          REST controllers and error handling
  config/       Kafka topic and clock configuration
  domain/       TransactionEvent (Kafka payload), TransactionRecord (JPA entity)
  metrics/      Micrometer counters and timers
  repository/   Spring Data repositories
  service/      Ingest logic (idempotency, publish-after-commit)
  simulator/    Labelled traffic generator
src/main/resources/db/migration   Flyway migrations
observability/                    Prometheus and Grafana provisioning
scripts/smoke-test.sh             End-to-end check against a running stack
```

## License

MIT. Copyright (c) 2026 Dhanush.
