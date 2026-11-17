package io.github.viniciuscarv.docextract.eval;

/** One CUAD gold span as stored in a gold file: CUAD label plus span text. */
public record GoldEntry(String category, String text) {}
