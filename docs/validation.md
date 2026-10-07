# Validation — October 6, 2026

- Baseline: original two tests passed before implementation.
- Red phase: the initial five Milestone 2 HTTP workflow tests failed because the required routes/UI/login did not yet exist.
- Regression cases observed failing before fixes: nonpositive cancellation ID, safe not-found handling, and removal/recreation of identical availability.
- Final H2: `mvn verify` — 31 tests, 0 failures, 0 errors, 0 skipped.
- Final MySQL 9.2: isolated local database, `scripts/test-mysql.sh` — 31 tests, 0 failures, 0 errors, 0 skipped.
- Test distribution: 16 service unit tests; 11 HTTP workflow tests; 2 read-endpoint tests; 2 transaction/database constraint tests.
- Concurrency: two independently authenticated customers start through a latch; exactly one HTTP 201 and one HTTP 409; exactly one active booking in the database.
- Transaction rollback: forced failure after INSERT leaves zero booking rows for the tested slot.
- Direct database backstop: a second active slot booking is rejected without going through application locking.
- Independent review: removal/recreation bug fixed with regression coverage; recheck found no remaining important defects.
- Browser check: real customer login → booking form → confirmation in a separate MySQL demo database.
- GitHub Actions configuration: Java 17 / H2 and MySQL 8.4; hosted results are separate from the local evidence above.
