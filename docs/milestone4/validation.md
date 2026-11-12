# Milestone 4 validation

Validated on November 11, 2026 in America/Los_Angeles.

- Java 17 and H2 full suite completed with 89 tests, zero failures, errors or skipped tests.
- Java 17 and MySQL 8.4 full suite completed with 89 tests, zero failures, errors or skipped tests.
- Four frontend regression tests passed.
- The real Google SDK adapter is exercised through a local HTTP server in GeminiAdapterTests.
- The scripted model tests exercise the actual Spring AI tool loop, session authentication, CSRF, services and database.
- AiWorkflowTests.twoAgentCustomersExactlyOneWinsAfterConfirmation creates two customer sessions, obtains two proposals for the same slot, and releases both confirmation requests through latches and futures. It asserts HTTP 200 and 409 and exactly one active database appointment.
- Saved conversation history survives New chat and another login. Owner-only reads, multi-turn appends and confirmation history are covered by integration tests.
- Availability date parsing recovery has a regression test and a successful live Gemini check.
- The Docker app and database passed their health checks.
- Browser checks covered 320px and 390px portrait views, a 1366x768 desktop view and short landscape views. Long-chat actions remained visible, history preserved the draft, and provider form errors retained values and restored field focus.
- The updated five-page PDF and editable Word report were rendered and visually reviewed.

## Live Gemini evidence

The configured gemini-3.1-flash-lite model returned readable appointment details. A previous live RAG check cited policy-cancellation. The corrected Find my next session request returned no algebra sessions in the next seven days, matching a database query.

Live confirmation writes and personalized outbound reminders still need the student's recorded demonstration. Automated integration coverage is included.

## Submission

Include the PDF report and source code in the ZIP. Submit the GitHub repository link and a student-recorded code walkthrough link separately. The video must last at least five minutes.
