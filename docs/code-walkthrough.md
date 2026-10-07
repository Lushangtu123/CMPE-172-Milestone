# Code walkthrough — target duration 6–7 minutes

Record your own screen and narration. Use this as an outline, and explain the code in your own words. Show the actual implementation and tests, not just a UI demo.

## 0:00–0:45 — Purpose and architecture

Open the project tree and README. Introduce TutorConnect's customer and provider roles. Explain Controller → Service → Repository → JdbcTemplate → MySQL and the DTO/model return path. Point out that no ORM is used. Briefly show the available-session page.

## 0:45–1:40 — Login and authorization

Open SecurityConfig.java and Account.java. Show the JDBC user lookup, BCrypt encoder, and ROLE_CUSTOMER / ROLE_PROVIDER mapping. Explain that login creates a server-side session and that customer IDs come from the authenticated principal. Show the route role checks and explain 403. Point to automatic CSRF fields in rendered forms.

## 1:40–2:30 — Frontend to database booking flow

Open book.html, PageController.book, AppointmentService.book, BookingTransaction.book and AppointmentRepository.insert. Follow slotId and serviceId from the form to parameterized SQL, generated appointment ID, redirect, ownership check and confirmation model. Show the real browser confirmation once, then return to the code.

## 2:30–3:50 — Race condition and transaction

Explain two customers both observing an available slot. Show SELECT FOR UPDATE and @Transactional(READ_COMMITTED). Explain that the second transaction waits on the same existing slot row and rechecks availability after the first commits. Explain rollback and the unique generated active_slot_id database backstop. Show how cancellation keeps history but releases the active key; a new booking gets a new appointment record.

## 3:50–4:40 — ACID and retry

Explain each ACID property using this booking implementation. Show the AppointmentService retry loop, its separate transactional bean and fresh transaction boundary. Distinguish transient lock failures, bounded retries/503 and ordinary 409 business conflicts with no retry.

## 4:40–5:35 — Customer/provider rules and errors

Show owner-only cancellation; UTC future-time validation; provider service ownership, matching duration and overlap checks; safe deactivation and restoration. Explain COMPLETED on past appointment list reads. Show GlobalExceptionHandler's standard statuses and generic unexpected-error message.

## 5:35–6:45 — Tests and evidence

Show BookingServiceTests, BookingWorkflowTests.twoConcurrentCustomersExactlyOneWins and TransactionTests. Explain two separate sessions, the readiness/start latches, futures and 201/409 assertion, then the database count. Run mvn test and the dedicated MySQL test command. Show the passing summaries; explain the rollback and direct database uniqueness tests. End by naming the report, source ZIP and your recorded video link as deliverables.
