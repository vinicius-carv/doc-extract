package io.github.viniciuscarv.docextract.extract;

import io.github.viniciuscarv.docextract.model.Category;
import io.github.viniciuscarv.docextract.model.VerifiedEvidence;
import java.util.List;

/** Extracted value (null if none) with its verified evidence. */
public record FieldOutcome(Category category, String value, List<VerifiedEvidence> evidence) {

  public FieldOutcome {
    evidence = List.copyOf(evidence);
  }
}
