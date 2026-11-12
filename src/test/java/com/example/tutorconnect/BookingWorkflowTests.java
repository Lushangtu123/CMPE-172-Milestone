package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(
    scripts = {"classpath:reset.sql", "classpath:seed.sql"},
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BookingWorkflowTests {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  static final Pattern TOKEN = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

  class Browser {
    final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    String csrf;

    HttpResponse<String> get(String path) throws Exception {
      return http.send(
          HttpRequest.newBuilder(
                  URI.create(path.startsWith("http") ? path : "http://localhost:" + port + path))
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(String path, String body) throws Exception {
      return http.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("X-CSRF-TOKEN", csrf == null ? "" : csrf)
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }

    void token(String path) throws Exception {
      var response = get(path);
      assertThat(response.statusCode()).isEqualTo(200);
      var matcher = TOKEN.matcher(response.body());
      assertThat(matcher.find()).as("CSRF field in rendered page").isTrue();
      csrf = matcher.group(1);
    }

    void login(String email) throws Exception {
      token("/login");
      assertThat(post("/login", "username=" + email + "&password=TutorDemo123%21").statusCode())
          .isEqualTo(302);
      token("/slots");
    }
  }

  Browser login(String email) throws Exception {
    var b = new Browser();
    b.login(email);
    return b;
  }

  @Test
  void homeAndSlotsAreRenderedAndPaginated() throws Exception {
    var b = new Browser();
    assertThat(b.get("/").body()).contains("<!DOCTYPE html>", "TutorConnect");
    assertThat(b.get("/api/slots?size=1").body()).contains("\"size\":1", "\"total\":4");
    assertThat(b.get("/api/slots?providerId=1&serviceId=3").body()).contains("\"total\":0");
    assertThat(b.get("/api/slots?page=-1").statusCode()).isEqualTo(400);
  }

  @Test
  void jdbcLoginEnforcesRolesAndCsrf() throws Exception {
    assertThat(new Browser().get("/api/customer/appointments").statusCode()).isEqualTo(401);
    var customer = login("maya.patel@example.com");
    assertThat(customer.get("/api/provider/appointments").statusCode()).isEqualTo(403);
    var provider = login("priya.shah@example.com");
    assertThat(provider.get("/api/customer/appointments").statusCode()).isEqualTo(403);
    customer.csrf = "invalid";
    assertThat(customer.post("/api/customer/appointments", "slotId=1&serviceId=1").statusCode())
        .isEqualTo(403);
  }

  @Test
  void loginRedirectsToTheWorkPageForEachRole() throws Exception {
    var provider = new Browser();
    provider.token("/login");
    assertThat(
            provider
                .post("/login", "username=priya.shah@example.com&password=TutorDemo123%21")
                .headers()
                .firstValue("Location")
                .orElseThrow())
        .endsWith("/provider/dashboard");
    var customer = new Browser();
    customer.token("/login");
    assertThat(
            customer
                .post("/login", "username=maya.patel@example.com&password=TutorDemo123%21")
                .headers()
                .firstValue("Location")
                .orElseThrow())
        .endsWith("/slots");
  }

  @Test
  void emptySessionPagesExplainWhetherFiltersAreApplied() throws Exception {
    var b = new Browser();
    assertThat(b.get("/slots?providerId=1&serviceId=3").body())
        .contains("No sessions match your filters", "Clear filters")
        .doesNotContain("Session pages");
    assertThat(b.get("/slots?size=1").body()).contains("Session pages", "Next");
    jdbc.update("UPDATE availability_slots SET active=FALSE");
    assertThat(b.get("/slots").body())
        .contains("No upcoming sessions are available")
        .doesNotContain("match your filters", "Clear filters", "Session pages");
    assertThat(b.get("/slots?providerId=1").body())
        .contains("No upcoming sessions are available")
        .doesNotContain("match your filters", "Session pages");
  }

  @Test
  void customerReviewsCancellationBeforePosting() throws Exception {
    var maya = login("maya.patel@example.com");
    var sam = login("sam.rivera@example.com");
    assertThat(maya.post("/api/customer/appointments", "slotId=1&serviceId=1").statusCode())
        .isEqualTo(201);
    var id =
        jdbc.queryForObject("SELECT appointment_id FROM appointments WHERE slot_id=1", Long.class);
    var path = "/customer/appointments/" + id + "/cancel";
    assertThat(maya.get("/customer/appointments").body()).contains("href=\"" + path + "\"");
    assertThat(sam.get(path).statusCode()).isEqualTo(403);
    assertThat(maya.get(path).body())
        .contains("Confirm cancellation", "Algebra Coaching", "Priya Shah", "UTC");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM appointments WHERE appointment_id=?", String.class, id))
        .isEqualTo("BOOKED");
    maya.token(path);
    assertThat(maya.post(path, "").statusCode()).isEqualTo(302);
    assertThat(maya.get("/customer/appointments").body()).contains("CANCELLED");
  }

  @Test
  void dashboardShowsPastAvailabilityWithoutARemoveLink() throws Exception {
    var provider = login("priya.shah@example.com");
    var path = "/provider/slots/1/remove";
    assertThat(provider.get(path).body()).contains("Confirm removal", "Algebra Coaching", "UTC");
    assertThat(provider.get("/provider/slots/4/remove").statusCode()).isEqualTo(403);
    jdbc.update(
        "UPDATE availability_slots SET start_time=TIMESTAMPADD(DAY,-2,CURRENT_TIMESTAMP),"
            + "end_time=TIMESTAMPADD(DAY,-1,CURRENT_TIMESTAMP) WHERE slot_id=1");
    assertThat(provider.get(path).statusCode()).isEqualTo(409);
    assertThat(provider.post(path, "").statusCode()).isEqualTo(409);
    assertThat(provider.get("/provider/dashboard").body())
        .contains("Upcoming appointments", "Availability history", "Expired")
        .doesNotContain("href=\"" + path + "\"");
  }

  @Test
  void bookingCancellationAndRebookingKeepHistory() throws Exception {
    var maya = login("maya.patel@example.com");
    var sam = login("sam.rivera@example.com");
    assertThat(maya.post("/api/customer/appointments", "slotId=1&serviceId=1").statusCode())
        .isEqualTo(201);
    Long id =
        jdbc.queryForObject("SELECT appointment_id FROM appointments WHERE slot_id=1", Long.class);
    assertThat(sam.post("/api/customer/appointments/" + id + "/cancel", "").statusCode())
        .isEqualTo(403);
    assertThat(maya.post("/api/customer/appointments/" + id + "/cancel", "").statusCode())
        .isEqualTo(200);
    assertThat(sam.post("/api/customer/appointments", "slotId=1&serviceId=1").statusCode())
        .isEqualTo(201);
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id=1", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE active_slot_id=1", Integer.class))
        .isEqualTo(1);
    assertThat(maya.get("/api/customer/appointments").body()).contains("CANCELLED");
  }

  @Test
  void invalidBookingAndProviderOwnershipAreRejected() throws Exception {
    var c = login("maya.patel@example.com");
    assertThat(c.post("/api/customer/appointments", "slotId=1&serviceId=3").statusCode())
        .isEqualTo(400);
    assertThat(c.post("/api/customer/appointments", "slotId=999&serviceId=1").statusCode())
        .isEqualTo(404);
    assertThat(c.post("/api/customer/appointments", "slotId=2&serviceId=1").statusCode())
        .isEqualTo(409);
    assertThat(c.post("/api/customer/appointments", "slotId=0&serviceId=1").statusCode())
        .isEqualTo(400);
    var p = login("priya.shah@example.com");
    assertThat(p.post("/api/provider/slots/4/remove", "").statusCode()).isEqualTo(403);
    assertThat(p.post("/api/provider/slots/2/remove", "").statusCode()).isEqualTo(409);
    assertThat(p.post("/api/provider/slots/1/remove", "").statusCode()).isEqualTo(200);
    assertThat(c.post("/api/customer/appointments", "slotId=1&serviceId=1").statusCode())
        .isEqualTo(404);
  }

  @Test
  void removedAvailabilityCanBeRecreated() throws Exception {
    var p = login("priya.shah@example.com");
    var times =
        jdbc.queryForMap("SELECT start_time,end_time FROM availability_slots WHERE slot_id=1");
    var start =
        jdbc.queryForObject(
            "SELECT start_time FROM availability_slots WHERE slot_id=1",
            java.time.LocalDateTime.class);
    var end =
        jdbc.queryForObject(
            "SELECT end_time FROM availability_slots WHERE slot_id=1",
            java.time.LocalDateTime.class);
    assertThat(p.post("/api/provider/slots/1/remove", "").statusCode()).isEqualTo(200);
    assertThat(
            p.post("/api/provider/slots", "serviceId=1&startTime=" + start + "&endTime=" + end)
                .statusCode())
        .isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM availability_slots WHERE slot_id=1 AND active=TRUE",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void wrongPasswordDoesNotAuthenticateAndLogoutInvalidatesSession() throws Exception {
    var b = new Browser();
    b.token("/login");
    var failed = b.post("/login", "username=maya.patel@example.com&password=wrong");
    assertThat(failed.statusCode()).isEqualTo(302);
    assertThat(failed.headers().firstValue("Location").orElseThrow()).contains("error");
    assertThat(b.get("/api/customer/appointments").statusCode()).isEqualTo(401);
    b.login("maya.patel@example.com");
    assertThat(b.post("/logout", "").statusCode()).isEqualTo(302);
    assertThat(b.get("/api/customer/appointments").statusCode()).isEqualTo(401);
  }

  @Test
  void availabilityFormKeepsValuesAndMarksTimeError() throws Exception {
    var provider = login("priya.shah@example.com");
    var start =
        java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(9).withSecond(0).withNano(0);
    var invalidEnd = start.plusMinutes(10);
    String form = "serviceId=1&startTime=" + start + "&endTime=";
    var invalid = provider.post("/provider/slots", form + invalidEnd);
    assertThat(invalid.statusCode()).isEqualTo(400);
    assertThat(invalid.body())
        .contains(
            "Teaching dashboard",
            "endTime-error",
            "Slot length must match the service duration.",
            start.toString(),
            invalidEnd.toString());
    assertThat(provider.post("/provider/slots", form + start.plusHours(1)).statusCode())
        .isEqualTo(302);
    var overlap = provider.post("/provider/slots", form + start.plusHours(1));
    assertThat(overlap.statusCode()).isEqualTo(409);
    assertThat(overlap.body()).contains("Teaching dashboard", "startTime-error", start.toString());
    var malformed =
        provider.post("/provider/slots", "serviceId=1&startTime=invalid&endTime=" + invalidEnd);
    assertThat(malformed.statusCode()).isEqualTo(400);
    assertThat(malformed.body()).contains("Enter a valid start time.");
    var retainedEnd =
        Pattern.compile("id=\"endTime\"[^>]*value=\"([^\"]+)\"").matcher(malformed.body());
    assertThat(retainedEnd.find()).isTrue();
    assertThat(java.time.LocalDateTime.parse(retainedEnd.group(1))).isEqualTo(invalidEnd);
  }

  @Test
  void providerCreatesFutureSlotAndRejectsOverlap() throws Exception {
    var p = login("priya.shah@example.com");
    var start = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(8).withNano(0);
    String body = "serviceId=1&startTime=" + start + "&endTime=" + start.plusHours(1);
    assertThat(p.post("/api/provider/slots", body).statusCode()).isEqualTo(201);
    assertThat(p.post("/api/provider/slots", body).statusCode()).isEqualTo(409);
    assertThat(
            p.post(
                    "/api/provider/slots",
                    "serviceId=1&startTime=" + start + "&endTime=" + start.plusMinutes(10))
                .statusCode())
        .isEqualTo(400);
    assertThat(p.get("/provider/dashboard").statusCode()).isEqualTo(200);
  }

  @Test
  void htmlBookingShowsConfirmationAndOwnerHistory() throws Exception {
    var c = login("maya.patel@example.com");
    assertThat(c.get("/customer/book/1").statusCode()).isEqualTo(200);
    var response = c.post("/customer/appointments", "slotId=1&serviceId=1");
    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(c.get(response.headers().firstValue("Location").orElseThrow()).body())
        .contains("Progress is", "Algebra Coaching");
    assertThat(c.get("/customer/appointments").statusCode()).isEqualTo(200);
  }

  @Test
  void pastAppointmentsBecomeCompletedAndCannotBeBooked() throws Exception {
    jdbc.update(
        "UPDATE availability_slots SET"
            + " start_time=TIMESTAMPADD(DAY,-2,CURRENT_TIMESTAMP),end_time=TIMESTAMPADD(DAY,-1,CURRENT_TIMESTAMP)"
            + " WHERE slot_id=2");
    var c = login("maya.patel@example.com");
    assertThat(c.get("/api/customer/appointments").body()).contains("COMPLETED");
    assertThat(c.post("/api/customer/appointments", "slotId=2&serviceId=1").statusCode())
        .isEqualTo(409);
  }

  @Test
  void invalidInputAndUnknownEndpointsReturnSafeErrors() throws Exception {
    var c = login("maya.patel@example.com");
    var missing = c.get("/api/customer/not-found");
    assertThat(missing.statusCode()).isEqualTo(404);
    assertThat(missing.body()).doesNotContain("stackTrace", "Exception", "com.example");
    assertThat(c.post("/api/customer/appointments/0/cancel", "").statusCode()).isEqualTo(400);
  }

  @Test
  void twoConcurrentCustomersExactlyOneWins() throws Exception {
    var maya = login("maya.patel@example.com");
    var sam = login("sam.rivera@example.com");
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      Callable<Integer> first =
          () -> {
            ready.countDown();
            start.await();
            return maya.post("/api/customer/appointments", "slotId=1&serviceId=1").statusCode();
          };
      Callable<Integer> second =
          () -> {
            ready.countDown();
            start.await();
            return sam.post("/api/customer/appointments", "slotId=1&serviceId=1").statusCode();
          };
      var a = executor.submit(first);
      var b = executor.submit(second);
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(java.util.List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM appointments WHERE active_slot_id=1", Integer.class))
          .isEqualTo(1);
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
  }
}
