package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AiWorkflowTests.Models.class)
@Sql(scripts = {"classpath:reset.sql", "classpath:seed.sql"})
class AiWorkflowTests {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  final ObjectMapper json = new ObjectMapper();

  HttpTestClient customer(String email) throws Exception {
    var b = new HttpTestClient(port);
    b.login(email);
    return b;
  }

  String chat(HttpTestClient b, String text) throws Exception {
    var r =
        b.post(
            "/api/customer/ai/chat",
            "message=" + URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8));
    assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    return json.readTree(r.body()).path("pending").path("token").asText();
  }

  @Test
  void firstTurnNeverWritesEvenIfModelRequestsBook() throws Exception {
    var b = customer("maya.patel@example.com");
    assertThat(chat(b, "book 1")).isNotBlank();
    var details =
        json.readTree(b.get("/api/customer/ai/history").body()).path("pending").path("details");
    assertThat(details.path("service").asText()).isEqualTo("Algebra Coaching");
    assertThat(details.path("tutor").asText()).isEqualTo("Priya Shah");
    assertThat(details.path("time").asText()).contains("UTC").doesNotContain("T0");
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id=1", Integer.class))
        .isZero();
  }

  @Test
  void explicitConfirmationCommitsThroughExistingServiceAndCannotReplay() throws Exception {
    var b = customer("maya.patel@example.com");
    var token = chat(b, "book 1");
    assertThat(b.post("/api/customer/ai/confirm", "token=" + token).statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE active_slot_id=1", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE type='BOOKING_CONFIRMED'",
                Integer.class))
        .isEqualTo(1);
    assertThat(b.post("/api/customer/ai/confirm", "token=" + token).statusCode()).isEqualTo(409);
  }

  @Test
  void anotherSessionCannotConfirmProposal() throws Exception {
    var a = customer("maya.patel@example.com");
    var b = customer("sam.rivera@example.com");
    var token = chat(a, "book 1");
    assertThat(b.post("/api/customer/ai/confirm", "token=" + token).statusCode()).isEqualTo(409);
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id=1", Integer.class))
        .isZero();
  }

  @Test
  void twoAgentCustomersExactlyOneWinsAfterConfirmation() throws Exception {
    var a = customer("maya.patel@example.com");
    var b = customer("sam.rivera@example.com");
    var ta = chat(a, "book 1");
    var tb = chat(b, "book 1");
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var fa =
          pool.submit(
              () -> {
                ready.countDown();
                start.await();
                return a.post("/api/customer/ai/confirm", "token=" + ta).statusCode();
              });
      var fb =
          pool.submit(
              () -> {
                ready.countDown();
                start.await();
                return b.post("/api/customer/ai/confirm", "token=" + tb).statusCode();
              });
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(fa.get(10, TimeUnit.SECONDS), fb.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM appointments WHERE active_slot_id=1", Integer.class))
          .isEqualTo(1);
    } finally {
      start.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void cancellationNeedsConfirmationAndChecksOwner() throws Exception {
    var sam = customer("sam.rivera@example.com");
    var denied = sam.post("/api/customer/ai/chat", "message=cancel+1");
    assertThat(denied.statusCode()).isEqualTo(403);
    var maya = customer("maya.patel@example.com");
    var token = chat(maya, "cancel 1");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM appointments WHERE appointment_id=1", String.class))
        .isEqualTo("BOOKED");
    assertThat(maya.post("/api/customer/ai/confirm", "token=" + token).statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM appointments WHERE appointment_id=1", String.class))
        .isEqualTo("CANCELLED");
  }

  @Test
  void providerAndAnonymousCannotUseCustomerAgent() throws Exception {
    assertThat(new HttpTestClient(port).get("/api/customer/ai/history").statusCode())
        .isEqualTo(401);
    var p = customer("priya.shah@example.com");
    assertThat(p.post("/api/customer/ai/chat", "message=book+1").statusCode()).isEqualTo(403);
  }

  @Test
  void confirmationRequiresCsrf() throws Exception {
    var b = customer("maya.patel@example.com");
    var token = chat(b, "book 1");
    b.csrf = null;
    assertThat(b.post("/api/customer/ai/confirm", "token=" + token).statusCode()).isEqualTo(403);
  }

  @Test
  void malformedModelDatesAreCorrectedWithoutFailingTheChat() throws Exception {
    var b = customer("maya.patel@example.com");
    var response = b.post("/api/customer/ai/chat", "message=retry+dates");
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(response.body()).contains("I checked the current session availability.");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointments", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void multiStepAvailabilityThenBookAndMultiTurnHistory() throws Exception {
    var b = customer("maya.patel@example.com");
    assertThat(chat(b, "earliest")).isNotBlank();
    var r = b.get("/api/customer/ai/history");
    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(r.body()).contains("earliest");
    chat(b, "list");
    assertThat(b.get("/api/customer/ai/history").body()).contains("list");
  }

  @Test
  void appointmentReplyShowsReadableDetailsInsteadOfToolPayload() throws Exception {
    var b = customer("maya.patel@example.com");
    var result = b.post("/api/customer/ai/chat", "message=List+my+upcoming+appointments.");
    assertThat(result.statusCode()).isEqualTo(200);
    String reply = json.readTree(result.body()).path("messages").get(1).path("text").asText();
    assertThat(reply)
        .contains("Your upcoming appointments", "Algebra Coaching with Priya Shah", "UTC")
        .doesNotContain("ToolResponse", "responseData", "\"userId\"", "\"slotId\"");
  }

  @Test
  void chatHistorySurvivesNewChatAndLogoutAndIsPrivate() throws Exception {
    var maya = customer("maya.patel@example.com");
    var token = chat(maya, "book 1");
    var historyPath = "/customer/assistant/history";
    assertThat(maya.get("/customer/assistant").body()).contains("Chat history");
    assertThat(maya.post("/api/customer/ai/reset", "").body()).contains("\"messages\":[]");
    assertThat(maya.post("/api/customer/ai/confirm", "token=" + token).statusCode()).isEqualTo(409);
    chat(maya, "list");
    var cancellation = chat(maya, "cancel 1");
    assertThat(maya.post("/api/customer/ai/confirm", "token=" + cancellation).statusCode())
        .isEqualTo(200);
    assertThat(maya.post("/logout", "").statusCode()).isEqualTo(302);

    var returning = customer("maya.patel@example.com");
    var history = returning.get(historyPath);
    assertThat(history.statusCode()).isEqualTo(200);
    assertThat(history.body()).contains("book 1", ">list<");
    var thread = java.util.regex.Pattern.compile(historyPath + "/[0-9]+").matcher(history.body());
    assertThat(thread.find()).isTrue();
    var latest = thread.group();
    assertThat(thread.find()).isTrue();
    var older = thread.group();
    assertThat(returning.get(older).body()).contains("book 1").doesNotContain(token, ">list<");
    assertThat(returning.get(latest).body())
        .contains(">list<", "cancel 1", "Cancellation confirmed. Appointment #1.")
        .doesNotContain("book 1", cancellation);
    var sam = customer("sam.rivera@example.com");
    assertThat(sam.get(historyPath).body()).doesNotContain("book 1", ">list<");
    assertThat(sam.get(older).statusCode()).isEqualTo(404);
    assertThat(customer("priya.shah@example.com").get(older).statusCode()).isEqualTo(403);
  }

  @Test
  void newQuestionInvalidatesOldPendingWrite() throws Exception {
    var b = customer("maya.patel@example.com");
    var t = chat(b, "book 1");
    chat(b, "list");
    assertThat(b.post("/api/customer/ai/confirm", "token=" + t).statusCode()).isEqualTo(409);
  }

  @Test
  void policyAnswerUsesRetrievedPassageAndSource() throws Exception {
    var b = customer("maya.patel@example.com");
    chat(b, "cancellation policy");
    assertThat(b.get("/api/customer/ai/history").body()).contains("policy-cancellation", "future");
  }

  @Test
  void reminderJobGeneratesAndEnqueuesOnce() throws Exception {
    jdbc.update(
        "UPDATE availability_slots SET start_time=?,end_time=? WHERE slot_id=2",
        java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(2),
        java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(3));
    var p = customer("priya.shah@example.com");
    var first = p.post("/api/provider/ai/reminders/run", "");
    assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE type='APPOINTMENT_REMINDER'",
                Integer.class))
        .isEqualTo(1);
    assertThat(p.post("/api/provider/ai/reminders/run", "").statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE type='APPOINTMENT_REMINDER'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT message FROM notification_outbox WHERE type='APPOINTMENT_REMINDER'",
                String.class))
        .contains("Prepare your algebra questions");
  }

  @Test
  void customerCannotTriggerAutonomousJob() throws Exception {
    var b = customer("maya.patel@example.com");
    assertThat(b.post("/api/provider/ai/reminders/run", "").statusCode()).isEqualTo(403);
  }

  @Test
  void providerHasManualReminderControl() throws Exception {
    var b = customer("priya.shah@example.com");
    assertThat(b.get("/provider/dashboard").body()).contains("reminder-form", "Run reminder agent");
  }

  @TestConfiguration
  static class Models {
    @Bean
    ChatModel testChatModel() {
      return new ScriptedModel();
    }

    @Bean
    EmbeddingModel testEmbeddingModel() {
      return new TestEmbeddings();
    }
  }

  // Scripted external model: real Spring AI tool loop, services, HTTP and DB are exercised.
  static class TestEmbeddings implements EmbeddingModel {
    public float[] embed(Document d) {
      return embed(d.getText());
    }

    public EmbeddingResponse call(EmbeddingRequest r) {
      var results = new ArrayList<Embedding>();
      int i = 0;
      for (String text : r.getInstructions()) {
        float[] v = new float[5];
        String t = text.toLowerCase();
        v[0] = t.contains("cancel") ? 1 : 0;
        v[1] = t.contains("hour") ? 1 : 0;
        v[2] = t.contains("service") ? 1 : 0;
        v[3] = t.contains("remind") ? 1 : 0;
        v[4] = .05f;
        results.add(new Embedding(v, i++));
      }
      return new EmbeddingResponse(results);
    }
  }

  static class ScriptedModel implements ChatModel {
    private final ObjectMapper json = new ObjectMapper();
    private static final DateTimeFormatter UTC_TIME =
        DateTimeFormatter.ofPattern("MMM d, uuuu 'at' HH:mm 'UTC'", Locale.ENGLISH);

    public ChatResponse call(Prompt prompt) {
      var messages = prompt.getInstructions();
      String user =
          messages.stream()
              .filter(x -> x instanceof UserMessage)
              .reduce((a, b) -> b)
              .map(Message::getText)
              .orElse("");
      if (user.startsWith("REMINDER"))
        return new ChatResponse(
            List.of(
                new Generation(
                    new AssistantMessage(
                        "Prepare your algebra questions for your upcoming session."))));
      boolean toolLast = messages.get(messages.size() - 1) instanceof ToolResponseMessage;
      if (toolLast) {
        var last = (ToolResponseMessage) messages.get(messages.size() - 1);
        if (user.equals("retry dates")) {
          if (last.getResponses().stream().anyMatch(r -> r.responseData().contains("yyyy-MM-dd")))
            return tool(
                "checkAvailability",
                "{\"fromDate\":\""
                    + java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                    + "\",\"toDate\":\""
                    + java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(6)
                    + "\"}");
          return new ChatResponse(
              List.of(
                  new Generation(
                      new AssistantMessage("I checked the current session availability."))));
        }
        if (user.equals("earliest")
            && last.getResponses().stream().anyMatch(x -> x.name().equals("checkAvailability")))
          return tool("book", "{\"slotId\":1,\"serviceId\":1}");
        var response = last.getResponses().get(0);
        var data = json.readTree(response.responseData());
        String answer =
            switch (response.name()) {
              case "book", "cancel" ->
                  data.has("summary")
                      ? "Please review the details and confirm if they are correct.\n\n"
                          + data.path("summary").asText()
                      : data.path("message").asText();
              case "listMyAppointments" ->
                  appointmentsReply(data, user.toLowerCase(Locale.ROOT).contains("upcoming"));
              case "searchKnowledge" -> policyReply(data);
              default ->
                  throw new IllegalStateException("No scripted reply for " + response.name());
            };
        return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
      }
      if (user.contains("policy"))
        return tool("searchKnowledge", "{\"question\":\"cancellation policy\"}");
      if (user.equals("retry dates"))
        return tool("checkAvailability", "{\"fromDate\":\"today\",\"toDate\":\"next week\"}");
      if (user.startsWith("book"))
        return tool("book", "{\"slotId\":" + user.split(" ")[1] + ",\"serviceId\":1}");
      if (user.startsWith("cancel"))
        return tool("cancel", "{\"appointmentId\":" + user.split(" ")[1] + "}");
      if (user.equals("earliest"))
        return tool(
            "checkAvailability",
            "{\"fromDate\":\""
                + java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                + "\",\"toDate\":\""
                + java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(6)
                + "\"}");
      return tool("listMyAppointments", "{}");
    }

    private String appointmentsReply(JsonNode appointments, boolean upcoming) {
      var lines = new ArrayList<String>();
      for (var appointment : appointments) {
        if (upcoming && !appointment.path("status").asText().equals("BOOKED")) continue;
        var start = LocalDateTime.parse(appointment.path("startTime").asText());
        lines.add(
            "• "
                + appointment.path("serviceName").asText()
                + " with "
                + appointment.path("providerName").asText()
                + " on "
                + UTC_TIME.format(start));
      }
      String description = upcoming ? "upcoming appointments" : "appointments";
      return lines.isEmpty()
          ? "You have no " + description + "."
          : "Your " + description + ":\n" + String.join("\n", lines);
    }

    private String policyReply(JsonNode passages) {
      var answers = new ArrayList<String>();
      for (var passage : passages)
        answers.add(passage.path("text").asText() + "\nSource: " + passage.path("source").asText());
      return answers.isEmpty()
          ? "I could not find a policy that answers that question."
          : String.join("\n\n", answers);
    }

    ChatResponse tool(String name, String args) {
      return new ChatResponse(
          List.of(
              new Generation(
                  AssistantMessage.builder()
                      .content("")
                      .toolCalls(
                          List.of(
                              new AssistantMessage.ToolCall(
                                  UUID.randomUUID().toString(), "function", name, args)))
                      .build())));
    }
  }
}
