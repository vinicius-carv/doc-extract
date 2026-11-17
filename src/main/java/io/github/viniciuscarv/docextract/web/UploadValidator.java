package io.github.viniciuscarv.docextract.web;

import io.github.viniciuscarv.docextract.pdf.UnreadablePdfException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Cheap pre-parse checks on an uploaded file; page count and encryption are checked by the parser. */
final class UploadValidator {

  static final int MAX_BYTES = 10 * 1024 * 1024;
  private static final byte[] MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

  private UploadValidator() {}

  static void check(byte[] bytes) {
    if (bytes.length > MAX_BYTES) {
      throw new UnreadablePdfException("File exceeds the 10 MB limit");
    }
    if (bytes.length < MAGIC.length || !Arrays.equals(bytes, 0, MAGIC.length, MAGIC, 0, MAGIC.length)) {
      throw new UnreadablePdfException("File is not a PDF");
    }
  }
}
