package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.tutorconnect.repository.AppointmentRepository;
import com.example.tutorconnect.service.BookingTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Sql(scripts = {"classpath:reset.sql", "classpath:seed.sql"})
class TransactionTests {
  @Autowired BookingTransaction transactions;
  @Autowired JdbcTemplate jdbc;
  @MockitoSpyBean AppointmentRepository appointments;

  @Test
  void unexpectedFailureAfterInsertRollsBackWholeBooking() {
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("forced failure after insert");
            })
        .when(appointments)
        .insert(1, 1);
    assertThatThrownBy(() -> transactions.book(1, 1, 1)).isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id=1", Integer.class))
        .isZero();
  }

  @Test
  void databaseRejectsSecondActiveBookingEvenWithoutServiceLocks() {
    jdbc.update("INSERT INTO appointments(user_id,slot_id,status) VALUES(1,1,'BOOKED')");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO appointments(user_id,slot_id,status) VALUES(2,1,'BOOKED')"))
        .isInstanceOf(DuplicateKeyException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE active_slot_id=1", Integer.class))
        .isEqualTo(1);
  }
}
