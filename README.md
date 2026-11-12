# TutorConnect — Milestone 3

A tutoring appointment system built with Java 17+, Spring Boot 4.1.1, Spring Security, Thymeleaf, JDBC (`JdbcTemplate`) and MySQL. No ORM. Customers find and book sessions; tutors manage service-specific availability. Booking remains correct when two users request the same slot.

## Run with MySQL (recommended)

Requirements: Docker Desktop / Compose for the container run; JDK 17+ and Maven for host development and tests.

```sh
docker compose up -d --build --wait
```

Open http://localhost:8080. The database listens on local port 3307. Compose initializes the schema and demo data only when its persistent volume is first created; restarting the application does not delete bookings. `docker compose down` stops the database and preserves the volume.

For host-based development, run `docker compose up -d --wait db` followed by `mvn spring-boot:run`. Do not run the host app and Compose app on the same HTTP port.

For an existing MySQL server, create a **new empty** `tutorconnect` database, execute `src/main/resources/schema-mysql.sql`, optionally execute `seed.sql`, then supply `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`. The Milestone 1 H2 schema is not a migration target: its old unconditional unique slot constraint must not be retained alongside the new active-slot constraint. Times are entered and displayed in UTC; the default JDBC URL sets the MySQL session to UTC.

## Quick demo without Docker

```sh
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

This uses disposable in-memory H2 data. It is convenient for viewing the app; the submission concurrency evidence also uses MySQL.

| Role | Email / username | Demo password |
| --- | --- | --- |
| Customer | maya.patel@example.com | TutorDemo123! |
| Customer | sam.rivera@example.com | TutorDemo123! |
| Provider | priya.shah@example.com | TutorDemo123! |
| Provider | jordan.lee@example.com | TutorDemo123! |

Demo credentials are public local-development examples. The database contains BCrypt hashes, never plaintext passwords.

## Web interface

- `/`: overview and links to available sessions.
- `/login`: email/password login; server-side session and POST logout.
- `/slots`: available future slots, provider/service/date filters and pagination.
- `/customer/book/{slotId}`: booking form; POST confirms the appointment.
- `/customer/confirmation/{appointmentId}`: owner-only confirmation.
- `/customer/appointments`: upcoming sessions, history and owner-only cancellation.
- `/provider/dashboard`: create/remove own availability and view booked sessions.

Slots belong to services. A tutor's overlapping slots are rejected even across different services, and slot length must equal service duration. Removing an unbooked slot deactivates it, preserving foreign keys and cancellation history. Slots with BOOKED/COMPLETED appointments cannot be removed. Past booked appointments become COMPLETED when their appointment list is viewed; this is deliberately lazy rather than a background scheduler.

## Request flow and APIs

```text
Browser form → Spring Security (session, role, CSRF)
 → Controller → Service → Repository → JdbcTemplate → MySQL
 → DTO / model → Thymeleaf HTML or JSON → Browser
```

Controllers receive requests. Services enforce business rules and transactions. Repositories execute parameterized SQL. Account ID and role come from the authenticated JDBC-loaded principal, never from a submitted customer ID.

| Method | Endpoint | Access / result |
| --- | --- | --- |
| GET | `/api/home` | Public, database overview (Milestone 1 JSON retained here) |
| GET | `/api/slots?providerId=1&serviceId=1&date=2026-10-10&page=0&size=6` | Public, `{items,page,size,total}`; SQL LIMIT/OFFSET |
| GET | `/api/customer/appointments` | Customer, own appointments |
| POST | `/api/customer/appointments` | Customer, form fields `slotId`, `serviceId`; 201 with appointment ID |
| POST | `/api/customer/appointments/{id}/cancel` | Customer and owner, 200 |
| GET | `/api/provider/appointments` | Provider, own appointments |
| POST | `/api/provider/slots` | Provider, form fields `serviceId`, `startTime`, `endTime`; 201 |
| POST | `/api/provider/slots/{id}/remove` | Provider and owner, 200 |

API clients must log in using the session cookie and send a CSRF token (`X-CSRF-TOKEN`) for all POSTs. The HTML forms include the token automatically. Missing authentication returns 401 for APIs and a login redirect for pages. Wrong role/owner or invalid CSRF returns 403. Invalid input returns 400, missing records 404, occupied/overlapping slots 409, and exhausted transient lock retries 503. Client error responses never contain stack traces.

## Transaction and concurrency design

Two customers can both observe an available slot before either inserts a booking. An ordinary check followed by INSERT can therefore race.

`BookingTransaction.book` uses `@Transactional(isolation = Isolation.READ_COMMITTED)`, locks the existing availability row with `SELECT ... FOR UPDATE`, checks availability again, and inserts the appointment. Cancellation and removal lock the same slot before changing its state. The losing transaction waits, then sees the committed reservation and receives 409. The lock belongs to the database and works across application processes, not just threads in one JVM.

The database slot backstop preserves historical cancellations:

```sql
active_slot_id BIGINT GENERATED ALWAYS AS
  (CASE WHEN status IN ('BOOKED','COMPLETED') THEN slot_id ELSE NULL END) STORED,
CONSTRAINT uq_appointment_active_slot UNIQUE (active_slot_id)
```

`slot_id` remains the immutable historical FK. A CANCELLED record has a NULL active slot; MySQL permits multiple NULLs in a unique index. Therefore a new booking gets a new appointment ID while the old customer's cancelled record remains visible. Simply marking the old row CANCELLED under Milestone 1's `UNIQUE(slot_id)` would block rebooking forever.

READ_COMMITTED avoids dirty reads; the explicit row lock serializes changes for one slot. The service facade retries transient pessimistic lock failures up to three attempts, with short bounded delays, entering a **fresh** transaction through a separate Spring bean each time. A business conflict or duplicate-key conflict is returned as 409 without retry.

ACID: Atomicity rolls back failed mutations; Consistency enforces FKs, time/status checks and active-slot uniqueness; Isolation uses READ_COMMITTED plus explicit locking; Durability uses MySQL InnoDB and a persistent Compose volume. H2 demo data is intentionally disposable.

## Tests

```sh
mvn test
```

The suite includes service unit tests plus real HTTP integration tests with JDBC-loaded credentials, independent session cookies, CSRF, role/owner checks, cancellation/rebooking, pagination, provider operations and two concurrent customer requests. The concurrency test starts two worker threads behind a latch and asserts statuses **201 and 409**, then verifies one active appointment in the database.

To repeat against MySQL, create a dedicated empty `tutorconnect_test` database and grant the test account access:

```sh
TEST_DB_URL='jdbc:mysql://localhost:3307/tutorconnect_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
TEST_DB_USERNAME=tutorconnect TEST_DB_PASSWORD=tutorconnect-demo \
sh scripts/test-mysql.sh
```

The test profile resets its database. The script restricts the target database to `tutorconnect_test`; never point test configuration at the application database. GitHub Actions verifies both H2 and MySQL 8.4 on Java 17.

## Submission

See `docs/milestone2-report.md`, the accompanying PDF report, and `docs/code-walkthrough.md`. Record your own code walkthrough of at least five minutes: explain the code, transactions, SQL, RBAC and tests, rather than only clicking through the app. Submit the report/source ZIP, GitHub repository link and your video link. Check course General Rules separately; they were not included in the provided Milestone 2 document.


## Milestone 3 — integration and deployment

See [Chinese run/recording guide](docs/milestone3/README_zh.md), [design](docs/milestone3/design.md), [report](docs/milestone3/report.md), [distribution diagram](docs/milestone3/distribution.png), [validation](docs/milestone3/validation.md), and [walkthrough speech](docs/milestone3/speech.md).

The existing booking/cancellation transaction now also inserts a durable notification outbox event. After commit, a notification Gateway posts one coarse-grained DTO (`eventId`, `recipient`, `type`, `message`) to a mock second controller at `/internal/notifications`. The internal endpoint uses a shared service key and durable event-ID/payload deduplication; it sends no real messages. The ordinary browser Session, role restrictions and CSRF protections remain enabled.

The Gateway bounds connection and complete-response time, makes up to three attempts using the same event ID, and falls back to persisted PENDING state without rolling back the booking. A recovery worker retries due events every 15 seconds in batches of 20, ordered by next attempt time. Non-retryable HTTP responses become FAILED for manual review. See the report for delivery guarantees and recovery limits.

`GET /health` checks `SELECT 1`, returning 200/UP or 503/DOWN. `GET /api/provider/metrics` exposes Micrometer booking success/failure counts, cancellation and notification counts, average service booking latency, and durable queue counts. Metrics require a provider session. Logs use event/appointment IDs, types and failure categories; notification content, recipient addresses and service tokens are not logged by the new notification code.

```sh
# Dedicated MySQL database must be set up first (see Chinese guide).
sh scripts/test-mysql.sh '-Dtest=NotificationDownTests'
sh scripts/test-mysql.sh '-Dtest=Milestone3IntegrationTests'

# End-to-end smoke against a fresh running demo app.
python3 scripts/smoke.py --base-url http://localhost:8080
```

The multi-stage Dockerfile compiles on Java 17, runs as a non-root user and includes a Java health probe. `docker-compose.yml` now runs app + MySQL while preserving the prior Compose project and volume names. New notification tables are created additively at startup, including on existing volumes. GitHub Actions verifies H2/MySQL tests and builds/smokes the image on each push or pull request. A hosted CI run is separate from local validation and is not implied until this branch is published.

## Milestone 4 Enterprise AI

Spring AI adds a customer assistant at `/customer/assistant`, server-enforced confirmation,
vector-grounded policy answers, and scheduled personalized reminders through the existing
notification outbox. Providers can run their reminder agent from the dashboard.

The original app works with AI disabled. To enable real Gemini calls, copy `.env.example`
to `.env`, set your Google AI Studio key locally, then run `docker compose up -d --build --wait`.
Never commit `.env` or put a key in a report, screenshot or video. Model names can be overridden
with `GEMINI_CHAT_MODEL` and `GEMINI_EMBEDDING_MODEL`.

See [the Milestone 4 run guide](docs/milestone4/README_zh.md) for setup, test commands,
manual-trigger behavior and the recording checklist. A missing key yields HTTP503 from AI
operations while ordinary booking and health endpoints remain available.
