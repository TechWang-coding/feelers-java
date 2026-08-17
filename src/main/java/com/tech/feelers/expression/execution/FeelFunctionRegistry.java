package com.tech.feelers.expression.execution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tech.feelers.expression.FeelFunctionDefinition;
import com.tech.feelers.expression.adapters.camunda.BuiltinFunctionNames;
import com.tech.feelers.expression.validation.FunctionDefinitionValidator;

/**
 * Startup-only builder for an immutable custom-function registry.
 *
 * <p>Centralising registration here is what prevents one function from shadowing another or a
 * built-in, and what guarantees the executable capability set cannot change after the engine is
 * constructed.
 */
public final class FeelFunctionRegistry {

  private final Map<String, FeelFunctionDefinition> definitions = new LinkedHashMap<>();
  private boolean frozen;

  /**
   * Validates and records one function before the registry is frozen.
   *
   * @param definition function contract to register
   * @return this registry, for startup-time composition
   * @throws IllegalStateException when the registry has already been frozen
   * @throws IllegalArgumentException when the definition is invalid or its name conflicts
   */
  public FeelFunctionRegistry register(FeelFunctionDefinition definition) {
    if (frozen) {
      throw new IllegalStateException("Cannot register FEEL functions after the registry is frozen");
    }
    FunctionDefinitionValidator.validate(definition);
    if (BuiltinFunctionNames.contains(definition.name())) {
      throw new IllegalArgumentException("Custom FEEL function \"" + definition.name()
          + "\" conflicts with a built-in function");
    }
    if (definitions.containsKey(definition.name())) {
      throw new IllegalArgumentException(
          "Custom FEEL function \"" + definition.name() + "\" is already registered");
    }
    definitions.put(definition.name(), definition);
    return this;
  }

  /**
   * Closes startup registration and returns an immutable snapshot for engine construction, so
   * runtime code cannot mutate executable capabilities.
   *
   * @return registered definitions in registration order
   */
  public List<FeelFunctionDefinition> freeze() {
    frozen = true;
    return List.copyOf(new ArrayList<>(definitions.values()));
  }
}
