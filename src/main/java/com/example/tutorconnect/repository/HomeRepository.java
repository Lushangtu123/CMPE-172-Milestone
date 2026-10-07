package com.example.tutorconnect.repository;

import com.example.tutorconnect.dto.HomeDto;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HomeRepository {

  private final JdbcTemplate jdbcTemplate;

  public HomeRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public HomeDto findHome() {
    int providerCount = requiredCount("SELECT COUNT(*) FROM providers");
    int serviceCount = requiredCount("SELECT COUNT(*) FROM services");
    int openSlotCount =
        requiredCount(
            """
            SELECT COUNT(*)
            FROM availability_slots s
            LEFT JOIN appointments a ON a.active_slot_id = s.slot_id
            WHERE a.appointment_id IS NULL AND s.active=TRUE AND s.start_time>CURRENT_TIMESTAMP
            """);
    List<String> providers =
        jdbcTemplate.query(
            """
            SELECT u.name
            FROM providers p
            JOIN users u ON u.user_id = p.user_id
            ORDER BY u.name
            """,
            (rs, rowNum) -> rs.getString("name"));

    return new HomeDto(
        "TutorConnect",
        "Home tutoring and coaching appointments",
        providerCount,
        serviceCount,
        openSlotCount,
        providers);
  }

  private int requiredCount(String sql) {
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
    if (count == null) {
      throw new IllegalStateException("Count query returned no row");
    }
    return count;
  }
}
