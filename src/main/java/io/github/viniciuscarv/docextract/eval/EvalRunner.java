package io.github.viniciuscarv.docextract.eval;

import io.github.viniciuscarv.docextract.extract.ExtractionService;
import io.github.viniciuscarv.docextract.extract.FieldOutcome;
import io.github.viniciuscarv.docextract.model.Category;
import io.github.viniciuscarv.docextract.model.DocumentText;
import io.github.viniciuscarv.docextract.pdf.PdfTextExtractor;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** With {@code --eval}: scores every manifest document, writes results, gates on the baseline. */
@Component
public class EvalRunner implements ApplicationRunner, ExitCodeGenerator {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final ExtractionService service;
  private final Path corpusDir;
  private final Path resultsDir;
  private final String model;
  private int exitCode;

  @Autowired
  public EvalRunner(
      ExtractionService service,
      @Value("${docextract.corpus-dir:corpus}") String corpusDir,
      @Value("${docextract.model:${DOC_EXTRACT_MODEL:qwen3.5:9b}}") String model) {
    this(service, Path.of(corpusDir), Path.of("results"), model);
  }

  EvalRunner(ExtractionService service, Path corpusDir, Path resultsDir, String model) {
    this.service = service;
    this.corpusDir = corpusDir;
    this.resultsDir = resultsDir;
    this.model = model;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (args.containsOption("eval")) {
      exitCode = evaluate(System.out);
    }
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }

  /** Returns 0 on success, 1 if any document failed or overall F1 is below the baseline. */
  int evaluate(PrintStream out) {
    try {
      Manifest manifest =
          JSON.readValue(corpusDir.resolve("manifest.json").toFile(), Manifest.class);
      PdfTextExtractor pdfs = new PdfTextExtractor(Integer.MAX_VALUE);
      List<Map<String, Object>> docs = new ArrayList<>();
      List<List<FieldOutcome>> predicted = new ArrayList<>();
      List<List<GoldSpan>> gold = new ArrayList<>();
      int unmapped = 0;
      boolean failed = false;
      for (Manifest.Doc d : manifest.documents()) {
        try {
          DocumentText doc = pdfs.read(corpusDir.resolve(d.pdf()));
          List<GoldEntry> entries =
              JSON.readValue(
                  corpusDir.resolve(d.gold()).toFile(), new TypeReference<List<GoldEntry>>() {});
          GoldMapper.Mapped mapped = GoldMapper.map(doc, entries);
          List<FieldOutcome> fields = service.extract(doc);
          predicted.add(fields);
          gold.add(mapped.spans());
          unmapped += mapped.unmapped();
          Scorer.Report one = Scorer.score(List.of(fields), List.of(mapped.spans()), mapped.unmapped());
          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("id", d.id());
          entry.put("title", d.title() == null ? d.id() : d.title());
          entry.put("pages", doc.pages());
          entry.put("fields", fields);
          entry.put("precision", one.overallPrecision());
          entry.put("recall", one.overallRecall());
          entry.put("f1", one.overallF1());
          docs.add(entry);
        } catch (RuntimeException e) { // any per-document failure is loud but does not stop the run
          failed = true;
          out.println("FAILED " + d.id() + ": " + e.getMessage());
        }
      }
      Scorer.Report report = Scorer.score(predicted, gold, unmapped);
      Map<String, Object> featured = new LinkedHashMap<>();
      featured.put("promptVersion", ExtractionService.PROMPT_VERSION);
      featured.put("model", model);
      featured.put("documents", docs);
      featured.put("report", report);
      Files.createDirectories(resultsDir);
      JSON.writerWithDefaultPrettyPrinter()
          .writeValue(resultsDir.resolve("featured.json").toFile(), featured);
      out.print(table(report));

      double baseline =
          JSON.readTree(corpusDir.resolve("baseline.json").toFile()).path("overallF1").asDouble(0);
      boolean below = report.overallF1() < baseline;
      if (below) {
        out.printf(
            Locale.ROOT, "Overall F1 %.4f is below baseline %.4f%n", report.overallF1(), baseline);
      }
      return failed || below ? 1 : 0;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String table(Scorer.Report r) {
    StringBuilder sb =
        new StringBuilder("| category | precision | recall | F1 |\n|---|---|---|---|\n");
    for (Category c : Category.values()) {
      Scorer.Prf p = r.perCategory().get(c);
      sb.append(
          String.format(
              Locale.ROOT,
              "| %s | %.2f | %.2f | %.2f |%n",
              c.key(),
              p.precision(),
              p.recall(),
              p.f1()));
    }
    sb.append(String.format(Locale.ROOT, "| overall | | | %.2f |%n%n", r.overallF1()));
    sb.append(
        String.format(
            Locale.ROOT,
            "verified rate: %.2f, unsupported rate: %.2f, unmapped gold spans: %d%n",
            r.verifiedRate(),
            r.unsupportedRate(),
            r.unmapped()));
    return sb.toString();
  }
}
