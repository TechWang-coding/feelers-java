package com.tech.feelers.validation.schema;

import java.util.Optional;

/**
 * Recognises the {@code {{ expression }}} form used by dynamic numeric bounds.
 *
 * <p>The delimiters must wrap the whole value: accepting surrounding text would make a bound
 * ambiguous between a literal string and an expression, and a partially interpolated bound has no
 * meaning as a JSON Schema keyword value.
 */
public final class DynamicBoundSyntax {

  private static final String OPEN = "{{";
  private static final String CLOSE = "}}";

  private DynamicBoundSyntax() {
  }

  /**
   * Extracts the expression from a bound value.
   *
   * @param value raw keyword value
   * @return the trimmed expression, or empty when the value is not a single well-formed expression
   */
  public static Optional<String> unwrap(String value) {
    String trimmed = value.strip();
    if (!trimmed.startsWith(OPEN) || !trimmed.endsWith(CLOSE) || trimmed.length() < 4) {
      return Optional.empty();
    }
    String expression = trimmed.substring(OPEN.length(), trimmed.length() - CLOSE.length()).strip();
    return expression.isEmpty() ? Optional.empty() : Optional.of(expression);
  }
}
