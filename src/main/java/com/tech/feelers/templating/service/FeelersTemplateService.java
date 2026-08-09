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
 * 设定意图：作为与前端 FEELers 对齐的静态模板 API，编排模板解析、变量作用域、FEEL 求值和输出策略。
 * 作用边界：只公开 evaluate、parse 和 parseToSimpleTree；不解析 FEEL 本身，不暴露 Camunda 类型。
 */
public final class FeelersTemplateService {
  private final FeelExpressionEngine feel;

  private FeelersTemplateService() {
    this(new CamundaFeelExpressionEngine());
  }

  private FeelersTemplateService(FeelExpressionEngine feel) {
    this.feel = Objects.requireNonNull(feel, "feel");
  }

  /** 对应前端 evaluate(template, context)，使用默认渲染选项。 */
  public static String evaluate(String template, Map<String, Object> model) {
    return evaluate(template, model, RenderOptions.DEFAULT);
  }

  /** 对应前端 evaluate(template, context, options)，渲染完整 FEELers 模板。 */
  public static String evaluate(String template, Map<String, Object> model, RenderOptions options) {
    return new FeelersTemplateService().evaluateTemplate(template, model, options);
  }

  /**
   * 对应前端 parse(template)，验证模板外壳并返回 Java 模板 AST。
   * 模板结构不合法时抛出 {@link TemplateException}。
   */
  public static TemplateNode parse(String template) {
    Objects.requireNonNull(template, "template");
    return new TemplateParser(template).parse();
  }

  /**
   * 对应前端 parseToSimpleTree(template)。
   * Java 解析器直接构建 {@link TemplateNode}，因此不再区分原始树与简化树。
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
    if (node instanceof TemplateNode.Text text) return text.value();
    if (node instanceof TemplateNode.Insert insert) return stringify(insert.expression(), context, options, insert.offset());
    if (node instanceof TemplateNode.TopLevelFeel expression) return stringify(expression.expression(), context, options, expression.offset());
    if (node instanceof TemplateNode.Block block) {
      StringBuilder rendered = new StringBuilder();
      for (TemplateNode child : block.children()) rendered.append(renderNode(child, context, options));
      return rendered.toString();
    }
    if (node instanceof TemplateNode.If conditional) {
      Object value = evaluate(conditional.condition(), context, options, conditional.offset());
      if (options.strict() && !(value instanceof Boolean)) {
        return error("FEEL expression " + conditional.condition() + " expected to evaluate to a boolean", conditional.offset(), options);
      }
      if (!truthy(value)) return "";
      String result = renderNode(conditional.body(), context, options);
      return conditional.closeHadNewline() && !result.endsWith("\n") ? result + "\n" : result;
    }
    if (node instanceof TemplateNode.Loop loop) {
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

  /**
   * 设定意图：封装单层渲染变量，并统一维护 this、parent 及兼容别名。
   * 作用边界：只建立循环层级作用域；不负责对象映射和 FEEL 表达式求值。
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

}
