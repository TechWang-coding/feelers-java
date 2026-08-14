package com.tech.feelers.templating.parser.feelers.nodes;

import com.tech.feelers.templating.engine.FeelExpressionEngine;
import com.tech.feelers.templating.service.FeelEvaluatorService;
import com.tech.feelers.templating.parser.feelers.context.RenderContext;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Intent: represent a top-level FEEL expression prefixed with {@code =}.
 * Boundary: evaluate the complete template expression only; do not render child nodes.
 */
@Value
@Accessors(fluent = true)
public class TopLevelFeelNode implements FeelersTemplateNode {
  private final String expression;
  private final int offset;

  @Override
  public String render(FeelExpressionEngine feel, RenderContext context, RenderOptions options) {
    return FeelEvaluatorService.stringify(feel, expression, context, options, offset);
  }
}
