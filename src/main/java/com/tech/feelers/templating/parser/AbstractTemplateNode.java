package com.tech.feelers.templating.parser;

import java.util.Arrays;
import java.util.List;

/**
 * Intent: provide common value-object behavior for template AST nodes.
 * Boundary: compare node type and constructor values only; do not participate in parsing or
 * rendering.
 */
abstract class AbstractTemplateNode implements TemplateNode {
  private final List<Object> values;

  protected AbstractTemplateNode(Object... values) {
    this.values = Arrays.asList(values.clone());
  }

  @Override
  public final boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    AbstractTemplateNode that = (AbstractTemplateNode) other;
    return values.equals(that.values);
  }

  @Override
  public final int hashCode() {
    return 31 * getClass().hashCode() + values.hashCode();
  }

  @Override
  public final String toString() {
    return getClass().getSimpleName() + values;
  }
}
