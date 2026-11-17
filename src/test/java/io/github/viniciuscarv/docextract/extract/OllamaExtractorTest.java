package io.github.viniciuscarv.docextract.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OllamaExtractorTest {

  @Test
  void postsSchemaAndOptionsAndReturnsMessageContent() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://ollama:11434/api/chat"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.model").value("m1"))
        .andExpect(jsonPath("$.stream").value(false))
        .andExpect(jsonPath("$.think").value(false))
        .andExpect(jsonPath("$.options.temperature").value(0))
        .andExpect(jsonPath("$.options.num_ctx").value(16384))
        .andExpect(jsonPath("$.format.properties.parties").exists())
        .andExpect(jsonPath("$.format.properties.renewal_term").exists())
        .andExpect(jsonPath("$.messages[0].content").value("hello"))
        .andRespond(
            withSuccess("{\"message\":{\"content\":\"{\\\"x\\\":1}\"}}", MediaType.APPLICATION_JSON));

    String out = new OllamaExtractor(builder, "http://ollama:11434", "m1").complete("sha", 0, "hello");

    assertThat(out).isEqualTo("{\"x\":1}");
    server.verify();
  }
}
