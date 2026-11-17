package io.github.viniciuscarv.docextract.text;

/**
 * Text normalized for matching, with a map back to the original string. {@code originalOffsets[i]}
 * is the index in the original string of normalized char {@code i}.
 */
public record MatchText(String normalized, int[] originalOffsets) {

  /** Original index of the normalized char at {@code i}. */
  public int originalStart(int i) {
    return originalOffsets[i];
  }

  /** Original index just past the normalized char before {@code iExclusive}. */
  public int originalEnd(int iExclusive) {
    return originalOffsets[iExclusive - 1] + 1;
  }
}
