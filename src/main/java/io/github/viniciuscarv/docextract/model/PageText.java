package io.github.viniciuscarv.docextract.model;

import java.util.Objects;

/** Text of one page; {@code number} is 1-based. Empty text means the page has no text layer. */
public record PageText(int number, String text) {

  public PageText {
    Objects.requireNonNull(text, "text");
  }
}
