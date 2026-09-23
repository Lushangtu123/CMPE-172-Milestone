package com.example.tutorconnect.repository;

import com.example.tutorconnect.dto.SlotDto;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SlotRepository {

    private static final String FIND_ALL_SQL = """
            SELECT
                s.slot_id,
                u.name AS provider_name,
                sv.name AS service_name,
                sv.description,
                sv.duration_minutes,
                s.start_time,
                s.end_time,
                CASE WHEN a.appointment_id IS NULL THEN 'OPEN' ELSE 'BOOKED' END AS status
            FROM availability_slots s
            JOIN services sv ON sv.service_id = s.service_id
            JOIN providers p ON p.provider_id = sv.provider_id
            JOIN users u ON u.user_id = p.user_id
            LEFT JOIN appointments a ON a.slot_id = s.slot_id
            ORDER BY s.start_time
            """;

    private static final RowMapper<SlotDto> SLOT_MAPPER = (rs, rowNum) -> new SlotDto(
            rs.getLong("slot_id"),
            rs.getString("provider_name"),
            rs.getString("service_name"),
            rs.getString("description"),
            rs.getInt("duration_minutes"),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getTimestamp("end_time").toLocalDateTime(),
            rs.getString("status"));

    private final JdbcTemplate jdbcTemplate;

    public SlotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SlotDto> findAll() {
        return jdbcTemplate.query(FIND_ALL_SQL, SLOT_MAPPER);
    }
}
