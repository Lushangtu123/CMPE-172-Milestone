package com.example.tutorconnect.ai;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.example.tutorconnect.ai.AiConversation.Turn;
import com.example.tutorconnect.exception.AppException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class AiChatHistory {
  record ThreadSummary(long id, String title, LocalDateTime updatedAt) {}

  record Transcript(ThreadSummary thread, List<Turn> messages) {}

  private static final RowMapper<ThreadSummary> SUMMARY =
      (rs, row) ->
          new ThreadSummary(
              rs.getLong("thread_id"),
              rs.getString("title"),
              rs.getObject("updated_at", LocalDateTime.class));

  private final JdbcTemplate jdbc;

  AiChatHistory(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(timeout = 3)
  public long append(long userId, Long threadId, List<Turn> turns) {
    long id = threadId == null ? createThread(userId, turns.get(0).text()) : threadId;
    if (threadId != null
        && jdbc.update(
                "UPDATE ai_chat_threads SET updated_at=CURRENT_TIMESTAMP(6) WHERE user_id=? AND"
                    + " thread_id=?",
                userId,
                id)
            != 1) throw new DataRetrievalFailureException("Chat thread is unavailable.");
    for (Turn turn : turns)
      jdbc.update(
          "INSERT INTO ai_chat_messages (thread_id, role, body) VALUES (?,?,?)",
          id,
          turn.role(),
          turn.text());
    return id;
  }

  List<ThreadSummary> recent(long userId, int limit, long offset) {
    return jdbc.query(
        """
        SELECT thread_id, title, updated_at FROM ai_chat_threads WHERE user_id=?
        ORDER BY updated_at DESC, thread_id DESC LIMIT ? OFFSET ?
        """,
        SUMMARY,
        userId,
        limit,
        offset);
  }

  Transcript transcript(long userId, long threadId) {
    var thread =
        jdbc
            .query(
                """
                SELECT thread_id, title, updated_at FROM ai_chat_threads WHERE user_id=? AND thread_id=?
                """,
                SUMMARY,
                userId,
                threadId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new AppException(NOT_FOUND, "Conversation not found."));
    var messages =
        jdbc.query(
            """
            SELECT m.role, m.body FROM ai_chat_messages m JOIN ai_chat_threads t ON t.thread_id=m.thread_id
            WHERE t.user_id=? AND t.thread_id=? ORDER BY m.message_id
            """,
            (rs, row) -> new Turn(rs.getString("role"), rs.getString("body")),
            userId,
            threadId);
    return new Transcript(thread, messages);
  }

  private long createThread(long userId, String firstMessage) {
    String title = firstMessage.replaceAll("(?U)\\s+", " ").strip();
    if (title.codePointCount(0, title.length()) > 100)
      title = title.substring(0, title.offsetByCodePoints(0, 100)) + "…";
    String threadTitle = title;
    var keys = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  "INSERT INTO ai_chat_threads (user_id, title) VALUES (?,?)",
                  new String[] {"thread_id"});
          statement.setLong(1, userId);
          statement.setString(2, threadTitle);
          return statement;
        },
        keys);
    var key = keys.getKey();
    if (key == null) throw new DataRetrievalFailureException("Chat thread ID was not generated.");
    return key.longValue();
  }
}
