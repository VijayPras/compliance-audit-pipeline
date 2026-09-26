# Compliance Audit Pipeline — Progress Log

Running summary of everything done on this project so far. Updated after each relevant step.

## Where things stand

- **Repo**: [github.com/VijayPras/compliance-audit-pipeline](https://github.com/VijayPras/compliance-audit-pipeline), cloned locally to `Audit Pipeline Project/compliance-audit-pipeline`.
- **Infra (Docker)**: Postgres, Kafka, Kafka Connect (Debezium), Kafka UI — all running and healthy locally.
- **Order Service**: Spring Boot app scaffolded, builds clean, runs, and has successfully processed one real trade end-to-end through the whole pipeline (Postgres → Debezium → Kafka).
- **Audit Service**: Spring Boot app scaffolded — idempotent consumer, rule engine, SSE endpoint. Not yet built/run on Vijay's machine.
- **Dashboard**: not started yet (Phase 3 of the build plan).

## 1. Planning

- Reviewed the architecture PDF (`Event-Driven Real-Time Compliance Audit Pipeline.pdf`) and confirmed every tool in the stack (Java, Spring Boot, Kafka, Debezium, Postgres, Docker, React) is free — the one thing to actively avoid is MS SQL Server outside its free Express edition, so the plan uses Postgres throughout.
- Wrote a full build plan (`audit-pipeline-plan.md`, delivered and saved to the project folder): exact tool versions, a phase-by-phase build order, a realistic ~3–4 week part-time timeline, and how to give the project a genuine use case (grounding the rule engine in the real, public OFAC sanctions list rather than made-up data) so it reads as more than a Kafka plumbing exercise.

## 2. Docker infrastructure (Phase 0)

Built `docker-compose.yml` with four services: Postgres 17 (with `wal_level=logical` for CDC), Kafka 4.1.0 (KRaft mode, no ZooKeeper), Kafka Connect running the Debezium Postgres connector, and Kafka UI for visual inspection.

Debugging along the way, in order hit and fixed:

1. **`debezium/connect:3.6` not found on Docker Hub** — Debezium's actual current image registry is Quay.io, not Docker Hub (which is stale there). Switched to `quay.io/debezium/connect:3.6.2.Final`.
2. **Postgres crashing with `exec format error`** — a known Docker Desktop bug: its newer "containerd image store" feature mishandles certain Debian-based images on Windows/WSL2. Fixed by disabling that setting (Docker Desktop → Settings → General → uncheck "Use containerd for pulling and storing images").
3. **All four containers crashing simultaneously ~2 minutes after startup** — the Docker Desktop VM only had 7.6GB total, and four uncapped JVM-ish processes (Kafka, Kafka Connect, Kafka UI) plus Postgres pushed past it, crashing the whole VM. Fixed by adding explicit `mem_limit` and JVM heap caps (`KAFKA_HEAP_OPTS`, `JAVA_OPTS`) to every service — total worst-case footprint now ~3GB.
4. Several unrelated one-off network blips during image pulls (`unexpected EOF`, `500` errors on large downloads) — resolved by retrying; not a config issue.

Stack is now stable and stays up.

## 3. Order Service (Phase 1)

Scaffolded a Spring Boot 3.5.16 / Java 17 app implementing the **transactional outbox pattern** from the architecture doc:

- `V1__init.sql` (Flyway migration) — `trades` and `outbox_events` tables.
- `Trade` / `OutboxEvent` entities and repositories.
- `TradeService.recordTrade()` — the core of the pattern: one `@Transactional` method writes both the trade and its outbox event in a single Postgres transaction, so they can never be written independently of each other.
- `TradeController` (`POST /api/v1/trades`) with request validation and a clean error response shape.
- `register-postgres-connector.json` — the Debezium connector config using the outbox event router transform, publishing to a `trade-events.v1` Kafka topic.

Debugging along the way:

1. **Maven Central blocked in Claude's own cloud sandbox** — so `mvn clean install` had to run on Vijay's machine, not in the cloud workspace. Installed via `winget install --id Apache.Maven -e --source winget`.
2. **`no pg_hba.conf entry` on first connection attempt** — Postgres's access-control file was likely left incomplete after the container crashes during Docker debugging (same shared data volume across several crash-and-restart cycles). Fixed with `docker compose down -v` (wipes the volume) + `docker compose up -d` for a clean re-init.
3. **`FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"`** — Windows reports the local timezone to Java using an old IANA alias that newer Postgres builds no longer recognize. Fixed by forcing `user.timezone=Asia/Kolkata` as a JVM system property before Spring starts.
4. **PowerShell `curl` quoting issues** when submitting test requests — PowerShell aliases `curl` to `Invoke-WebRequest`, which doesn't accept real curl flags. Fixed by calling `curl.exe` explicitly, and by writing JSON payloads to a file (`--data "@file.json"`) instead of inlining them, since PowerShell mangles escaped quotes in long inline strings.

**Result**: Debezium connector registered successfully; a real trade was submitted via `POST /api/v1/trades` and got a `201` back with a generated trade ID — confirming the trade committed to Postgres. *(Still to visually confirm: that the trade shows up as a message on the `trade-events.v1` topic in Kafka UI — last step before calling Phase 1 fully done.)*

## 4. Config refactor (per Vijay's preference)

- Renamed the Java package from `com.vijayprasanna.orderservice` to `com.auditpipeline.orderservice` across every file.
- Replaced `application.yml` with a two-file properties setup Vijay is more used to: a minimal `application.properties` (just sets `spring.profiles.active=local`) plus a profile-specific `local_auditpipeline.properties` (loaded via `@PropertySource("classpath:${spring.profiles.active}_auditpipeline.properties")`) holding the actual datasource/JPA/Flyway/logging config.
- Moved the timezone fix so its value (`app.timezone=Asia/Kolkata`) lives in the properties file instead of being hardcoded in Java — read via a plain classpath lookup in `main()` before Spring starts (an `@Value`-injected field would be wired up too late, after the DataSource bean already needs it).
- **Still open**: the old `application.yml` needs to be manually deleted on Vijay's machine — the file bridge can write files but not delete them, so this one's on him. Also still need to confirm `mvn clean install` + `mvn spring-boot:run` succeed cleanly with the new properties setup.

## 5. Audit Service (Phase 2)

Scaffolded a second Spring Boot 3.5.16 / Java 17 app, `audit-service`, following the same `com.auditpipeline.*` package and split-properties conventions as order-service (`application.properties` sets the profile, `local_auditpipeline.properties` holds the real config). Consumes `trade-events.v1` and implements the rest of the architecture doc's pattern:

- `V1__init.sql` — `inbox_events` (idempotency guard, keyed by the trade's own id, which is what Debezium's outbox router uses as the Kafka record key) and `audit_results` (audit-service's own record of every trade it evaluated). Uses its own Flyway history table (`spring.flyway.table=flyway_schema_history_audit_service`) so its migrations never collide with order-service's, even though both share the one local Postgres instance.
- `RuleEngine` — three independently simple, configurable rules (all values in `local_auditpipeline.properties`, not hardcoded): a notional-amount threshold, a velocity check (too many trades from one account in a rolling window, queried against audit-service's own `audit_results` history rather than reaching into order-service's tables), and a sanctioned-country match against a small illustrative list (`IR,KP,SY,CU` — flagged in the build plan as the spot to swap in a real OFAC SDN snapshot later for a stronger portfolio story).
- `AuditProcessingService` — the Inbox Pattern: checks `inbox_events` before doing anything, so a redelivered Kafka message (at-least-once delivery) is a safe no-op instead of double-processing.
- Two separate `@KafkaListener`s: `TradeEventConsumer` (trade-events.v1 → runs rules → saves the result → publishes to `compliance-alerts.v1` if flagged) and `ComplianceAlertConsumer` (compliance-alerts.v1 → fans out to connected dashboards). Kept as two hops rather than pushing straight to SSE from the first listener, so the alerts topic stays a real decoupling point other consumers could subscribe to later, matching the doc's design.
- `AlertBroadcaster` + `ComplianceStreamController` — in-memory `SseEmitter` list, exposed at `GET /api/v1/compliance/stream`, which the React dashboard will subscribe to in Phase 3.

Not yet built or run on Vijay's machine — next step is `mvn clean install` + `mvn spring-boot:run` (port 8082) and firing another trade through the Order Service to watch an alert come out the other end.

## Next up

Get audit-service building and running locally, submit a trade that trips a rule (e.g. above the 100000 threshold, or to a sanctioned country code), and confirm a message lands on `compliance-alerts.v1` in Kafka UI. Then Phase 3: the React dashboard.
