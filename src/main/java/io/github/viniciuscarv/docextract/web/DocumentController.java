package io.github.viniciuscarv.docextract.web;

import io.github.viniciuscarv.docextract.extract.ExtractionService;
import io.github.viniciuscarv.docextract.extract.OllamaExtractor;
import io.github.viniciuscarv.docextract.model.DocumentText;
import io.github.viniciuscarv.docextract.pdf.PdfTextExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Serves the featured results and live extraction of an uploaded PDF (not persisted). */
@RestController
public class DocumentController {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final int MAX_PAGES = 10;

  private final ExtractionService live; // always Ollama, never the recorded replay
  private final Path resultsFile;

  public DocumentController(
      OllamaExtractor ollama,
      @Value("${docextract.results-file:results/featured.json}") String resultsFile) {
    this.live = new ExtractionService(ollama);
    this.resultsFile = Path.of(resultsFile);
  }

  @GetMapping("/api/documents")
  public List<Map<String, Object>> list() {
    return documents().valueStream()
        .map(
            d -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", d.path("id").asString());
              m.put("title", d.path("title").asString());
              m.put("pages", d.path("pages").size());
              m.put("f1", d.path("f1").asDouble());
              return m;
            })
        .toList();
  }

  @GetMapping("/api/documents/{id}")
  public JsonNode one(@PathVariable String id) {
    return documents()
        .valueStream()
        .filter(d -> id.equals(d.path("id").asString()))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such document"));
  }

  @PostMapping("/api/extract")
  public Map<String, Object> extract(@RequestParam("file") MultipartFile file) throws IOException {
    byte[] bytes = file.getBytes();
    UploadValidator.check(bytes);
    Path tmp = Files.createTempFile("upload-", ".pdf");
    try {
      Files.write(tmp, bytes);
      DocumentText doc = new PdfTextExtractor(MAX_PAGES).read(tmp);
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", "upload");
      m.put("title", file.getOriginalFilename());
      m.put("pages", doc.pages());
      m.put("fields", live.extract(doc));
      return m;
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  private JsonNode documents() {
    if (!Files.exists(resultsFile)) {
      return JSON.createArrayNode();
    }
    return JSON.readTree(resultsFile.toFile()).path("documents");
  }
}
