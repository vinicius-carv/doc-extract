package io.github.viniciuscarv.docextract.model;

import java.util.Objects;

/** A quote the model claims supports a value, with the page it claims it is on. */
public record Evidence(int page, String quote) {

  public Evidence {
    Objects.requireNonNull(quote, "quote");
  }
}
