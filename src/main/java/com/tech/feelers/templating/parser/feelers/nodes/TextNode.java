package com.tech.feelers.templating.parser.feelers.nodes;

import com.tech.feelers.templating.engine.FeelExpressionEngine;
import com.tech.feelers.templating.parser.feelers.context.RenderContext;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Intent: represent literal template text.
 * Boundary: emit its value unchanged without FEEL evaluation.
 */
@Value
@Accessors(fluent = true)
public class TextNode implements FeelersTemplateNode {
  private final String value;

  @Override
  public String render(FeelExpressionEngine feel, RenderContext context, RenderOptions options) {
    return value;
  }
}
