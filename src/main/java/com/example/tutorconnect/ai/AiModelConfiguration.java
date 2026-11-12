package com.example.tutorconnect.ai;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.*;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

@Configuration
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class AiModelConfiguration {
  @Bean(destroyMethod = "close")
  Client geminiClient(@Value("${spring.ai.google.genai.api-key:}") String key) {
    if (key.isBlank())
      throw new IllegalStateException(
          "AI_ENABLED=true requires GEMINI_API_KEY in the server environment.");
    return Client.builder()
        .apiKey(key)
        .vertexAI(false)
        .httpOptions(HttpOptions.builder().timeout(20000).build())
        .build();
  }

  @Bean
  @ConditionalOnMissingBean
  ChatModel chatModel(Client client, @Value("${ai.chat-model}") String model) {
    return new GeminiPublicAnswerModel(
        GoogleGenAiChatModel.builder()
            .genAiClient(client)
            .options(
                GoogleGenAiChatOptions.builder()
                    .model(model)
                    .temperature(.2)
                    .maxOutputTokens(2048)
                    .includeThoughts(true)
                    .build())
            .retryTemplate(new RetryTemplate(RetryPolicy.builder().maxRetries(1).build()))
            .build());
  }

  @Bean
  @ConditionalOnMissingBean
  EmbeddingModel embeddingModel(Client client, @Value("${ai.embedding-model}") String model) {
    return new GoogleGenAiTextEmbeddingModel(
        GoogleGenAiEmbeddingConnectionDetails.builder().genAiClient(client).build(),
        GoogleGenAiTextEmbeddingOptions.builder().model(model).build(),
        new RetryTemplate(RetryPolicy.builder().maxRetries(1).build()));
  }
}
