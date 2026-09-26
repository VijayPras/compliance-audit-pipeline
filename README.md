# Compliance Audit Pipeline

A real-time, event-driven compliance monitoring system for financial trades — built to demonstrate the **transactional outbox pattern**, **Change Data Capture (CDC)** with Debezium, and **Apache Kafka** event streaming, end to end, on infrastructure that actually runs.

When a trade is recorded, it's automatically checked against a set of compliance rules (sanctioned-country counterparties, large-notional thresholds, unusual trading velocity) within moments of being written to the database — no polling, no batch jobs, no manual review step in between.

## Why this exists

Financial institutions need to know the instant a risky trade happens, not hours later in an end-of-day batch report. This project is a small, working implementation of that idea: a trade lands in a database, and a separate service reacts to it in real time, purely by watching for the database change — the trade-writing service never has to know a compliance checker exists.

The interesting engineering problem isn't the rule-checking logic itself (that's intentionally simple) — it's making sure **no trade can ever go unchecked**, even if a service crashes mid-write, a message gets delivered twice, or a downstream consumer is temporarily down. That reliability is what the architecture below is built around.

## Architecture

```
┌────────────────┐        ┌──────────┐        ┌───────────────┐        ┌─────────────────┐
│  Order Service  │ writes │ Postgres │  CDC   │  Kafka Connect │ events │   Audit Service   │
│ (records trades) │──────▶│  (WAL)   │───────▶│   (Debezium)   │───────▶│ (rule engine +    │
│                 │        │          │        │  outbox router │        │  idempotent       │
└────────────────┘        └──────────┘        └───────────────┘        │  consumer)        │
                                                                         └────────┬──────────┘
                                                                                  │ publishes
                                                                                  ▼
                                                                          compliance-alerts.v1
                                                                                  │
                                                                                  ▼
                                                                          SSE stream → dashboard
```

**Order Service** writes a trade and an "outbox event" describing it in a single database transaction — the [transactional outbox pattern](https://microservices.io/patterns/data/transactional-outbox.html). This guarantees the trade and its event are never out of sync: either both are written, or neither is.

**Debezium**, running inside Kafka Connect, tails Postgres's write-ahead log (WAL) using logical replication — it never queries the outbox table directly, so there's no polling and no added load on the database. Its outbox **Event Router** transform turns each outbox row into a clean, versioned Kafka message on `trade-events.v1`.

**Audit Service** consumes `trade-events.v1`, runs each trade through a configurable rule engine, and — if the trade is flagged — publishes an alert to `compliance-alerts.v1`. It uses the **Inbox Pattern** (a dedup table checked before processing) to stay safe under Kafka's at-least-once delivery guarantee, so a redelivered message is a no-op rather than a duplicate alert.

A dashboard (in progress) will subscribe to Audit Service's Server-Sent Events endpoint for live, push-based updates — no polling from the frontend either.

## Tech stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 17 |
| Services | Spring Boot 3.5.16 (Web, Data JPA, Kafka) |
| Database | PostgreSQL 17 (logical replication enabled) |
| Migrations | Flyway |
| CDC | Debezium (PostgreSQL connector, `pgoutput` plugin) |
| Streaming | Apache Kafka 4.1.0 (KRaft mode — no ZooKeeper) |
| Orchestration | Kafka Connect |
| Local infra | Docker Compose |
| Observability | Kafka UI |

## Project structure

```
compliance-audit-pipeline/
├── order-service/       # Records trades; writes trade + outbox event transactionally
├── audit-service/       # Consumes trade events, runs rules, publishes alerts
├── docker-compose.yml   # Postgres, Kafka, Kafka Connect (Debezium), Kafka UI
├── register-postgres-connector.json   # Debezium connector config (outbox event router)
├── PROGRESS.md           # Detailed build log, including real bugs hit and how they were fixed
└── PROJECT_OVERVIEW.md   # Plain-language explanation of the project and its goals
```

## Getting started (local)

**Prerequisites:** Docker Desktop, Java 17, Maven.

1. **Start the infrastructure:**
   ```
   docker compose up -d
   ```
   This brings up Postgres, Kafka, Kafka Connect, and Kafka UI (`localhost:8080`).

2. **Register the Debezium connector:**
   ```
   curl -X POST -H "Content-Type: application/json" \
     --data "@register-postgres-connector.json" \
     http://localhost:8083/connectors
   ```

3. **Run Order Service** (from `order-service/`):
   ```
   mvn spring-boot:run
   ```
   Runs on port `8081`.

4. **Run Audit Service** (from `audit-service/`):
   ```
   mvn spring-boot:run
   ```
   Runs on port `8082`.

5. **Submit a trade:**
   ```
   curl -X POST -H "Content-Type: application/json" \
     --data '{"accountId":"ACC-100","counterparty":"Some Counterparty","countryCode":"IR","tradeType":"BUY","notionalAmount":50000,"currency":"USD"}' \
     http://localhost:8081/api/v1/trades
   ```
   A trade to a sanctioned country (`IR`, `KP`, `SY`, `CU`) or over the configured notional threshold will trigger a compliance alert. Watch it flow through `trade-events.v1` → Audit Service → `compliance-alerts.v1` in Kafka UI.

## Compliance rules

Configured per-service in `audit-service`'s properties, not hardcoded:

- **Sanctioned country** — counterparty country code matches a configured list (illustrative list by default; intended to be swapped for a real OFAC SDN snapshot).
- **Notional threshold** — trade amount exceeds a configured limit.
- **Velocity** — too many trades from one account within a configured rolling time window.

## Status

- ✅ **Order Service** — trade ingestion via the transactional outbox pattern, proven end-to-end (Postgres → Debezium → Kafka).
- ✅ **Audit Service** — idempotent Kafka consumer, rule engine, SSE endpoint, proven end-to-end (a flagged trade correctly produces a compliance alert).
- ⏳ **Dashboard** — a live frontend subscribing to the SSE alert stream. Not yet built.

See `PROGRESS.md` for the full build log, including the real infrastructure bugs encountered along the way (Flyway baselining conflicts, Kafka listener/hostname mismatches, and a Debezium JSON double-encoding issue) and how each was diagnosed and fixed.
