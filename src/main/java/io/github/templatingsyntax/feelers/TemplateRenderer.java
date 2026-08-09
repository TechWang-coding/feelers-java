package io.github.templatingsyntax.feelers;

import java.util.Map;

/** Renders the FEELers template language without exposing an engine-specific API. */
public interface TemplateRenderer {
  String render(String template, Map<String, Object> model, RenderOptions options);

  default String render(String template, Map<String, Object> model) {
    return render(template, model, RenderOptions.DEFAULT);
  }
}
