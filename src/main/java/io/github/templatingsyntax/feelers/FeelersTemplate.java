package io.github.templatingsyntax.feelers;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Java renderer for the FEELers template shell. FEEL expressions are delegated
 * to {@link CamundaFeelExpressionEngine}, the Java engine used by Camunda 8.
 */
public final class FeelersTemplate implements TemplateRenderer {
  private final FeelExpressionEngine feel;

  public FeelersTemplate() {
    this(new CamundaFeelExpressionEngine());
  }

  public FeelersTemplate(FeelExpressionEngine feel) {
    this.feel = Objects.requireNonNull(feel, "feel");
  }

  public static String evaluate(String template, Map<String, Object> model) {
    return new FeelersTemplate().render(template, model);
  }

  @Override
  public String render(String template, Map<String, Object> model, RenderOptions options) {
    Objects.requireNonNull(template, "template");
    RenderOptions effectiveOptions = options == null ? RenderOptions.DEFAULT : options;
    TemplateNode root = new TemplateParser(template).parse();
    return render(root, RenderContext.root(model == null ? Map.of() : model), effectiveOptions);
  }

  private String render(TemplateNode node, RenderContext context, RenderOptions options) {
    if (node instanceof TextNode text) return text.value;
    if (node instanceof InsertNode insert) return stringify(insert.expression, context, options, insert.offset);
    if (node instanceof TopLevelFeelNode expression) return stringify(expression.expression, context, options, expression.offset);
    if (node instanceof BlockNode block) {
      StringBuilder rendered = new StringBuilder();
      for (TemplateNode child : block.children) rendered.append(render(child, context, options));
      return rendered.toString();
    }
    if (node instanceof IfNode conditional) {
      Object value = evaluate(conditional.condition, context, options, conditional.offset);
      if (options.strict() && !(value instanceof Boolean)) {
        return error("FEEL expression " + conditional.condition + " expected to evaluate to a boolean", conditional.offset, options);
      }
      if (!truthy(value)) return "";
      String result = render(conditional.body, context, options);
      return conditional.closeHadNewline && !result.endsWith("\n") ? result + "\n" : result;
    }
    if (node instanceof LoopNode loop) {
      Object value = evaluate(loop.collection, context, options, loop.offset);
      if (options.strict() && !isCollectionLike(value)) {
        return error("FEEL expression " + loop.collection + " expected to evaluate to an array", loop.offset, options);
      }
      List<Object> items = toItems(value, loop.collection, loop.offset, options);
      StringBuilder result = new StringBuilder();
      for (Object item : items) result.append(render(loop.body, context.child(item), options));
      return loop.closeHadNewline && !result.toString().endsWith("\n") ? result + "\n" : result.toString();
    }
    throw new IllegalStateException("Unknown template node " + node);
  }

  private String stringify(String expression, RenderContext context, RenderOptions options, int offset) {
    try {
      Object value = feel.evaluate("string(" + expression + ")", context.variables);
      String result = value == null ? "null" : value.toString();
      return options.sanitizer() == null ? result : options.sanitizer().apply(result);
    } catch (RuntimeException exception) {
      if (options.debug()) return "{{ feel expression " + expression + " couldn't be evaluated }}";
      throw new TemplateException("FEEL expression " + expression + " couldn't be evaluated", offset, exception);
    }
  }

  private Object evaluate(String expression, RenderContext context, RenderOptions options, int offset) {
    try {
      return feel.evaluate(expression, context.variables);
    } catch (RuntimeException exception) {
      if (options.debug()) return "{{ feel expression " + expression + " couldn't be evaluated }}";
      throw new TemplateException("FEEL expression " + expression + " couldn't be evaluated", offset, exception);
    }
  }

  private String error(String message, int offset, RenderOptions options) {
    if (options.debug()) return "{{ " + message.toLowerCase() + " }}";
    throw new TemplateException(message, offset);
  }

  private List<Object> toItems(Object value, String expression, int offset, RenderOptions options) {
    if (value instanceof Collection<?> collection) return new ArrayList<>(collection);
    if (value != null && value.getClass().isArray()) {
      int length = Array.getLength(value);
      List<Object> result = new ArrayList<>(length);
      for (int i = 0; i < length; i++) result.add(Array.get(value, i));
      return result;
    }
    return value == null ? List.of() : List.of(value);
  }

  private static boolean isCollectionLike(Object value) {
    return value instanceof Collection<?> || (value != null && value.getClass().isArray());
  }

  private static boolean truthy(Object value) {
    return !(value == null || Boolean.FALSE.equals(value));
  }

  private static final class RenderContext {
    private final Map<String, Object> variables;

    private RenderContext(Map<String, Object> variables) {
      this.variables = variables;
    }

    static RenderContext root(Map<String, Object> model) {
      return build(model, null, model);
    }

    RenderContext child(Object item) {
      return build(item instanceof Map<?, ?> map ? map : Map.of(), variables, item);
    }

    private static RenderContext build(Map<?, ?> values, Map<String, Object> parent, Object thisValue) {
      Map<String, Object> result = new HashMap<>();
      result.put("this", thisValue);
      result.put("parent", parent);
      values.forEach((key, value) -> result.put(String.valueOf(key), value));
      result.put("_this_", thisValue);
      result.put("_parent_", parent);
      // Root contexts contain parent=null; Map.copyOf rejects null values.
      return new RenderContext(Collections.unmodifiableMap(new HashMap<>(result)));
    }
  }

  private sealed interface TemplateNode permits TextNode, InsertNode, TopLevelFeelNode, BlockNode, IfNode, LoopNode { }
  private record TextNode(String value) implements TemplateNode { }
  private record InsertNode(String expression, int offset) implements TemplateNode { }
  private record TopLevelFeelNode(String expression, int offset) implements TemplateNode { }
  private record BlockNode(List<TemplateNode> children) implements TemplateNode { }
  private record IfNode(String condition, BlockNode body, boolean closeHadNewline, int offset) implements TemplateNode { }
  private record LoopNode(String collection, BlockNode body, boolean closeHadNewline, int offset) implements TemplateNode { }

  /** Recursive-descent parser for FEELers blocks; FEEL expressions stay opaque. */
  private static final class TemplateParser {
    private final String source;
    private int position;

    TemplateParser(String source) { this.source = source; }

    TemplateNode parse() {
      if (source.startsWith("=")) return new TopLevelFeelNode(source.substring(1), 0);
      return parseBlock(null).body;
    }

    private ParsedBlock parseBlock(String expectedClose) {
      List<TemplateNode> nodes = new ArrayList<>();
      while (position < source.length()) {
        int open = source.indexOf("{{", position);
        if (open < 0) {
          nodes.add(new TextNode(source.substring(position)));
          position = source.length();
          break;
        }
        if (open > position) nodes.add(new TextNode(source.substring(position, open)));
        int close = source.indexOf("}}", open + 2);
        if (close < 0) throw syntax("Unclosed template tag", open);
        String rawDirective = source.substring(open + 2, close);
        String directive = rawDirective.trim();
        position = close + 2;
        if (directive.startsWith("/")) {
          String name = directive.substring(1).trim();
          boolean newline = consumeNewline();
          if (expectedClose == null || !expectedClose.equals(name)) throw syntax("Unexpected closing {{/" + name + "}}", open);
          return new ParsedBlock(new BlockNode(List.copyOf(nodes)), newline);
        }
        if (directive.startsWith("#if ")) {
          consumeNewline();
          ParsedBlock body = parseBlock("if");
          nodes.add(new IfNode(rawDirective.substring(rawDirective.indexOf("#if ") + 4), body.body, body.closeHadNewline, open));
        } else if (directive.startsWith("#loop ")) {
          consumeNewline();
          ParsedBlock body = parseBlock("loop");
          nodes.add(new LoopNode(rawDirective.substring(rawDirective.indexOf("#loop ") + 6), body.body, body.closeHadNewline, open));
        } else if (!directive.isEmpty() && !directive.equals("=")) {
          nodes.add(new InsertNode(rawDirective.stripLeading().startsWith("=")
              ? rawDirective.substring(rawDirective.indexOf('=') + 1)
              : rawDirective, open));
        }
      }
      if (expectedClose != null) throw syntax("Missing closing {{/" + expectedClose + "}}", position);
      return new ParsedBlock(new BlockNode(List.copyOf(nodes)), false);
    }

    private boolean consumeNewline() {
      if (position < source.length() && source.charAt(position) == '\n') { position++; return true; }
      return false;
    }

    private static TemplateException syntax(String message, int offset) {
      return new TemplateException(message, offset);
    }

    private record ParsedBlock(BlockNode body, boolean closeHadNewline) { }
  }
}
