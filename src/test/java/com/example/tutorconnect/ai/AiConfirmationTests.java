package com.example.tutorconnect.ai;

import static org.assertj.core.api.Assertions.*;

import com.example.tutorconnect.exception.AppException;
import com.example.tutorconnect.security.Account;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Sql(scripts = {"classpath:reset.sql", "classpath:seed.sql"})
class AiConfirmationTests {
  @Autowired AiChatService service;
  @Autowired JdbcTemplate jdbc;
  Account maya = new Account(1, "Maya", "maya.patel@example.com", "", "CUSTOMER");

  @Test
  void expiredTokenCannotWrite() {
    var s = new AiConversation(1);
    s.pending =
        new AiConversation.Pending(
            "expired", "BOOK", 1, 1, "Summary", Instant.now().minusSeconds(1), null);
    assertThatThrownBy(() -> service.confirm(maya, s, "expired")).isInstanceOf(AppException.class);
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id=1", Integer.class))
        .isZero();
  }

  @Test
  void identityCannotBeSwappedEvenWithValidToken() {
    var s = new AiConversation(1);
    s.propose("BOOK", 1, 1, "Summary", null);
    var sam = new Account(2, "Sam", "sam.rivera@example.com", "", "CUSTOMER");
    assertThatThrownBy(() -> service.confirm(sam, s, s.pending.token()))
        .isInstanceOf(AppException.class);
  }

  @Test
  void providerCannotUseWriteServiceThroughAssistant() {
    var s = new AiConversation(3);
    s.propose("BOOK", 1, 1, "Summary", null);
    var provider = new Account(3, "Priya", "priya.shah@example.com", "", "PROVIDER");
    assertThatThrownBy(() -> service.confirm(provider, s, s.pending.token()))
        .isInstanceOf(AppException.class);
  }

  @Test
  void historyIsBoundedAndProposalContainsNoModelToken() {
    var s = new AiConversation(1);
    for (int i = 0; i < 30; i++) s.add("user", "turn " + i);
    assertThat(s.turns).hasSize(20);
    assertThat(s.propose("BOOK", 1, 1, "Summary", null)).doesNotContainKey("token");
  }
}
