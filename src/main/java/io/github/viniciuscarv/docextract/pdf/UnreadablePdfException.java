package io.github.viniciuscarv.docextract.pdf;

/** The file is not a PDF, is corrupt, or is encrypted. */
public class UnreadablePdfException extends RuntimeException {

  public UnreadablePdfException(String message) {
    super(message);
  }

  public UnreadablePdfException(String message, Throwable cause) {
    super(message, cause);
  }
}
