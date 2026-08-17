package com.tech.feelers.expression;

import java.util.Objects;
import java.util.Optional;

/**
 * Syntax-only validation outcome, produced by parsing an expression without executing it. Keeping
 * parsing separate from evaluation lets callers reject malformed expressions before any data is
 * submitted or any function is invoked.
 *
 * @param message parser diagnostic when parsing failed, otherwise {@code null}
 */
public record FeelSyntaxValidationResult(String message) {

  /**
   * Builds the success outcome shared by every syntactically correct expression.
   *
   * @return a result with no diagnostic
   */
  public static FeelSyntaxValidationResult valid() {
    return new FeelSyntaxValidationResult(null);
  }

  /**
   * Builds a failure outcome carrying the parser's diagnostic.
   *
   * @param message parser diagnostic describing the syntax error; must not be null
   * @return an invalid result
   */
  public static FeelSyntaxValidationResult invalid(String message) {
    return new FeelSyntaxValidationResult(Objects.requireNonNull(message, "message"));
  }

  /**
   * Reports success as the absence of a diagnostic, so the two can never disagree.
   *
   * @return whether parsing found no syntax errors
   */
  public boolean isValid() {
    return message == null;
  }

  /**
   * Exposes the diagnostic as an {@link Optional} so success and failure are handled uniformly.
   *
   * @return the parser diagnostic when the expression is invalid
   */
  public Optional<String> optionalMessage() {
    return Optional.ofNullable(message);
  }
}
