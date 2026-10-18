package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.tutorconnect.notification.*;
import com.example.tutorconnect.observability.*;
import com.example.tutorconnect.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class NotificationRecoveryTests {
  @Test
  void databaseOutageReturnsSafe503() throws Exception {
    var db = mock(DataSource.class);
    when(db.getConnection()).thenThrow(new SQLException("private connection details", "08001"));
    var result = new HealthController(db).health();
    assertThat(result.getStatusCode().value()).isEqualTo(503);
    assertThat(result.getBody()).containsEntry("database", "DOWN");
    assertThat(result.getBody().toString()).doesNotContain("private");
  }

  @Test
  void successfulRecoveryMarksDeferredEventDelivered() {
    var outbox = mock(NotificationOutboxRepository.class);
    var gateway = mock(NotificationGateway.class);
    var registry = new SimpleMeterRegistry();
    var metrics = new BookingMetrics(registry);
    var request =
        new NotificationRequest("event:1", "demo@example.com", "BOOKING_CONFIRMED", "Hello");
    when(outbox.due()).thenReturn(List.of(request.eventId()));
    when(outbox.pending(request.eventId())).thenReturn(Optional.of(request));
    when(gateway.send(request)).thenReturn(new NotificationGateway.Result(true, false, 1, "OK"));
    when(outbox.delivered(request.eventId(), 1)).thenReturn(1);
    new NotificationDispatcher(outbox, gateway, settings(), metrics).recover();
    verify(outbox).delivered(request.eventId(), 1);
    assertThat(metrics.snapshot(0, 0).notificationsDelivered()).isEqualTo(1);
  }

  @Test
  void unexpectedDispatchFailureDoesNotEscapeIntoBookingResponse() {
    var outbox = mock(NotificationOutboxRepository.class);
    when(outbox.pending("event:1")).thenThrow(new IllegalStateException("DB unavailable"));
    assertThatCode(
            () ->
                new NotificationDispatcher(
                        outbox,
                        mock(NotificationGateway.class),
                        settings(),
                        new BookingMetrics(new SimpleMeterRegistry()))
                    .deliver("event:1"))
        .doesNotThrowAnyException();
  }

  @Test
  void sentEventsAreNotDispatchedAgain() {
    var outbox = mock(NotificationOutboxRepository.class);
    var gateway = mock(NotificationGateway.class);
    when(outbox.pending("event:1")).thenReturn(Optional.empty());
    new NotificationDispatcher(
            outbox, gateway, settings(), new BookingMetrics(new SimpleMeterRegistry()))
        .deliver("event:1");
    verifyNoInteractions(gateway);
  }

  @Test
  void metricsCountSuccessFailureCancellationAndLatency() {
    var metrics = new BookingMetrics(new SimpleMeterRegistry());
    metrics.booked();
    metrics.bookingFailed();
    metrics.cancelled();
    metrics.timing(2_000_000);
    var s = metrics.snapshot(2, 1);
    assertThat(s.bookingsSucceeded()).isEqualTo(1);
    assertThat(s.bookingsFailed()).isEqualTo(1);
    assertThat(s.cancellations()).isEqualTo(1);
    assertThat(s.bookingAverageMillis()).isEqualTo(2);
    assertThat(s.notificationPending()).isEqualTo(2);
  }

  NotificationSettings settings() {
    return new NotificationSettings(
        URI.create("http://127.0.0.1:8080/internal/notifications"),
        "test",
        Duration.ofMillis(100),
        Duration.ofMillis(100),
        3,
        Duration.ZERO,
        true);
  }
}
