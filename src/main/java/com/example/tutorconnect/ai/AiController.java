package com.example.tutorconnect.ai;

import com.example.tutorconnect.exception.AppException;
import com.example.tutorconnect.security.Account;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AiController {
  private final AiChatService chat;
  private final AiChatHistory historyStore;
  private static final int HISTORY_PAGE_SIZE = 20;

  public AiController(AiChatService chat, AiChatHistory historyStore) {
    this.chat = chat;
    this.historyStore = historyStore;
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
    model.addAttribute("currentPath", "/customer/assistant");
    model.addAttribute("recentChats", historyStore.recent(actor.id(), 5, 0));
    return "assistant";
  }

  @GetMapping("/customer/assistant/history")
  String savedChats(
      @AuthenticationPrincipal Account actor,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    if (page < 0) throw new AppException(HttpStatus.BAD_REQUEST, "Invalid history page.");
    var threads =
        historyStore.recent(actor.id(), HISTORY_PAGE_SIZE + 1, (long) page * HISTORY_PAGE_SIZE);
    model.addAttribute("threads", threads.stream().limit(HISTORY_PAGE_SIZE).toList());
    model.addAttribute("page", page);
    model.addAttribute("hasNext", threads.size() > HISTORY_PAGE_SIZE);
    model.addAttribute("account", actor);
    model.addAttribute("currentPath", "/customer/assistant");
    return "assistant-history";
  }

  @GetMapping("/customer/assistant/history/{threadId}")
  String savedChat(
      @AuthenticationPrincipal Account actor, @PathVariable long threadId, Model model) {
    model.addAttribute("transcript", historyStore.transcript(actor.id(), threadId));
    model.addAttribute("account", actor);
    model.addAttribute("currentPath", "/customer/assistant");
    return "assistant-history";
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

  @GetMapping("/api/customer/ai/conversations")
  @ResponseBody
  Map<String, Object> conversations(
      @AuthenticationPrincipal Account actor, @RequestParam(defaultValue = "0") int page) {
    if (page < 0) throw new AppException(HttpStatus.BAD_REQUEST, "Invalid history page.");
    var threads =
        historyStore.recent(actor.id(), HISTORY_PAGE_SIZE + 1, (long) page * HISTORY_PAGE_SIZE);
    return Map.of(
        "items",
        threads.stream().limit(HISTORY_PAGE_SIZE).toList(),
        "page",
        page,
        "hasNext",
        threads.size() > HISTORY_PAGE_SIZE);
  }

  @GetMapping("/api/customer/ai/conversations/{threadId}")
  @ResponseBody
  AiChatHistory.Transcript conversation(
      @AuthenticationPrincipal Account actor, @PathVariable long threadId) {
    return historyStore.transcript(actor.id(), threadId);
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
      s.reset();
      return s.view();
    }
  }
}
