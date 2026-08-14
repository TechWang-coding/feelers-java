package com.tech.feelers.templating.parser;

import com.google.common.collect.ImmutableList;
import com.tech.feelers.templating.entity.BlockResult;
import com.tech.feelers.templating.exception.TemplateException;
import com.tech.feelers.templating.parser.feelers.DirectiveParser;
import com.tech.feelers.templating.parser.feelers.IfDirectiveParser;
import com.tech.feelers.templating.parser.feelers.InsertDirectiveParser;
import com.tech.feelers.templating.parser.feelers.LoopDirectiveParser;
import com.tech.feelers.templating.parser.feelers.context.FeelersTemplateParseContext;
import com.tech.feelers.templating.parser.feelers.context.ParseContext;
import com.tech.feelers.templating.parser.feelers.nodes.BlockNode;
import com.tech.feelers.templating.parser.feelers.nodes.FeelersTemplateNode;
import com.tech.feelers.templating.parser.feelers.nodes.TextNode;
import com.tech.feelers.templating.parser.feelers.nodes.TopLevelFeelNode;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Intent: parse FEELers text, insertions, conditional tags, and loop tags with nested blocks.
 * Boundary: produce {@link FeelersTemplateNode} values and syntax offsets only; retain FEEL expression
 * source without validating or evaluating it.
 */
public final class FeelersTemplateParser {
  private static final List<DirectiveParser> DIRECTIVE_PARSERS = ImmutableList.of(
      new IfDirectiveParser(), new LoopDirectiveParser(), new InsertDirectiveParser());

  private final String source;
  private int position;
  private final ParseContext parseContext = new FeelersTemplateParseContext(this);

  public FeelersTemplateParser(String source) {
    this.source = source;
  }

  public FeelersTemplateNode parse() {
    if (StringUtils.startsWith(source, "=")) return new TopLevelFeelNode(source.substring(1), 0);
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
  public BlockResult parseBlock(String expectedClose) {
    List<FeelersTemplateNode> nodes = new ArrayList<>();
    while (position < source.length()) {
      // Find the next template-tag opening delimiter from the current cursor.
      int open = source.indexOf("{{", position);
      if (open < 0) {
        // No more tags: the remaining source belongs to this block as literal text.
        nodes.add(new TextNode(source.substring(position)));
        position = source.length();
        break;
      }
      // Text preceding a tag is emitted as its own leaf node.
      if (open > position) nodes.add(new TextNode(source.substring(position, open)));

      // Locate the matching end delimiter for the current tag.
      int close = source.indexOf("}}", open + 2);
      if (close < 0) throw syntax("Unclosed template tag", open);

      // Preserve raw expression whitespace while using the trimmed form for strategy routing.
      String rawDirective = source.substring(open + 2, close);
      String directive = StringUtils.trim(rawDirective);

      // Advance before recursion so nested parsing resumes immediately after this opening tag.
      position = close + 2;
      if (StringUtils.startsWith(directive, "/")) {
        String name = StringUtils.trim(directive.substring(1));
        // Record the closing-tag newline so rendering can reproduce FEELers newline behavior.
        boolean newline = consumeNewline();
        // A close tag completes only the recursive block that requested the same tag name.
        if (expectedClose == null || !expectedClose.equals(name)) throw syntax("Unexpected closing {{/" + name + "}}", open);
        return new BlockResult(new BlockNode(nodes), newline);
      }

      // If, Loop, and Insert select their own parsing strategy and construct their AST node.
      FeelersTemplateNode node = parseDirective(rawDirective, directive, open);
      if (node != null) nodes.add(node);
    }
    // End of source is valid only for the root block; nested blocks require their closing tag.
    if (expectedClose != null) throw syntax("Missing closing {{/" + expectedClose + "}}", position);
    return new BlockResult(new BlockNode(nodes), false);
  }

  private FeelersTemplateNode parseDirective(String rawDirective, String directive, int offset) {
    for (DirectiveParser parser : DIRECTIVE_PARSERS) {
      if (parser.supports(directive)) {
        return parser.parse(parseContext, rawDirective, offset);
      }
    }
    return null;
  }

  public boolean consumeNewline() {
    if (position < source.length() && source.charAt(position) == '\n') { position++; return true; }
    return false;
  }

  private static TemplateException syntax(String message, int offset) {
    return new TemplateException(message, offset);
  }

}
