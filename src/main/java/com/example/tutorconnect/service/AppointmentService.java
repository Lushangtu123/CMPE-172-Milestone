package com.example.tutorconnect.service;

import static org.springframework.http.HttpStatus.*;

import com.example.tutorconnect.dto.AppointmentDto;
import com.example.tutorconnect.exception.AppException;
import com.example.tutorconnect.notification.NotificationDispatcher;
import com.example.tutorconnect.observability.BookingMetrics;
import com.example.tutorconnect.repository.AppointmentRepository;
import java.time.*;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.*;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AppointmentService {
  private final BookingTransaction transactions;
  private final AppointmentRepository appointments;

  private final NotificationDispatcher notifications;
  private final BookingMetrics metrics;
  private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

  public AppointmentService(
      BookingTransaction transactions,
      AppointmentRepository appointments,
      NotificationDispatcher notifications,
      BookingMetrics metrics) {
    this.notifications = notifications;
    this.metrics = metrics;
    this.transactions = transactions;
    this.appointments = appointments;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public long book(long user, long slot, long service) {
    long start = System.nanoTime();
    try {
      long id = retry(() -> transactions.book(user, slot, service));
      metrics.booked();
      log.info("booking_committed appointmentId={} slotId={}", id, slot);
      notifications.afterCommit(id, "BOOKING_CONFIRMED");
      return id;
    } catch (RuntimeException e) {
      metrics.bookingFailed();
      log.warn("booking_failed slotId={} reason={}", slot, e.getClass().getSimpleName());
      throw e;
    } finally {
      metrics.timing(System.nanoTime() - start);
    }
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void cancel(long user, long id) {
    retry(
        () -> {
          transactions.cancel(user, id);
          return null;
        });
    metrics.cancelled();
    log.info("cancellation_committed appointmentId={}", id);
    notifications.afterCommit(id, "CANCELLATION_CONFIRMED");
  }

  // Each call enters the separate Spring transaction proxy: retries never reuse a failed
  // transaction.
  private <T> T retry(Supplier<T> attempt) {
    for (int tries = 0; tries < 3; tries++)
      try {
        return attempt.get();
      } catch (PessimisticLockingFailureException e) {
        if (tries == 2)
          throw new AppException(
              SERVICE_UNAVAILABLE, "The slot is busy. Please try again shortly.");
        try {
          Thread.sleep(25L * (tries + 1));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new AppException(SERVICE_UNAVAILABLE, "Request interrupted. Please try again.");
        }
      }
    throw new IllegalStateException("Unreachable retry state");
  }

  public List<AppointmentDto> customer(long user) {
    appointments.completePast(user, false, LocalDateTime.now(ZoneOffset.UTC));
    return appointments.customer(user);
  }

  public List<AppointmentDto> provider(long user) {
    appointments.completePast(user, true, LocalDateTime.now(ZoneOffset.UTC));
    return appointments.provider(user);
  }

  public AppointmentDto owned(long user, long id) {
    var a =
        appointments
            .find(id)
            .orElseThrow(() -> new AppException(NOT_FOUND, "Appointment not found."));
    if (a.userId() != user)
      throw new AppException(FORBIDDEN, "This appointment belongs to another customer.");
    return a;
  }
}
