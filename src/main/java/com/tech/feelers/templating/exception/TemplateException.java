package com.tech.feelers.templating.exception;

/**
 * Intent: provide one template-domain exception without exposing parser or Camunda exception types.
 * Boundary: retain a message, template offset, and optional cause; do not model engine-specific
 * diagnostics.
 */
public final class TemplateException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final int offset;

  public TemplateException(String message, int offset) {
    super(message);
    this.offset = offset;
  }

  public TemplateException(String message, int offset, Throwable cause) {
    super(message, cause);
    this.offset = offset;
  }

  public int offset() {
    return offset;
  }
}
