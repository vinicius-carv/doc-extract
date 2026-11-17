package io.github.viniciuscarv.docextract.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

  @Test
  void joinsHyphenationAndCollapsesWhitespace() {
    assertThat(TextNormalizer.forMatching("Termi-\nnation  for\tConvenience").normalized())
        .isEqualTo("termination for convenience");
  }

  @Test
  void straightensQuotes() {
    assertThat(TextNormalizer.forMatching("\u201CAgreement\u201D").normalized())
        .isEqualTo("\"agreement\"");
  }

  @Test
  void normalizesDashes() {
    assertThat(TextNormalizer.forMatching("12\u201324").normalized()).isEqualTo("12-24");
    assertThat(TextNormalizer.forMatching("a\u2014b").normalized()).isEqualTo("a-b");
  }

  @Test
  void trims() {
    assertThat(TextNormalizer.forMatching("  \n a  b \t").normalized()).isEqualTo("a b");
  }

  @Test
  void mapsOffsetsBackToOriginal() {
    String original = "Right of  Convenience";
    MatchText m = TextNormalizer.forMatching(original);
    int i = m.normalized().indexOf("convenience");
    assertThat(m.originalStart(i)).isEqualTo(original.indexOf("C"));
    assertThat(m.originalEnd(i + "convenience".length())).isEqualTo(original.length());
  }

  @Test
  void offsetsSurviveHyphenJoin() {
    String original = "x Termi-\nnation y";
    MatchText m = TextNormalizer.forMatching(original);
    int i = m.normalized().indexOf("termination");
    assertThat(m.originalStart(i)).isEqualTo(2);
    assertThat(m.originalEnd(i + "termination".length())).isEqualTo(original.indexOf(" y"));
  }
}
