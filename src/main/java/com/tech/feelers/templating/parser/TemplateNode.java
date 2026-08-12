package com.tech.feelers.templating.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Intent: represent the FEELers template shell as an immutable AST and separate parsing from
 * evaluation. Directive parsing uses a node-local strategy pattern: If, Loop, and Insert each
 * own the strategy that recognizes and constructs their directive, while TemplateParser scans the
 * source, routes directives, and controls recursive block completion.
 * Boundary: retain tag structure, expression source, and offsets; do not validate or evaluate
 * FEEL, or decide output content.
 */
public interface TemplateNode {

  /**
   * Intent: provide node parsing strategies with the parser operations required for nested blocks.
   * Boundary: expose block recursion and newline consumption only; source scanning remains owned by
   * {@link TemplateParser}.
   */
  interface ParseContext {
    BlockResult parseBlock(String expectedClose);
    void consumeNewline();
  }

  /** Intent: return a parsed block with closing-tag newline state. Boundary: parser coordination only. */
  final class BlockResult {
    private final Block body;
    private final boolean closeHadNewline;

    public BlockResult(Block body, boolean closeHadNewline) {
      this.body = body;
      this.closeHadNewline = closeHadNewline;
    }

    public Block body() { return body; }
    public boolean closeHadNewline() { return closeHadNewline; }
  }

  /** Intent: define one directive parsing strategy. Boundary: construct a node after the tag is scanned. */
  interface DirectiveParser {
    boolean supports(String directive);
    TemplateNode parse(ParseContext context, String rawDirective, int offset);
  }

  /**
   * Select the node strategy for a non-closing directive.
   * Closing tags remain parser control flow because they determine recursive block completion.
   */
  static TemplateNode parseDirective(ParseContext context, String rawDirective, String directive, int offset) {
    for (DirectiveParser parser : DIRECTIVE_PARSERS) {
      if (parser.supports(directive)) return parser.parse(context, rawDirective, offset);
    }
    return null;
  }

  List<DirectiveParser> DIRECTIVE_PARSERS = Collections.unmodifiableList(Arrays.asList(
      If.DIRECTIVE_PARSER, Loop.DIRECTIVE_PARSER, Insert.DIRECTIVE_PARSER));

  /** Intent: represent literal text. Boundary: emit it unchanged without FEEL evaluation. */
  final class Text extends AbstractTemplateNode {
    private final String value;
    public Text(String value) { super(value); this.value = value; }
    public String value() { return value; }
  }

  /** Intent: represent an inline insertion tag. Boundary: retain the expression only. */
  final class Insert extends AbstractTemplateNode {
    private final String expression;
    private final int offset;
    public Insert(String expression, int offset) { super(expression, offset); this.expression = expression; this.offset = offset; }
    public String expression() { return expression; }
    public int offset() { return offset; }

    private static final DirectiveParser DIRECTIVE_PARSER = new DirectiveParser() {
      @Override public boolean supports(String directive) {
        return !directive.isEmpty() && !"=".equals(directive);
      }

      @Override public TemplateNode parse(ParseContext context, String rawDirective, int offset) {
        int expressionStart = firstNonWhitespace(rawDirective);
        String expression = expressionStart < rawDirective.length() && rawDirective.charAt(expressionStart) == '='
            ? rawDirective.substring(expressionStart + 1) : rawDirective;
        return new Insert(expression, offset);
      }
    };
  }

  /** Intent: represent a top-level FEEL expression prefixed with {@code =}. Boundary: retain source and offset only. */
  final class TopLevelFeel extends AbstractTemplateNode {
    private final String expression;
    private final int offset;
    public TopLevelFeel(String expression, int offset) { super(expression, offset); this.expression = expression; this.offset = offset; }
    public String expression() { return expression; }
    public int offset() { return offset; }
  }

  /** Intent: hold ordered children for roots, conditions, and loops. Boundary: describe structure only. */
  final class Block extends AbstractTemplateNode {
    private final List<TemplateNode> children;
    public Block(List<TemplateNode> children) {
      super(Collections.unmodifiableList(new ArrayList<TemplateNode>(children)));
      this.children = Collections.unmodifiableList(new ArrayList<TemplateNode>(children));
    }
    public List<TemplateNode> children() { return children; }
  }

  /** Intent: represent a conditional block. Boundary: retain its condition and body only. */
  final class If extends AbstractTemplateNode {
    private final String condition;
    private final Block body;
    private final boolean closeHadNewline;
    private final int offset;
    public If(String condition, Block body, boolean closeHadNewline, int offset) {
      super(condition, body, closeHadNewline, offset);
      this.condition = condition; this.body = body; this.closeHadNewline = closeHadNewline; this.offset = offset;
    }
    public String condition() { return condition; }
    public Block body() { return body; }
    public boolean closeHadNewline() { return closeHadNewline; }
    public int offset() { return offset; }

    private static final DirectiveParser DIRECTIVE_PARSER = new DirectiveParser() {
      @Override public boolean supports(String directive) { return directive.startsWith("#if "); }

      @Override public TemplateNode parse(ParseContext context, String rawDirective, int offset) {
        context.consumeNewline();
        BlockResult body = context.parseBlock("if");
        return new If(rawDirective.substring(rawDirective.indexOf("#if ") + 4),
            body.body(), body.closeHadNewline(), offset);
      }
    };
  }

  /** Intent: represent a loop block. Boundary: retain its collection expression and body only. */
  final class Loop extends AbstractTemplateNode {
    private final String collection;
    private final Block body;
    private final boolean closeHadNewline;
    private final int offset;
    public Loop(String collection, Block body, boolean closeHadNewline, int offset) {
      super(collection, body, closeHadNewline, offset);
      this.collection = collection; this.body = body; this.closeHadNewline = closeHadNewline; this.offset = offset;
    }
    public String collection() { return collection; }
    public Block body() { return body; }
    public boolean closeHadNewline() { return closeHadNewline; }
    public int offset() { return offset; }

    private static final DirectiveParser DIRECTIVE_PARSER = new DirectiveParser() {
      @Override public boolean supports(String directive) { return directive.startsWith("#loop "); }

      @Override public TemplateNode parse(ParseContext context, String rawDirective, int offset) {
        context.consumeNewline();
        BlockResult body = context.parseBlock("loop");
        return new Loop(rawDirective.substring(rawDirective.indexOf("#loop ") + 6),
            body.body(), body.closeHadNewline(), offset);
      }
    };
  }

  static int firstNonWhitespace(String value) {
    int index = 0;
    while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
    return index;
  }
}
