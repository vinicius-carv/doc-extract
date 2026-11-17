package io.github.viniciuscarv.docextract.eval;

import java.util.List;

/** corpus/manifest.json; {@code pdf} and {@code gold} are relative to the corpus directory; {@code title} is optional (falls back to id). */
public record Manifest(List<Doc> documents) {

  public record Doc(String id, String title, String pdf, String gold) {}
}
