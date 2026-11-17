package io.github.viniciuscarv.docextract.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CategoryTest {

  @Test
  void hasSixCategories() {
    assertThat(Category.values()).hasSize(6);
  }

  @Test
  void resolvesByCuadLabel() {
    assertThat(Category.fromCuadLabel("Governing Law")).isEqualTo(Category.GOVERNING_LAW);
  }

  @Test
  void resolvesByKey() {
    assertThat(Category.fromKey("parties")).isEqualTo(Category.PARTIES);
  }

  @Test
  void keysAreUniqueAndWellFormed() {
    var keys = Arrays.stream(Category.values()).map(Category::key).collect(Collectors.toSet());
    assertThat(keys).hasSize(Category.values().length);
    assertThat(keys).allMatch(k -> k.matches("[a-z_]+"));
  }

  @Test
  void everyCategoryHasDescription() {
    assertThat(Category.values()).allMatch(c -> !c.description().isBlank());
  }

  @Test
  void unknownKeyOrLabelThrows() {
    assertThatThrownBy(() -> Category.fromKey("nope")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Category.fromCuadLabel("Nope"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void documentTextLooksUpPagesByNumber() {
    var doc =
        new DocumentText(
            "abc",
            java.util.List.of(
                new PageText(1, "one"),
                new PageText(2, "two")));
    assertThat(doc.page(2).text()).isEqualTo("two");
  }
}
