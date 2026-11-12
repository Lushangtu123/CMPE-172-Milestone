package com.example.tutorconnect.ai;

import static org.springframework.http.HttpStatus.*;

import com.example.tutorconnect.exception.AppException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class AiKnowledgeService {
  public record Passage(String source, String text) {}

  private final ObjectProvider<EmbeddingModel> models;
  private volatile SimpleVectorStore store;

  public AiKnowledgeService(ObjectProvider<EmbeddingModel> models) {
    this.models = models;
  }

  private synchronized SimpleVectorStore index() {
    if (store != null) return store;
    var model = models.getIfAvailable();
    if (model == null)
      throw new AppException(SERVICE_UNAVAILABLE, "AI knowledge search is not configured.");
    try {
      var text =
          new ClassPathResource("knowledge/tutorconnect-policies.md")
              .getContentAsString(StandardCharsets.UTF_8);
      var documents = new ArrayList<Document>();
      for (var part : text.split("(?m)^## ")) {
        if (part.isBlank()) continue;
        var split = part.split("\\R", 2);
        documents.add(
            new Document(split[0].trim(), split[1].trim(), Map.of("source", split[0].trim())));
      }
      var candidate = SimpleVectorStore.builder(model).build();
      candidate.add(documents);
      store = candidate;
      return store;
    } catch (Exception e) {
      throw new AppException(
          SERVICE_UNAVAILABLE, "Knowledge search is temporarily unavailable. Please try again.");
    }
  }

  public List<Passage> search(String question) {
    if (question == null || question.isBlank() || question.length() > 2000)
      throw new AppException(BAD_REQUEST, "Invalid knowledge question.");
    return index()
        .similaritySearch(
            SearchRequest.builder().query(question).topK(3).similarityThreshold(.35).build())
        .stream()
        .map(d -> new Passage(d.getMetadata().get("source").toString(), d.getText()))
        .toList();
  }
}
