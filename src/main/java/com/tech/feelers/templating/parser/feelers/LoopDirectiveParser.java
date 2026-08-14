package com.tech.feelers.templating.parser.feelers;

import com.tech.feelers.templating.entity.BlockResult;
import com.tech.feelers.templating.parser.feelers.context.ParseContext;
import com.tech.feelers.templating.parser.feelers.nodes.FeelersTemplateNode;
import com.tech.feelers.templating.parser.feelers.nodes.LoopNode;

/**
 * Intent: parse a {@code #loop} opening directive and its nested block.
 * Boundary: build {@link LoopNode}; closing-tag matching remains owned by {@link FeelersTemplateParser}.
 */
public final class LoopDirectiveParser extends AbstractDirectiveParser {
  @Override
  public boolean supports(String directive) {
    return hasPrefix(directive, "#loop ");
  }

  @Override
  public FeelersTemplateNode parse(ParseContext context, String rawDirective, int offset) {
    BlockResult body = parseBlock(context, "loop");
    return new LoopNode(argumentAfter(rawDirective, "#loop "), body.body(),
        body.closeHadNewline(), offset);
  }
}
