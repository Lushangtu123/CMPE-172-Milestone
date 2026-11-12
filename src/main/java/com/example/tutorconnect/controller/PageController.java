package com.example.tutorconnect.controller;

import static org.springframework.http.HttpStatus.*;

import com.example.tutorconnect.dto.AppointmentDto;
import com.example.tutorconnect.exception.AppException;
import com.example.tutorconnect.security.Account;
import com.example.tutorconnect.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.*;
import java.util.LinkedHashMap;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class PageController {
  public static class AvailabilityForm {
    private Long serviceId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;

    public Long getServiceId() {
      return serviceId;
    }

    public void setServiceId(Long serviceId) {
      this.serviceId = serviceId;
    }

    public LocalDateTime getStartTime() {
      return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
      this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
      return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
      this.endTime = endTime;
    }
  }

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

  @ModelAttribute("currentPath")
  String currentPath(HttpServletRequest request) {
    return request.getServletPath();
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
    var results = slots.available(providerId, serviceId, date, page, size);
    boolean filtersApplied = providerId != null || serviceId != null || date != null;
    boolean noUpcomingSessions =
        results.total() == 0
            && (!filtersApplied || slots.available(null, null, null, 0, 1).total() == 0);
    m.addAttribute("slots", results);
    m.addAttribute("providers", slots.providers());
    m.addAttribute("services", slots.services());
    m.addAttribute("providerId", providerId);
    m.addAttribute("serviceId", serviceId);
    m.addAttribute("date", date);
    m.addAttribute("filtersApplied", filtersApplied);
    m.addAttribute("noUpcomingSessions", noUpcomingSessions);
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
    m.addAttribute("upcoming", list.stream().filter(x -> isUpcoming(x, now)).toList());
    m.addAttribute("history", list.stream().filter(x -> !isUpcoming(x, now)).toList());
    return "appointments";
  }

  @GetMapping("/customer/appointments/{id}/cancel")
  String reviewCancellation(@AuthenticationPrincipal Account a, @PathVariable long id, Model m) {
    var appointment = appointments.owned(a.id(), id);
    if (!isUpcoming(appointment, LocalDateTime.now(ZoneOffset.UTC)))
      throw new AppException(CONFLICT, "Only future booked appointments can be cancelled.");
    m.addAttribute("appointment", appointment);
    return "confirm-action";
  }

  @PostMapping("/customer/appointments/{id}/cancel")
  String cancel(@AuthenticationPrincipal Account a, @PathVariable long id) {
    appointments.cancel(a.id(), id);
    return "redirect:/customer/appointments?cancelled";
  }

  @GetMapping("/provider/dashboard")
  String provider(@AuthenticationPrincipal Account a, Model m) {
    m.addAttribute("availabilityForm", new AvailabilityForm());
    return providerPage(a, m);
  }

  private String providerPage(Account a, Model m) {
    m.addAttribute("currentPath", "/provider/dashboard");
    var availability = slots.forProvider(a.id());
    var sessions = appointments.provider(a.id());
    var now = LocalDateTime.now(ZoneOffset.UTC);
    m.addAttribute(
        "futureSlots", availability.stream().filter(s -> s.startTime().isAfter(now)).toList());
    m.addAttribute(
        "pastSlots", availability.stream().filter(s -> !s.startTime().isAfter(now)).toList());
    m.addAttribute("services", slots.servicesFor(a.id()));
    m.addAttribute("upcoming", sessions.stream().filter(s -> isUpcoming(s, now)).toList());
    m.addAttribute("history", sessions.stream().filter(s -> !isUpcoming(s, now)).toList());
    return "provider";
  }

  private boolean isUpcoming(AppointmentDto appointment, LocalDateTime now) {
    return appointment.status().equals("BOOKED") && appointment.startTime().isAfter(now);
  }

  @PostMapping("/provider/slots")
  String create(
      @AuthenticationPrincipal Account a,
      @ModelAttribute("availabilityForm") AvailabilityForm form,
      BindingResult errors,
      Model model,
      HttpServletResponse response) {
    if (!errors.hasFieldErrors("serviceId")
        && (form.getServiceId() == null || form.getServiceId() <= 0))
      errors.rejectValue("serviceId", "required", "Choose a service.");
    if (!errors.hasFieldErrors("startTime")
        && (form.getStartTime() == null
            || !form.getStartTime().isAfter(LocalDateTime.now(ZoneOffset.UTC))))
      errors.rejectValue("startTime", "future", "Choose a future start time in UTC.");
    if (!errors.hasFieldErrors("endTime")
        && (form.getEndTime() == null
            || form.getStartTime() != null && !form.getEndTime().isAfter(form.getStartTime())))
      errors.rejectValue("endTime", "range", "Choose an end time after the start time.");
    if (errors.hasErrors()) {
      return availabilityError(a, errors, model, response, BAD_REQUEST.value());
    }
    try {
      slots.create(a.id(), form.getServiceId(), form.getStartTime(), form.getEndTime());
      return "redirect:/provider/dashboard?created";
    } catch (AppException e) {
      String field =
          switch (e.status()) {
            case FORBIDDEN -> "serviceId";
            case CONFLICT -> "startTime";
            case BAD_REQUEST ->
                form.getStartTime().isAfter(LocalDateTime.now(ZoneOffset.UTC))
                    ? "endTime"
                    : "startTime";
            default -> throw e;
          };
      errors.rejectValue(field, "invalid", e.getMessage());
      return availabilityError(a, errors, model, response, e.status().value());
    }
  }

  private String availabilityError(
      Account account,
      BindingResult errors,
      Model model,
      HttpServletResponse response,
      int status) {
    var messages = new LinkedHashMap<String, String>();
    for (var error : errors.getFieldErrors()) {
      String message =
          error.isBindingFailure()
              ? switch (error.getField()) {
                case "serviceId" -> "Choose a service.";
                case "startTime" -> "Enter a valid start time.";
                default -> "Enter a valid end time.";
              }
              : error.getDefaultMessage();
      messages.putIfAbsent(error.getField(), message);
    }
    model.addAttribute("availabilityErrors", messages);
    response.setStatus(status);
    return providerPage(account, model);
  }

  @GetMapping("/provider/slots/{id}/remove")
  String reviewRemoval(@AuthenticationPrincipal Account a, @PathVariable long id, Model m) {
    m.addAttribute("slot", slots.findRemovable(a.id(), id));
    return "confirm-action";
  }

  @PostMapping("/provider/slots/{id}/remove")
  String remove(@AuthenticationPrincipal Account a, @PathVariable long id) {
    slots.removeUpcoming(a.id(), id);
    return "redirect:/provider/dashboard?removed";
  }
}
