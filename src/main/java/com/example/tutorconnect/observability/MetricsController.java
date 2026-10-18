package com.example.tutorconnect.observability;

import com.example.tutorconnect.repository.NotificationOutboxRepository;
import org.springframework.web.bind.annotation.*;

@RestController
public class MetricsController {
  private final BookingMetrics metrics;
  private final NotificationOutboxRepository outbox;

  public MetricsController(BookingMetrics metrics, NotificationOutboxRepository outbox) {
    this.metrics = metrics;
    this.outbox = outbox;
  }

  @GetMapping("/api/provider/metrics")
  public BookingMetrics.Snapshot metrics() {
    return metrics.snapshot(outbox.count("PENDING"), outbox.count("FAILED"));
  }
}
