package com.example.tutorconnect.controller;

import com.example.tutorconnect.dto.AppointmentDto;
import com.example.tutorconnect.security.Account;
import com.example.tutorconnect.service.AppointmentService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class AppointmentController {
  private final AppointmentService appointments;

  public AppointmentController(AppointmentService a) {
    appointments = a;
  }

  @GetMapping("/api/customer/appointments")
  public List<AppointmentDto> mine(@AuthenticationPrincipal Account a) {
    return appointments.customer(a.id());
  }

  @GetMapping("/api/customer/appointments/{id}")
  public AppointmentDto owned(@AuthenticationPrincipal Account a, @PathVariable long id) {
    return appointments.owned(a.id(), id);
  }

  @GetMapping("/api/provider/appointments")
  public List<AppointmentDto> provider(@AuthenticationPrincipal Account a) {
    return appointments.provider(a.id());
  }

  @PostMapping("/api/customer/appointments")
  public ResponseEntity<Map<String, Long>> book(
      @AuthenticationPrincipal Account a, @RequestParam long slotId, @RequestParam long serviceId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("appointmentId", appointments.book(a.id(), slotId, serviceId)));
  }

  @PostMapping("/api/customer/appointments/{id}/cancel")
  public Map<String, String> cancel(@AuthenticationPrincipal Account a, @PathVariable long id) {
    appointments.cancel(a.id(), id);
    return Map.of("status", "CANCELLED");
  }
}
