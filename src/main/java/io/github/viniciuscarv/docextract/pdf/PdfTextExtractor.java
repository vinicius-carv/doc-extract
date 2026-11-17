package io.github.viniciuscarv.docextract.pdf;

import io.github.viniciuscarv.docextract.corpus.Sha256;
import io.github.viniciuscarv.docextract.model.DocumentText;
import io.github.viniciuscarv.docextract.model.PageText;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

/** Reads the text layer of a PDF page by page; a page without a text layer has empty text. */
public class PdfTextExtractor {

  private final int maxPages;

  public PdfTextExtractor(int maxPages) {
    this.maxPages = maxPages;
  }

  public DocumentText read(Path pdf) {
    String sha256;
    try {
      sha256 = Sha256.of(pdf);
    } catch (UncheckedIOException e) {
      throw new UnreadablePdfException("cannot read file: " + pdf.getFileName(), e);
    }
    try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
      if (doc.isEncrypted()) {
        throw new UnreadablePdfException("PDF is encrypted: " + pdf.getFileName());
      }
      int pageCount = doc.getNumberOfPages();
      if (pageCount > maxPages) {
        throw new PageLimitExceededException(pageCount, maxPages);
      }
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      List<PageText> pages = new ArrayList<>(pageCount);
      for (int p = 1; p <= pageCount; p++) {
        stripper.setStartPage(p);
        stripper.setEndPage(p);
        pages.add(new PageText(p, stripper.getText(doc)));
      }
      return new DocumentText(sha256, pages);
    } catch (InvalidPasswordException e) {
      throw new UnreadablePdfException("PDF is encrypted: " + pdf.getFileName(), e);
    } catch (UnreadablePdfException | PageLimitExceededException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      throw new UnreadablePdfException("File is not a readable PDF: " + pdf.getFileName(), e);
    }
  }
}
