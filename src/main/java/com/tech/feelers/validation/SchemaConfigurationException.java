package com.tech.feelers.validation;

/**
 * Raised while turning a form definition into a JSON Schema, when the definition itself is invalid.
 *
 * <p>This aborts the run rather than accumulating errors, because a broken form definition is
 * broken for every submission: continuing would report the author's mistake as if it were the
 * user's data problem.
 */
public final class SchemaConfigurationException extends RuntimeException {

  private final transient ValidationError error;

  /**
   * Carries the fully formed error so the service can return it without re-deriving paths or codes.
   *
   * @param error schema-category error describing the defect
   */
  public SchemaConfigurationException(ValidationError error) {
    super(error.code() + " at " + error.instancePath());
    this.error = error;
  }

  /**
   * Exposes the error for inclusion in the validation result.
   *
   * @return the schema-category error
   */
  public ValidationError error() {
    return error;
  }
}
