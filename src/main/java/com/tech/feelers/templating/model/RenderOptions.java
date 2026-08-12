package com.tech.feelers.templating.model;

import java.util.function.UnaryOperator;
import java.util.Objects;

/**
 * Intent: collect one evaluation's optional policies and align them with FEELers options.
 * Boundary: control strict mode, debug output, and the output sanitizer; do not configure FEEL
 * function security or template parsing.
 */
public final class RenderOptions {
  public static final RenderOptions DEFAULT = new RenderOptions(false, false, null);

  private final boolean strict;
  private final boolean debug;
  private final UnaryOperator<String> sanitizer;

  public RenderOptions(boolean strict, boolean debug, UnaryOperator<String> sanitizer) {
    this.strict = strict;
    this.debug = debug;
    this.sanitizer = sanitizer;
  }

  public boolean strict() { return strict; }
  public boolean debug() { return debug; }
  public UnaryOperator<String> sanitizer() { return sanitizer; }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof RenderOptions)) return false;
    RenderOptions that = (RenderOptions) other;
    return strict == that.strict && debug == that.debug && Objects.equals(sanitizer, that.sanitizer);
  }

  @Override
  public int hashCode() { return Objects.hash(strict, debug, sanitizer); }

  @Override
  public String toString() {
    return "RenderOptions[strict=" + strict + ", debug=" + debug + ", sanitizer=" + sanitizer + "]";
  }
}
