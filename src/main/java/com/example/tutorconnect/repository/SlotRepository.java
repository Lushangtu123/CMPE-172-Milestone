package com.example.tutorconnect.repository;

import com.example.tutorconnect.dto.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class SlotRepository {
  private final JdbcTemplate jdbc;

  public SlotRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record LockedSlot(
      long slotId,
      long serviceId,
      long providerUserId,
      LocalDateTime startTime,
      LocalDateTime endTime,
      boolean active) {}

  public Optional<LockedSlot> lock(long id) {
    return jdbc
        .query(
            """
            SELECT s.*,(SELECT p.user_id FROM services sv JOIN providers p ON p.provider_id=sv.provider_id WHERE sv.service_id=s.service_id) AS provider_user_id
            FROM availability_slots s WHERE s.slot_id=? FOR UPDATE
            """,
            (rs, n) ->
                new LockedSlot(
                    rs.getLong("slot_id"),
                    rs.getLong("service_id"),
                    rs.getLong("provider_user_id"),
                    rs.getObject("start_time", LocalDateTime.class),
                    rs.getObject("end_time", LocalDateTime.class),
                    rs.getBoolean("active")),
            id)
        .stream()
        .findFirst();
  }

  private static final String SELECT =
      """
      SELECT s.slot_id,s.service_id,sv.provider_id,u.name provider_name,sv.name service_name,sv.description,sv.duration_minutes,s.start_time,s.end_time,
      CASE WHEN a.appointment_id IS NULL THEN 'OPEN' ELSE 'BOOKED' END status
      FROM availability_slots s JOIN services sv ON sv.service_id=s.service_id JOIN providers p ON p.provider_id=sv.provider_id
      JOIN users u ON u.user_id=p.user_id LEFT JOIN appointments a ON a.active_slot_id=s.slot_id
      """;
  private static final RowMapper<SlotDto> MAPPER =
      (rs, n) ->
          new SlotDto(
              rs.getLong("slot_id"),
              rs.getLong("service_id"),
              rs.getLong("provider_id"),
              rs.getString("provider_name"),
              rs.getString("service_name"),
              rs.getString("description"),
              rs.getInt("duration_minutes"),
              rs.getObject("start_time", LocalDateTime.class),
              rs.getObject("end_time", LocalDateTime.class),
              rs.getString("status"));

  public SlotPage available(
      Long provider, Long service, LocalDate date, int page, int size, LocalDateTime now) {
    StringBuilder where =
        new StringBuilder(" WHERE s.active=TRUE AND s.start_time>? AND a.appointment_id IS NULL");
    List<Object> args = new ArrayList<>();
    args.add(now);
    if (provider != null) {
      where.append(" AND sv.provider_id=?");
      args.add(provider);
    }
    if (service != null) {
      where.append(" AND s.service_id=?");
      args.add(service);
    }
    if (date != null) {
      where.append(" AND s.start_time>=? AND s.start_time<?");
      args.add(date.atStartOfDay());
      args.add(date.plusDays(1).atStartOfDay());
    }
    long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM (" + SELECT + where + ") filtered", Long.class, args.toArray());
    args.add(size);
    args.add((long) page * size);
    return new SlotPage(
        jdbc.query(
            SELECT + where + " ORDER BY s.start_time,s.slot_id LIMIT ? OFFSET ?",
            MAPPER,
            args.toArray()),
        page,
        size,
        total);
  }

  public List<SlotDto> forProvider(long user) {
    return jdbc.query(
        SELECT + " WHERE p.user_id=? AND s.active=TRUE ORDER BY s.start_time,s.slot_id",
        MAPPER,
        user);
  }

  public List<ProviderDto> providers() {
    return jdbc.query(
        "SELECT p.provider_id,u.name FROM providers p JOIN users u ON u.user_id=p.user_id ORDER BY"
            + " u.name",
        (rs, n) -> new ProviderDto(rs.getLong(1), rs.getString(2)));
  }

  public List<ServiceDto> services() {
    return jdbc.query(
        "SELECT sv.service_id,sv.provider_id,sv.name,u.name provider_name,sv.duration_minutes FROM"
            + " services sv JOIN providers p ON p.provider_id=sv.provider_id JOIN users u ON"
            + " u.user_id=p.user_id ORDER BY sv.name",
        (rs, n) ->
            new ServiceDto(
                rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getInt(5)));
  }

  public List<ServiceDto> servicesFor(long user) {
    return jdbc.query(
        "SELECT sv.service_id,sv.provider_id,sv.name,u.name,sv.duration_minutes FROM services sv"
            + " JOIN providers p ON p.provider_id=sv.provider_id JOIN users u ON"
            + " u.user_id=p.user_id WHERE p.user_id=? ORDER BY sv.name",
        (rs, n) ->
            new ServiceDto(
                rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getInt(5)),
        user);
  }

  public Optional<SlotDto> find(long id) {
    return jdbc.query(SELECT + " WHERE s.slot_id=? AND s.active=TRUE", MAPPER, id).stream()
        .findFirst();
  }

  public int[] ownedService(long service, long user) {
    return jdbc
        .query(
            "SELECT sv.duration_minutes FROM services sv JOIN providers p ON"
                + " p.provider_id=sv.provider_id WHERE sv.service_id=? AND p.user_id=?",
            (rs, n) -> new int[] {rs.getInt(1)},
            service,
            user)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void lockProvider(long user) {
    jdbc.queryForObject(
        "SELECT provider_id FROM providers WHERE user_id=? FOR UPDATE", Long.class, user);
  }

  public boolean overlaps(long user, LocalDateTime start, LocalDateTime end) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM availability_slots s JOIN services sv ON"
                + " sv.service_id=s.service_id JOIN providers p ON p.provider_id=sv.provider_id"
                + " WHERE p.user_id=? AND s.active=TRUE AND s.start_time<? AND s.end_time>?",
            Long.class,
            user,
            end,
            start)
        > 0;
  }

  public void create(long service, LocalDateTime start, LocalDateTime end) {
    int restored =
        jdbc.update(
            "UPDATE availability_slots SET active=TRUE WHERE service_id=? AND start_time=? AND"
                + " end_time=? AND active=FALSE",
            service,
            start,
            end);
    if (restored == 0)
      jdbc.update(
          "INSERT INTO availability_slots(service_id,start_time,end_time) VALUES(?,?,?)",
          service,
          start,
          end);
  }

  public void deactivate(long slot) {
    jdbc.update("UPDATE availability_slots SET active=FALSE WHERE slot_id=?", slot);
  }
}
