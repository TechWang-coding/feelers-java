package com.tech.feelers.templating.model;

import java.util.function.UnaryOperator;

/**
 * Intent: collect one evaluation's optional policies and align them with FEELers options.
 * Boundary: control strict mode, debug output, and the output sanitizer; do not configure FEEL
 * function security or template parsing.
 */
public record RenderOptions(boolean strict, boolean debug, UnaryOperator<String> sanitizer) {
  public static final RenderOptions DEFAULT = new RenderOptions(false, false, null);
}
