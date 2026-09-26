# Compliance Audit Pipeline — Progress Log

Running summary of everything done on this project so far. Updated after each relevant step.

## Where things stand

- **Repo**: [github.com/VijayPras/compliance-audit-pipeline](https://github.com/VijayPras/compliance-audit-pipeline), cloned locally to `Audit Pipeline Project/compliance-audit-pipeline`.
- **Infra (Docker)**: Postgres, Kafka, Kafka Connect (Debezium), Kafka UI — all running and healthy locally, Kafka and Postgres both on persistent named volumes now.
- **Order Service**: Spring Boot app scaffolded, builds clean, runs, and has successfully processed real trades end-to-end — confirmed all the way through to a message landing on `trade-events.v1` in Kafka UI.
- **Audit Service**: Spring Boot app scaffolded and running — idempotent consumer, rule engine, SSE endpoint. Fully proven end-to-end: a flagged trade correctly produces an alert on `compliance-alerts.v1`.
- **Dashboard**: built and working — a Vite + React app (`dashboard/`) subscribing to Audit Service's SSE stream via a native `EventSource`, rendering compliance alerts live as they arrive, no polling/refresh. Confirmed working against a real submitted trade.

**The entire build plan is now complete and demoable end-to-end**: submit a trade via Order Service → it's recorded via the transactional outbox pattern → Debezium/Kafka Connect picks it up via CDC → published to `trade-events.v1` → Audit Service consumes it idempotently, runs it through the rule engine → publishes an alert to `compliance-alerts.v1` if flagged → the alert appears live on the dashboard within about a second, no manual refresh.

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

**Result**: Debezium connector registered successfully; a real trade was submitted via `POST /api/v1/trades` and got a `201` back with a generated trade ID, confirming the trade committed to Postgres — and, after the additional debugging in section 5 below, confirmed visually in Kafka UI as a message on `trade-events.v1`. **Phase 1 is fully done end-to-end.**

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

**First run hit an error**: `org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: missing table [audit_results]`. Root cause found via the full startup log + a manual `psql` check of `flyway_schema_history_audit_service`: Flyway never actually ran `V1__init.sql` — it **baselined** instead (`installed_rank 1, type BASELINE, description << Flyway Baseline >>, version 1`). Reason: `spring.flyway.baseline-on-migrate=true` triggers whenever Flyway sees a non-empty schema with no history table yet, and it checks at the *whole-schema* level, not per-service — since audit-service shares the same Postgres `public` schema as order-service (which already had `trades` and `outbox_events` in it), Flyway saw a non-empty schema on audit-service's first run and baselined. `baseline-version` defaults to `1`, and `V1__init.sql` is also version 1, so Flyway treated its own real migration as "already applied" and silently skipped it — no tables, no error until Hibernate's validation caught the gap.

**Fix**: added `spring.flyway.baseline-version=0` to `audit-service/src/main/resources/local_auditpipeline.properties`, so the baseline stamps as version 0 and the real `V1__init.sql` (version 1) still runs afterward, plus dropped the stale `flyway_schema_history_audit_service` table so Flyway re-evaluated from scratch. **Confirmed working** — audit-service now migrates cleanly and starts.

**Second issue hit on first real run — `UnknownHostException: kafka`**: audit-service runs on the host machine (not inside Docker), but Kafka's only listener was advertised as `kafka:9092`, a hostname that only resolves inside the Docker network. Fixed by adding a second Kafka listener (`PLAINTEXT_HOST` on `9094`, advertised as `localhost:9094`) already present in `docker-compose.yml`'s `KAFKA_ADVERTISED_LISTENERS`, but the `ports:` section wasn't actually publishing `9094` out of the container — added `"9094:9094"` to the `kafka` service's `ports`, and pointed `audit-service`'s `spring.kafka.bootstrap-servers` at `localhost:9094` instead of `9092`. **Confirmed working.**

**Third issue — Debezium connector silently stopped publishing anything.** Root cause: the `kafka` service in `docker-compose.yml` had no persistent volume, so recreating the container (to pick up the port change above) wiped every Kafka topic, including Kafka Connect's own internal config/offset/status topics where the connector's registration lived. Fixed two ways: (1) added a named `kafka-data` volume mounted at `/var/lib/kafka/data` (with `KAFKA_LOG_DIRS` pointed at the same path) so future container recreations don't lose topic data; (2) re-registered the connector via the same `register-postgres-connector.json` POST used originally. Along the way also fixed a secondary Kafka Connect issue — its internal `status`/`offset` topics got auto-created with only 1 partition (Kafka's bare default) while Connect expected more, causing a log-spamming `UNKNOWN_TOPIC_OR_PARTITION` warning loop; fixed by explicitly setting `OFFSET_STORAGE_PARTITIONS` / `STATUS_STORAGE_PARTITIONS` to `"1"` on the `kafka-connect` service so the counts match on creation.

**Fourth issue — the real blocker, found once the connector was healthy and actually processing a trade**: the task crashed immediately with `DataException: Field 'created_at' is not of type INT64` inside Debezium's outbox `EventRouterDelegate`. Root cause: `register-postgres-connector.json` mapped `transforms.outbox.table.field.event.timestamp` to `created_at`, but that column is `TIMESTAMPTZ` in Postgres, which Debezium represents internally as a `ZonedTimestamp` (a string), not the `INT64` epoch-millis type the outbox router's timestamp feature requires (that only works cleanly against a timezone-less `TIMESTAMP` column). Since nothing downstream actually reads the Kafka record's own timestamp (audit-service parses `createdAt` straight out of the JSON payload instead), fixed by simply removing the `transforms.outbox.table.field.event.timestamp` line from `register-postgres-connector.json` and re-registering the connector. **Confirmed working** — `trade-events.v1` now shows a real message in Kafka UI for a submitted trade.

**Result**: the full CDC pipe (Postgres → Debezium → Kafka) is proven working end-to-end again, this time with audit-service's consumers also up and listening.

**Fifth issue — found on the very next step (a real trade actually reaching audit-service)**: audit-service's consumer crashed with `MismatchedInputException: Cannot construct instance of TradeEventPayload ... no String-argument constructor`, and the Kafka UI message value showed `"{\"status\": \"EXECUTED\", ...}"` — a JSON string wrapping more JSON text, i.e. **double-encoded JSON**. Root cause: `outbox_events.payload` is `JSONB`, which Debezium represents as a plain Kafka Connect `STRING` field holding raw JSON text. The outbox router passes that string straight through as the record value, but with `value.converter: JsonConverter` and schemas disabled, `JsonConverter` doesn't know that string is "already JSON" — it serializes it as a quoted JSON string, escaping everything inside. **Fix**: added `"transforms.outbox.table.expand.json.payload": "true"` to `register-postgres-connector.json`, so the outbox router parses the payload into a real structured object before handing it to the converter, and deleted + re-registered the connector. **Confirmed working** — `trade-events.v1` values now render as clean, unescaped JSON objects.

**Sixth issue — same double-encoding bug, but on the Kafka record *key* this time**: with the payload fixed, audit-service's consumer immediately hit a new error, `IllegalArgumentException: UUID string too large` in `AuditProcessingService.process()`. Kafka UI showed the message *key* wrapped in quotes (`"c2570825-...454"`, 38 characters instead of 36). Root cause: the connector's `key.converter` was also `JsonConverter`, and the outbox record's key (the `aggregate_id` column — a plain UUID string, not JSON) got the same quoting treatment as the payload had. Since the key genuinely isn't JSON, there's no "expand" option for it — **fix**: changed `key.converter` from `org.apache.kafka.connect.json.JsonConverter` to `org.apache.kafka.connect.storage.StringConverter` in `register-postgres-connector.json` (and dropped the now-irrelevant `key.converter.schemas.enable` line), then deleted + re-registered the connector. **Confirmed working** — Kafka UI now shows a clean, unquoted UUID as the key (`Key Serde: String, Size: 36 Bytes`).

Also created for the repo: `README.md` (public-facing project README — architecture, tech stack, setup instructions, status) and `PROJECT_OVERVIEW.md` (plain-language explanation of the project's purpose and plan), both committed to the connected local repo folder.

**Phase 2 is now fully proven end-to-end.** Confirmed via a real sanctioned-country trade: audit-service logged `Processed trade 9b013489-... -- flagged=true reasons=[SANCTIONED_COUNTERPARTY_COUNTRY]`, and Kafka UI shows the corresponding alert on `compliance-alerts.v1` with the full trade details and `reasons: ["SANCTIONED_COUNTERPARTY_COUNTRY"]`. The whole pipeline — outbox write → Debezium CDC → Kafka → idempotent consumer → rule engine → alert topic — works.

## 6. Dashboard (Phase 3)

Built `dashboard/` — a Vite + React app, chosen over a plain HTML file to match the original architecture doc and read as a complete standalone frontend project on the resume/repo. Installed Node.js via `winget install OpenJS.NodeJS.LTS` (same pattern as the earlier Maven install) since Vijay didn't have Node set up.

- `src/App.jsx` — opens a native browser `EventSource` connection to `http://localhost:8082/api/v1/compliance/stream` (no polling, no WebSocket handshake), listens for the named `compliance-alert` SSE event, and renders each alert as a card (trade amount, account → counterparty/country, and which rules it tripped) with the newest on top and a small slide-in animation. Shows a live connection-status badge (connecting / live / reconnecting — the browser's `EventSource` auto-reconnects on its own on a dropped connection, no manual reconnect logic needed).
- Plain CSS, dark theme, no extra UI framework — kept deliberately lightweight since the point is the real-time behavior, not the styling.
- **CORS fix required**: the dashboard (`localhost:5173`, Vite's dev server) and audit-service (`localhost:8082`) are different origins, so the browser blocked the `EventSource` connection until `@CrossOrigin(origins = "http://localhost:5173")` was added to `ComplianceStreamController.stream()`. Scoped to the Vite dev port deliberately — noted in the code as a local-dev-only allowance, not something to carry into a real deployment as-is.
- One small process hiccup along the way: ran `mvn spring-boot:run` from inside `dashboard/` (a Node project, no `pom.xml`) instead of `audit-service/`, producing a `No plugin found for prefix 'spring-boot'` error — not a bug, just wrong working directory; resolved immediately once pointed out.

**Confirmed working**: submitted a real trade, watched the alert appear on the dashboard live (`$50,000.00`, `ACC-100 → Some Counterparty (IR)`, `Sanctioned counterparty country` pill) within about a second of the trade being processed, no refresh.

## Project complete

All three phases of the original build plan are done and proven end-to-end:

1. **Order Service** — trade ingestion via the transactional outbox pattern.
2. **Audit Service** — Debezium CDC → Kafka → idempotent consumer → rule engine → alert topic.
3. **Dashboard** — live SSE-driven UI showing alerts the moment they're raised.

## Possible next steps (optional, not required for the project to be "done")

- Swap the illustrative sanctioned-country list for a real OFAC SDN snapshot, per the original build plan's "genuine use case" idea.
- Add a few more rule types, or make the velocity/threshold rules visible/configurable from the dashboard itself.
- Write a couple of integration tests (e.g. for `AuditProcessingService`'s idempotency check, or `RuleEngine`'s rule logic) — good for demonstrating test discipline in an interview.
- Commit and push everything to GitHub (`git add` / `git commit` / `git push`) — hasn't been done yet; the repo has only been worked on locally so far.
- Tidy up: delete the old `order-service/application.yml` (still pending from the earlier config refactor), and clean up stray files in the repo root (`logs.txt`, `currentlogs.txt`, `hs_err_pid15656.log`, `.gitignore.txt` — probably meant to be `.gitignore`).
