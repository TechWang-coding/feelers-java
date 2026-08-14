package com.tech.feelers.templating.entity;

import com.tech.feelers.templating.parser.feelers.nodes.BlockNode;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Intent: transfer a parsed block and the newline state of its closing tag.
 * Boundary: coordinate recursive parsing only; do not represent a template AST node.
 */
@Value
@Accessors(fluent = true)
public class BlockResult {
  private final BlockNode body;
  private final boolean closeHadNewline;
}
