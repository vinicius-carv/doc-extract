package io.github.viniciuscarv.docextract.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.viniciuscarv.docextract.model.Category;
import io.github.viniciuscarv.docextract.model.DocumentText;
import io.github.viniciuscarv.docextract.model.EvidenceStatus;
import io.github.viniciuscarv.docextract.model.PageText;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractionServiceTest {

  private static DocumentText doc(int pages) {
    List<PageText> list = new ArrayList<>();
    for (int i = 1; i <= pages; i++) {
      list.add(new PageText(i, "Page " + i + " text. Governed by the laws of Delaware."));
    }
    return new DocumentText("sha", list);
  }

  private static FieldOutcome of(List<FieldOutcome> all, Category c) {
    return all.get(c.ordinal());
  }

  @Test
  void verifiesEvidenceAndReturnsOneOutcomePerCategoryInOrder() {
    String json =
        "{\"governing_law\":{\"value\":\"Delaware\",\"evidence\":"
            + "[{\"page\":1,\"quote\":\"laws of Delaware\"}]}}";
    List<FieldOutcome> out = new ExtractionService((s, w, p) -> json).extract(doc(1));

    assertThat(out).extracting(FieldOutcome::category).containsExactly(Category.values());
    FieldOutcome law = of(out, Category.GOVERNING_LAW);
    assertThat(law.value()).isEqualTo("Delaware");
    assertThat(law.evidence()).singleElement().extracting("status").isEqualTo(EvidenceStatus.VERIFIED);
    assertThat(of(out, Category.PARTIES).value()).isNull();
  }

  @Test
  void pageOutsideDocumentOrWindowIsUnsupportedNotAnException() {
    String json =
        "{\"governing_law\":{\"value\":\"Delaware\",\"evidence\":"
            + "[{\"page\":99,\"quote\":\"laws of Delaware\"},{\"page\":0,\"quote\":\"laws\"}]}}";
    FieldOutcome law =
        of(new ExtractionService((s, w, p) -> json).extract(doc(2)), Category.GOVERNING_LAW);
    assertThat(law.evidence())
        .extracting("status")
        .containsExactly(EvidenceStatus.UNSUPPORTED, EvidenceStatus.UNSUPPORTED);
  }

  @Test
  void promptIsPageTaggedPerWindowAndWindowsAreMerged() {
    List<String> prompts = new ArrayList<>();
    Extractor fake =
        (s, w, p) -> {
          prompts.add(p);
          return w == 0
              ? "{\"parties\":{\"value\":\"Unsure\",\"evidence\":[]}}"
              : "{\"parties\":{\"value\":\"Acme\",\"evidence\":[{\"page\":7,\"quote\":\"Page 7 text\"}]}}";
        };
    List<FieldOutcome> out = new ExtractionService(fake).extract(doc(7));

    assertThat(prompts).hasSize(2);
    assertThat(prompts.get(0)).contains("<page n=\"1\">", "<page n=\"6\">").doesNotContain("<page n=\"7\">");
    assertThat(prompts.get(1)).contains("<page n=\"6\">", "<page n=\"7\">");
    assertThat(of(out, Category.PARTIES).value()).isEqualTo("Acme");
  }

  @Test
  void emptyOrNonObjectOutputThrows() {
    for (String bad : new String[] {"", "[]", "null"}) {
      assertThatThrownBy(() -> new ExtractionService((s, w, p) -> bad).extract(doc(1)))
          .isInstanceOf(ExtractionException.class);
    }
  }

  @Test
  void blankValueIsNull() {
    String json = "{\"parties\":{\"value\":\"  \",\"evidence\":[]}}";
    assertThat(of(new ExtractionService((s, w, p) -> json).extract(doc(1)), Category.PARTIES).value())
        .isNull();
  }

  @Test
  void invalidJsonThrowsExtractionException() {
    assertThatThrownBy(() -> new ExtractionService((s, w, p) -> "oops").extract(doc(1)))
        .isInstanceOf(ExtractionException.class);
  }
}
