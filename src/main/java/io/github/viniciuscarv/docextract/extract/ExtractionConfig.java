package io.github.viniciuscarv.docextract.extract;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class ExtractionConfig {

  @Bean
  OllamaExtractor ollamaExtractor(
      @Value("${docextract.ollama-url:${OLLAMA_URL:http://localhost:11434}}") String url,
      @Value("${docextract.model:${DOC_EXTRACT_MODEL:qwen3.5:9b}}") String model) {
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(300));
    return new OllamaExtractor(RestClient.builder().requestFactory(factory), url, model);
  }

  /**
   * Replays {@code <corpus-dir>/recordings}; with docextract.record=true, calls Ollama only for
   * windows that have no recording yet. Delete the recordings to re-record after changing the
   * prompt or model.
   */
  @Bean
  ExtractionService extractionService(
      OllamaExtractor ollama,
      @Value("${docextract.record:false}") boolean record,
      @Value("${docextract.corpus-dir:corpus}") String corpusDir) {
    return new ExtractionService(
        new RecordedExtractor(Path.of(corpusDir, "recordings"), record ? ollama : null));
  }
}
