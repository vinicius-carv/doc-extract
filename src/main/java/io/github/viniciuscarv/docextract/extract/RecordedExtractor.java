package io.github.viniciuscarv.docextract.extract;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Replays {@code <dir>/<sha>-<window>.json}; with a delegate, records missing ones. */
public class RecordedExtractor implements Extractor {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Path dir;
  private final Extractor delegate;

  public RecordedExtractor(Path dir, Extractor delegateOrNull) {
    this.dir = dir;
    this.delegate = delegateOrNull;
  }

  @Override
  public String complete(String documentSha, int windowIndex, String prompt) {
    Path file = dir.resolve(documentSha + "-" + windowIndex + ".json");
    try {
      if (Files.exists(file)) {
        return Files.readString(file, StandardCharsets.UTF_8);
      }
      if (delegate == null) {
        throw new IllegalStateException("Missing recording: " + file);
      }
      String content = delegate.complete(documentSha, windowIndex, prompt);
      try {
        if (!JSON.readTree(content).isObject()) { // never record garbage
          throw new ExtractionException("Model output is not a JSON object; not recording " + file);
        }
      } catch (JacksonException e) {
        throw new ExtractionException("Model returned invalid JSON; not recording " + file, e);
      }
      Files.createDirectories(dir);
      Files.writeString(file, content, StandardCharsets.UTF_8);
      return content;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
