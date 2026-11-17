package io.github.viniciuscarv.docextract.eval;

import io.github.viniciuscarv.docextract.model.Category;
import io.github.viniciuscarv.docextract.model.DocumentText;
import io.github.viniciuscarv.docextract.text.MatchText;
import io.github.viniciuscarv.docextract.text.TextNormalizer;
import java.util.ArrayList;
import java.util.List;

/** Locates CUAD gold span texts in our extracted page text. */
public final class GoldMapper {

  private GoldMapper() {}

  public record Mapped(List<GoldSpan> spans, int unmapped) {}

  /** First occurrence on any page wins; spans not found (or blank) count as unmapped. */
  public static Mapped map(DocumentText doc, List<GoldEntry> entries) {
    List<MatchText> pages =
        doc.pages().stream().map(p -> TextNormalizer.forMatching(p.text())).toList();
    List<GoldSpan> spans = new ArrayList<>();
    int unmapped = 0;
    for (GoldEntry e : entries) {
      Category category = Category.fromCuadLabel(e.category());
      String needle = TextNormalizer.forMatching(e.text()).normalized();
      GoldSpan found = null;
      for (int i = 0; i < pages.size() && !needle.isEmpty() && found == null; i++) {
        int at = pages.get(i).normalized().indexOf(needle);
        if (at >= 0) {
          found =
              new GoldSpan(
                  category,
                  doc.pages().get(i).number(),
                  pages.get(i).originalStart(at),
                  pages.get(i).originalEnd(at + needle.length()));
        }
      }
      if (found == null) {
        unmapped++;
      } else {
        spans.add(found);
      }
    }
    return new Mapped(spans, unmapped);
  }
}
