package com.tech.feelers.templating.service;

import com.tech.feelers.templating.engine.CamundaFeelExpressionEngine;
import com.tech.feelers.templating.engine.FeelExpressionEngine;
import com.tech.feelers.templating.exception.TemplateException;
import com.tech.feelers.templating.model.RenderOptions;
import com.tech.feelers.templating.parser.TemplateNode;
import com.tech.feelers.templating.parser.TemplateParser;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Intent: provide a static template API aligned with front-end FEELers and coordinate parsing,
 * variable scoping, FEEL evaluation, and output policy.
 * Boundary: expose evaluate, parse, and parseToSimpleTree only; do not parse FEEL or expose
 * Camunda types.
 */
public final class FeelersTemplateService {
  private final FeelExpressionEngine feel;

  private FeelersTemplateService() {
    this(new CamundaFeelExpressionEngine());
  }

  private FeelersTemplateService(FeelExpressionEngine feel) {
    this.feel = Objects.requireNonNull(feel, "feel");
  }

  /** Equivalent to front-end evaluate(template, context) with default options. */
  public static String evaluate(String template, Map<String, Object> model) {
    return evaluate(template, model, RenderOptions.DEFAULT);
  }

  /** Equivalent to front-end evaluate(template, context, options). */
  public static String evaluate(String template, Map<String, Object> model, RenderOptions options) {
    return new FeelersTemplateService().evaluateTemplate(template, model, options);
  }

  /**
   * Equivalent to front-end parse(template): validate the template shell and return a Java AST.
   * Throws {@link TemplateException} when the template structure is invalid.
   */
  public static TemplateNode parse(String template) {
    Objects.requireNonNull(template, "template");
    return new TemplateParser(template).parse();
  }

  /**
   * Equivalent to front-end parseToSimpleTree(template).
   * The Java parser builds {@link TemplateNode} directly, so it has no separate raw-tree layer.
   */
  public static TemplateNode parseToSimpleTree(String template) {
    return parse(template);
  }

  private String evaluateTemplate(String template, Map<String, Object> model, RenderOptions options) {
    RenderOptions effectiveOptions = options == null ? RenderOptions.DEFAULT : options;
    TemplateNode root = parse(template);
    return renderNode(root, RenderContext.root(model == null ? Map.of() : model), effectiveOptions);
  }

  private String renderNode(TemplateNode node, RenderContext context, RenderOptions options) {
    if (node instanceof TemplateNode.Text) {
      TemplateNode.Text text = (TemplateNode.Text) node;
      return text.value();
    }
    if (node instanceof TemplateNode.Insert) {
      TemplateNode.Insert insert = (TemplateNode.Insert) node;
      return stringify(insert.expression(), context, options, insert.offset());
    }
    if (node instanceof TemplateNode.TopLevelFeel) {
      TemplateNode.TopLevelFeel expression = (TemplateNode.TopLevelFeel) node;
      return stringify(expression.expression(), context, options, expression.offset());
    }
    if (node instanceof TemplateNode.Block) {
      TemplateNode.Block block = (TemplateNode.Block) node;
      StringBuilder rendered = new StringBuilder();
      for (TemplateNode child : block.children()) rendered.append(renderNode(child, context, options));
      return rendered.toString();
    }
    if (node instanceof TemplateNode.If) {
      TemplateNode.If conditional = (TemplateNode.If) node;
      Object value = evaluate(conditional.condition(), context, options, conditional.offset());
      if (options.strict() && !(value instanceof Boolean)) {
        return error("FEEL expression " + conditional.condition() + " expected to evaluate to a boolean", conditional.offset(), options);
      }
      if (!truthy(value)) return "";
      String result = renderNode(conditional.body(), context, options);
      return conditional.closeHadNewline() && !result.endsWith("\n") ? result + "\n" : result;
    }
    if (node instanceof TemplateNode.Loop) {
      TemplateNode.Loop loop = (TemplateNode.Loop) node;
      Object value = evaluate(loop.collection(), context, options, loop.offset());
      if (options.strict() && !isCollectionLike(value)) {
        return error("FEEL expression " + loop.collection() + " expected to evaluate to an array", loop.offset(), options);
      }
      List<Object> items = toItems(value);
      StringBuilder result = new StringBuilder();
      for (Object item : items) result.append(renderNode(loop.body(), context.child(item), options));
      return loop.closeHadNewline() && !result.toString().endsWith("\n") ? result + "\n" : result.toString();
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

  private List<Object> toItems(Object value) {
    if (value instanceof Collection<?>) return new ArrayList<Object>((Collection<?>) value);
    if (value != null && value.getClass().isArray()) {
      int length = Array.getLength(value);
      List<Object> result = new ArrayList<>(length);
      for (int i = 0; i < length; i++) result.add(Array.get(value, i));
      return result;
    }
    return value == null ? Collections.<Object>emptyList() : Collections.singletonList(value);
  }

  private static boolean isCollectionLike(Object value) {
    return value instanceof Collection<?> || (value != null && value.getClass().isArray());
  }

  private static boolean truthy(Object value) {
    return !(value == null || Boolean.FALSE.equals(value));
  }

  /**
   * Intent: encapsulate one variable scope and maintain this, parent, and compatibility aliases.
   * Boundary: build loop scopes only; do not map arbitrary objects or evaluate FEEL expressions.
   */
  private static final class RenderContext {
    private final Map<String, Object> variables;

    private RenderContext(Map<String, Object> variables) {
      this.variables = variables;
    }

    static RenderContext root(Map<String, Object> model) {
      return build(model, null, model);
    }

    RenderContext child(Object item) {
      Map<?, ?> itemValues = item instanceof Map<?, ?> ? (Map<?, ?>) item : Collections.emptyMap();
      return build(itemValues, variables, item);
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

}
