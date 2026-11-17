package io.github.viniciuscarv.docextract.testutil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Generates small PDFs for tests. Files are temp files deleted on JVM exit. */
public final class TestPdfs {

  private static final float FONT_SIZE = 12f;
  private static final float LEADING = 16f;
  private static final float MARGIN = 50f;

  private TestPdfs() {}

  /** One page per entry, real text layer; "\n" in an entry starts a new line. */
  public static Path textPdf(List<String> pages) {
    try (PDDocument doc = new PDDocument()) {
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      for (String text : pages) {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
          cs.beginText();
          cs.setFont(font, FONT_SIZE);
          cs.setLeading(LEADING);
          cs.newLineAtOffset(MARGIN, page.getMediaBox().getHeight() - MARGIN);
          for (String line : text.split("\n")) {
            cs.showText(line);
            cs.newLine();
          }
          cs.endText();
        }
      }
      return save(doc);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path save(PDDocument doc) throws IOException {
    Path file = Files.createTempFile("doc-extract-test-", ".pdf");
    file.toFile().deleteOnExit();
    doc.save(file.toFile());
    return file;
  }
}
