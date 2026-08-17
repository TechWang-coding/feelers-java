package com.tech.feelers.validation;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of one validation run: every error found, in execution order.
 *
 * @param errors errors produced by schema generation, schema validation, or assertions
 */
public record ValidationResult(List<ValidationError> errors) {

  /** Copies the error list so a result cannot be altered after it is returned to a caller. */
  public ValidationResult {
    Objects.requireNonNull(errors, "errors");
    errors = List.copyOf(errors);
  }

  /**
   * Builds the outcome for data that satisfied every rule.
   *
   * @return a result with no errors
   */
  public static ValidationResult valid() {
    return new ValidationResult(List.of());
  }

  /**
   * Reports whether the data passed, so callers do not have to inspect the list themselves.
   *
   * @return whether no errors were produced
   */
  public boolean isValid() {
    return errors.isEmpty();
  }

  /**
   * Reports whether any error requires server-side handling rather than a user-facing message.
   *
   * @return whether at least one schema-configuration error is present
   */
  public boolean hasSchemaErrors() {
    return errors.stream().anyMatch(error -> error.category() == ErrorCategory.SCHEMA);
  }
}
