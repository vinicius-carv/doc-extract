package io.github.viniciuscarv.docextract.eval;

import java.util.List;

/** corpus/manifest.json; {@code pdf} and {@code gold} are relative to the corpus directory. */
public record Manifest(List<Doc> documents) {

  public record Doc(String id, String pdf, String gold) {}
}
