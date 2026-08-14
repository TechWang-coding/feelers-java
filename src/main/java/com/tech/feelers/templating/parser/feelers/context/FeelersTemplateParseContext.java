package com.tech.feelers.templating.parser.feelers.context;

import com.tech.feelers.templating.entity.BlockResult;
import com.tech.feelers.templating.parser.FeelersTemplateParser;

/**
 * Intent: adapt {@link FeelersTemplateParser} operations for block directive strategies.
 * Boundary: delegate parser recursion and newline consumption only; do not scan or build nodes.
 */
public final class FeelersTemplateParseContext implements ParseContext {
  private final FeelersTemplateParser parser;

  public FeelersTemplateParseContext(FeelersTemplateParser parser) {
    this.parser = parser;
  }

  @Override
  public BlockResult parseBlock(String expectedClose) {
    return parser.parseBlock(expectedClose);
  }

  @Override
  public void consumeNewline() {
    parser.consumeNewline();
  }
}
