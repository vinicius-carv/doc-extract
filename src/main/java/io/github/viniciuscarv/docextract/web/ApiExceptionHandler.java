package io.github.viniciuscarv.docextract.web;

import io.github.viniciuscarv.docextract.extract.ExtractionException;
import io.github.viniciuscarv.docextract.pdf.PageLimitExceededException;
import io.github.viniciuscarv.docextract.pdf.UnreadablePdfException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
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

  @ExceptionHandler(ResourceAccessException.class)
  ProblemDetail unavailable(ResourceAccessException e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE, "Extraction service unavailable — is Ollama running?");
  }

  @ExceptionHandler(RestClientResponseException.class)
  ProblemDetail ollamaError(RestClientResponseException e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE, "Ollama returned an error — is the model pulled?");
  }

  @ExceptionHandler(ExtractionException.class)
  ProblemDetail badModelOutput(ExtractionException e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE, "Model output was not valid JSON — try again");
  }

  @ExceptionHandler(ResponseStatusException.class)
  ProblemDetail status(ResponseStatusException e) {
    return e.getBody();
  }
}
