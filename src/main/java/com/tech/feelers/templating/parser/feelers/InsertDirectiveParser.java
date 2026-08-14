package com.tech.feelers.templating.parser.feelers;

import com.tech.feelers.templating.parser.feelers.context.ParseContext;
import com.tech.feelers.templating.parser.feelers.nodes.FeelersTemplateNode;
import com.tech.feelers.templating.parser.feelers.nodes.InsertNode;
import org.apache.commons.lang3.StringUtils;

/**
 * Intent: parse a non-block inline insertion directive.
 * Boundary: recognize and construct {@link InsertNode} only; do not evaluate FEEL.
 */
public final class InsertDirectiveParser extends AbstractDirectiveParser {
  @Override
  public boolean supports(String directive) {
    return StringUtils.isNotEmpty(directive) && !"=".equals(directive);
  }

  @Override
  public FeelersTemplateNode parse(ParseContext context, String rawDirective, int offset) {
    return new InsertNode(insertionExpression(rawDirective), offset);
  }
}
