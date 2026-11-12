package com.example.tutorconnect.service;

import static org.springframework.http.HttpStatus.*;

import com.example.tutorconnect.dto.*;
import com.example.tutorconnect.exception.AppException;
import com.example.tutorconnect.repository.*;
import java.time.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class SlotService {
  private final SlotRepository slots;
  private final AppointmentRepository appointments;

  public SlotService(SlotRepository slots, AppointmentRepository appointments) {
    this.slots = slots;
    this.appointments = appointments;
  }

  public SlotPage available(Long provider, Long service, LocalDate date, int page, int size) {
    if (page < 0
        || size < 1
        || size > 100
        || provider != null && provider <= 0
        || service != null && service <= 0)
      throw new AppException(BAD_REQUEST, "Invalid filter or pagination.");
    return slots.available(provider, service, date, page, size, LocalDateTime.now(ZoneOffset.UTC));
  }

  public List<ProviderDto> providers() {
    return slots.providers();
  }

  public List<ServiceDto> services() {
    return slots.services();
  }

  public List<ServiceDto> servicesFor(long user) {
    return slots.servicesFor(user);
  }

  public List<SlotDto> forProvider(long user) {
    return slots.forProvider(user);
  }

  public SlotDto find(long id) {
    return slots.find(id).orElseThrow(() -> new AppException(NOT_FOUND, "Slot not found."));
  }

  public SlotDto findRemovable(long user, long id) {
    var slot = find(id);
    if (slots.ownedService(slot.serviceId(), user) == null)
      throw new AppException(FORBIDDEN, "You can only remove your own slots.");
    if (!"OPEN".equals(slot.status()))
      throw new AppException(CONFLICT, "A reserved slot cannot be removed.");
    requireFutureRemoval(slot.startTime());
    return slot;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void create(long user, long service, LocalDateTime start, LocalDateTime end) {
    if (service <= 0
        || start == null
        || end == null
        || !start.isAfter(LocalDateTime.now(ZoneOffset.UTC))
        || !end.isAfter(start))
      throw new AppException(BAD_REQUEST, "Choose a future start time and a later end time (UTC).");
    var duration = slots.ownedService(service, user);
    if (duration == null) throw new AppException(FORBIDDEN, "Choose one of your own services.");
    if (Duration.between(start, end).toMinutes() != duration[0]
        || !start.plusMinutes(duration[0]).equals(end))
      throw new AppException(BAD_REQUEST, "Slot length must match the service duration.");
    slots.lockProvider(user);
    if (slots.overlaps(user, start, end))
      throw new AppException(CONFLICT, "This time overlaps another availability slot.");
    slots.create(service, start, end);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void remove(long user, long id) {
    remove(user, id, false);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void removeUpcoming(long user, long id) {
    remove(user, id, true);
  }

  private void remove(long user, long id, boolean upcomingOnly) {
    var slot =
        slots
            .lock(id)
            .filter(SlotRepository.LockedSlot::active)
            .orElseThrow(() -> new AppException(NOT_FOUND, "Slot not found."));
    if (slot.providerUserId() != user)
      throw new AppException(FORBIDDEN, "You can only remove your own slots.");
    if (appointments.occupied(id))
      throw new AppException(CONFLICT, "A reserved slot cannot be removed.");
    if (upcomingOnly) requireFutureRemoval(slot.startTime());
    slots.deactivate(id);
  }

  private void requireFutureRemoval(LocalDateTime startTime) {
    if (!startTime.isAfter(LocalDateTime.now(ZoneOffset.UTC)))
      throw new AppException(
          CONFLICT, "Only future availability can be removed from the dashboard.");
  }
}
