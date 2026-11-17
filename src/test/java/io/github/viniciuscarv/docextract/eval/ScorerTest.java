package io.github.viniciuscarv.docextract.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.viniciuscarv.docextract.extract.FieldOutcome;
import io.github.viniciuscarv.docextract.model.Category;
import io.github.viniciuscarv.docextract.model.Evidence;
import io.github.viniciuscarv.docextract.model.EvidenceStatus;
import io.github.viniciuscarv.docextract.model.VerifiedEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScorerTest {

  private static final Category C = Category.PARTIES;

  private static VerifiedEvidence ev(EvidenceStatus st, int page, int start, int end) {
    return new VerifiedEvidence(new Evidence(page, "q"), st, page, start, end);
  }

  private static Scorer.Report score(
      List<VerifiedEvidence> pred, List<GoldSpan> gold, int unmapped) {
    return Scorer.score(List.of(List.of(new FieldOutcome(C, "v", pred))), List.of(gold), unmapped);
  }

  private static GoldSpan gold(int page, int start, int end) {
    return new GoldSpan(C, page, start, end);
  }

  @Test
  void overlapOfExactlyHalfTheShorterSpanIsAHit() {
    // gold 0-10, predicted 5-100: overlap 5 = 50% of the shorter span (10)
    Scorer.Report r =
        score(List.of(ev(EvidenceStatus.VERIFIED, 1, 5, 100)), List.of(gold(1, 0, 10)), 0);
    assertThat(r.perCategory().get(C).precision()).isEqualTo(1.0);
    assertThat(r.perCategory().get(C).recall()).isEqualTo(1.0);
    assertThat(r.overallF1()).isEqualTo(1.0);
  }

  @Test
  void overlapBelowHalfIsAMiss() {
    // gold 0-100, predicted 51-300: overlap 49 of shorter 100 = 49%
    Scorer.Report r =
        score(List.of(ev(EvidenceStatus.VERIFIED, 1, 51, 300)), List.of(gold(1, 0, 100)), 0);
    assertThat(r.perCategory().get(C).precision()).isZero();
    assertThat(r.perCategory().get(C).recall()).isZero();
    assertThat(r.overallF1()).isZero();
  }

  @Test
  void differentPageIsAMiss() {
    Scorer.Report r =
        score(List.of(ev(EvidenceStatus.VERIFIED, 2, 0, 10)), List.of(gold(1, 0, 10)), 0);
    assertThat(r.overallF1()).isZero();
  }

  @Test
  void emptyPredictionAndEmptyGoldDoNotDivideByZero() {
    Scorer.Report r = score(List.of(), List.of(), 3);
    assertThat(r.perCategory().get(C).f1()).isZero();
    assertThat(r.overallF1()).isZero();
    assertThat(r.supportedRate()).isZero();
    assertThat(r.unsupportedRate()).isZero();
    assertThat(r.unmapped()).isEqualTo(3);
  }

  @Test
  void unsupportedEvidenceIsNotScoredButCountsInRates() {
    Scorer.Report r =
        score(
            List.of(
                ev(EvidenceStatus.VERIFIED, 1, 0, 10),
                ev(EvidenceStatus.UNSUPPORTED, -1, -1, -1),
                ev(EvidenceStatus.FUZZY, 1, 50, 60)),
            List.of(gold(1, 0, 10)),
            0);
    assertThat(r.perCategory().get(C).precision()).isEqualTo(0.5); // 1 of 2 scored
    assertThat(r.perCategory().get(C).recall()).isEqualTo(1.0);
    assertThat(r.perCategory().get(C).f1()).isCloseTo(2.0 / 3, within(1e-9));
    assertThat(r.supportedRate()).isCloseTo(2.0 / 3, within(1e-9));
    assertThat(r.unsupportedRate()).isCloseTo(1.0 / 3, within(1e-9));
  }
}
