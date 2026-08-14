package com.tech.feelers.templating.parser.feelers.nodes;

import com.google.common.collect.ImmutableList;
import com.tech.feelers.templating.engine.FeelExpressionEngine;
import com.tech.feelers.templating.parser.feelers.context.RenderContext;
import com.tech.feelers.templating.service.FeelEvaluatorService;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Intent: represent a loop template block.
 * Boundary: enumerate the evaluated collection and render its body; do not evaluate child nodes.
 */
@Value
@Accessors(fluent = true)
public class LoopNode implements FeelersTemplateNode {
  private final String collection;
  private final BlockNode body;
  private final boolean closeHadNewline;
  private final int offset;

  @Override
  public String render(FeelExpressionEngine feel, RenderContext context, RenderOptions options) {
    Object value = FeelEvaluatorService.evaluate(feel, collection, context, options, offset);
    if (options.strict() && !isCollectionLike(value)) {
      return FeelEvaluatorService.error(
          "FEEL expression " + collection + " expected to evaluate to an array", offset, options);
    }
    StringBuilder rendered = new StringBuilder();
    for (Object item : toItems(value)) {
      rendered.append(body.render(feel, context.child(item), options));
    }
    String result = rendered.toString();
    return closeHadNewline && !StringUtils.endsWith(result, "\n") ? result + "\n" : result;
  }

  private static boolean isCollectionLike(Object value) {
    return value instanceof Collection<?> || (value != null && value.getClass().isArray());
  }

  private static List<?> toItems(Object value) {
    if (value instanceof Collection<?>) {
      return ImmutableList.copyOf((Collection<?>) value);
    }
    if (value != null && value.getClass().isArray()) {
      int length = Array.getLength(value);
      ImmutableList.Builder<Object> items = ImmutableList.builderWithExpectedSize(length);
      for (int index = 0; index < length; index++) {
        items.add(Array.get(value, index));
      }
      return items.build();
    }
    return value == null ? ImmutableList.of() : ImmutableList.of(value);
  }
}
