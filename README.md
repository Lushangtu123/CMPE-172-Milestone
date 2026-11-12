# TutorConnect Milestone 4

TutorConnect is a tutoring appointment system with a Gemini-powered scheduling assistant, policy Q&A and scheduled study reminders. It uses Java 17, Spring Boot 4.1.1, Spring AI 2.0.2, Spring Security, Thymeleaf and MySQL. Database access uses parameterized SQL through JdbcTemplate.

Customers find sessions and manage their appointments. Providers manage availability and upcoming appointments. Both normal and AI-confirmed bookings use the same transactional service to prevent double-booking.

[PDF report](docs/milestone4/report.pdf) · [Editable Word report](docs/milestone4/report.docx) · [Validation evidence](docs/milestone4/validation.md)

## Milestone 4 features

- **Conversational agent.** Spring AI ChatClient calls service-backed tools to check availability, list the customer's appointments and propose bookings or cancellations. The assistant can use several tools in one turn.
- **Explicit confirmation.** The customer reviews the course, tutor and UTC time, then clicks Confirm booking or Confirm cancellation. The server checks the session-bound token, role, ownership and expiry before calling the transactional service.
- **Autonomous reminders.** A scheduled agent scans upcoming appointments, asks Gemini for a personalized reminder and queues it through the notification outbox. Providers can manually run their own reminder scan from the dashboard.
- **Grounded policy answers.** Policy and FAQ passages are embedded with Gemini and retrieved through Spring AI SimpleVectorStore. Answers cite source IDs from the knowledge file.
- **Persistent chat history.** Successful conversations are stored in MySQL and remain available after New chat or another login. Customers can read their own transcripts through Chat history. Recent chats appear in the desktop sidebar, with a history panel on mobile.
- **Responsive workflows.** Long conversations scroll independently while the composer and confirmation stay visible. History preserves the draft. Provider availability forms calculate the end time from the service duration and keep submitted values beside inline errors.

The model context keeps the latest 20 messages in the login session. Pending actions also belong to that session and expire after five minutes. Saved transcripts contain messages and confirmation results. Each chat turn is bounded to eight model rounds and ten tool calls.

## Run with MySQL (recommended)

Requirements: Docker Desktop / Compose for the container run; JDK 17+ and Maven for host development and tests.

To enable real Gemini responses, create a local environment file on the first run.

```sh
test -f .env || cp .env.example .env
```

Open `.env` in an editor, set your Google AI Studio key, and configure the model.

```dotenv
AI_ENABLED=true
GEMINI_API_KEY=<your Google AI Studio key>
GEMINI_CHAT_MODEL=gemini-3.1-flash-lite
GEMINI_EMBEDDING_MODEL=gemini-embedding-001
AI_REMINDERS_ENABLED=true
```

The local deployment was verified with `gemini-3.1-flash-lite`. The checked-in example defaults to `gemini-3.8-flash`, which timed out for this account. Choose a model available to your account. The Gemini client uses the Developer API with `vertexAI(false)`. Configure the API key through the environment.

Start the application and database.

```sh
docker compose up -d --build --wait
curl --fail http://localhost:8080/health
```

The repository and Docker build exclude `.env`. Set `AI_ENABLED=false` to use the ordinary booking features without a Gemini key.

Open [TutorConnect](http://localhost:8080). The database listens on local port 3307. Compose initializes the schema and demo data only when its persistent volume is first created; restarting the application does not delete bookings. `docker compose down` stops the database and preserves the volume.

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

Demo credentials are public local-development examples. The database stores BCrypt password hashes.

Existing database volumes retain their original availability dates. If the next seven days have no available sessions, sign in as a provider and add a future slot before demonstrating an AI booking. All dates and times in the app use UTC.

## Web interface

- `/`: overview and links to available sessions.
- `/login`: email/password login; server-side session and POST logout.
- `/slots`: available future slots, provider/service/date filters and pagination.
- `/customer/book/{slotId}`: booking form; POST confirms the appointment.
- `/customer/confirmation/{appointmentId}`: owner-only confirmation.
- `/customer/appointments`: upcoming sessions, history and owner-only cancellation review.
- `/customer/assistant`: scheduling chat, structured confirmation and recent conversations.
- `/customer/assistant/history`: owner-only saved conversations and read-only transcripts.
- `/provider/dashboard`: provider login destination, upcoming appointments, availability, removal review and AI reminder tools.

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

## AI endpoints and knowledge source

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/api/customer/ai/chat` | Submit a message and run the tool loop |
| GET | `/api/customer/ai/history` | Read the current session context and pending action |
| POST | `/api/customer/ai/confirm` | Confirm the pending server-owned action using its token |
| POST | `/api/customer/ai/discard` | Discard the pending action |
| POST | `/api/customer/ai/reset` | Start a new session conversation while retaining saved transcripts |
| POST | `/api/provider/ai/reminders/run` | Run a reminder scan for the authenticated provider |

Customer and provider routes enforce their respective roles. POST requests require the session cookie and CSRF token.

The knowledge file is [tutorconnect-policies.md](src/main/resources/knowledge/tutorconnect-policies.md). AiKnowledgeService splits it into passages, creates Gemini embeddings and retrieves up to three matches from SimpleVectorStore. The vector index is rebuilt on demand after an app restart.

The reminder scheduler runs every 60 seconds and considers appointments in the next 24 hours. For a reminder demonstration, create and book a slot about two hours ahead, then open **Tools and history → AI study reminders** on the provider dashboard. The outbound action uses the existing mock notification receiver and durable outbox.

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

Java 17 and Maven run the backend suite. Node.js runs the frontend regression tests.

```sh
mvn verify
node --test scripts/tests/assistant.test.cjs
```

The latest local validation on November 11, 2026 passed **89 Java tests on H2**, **89 Java tests on MySQL 8.4**, and **4 JavaScript tests**. Both Java runs had zero failures, errors or skipped tests.

Run the AI concurrency regression directly.

```sh
mvn '-Dtest=AiWorkflowTests#twoAgentCustomersExactlyOneWinsAfterConfirmation' test
```

Two authenticated customers obtain proposals for the same slot and confirm concurrently. Readiness/start latches coordinate the requests. The test asserts HTTP **200 and 409**, then checks that the database contains exactly one active appointment. The normal booking concurrency test expects **201 and 409**.

Other tests cover confirmation tokens, RBAC, CSRF, saved-history ownership and re-login, invalid-date recovery, provider form errors, RAG retrieval, reminder scheduling, delivery and deduplication. Repeatable tests use scripted external model responses while exercising the actual Spring AI tool loop, HTTP sessions and database.

Set up the dedicated MySQL test database, then run its suite.

```sh
docker compose up -d --wait db
docker compose exec -T -e MYSQL_PWD=root-demo-only db mysql -uroot -e "CREATE DATABASE IF NOT EXISTS tutorconnect_test; GRANT ALL PRIVILEGES ON tutorconnect_test.* TO 'tutorconnect'@'%';"
sh scripts/test-mysql.sh
```

The test profile resets `tutorconnect_test`. The script restricts its target to that database.

With Gemini configured, run a live policy query.

```sh
python3 scripts/ai-live-smoke.py
```

GitHub Actions is configured to run H2/MySQL verification, frontend tests and a container smoke test. Hosted results are listed on the [Actions page](https://github.com/Lushangtu123/CMPE-172-Milestone/actions).

## AI request troubleshooting

The browser displays progress and stops waiting for a chat after 45 seconds. Inspect `docker compose logs --tail=100 app` for a failed request. Logs include the failure category, tool and cause type.

The availability tool specifies calendar dates in `yyyy-MM-dd` format. If a model supplies an invalid date, the tool loop asks it to correct the parameters and retry within the round limit. The assistant reports the actual availability when a query succeeds. Providers can add new availability when the requested range is empty.

After changing `.env`, recreate the application container to load the new environment.

```sh
docker compose up -d --no-deps --wait app
```

If an explicit confirmation request times out, check My appointments before retrying. A browser timeout can occur after the server commits a booking.

## Milestone 4 submission

Package the PDF report and complete source, knowledge file and tests as `CMPE172_Milestone4_FirstName_LastName.zip`. Submit that ZIP with the GitHub repository link and a student-recorded code walkthrough link.

The video must last at least five minutes and explain the implementation and tests. Cover AiChatService, AiBookingTools, the confirmation transaction path, AiReminderService, ReminderTransaction and AiKnowledgeService. Include a recorded demonstration of live confirmation writes and personalized reminders.

- [PDF report](docs/milestone4/report.pdf)
- [Editable Word report](docs/milestone4/report.docx)
- [Report source](docs/milestone4/report.md)
- [Walkthrough outline](docs/milestone4/speech.md)
- [Validation evidence](docs/milestone4/validation.md)
- [Milestone 2 report](docs/milestone2-report.md)

## Milestone 3 — integration and deployment

See [design](docs/milestone3/design.md), [report](docs/milestone3/report.md), [distribution diagram](docs/milestone3/distribution.png), [validation](docs/milestone3/validation.md), and [walkthrough speech](docs/milestone3/speech.md).

The existing booking/cancellation transaction now also inserts a durable notification outbox event. After commit, a notification Gateway posts one coarse-grained DTO (`eventId`, `recipient`, `type`, `message`) to a mock second controller at `/internal/notifications`. The internal endpoint uses a shared service key and durable event-ID/payload deduplication; it sends no real messages. The ordinary browser Session, role restrictions and CSRF protections remain enabled.

The Gateway bounds connection and complete-response time, makes up to three attempts using the same event ID, and falls back to persisted PENDING state without rolling back the booking. A recovery worker retries due events every 15 seconds in batches of 20, ordered by next attempt time. Non-retryable HTTP responses become FAILED for manual review. See the report for delivery guarantees and recovery limits.

`GET /health` checks `SELECT 1`, returning 200/UP or 503/DOWN. `GET /api/provider/metrics` exposes Micrometer booking success/failure counts, cancellation and notification counts, average service booking latency, and durable queue counts. Metrics require a provider session. Logs use event/appointment IDs, types and failure categories; notification content, recipient addresses and service tokens are not logged by the new notification code.

```sh
# Set up the dedicated MySQL database using the Tests section above.
sh scripts/test-mysql.sh '-Dtest=NotificationDownTests'
sh scripts/test-mysql.sh '-Dtest=Milestone3IntegrationTests'

# End-to-end smoke against a fresh running demo app.
python3 scripts/smoke.py --base-url http://localhost:8080
```

The multi-stage Dockerfile compiles on Java 17, runs as a non-root user and includes a Java health probe. `docker-compose.yml` now runs app + MySQL while preserving the prior Compose project and volume names. New notification tables are created additively at startup, including on existing volumes. GitHub Actions verifies H2/MySQL tests and builds/smokes the image on each push or pull request. Check the Actions page for the status of each hosted run.
