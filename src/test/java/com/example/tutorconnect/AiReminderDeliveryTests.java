package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tutorconnect.ai.*;
import com.example.tutorconnect.notification.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(properties = {"notifications.enabled=true", "notifications.recovery.enabled=false"})
@Import(AiWorkflowTests.Models.class)
@Sql(scripts = {"classpath:reset.sql", "classpath:seed.sql"})
class AiReminderDeliveryTests {
  static final AtomicInteger accepted = new AtomicInteger();
  static final HttpServer receiver;

  static {
    try {
      receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      receiver.createContext(
          "/",
          x -> {
            String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).contains("APPOINTMENT_REMINDER");
            accepted.incrementAndGet();
            x.sendResponseHeaders(201, -1);
            x.close();
          });
      receiver.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @AfterAll
  static void close() {
    receiver.stop(0);
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("notifications.url", () -> "http://127.0.0.1:" + receiver.getAddress().getPort());
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired AiReminderService reminders;
  @Autowired ReminderTransaction transaction;
  @Autowired NotificationDispatcher dispatcher;

  @BeforeEach
  void clear() {
    accepted.set(0);
  }

  void upcoming() {
    var now = LocalDateTime.now(ZoneOffset.UTC);
    jdbc.update(
        "UPDATE availability_slots SET start_time=?,end_time=? WHERE slot_id=2",
        now.plusHours(2),
        now.plusHours(3));
  }

  @Test
  void generatedReminderUsesHttpGatewayAndIsSentOnlyOnce() {
    upcoming();
    assertThat(reminders.run(3L).enqueued()).isEqualTo(1);
    assertThat(accepted).hasValue(1);
    assertThat(jdbc.queryForObject("SELECT state FROM notification_outbox", String.class))
        .isEqualTo("SENT");
    assertThat(reminders.run(3L).enqueued()).isZero();
    assertThat(accepted).hasValue(1);
  }

  @Test
  void generationRechecksCancellationBeforeEnqueue() {
    upcoming();
    jdbc.update("UPDATE appointments SET status='CANCELLED' WHERE appointment_id=1");
    assertThat(transaction.enqueue(1, "A generated reminder")).isFalse();
    assertThat(accepted).hasValue(0);
  }

  @Test
  void notificationRecoveryDoesNotSendAnAlreadyCancelledReminder() {
    upcoming();
    assertThat(transaction.enqueue(1, "A generated reminder")).isTrue();
    jdbc.update("UPDATE appointments SET status='CANCELLED' WHERE appointment_id=1");
    dispatcher.deliver("appointment:1:APPOINTMENT_REMINDER");
    assertThat(accepted).hasValue(0);
    assertThat(jdbc.queryForObject("SELECT last_error FROM notification_outbox", String.class))
        .isEqualTo("NOT_UPCOMING");
  }

  @Test
  void providerTriggerCannotScanAnotherProvidersAppointments() {
    upcoming();
    assertThat(reminders.run(4L).scanned()).isZero();
    assertThat(accepted).hasValue(0);
  }
}
