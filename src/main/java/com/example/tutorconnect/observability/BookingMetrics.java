package com.example.tutorconnect.observability;

import io.micrometer.core.instrument.*;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class BookingMetrics {
  private final Counter succeeded, failed, cancelled, delivered;
  private final Timer latency;

  public BookingMetrics(MeterRegistry registry) {
    succeeded = registry.counter("tutorconnect.bookings", "outcome", "success");
    failed = registry.counter("tutorconnect.bookings", "outcome", "failure");
    cancelled = registry.counter("tutorconnect.cancellations");
    delivered = registry.counter("tutorconnect.notifications.delivered");
    latency = registry.timer("tutorconnect.booking.latency");
  }

  public void booked() {
    succeeded.increment();
  }

  public void bookingFailed() {
    failed.increment();
  }

  public void cancelled() {
    cancelled.increment();
  }

  public void delivered() {
    delivered.increment();
  }

  public void timing(long nanos) {
    latency.record(nanos, TimeUnit.NANOSECONDS);
  }

  public record Snapshot(
      double bookingsSucceeded,
      double bookingsFailed,
      double cancellations,
      double notificationsDelivered,
      long notificationPending,
      long notificationFailed,
      double bookingAverageMillis) {}

  public Snapshot snapshot(long pending, long failedNotifications) {
    return new Snapshot(
        succeeded.count(),
        failed.count(),
        cancelled.count(),
        delivered.count(),
        pending,
        failedNotifications,
        latency.mean(TimeUnit.MILLISECONDS));
  }
}
