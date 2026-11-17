package io.github.viniciuscarv.docextract.model;

import java.util.Arrays;

/** The extractable contract categories (names follow CUAD v1). */
public enum Category {
  PARTIES(
      "parties",
      "Parties",
      "The legal names of the parties that entered into the agreement."),
  AGREEMENT_DATE(
      "agreement_date",
      "Agreement Date",
      "The date the agreement was signed or dated."),
  EFFECTIVE_DATE(
      "effective_date",
      "Effective Date",
      "The date on which the agreement takes effect."),
  EXPIRATION_DATE(
      "expiration_date",
      "Expiration Date",
      "The date on which the initial term of the agreement ends."),
  GOVERNING_LAW(
      "governing_law",
      "Governing Law",
      "The jurisdiction whose law governs the agreement."),
  RENEWAL_TERM(
      "renewal_term",
      "Renewal Term",
      "The length of each renewal period after the initial term.");

  private final String key;
  private final String cuadLabel;
  private final String description;

  Category(String key, String cuadLabel, String description) {
    this.key = key;
    this.cuadLabel = cuadLabel;
    this.description = description;
  }

  public String key() {
    return key;
  }

  public String cuadLabel() {
    return cuadLabel;
  }

  public String description() {
    return description;
  }

  public static Category fromKey(String key) {
    return Arrays.stream(values())
        .filter(c -> c.key.equals(key))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown category key: " + key));
  }

  public static Category fromCuadLabel(String label) {
    return Arrays.stream(values())
        .filter(c -> c.cuadLabel.equals(label))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown CUAD label: " + label));
  }
}
