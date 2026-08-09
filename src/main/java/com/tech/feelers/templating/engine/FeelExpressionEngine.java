package com.tech.feelers.templating.engine;

import java.util.Map;

/**
 * Intent: define the template module's FEEL engine contract and isolate the Camunda API.
 * Boundary: evaluate a complete FEEL expression against variables only; do not handle template
 * tags, result formatting, or rendering policy.
 */
public interface FeelExpressionEngine {
  Object evaluate(String expression, Map<String, Object> variables);
}
