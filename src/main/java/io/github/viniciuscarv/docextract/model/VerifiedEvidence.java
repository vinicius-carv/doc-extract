package io.github.viniciuscarv.docextract.model;

import java.util.Objects;

/**
 * Evidence after checking against the source text. {@code page/start/end} locate where the quote
 * was actually found (offsets into the original page text, end exclusive); {@code -1} when
 * UNSUPPORTED.
 */
public record VerifiedEvidence(
    Evidence claimed, EvidenceStatus status, int page, int start, int end) {

  public VerifiedEvidence {
    Objects.requireNonNull(claimed, "claimed");
    Objects.requireNonNull(status, "status");
  }
}
