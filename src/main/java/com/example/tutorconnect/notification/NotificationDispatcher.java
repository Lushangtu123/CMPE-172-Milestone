package com.example.tutorconnect.notification;

import com.example.tutorconnect.observability.BookingMetrics;
import com.example.tutorconnect.repository.NotificationOutboxRepository;
import org.slf4j.*;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatcher {
  private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
  private final NotificationOutboxRepository outbox;
  private final NotificationGateway gateway;
  private final NotificationSettings settings;
  private final BookingMetrics metrics;

  public NotificationDispatcher(
      NotificationOutboxRepository outbox,
      NotificationGateway gateway,
      NotificationSettings settings,
      BookingMetrics metrics) {
    this.outbox = outbox;
    this.gateway = gateway;
    this.settings = settings;
    this.metrics = metrics;
  }

  public void afterCommit(long appointment, String type) {
    deliver(outbox.eventId(appointment, type));
  }

  public void deliver(String eventId) {
    if (!settings.enabled()) return;
    try {
      var request = outbox.pending(eventId);
      if (request.isEmpty()) return;
      if (request.get().type().equals("APPOINTMENT_REMINDER")
          && !outbox.reminderStillUpcoming(eventId)) {
        outbox.failed(eventId, 0, "NOT_UPCOMING", true);
        log.info("notification_suppressed eventId={} reason=NOT_UPCOMING", eventId);
        return;
      }
      var result = gateway.send(request.get());
      if (result.delivered()) {
        if (outbox.delivered(eventId, result.attempts()) == 1) {
          metrics.delivered();
          log.info("notification_delivered eventId={} attempts={}", eventId, result.attempts());
        }
      } else {
        outbox.failed(eventId, result.attempts(), result.reason(), result.permanent());
        if (result.permanent())
          log.error(
              "notification_rejected eventId={} reason={} action=manual_review",
              eventId,
              result.reason());
        else
          log.warn(
              "notification_deferred eventId={} reason={} action=scheduled_retry",
              eventId,
              result.reason());
      }
    } catch (RuntimeException e) {
      log.error(
          "notification_dispatch_failed eventId={} exception={} action=retry_persisted_event",
          eventId,
          e.getClass().getSimpleName());
    }
  }

  public void recover() {
    for (String id : outbox.due()) deliver(id);
  }
}
