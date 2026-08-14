package com.tech.feelers.templating.parser.feelers;

import com.tech.feelers.templating.entity.BlockResult;
import com.tech.feelers.templating.parser.feelers.context.ParseContext;
import com.tech.feelers.templating.parser.feelers.nodes.FeelersTemplateNode;
import com.tech.feelers.templating.parser.feelers.nodes.IfNode;

/**
 * Intent: parse an {@code #if} opening directive and its nested block.
 * Boundary: build {@link IfNode}; closing-tag matching remains owned by {@link FeelersTemplateParser}.
 */
public final class IfDirectiveParser extends AbstractDirectiveParser {
  @Override
  public boolean supports(String directive) {
    return hasPrefix(directive, "#if ");
  }

  @Override
  public FeelersTemplateNode parse(ParseContext context, String rawDirective, int offset) {
    BlockResult body = parseBlock(context, "if");
    return new IfNode(argumentAfter(rawDirective, "#if "), body.body(),
        body.closeHadNewline(), offset);
  }
}
