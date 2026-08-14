package com.tech.feelers.templating.parser.feelers.nodes;

import com.google.common.collect.ImmutableList;
import com.tech.feelers.templating.engine.FeelExpressionEngine;
import com.tech.feelers.templating.parser.feelers.context.RenderContext;
import java.util.List;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Intent: hold the ordered children of a template root, conditional, or loop.
 * Boundary: render children in order only; do not alter their scope or evaluate expressions.
 */
@Value
@Accessors(fluent = true)
public class BlockNode implements FeelersTemplateNode {
  private final List<FeelersTemplateNode> children;

  public BlockNode(List<FeelersTemplateNode> children) {
    this.children = ImmutableList.copyOf(children);
  }

  @Override
  public String render(FeelExpressionEngine feel, RenderContext context, RenderOptions options) {
    StringBuilder rendered = new StringBuilder();
    for (FeelersTemplateNode child : children) {
      rendered.append(child.render(feel, context, options));
    }
    return rendered.toString();
  }
}
