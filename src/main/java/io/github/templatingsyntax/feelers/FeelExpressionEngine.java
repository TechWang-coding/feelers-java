package io.github.templatingsyntax.feelers;

import java.util.Map;

/** Boundary between template rendering and the concrete FEEL runtime. */
public interface FeelExpressionEngine {
  Object evaluate(String expression, Map<String, Object> variables);
}
