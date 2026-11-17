package io.github.viniciuscarv.docextract.eval;

import io.github.viniciuscarv.docextract.extract.FieldOutcome;
import io.github.viniciuscarv.docextract.model.Category;
import io.github.viniciuscarv.docextract.model.EvidenceStatus;
import io.github.viniciuscarv.docextract.model.VerifiedEvidence;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Span-level precision/recall/F1 of verified evidence against gold spans. */
public final class Scorer {

  private Scorer() {}

  public record Prf(double precision, double recall, double f1) {}

  public record Report(
      Map<Category, Prf> perCategory,
      double overallF1,
      double overallPrecision,
      double overallRecall,
      double supportedRate,
      double unsupportedRate,
      int unmapped) {}

  /** Both lists are per document and aligned by index. UNSUPPORTED evidence is never scored. */
  public static Report score(
      List<List<FieldOutcome>> predicted, List<List<GoldSpan>> gold, int unmapped) {
    Map<Category, Prf> per = new EnumMap<>(Category.class);
    int allHitPred = 0;
    int allPred = 0;
    int allHitGold = 0;
    int allGold = 0;
    int evidence = 0;
    int supported = 0;
    for (Category c : Category.values()) {
      int hitPred = 0;
      int pred = 0;
      int hitGold = 0;
      int goldCount = 0;
      for (int d = 0; d < predicted.size(); d++) {
        List<VerifiedEvidence> spans =
            predicted.get(d).stream()
                .filter(o -> o.category() == c)
                .flatMap(o -> o.evidence().stream())
                .toList();
        List<VerifiedEvidence> scored =
            spans.stream().filter(v -> v.status() != EvidenceStatus.UNSUPPORTED).toList();
        List<GoldSpan> golds = gold.get(d).stream().filter(g -> g.category() == c).toList();
        evidence += spans.size();
        supported += scored.size();
        pred += scored.size();
        goldCount += golds.size();
        hitPred += (int) scored.stream().filter(v -> golds.stream().anyMatch(g -> hit(v, g))).count();
        hitGold += (int) golds.stream().filter(g -> scored.stream().anyMatch(v -> hit(v, g))).count();
      }
      per.put(c, prf(hitPred, pred, hitGold, goldCount));
      allHitPred += hitPred;
      allPred += pred;
      allHitGold += hitGold;
      allGold += goldCount;
    }
    double supportedRate = ratio(supported, evidence);
    Prf overall = prf(allHitPred, allPred, allHitGold, allGold);
    return new Report(
        per,
        overall.f1(),
        overall.precision(),
        overall.recall(),
        supportedRate,
        evidence == 0 ? 0 : 1 - supportedRate,
        unmapped);
  }

  /** Same page, overlap of at least 50% of the shorter span. */
  private static boolean hit(VerifiedEvidence v, GoldSpan g) {
    if (v.page() != g.page()) {
      return false;
    }
    int overlap = Math.min(v.end(), g.end()) - Math.max(v.start(), g.start());
    int shorter = Math.min(v.end() - v.start(), g.end() - g.start());
    return shorter > 0 && overlap > 0 && overlap * 2 >= shorter;
  }

  private static Prf prf(int hitPred, int pred, int hitGold, int gold) {
    double p = ratio(hitPred, pred);
    double r = ratio(hitGold, gold);
    return new Prf(p, r, p + r == 0 ? 0 : 2 * p * r / (p + r));
  }

  private static double ratio(int n, int d) {
    return d == 0 ? 0 : (double) n / d;
  }
}
