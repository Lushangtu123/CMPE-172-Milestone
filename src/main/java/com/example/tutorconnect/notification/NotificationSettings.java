package com.example.tutorconnect.notification;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record NotificationSettings(
    @Value("${notifications.url:http://127.0.0.1:8080/internal/notifications}") URI url,
    @Value("${notifications.service-token:tutorconnect-local-notification-key}") String token,
    @Value("${notifications.connect-timeout:200ms}") Duration connectTimeout,
    @Value("${notifications.request-timeout:300ms}") Duration requestTimeout,
    @Value("${notifications.max-attempts:3}") int maxAttempts,
    @Value("${notifications.retry-delay:25ms}") Duration retryDelay,
    @Value("${notifications.enabled:true}") boolean enabled) {
  public NotificationSettings {
    if (!java.util.Set.of("http", "https").contains(url.getScheme())
        || url.getHost() == null
        || url.getUserInfo() != null
        || token == null
        || token.isBlank()
        || connectTimeout.isZero()
        || connectTimeout.isNegative()
        || requestTimeout.isZero()
        || requestTimeout.isNegative()
        || retryDelay.isNegative()
        || maxAttempts < 1
        || maxAttempts > 5) throw new IllegalArgumentException("Invalid notification settings.");
  }
}
