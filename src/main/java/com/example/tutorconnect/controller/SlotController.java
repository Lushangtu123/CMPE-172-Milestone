package com.example.tutorconnect.controller;

import com.example.tutorconnect.dto.ProviderDto;
import com.example.tutorconnect.dto.ServiceDto;
import com.example.tutorconnect.dto.SlotDto;
import com.example.tutorconnect.dto.SlotPage;
import com.example.tutorconnect.service.SlotService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class SlotController {
  private final SlotService slots;

  public SlotController(SlotService slots) {
    this.slots = slots;
  }

  public record Catalog(List<ProviderDto> providers, List<ServiceDto> services) {}

  @GetMapping("/api/catalog")
  public Catalog catalog() {
    return new Catalog(slots.providers(), slots.services());
  }

  @GetMapping("/api/slots/{id}")
  public SlotDto slot(@PathVariable long id) {
    return slots.find(id);
  }

  @GetMapping("/api/slots")
  public SlotPage slots(
      @RequestParam(required = false) Long providerId,
      @RequestParam(required = false) Long serviceId,
      @RequestParam(required = false) LocalDate date,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "6") int size) {
    return slots.available(providerId, serviceId, date, page, size);
  }
}
