package io.github.templatingsyntax.feelers;

import java.util.function.UnaryOperator;

/** Rendering policy compatible with the front-end feelers options. */
public record RenderOptions(boolean strict, boolean debug, UnaryOperator<String> sanitizer) {
  public static final RenderOptions DEFAULT = new RenderOptions(false, false, null);
}
