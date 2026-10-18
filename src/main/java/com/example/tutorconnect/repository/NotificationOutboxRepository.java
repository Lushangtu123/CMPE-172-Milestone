package com.example.tutorconnect.repository;

import com.example.tutorconnect.notification.NotificationRequest;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationOutboxRepository {
  private final JdbcTemplate jdbc;

  public NotificationOutboxRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void enqueue(long user, long appointment, String type) {
    NotificationRequest request =
        jdbc.queryForObject(
            """
            SELECT u.email,sv.name,s.start_time FROM appointments a JOIN users u ON u.user_id=a.user_id
            JOIN availability_slots s ON s.slot_id=a.slot_id JOIN services sv ON sv.service_id=s.service_id
            WHERE a.appointment_id=? AND a.user_id=?
            """,
            (rs, n) ->
                new NotificationRequest(
                    "appointment:" + appointment + ":" + type,
                    rs.getString("email"),
                    type,
                    (type.equals("BOOKING_CONFIRMED")
                            ? "Booking confirmed"
                            : "Cancellation confirmed")
                        + " for appointment #"
                        + appointment
                        + ": "
                        + rs.getString("name")
                        + " at "
                        + rs.getObject("start_time", LocalDateTime.class)
                        + " UTC."),
            appointment,
            user);
    jdbc.update(
        "INSERT INTO notification_outbox(event_id,recipient,type,message) VALUES(?,?,?,?)",
        request.eventId(),
        request.recipient(),
        request.type(),
        request.message());
  }

  public String eventId(long appointment, String type) {
    return "appointment:" + appointment + ":" + type;
  }

  public Optional<NotificationRequest> pending(String id) {
    return jdbc
        .query(
            "SELECT event_id,recipient,type,message FROM notification_outbox WHERE event_id=? AND"
                + " state='PENDING'",
            (rs, n) ->
                new NotificationRequest(
                    rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
            id)
        .stream()
        .findFirst();
  }

  public List<String> due() {
    return jdbc.query(
        "SELECT event_id FROM notification_outbox WHERE state='PENDING' AND next_attempt_at<=?"
            + " ORDER BY next_attempt_at,created_at,event_id LIMIT 20",
        (rs, n) -> rs.getString(1),
        LocalDateTime.now(ZoneOffset.UTC));
  }

  public int delivered(String id, int attempts) {
    return jdbc.update(
        "UPDATE notification_outbox SET state='SENT',attempts=attempts+?,last_error=NULL WHERE"
            + " event_id=? AND state='PENDING'",
        attempts,
        id);
  }

  public int failed(String id, int attempts, String error, boolean permanent) {
    return jdbc.update(
        "UPDATE notification_outbox SET state=?,attempts=attempts+?,last_error=?,next_attempt_at=?"
            + " WHERE event_id=? AND state='PENDING'",
        permanent ? "FAILED" : "PENDING",
        attempts,
        error,
        LocalDateTime.now(ZoneOffset.UTC).plusSeconds(15),
        id);
  }

  public long count(String state) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM notification_outbox WHERE state=?", Long.class, state);
  }
}
