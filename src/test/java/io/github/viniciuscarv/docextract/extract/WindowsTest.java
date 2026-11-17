package io.github.viniciuscarv.docextract.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class WindowsTest {

  @Test
  void splitsInWindowsOfSixOverlappingByOne() {
    assertThat(Windows.split(0)).isEmpty();
    assertThat(Windows.split(1)).containsExactly(new int[] {1, 1});
    assertThat(Windows.split(6)).containsExactly(new int[] {1, 6});
    assertThat(Windows.split(7)).containsExactly(new int[] {1, 6}, new int[] {6, 7});
    List<int[]> twelve = Windows.split(12);
    assertThat(twelve)
        .containsExactly(new int[] {1, 6}, new int[] {6, 11}, new int[] {11, 12});
  }
}
