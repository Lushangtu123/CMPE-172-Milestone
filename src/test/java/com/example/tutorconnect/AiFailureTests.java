package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AiFailureTests.Models.class)
@Sql(scripts = {"classpath:reset.sql", "classpath:seed.sql"})
class AiFailureTests {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;

  HttpTestClient customer(String email) throws Exception {
    var b = new HttpTestClient(port);
    b.login(email);
    return b;
  }

  @Test
  void modelFailureIsSafeAndDoesNotWrite() throws Exception {
    var b = customer("maya.patel@example.com");
    var r = b.post("/api/customer/ai/chat", "message=fail");
    assertThat(r.statusCode()).isEqualTo(503);
    assertThat(r.body()).doesNotContain("SECRET_MODEL_DETAIL");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointments", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void endlessModelToolLoopIsBounded() throws Exception {
    var b = customer("maya.patel@example.com");
    var r = b.post("/api/customer/ai/chat", "message=loop");
    assertThat(r.statusCode()).isEqualTo(400);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointments", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void failedLlmGenerationCreatesNoPretendAiReminder() throws Exception {
    var now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
    jdbc.update(
        "UPDATE availability_slots SET start_time=?,end_time=? WHERE slot_id=2",
        now.plusHours(2),
        now.plusHours(3));
    var p = customer("priya.shah@example.com");
    var r = p.post("/api/provider/ai/reminders/run", "");
    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(r.body()).contains("\"failed\":1");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox", Integer.class))
        .isZero();
  }

  @TestConfiguration
  static class Models {
    @Bean
    ChatModel brokenModel() {
      return new ChatModel() {
        public ChatResponse call(Prompt p) {
          if (p.getInstructions().stream()
              .anyMatch(m -> m instanceof UserMessage && m.getText().equals("loop")))
            return new ChatResponse(
                List.of(
                    new Generation(
                        AssistantMessage.builder()
                            .content("")
                            .toolCalls(
                                List.of(
                                    new AssistantMessage.ToolCall(
                                        UUID.randomUUID().toString(),
                                        "function",
                                        "listMyAppointments",
                                        "{}")))
                            .build())));
          throw new IllegalStateException("SECRET_MODEL_DETAIL");
        }
      };
    }
  }
}
