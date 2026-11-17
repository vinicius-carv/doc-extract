package io.github.viniciuscarv.docextract.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordedExtractorTest {

  @TempDir Path dir;

  @Test
  void replaysExistingRecordingWithoutCallingDelegate() throws IOException {
    Files.writeString(dir.resolve("abc-2.json"), "{\"a\":1}");
    Extractor boom =
        (s, w, p) -> {
          throw new AssertionError("delegate must not be called");
        };
    assertThat(new RecordedExtractor(dir, boom).complete("abc", 2, "p")).isEqualTo("{\"a\":1}");
  }

  @Test
  void recordsDelegateResponseWhenMissing() throws IOException {
    RecordedExtractor r = new RecordedExtractor(dir.resolve("sub"), (s, w, p) -> "{\"b\":2}");
    assertThat(r.complete("abc", 0, "p")).isEqualTo("{\"b\":2}");
    assertThat(Files.readString(dir.resolve("sub/abc-0.json"))).isEqualTo("{\"b\":2}");
  }

  @Test
  void doesNotRecordInvalidJson() {
    RecordedExtractor r = new RecordedExtractor(dir, (s, w, p) -> "not json");
    assertThatThrownBy(() -> r.complete("abc", 0, "p")).isInstanceOf(ExtractionException.class);
    assertThat(dir.resolve("abc-0.json")).doesNotExist();
  }

  @Test
  void doesNotRecordEmptyOrNonObjectJson() {
    for (String bad : new String[] {"", "[]", "null", "42"}) {
      RecordedExtractor r = new RecordedExtractor(dir, (s, w, p) -> bad);
      assertThatThrownBy(() -> r.complete("abc", 0, "p")).isInstanceOf(ExtractionException.class);
    }
    assertThat(dir.resolve("abc-0.json")).doesNotExist();
  }

  @Test
  void missingWithoutDelegateThrowsNamingFile() {
    assertThatThrownBy(() -> new RecordedExtractor(dir, null).complete("abc", 3, "p"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("abc-3.json");
  }
}
