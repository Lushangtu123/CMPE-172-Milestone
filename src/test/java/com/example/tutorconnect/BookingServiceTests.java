package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

import com.example.tutorconnect.dto.AppointmentDto;
import com.example.tutorconnect.exception.AppException;
import com.example.tutorconnect.repository.*;
import com.example.tutorconnect.service.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.dao.CannotAcquireLockException;

class BookingServiceTests {
  SlotRepository slots;
  AppointmentRepository appointments;
  BookingTransaction transaction;
  LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC).plusDays(1);

  @BeforeEach
  void setup() {
    slots = mock(SlotRepository.class);
    appointments = mock(AppointmentRepository.class);
    transaction = new BookingTransaction(slots, appointments);
  }

  void slot() {
    when(slots.lock(1))
        .thenReturn(
            Optional.of(new SlotRepository.LockedSlot(1, 1, 3, start, start.plusHours(1), true)));
  }

  void appointment(String status, long owner) {
    when(appointments.find(7))
        .thenReturn(
            Optional.of(
                new AppointmentDto(
                    7, owner, 1, "Maya", "Priya", "Algebra", start, start.plusHours(1), status)));
    slot();
  }

  void status(Runnable operation, org.springframework.http.HttpStatus expected) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            AppException.class, e -> assertThat(e.status()).isEqualTo(expected));
  }

  @Test
  void rejectsNonpositiveBookingIdsBeforeDatabaseAccess() {
    status(() -> transaction.book(1, 0, 1), BAD_REQUEST);
    verifyNoInteractions(slots, appointments);
  }

  @Test
  void rejectsUnknownSlot() {
    when(slots.lock(1)).thenReturn(Optional.empty());
    status(() -> transaction.book(1, 1, 1), NOT_FOUND);
  }

  @Test
  void rejectsMismatchedService() {
    slot();
    status(() -> transaction.book(1, 1, 3), BAD_REQUEST);
    verify(appointments, never()).insert(anyLong(), anyLong());
  }

  @Test
  void rejectsPastSlot() {
    when(slots.lock(1))
        .thenReturn(
            Optional.of(
                new SlotRepository.LockedSlot(
                    1, 1, 3, start.minusDays(2), start.minusDays(2).plusHours(1), true)));
    status(() -> transaction.book(1, 1, 1), CONFLICT);
  }

  @Test
  void rejectsOccupiedSlot() {
    slot();
    when(appointments.occupied(1)).thenReturn(true);
    status(() -> transaction.book(1, 1, 1), CONFLICT);
    verify(appointments, never()).insert(anyLong(), anyLong());
  }

  @Test
  void returnsInsertedAppointmentId() {
    slot();
    when(appointments.insert(1, 1)).thenReturn(7L);
    assertThat(transaction.book(1, 1, 1)).isEqualTo(7);
  }

  @Test
  void cancelRejectsNonpositiveIdsBeforeDatabaseAccess() {
    status(() -> transaction.cancel(1, 0), BAD_REQUEST);
    verifyNoInteractions(slots, appointments);
  }

  @Test
  void onlyOwnerCanCancel() {
    appointment("BOOKED", 1);
    status(() -> transaction.cancel(2, 7), FORBIDDEN);
    verify(appointments, never()).cancel(anyLong());
  }

  @Test
  void cancelledAppointmentCannotBeCancelledAgain() {
    appointment("CANCELLED", 1);
    status(() -> transaction.cancel(1, 7), CONFLICT);
  }

  @Test
  void ownerCanCancelFutureBooking() {
    appointment("BOOKED", 1);
    transaction.cancel(1, 7);
    verify(appointments).cancel(7);
  }

  @Test
  void retriesTransientLockFailureButNotBusinessConflict() {
    var tx = mock(BookingTransaction.class);
    var service = new AppointmentService(tx, appointments);
    when(tx.book(1, 1, 1)).thenThrow(new CannotAcquireLockException("busy")).thenReturn(7L);
    assertThat(service.book(1, 1, 1)).isEqualTo(7);
    verify(tx, times(2)).book(1, 1, 1);
    when(tx.book(2, 1, 1)).thenThrow(new AppException(CONFLICT, "occupied"));
    status(() -> service.book(2, 1, 1), CONFLICT);
    verify(tx, times(1)).book(2, 1, 1);
  }

  @Test
  void exhaustedRetryReturns503() {
    var tx = mock(BookingTransaction.class);
    when(tx.book(1, 1, 1)).thenThrow(new CannotAcquireLockException("busy"));
    status(() -> new AppointmentService(tx, appointments).book(1, 1, 1), SERVICE_UNAVAILABLE);
    verify(tx, times(3)).book(1, 1, 1);
  }

  @Test
  void validatesPaginationBeforeQuery() {
    var service = new SlotService(slots, appointments);
    status(() -> service.available(null, null, null, 0, 101), BAD_REQUEST);
    verifyNoInteractions(slots);
  }

  @Test
  void validatesProviderTimeWindow() {
    var service = new SlotService(slots, appointments);
    status(() -> service.create(3, 1, start, start.minusHours(1)), BAD_REQUEST);
    verifyNoInteractions(slots);
  }

  @Test
  void providerCannotCreateSlotsForSomeoneElse() {
    when(slots.ownedService(3, 3)).thenReturn(null);
    status(
        () -> new SlotService(slots, appointments).create(3, 3, start, start.plusHours(1)),
        FORBIDDEN);
  }

  @Test
  void providerSlotMustMatchServiceDuration() {
    when(slots.ownedService(1, 3)).thenReturn(new int[] {60});
    status(
        () -> new SlotService(slots, appointments).create(3, 1, start, start.plusMinutes(30)),
        BAD_REQUEST);
  }
}
