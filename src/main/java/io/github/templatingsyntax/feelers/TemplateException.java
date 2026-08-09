package io.github.templatingsyntax.feelers;

/** Template syntax, FEEL evaluation, or rendering failure with source location. */
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
