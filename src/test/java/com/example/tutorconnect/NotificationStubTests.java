package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = {"classpath:reset.sql", "classpath:seed.sql"})
class NotificationStubTests {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  final HttpClient http = HttpClient.newHttpClient();

  String payload(String message) {
    return "{\"eventId\":\"test:123:BOOKING_CONFIRMED\",\"recipient\":\"demo@example.com\",\"type\":\"BOOKING_CONFIRMED\",\"message\":\""
        + message
        + "\"}";
  }

  int post(String key, String body) throws Exception {
    var req =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/notifications"))
            .header("Content-Type", "application/json");
    if (key != null) req.header("X-Notification-Key", key);
    return http.send(
            req.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString())
        .statusCode();
  }

  @Test
  void requiresServiceAuthenticationEvenWithoutBrowserCsrf() throws Exception {
    assertThat(post(null, payload("Hello"))).isEqualTo(401);
    assertThat(post("wrong", payload("Hello"))).isEqualTo(401);
  }

  @Test
  void duplicateIdWithSamePayloadIsAcceptedOnlyOnce() throws Exception {
    assertThat(post("tutorconnect-local-notification-key", payload("Hello"))).isEqualTo(201);
    assertThat(post("tutorconnect-local-notification-key", payload("Hello"))).isEqualTo(200);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_receipts", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void reusedIdWithChangedPayloadReturnsConflict() throws Exception {
    assertThat(post("tutorconnect-local-notification-key", payload("Hello"))).isEqualTo(201);
    assertThat(post("tutorconnect-local-notification-key", payload("Changed"))).isEqualTo(409);
  }

  @Test
  void malformedAndIncompleteRequestsReturn400() throws Exception {
    assertThat(post("tutorconnect-local-notification-key", "{bad")).isEqualTo(400);
    assertThat(post("tutorconnect-local-notification-key", "{}")).isEqualTo(400);
  }

  @Test
  void concurrentDuplicatesHaveOneStoredSideEffect() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      Callable<Integer> call =
          () -> {
            start.await();
            return post("tutorconnect-local-notification-key", payload("Hello"));
          };
      var a = pool.submit(call);
      var b = pool.submit(call);
      start.countDown();
      assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 200);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_receipts", Integer.class))
          .isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }
}
