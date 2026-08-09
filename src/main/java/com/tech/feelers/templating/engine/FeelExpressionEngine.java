package com.tech.feelers.templating.engine;

import java.util.Map;

/**
 * 设定意图：定义模板模块访问 FEEL 引擎的统一接口，隔离 Camunda 具体 API。
 * 作用边界：只对完整 FEEL 表达式和变量上下文求值；不处理模板标签、结果格式化和渲染策略。
 */
public interface FeelExpressionEngine {
  Object evaluate(String expression, Map<String, Object> variables);
}
