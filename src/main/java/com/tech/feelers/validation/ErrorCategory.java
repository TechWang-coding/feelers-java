package com.tech.feelers.validation;

/**
 * Classification of a validation error, matching the categories frozen in the cross-runtime
 * contract.
 *
 * <p>The category decides how a caller must react, so the four kinds are kept distinct even when
 * they arise from the same expression: a rule the user can fix, a form the author must fix, and a
 * dependency that may simply be retried are not interchangeable.
 */
public enum ErrorCategory {

  /** The form definition or its expressions are invalid; handled as a server-side fault. */
  SCHEMA("schema"),

  /** The submitted data violates the generated JSON Schema. */
  DATA("data"),

  /** A FEEL relation assertion evaluated to {@code false}. */
  ASSERTION("assertion"),

  /** A controlled lookup could not answer, so the request may be retried unchanged. */
  UPSTREAM("upstream");

  private final String wireName;

  ErrorCategory(String wireName) {
    this.wireName = wireName;
  }

  /**
   * Returns the lowercase name used on the wire, keeping the JSON contract independent of Java
   * enum naming conventions.
   *
   * @return the serialized category name
   */
  public String wireName() {
    return wireName;
  }
}
