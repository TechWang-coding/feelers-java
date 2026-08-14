package com.tech.feelers.templating.service;

import com.tech.feelers.templating.engine.CamundaFeelExpressionEngine;
import com.tech.feelers.templating.engine.FeelExpressionEngine;
import com.tech.feelers.templating.exception.TemplateException;
import com.tech.feelers.templating.parser.FeelersTemplateParser;
import com.tech.feelers.templating.parser.feelers.context.RenderContext;
import com.tech.feelers.templating.parser.feelers.nodes.FeelersTemplateNode;
import com.tech.feelers.templating.parser.feelers.nodes.RenderOptions;
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
  public static FeelersTemplateNode parse(String template) {
    Objects.requireNonNull(template, "template");
    return new FeelersTemplateParser(template).parse();
  }

  /**
   * Equivalent to front-end parseToSimpleTree(template).
   * The Java parser builds {@link FeelersTemplateNode} directly, so it has no separate raw-tree
   * layer.
   */
  public static FeelersTemplateNode parseToSimpleTree(String template) {
    return parse(template);
  }

  private String evaluateTemplate(String template, Map<String, Object> model, RenderOptions options) {
    RenderOptions effectiveOptions = options == null ? RenderOptions.DEFAULT : options;
    FeelersTemplateNode root = parse(template);
    return root.render(feel, RenderContext.root(model == null ? Map.of() : model), effectiveOptions);
  }
}
