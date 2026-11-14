package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = {"classpath:reset.sql", "classpath:seed.sql"})
class SpaApiTests {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  final ObjectMapper json = new ObjectMapper();

  class Browser {
    final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    String token = "";

    HttpResponse<String> request(String path, String body) throws Exception {
      var request =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .header("Accept", "application/json");
      if (body == null) request.GET();
      else
        request
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("X-CSRF-TOKEN", token)
            .POST(HttpRequest.BodyPublishers.ofString(body));
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    JsonNode session() throws Exception {
      var response = request("/api/session", null);
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
      var session = json.readTree(response.body());
      token = session.path("csrf").path("token").asText();
      assertThat(token).isNotBlank();
      return session;
    }

    void login(String email) throws Exception {
      session();
      assertThat(request("/login", "username=" + email + "&password=TutorDemo123%21").statusCode())
          .isEqualTo(204);
      session();
    }
  }

  @Test
  void jsonLoginRotatesCsrfAndReturnsOnlyPublicAccountFields() throws Exception {
    var b = new Browser();
    assertThat(b.session().path("account").isNull()).isTrue();
    String anonymousToken = b.token;
    assertThat(b.request("/login", "username=maya.patel@example.com&password=wrong").statusCode())
        .isEqualTo(401);
    b.login("maya.patel@example.com");
    var session = b.session();
    assertThat(session.path("account").path("role").asText()).isEqualTo("CUSTOMER");
    assertThat(session.path("account").path("id").asLong()).isEqualTo(1);
    assertThat(session.toString()).doesNotContain("password", "$2a$");
    b.token = anonymousToken;
    assertThat(b.request("/api/customer/appointments", "slotId=1&serviceId=1").statusCode())
        .isEqualTo(403);
    b.session();
    assertThat(b.request("/api/customer/appointments", "slotId=1&serviceId=1").statusCode())
        .isEqualTo(201);
    assertThat(b.request("/api/provider/slots", null).statusCode()).isEqualTo(403);
    assertThat(b.request("/logout", "").statusCode()).isEqualTo(204);
    assertThat(b.request("/api/customer/appointments", null).statusCode()).isEqualTo(401);
    assertThat(b.session().path("account").isNull()).isTrue();
  }

  @Test
  void catalogAndProviderReadsKeepTheExistingOwnershipBoundary() throws Exception {
    var b = new Browser();
    var catalog = b.request("/api/catalog", null);
    assertThat(catalog.statusCode()).isEqualTo(200);
    assertThat(catalog.body()).contains("providers", "services", "Algebra Coaching");
    assertThat(b.request("/api/slots/1", null).statusCode()).isEqualTo(200);
    assertThat(b.request("/api/slots/99999", null).statusCode()).isEqualTo(404);
    b.login("priya.shah@example.com");
    var services = json.readTree(b.request("/api/provider/services", null).body());
    assertThat(services.size()).isEqualTo(2);
    for (var service : services) assertThat(service.path("providerId").asLong()).isEqualTo(1);
    assertThat(b.request("/api/provider/slots", null).body())
        .contains("Algebra Coaching")
        .doesNotContain("Essay Coaching");
    var customer = new Browser();
    customer.login("maya.patel@example.com");
    assertThat(customer.request("/api/customer/appointments/1", null).statusCode()).isEqualTo(200);
    var other = new Browser();
    other.login("sam.rivera@example.com");
    assertThat(other.request("/api/customer/appointments/1", null).statusCode()).isEqualTo(403);
  }

  @Test
  void archiveApiIsPaginatedReadOnlyAndOwnerScoped() throws Exception {
    jdbc.update(
        "INSERT INTO ai_chat_threads(thread_id,user_id,title) VALUES (900,1,'Saved conversation')");
    jdbc.update(
        "INSERT INTO ai_chat_messages(thread_id,role,body) VALUES (900,'user','Find my next"
            + " session')");
    var owner = new Browser();
    owner.login("maya.patel@example.com");
    var list = owner.request("/api/customer/ai/conversations", null);
    assertThat(list.statusCode()).isEqualTo(200);
    assertThat(list.body()).contains("Saved conversation", "hasNext");
    assertThat(owner.request("/api/customer/ai/conversations/900", null).body())
        .contains("Find my next session");
    assertThat(owner.request("/api/customer/ai/conversations?page=-1", null).statusCode())
        .isEqualTo(400);
    assertThat(owner.request("/api/customer/ai/history", null).body()).contains("\"messages\":[]");
    var other = new Browser();
    other.login("sam.rivera@example.com");
    assertThat(other.request("/api/customer/ai/conversations", null).body())
        .doesNotContain("Saved conversation");
    assertThat(other.request("/api/customer/ai/conversations/900", null).statusCode())
        .isEqualTo(404);
  }
}
