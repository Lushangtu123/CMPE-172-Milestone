package com.example.tutorconnect.repository;

import com.example.tutorconnect.dto.AppointmentDto;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentRepository {
  private final JdbcTemplate jdbc;

  public AppointmentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String SELECT =
      """
      SELECT a.appointment_id,a.user_id,a.slot_id,a.status,cu.name customer_name,pu.name provider_name,sv.name service_name,s.start_time,s.end_time
      FROM appointments a JOIN users cu ON cu.user_id=a.user_id JOIN availability_slots s ON s.slot_id=a.slot_id
      JOIN services sv ON sv.service_id=s.service_id JOIN providers p ON p.provider_id=sv.provider_id JOIN users pu ON pu.user_id=p.user_id
      """;
  private static final RowMapper<AppointmentDto> MAPPER =
      (rs, n) ->
          new AppointmentDto(
              rs.getLong("appointment_id"),
              rs.getLong("user_id"),
              rs.getLong("slot_id"),
              rs.getString("customer_name"),
              rs.getString("provider_name"),
              rs.getString("service_name"),
              rs.getObject("start_time", LocalDateTime.class),
              rs.getObject("end_time", LocalDateTime.class),
              rs.getString("status"));

  public Optional<AppointmentDto> find(long id) {
    return jdbc.query(SELECT + " WHERE a.appointment_id=?", MAPPER, id).stream().findFirst();
  }

  public boolean occupied(long slot) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM appointments WHERE active_slot_id=?", Long.class, slot)
        > 0;
  }

  public long insert(long user, long slot) {
    KeyHolder key = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          var ps =
              con.prepareStatement(
                  "INSERT INTO appointments(user_id,slot_id,status) VALUES(?,?,'BOOKED')",
                  new String[] {"appointment_id"});
          ps.setLong(1, user);
          ps.setLong(2, slot);
          return ps;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  public void cancel(long id) {
    jdbc.update("UPDATE appointments SET status='CANCELLED' WHERE appointment_id=?", id);
  }

  public List<AppointmentDto> customer(long user) {
    return jdbc.query(
        SELECT + " WHERE a.user_id=? ORDER BY s.start_time,a.appointment_id", MAPPER, user);
  }

  public List<AppointmentDto> provider(long user) {
    return jdbc.query(
        SELECT + " WHERE p.user_id=? ORDER BY s.start_time,a.appointment_id", MAPPER, user);
  }

  public void completePast(long user, boolean provider, LocalDateTime now) {
    String ids =
        "SELECT s.slot_id FROM availability_slots s JOIN services sv ON sv.service_id=s.service_id"
            + " JOIN providers p ON p.provider_id=sv.provider_id WHERE s.end_time<=?";
    if (provider) ids += " AND p.user_id=?";
    String sql =
        "UPDATE appointments SET status='COMPLETED' WHERE status='BOOKED' AND slot_id IN ("
            + ids
            + ")";
    if (provider) jdbc.update(sql, now, user);
    else jdbc.update(sql + " AND user_id=?", now, user);
  }
}
