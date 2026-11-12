package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiDisabledTests {
  @LocalServerPort int port;

  @Test
  void missingKeyLeavesExistingAppUsableAndAiReturns503() throws Exception {
    var b = new HttpTestClient(port);
    b.login("maya.patel@example.com");
    assertThat(b.get("/customer/assistant").statusCode()).isEqualTo(200);
    assertThat(b.post("/api/customer/ai/chat", "message=hello").statusCode()).isEqualTo(503);
    assertThat(b.get("/health").statusCode()).isEqualTo(200);
  }
}
