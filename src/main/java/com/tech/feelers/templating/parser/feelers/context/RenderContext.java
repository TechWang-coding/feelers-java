package com.tech.feelers.templating.parser.feelers.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Intent: encapsulate one render variable scope and maintain this, parent, and compatibility aliases.
 * Boundary: build loop scopes only; do not map arbitrary objects or evaluate FEEL expressions.
 */
public final class RenderContext {
  private final Map<String, Object> variables;

  private RenderContext(Map<String, Object> variables) {
    this.variables = variables;
  }

  public static RenderContext root(Map<String, Object> model) {
    return build(model, null, model);
  }

  public RenderContext child(Object item) {
    Map<?, ?> itemValues = item instanceof Map<?, ?> ? (Map<?, ?>) item : Collections.emptyMap();
    return build(itemValues, variables, item);
  }

  public Map<String, Object> variables() {
    return variables;
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
