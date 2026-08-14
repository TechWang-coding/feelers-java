package com.tech.feelers.templating.parser.feelers;

import com.tech.feelers.templating.entity.BlockResult;
import com.tech.feelers.templating.parser.feelers.context.ParseContext;
import org.apache.commons.lang3.StringUtils;

/**
 * Intent: provide shared tag matching, argument extraction, and nested-block parsing for directives.
 * Boundary: support directive strategies only; do not select a strategy or construct AST nodes.
 */
abstract class AbstractDirectiveParser implements DirectiveParser {
  protected final boolean hasPrefix(String directive, String prefix) {
    return StringUtils.startsWith(directive, prefix);
  }

  protected final String argumentAfter(String rawDirective, String prefix) {
    return StringUtils.substringAfter(rawDirective, prefix);
  }

  protected final BlockResult parseBlock(ParseContext context, String closingDirective) {
    context.consumeNewline();
    return context.parseBlock(closingDirective);
  }

  protected final String insertionExpression(String rawDirective) {
    String leftStripped = StringUtils.stripStart(rawDirective, null);
    return StringUtils.startsWith(leftStripped, "=")
        ? StringUtils.substring(leftStripped, 1) : rawDirective;
  }
}
