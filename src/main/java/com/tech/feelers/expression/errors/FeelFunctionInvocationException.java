package com.tech.feelers.expression.errors;

/**
 * Raised when a registered host function fails during evaluation.
 *
 * <p>This type exists to separate a failure inside application-supplied code from a failure of the
 * expression runtime itself: the former is converted into an evaluation warning, while the latter
 * keeps propagating because it indicates an engine defect.
 */
public final class FeelFunctionInvocationException extends RuntimeException {

  private final String functionName;

  /**
   * Wraps the original failure while retaining the function name needed for diagnostics.
   *
   * @param functionName registered name of the function that failed
   * @param cause original failure thrown by the host function
   */
  public FeelFunctionInvocationException(String functionName, Throwable cause) {
    super("FEEL function \"" + functionName + "\" failed", cause);
    this.functionName = functionName;
  }

  /**
   * Exposes the failing function name so callers can classify and log the failure without parsing
   * the message text.
   *
   * @return registered function name
   */
  public String functionName() {
    return functionName;
  }
}
