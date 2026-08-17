package com.tech.feelers.expression;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Declares one host function callable from a FEEL expression, together with the argument names used
 * by FEEL named-argument calls.
 *
 * <p>Mirrors the TypeScript {@code FeelFunctionDefinition} contract so both runtimes register the
 * same function names and arities.
 *
 * @param name name used by FEEL expressions; must be non-blank and free of surrounding whitespace
 * @param args parameter names used by FEEL named-argument calls; must be unique and non-blank
 * @param handler synchronous host-side operation invoked with the positional arguments
 */
public record FeelFunctionDefinition(String name, List<String> args,
    Function<List<Object>, Object> handler) {

  /**
   * Normalizes the argument list so a registered definition cannot be mutated through the list
   * reference supplied at construction time.
   */
  public FeelFunctionDefinition {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(args, "args");
    Objects.requireNonNull(handler, "handler");
    args = List.copyOf(args);
  }
}
