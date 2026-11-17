package io.github.viniciuscarv.docextract.extract;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.viniciuscarv.docextract.model.Category;
import io.github.viniciuscarv.docextract.model.Evidence;
import io.github.viniciuscarv.docextract.model.EvidenceStatus;
import io.github.viniciuscarv.docextract.model.VerifiedEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class MergeTest {

  private static final Category C = Category.GOVERNING_LAW;

  private static FieldOutcome outcome(String value, EvidenceStatus... statuses) {
    return new FieldOutcome(
        C,
        value,
        java.util.Arrays.stream(statuses)
            .map(s -> new VerifiedEvidence(new Evidence(1, "q"), s, 1, 0, 1))
            .toList());
  }

  @Test
  void verifiedBeatsEarlierUnverified() {
    FieldOutcome unverified = outcome("Texas", EvidenceStatus.UNSUPPORTED);
    FieldOutcome verified = outcome("Delaware", EvidenceStatus.WRONG_PAGE);
    assertThat(Merge.pick(C, List.of(unverified, verified))).isSameAs(verified);
  }

  @Test
  void firstVerifiedWinsAmongVerified() {
    FieldOutcome first = outcome("A", EvidenceStatus.FUZZY);
    assertThat(Merge.pick(C, List.of(outcome(null), first, outcome("B", EvidenceStatus.VERIFIED))))
        .isSameAs(first);
  }

  @Test
  void keepsFirstUnverifiedWhenNothingVerified() {
    FieldOutcome first = outcome("Texas", EvidenceStatus.UNSUPPORTED);
    assertThat(Merge.pick(C, List.of(outcome(null), first, outcome("Ohio")))).isSameAs(first);
  }

  @Test
  void allNullGivesNullWithNoEvidence() {
    FieldOutcome r = Merge.pick(C, List.of(outcome(null), outcome(null)));
    assertThat(r.category()).isEqualTo(C);
    assertThat(r.value()).isNull();
    assertThat(r.evidence()).isEmpty();
  }
}
