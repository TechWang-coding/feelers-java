package com.tech.feelers.validation;

import java.util.Map;
import java.util.Objects;

/**
 * One validation error in the cross-runtime error contract.
 *
 * <p>{@code messageKey} and {@code message} are mutually exclusive: platform-owned errors carry a
 * catalogue key plus arguments, while an assertion carries the text its author wrote. Field values
 * are never included, so an error can be logged without leaking submitted data.
 *
 * @param category which kind of failure this is
 * @param code stable error code, for example {@code data.required}
 * @param instancePath JSON Pointer to the offending field in the submitted data
 * @param schemaPath JSON Pointer to the responsible location inside the schema
 * @param keyword JSON Schema keyword that failed, or {@code null} outside schema validation
 * @param assertionId identifier of the failing assertion, or {@code null}
 * @param messageKey message-catalogue key for platform-owned errors, or {@code null}
 * @param message author-written text for assertion errors, or {@code null}
 * @param arguments values for rendering {@code messageKey}; never contains submitted field values
 */
public record ValidationError(ErrorCategory category, String code, String instancePath,
    String schemaPath, String keyword, String assertionId, String messageKey, String message,
    Map<String, Object> arguments) {

  /**
   * Enforces the message contract at construction time so an inconsistent error can never reach a
   * caller, and copies the argument map to keep the record immutable.
   */
  public ValidationError {
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(code, "code");
    if (messageKey != null && message != null) {
      throw new IllegalArgumentException("messageKey and message are mutually exclusive");
    }
    arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
  }

  /**
   * Builds a schema-configuration error, which terminates the request as a server-side fault.
   *
   * @param code stable error code
   * @param instancePath pointer to the affected field
   * @param schemaPath pointer to the offending schema location
   * @param keyword extension or schema keyword involved
   * @param arguments diagnostic values safe to expose
   * @return the error
   */
  public static ValidationError schema(String code, String instancePath, String schemaPath,
      String keyword, Map<String, Object> arguments) {
    return new ValidationError(ErrorCategory.SCHEMA, code, instancePath, schemaPath, keyword, null,
        null, null, arguments);
  }

  /**
   * Builds an assertion failure carrying the text the rule author wrote.
   *
   * @param assertionId identifier of the failing assertion
   * @param instancePath pointer to the field the assertion targets
   * @param schemaPath pointer to the assertion inside the form definition
   * @param message author-written or default failure text
   * @return the error
   */
  public static ValidationError assertionFailed(String assertionId, String instancePath,
      String schemaPath, String message) {
    return new ValidationError(ErrorCategory.ASSERTION, "assertion.failed", instancePath,
        schemaPath, null, assertionId, null, message, Map.of());
  }

  /**
   * Builds an upstream failure, which callers may retry because the data itself may be valid.
   *
   * @param code stable upstream error code
   * @param assertionId assertion whose lookup failed
   * @param instancePath pointer to the field being looked up
   * @param schemaPath pointer to the assertion inside the form definition
   * @param arguments diagnostic values safe to expose
   * @return the error
   */
  public static ValidationError upstream(String code, String assertionId, String instancePath,
      String schemaPath, Map<String, Object> arguments) {
    return new ValidationError(ErrorCategory.UPSTREAM, code, instancePath, schemaPath, null,
        assertionId, "system.lookupUnavailable", null, arguments);
  }
}
