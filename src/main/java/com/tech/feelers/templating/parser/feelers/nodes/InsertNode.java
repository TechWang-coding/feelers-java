package com.tech.feelers.templating.parser.feelers.nodes;

import com.tech.feelers.templating.engine.FeelExpressionEngine;
import com.tech.feelers.templating.service.FeelEvaluatorService;
import com.tech.feelers.templating.parser.feelers.context.RenderContext;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Intent: represent an inline expression insertion tag.
 * Boundary: evaluate only this insertion expression; do not render surrounding structure.
 */
@Value
@Accessors(fluent = true)
public class InsertNode implements FeelersTemplateNode {
  private final String expression;
  private final int offset;

  @Override
  public String render(FeelExpressionEngine feel, RenderContext context, RenderOptions options) {
    return FeelEvaluatorService.stringify(feel, expression, context, options, offset);
  }
}
