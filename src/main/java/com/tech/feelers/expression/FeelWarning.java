package com.tech.feelers.expression;

import java.util.Objects;

/**
 * A diagnostic emitted while evaluating an expression without necessarily aborting evaluation.
 *
 * <p>Mirrors the TypeScript {@code FeelWarning} shape so both runtimes report the same categories
 * for the same expression failures.
 *
 * @param type stable diagnostic category supplied by the expression runtime
 * @param message human-readable description suitable for display or logging
 */
public record FeelWarning(String type, String message) {

  /** Rejects missing diagnostic fields so callers never receive a partially populated warning. */
  public FeelWarning {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(message, "message");
  }
}
