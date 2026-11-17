package io.github.viniciuscarv.docextract.eval;

import io.github.viniciuscarv.docextract.model.Category;

/** A gold span located in the page text; offsets into the original page text, end exclusive. */
public record GoldSpan(Category category, int page, int start, int end) {}
