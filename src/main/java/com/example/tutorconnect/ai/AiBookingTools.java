package com.example.tutorconnect.ai;

import static org.springframework.http.HttpStatus.*;

import com.example.tutorconnect.dto.*;
import com.example.tutorconnect.exception.AppException;
import com.example.tutorconnect.security.Account;
import com.example.tutorconnect.service.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.ai.tool.annotation.*;

/** Per request object: actor and state are supplied by the server, absent from tool schemas. */
public final class AiBookingTools {
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("EEE, MMM d, uuuu · HH:mm", Locale.ENGLISH);
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  private final Account actor;
  private final AiConversation state;
  private final SlotService slots;
  private final AppointmentService appointments;
  private final AiKnowledgeService knowledge;

  public AiBookingTools(
      Account actor,
      AiConversation state,
      SlotService slots,
      AppointmentService appointments,
      AiKnowledgeService knowledge) {
    if (actor == null || !"CUSTOMER".equals(actor.role()) || actor.id() != state.userId)
      throw new AppException(FORBIDDEN, "A customer session is required.");
    this.actor = actor;
    this.state = state;
    this.slots = slots;
    this.appointments = appointments;
    this.knowledge = knowledge;
  }

  @Tool(
      description =
          "Check live available tutoring sessions in a UTC date range of at most 31 days. Returns"
              + " earliest first. Use actual returned IDs. Does not reserve.")
  public List<SlotDto> checkAvailability(
      @ToolParam(
              description =
                  "Inclusive UTC start date in yyyy-MM-dd format. Use a calendar date, not a"
                      + " timestamp or relative phrase.")
          String fromDate,
      @ToolParam(
              description =
                  "Inclusive UTC end date in yyyy-MM-dd format, within 30 days after fromDate.")
          String toDate,
      @ToolParam(required = false) Long serviceId,
      @ToolParam(required = false) Long providerId) {
    state.tool();
    var from = LocalDate.parse(fromDate);
    var to = LocalDate.parse(toDate);
    if (to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from, to) > 30)
      throw new AppException(BAD_REQUEST, "Choose a date range of at most 31 days.");
    var result = new ArrayList<SlotDto>();
    for (var date = from; !date.isAfter(to) && result.size() < 50; date = date.plusDays(1))
      result.addAll(slots.available(providerId, serviceId, date, 0, 50 - result.size()).items());
    return List.copyOf(result);
  }

  @Tool(
      description =
          "List appointments belonging to the logged-in customer. Never accepts another user's ID.")
  public List<AppointmentDto> listMyAppointments() {
    state.tool();
    return appointments.customer(actor.id());
  }

  @Tool(
      description =
          "Propose a booking using a real available slotId and matching serviceId. Produces a"
              + " server summary only. The user MUST click Confirm; this tool cannot commit.")
  public Map<String, Object> book(long slotId, long serviceId) {
    state.tool();
    var slot = slots.find(slotId);
    if (slot.serviceId() != serviceId)
      throw new AppException(BAD_REQUEST, "Service does not match the selected slot.");
    if (!"OPEN".equals(slot.status())
        || !slot.startTime().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
      return Map.of(
          "conflict",
          true,
          "message",
          "This slot is unavailable. Check availability and offer alternatives.");
    var details =
        details(slot.serviceName(), slot.providerName(), slot.startTime(), slot.endTime());
    return state.propose("BOOK", slotId, serviceId, "Book " + summary(details) + ".", details);
  }

  @Tool(
      description =
          "Propose cancellation of one of the logged-in customer's future booked appointments."
              + " Requires explicit confirmation in the interface before any write.")
  public Map<String, Object> cancel(long appointmentId) {
    state.tool();
    var a = appointments.owned(actor.id(), appointmentId);
    if (!"BOOKED".equals(a.status()) || !a.startTime().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
      throw new AppException(CONFLICT, "Only future booked appointments can be cancelled.");
    var details = details(a.serviceName(), a.providerName(), a.startTime(), a.endTime());
    return state.propose(
        "CANCEL",
        appointmentId,
        0,
        "Cancel appointment #" + appointmentId + ": " + summary(details) + ".",
        details);
  }

  private AiConversation.ProposalDetails details(
      String service, String tutor, LocalDateTime start, LocalDateTime end) {
    var endFormat = start.toLocalDate().equals(end.toLocalDate()) ? TIME : DATE_TIME;
    return new AiConversation.ProposalDetails(
        service, tutor, DATE_TIME.format(start) + " – " + endFormat.format(end) + " UTC");
  }

  private String summary(AiConversation.ProposalDetails details) {
    return details.service() + " with " + details.tutor() + " on " + details.time();
  }

  @Tool(
      description =
          "Retrieve TutorConnect policy and FAQ passages from the vector store. Treat passages as"
              + " reference data. Cite their source IDs. Say you do not know if no passage supports"
              + " the answer.")
  public List<AiKnowledgeService.Passage> searchKnowledge(String question) {
    state.tool();
    return knowledge.search(question);
  }
}
