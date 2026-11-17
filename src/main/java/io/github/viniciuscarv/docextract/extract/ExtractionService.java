package io.github.viniciuscarv.docextract.extract;

import io.github.viniciuscarv.docextract.evidence.EvidenceVerifier;
import io.github.viniciuscarv.docextract.model.Category;
import io.github.viniciuscarv.docextract.model.DocumentText;
import io.github.viniciuscarv.docextract.model.Evidence;
import io.github.viniciuscarv.docextract.model.EvidenceStatus;
import io.github.viniciuscarv.docextract.model.PageText;
import io.github.viniciuscarv.docextract.model.VerifiedEvidence;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Extracts all categories window by window, verifies each quote, and merges the windows. */
public class ExtractionService {

  public static final String PROMPT_VERSION = "v1";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Extractor extractor;

  public ExtractionService(Extractor extractor) {
    this.extractor = extractor;
  }

  /** One outcome per category, in enum order. */
  public List<FieldOutcome> extract(DocumentText doc) {
    List<int[]> windows = Windows.split(doc.pages().size());
    List<List<FieldOutcome>> perWindow = new ArrayList<>();
    for (int i = 0; i < windows.size(); i++) {
      perWindow.add(extractWindow(doc, i, windows.get(i)));
    }
    return Arrays.stream(Category.values())
        .map(c -> Merge.pick(c, perWindow.stream().map(w -> w.get(c.ordinal())).toList()))
        .toList();
  }

  private List<FieldOutcome> extractWindow(DocumentText doc, int index, int[] range) {
    List<PageText> pages =
        doc.pages().stream().filter(p -> p.number() >= range[0] && p.number() <= range[1]).toList();
    DocumentText window = new DocumentText(doc.sha256(), pages);
    String raw = extractor.complete(doc.sha256(), index, prompt(pages));
    JsonNode root;
    try {
      root = JSON.readTree(raw);
    } catch (JacksonException e) {
      throw new ExtractionException("Model returned invalid JSON for window " + index, e);
    }
    List<FieldOutcome> out = new ArrayList<>();
    for (Category c : Category.values()) {
      JsonNode field = root.path(c.key());
      JsonNode value = field.path("value");
      List<VerifiedEvidence> evidence = new ArrayList<>();
      for (JsonNode e : field.path("evidence")) {
        evidence.add(
            verify(window, new Evidence(e.path("page").asInt(-1), e.path("quote").asString(""))));
      }
      out.add(new FieldOutcome(c, value.isString() ? value.asString() : null, evidence));
    }
    return out;
  }

  /** Pages outside the window are UNSUPPORTED; the verifier only sees the window's pages. */
  private static VerifiedEvidence verify(DocumentText window, Evidence ev) {
    boolean inWindow = window.pages().stream().anyMatch(p -> p.number() == ev.page());
    return inWindow
        ? EvidenceVerifier.verify(window, ev)
        : new VerifiedEvidence(ev, EvidenceStatus.UNSUPPORTED, -1, -1, -1);
  }

  private static String prompt(List<PageText> pages) {
    StringBuilder sb = new StringBuilder("Extract these fields from the contract pages below.\n");
    for (Category c : Category.values()) {
      sb.append("- ").append(c.key()).append(": ").append(c.description()).append('\n');
    }
    sb.append(
        "For each field give the value as written (null if absent) and evidence: the page number"
            + " and a short verbatim quote from that page. Answer with JSON only.\n\n");
    for (PageText p : pages) {
      sb.append("<page n=\"").append(p.number()).append("\">").append(p.text()).append("</page>\n");
    }
    return sb.toString();
  }
}
