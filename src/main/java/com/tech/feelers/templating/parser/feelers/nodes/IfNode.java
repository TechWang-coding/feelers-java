package com.tech.feelers.templating.parser.feelers.nodes;

import com.tech.feelers.templating.engine.FeelExpressionEngine;
import com.tech.feelers.templating.parser.feelers.context.RenderContext;
import com.tech.feelers.templating.service.FeelEvaluatorService;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Intent: represent a conditional template block.
 * Boundary: evaluate the condition and render its body; do not create a child scope.
 */
@Value
@Accessors(fluent = true)
public class IfNode implements FeelersTemplateNode {
  private final String condition;
  private final BlockNode body;
  private final boolean closeHadNewline;
  private final int offset;

  @Override
  public String render(FeelExpressionEngine feel, RenderContext context, RenderOptions options) {
    Object value = FeelEvaluatorService.evaluate(feel, condition, context, options, offset);
    if (options.strict() && !(value instanceof Boolean)) {
      return FeelEvaluatorService.error(
          "FEEL expression " + condition + " expected to evaluate to a boolean", offset, options);
    }
    if (value == null || Boolean.FALSE.equals(value)) {
      return "";
    }
    String rendered = body.render(feel, context, options);
    return closeHadNewline && !rendered.endsWith("\n") ? rendered + "\n" : rendered;
  }
}
