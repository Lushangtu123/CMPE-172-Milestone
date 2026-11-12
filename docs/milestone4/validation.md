# Milestone 4 validation

Validation date: November11,2026 (America/Los_Angeles).

- Baseline52tests passed before changes.
- FinalJava17/H2:81tests,0failures,0errors,0skipped; mvn verify BUILD SUCCESS.
- FinalJava17/MySQL8.4:81tests,0failures,0errors,0skipped; scripts/test-mysql.sh BUILD SUCCESS.
- AIworkflow tests use scripted external model responses but actual SpringAI ChatClient tool loop, HTTP sessions, CSRF, service transactions and DB.
- GeminiAdapterTests use actual GoogleGenAi model and SDK against a local HTTPserver: public multipart answer selection, thought removal and signed function call roundtrip.
- Two real session customers simultaneously confirm proposals: one200/one409 and databaseactivecount1.
- Reminder HTTPgateway test verifies one stored/outbound event, no duplicate enqueue, cancellation recheck, suppression at recovery, and provider scope.
- Regression RED observed for missing routes, thought-response handling, scheduling independence/pool and cancelled reminder suppression. Each corrected and GREEN.
- Exported AI_ENABLED and sentinel key initially exposed test configuration leakage; Surefire now explicitly disables production AI during tests. Focused14tests passed even with AI_ENABLED=true exported.
- GoogleJavaFormat1.24.0 used on Java17 after hostJava25 incompatibility.
- DockerJava17multi-stage image built. Isolated app+MySQL became healthy.
- ContainerHTTPsmoke: health200, booking201, cancel200, two deliverednotificationevents. Syntheticdemo data only.
- JavaScript syntax checks passed. PDF/DOCX rendered and all5pages visually checked.
- Independentread-only review found thought/schedulingissues, fixed and rechecked; no remainingimportant defect.

## Live model verification

The user configured a key in a local ignored .env. With gemini-3.1-flash-lite, live app chat returnedHTTP200 in0.84seconds. The live policy smoke returned a cancellation answer citing policy-cancellation through the real vector retrieval path. The key was not printed, committed or added to any artifact. Live confirmation writes and scheduled outbound reminders still need a separate demonstration; their automated integration tests pass.

The original gemini-3.8-flash request timed out with this account while listing models succeeded. Switching only the local model configuration restored service. Two Node regression tests reproduce missing progress feedback and unbounded browser waiting; the fixed UI shows progress, aborts after45seconds and does not wait on history potentially locked by the still-running server request.

## Submission

Mandatory PDF and editable Wordreport share the same five-page content. Include source, knowledgefile and tests in the ZIP. Record a minimumfive-minute codewalkthrough and submit its link alongside the ZIP and GitHubrepository link. HostedCI results require publication of this branch and are separate from localvalidation.
- Browser check: Playwright logged in with the demo customer, rendered the assistant, submitted a policy question with AI disabled and verified the configuration alert with no pending confirmation. Browser session closed afterward. An unrelated favicon404 was the only initial asset error.

- Frontend regressions: `node --test scripts/tests/assistant.test.cjs`,2passed. Existing81Java tests remain required.
