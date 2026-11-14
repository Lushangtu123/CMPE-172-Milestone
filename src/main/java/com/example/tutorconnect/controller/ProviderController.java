package com.example.tutorconnect.controller;

import com.example.tutorconnect.dto.ServiceDto;
import com.example.tutorconnect.dto.SlotDto;
import com.example.tutorconnect.security.Account;
import com.example.tutorconnect.service.SlotService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/provider")
public class ProviderController {
  private final SlotService slots;

  public ProviderController(SlotService s) {
    slots = s;
  }

  @GetMapping("/slots")
  public List<SlotDto> list(@AuthenticationPrincipal Account a) {
    return slots.forProvider(a.id());
  }

  @GetMapping("/services")
  public List<ServiceDto> services(@AuthenticationPrincipal Account a) {
    return slots.servicesFor(a.id());
  }

  @PostMapping("/slots")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, String> create(
      @AuthenticationPrincipal Account a,
      @RequestParam long serviceId,
      @RequestParam LocalDateTime startTime,
      @RequestParam LocalDateTime endTime) {
    slots.create(a.id(), serviceId, startTime, endTime);
    return Map.of("status", "CREATED");
  }

  @PostMapping("/slots/{id}/remove")
  public Map<String, String> remove(@AuthenticationPrincipal Account a, @PathVariable long id) {
    slots.remove(a.id(), id);
    return Map.of("status", "REMOVED");
  }

  @PostMapping("/slots/{id}/remove-upcoming")
  public Map<String, String> removeUpcoming(
      @AuthenticationPrincipal Account a, @PathVariable long id) {
    slots.removeUpcoming(a.id(), id);
    return Map.of("status", "REMOVED");
  }
}
