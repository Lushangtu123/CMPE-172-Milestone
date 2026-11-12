package com.example.tutorconnect.ai;

import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderTransaction {
  private final JdbcTemplate jdbc;

  public ReminderTransaction(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public boolean enqueue(long appointment, String message) {
    var rows =
        jdbc.query(
            "SELECT a.status,s.start_time,u.email FROM appointments a JOIN availability_slots s ON"
                + " s.slot_id=a.slot_id JOIN users u ON u.user_id=a.user_id WHERE"
                + " a.appointment_id=? FOR UPDATE",
            (r, n) ->
                new Object[] {r.getString(1), r.getObject(2, LocalDateTime.class), r.getString(3)},
            appointment);
    if (rows.isEmpty()) return false;
    var row = rows.get(0);
    var start = (LocalDateTime) row[1];
    var now = LocalDateTime.now(ZoneOffset.UTC);
    if (!row[0].equals("BOOKED") || !start.isAfter(now) || start.isAfter(now.plusHours(24)))
      return false;
    String id = "appointment:" + appointment + ":APPOINTMENT_REMINDER";
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM notification_outbox WHERE event_id=?", Integer.class, id)
        > 0) return false;
    jdbc.update(
        "INSERT INTO notification_outbox(event_id,recipient,type,message)"
            + " VALUES(?,?,'APPOINTMENT_REMINDER',?)",
        id,
        row[2],
        message);
    return true;
  }
}
