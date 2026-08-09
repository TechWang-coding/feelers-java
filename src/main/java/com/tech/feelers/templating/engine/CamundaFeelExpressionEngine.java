package com.tech.feelers.templating.engine;

import java.util.Map;

import org.camunda.feel.api.EvaluationResult;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;

/**
 * Intent: implement {@link FeelExpressionEngine} with Camunda FEEL Scala Engine for consistent
 * backend expression semantics.
 * Boundary: create and invoke the Camunda engine with external functions disabled; do not handle
 * template syntax, variable scopes, or output formatting.
 */
public final class CamundaFeelExpressionEngine implements FeelExpressionEngine {
  private final FeelEngineApi engine;

  public CamundaFeelExpressionEngine() {
    engine = FeelEngineBuilder.forJava()
        .withEnabledExternalFunctions(false)
        .build();
  }

  @Override
  public Object evaluate(String expression, Map<String, Object> variables) {
    EvaluationResult result = engine.evaluateExpression(expression, variables);
    if (result.isSuccess()) {
      return result.result();
    }
    throw new IllegalArgumentException(result.failure().message());
  }
}
