package io.github.viniciuscarv.docextract.extract;

import java.util.ArrayList;
import java.util.List;

/** Splits a document into windows of 6 pages overlapping by 1. */
public final class Windows {

  private static final int SIZE = 6;

  private Windows() {}

  /** 1-based inclusive {@code [from, to]} page ranges; empty for 0 pages. */
  public static List<int[]> split(int pageCount) {
    List<int[]> out = new ArrayList<>();
    for (int from = 1; from <= pageCount; from += SIZE - 1) {
      int to = Math.min(from + SIZE - 1, pageCount);
      out.add(new int[] {from, to});
      if (to == pageCount) {
        break;
      }
    }
    return out;
  }
}
