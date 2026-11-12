package com.example.tutorconnect.ai;

import com.example.tutorconnect.exception.AppException;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;

/** Model context and pending actions for the authenticated HTTP session. */
public final class AiConversation {
  public record Turn(String role, String text) {}

  public record ProposalDetails(String service, String tutor, String time) {}

  public record Pending(
      String token,
      String action,
      long targetId,
      long serviceId,
      String summary,
      Instant expiresAt,
      ProposalDetails details) {}

  final long userId;
  final List<Turn> turns = new ArrayList<>();
  Long threadId;
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

  void reset() {
    begin();
    turns.clear();
    threadId = null;
  }

  Map<String, Object> propose(
      String action, long target, long service, String summary, ProposalDetails details) {
    if (pending != null) return Map.of("confirmationRequired", true, "summary", pending.summary());
    pending =
        new Pending(
            UUID.randomUUID().toString(),
            action,
            target,
            service,
            summary,
            Instant.now().plusSeconds(300),
            details);
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
