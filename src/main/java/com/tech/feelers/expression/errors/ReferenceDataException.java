package com.tech.feelers.expression.errors;

/**
 * Raised when a reference-data lookup cannot produce a definite answer.
 *
 * <p>A lookup that completes and reports "value not found" is a normal {@code false} result, not an
 * exception. This type is reserved for the cases where the question was never answered, so callers
 * can report them separately from a failed business rule.
 */
public final class ReferenceDataException extends RuntimeException {

  /** Why the lookup could not answer, which determines the reported error category. */
  public enum Reason {
    /** The lookup exceeded its time budget. */
    TIMEOUT,
    /** The data source rejected the request or was unreachable. */
    UNAVAILABLE,
    /** The field exists but declares no data source, which is a form configuration defect. */
    NOT_CONFIGURED
  }

  private final Reason reason;

  /**
   * Creates a lookup failure carrying the reason needed to pick an error category.
   *
   * @param reason why the lookup could not answer
   * @param message safe diagnostic text; must not contain the looked-up value
   */
  public ReferenceDataException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  /**
   * Creates a lookup failure that preserves the underlying transport failure.
   *
   * @param reason why the lookup could not answer
   * @param message safe diagnostic text; must not contain the looked-up value
   * @param cause original transport or parsing failure
   */
  public ReferenceDataException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  /**
   * Exposes the reason so the validation layer can map it to an error category without inspecting
   * the message.
   *
   * @return the lookup failure reason
   */
  public Reason reason() {
    return reason;
  }
}
