package com.tech.feelers.templating.parser;

import static com.tech.feelers.templating.parser.TemplateNode.Block;
import static com.tech.feelers.templating.parser.TemplateNode.BlockResult;

import com.tech.feelers.templating.exception.TemplateException;
import java.util.ArrayList;
import java.util.List;

/**
 * Intent: parse FEELers text, insertions, conditional tags, and loop tags with nested blocks.
 * Boundary: produce {@link TemplateNode} values and syntax offsets only; retain FEEL expression
 * source without validating or evaluating it.
 */
public final class TemplateParser {
  private final String source;
  private int position;
  private final TemplateNode.ParseContext parseContext = new TemplateNode.ParseContext() {
    @Override public BlockResult parseBlock(String expectedClose) {
      return TemplateParser.this.parseBlock(expectedClose);
    }

    @Override public void consumeNewline() {
      TemplateParser.this.consumeNewline();
    }
  };

  public TemplateParser(String source) {
    this.source = source;
  }

  public TemplateNode parse() {
    if (source.startsWith("=")) return new TemplateNode.TopLevelFeel(source.substring(1), 0);
    return parseBlock(null).body();
  }

  private BlockResult parseBlock(String expectedClose) {
    List<TemplateNode> nodes = new ArrayList<>();
    while (position < source.length()) {
      int open = source.indexOf("{{", position);
      if (open < 0) {
        nodes.add(new TemplateNode.Text(source.substring(position)));
        position = source.length();
        break;
      }
      if (open > position) nodes.add(new TemplateNode.Text(source.substring(position, open)));
      int close = source.indexOf("}}", open + 2);
      if (close < 0) throw syntax("Unclosed template tag", open);
      String rawDirective = source.substring(open + 2, close);
      String directive = rawDirective.trim();
      position = close + 2;
      if (directive.startsWith("/")) {
        String name = directive.substring(1).trim();
        boolean newline = consumeNewline();
        if (expectedClose == null || !expectedClose.equals(name)) throw syntax("Unexpected closing {{/" + name + "}}", open);
        return new BlockResult(new Block(nodes), newline);
      }
      TemplateNode node = TemplateNode.parseDirective(parseContext, rawDirective, directive, open);
      if (node != null) nodes.add(node);
    }
    if (expectedClose != null) throw syntax("Missing closing {{/" + expectedClose + "}}", position);
    return new BlockResult(new Block(nodes), false);
  }

  private boolean consumeNewline() {
    if (position < source.length() && source.charAt(position) == '\n') { position++; return true; }
    return false;
  }

  private static TemplateException syntax(String message, int offset) {
    return new TemplateException(message, offset);
  }

}
