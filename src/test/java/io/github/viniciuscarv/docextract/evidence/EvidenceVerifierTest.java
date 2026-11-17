package io.github.viniciuscarv.docextract.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.viniciuscarv.docextract.model.DocumentText;
import io.github.viniciuscarv.docextract.model.Evidence;
import io.github.viniciuscarv.docextract.model.EvidenceStatus;
import io.github.viniciuscarv.docextract.model.PageText;
import io.github.viniciuscarv.docextract.model.VerifiedEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceVerifierTest {

  private static final String P1 =
      "Either party may terminate this Agreement for Convenience upon sixty (30) days notice.";
  private static final String P2 =
      "The \"Licensee\" shall pay fees of 12-24 months. This Agreement is governed by the laws of"
          + " Delaware. Confidential Information includes the termi-\nnation schedule and  all\t"
          + "related\nexhibits.";

  private static DocumentText doc(String p1, String p2) {
    return new DocumentText(
        "sha",
        List.of(
            new PageText(1, p1), new PageText(2, p2)));
  }

  private final DocumentText doc = doc(P1, P2);

  private VerifiedEvidence verify(int page, String quote) {
    return EvidenceVerifier.verify(doc, new Evidence(page, quote));
  }

  private void assertVerifiedSpan(VerifiedEvidence v, int page, String expectedSpan) {
    assertThat(v.status()).isEqualTo(EvidenceStatus.VERIFIED);
    assertThat(v.page()).isEqualTo(page);
    assertThat(doc.page(page).text().substring(v.start(), v.end())).isEqualTo(expectedSpan);
  }

  @Test
  void exactQuoteOnClaimedPage() {
    assertVerifiedSpan(verify(1, "for Convenience upon"), 1, "for Convenience upon");
  }

  @Test
  void caseInsensitiveReturnsOriginalCasing() {
    assertVerifiedSpan(verify(1, "FOR CONVENIENCE"), 1, "for Convenience");
  }

  @Test
  void curlyQuotesInQuote() {
    assertVerifiedSpan(
        verify(2, "The \u201CLicensee\u201D shall pay"), 2, "The \"Licensee\" shall pay");
  }

  @Test
  void dashVariantInQuote() {
    assertVerifiedSpan(verify(2, "fees of 12\u201324 months"), 2, "fees of 12-24 months");
  }

  @Test
  void hyphenatedLineBreakInPage() {
    assertVerifiedSpan(verify(2, "the termination schedule"), 2, "the termi-\nnation schedule");
  }

  @Test
  void spacingDifferences() {
    assertVerifiedSpan(
        verify(2, "schedule and all related exhibits"),
        2,
        "schedule and  all\trelated\nexhibits");
  }

  @Test
  void oneWrongCharIsFuzzy() {
    // "sixty (30) days notice" -> one char changed within a >40 char quote
    String quote = "Either party may terminate this Agreement for Convenienze upon sixty (30) days";
    VerifiedEvidence v = verify(1, quote);
    assertThat(v.status()).isEqualTo(EvidenceStatus.FUZZY);
    assertThat(v.page()).isEqualTo(1);
    assertThat(v.start()).isGreaterThanOrEqualTo(0);
    assertThat(v.end()).isGreaterThan(v.start());
    String span = doc.page(1).text().substring(v.start(), v.end());
    assertThat(span).contains("Either party may terminate").contains("sixty (30) days");
  }

  @Test
  void otherPageIsWrongPage() {
    VerifiedEvidence v = verify(1, "governed by the laws of Delaware");
    assertThat(v.status()).isEqualTo(EvidenceStatus.WRONG_PAGE);
    assertThat(v.page()).isEqualTo(2);
    assertThat(doc.page(2).text().substring(v.start(), v.end()))
        .isEqualTo("governed by the laws of Delaware");
  }

  @Test
  void nonexistentClaimedPageSearchesOthers() {
    VerifiedEvidence v = verify(99, "governed by the laws of Delaware");
    assertThat(v.status()).isEqualTo(EvidenceStatus.WRONG_PAGE);
    assertThat(v.page()).isEqualTo(2);
  }

  @Test
  void inventedQuoteIsUnsupported() {
    VerifiedEvidence v = verify(1, "The moon is made of green cheese and nobody can stop it");
    assertThat(v.status()).isEqualTo(EvidenceStatus.UNSUPPORTED);
    assertThat(v.page()).isEqualTo(-1);
    assertThat(v.start()).isEqualTo(-1);
    assertThat(v.end()).isEqualTo(-1);
    assertThat(v.claimed().page()).isEqualTo(1);
  }

  @Test
  void blankQuoteIsUnsupported() {
    assertThat(verify(1, "   \n ").status()).isEqualTo(EvidenceStatus.UNSUPPORTED);
    assertThat(verify(1, "").status()).isEqualTo(EvidenceStatus.UNSUPPORTED);
  }

  @Test
  void claimedPageWinsWhenQuoteOnBothPages() {
    DocumentText both = doc("This Agreement is binding.", "Preamble. This Agreement is binding.");
    VerifiedEvidence v = EvidenceVerifier.verify(both, new Evidence(2, "this agreement is binding"));
    assertThat(v.status()).isEqualTo(EvidenceStatus.VERIFIED);
    assertThat(v.page()).isEqualTo(2);
  }

  @Test
  void shortQuotesAreNeverFuzzy() {
    assertThat(verify(1, "Agreemxnt for").status()).isEqualTo(EvidenceStatus.FUZZY); // 13 chars
    assertThat(verify(1, "Agrexme").status()).isEqualTo(EvidenceStatus.UNSUPPORTED); // 7 chars
  }

  @Test
  void overlongQuotesAreExactOnly() {
    String long1 = "word ".repeat(120).trim(); // 599 chars
    DocumentText d = doc(long1, "x");
    String off = long1.substring(0, 300) + "Z" + long1.substring(301);
    assertThat(EvidenceVerifier.verify(d, new Evidence(1, long1)).status())
        .isEqualTo(EvidenceStatus.VERIFIED);
    assertThat(EvidenceVerifier.verify(d, new Evidence(1, off)).status())
        .isEqualTo(EvidenceStatus.UNSUPPORTED);
  }

  @Test
  void crlfHyphenation() {
    DocumentText d = doc("The termi-\r\nnation clause applies.", "x");
    VerifiedEvidence v = EvidenceVerifier.verify(d, new Evidence(1, "termination clause"));
    assertThat(v.status()).isEqualTo(EvidenceStatus.VERIFIED);
    assertThat(d.page(1).text().substring(v.start(), v.end())).isEqualTo("termi-\r\nnation clause");
  }

  @Test
  void longNonMatchingQuoteIsFast() {
    String[] words = {
      "the", "party", "shall", "pay", "all", "fees", "under", "this", "agreement", "and", "any",
      "notice", "must", "be", "given", "in", "writing", "to", "the", "other", "party", "within",
      "thirty", "days", "of", "receipt", "unless", "otherwise", "agreed", "by", "both", "parties"
    };
    java.util.Random rnd = new java.util.Random(42);
    List<PageText> pages = new java.util.ArrayList<>();
    for (int p = 1; p <= 10; p++) {
      StringBuilder sb = new StringBuilder();
      while (sb.length() < 5000) {
        sb.append(words[rnd.nextInt(words.length)]).append(' ');
      }
      pages.add(new PageText(p, sb.toString()));
    }
    DocumentText big = new DocumentText("sha", pages);
    StringBuilder qb = new StringBuilder();
    while (qb.length() < 390) {
      qb.append(words[rnd.nextInt(words.length)]).append(' ');
    }
    Evidence inVocab = new Evidence(1, qb.toString().trim().replace('a', 'q'));
    Evidence offVocab = new Evidence(1, qb.toString().trim().replace("the", "zzz").replace('e', 'x'));
    for (Evidence ev : List.of(inVocab, offVocab)) {
      assertThat(EvidenceVerifier.verify(big, ev).status()).isEqualTo(EvidenceStatus.UNSUPPORTED);
      org.junit.jupiter.api.Assertions.assertTimeout(
          java.time.Duration.ofMillis(500),
          () ->
              assertThat(EvidenceVerifier.verify(big, ev).status())
                  .isEqualTo(EvidenceStatus.UNSUPPORTED));
    }
  }

  @Test
  void spreadOcrEditsAreFuzzy() {
    DocumentText d =
        doc(
            "Intro text. Tenant shall maintain insurance coverage during entire lease period"
                + " always. More text follows here.",
            "unrelated");
    VerifiedEvidence v =
        EvidenceVerifier.verify(
            d,
            new Evidence(
                1, "Tenant shill maintiin insuranse coveraga durinj entirr lease period always"));
    assertThat(v.status()).isEqualTo(EvidenceStatus.FUZZY);
    assertThat(v.page()).isEqualTo(1);
    assertThat(d.page(1).text().substring(v.start(), v.end()))
        .startsWith("Tenant shall maintain")
        .contains("lease period always");
  }

  @Test
  void ninePercentErrorsInEveryWordAreFuzzy() {
    String exact =
        "the lessee shall indemnify and hold harmless the lessor against all claims arising from"
            + " the use of the premises during the term of this lease";
    // ~9% char errors, spread over almost every word (one substitution per ~11 chars)
    StringBuilder noisy = new StringBuilder(exact);
    for (int i = 5; i < noisy.length(); i += 11) {
      noisy.setCharAt(i, noisy.charAt(i) == 'x' ? 'y' : 'x');
    }
    DocumentText d = doc("Preface. " + exact + ". Tail.", "other");
    VerifiedEvidence v = EvidenceVerifier.verify(d, new Evidence(1, noisy.toString()));
    assertThat(v.status()).isEqualTo(EvidenceStatus.FUZZY);
    assertThat(d.page(1).text().substring(v.start(), v.end())).startsWith(exact);
  }
}
