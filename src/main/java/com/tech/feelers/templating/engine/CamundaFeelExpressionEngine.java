package com.tech.feelers.templating.engine;

import java.util.Map;

import org.camunda.feel.api.EvaluationResult;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;

/**
 * 设定意图：以 Camunda FEEL Scala Engine 实现 {@link FeelExpressionEngine}，统一后端表达式语义。
 * 作用边界：负责创建并调用 Camunda 引擎，禁用外部函数；不处理模板语法、作用域和输出格式。
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
