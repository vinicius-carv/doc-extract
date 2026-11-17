package io.github.viniciuscarv.docextract.text;

import java.util.Arrays;

/** Normalizes text so quotes can be compared regardless of typography. */
public final class TextNormalizer {

  private TextNormalizer() {}

  /**
   * Lowercases, straightens curly quotes, maps dashes/minus to {@code -}, joins {@code -\n}
   * hyphenation before a letter, collapses whitespace runs to one space and trims. Each normalized
   * char maps back to an index in {@code original}.
   */
  public static MatchText forMatching(String original) {
    int n = original.length();
    StringBuilder sb = new StringBuilder(n);
    int[] offsets = new int[n];
    int count = 0;
    boolean pendingSpace = false;
    int spaceIndex = -1;
    int i = 0;
    while (i < n) {
      char c = original.charAt(i);
      if (isSpace(c)) {
        if (!pendingSpace) {
          pendingSpace = true;
          spaceIndex = i;
        }
        i++;
        continue;
      }
      if (c == '-') {
        int next = i + 1;
        if (next < n && original.charAt(next) == '\r') {
          next++;
        }
        if (next < n
            && original.charAt(next) == '\n'
            && next + 1 < n
            && Character.isLetter(original.charAt(next + 1))
            && i > 0
            && !isSpace(original.charAt(i - 1))) {
          i = next + 1; // drop hyphen and line break; next letter handled normally
          continue;
        }
      }
      if (pendingSpace) {
        if (count > 0) {
          sb.append(' ');
          offsets[count++] = spaceIndex;
        }
        pendingSpace = false;
      }
      sb.append(map(c));
      offsets[count++] = i;
      i++;
    }
    return new MatchText(sb.toString(), Arrays.copyOf(offsets, count));
  }

  private static boolean isSpace(char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c);
  }

  private static char map(char c) {
    return switch (c) {
      case '‘', '’', '‚', '‛' -> '\'';
      case '“', '”', '„', '‟' -> '"';
      case '‐', '‑', '‒', '–', '—', '―', '−' -> '-';
      default -> Character.toLowerCase(c);
    };
  }
}
