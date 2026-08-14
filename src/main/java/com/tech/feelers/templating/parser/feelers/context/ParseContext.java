package com.tech.feelers.templating.parser.feelers.context;

import com.tech.feelers.templating.entity.BlockResult;
import com.tech.feelers.templating.parser.FeelersTemplateParser;

/**
 * Intent: expose the parser operations needed by block directive strategies.
 * Boundary: provide recursive block parsing and newline consumption only; source scanning remains
 * owned by {@link FeelersTemplateParser}.
 */
public interface ParseContext {
  BlockResult parseBlock(String expectedClose);

  void consumeNewline();
}
