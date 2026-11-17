package io.github.viniciuscarv.docextract.extract;

import io.github.viniciuscarv.docextract.model.Category;
import io.github.viniciuscarv.docextract.model.EvidenceStatus;
import java.util.List;

/** Picks one outcome per category from the per-window outcomes, in window order. */
public final class Merge {

  private Merge() {}

  /** First verified non-null value; else first non-null; else null with no evidence. */
  public static FieldOutcome pick(Category category, List<FieldOutcome> perWindow) {
    return perWindow.stream()
        .filter(o -> o.value() != null && hasVerified(o))
        .findFirst()
        .or(() -> perWindow.stream().filter(o -> o.value() != null).findFirst())
        .orElseGet(() -> new FieldOutcome(category, null, List.of()));
  }

  private static boolean hasVerified(FieldOutcome o) {
    return o.evidence().stream().anyMatch(e -> e.status() != EvidenceStatus.UNSUPPORTED);
  }
}
