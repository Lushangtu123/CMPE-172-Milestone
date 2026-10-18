package com.example.tutorconnect.notification;

import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationGateway {
  private static final Logger log = LoggerFactory.getLogger(NotificationGateway.class);
  private final NotificationSettings settings;
  private final ObjectMapper json;
  private final HttpClient http;

  public record Result(boolean delivered, boolean permanent, int attempts, String reason) {}

  public NotificationGateway(NotificationSettings settings, ObjectMapper json) {
    this.settings = settings;
    this.json = json;
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(settings.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public Result send(NotificationRequest notification) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException(
          "Remote notification must run outside a database transaction");
    String payload = json.writeValueAsString(notification);
    String reason = "UNAVAILABLE";
    for (int attempt = 1; attempt <= settings.maxAttempts(); attempt++) {
      CompletableFuture<HttpResponse<Void>> pending = null;
      try {
        var request =
            HttpRequest.newBuilder(settings.url())
                .timeout(settings.requestTimeout())
                .header("Content-Type", "application/json")
                .header("X-Notification-Key", settings.token())
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        pending = http.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        int status =
            pending.get(settings.requestTimeout().toMillis(), TimeUnit.MILLISECONDS).statusCode();
        if (status >= 200 && status < 300) return new Result(true, false, attempt, "OK");
        reason = "HTTP_" + status;
        if (status != 408 && status != 429 && status < 500)
          return new Result(false, true, attempt, reason);
      } catch (InterruptedException e) {
        if (pending != null) pending.cancel(true);
        Thread.currentThread().interrupt();
        return new Result(false, false, attempt, "INTERRUPTED");
      } catch (TimeoutException e) {
        if (pending != null) pending.cancel(true);
        reason = "TIMEOUT";
      } catch (ExecutionException e) {
        reason = "IO_ERROR";
      }
      log.warn(
          "notification_attempt_failed eventId={} attempt={} reason={}",
          notification.eventId(),
          attempt,
          reason);
      if (attempt < settings.maxAttempts())
        try {
          Thread.sleep(settings.retryDelay().toMillis());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return new Result(false, false, attempt, "INTERRUPTED");
        }
    }
    return new Result(false, false, settings.maxAttempts(), reason);
  }
}
