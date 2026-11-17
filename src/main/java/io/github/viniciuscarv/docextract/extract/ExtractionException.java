package io.github.viniciuscarv.docextract.extract;

/** The model returned content that is not the expected JSON. */
public class ExtractionException extends RuntimeException {
  public ExtractionException(String message) {
    super(message);
  }

  public ExtractionException(String message, Throwable cause) {
    super(message, cause);
  }
}
