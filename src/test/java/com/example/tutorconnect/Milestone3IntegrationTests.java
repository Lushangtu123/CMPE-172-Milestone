package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "notifications.enabled=true",
      "notifications.max-attempts=3",
      "notifications.request-timeout=100ms",
      "notifications.connect-timeout=100ms",
      "notifications.retry-delay=1ms",
      "notifications.recovery.enabled=false"
    })
@Sql(scripts = {"classpath:reset.sql", "classpath:seed.sql"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Milestone3IntegrationTests {
  static final AtomicInteger calls = new AtomicInteger();
  static final List<String> keys = new CopyOnWriteArrayList<>();
  static final AtomicBoolean committed = new AtomicBoolean();
  static volatile int mode = 200;
  static volatile JdbcTemplate observer;
  static HttpServer stub;
  static final ExecutorService workers =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
          });

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) throws Exception {
    stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    stub.setExecutor(workers);
    stub.createContext(
        "/notifications",
        exchange -> {
          try {
            String body =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var m = Pattern.compile("appointment:(\\d+):").matcher(body);
            if (m.find())
              committed.set(
                  observer.queryForObject(
                          "SELECT COUNT(*) FROM appointments WHERE appointment_id=?",
                          Integer.class,
                          Long.parseLong(m.group(1)))
                      == 1);
            keys.add(body);
            calls.incrementAndGet();
            if (mode == -1) {
              exchange.close();
              return;
            }
            if (mode == 408)
              try {
                Thread.sleep(400);
              } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
              }
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(mode == 408 ? 200 : mode, response.length);
            exchange.getResponseBody().write(response);
          } finally {
            exchange.close();
          }
        });
    stub.start();
    r.add(
        "notifications.url",
        () -> "http://127.0.0.1:" + stub.getAddress().getPort() + "/notifications");
  }

  @AfterAll
  static void stop() {
    stub.stop(0);
    workers.shutdownNow();
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clear() {
    observer = jdbc;
    mode = 200;
    calls.set(0);
    keys.clear();
    committed.set(false);
  }

  class Browser extends HttpTestClient {
    Browser() {
      super(Milestone3IntegrationTests.this.port);
    }
  }

  Browser customer() throws Exception {
    var b = new Browser();
    b.login("maya.patel@example.com");
    return b;
  }

  @Autowired com.example.tutorconnect.notification.NotificationDispatcher dispatcher;

  @Test
  void deferredNotificationRecoversUsingSameEventAfterServiceReturns() throws Exception {
    mode = 503;
    var browser = customer();
    browser.post("/api/customer/appointments", "slotId=1&serviceId=1");
    assertThat(calls.get()).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT state FROM notification_outbox", String.class))
        .isEqualTo("PENDING");
    mode = 200;
    jdbc.update(
        "UPDATE notification_outbox SET next_attempt_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP)");
    dispatcher.recover();
    assertThat(calls.get()).isEqualTo(4);
    assertThat(new HashSet<>(keys)).hasSize(1);
    assertThat(jdbc.queryForObject("SELECT state FROM notification_outbox", String.class))
        .isEqualTo("SENT");
    assertThat(jdbc.queryForObject("SELECT attempts FROM notification_outbox", Integer.class))
        .isEqualTo(4);
    dispatcher.recover();
    assertThat(calls.get()).isEqualTo(4);
  }

  @Autowired com.example.tutorconnect.repository.NotificationOutboxRepository outbox;

  @Test
  void repeatedFailuresDoNotStarveEventsBeyondTheFirstBatch() {
    var created = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(2);
    for (int i = 0; i < 21; i++)
      jdbc.update(
          "INSERT INTO"
              + " notification_outbox(event_id,recipient,type,message,created_at,next_attempt_at)"
              + " VALUES(?,?,?,?,?,?)",
          "fair:" + i,
          "demo@example.com",
          "BOOKING_CONFIRMED",
          "Hello",
          created.plusSeconds(i),
          created);
    for (int i = 0; i < 20; i++) outbox.failed("fair:" + i, 3, "HTTP_503", false);
    // Simulate the next recovery cycle: these retries are due again, but row 21 waited longer.
    jdbc.update(
        "UPDATE notification_outbox SET next_attempt_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP)"
            + " WHERE event_id<>'fair:20'");
    assertThat(outbox.due()).hasSize(20);
    assertThat(outbox.due().get(0)).isEqualTo("fair:20");
  }

  @Test
  void healthChecksDatabaseAndIsPublic() throws Exception {
    var response = new Browser().get("/health");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"", "\"database\":\"UP\"");
  }

  @Test
  void bookingAndCancellationNotifyOnlyAfterCommit() throws Exception {
    var b = customer();
    assertThat(b.post("/api/customer/appointments", "slotId=1&serviceId=1").statusCode())
        .isEqualTo(201);
    assertThat(calls.get()).isEqualTo(1);
    assertThat(committed.get()).isTrue();
    assertThat(keys.get(0)).contains("BOOKING_CONFIRMED", "maya.patel@example.com");
    long id =
        jdbc.queryForObject("SELECT appointment_id FROM appointments WHERE slot_id=1", Long.class);
    assertThat(b.post("/api/customer/appointments/" + id + "/cancel", "").statusCode())
        .isEqualTo(200);
    assertThat(calls.get()).isEqualTo(2);
    assertThat(keys.get(1)).contains("CANCELLATION_CONFIRMED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE state='SENT'", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void bookingSucceedsWhenNotificationServiceDisconnects() throws Exception {
    mode = -1;
    var b = customer();
    long start = System.nanoTime();
    assertThat(b.post("/api/customer/appointments", "slotId=1&serviceId=1").statusCode())
        .isEqualTo(201);
    assertThat(calls.get()).isEqualTo(3);
    assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(4000);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE active_slot_id=1", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE state='PENDING'", Integer.class))
        .isEqualTo(1);
    assertThat(new HashSet<>(keys)).hasSize(1);
  }

  @Test
  void slowNotificationsTimeOutWithoutRollingBackBooking() throws Exception {
    mode = 408;
    var b = customer();
    long start = System.nanoTime();
    assertThat(b.post("/api/customer/appointments", "slotId=1&serviceId=1").statusCode())
        .isEqualTo(201);
    assertThat(calls.get()).isGreaterThanOrEqualTo(2);
    assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(4000);
    assertThat(jdbc.queryForObject("SELECT state FROM notification_outbox", String.class))
        .isEqualTo("PENDING");
  }

  @Test
  void retryableServerErrorUsesStablePayloadAndPersistsFallback() throws Exception {
    mode = 503;
    var b = customer();
    assertThat(b.post("/api/customer/appointments", "slotId=1&serviceId=1").statusCode())
        .isEqualTo(201);
    assertThat(calls.get()).isEqualTo(3);
    assertThat(new HashSet<>(keys)).hasSize(1);
    assertThat(jdbc.queryForObject("SELECT attempts FROM notification_outbox", Integer.class))
        .isEqualTo(3);
  }

  @Test
  void permanentRejectionDoesNotSpinInRetryLoop() throws Exception {
    mode = 400;
    var b = customer();
    assertThat(b.post("/api/customer/appointments", "slotId=1&serviceId=1").statusCode())
        .isEqualTo(201);
    assertThat(calls.get()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT state FROM notification_outbox", String.class))
        .isEqualTo("FAILED");
  }

  @Test
  void metricsRequireProviderAndIncludeCommittedBooking() throws Exception {
    assertThat(new Browser().get("/api/provider/metrics").statusCode()).isEqualTo(401);
    var b = customer();
    assertThat(b.get("/api/provider/metrics").statusCode()).isEqualTo(403);
    b.post("/api/customer/appointments", "slotId=1&serviceId=1");
    var p = new Browser();
    p.login("priya.shah@example.com");
    var metrics = p.get("/api/provider/metrics");
    assertThat(metrics.statusCode()).isEqualTo(200);
    assertThat(metrics.body())
        .contains("bookingsSucceeded", "notificationPending", "bookingAverageMillis");
  }

  @Test
  void failedBusinessOperationDoesNotNotify() throws Exception {
    var b = customer();
    assertThat(b.post("/api/customer/appointments", "slotId=2&serviceId=1").statusCode())
        .isEqualTo(409);
    assertThat(calls.get()).isZero();
  }
}
