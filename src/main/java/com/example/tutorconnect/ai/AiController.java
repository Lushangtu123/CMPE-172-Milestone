package com.example.tutorconnect.ai;

import com.example.tutorconnect.security.Account;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AiController {
  private final AiChatService chat;

  public AiController(AiChatService chat) {
    this.chat = chat;
  }

  private AiConversation state(HttpSession session, Account actor) {
    synchronized (session) {
      var s = (AiConversation) session.getAttribute("aiConversation");
      if (s == null || s.userId != actor.id()) {
        s = new AiConversation(actor.id());
        session.setAttribute("aiConversation", s);
      }
      return s;
    }
  }

  @GetMapping("/customer/assistant")
  String page(@AuthenticationPrincipal Account actor, Model model) {
    model.addAttribute("account", actor);
    return "assistant";
  }

  @PostMapping("/api/customer/ai/chat")
  @ResponseBody
  Map<String, Object> chat(
      @AuthenticationPrincipal Account actor, HttpSession session, @RequestParam String message) {
    var s = state(session, actor);
    synchronized (s) {
      return chat.chat(actor, s, message);
    }
  }

  @GetMapping("/api/customer/ai/history")
  @ResponseBody
  Map<String, Object> history(@AuthenticationPrincipal Account actor, HttpSession session) {
    var s = state(session, actor);
    synchronized (s) {
      return s.view();
    }
  }

  @PostMapping("/api/customer/ai/confirm")
  @ResponseBody
  Map<String, Object> confirm(
      @AuthenticationPrincipal Account actor, HttpSession session, @RequestParam String token) {
    var s = state(session, actor);
    synchronized (s) {
      return chat.confirm(actor, s, token);
    }
  }

  @PostMapping("/api/customer/ai/discard")
  @ResponseBody
  Map<String, Object> discard(@AuthenticationPrincipal Account actor, HttpSession session) {
    var s = state(session, actor);
    synchronized (s) {
      s.pending = null;
      return s.view();
    }
  }

  @PostMapping("/api/customer/ai/reset")
  @ResponseBody
  Map<String, Object> reset(@AuthenticationPrincipal Account actor, HttpSession session) {
    var s = state(session, actor);
    synchronized (s) {
      s.pending = null;
      s.turns.clear();
      return s.view();
    }
  }
}
