# Milestone 3 validation - October 18, 2026

- Baseline main f63436b: 31 existing tests passed before changes.
- Initial Milestone 3 HTTP tests: seven feature assertions failed before implementation; one preexisting business-conflict path already passed.
- Final H2: 52 tests, zero failures/errors/skips; Maven verify built the JAR.
- Java 17 official Maven container: 52 H2 tests passed, zero failures/errors/skips; executable JAR built.
- Final MySQL 8.4: 52 tests, zero failures/errors/skips, using a separate Compose project and dedicated tutorconnect_test database.
- HTTP tests verify truly unavailable stub, connection close, delayed response, retryable 503 and permanent 400; booking still commits and fallback state persists.
- Commit-order probe reads the appointment through another database connection before acknowledging the remote request.
- Receiver tests cover shared-key authentication, malformed bodies, exact replay, changed-payload conflict and concurrent duplicate acceptance with one receipt.
- Recovery HTTP test proves PENDING-to-SENT with the same event payload and no repeat send after SENT.
- Independent code review found a >20-event starvation issue. A regression failed before the ordering fix; next_attempt_at ordering resolved it and review recheck found no remaining important defects.
- Multi-stage Java 17 image built. Non-root app + MySQL containers reached healthy status.
- Container smoke: actual Session/CSRF login, booking 201, cancellation 200, two delivered notifications and zero pending/failed events.
- App/container recreation preserved the first two receipts; a later smoke produced four stored receipts total, verifying persistent database state.
- Database stop/restore drill: /health returned DOWN/503 while the isolated DB was stopped, then UP/200 when restored.
- Compose/workflow YAML and Python smoke syntax validated. Hosted GitHub Actions results are not claimed; configuration runs on every push/PR after publication.
- Report: 7 pages; distribution diagram: PDF/PNG/editable SVG; visually inspected via PDF rendering.
- English speech is about 780 spoken words with Chinese screen cues; actual video must be recorded by the student and last at least five minutes.
