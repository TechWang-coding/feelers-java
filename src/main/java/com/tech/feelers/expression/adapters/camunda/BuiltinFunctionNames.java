package com.tech.feelers.expression.adapters.camunda;

import java.util.Set;

/**
 * Built-in FEEL function names recognised by the Camunda engine.
 *
 * <p>The engine does not expose its built-in registry as public API, so this list is maintained by
 * hand and mirrors {@code frontend/src/FeelExpressionEngine/adapters/feelin/builtinFunctionNames.ts}.
 * Keep both lists in sync when either engine is upgraded: they exist so a registered function or a
 * caller variable can never silently shadow a built-in on one runtime but not the other.
 */
public final class BuiltinFunctionNames {

  private static final Set<String> NAMES = Set.of(
      "date", "time", "date and time", "duration", "years and months duration", "string", "number",
      "substring", "string length", "upper case", "lower case", "substring before",
      "substring after", "replace", "contains", "starts with", "ends with", "matches", "split",
      "list contains", "list replace", "count", "min", "max", "sum", "mean", "all", "any",
      "sublist", "append", "concatenate", "insert before", "remove", "reverse", "index of", "union",
      "distinct values", "flatten", "product", "median", "stddev", "mode", "get entries",
      "get value", "context put", "context merge", "context", "sort", "decimal", "floor", "ceiling",
      "abs", "modulo", "sqrt", "log", "exp", "odd", "even", "day of week", "day of year",
      "week of year", "month of year", "today", "now", "before", "after", "meets", "met by",
      "overlaps", "overlaps before", "overlaps after", "finishes", "finished by", "includes",
      "during", "starts", "started by", "coincides");

  private BuiltinFunctionNames() {
  }

  /**
   * Reports whether a name belongs to the engine's built-in function set, so registration and
   * context construction can refuse to shadow it.
   *
   * @param name candidate function or variable name
   * @return whether the name is a built-in FEEL function
   */
  public static boolean contains(String name) {
    return NAMES.contains(name);
  }
}
