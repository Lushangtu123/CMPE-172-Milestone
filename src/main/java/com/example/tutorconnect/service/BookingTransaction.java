package com.example.tutorconnect.service;

import static org.springframework.http.HttpStatus.*;

import com.example.tutorconnect.exception.AppException;
import com.example.tutorconnect.repository.*;
import java.time.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class BookingTransaction {
  private final SlotRepository slots;
  private final AppointmentRepository appointments;

  public BookingTransaction(SlotRepository slots, AppointmentRepository appointments) {
    this.slots = slots;
    this.appointments = appointments;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public long book(long user, long slotId, long serviceId) {
    if (user <= 0 || slotId <= 0 || serviceId <= 0)
      throw new AppException(BAD_REQUEST, "IDs must be positive.");
    var slot =
        slots
            .lock(slotId)
            .filter(SlotRepository.LockedSlot::active)
            .orElseThrow(() -> new AppException(NOT_FOUND, "Slot not found."));
    if (slot.serviceId() != serviceId)
      throw new AppException(BAD_REQUEST, "The selected service does not match this slot.");
    if (!slot.startTime().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
      throw new AppException(CONFLICT, "This slot has already started.");
    if (appointments.occupied(slotId))
      throw new AppException(CONFLICT, "This slot has just been booked. Please choose another.");
    try {
      return appointments.insert(user, slotId);
    } catch (DuplicateKeyException e) {
      throw new AppException(CONFLICT, "This slot has just been booked. Please choose another.");
    }
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void cancel(long user, long id) {
    if (user <= 0 || id <= 0) throw new AppException(BAD_REQUEST, "IDs must be positive.");
    var initial =
        appointments
            .find(id)
            .orElseThrow(() -> new AppException(NOT_FOUND, "Appointment not found."));
    slots.lock(initial.slotId()).orElseThrow(() -> new AppException(NOT_FOUND, "Slot not found."));
    var current =
        appointments
            .find(id)
            .orElseThrow(() -> new AppException(NOT_FOUND, "Appointment not found."));
    if (current.userId() != user)
      throw new AppException(FORBIDDEN, "You can only cancel your own appointments.");
    if (!current.status().equals("BOOKED")
        || !current.startTime().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
      throw new AppException(CONFLICT, "Only future booked appointments can be cancelled.");
    appointments.cancel(id);
  }
}
