package com.tech.feelers.templating.service;

import com.tech.feelers.templating.engine.FeelExpressionEngine;
import com.tech.feelers.templating.exception.TemplateException;
import com.tech.feelers.templating.parser.feelers.context.RenderContext;
import com.tech.feelers.templating.parser.feelers.nodes.RenderOptions;

/**
 * Intent: centralize FEEL evaluation, string conversion, and template error policies.
 * Boundary: invoke the supplied FEEL engine only; do not control template node traversal or scopes.
 */
public final class FeelEvaluatorService {
  private FeelEvaluatorService() {
  }

  public static String stringify(FeelExpressionEngine feel, String expression, RenderContext context,
      RenderOptions options, int offset) {
    try {
      Object value = feel.evaluate("string(" + expression + ")", context.variables());
      String result = String.valueOf(value);
      return options.sanitizer() == null ? result : options.sanitizer().apply(result);
    } catch (RuntimeException exception) {
      if (options.debug()) {
        return "{{ feel expression " + expression + " couldn't be evaluated }}";
      }
      throw new TemplateException("FEEL expression " + expression + " couldn't be evaluated", offset,
          exception);
    }
  }

  public static Object evaluate(FeelExpressionEngine feel, String expression, RenderContext context,
      RenderOptions options, int offset) {
    try {
      return feel.evaluate(expression, context.variables());
    } catch (RuntimeException exception) {
      if (options.debug()) {
        return "{{ feel expression " + expression + " couldn't be evaluated }}";
      }
      throw new TemplateException("FEEL expression " + expression + " couldn't be evaluated", offset,
          exception);
    }
  }

  public static String error(String message, int offset, RenderOptions options) {
    if (options.debug()) {
      return "{{ " + message.toLowerCase() + " }}";
    }
    throw new TemplateException(message, offset);
  }
}
