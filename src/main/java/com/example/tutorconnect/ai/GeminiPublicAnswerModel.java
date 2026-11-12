package com.example.tutorconnect.ai;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.*;

/** Spring AI 2.0.2 emits each Gemini text part as a generation, including thought summaries. */
final class GeminiPublicAnswerModel implements ChatModel {
  private final ChatModel delegate;

  GeminiPublicAnswerModel(ChatModel delegate) {
    this.delegate = delegate;
  }

  @Override
  public ChatOptions getOptions() {
    return delegate.getOptions();
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    var response = delegate.call(prompt);
    var visible =
        response.getResults().stream()
            .filter(g -> !Boolean.TRUE.equals(g.getOutput().getMetadata().get("isThought")))
            .toList();
    // Retain the provider's tool message and thought-signature metadata for the next round.
    var calls = visible.stream().filter(g -> g.getOutput().hasToolCalls()).toList();
    if (!calls.isEmpty()) return new ChatResponse(calls, response.getMetadata());
    if (visible.isEmpty()) throw new IllegalStateException("The model supplied no public answer.");
    String text =
        visible.stream()
            .map(g -> g.getOutput().getText())
            .filter(Objects::nonNull)
            .collect(Collectors.joining("\n"));
    var message =
        AssistantMessage.builder()
            .content(text)
            .properties(visible.get(0).getOutput().getMetadata())
            .build();
    return new ChatResponse(
        List.of(new Generation(message, visible.get(0).getMetadata())), response.getMetadata());
  }
}
