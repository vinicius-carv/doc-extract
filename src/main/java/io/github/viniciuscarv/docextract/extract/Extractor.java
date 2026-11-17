package io.github.viniciuscarv.docextract.extract;

/** Produces the raw JSON content string the model returns for one window of a document. */
public interface Extractor {
  String complete(String documentSha, int windowIndex, String prompt);
}
