package io.github.viniciuscarv.docextract.evidence;

import io.github.viniciuscarv.docextract.model.DocumentText;
import io.github.viniciuscarv.docextract.model.Evidence;
import io.github.viniciuscarv.docextract.model.EvidenceStatus;
import io.github.viniciuscarv.docextract.model.PageText;
import io.github.viniciuscarv.docextract.model.VerifiedEvidence;
import io.github.viniciuscarv.docextract.text.MatchText;
import io.github.viniciuscarv.docextract.text.TextNormalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Checks a claimed quote against page text. Search order: exact (normalized) on the claimed page,
 * exact on other pages, fuzzy on the claimed page, fuzzy on other pages. A match on a page other
 * than the claimed one is reported as WRONG_PAGE (exact or fuzzy).
 */
public final class EvidenceVerifier {

  /** Minimum similarity (1 - levenshtein / max length) for a fuzzy match. */
  public static final double FUZZY_MIN_SIMILARITY = 0.90;

  /** Quotes shorter than this many normalized chars are matched exactly only. */
  public static final int FUZZY_MIN_QUOTE_CHARS = 8;

  /** Quotes longer than this many normalized chars are matched exactly only (bounds the cost). */
  public static final int FUZZY_MAX_QUOTE_CHARS = 400;

  /** Fuzzy window length tolerance around the quote length. */
  private static final double WINDOW_TOLERANCE = 0.10;

  private EvidenceVerifier() {}

  public static VerifiedEvidence verify(DocumentText doc, Evidence ev) {
    String q = TextNormalizer.forMatching(ev.quote()).normalized();
    if (q.isEmpty()) {
      return unsupported(ev);
    }
    List<Candidate> claimed = new ArrayList<>();
    List<Candidate> others = new ArrayList<>();
    for (PageText p : doc.pages()) {
      Candidate c = new Candidate(p, TextNormalizer.forMatching(p.text()));
      (p.number() == ev.page() ? claimed : others).add(c);
    }
    others.sort(Comparator.comparingInt(c -> c.page.number()));

    VerifiedEvidence hit = exact(ev, q, claimed, EvidenceStatus.VERIFIED);
    if (hit == null) {
      hit = exact(ev, q, others, EvidenceStatus.WRONG_PAGE);
    }
    boolean fuzzyAllowed =
        q.length() >= FUZZY_MIN_QUOTE_CHARS && q.length() <= FUZZY_MAX_QUOTE_CHARS;
    if (hit == null && fuzzyAllowed) {
      hit = fuzzy(ev, q, claimed, EvidenceStatus.FUZZY);
    }
    if (hit == null && fuzzyAllowed) {
      hit = fuzzy(ev, q, others, EvidenceStatus.WRONG_PAGE);
    }
    return hit != null ? hit : unsupported(ev);
  }

  private static VerifiedEvidence unsupported(Evidence ev) {
    return new VerifiedEvidence(ev, EvidenceStatus.UNSUPPORTED, -1, -1, -1);
  }

  private static VerifiedEvidence exact(
      Evidence ev, String q, List<Candidate> pages, EvidenceStatus status) {
    for (Candidate c : pages) {
      int idx = c.text.normalized().indexOf(q);
      if (idx >= 0) {
        return located(ev, status, c, idx, idx + q.length());
      }
    }
    return null;
  }

  private static VerifiedEvidence fuzzy(
      Evidence ev, String q, List<Candidate> pages, EvidenceStatus status) {
    VerifiedEvidence best = null;
    int bestDist = Integer.MAX_VALUE;
    for (Candidate c : pages) {
      int[] w = bestWindow(c.text.normalized(), q, bestDist);
      if (w != null) {
        bestDist = w[2];
        best = located(ev, status, c, w[0], w[1]);
      }
    }
    return best;
  }

  private static VerifiedEvidence located(
      Evidence ev, EvidenceStatus status, Candidate c, int from, int to) {
    return new VerifiedEvidence(
        ev, status, c.page.number(), c.text.originalStart(from), c.text.originalEnd(to));
  }

  /**
   * Best window [start, end, distance] in {@code text} reaching the similarity threshold with a
   * distance below {@code distanceCeiling}, or null. For each word-boundary start one banded DP
   * aligns the quote against the following text; the last row yields the distance to every end.
   */
  private static int[] bestWindow(String text, String q, int distanceCeiling) {
    int len = q.length();
    int maxWin = (int) Math.ceil(len * (1 + WINDOW_TOLERANCE));
    int minWin = (int) Math.floor(len * (1 - WINDOW_TOLERANCE));
    int limit = (int) Math.floor((1 - FUZZY_MIN_SIMILARITY) * maxWin);
    int[] prev = new int[maxWin + 2];
    int[] cur = new int[maxWin + 2];
    int[] best = null;
    int bestDist = distanceCeiling;
    for (int start = 0; start < text.length(); start++) {
      if (start > 0 && text.charAt(start - 1) != ' ') {
        continue; // windows start at word boundaries
      }
      int cols = Math.min(maxWin, text.length() - start);
      if (cols < minWin) {
        break;
      }
      int[] row = bandedRow(q, text, start, cols, limit, prev, cur);
      if (row == null) {
        continue;
      }
      for (int j = Math.max(minWin, len - limit); j <= Math.min(cols, len + limit); j++) {
        int end = start + j;
        if (end < text.length() && text.charAt(end) != ' ') {
          continue; // windows end at word boundaries
        }
        int d = row[j];
        if (d < bestDist && d <= Math.floor((1 - FUZZY_MIN_SIMILARITY) * Math.max(len, j))) {
          bestDist = d;
          best = new int[] {start, end, d};
        }
      }
    }
    return best;
  }

  private static final int INF = Integer.MAX_VALUE / 4;

  /**
   * Banded Levenshtein DP of {@code a} against {@code b[from, from+cols)} (all prefixes at once).
   * Returns the final row (one of the two buffers) or null if the band minimum exceeded {@code
   * limit} (early exit).
   */
  private static int[] bandedRow(
      String a, String b, int from, int cols, int limit, int[] prev, int[] cur) {
    int n = a.length();
    for (int j = 0; j <= Math.min(cols, limit); j++) {
      prev[j] = j;
    }
    if (limit + 1 <= cols) {
      prev[limit + 1] = INF;
    }
    for (int i = 1; i <= n; i++) {
      int lo = Math.max(0, i - limit);
      int hi = Math.min(cols, i + limit);
      if (lo > hi) {
        return null;
      }
      if (lo > 0) {
        cur[lo - 1] = INF;
      }
      int rowMin = INF;
      char ca = a.charAt(i - 1);
      for (int j = lo; j <= hi; j++) {
        int v;
        if (j == 0) {
          v = i;
        } else {
          int cost = ca == b.charAt(from + j - 1) ? 0 : 1;
          v = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
        }
        cur[j] = v;
        rowMin = Math.min(rowMin, v);
      }
      if (rowMin > limit) {
        return null;
      }
      if (hi + 1 <= cols) {
        cur[hi + 1] = INF;
      }
      int[] t = prev;
      prev = cur;
      cur = t;
    }
    return prev; // after the final swap the last row is in prev
  }

  private record Candidate(PageText page, MatchText text) {}
}
