package io.github.viniciuscarv.docextract.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.viniciuscarv.docextract.model.DocumentText;
import io.github.viniciuscarv.docextract.testutil.TestPdfs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;

class PdfTextExtractorTest {

  private static final List<String> THREE_PAGES =
      List.of("Page one preamble text here", "Section 9\nGoverning Law is Delaware", "Last page");

  @Test
  void readsTextPerPage() {
    Path pdf = TestPdfs.textPdf(THREE_PAGES);
    DocumentText raw = new PdfTextExtractor(10).read(pdf);
    assertThat(raw.pages()).hasSize(3);
    assertThat(raw.page(2).text()).contains("Governing Law");
  }

  @Test
  void sha256IsStableForSameBytes() throws Exception {
    Path a = TestPdfs.textPdf(THREE_PAGES);
    Path b = Files.createTempFile("copy-", ".pdf");
    b.toFile().deleteOnExit();
    Files.copy(a, b, StandardCopyOption.REPLACE_EXISTING);
    PdfTextExtractor ex = new PdfTextExtractor(10);
    String h1 = ex.read(a).sha256();
    assertThat(h1).matches("[0-9a-f]{64}").isEqualTo(ex.read(b).sha256());
  }

  @Test
  void rejectsPageCountAboveMax() {
    Path pdf = TestPdfs.textPdf(THREE_PAGES);
    assertThatThrownBy(() -> new PdfTextExtractor(2).read(pdf))
        .isInstanceOfSatisfying(
            PageLimitExceededException.class,
            e -> {
              assertThat(e.pages()).isEqualTo(3);
              assertThat(e.max()).isEqualTo(2);
            });
  }

  @Test
  void rejectsNonPdf() throws Exception {
    Path f = Files.createTempFile("fake-", ".pdf");
    f.toFile().deleteOnExit();
    Files.writeString(f, "hello");
    assertThatThrownBy(() -> new PdfTextExtractor(10).read(f))
        .isInstanceOf(UnreadablePdfException.class)
        .hasMessageContaining("not a readable PDF");
  }

  @Test
  void rejectsTruncatedPdf() throws Exception {
    byte[] bytes = Files.readAllBytes(TestPdfs.textPdf(THREE_PAGES));
    Path f = Files.createTempFile("trunc-", ".pdf");
    f.toFile().deleteOnExit();
    Files.write(f, java.util.Arrays.copyOf(bytes, bytes.length / 2));
    assertThatThrownBy(() -> new PdfTextExtractor(10).read(f))
        .isInstanceOf(UnreadablePdfException.class)
        .hasMessageContaining("not a readable PDF");
  }

  @Test
  void rejectsEmptyFile() throws Exception {
    Path f = Files.createTempFile("empty-", ".pdf");
    f.toFile().deleteOnExit();
    assertThatThrownBy(() -> new PdfTextExtractor(10).read(f))
        .isInstanceOf(UnreadablePdfException.class)
        .hasMessageContaining("not a readable PDF");
  }

  @Test
  void rejectsMissingFile() throws Exception {
    Path f = Files.createTempFile("gone-", ".pdf");
    Files.delete(f);
    assertThatThrownBy(() -> new PdfTextExtractor(10).read(f))
        .isInstanceOf(UnreadablePdfException.class)
        .hasMessageContaining("cannot read file");
  }

  @Test
  void rejectsEncryptedPdf() throws Exception {
    Path plain = TestPdfs.textPdf(THREE_PAGES);
    Path enc = Files.createTempFile("enc-", ".pdf");
    enc.toFile().deleteOnExit();
    try (PDDocument doc = Loader.loadPDF(plain.toFile())) {
      StandardProtectionPolicy policy =
          new StandardProtectionPolicy("owner-pw", "user-pw", new AccessPermission());
      policy.setEncryptionKeyLength(128);
      doc.protect(policy);
      doc.save(enc.toFile());
    }
    assertThatThrownBy(() -> new PdfTextExtractor(10).read(enc))
        .isInstanceOf(UnreadablePdfException.class)
        .hasMessageContaining("encrypted");
  }
}
