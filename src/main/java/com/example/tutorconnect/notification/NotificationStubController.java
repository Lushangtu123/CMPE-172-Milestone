package com.example.tutorconnect.notification;

import com.example.tutorconnect.exception.AppException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
public class NotificationStubController {
  private static final Logger log = LoggerFactory.getLogger(NotificationStubController.class);
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public NotificationStubController(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @PostMapping("/internal/notifications")
  public ResponseEntity<Map<String, String>> receive(@RequestBody NotificationRequest request) {
    request.validate();
    String hash;
    try {
      hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    try {
      jdbc.update(
          "INSERT INTO notification_receipts(event_id,payload_hash,recipient,type,message)"
              + " VALUES(?,?,?,?,?)",
          request.eventId(),
          hash,
          request.recipient(),
          request.type(),
          request.message());
      log.info("notification_accepted eventId={} type={}", request.eventId(), request.type());
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(Map.of("eventId", request.eventId(), "status", "ACCEPTED"));
    } catch (DuplicateKeyException e) {
      String existing =
          jdbc.queryForObject(
              "SELECT payload_hash FROM notification_receipts WHERE event_id=?",
              String.class,
              request.eventId());
      if (!hash.equals(existing))
        throw new AppException(
            HttpStatus.CONFLICT, "Event ID was already used with a different payload.");
      return ResponseEntity.ok(Map.of("eventId", request.eventId(), "status", "DUPLICATE"));
    }
  }
}
