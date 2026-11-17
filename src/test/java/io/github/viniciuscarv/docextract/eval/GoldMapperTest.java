package io.github.viniciuscarv.docextract.eval;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.viniciuscarv.docextract.model.Category;
import io.github.viniciuscarv.docextract.model.DocumentText;
import io.github.viniciuscarv.docextract.model.PageText;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldMapperTest {

  private static final DocumentText DOC =
      new DocumentText(
          "sha",
          List.of(
              new PageText(1, "This Agreement is made between\nAcme  Corp and Beta Inc."),
              new PageText(2, "Governed by the laws of Delaware.")));

  @Test
  void exactSpanMapsToPageAndOriginalOffsets() {
    GoldMapper.Mapped m =
        GoldMapper.map(DOC, List.of(new GoldEntry("Governing Law", "laws of Delaware")));
    assertThat(m.unmapped()).isZero();
    GoldSpan s = m.spans().get(0);
    assertThat(s.category()).isEqualTo(Category.GOVERNING_LAW);
    assertThat(s.page()).isEqualTo(2);
    assertThat(DOC.page(2).text().substring(s.start(), s.end())).isEqualTo("laws of Delaware");
  }

  @Test
  void whitespaceVariantStillMaps() {
    GoldMapper.Mapped m =
        GoldMapper.map(DOC, List.of(new GoldEntry("Parties", "Acme Corp and Beta Inc.")));
    assertThat(m.unmapped()).isZero();
    GoldSpan s = m.spans().get(0);
    assertThat(s.page()).isEqualTo(1);
    assertThat(DOC.page(1).text().substring(s.start(), s.end()))
        .isEqualTo("Acme  Corp and Beta Inc.");
  }

  @Test
  void missingOrBlankSpanIsUnmapped() {
    GoldMapper.Mapped m =
        GoldMapper.map(
            DOC, List.of(new GoldEntry("Parties", "Gamma LLC"), new GoldEntry("Parties", "  ")));
    assertThat(m.spans()).isEmpty();
    assertThat(m.unmapped()).isEqualTo(2);
  }
}
