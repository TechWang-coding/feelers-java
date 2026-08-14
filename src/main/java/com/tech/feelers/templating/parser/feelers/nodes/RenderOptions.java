package com.tech.feelers.templating.parser.feelers.nodes;

import java.util.function.UnaryOperator;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Intent: collect one evaluation's optional policies and align them with FEELers options.
 * Boundary: control strict mode, debug output, and the output sanitizer; do not configure FEEL
 * function security or template parsing.
 */
@Value
@Accessors(fluent = true)
public class RenderOptions {
  public static final RenderOptions DEFAULT = new RenderOptions(false, false, null);

  private final boolean strict;
  private final boolean debug;
  private final UnaryOperator<String> sanitizer;

  public RenderOptions(boolean strict, boolean debug, UnaryOperator<String> sanitizer) {
    this.strict = strict;
    this.debug = debug;
    this.sanitizer = sanitizer;
  }
}
