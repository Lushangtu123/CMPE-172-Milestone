package com.example.tutorconnect.observability;

import java.sql.*;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
  private static final Logger log = LoggerFactory.getLogger(HealthController.class);
  private final DataSource source;

  public HealthController(DataSource source) {
    this.source = source;
  }

  @GetMapping("/health")
  public ResponseEntity<Map<String, String>> health() {
    try (var connection = source.getConnection();
        var statement = connection.prepareStatement("SELECT 1")) {
      statement.setQueryTimeout(1);
      try (var result = statement.executeQuery()) {
        if (!result.next() || result.getInt(1) != 1)
          throw new SQLException("Database probe failed");
      }
      return ResponseEntity.ok(Map.of("status", "UP", "database", "UP"));
    } catch (SQLException e) {
      log.error("health_database_down sqlState={}", e.getSQLState());
      return ResponseEntity.status(503).body(Map.of("status", "DOWN", "database", "DOWN"));
    }
  }
}
