package com.example.tutorconnect.ai;

import com.example.tutorconnect.exception.AppException;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;

/** Stored only in the authenticated HTTP session. No client-supplied history or identity. */
public final class AiConversation {
  public record Turn(String role, String text) {}

  public record Pending(
      String token,
      String action,
      long targetId,
      long serviceId,
      String summary,
      Instant expiresAt) {}

  final long userId;
  final List<Turn> turns = new ArrayList<>();
  Pending pending;
  int toolCalls;

  public AiConversation(long userId) {
    this.userId = userId;
  }

  void begin() {
    pending = null;
    toolCalls = 0;
  }

  void tool() {
    if (++toolCalls > 10)
      throw new AppException(HttpStatus.BAD_REQUEST, "Please narrow your request to one action.");
  }

  void add(String role, String text) {
    turns.add(new Turn(role, text));
    while (turns.size() > 20) turns.remove(0);
  }

  Map<String, Object> propose(String action, long target, long service, String summary) {
    if (pending != null) return Map.of("confirmationRequired", true, "summary", pending.summary());
    pending =
        new Pending(
            UUID.randomUUID().toString(),
            action,
            target,
            service,
            summary,
            Instant.now().plusSeconds(300));
    // Confirmation token is returned only to the browser, never to the model.
    return Map.of("confirmationRequired", true, "summary", summary);
  }

  public Map<String, Object> view() {
    var r = new LinkedHashMap<String, Object>();
    r.put("messages", List.copyOf(turns));
    r.put(
        "pending", pending != null && pending.expiresAt().isAfter(Instant.now()) ? pending : null);
    return r;
  }
}
