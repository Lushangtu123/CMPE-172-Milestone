package com.example.tutorconnect.controller;

import com.example.tutorconnect.security.Account;
import com.example.tutorconnect.service.*;
import java.time.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PageController {
  private final HomeService home;
  private final SlotService slots;
  private final AppointmentService appointments;

  public PageController(HomeService h, SlotService s, AppointmentService a) {
    home = h;
    slots = s;
    appointments = a;
  }

  @ModelAttribute
  void account(@AuthenticationPrincipal Account a, Model model) {
    model.addAttribute("account", a);
  }

  @GetMapping("/")
  String home(Model m) {
    m.addAttribute("home", home.getHome());
    return "home";
  }

  @GetMapping("/login")
  String login() {
    return "login";
  }

  @GetMapping("/slots")
  String slots(
      Model m,
      @RequestParam(required = false) Long providerId,
      @RequestParam(required = false) Long serviceId,
      @RequestParam(required = false) LocalDate date,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "6") int size) {
    m.addAttribute("slots", slots.available(providerId, serviceId, date, page, size));
    m.addAttribute("providers", slots.providers());
    m.addAttribute("services", slots.services());
    m.addAttribute("providerId", providerId);
    m.addAttribute("serviceId", serviceId);
    m.addAttribute("date", date);
    return "slots";
  }

  @GetMapping("/customer/book/{id}")
  String bookForm(@PathVariable long id, Model m) {
    m.addAttribute("slot", slots.find(id));
    return "book";
  }

  @PostMapping("/customer/appointments")
  String book(
      @AuthenticationPrincipal Account a, @RequestParam long slotId, @RequestParam long serviceId) {
    return "redirect:/customer/confirmation/" + appointments.book(a.id(), slotId, serviceId);
  }

  @GetMapping("/customer/confirmation/{id}")
  String confirmation(@AuthenticationPrincipal Account a, @PathVariable long id, Model m) {
    m.addAttribute("appointment", appointments.owned(a.id(), id));
    return "confirmation";
  }

  @GetMapping("/customer/appointments")
  String mine(@AuthenticationPrincipal Account a, Model m) {
    var list = appointments.customer(a.id());
    var now = LocalDateTime.now(ZoneOffset.UTC);
    m.addAttribute(
        "upcoming",
        list.stream()
            .filter(x -> x.status().equals("BOOKED") && x.startTime().isAfter(now))
            .toList());
    m.addAttribute(
        "history",
        list.stream()
            .filter(x -> !x.status().equals("BOOKED") || !x.startTime().isAfter(now))
            .toList());
    return "appointments";
  }

  @PostMapping("/customer/appointments/{id}/cancel")
  String cancel(@AuthenticationPrincipal Account a, @PathVariable long id) {
    appointments.cancel(a.id(), id);
    return "redirect:/customer/appointments?cancelled";
  }

  @GetMapping("/provider/dashboard")
  String provider(@AuthenticationPrincipal Account a, Model m) {
    m.addAttribute("slots", slots.forProvider(a.id()));
    m.addAttribute("services", slots.servicesFor(a.id()));
    m.addAttribute("appointments", appointments.provider(a.id()));
    return "provider";
  }

  @PostMapping("/provider/slots")
  String create(
      @AuthenticationPrincipal Account a,
      @RequestParam long serviceId,
      @RequestParam LocalDateTime startTime,
      @RequestParam LocalDateTime endTime) {
    slots.create(a.id(), serviceId, startTime, endTime);
    return "redirect:/provider/dashboard?created";
  }

  @PostMapping("/provider/slots/{id}/remove")
  String remove(@AuthenticationPrincipal Account a, @PathVariable long id) {
    slots.remove(a.id(), id);
    return "redirect:/provider/dashboard?removed";
  }
}
