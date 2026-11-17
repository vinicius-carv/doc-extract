package io.github.viniciuscarv.docextract.extract;

import io.github.viniciuscarv.docextract.model.Category;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Calls Ollama's /api/chat with a JSON schema; connection failures propagate unwrapped. */
public class OllamaExtractor implements Extractor {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final RestClient client;
  private final String model;

  public OllamaExtractor(RestClient.Builder builder, String url, String model) {
    this.client = builder.baseUrl(url).build();
    this.model = model;
  }

  @Override
  public String complete(String documentSha, int windowIndex, String prompt) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("stream", false);
    body.put("think", false);
    body.put("format", schema());
    body.put("options", Map.of("temperature", 0, "num_ctx", 16384));
    body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
    String response =
        client
            .post()
            .uri("/api/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .body(JSON.writeValueAsString(body))
            .retrieve()
            .body(String.class);
    return JSON.readTree(response).path("message").path("content").asString();
  }

  private static Map<String, Object> schema() {
    Map<String, Object> evidenceItem =
        Map.of(
            "type", "object",
            "properties",
                Map.of("page", Map.of("type", "integer"), "quote", Map.of("type", "string")),
            "required", List.of("page", "quote"));
    Map<String, Object> field =
        Map.of(
            "type", "object",
            "properties",
                Map.of(
                    "value", Map.of("type", List.of("string", "null")),
                    "evidence", Map.of("type", "array", "items", evidenceItem)),
            "required", List.of("value", "evidence"));
    Map<String, Object> props = new LinkedHashMap<>();
    Arrays.stream(Category.values()).forEach(c -> props.put(c.key(), field));
    return Map.of(
        "type", "object",
        "properties", props,
        "required", Arrays.stream(Category.values()).map(Category::key).toList());
  }
}
