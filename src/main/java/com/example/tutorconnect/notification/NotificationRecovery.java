package com.example.tutorconnect.notification;

import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@ConditionalOnProperty(
    name = {"notifications.enabled", "notifications.recovery.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class NotificationRecovery {
  private static final Logger log = LoggerFactory.getLogger(NotificationRecovery.class);
  private final NotificationDispatcher dispatcher;

  public NotificationRecovery(NotificationDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Scheduled(
      fixedDelayString = "${notifications.recovery-delay:15000}",
      initialDelayString = "${notifications.recovery-delay:15000}")
  public void recover() {
    try {
      dispatcher.recover();
    } catch (RuntimeException e) {
      log.error(
          "notification_recovery_failed exception={} action=retry_next_cycle",
          e.getClass().getSimpleName());
    }
  }
}
