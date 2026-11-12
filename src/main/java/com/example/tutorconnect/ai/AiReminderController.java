package com.example.tutorconnect.ai;

import com.example.tutorconnect.security.Account;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class AiReminderController {
  private final AiReminderService reminders;

  public AiReminderController(AiReminderService reminders) {
    this.reminders = reminders;
  }

  @PostMapping("/api/provider/ai/reminders/run")
  public AiReminderService.Result run(@AuthenticationPrincipal Account actor) {
    return reminders.run(actor.id());
  }
}
