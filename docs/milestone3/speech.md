# Milestone 3 walkthrough speech - target 6-7 minutes

只朗读英文段落，中文是屏幕操作提示。先试讲一次，实际时长至少五分钟。

## 0:00-0:45 - Purpose and distribution boundary

【展示 distribution.png，接着展示项目目录。】

Hi everyone. This video explains the Milestone Three implementation of TutorConnect.

The previous milestone provided login, role-based access, and concurrent appointment booking. This milestone adds a notification boundary, failure handling, observability, Docker deployment, and continuous integration.

The diagram shows the client, appointment scheduler, and notification service. The notification service is a second Spring controller in this project. Although it runs in the same application container, the scheduler calls it through HTTP, using a separate Gateway.

The mock records notifications. It does not send real email or text messages.

## 0:45-1:30 - Coarse-grained interface and security

【展示 NotificationRequest.java、NotificationGateway.java、NotificationSecurityConfig.java。】

NotificationRequest is the DTO passed across the boundary. It contains an event ID, recipient, notification type, and message.

This is a coarse-grained interface because one request contains everything needed to accept a notification. The notification service does not need several small calls to fetch customer or appointment details.

The same interface supports booking confirmations and cancellation confirmations.

The internal endpoint requires a shared service key. It uses a separate stateless security chain. Customer and provider routes still use session authentication, role checks, and CSRF protection.

## 1:30-2:35 - Commit first, then notify

【展示 BookingTransaction 中 enqueue，再展示 AppointmentService 中 afterCommit。】

The most important rule is that notification delivery must happen after the database transaction commits.

Inside BookingTransaction, the application inserts the appointment and a notification outbox event in the same transaction. Cancellation also changes the appointment and inserts its own event atomically.

If the transaction fails, both changes roll back.

AppointmentService calls the separate transactional bean and waits for it to return. Only then does it call the notification dispatcher. The facade suspends any caller transaction, and the Gateway checks that no database transaction is active before making the HTTP request.

The outbox provides durable fallback. If the process stops after commit but before sending, the event remains in MySQL. Notification failure therefore does not undo a successful booking.

## 2:35-3:40 - Timeout, retry and idempotency

【展示 Gateway 的 sendAsync、get 超时、状态判断；展示接收端的 event_id 与 hash 校验。】

The Gateway has a connection timeout and a deadline for the complete HTTP response. By default, the connection timeout is two hundred milliseconds and the response deadline is three hundred milliseconds.

It makes up to three attempts. Network failures, timeouts, rate limiting, and server errors can be retried. Other non-success responses require manual review.

Every attempt uses the same event ID and payload. This matters because the receiver may accept a request even when its acknowledgment is lost.

The receiver stores the event ID as a unique primary key. An identical replay returns success without creating another receipt. The same ID with changed content returns a conflict.

The delivery guarantee is at-least-once HTTP delivery, with deduplication at the mock receiver.

## 3:40-4:25 - Recovery

【展示 NotificationDispatcher 和 NotificationRecovery，以及 repository 的 due 查询。】

When temporary failures exhaust the immediate attempts, the event stays PENDING. A worker checks for due events every fifteen seconds.

Each batch contains at most twenty events. The query sorts by next attempt time, so repeatedly failing older events cannot permanently block newer waiting events.

When the service recovers, the worker sends the same event again and marks it SENT after acceptance.

Permanent rejections become FAILED and produce an error log for operator review. The queue is persistent, so an application restart does not discard pending events.

## 4:25-5:20 - Logging, health and metrics

【展示 INFO/WARN/ERROR 日志，运行 curl /health；展示导师指标接口及 BookingMetrics。】

The logging plan distinguishes normal operations, temporary failures, and permanent failures.

INFO logs record committed bookings and accepted notifications. WARN logs record retry attempts and deferred delivery. ERROR logs identify permanent rejections or database health failures.

The new logs do not include recipient addresses, notification content, passwords, or the service key.

The public health endpoint executes SELECT one. It returns HTTP two hundred when the database is available, or five hundred and three when the database check fails.

The provider-only metric endpoint shows booking success and failure counts, cancellations, delivered notifications, pending events, and average booking latency. In-memory counters reset on restart, while queue records persist.

## 5:20-6:45 - Tests, Docker and CI

【运行 NotificationDownTests；展示 Milestone3IntegrationTests 的恢复与公平性测试，以及 NotificationStubTests；最后展示 Dockerfile、Compose、CI 和测试汇总。】

The key integration test points the Gateway to a local port where no notification server is running.

The test still expects a successful booking response. It verifies the appointment is committed and the notification remains PENDING after bounded attempts.

Other tests simulate slow responses, disconnections, and server errors. They also verify that sending begins only after the appointment is visible through another database connection.

Recovery tests bring the service back and confirm that the same event becomes SENT. Concurrent duplicate requests verify that the receiver stores only one receipt.

The complete suite contains fifty-two tests and passes with both H2 and MySQL eight point four.

The Dockerfile builds with Java seventeen and runs the application as a non-root user. Compose runs the app and MySQL with health checks and persistent storage.

GitHub Actions runs the tests and verifies the application image on each push. Local container verification also completed a real login, booking, cancellation, and notification flow.

The submission includes the source code, PDF report, distribution diagram, and this walkthrough video. Thank you for watching.
