#!/usr/bin/env bash
# End-to-end smoke test for the running stack (docker compose up --build).
# Usage: scripts/smoke-test.sh [base-url]
set -euo pipefail

BASE="${1:-http://localhost:8080}"
EVENT_ID="smoke-$(date +%s)-$RANDOM"

say() { printf '\n== %s\n' "$*"; }

say "1. Health"
curl -fsS "$BASE/actuator/health"; echo

say "2. Ingest a transaction (expect 202 ACCEPTED)"
BODY=$(cat <<JSON
{"eventId":"$EVENT_ID","customerId":"smoke-customer","accountId":"smoke-account","merchantId":"m-1",
 "amount":42.50,"currency":"USD","status":"APPROVED","eventTime":"$(date -u +%Y-%m-%dT%H:%M:%SZ)",
 "latitude":37.77,"longitude":-122.42,"countryCode":"US"}
JSON
)
curl -sS -o /dev/null -w "HTTP %{http_code}\n" -H 'Content-Type: application/json' -d "$BODY" "$BASE/api/v1/transactions"

say "3. Replay the same eventId (expect 200 DUPLICATE)"
curl -sS -w "\nHTTP %{http_code}\n" -H 'Content-Type: application/json' -d "$BODY" "$BASE/api/v1/transactions"

say "4. Read it back"
curl -fsS "$BASE/api/v1/transactions/$EVENT_ID"; echo

say "5. Invalid payload (expect 400)"
curl -sS -o /dev/null -w "HTTP %{http_code}\n" -H 'Content-Type: application/json' \
  -d '{"eventId":"x","currency":"usd"}' "$BASE/api/v1/transactions"

say "6. Generate 200 labelled events (expect 2 fraud clusters)"
curl -fsS -H 'Content-Type: application/json' -d '{"count":200}' "$BASE/api/v1/simulator/transactions"; echo

say "7. Metrics"
curl -fsS "$BASE/actuator/prometheus" | grep -E '^fraudshield_(transactions|kafka)' || true

if command -v docker >/dev/null 2>&1 && docker compose ps --status running 2>/dev/null | grep -q kafka; then
  say "8. First 3 messages on transactions.v1"
  docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:19092 --topic transactions.v1 --from-beginning \
    --max-messages 3 --timeout-ms 15000 --property print.key=true || true
fi

say "Smoke test finished"
