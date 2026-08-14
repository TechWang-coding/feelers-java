package com.tech.feelers.templating.parser.feelers;

import com.tech.feelers.templating.parser.feelers.context.ParseContext;
import com.tech.feelers.templating.parser.feelers.nodes.FeelersTemplateNode;

/**
 * Intent: define one template directive parsing strategy.
 * Boundary: construct a node from an already scanned tag; do not scan template source.
 */
public interface DirectiveParser {
  boolean supports(String directive);

  FeelersTemplateNode parse(ParseContext context, String rawDirective, int offset);
}
