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

  /**
   * Scan from the current cursor until the expected closing tag or end of source, then build one
   * {@link Block}. The recursive scan builds the AST as follows:
   *
   * <pre>
   * character stream
   *   ├─ plain text        -> Text
   *   ├─ {{ expression }}  -> Insert strategy -> Insert
   *   ├─ {{#if ...}}       -> If strategy -> parseBlock("if") -> If(body)
   *   ├─ {{#loop ...}}     -> Loop strategy -> parseBlock("loop") -> Loop(body)
   *   └─ {{/if}} / {{/loop}} -> return BlockResult to the matching recursive caller
   * </pre>
   *
   * Closing tags remain parser control flow because they determine where a recursive block ends.
   */
  private BlockResult parseBlock(String expectedClose) {
    List<TemplateNode> nodes = new ArrayList<>();
    while (position < source.length()) {
      // Find the next template-tag opening delimiter from the current cursor.
      int open = source.indexOf("{{", position);
      if (open < 0) {
        // No more tags: the remaining source belongs to this block as literal text.
        nodes.add(new TemplateNode.Text(source.substring(position)));
        position = source.length();
        break;
      }
      // Text preceding a tag is emitted as its own leaf node.
      if (open > position) nodes.add(new TemplateNode.Text(source.substring(position, open)));

      // Locate the matching end delimiter for the current tag.
      int close = source.indexOf("}}", open + 2);
      if (close < 0) throw syntax("Unclosed template tag", open);

      // Preserve raw expression whitespace while using the trimmed form for strategy routing.
      String rawDirective = source.substring(open + 2, close);
      String directive = rawDirective.trim();

      // Advance before recursion so nested parsing resumes immediately after this opening tag.
      position = close + 2;
      if (directive.startsWith("/")) {
        String name = directive.substring(1).trim();
        // Record the closing-tag newline so rendering can reproduce FEELers newline behavior.
        boolean newline = consumeNewline();
        // A close tag completes only the recursive block that requested the same tag name.
        if (expectedClose == null || !expectedClose.equals(name)) throw syntax("Unexpected closing {{/" + name + "}}", open);
        return new BlockResult(new Block(nodes), newline);
      }

      // If, Loop, and Insert select their own parsing strategy and construct their AST node.
      TemplateNode node = TemplateNode.parseDirective(parseContext, rawDirective, directive, open);
      if (node != null) nodes.add(node);
    }
    // End of source is valid only for the root block; nested blocks require their closing tag.
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
