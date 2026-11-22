# Week 1 - Event model, Kafka producer and labelled traffic simulator

Author: Dhanush

## Goal (from the project plan)

> Week 1: event model, producers, simulator that generates normal and fraudulent traffic.

## Deliverables

- [x] Event model: `TransactionEvent` (Kafka payload) and `TransactionRecord` (JPA entity)
- [x] Flyway migration `V1__create_core_tables.sql`
- [x] Kafka producer: publish-after-commit, keyed by `customerId`, topic `transactions.v1` (3 partitions)
- [x] Ingest REST API: validation, idempotency by `eventId`, `202`/`200`/`400`/`409` semantics
- [x] Read endpoints (by id, by customer)
- [x] Labelled traffic simulator (legitimate traffic and fraud clusters)
- [x] Metrics, Prometheus scrape config, Grafana dashboard
- [x] Docker Compose (PostgreSQL, Kafka KRaft, app, Prometheus, Grafana)
- [x] CI workflow, unit/slice/integration tests, smoke-test script

## How to verify this week

```bash
docker compose up --build        # wait until the app is healthy
./scripts/smoke-test.sh          # expects 202, 200 DUPLICATE, 400, simulator summary, metrics, Kafka messages
mvn verify                       # all tests (Docker required)
```

Expected highlights:

- Posting the same `eventId` twice returns `202 ACCEPTED`, then `200 DUPLICATE`; exactly one message is on `transactions.v1`.
- `POST /api/v1/simulator/transactions {"count":200}` returns `labelledFraudClusters: 2`, `labelledFraudEvents: 10`.
- Grafana "FraudShield - Overview" shows ingest rate and latency.

## Verification status (be honest about this in interviews)

Checked in the build environment:

- All main sources compile with JDK 21 against API stubs of the Spring/Kafka/JPA/Micrometer types they use (signature and generics check).
- Simulator logic executed against a fake ingest for counts 1, 5, 9, 10, 50, 99, 100, 101, 199, 200, 1000, 2500, 9999 and 10000; all documented guarantees held (cluster counts, every cluster trips all four patterns, no legitimate customer can trip a rule, ids unique, out-of-range counts rejected).
- Test sources parse without syntax errors; YAML, JSON and shell script syntax validated.

**Not executed here** (no Maven, Docker or network available): `mvn verify`, Testcontainers tests, Docker Compose startup, Kafka delivery, Flyway migration against a real database. Run `mvn verify` and the smoke test once on your machine and fix anything they surface before presenting this as runtime-tested.

## Next: Week 2

Kafka Streams topology with windowed aggregations and the first three rules (velocity, amount spike, repeated failures), then impossible travel.
