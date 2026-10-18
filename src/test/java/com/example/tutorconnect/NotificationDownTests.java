package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "notifications.enabled=true",
      "notifications.recovery.enabled=false",
      "notifications.connect-timeout=100ms",
      "notifications.request-timeout=100ms",
      "notifications.retry-delay=1ms"
    })
@Sql(scripts = {"classpath:reset.sql", "classpath:seed.sql"})
class NotificationDownTests {
  @DynamicPropertySource
  static void downService(DynamicPropertyRegistry r) throws Exception {
    int unavailablePort;
    try (var reserved = new ServerSocket(0)) {
      unavailablePort = reserved.getLocalPort();
    }
    r.add("notifications.url", () -> "http://127.0.0.1:" + unavailablePort + "/notifications");
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;

  @Test
  void bookingIsCommittedWhenNotificationStubIsNotRunning() throws Exception {
    var browser = new HttpTestClient(port);
    browser.login("maya.patel@example.com");
    assertThat(browser.post("/api/customer/appointments", "slotId=1&serviceId=1").statusCode())
        .isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE active_slot_id=1", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT state FROM notification_outbox", String.class))
        .isEqualTo("PENDING");
    assertThat(jdbc.queryForObject("SELECT attempts FROM notification_outbox", Integer.class))
        .isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_receipts", Integer.class))
        .isZero();
  }
}
