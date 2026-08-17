package com.tech.feelers.expression;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of evaluating one FEEL expression. A registered-function failure is reported as a warning
 * with a {@code null} value rather than an exception, matching the TypeScript engine so both
 * runtimes classify the same failure the same way.
 *
 * @param value evaluated value, or {@code null} when a registered function failed
 * @param warnings diagnostics emitted by the runtime or the engine
 */
public record FeelEvaluationResult(Object value, List<FeelWarning> warnings) {

  /** Copies the warning list so an evaluation result cannot be altered after it is returned. */
  public FeelEvaluationResult {
    Objects.requireNonNull(warnings, "warnings");
    warnings = List.copyOf(warnings);
  }

  /**
   * Builds a successful result for runtimes that produced no diagnostics.
   *
   * @param value evaluated value
   * @return a result carrying the value and no warnings
   */
  public static FeelEvaluationResult of(Object value) {
    return new FeelEvaluationResult(value, List.of());
  }

  /**
   * Builds the failure result used when a registered function throws, keeping the {@code null}
   * value and warning pairing consistent across call sites.
   *
   * @param warning diagnostic describing the function failure
   * @return a result with no value and one warning
   */
  public static FeelEvaluationResult failed(FeelWarning warning) {
    return new FeelEvaluationResult(null, List.of(warning));
  }

  /**
   * Exposes the value as an {@link Optional} so callers handle the warning case explicitly instead
   * of dereferencing a {@code null}.
   *
   * @return the evaluated value when present
   */
  public Optional<Object> optionalValue() {
    return Optional.ofNullable(value);
  }

  /**
   * Reports whether evaluation completed without diagnostics, letting callers branch without
   * inspecting the warning list themselves.
   *
   * @return whether no warnings were emitted
   */
  public boolean isSuccess() {
    return warnings.isEmpty();
  }
}
