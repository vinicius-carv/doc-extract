package io.github.viniciuscarv.docextract.model;

import java.util.List;
import java.util.Objects;

/** Full text of a document, page by page, keyed by the SHA-256 of the source file. */
public record DocumentText(String sha256, List<PageText> pages) {

  public DocumentText {
    Objects.requireNonNull(sha256, "sha256");
    pages = List.copyOf(pages);
  }

  /** Returns the page with the given 1-based number. */
  public PageText page(int number) {
    return pages.stream()
        .filter(p -> p.number() == number)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No such page: " + number));
  }
}
