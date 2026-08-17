package com.tech.feelers.expression.validation;

import java.util.HashSet;
import java.util.Set;

import com.tech.feelers.expression.FeelFunctionDefinition;

/**
 * Verifies the structural contract a host function must satisfy before it enters the runtime
 * registry.
 *
 * <p>Validation happens before wrapping so an invalid definition fails predictably at startup
 * rather than during an end user's validation request.
 */
public final class FunctionDefinitionValidator {

  private FunctionDefinitionValidator() {
  }

  /**
   * Checks one candidate definition, mirroring the TypeScript {@code validateFunctionDefinition}
   * rules so both runtimes reject the same definitions.
   *
   * @param definition candidate function contract
   * @throws IllegalArgumentException when the name or argument names violate the contract
   */
  public static void validate(FeelFunctionDefinition definition) {
    if (definition == null) {
      throw new IllegalArgumentException("A FEEL function definition must not be null");
    }
    if (!isTrimmedNonEmpty(definition.name())) {
      throw new IllegalArgumentException(
          "A FEEL function name must be non-empty and free of surrounding whitespace");
    }
    Set<String> seen = new HashSet<>();
    for (String arg : definition.args()) {
      if (!isTrimmedNonEmpty(arg)) {
        throw new IllegalArgumentException("FEEL function \"" + definition.name()
            + "\" must declare non-empty argument names");
      }
      if (!seen.add(arg)) {
        throw new IllegalArgumentException("FEEL function \"" + definition.name()
            + "\" cannot declare duplicate argument names");
      }
    }
  }

  /**
   * Accepts only stable identifiers without invisible leading or trailing whitespace, preventing
   * names that look valid in a definition but cannot be called reliably from an expression.
   *
   * @param value candidate identifier
   * @return whether the value is non-null, non-empty, and already trimmed
   */
  private static boolean isTrimmedNonEmpty(String value) {
    return value != null && !value.isEmpty() && value.equals(value.trim());
  }
}
