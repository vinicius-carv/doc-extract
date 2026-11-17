package io.github.viniciuscarv.docextract.eval;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.viniciuscarv.docextract.corpus.Sha256;
import io.github.viniciuscarv.docextract.extract.ExtractionService;
import io.github.viniciuscarv.docextract.extract.RecordedExtractor;
import io.github.viniciuscarv.docextract.testutil.TestPdfs;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EvalRunnerTest {

  @TempDir Path dir;
  Path corpus;
  Path results;
  String sha;

  @BeforeEach
  void setUp() throws IOException {
    corpus = dir.resolve("corpus");
    results = dir.resolve("results");
    Files.createDirectories(corpus.resolve("cuad"));
    Path pdf =
        TestPdfs.textPdf(List.of("Alpha Corp agrees.", "Governed by the laws of Delaware."));
    Files.copy(pdf, corpus.resolve("cuad/d1.pdf"));
    sha = Sha256.of(pdf);
    Files.createDirectories(corpus.resolve("recordings"));
    Files.writeString(
        corpus.resolve("recordings/" + sha + "-0.json"),
        "{\"governing_law\":{\"value\":\"Delaware\",\"evidence\":"
            + "[{\"page\":2,\"quote\":\"laws of Delaware\"},{\"page\":9,\"quote\":\"x\"}]}}");
    Files.writeString(
        corpus.resolve("manifest.json"),
        "{\"documents\":[{\"id\":\"d1\",\"pdf\":\"cuad/d1.pdf\",\"gold\":\"cuad/d1.gold.json\"}]}");
    Files.writeString(
        corpus.resolve("cuad/d1.gold.json"),
        "[{\"category\":\"Governing Law\",\"text\":\"laws of Delaware\"},"
            + "{\"category\":\"Parties\",\"text\":\"not in the document\"}]");
  }

  private String evaluate(double baseline, int[] code) throws IOException {
    Files.writeString(corpus.resolve("baseline.json"), "{\"overallF1\":" + baseline + "}");
    EvalRunner runner =
        new EvalRunner(
            new ExtractionService(new RecordedExtractor(corpus.resolve("recordings"), null)),
            corpus,
            results,
            "test-model");
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    code[0] = runner.evaluate(new PrintStream(buf, true, StandardCharsets.UTF_8));
    return buf.toString(StandardCharsets.UTF_8);
  }

  @Test
  void printsTableWritesFeaturedAndPassesAtBaseline() throws IOException {
    int[] code = new int[1];
    String out = evaluate(0.5, code);

    assertThat(code[0]).isZero();
    assertThat(out).contains("| governing_law | 1.00 | 1.00 | 1.00 |");
    assertThat(out).contains("| overall |");
    assertThat(out).contains("unmapped gold spans: 1");
    assertThat(out).contains("verified rate: 0.50").contains("unsupported rate: 0.50");

    JsonNode f = new JsonMapper().readTree(results.resolve("featured.json").toFile());
    assertThat(f.path("promptVersion").asString()).isEqualTo(ExtractionService.PROMPT_VERSION);
    assertThat(f.path("model").asString()).isEqualTo("test-model");
    JsonNode doc = f.path("documents").get(0);
    assertThat(doc.path("id").asString()).isEqualTo("d1");
    assertThat(doc.path("fields")).hasSize(6);
    assertThat(f.path("report").path("unmapped").asInt()).isEqualTo(1);
    assertThat(f.path("report").path("overallF1").asDouble()).isEqualTo(1.0);
  }

  @Test
  void f1BelowBaselineFails() throws IOException {
    int[] code = new int[1];
    String out = evaluate(1.5, code);
    assertThat(code[0]).isEqualTo(1);
    assertThat(out).contains("below baseline");
  }

  @Test
  void documentWithInvalidModelOutputFailsLoudly() throws IOException {
    Files.writeString(corpus.resolve("recordings/" + sha + "-0.json"), "not json");
    int[] code = new int[1];
    String out = evaluate(0.0, code);
    assertThat(code[0]).isEqualTo(1);
    assertThat(out).contains("FAILED d1").contains("invalid JSON");
    JsonNode f = new JsonMapper().readTree(results.resolve("featured.json").toFile());
    assertThat(f.path("documents")).isEmpty();
  }
}
