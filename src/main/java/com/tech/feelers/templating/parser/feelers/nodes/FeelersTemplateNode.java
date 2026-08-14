package com.tech.feelers.templating.parser.feelers.nodes;

import com.tech.feelers.templating.engine.FeelExpressionEngine;
import com.tech.feelers.templating.parser.feelers.context.RenderContext;

/**
 * Intent: define the rendering contract for immutable FEELers template AST nodes.
 * Boundary: declare node rendering only; FEEL evaluation belongs to {@code FeelEvaluatorService}.
 */
public interface FeelersTemplateNode {
  String render(FeelExpressionEngine feel, RenderContext context, RenderOptions options);
}
