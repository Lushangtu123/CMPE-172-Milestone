package com.example.tutorconnect.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;

class GeminiAdapterTests {
  @Test
  void actualGeminiAdapterHidesThoughtPartsAndKeepsCompleteFinalText() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        x -> {
          x.getRequestBody().readAllBytes();
          byte[] b =
              """
{"candidates":[{"content":{"role":"model","parts":[{"text":"PRIVATE_REASONING","thought":true},{"text":"Public answer one."},{"text":"Public answer two."}]},"finishReason":"STOP"}],"modelVersion":"gemini-3.8-flash","usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":5,"totalTokenCount":10}}
"""
                  .getBytes(StandardCharsets.UTF_8);
          x.getResponseHeaders().set("Content-Type", "application/json");
          x.sendResponseHeaders(200, b.length);
          x.getResponseBody().write(b);
          x.close();
        });
    server.start();
    try (var client =
        Client.builder()
            .apiKey("test-key")
            .vertexAI(false)
            .httpOptions(
                HttpOptions.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .timeout(2000)
                    .build())
            .build()) {
      var model = new AiModelConfiguration().chatModel(client, "gemini-3.8-flash");
      assertThat(ChatClient.create(model).prompt("Hello").call().content())
          .contains("Public answer one.", "Public answer two.")
          .doesNotContain("PRIVATE_REASONING");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void actualGeminiAdapterCompletesSignedFunctionCallRoundTrip() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var calls = new AtomicInteger();
    var tools = new EchoTools();
    server.createContext(
        "/",
        x -> {
          String request = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          String response =
              calls.getAndIncrement() == 0
                  ? """
{"candidates":[{"content":{"role":"model","parts":[{"thought":true,"text":"PRIVATE_REASONING"},{"functionCall":{"id":"call-1","name":"echo","args":{"text":"hello"}},"thoughtSignature":"c2lnbmF0dXJl"}]},"finishReason":"STOP"}],"modelVersion":"gemini-3.8-flash","usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":5,"totalTokenCount":10}}
"""
                  : """
{"candidates":[{"content":{"role":"model","parts":[{"text":"Tool completed."}]},"finishReason":"STOP"}],"modelVersion":"gemini-3.8-flash","usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":5,"totalTokenCount":10}}
""";
          if (calls.get() == 2) {
            assertThat(request).contains("functionResponse", "thoughtSignature");
          }
          byte[] b = response.getBytes(StandardCharsets.UTF_8);
          x.getResponseHeaders().set("Content-Type", "application/json");
          x.sendResponseHeaders(200, b.length);
          x.getResponseBody().write(b);
          x.close();
        });
    server.start();
    try (var client =
        Client.builder()
            .apiKey("test-key")
            .vertexAI(false)
            .httpOptions(
                HttpOptions.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .timeout(2000)
                    .build())
            .build()) {
      var model = new AiModelConfiguration().chatModel(client, "gemini-3.8-flash");
      assertThat(ChatClient.create(model).prompt("Use echo").tools(tools).call().content())
          .isEqualTo("Tool completed.");
      assertThat(tools.count).isEqualTo(1);
      assertThat(calls).hasValue(2);
    } finally {
      server.stop(0);
    }
  }

  static class EchoTools {
    int count;

    @Tool(description = "Echo text")
    public String echo(String text) {
      count++;
      return text;
    }
  }
}
