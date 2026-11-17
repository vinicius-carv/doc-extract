package io.github.viniciuscarv.docextract.web;

import io.github.viniciuscarv.docextract.extract.ExtractionException;
import io.github.viniciuscarv.docextract.pdf.PageLimitExceededException;
import io.github.viniciuscarv.docextract.pdf.UnreadablePdfException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/** All API errors as RFC 9457 problem details. */
@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler({UnreadablePdfException.class, PageLimitExceededException.class})
  ProblemDetail badPdf(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "File exceeds the 10 MB limit");
  }

  @ExceptionHandler({ResourceAccessException.class, ExtractionException.class})
  ProblemDetail unavailable(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE, "Extraction service unavailable — is Ollama running?");
  }

  @ExceptionHandler(ResponseStatusException.class)
  ProblemDetail status(ResponseStatusException e) {
    return e.getBody();
  }
}
