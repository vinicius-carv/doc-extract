package io.github.viniciuscarv.docextract.extract;

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
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
    factory.setReadTimeout(Duration.ofSeconds(300));
    return new OllamaExtractor(RestClient.builder().requestFactory(factory), url, model);
  }

  /** Replays corpus/recordings; with docextract.record=true, records misses via Ollama. */
  @Bean
  ExtractionService extractionService(
      OllamaExtractor ollama, @Value("${docextract.record:false}") boolean record) {
    return new ExtractionService(
        new RecordedExtractor(Path.of("corpus/recordings"), record ? ollama : null));
  }
}
