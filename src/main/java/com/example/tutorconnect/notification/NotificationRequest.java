package com.example.tutorconnect.notification;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.example.tutorconnect.exception.AppException;

public record NotificationRequest(String eventId, String recipient, String type, String message) {
  public void validate() {
    if (eventId == null
        || !eventId.matches("[A-Za-z0-9:_-]{1,120}")
        || recipient == null
        || recipient.isBlank()
        || recipient.length() > 255
        || type == null
        || !java.util.Set.of("BOOKING_CONFIRMED", "CANCELLATION_CONFIRMED", "APPOINTMENT_REMINDER")
            .contains(type)
        || message == null
        || message.isBlank()
        || message.length() > 1000)
      throw new AppException(BAD_REQUEST, "Invalid notification request.");
  }
}
