package io.github.viniciuscarv.docextract.pdf;

/** The PDF has more pages than the configured maximum. */
public class PageLimitExceededException extends RuntimeException {

  private final int pages;
  private final int max;

  public PageLimitExceededException(int pages, int max) {
    super("PDF has " + pages + " pages, which exceeds the limit of " + max);
    this.pages = pages;
    this.max = max;
  }

  public int pages() {
    return pages;
  }

  public int max() {
    return max;
  }
}
