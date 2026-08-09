package io.github.templatingsyntax.feelers;

import java.util.Map;

import org.camunda.feel.api.EvaluationResult;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;

/** Camunda FEEL Scala adapter. External functions are deliberately disabled. */
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
