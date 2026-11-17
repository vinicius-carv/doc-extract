package io.github.viniciuscarv.docextract.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.viniciuscarv.docextract.extract.OllamaExtractor;
import io.github.viniciuscarv.docextract.testutil.TestPdfs;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) throws IOException {
    Path results = Files.createTempFile("featured", ".json");
    results.toFile().deleteOnExit();
    Files.writeString(
        results,
        "{\"documents\":[{\"id\":\"a\",\"title\":\"Doc A\",\"pages\":[{\"number\":1,\"text\":\"hi\"}],"
            + "\"fields\":[],\"precision\":1.0,\"recall\":1.0,\"f1\":0.5}]}");
    r.add("docextract.results-file", results::toString);
  }

  @Autowired MockMvc mvc;
  @MockitoBean OllamaExtractor ollama;

  private static MockMultipartFile file(byte[] bytes) {
    return new MockMultipartFile("file", "contract.pdf", "application/pdf", bytes);
  }

  private static byte[] pdf(String... pages) throws IOException {
    return Files.readAllBytes(TestPdfs.textPdf(List.of(pages)));
  }

  @Test
  void listsDocuments() throws Exception {
    mvc.perform(get("/api/documents"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("a"))
        .andExpect(jsonPath("$[0].title").value("Doc A"))
        .andExpect(jsonPath("$[0].pages").value(1))
        .andExpect(jsonPath("$[0].f1").value(0.5));
  }

  @Test
  void returnsOneDocument() throws Exception {
    mvc.perform(get("/api/documents/a"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pages[0].text").value("hi"));
  }

  @Test
  void unknownIdIs404() throws Exception {
    mvc.perform(get("/api/documents/nope"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  void missingResultsFileMeansEmptyList() {
    assertThat(new DocumentController(null, "no/such/file.json").list()).isEmpty();
  }

  @Test
  void nonPdfIs400() throws Exception {
    mvc.perform(multipart("/api/extract").file(file("hello world".getBytes())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("File is not a PDF"));
  }

  @Test
  void pdfMagicOnNonPdfIs400() throws Exception {
    mvc.perform(multipart("/api/extract").file(file("%PDF-1.4 garbage".getBytes())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void elevenPagesIs400() throws Exception {
    byte[] bytes = Files.readAllBytes(TestPdfs.textPdf(Collections.nCopies(11, "x")));
    mvc.perform(multipart("/api/extract").file(file(bytes)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("PDF has 11 pages, which exceeds the limit of 10"));
  }

  @Test
  void encryptedPdfIs400() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (PDDocument doc = Loader.loadPDF(pdf("secret"))) {
      doc.protect(new StandardProtectionPolicy("owner", "user", new AccessPermission()));
      doc.save(out);
    }
    mvc.perform(multipart("/api/extract").file(file(out.toByteArray())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(containsString("encrypted")));
  }

  @Test
  void oversizeIs400() throws Exception {
    byte[] big = new byte[10 * 1024 * 1024 + 1];
    System.arraycopy("%PDF-".getBytes(), 0, big, 0, 5);
    mvc.perform(multipart("/api/extract").file(file(big))).andExpect(status().isBadRequest());
  }

  @Test
  void multipartLimitExceptionMapsTo400() {
    assertThat(new ApiExceptionHandler().tooLarge(new MaxUploadSizeExceededException(1)).getStatus())
        .isEqualTo(400);
  }

  @Test
  void ollamaDownIs503() throws Exception {
    when(ollama.complete(anyString(), anyInt(), any()))
        .thenThrow(new ResourceAccessException("Connection refused"));
    mvc.perform(multipart("/api/extract").file(file(pdf("a"))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            jsonPath("$.detail").value("Extraction service unavailable — is Ollama running?"));
  }

  @Test
  void ollamaHttpErrorIs503() throws Exception {
    when(ollama.complete(anyString(), anyInt(), any()))
        .thenThrow(
            new org.springframework.web.client.HttpClientErrorException(
                org.springframework.http.HttpStatus.NOT_FOUND));
    mvc.perform(multipart("/api/extract").file(file(pdf("a"))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.detail").value("Ollama returned an error — is the model pulled?"));
  }

  @Test
  void badModelJsonIs503() throws Exception {
    when(ollama.complete(anyString(), anyInt(), any())).thenReturn("not json");
    mvc.perform(multipart("/api/extract").file(file(pdf("a"))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.detail").value("Model output was not valid JSON — try again"));
  }

  @Test
  void happyPathReturnsSixFields() throws Exception {
    when(ollama.complete(anyString(), anyInt(), any()))
        .thenReturn(
            "{\"governing_law\":{\"value\":\"Delaware\",\"evidence\":[{\"page\":1,\"quote\":\"Delaware law\"}]}}");
    mvc.perform(multipart("/api/extract").file(file(pdf("Governed by Delaware law."))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("contract.pdf"))
        .andExpect(jsonPath("$.pages[0].number").value(1))
        .andExpect(jsonPath("$.fields.length()").value(6))
        .andExpect(jsonPath("$.fields[4].value").value("Delaware"))
        .andExpect(jsonPath("$.fields[4].evidence[0].status").value("VERIFIED"));
  }
}
