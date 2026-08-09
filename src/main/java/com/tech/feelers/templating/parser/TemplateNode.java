package com.tech.feelers.templating.parser;

import java.util.List;

/**
 * Intent: represent the FEELers template shell as an immutable AST and separate parsing from
 * evaluation.
 * Boundary: retain tag structure, expression source, and offsets; do not validate or evaluate
 * FEEL, or decide output content.
 */
public sealed interface TemplateNode permits TemplateNode.Text, TemplateNode.Insert,
    TemplateNode.TopLevelFeel, TemplateNode.Block, TemplateNode.If, TemplateNode.Loop {

  /** Intent: represent literal text. Boundary: emit it unchanged without FEEL evaluation. */
  record Text(String value) implements TemplateNode { }

  /** Intent: represent an inline insertion tag. Boundary: retain the expression only. */
  record Insert(String expression, int offset) implements TemplateNode { }

  /** Intent: represent a top-level FEEL expression prefixed with {@code =}. Boundary: retain source and offset only. */
  record TopLevelFeel(String expression, int offset) implements TemplateNode { }

  /** Intent: hold ordered children for roots, conditions, and loops. Boundary: describe structure only. */
  record Block(List<TemplateNode> children) implements TemplateNode { }

  /** Intent: represent a conditional block. Boundary: retain its condition and body only. */
  record If(String condition, Block body, boolean closeHadNewline, int offset) implements TemplateNode { }

  /** Intent: represent a loop block. Boundary: retain its collection expression and body only. */
  record Loop(String collection, Block body, boolean closeHadNewline, int offset) implements TemplateNode { }
}
