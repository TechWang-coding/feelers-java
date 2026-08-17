package com.tech.feelers.expression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.tech.feelers.expression.adapters.camunda.BuiltinFunctionNames;
import com.tech.feelers.expression.adapters.camunda.CamundaFeelRuntime;
import com.tech.feelers.expression.errors.FeelFunctionInvocationException;
import com.tech.feelers.expression.errors.ReferenceDataException;
import com.tech.feelers.expression.execution.FeelFunctionRegistry;

/**
 * Immutable FEEL engine backed by the Camunda FEEL engine.
 *
 * <p>Register every business function when the engine is created, then reuse the instance for
 * evaluation. The function set is fixed at construction so submitted data can never introduce or
 * replace an executable capability.
 *
 * <p>This mirrors {@code frontend/src/FeelExpressionEngine/FeelExpressionEngine.ts}; both runtimes
 * must accept the same expressions and classify the same failures the same way.
 */
public final class FeelExpressionEngine {

  private final CamundaFeelRuntime runtime;
  private final Set<String> functionNames;

  /**
   * Keeps construction private so every instance preserves the registry's duplicate-name and
   * built-in-name invariants.
   *
   * @param definitions definitions that already passed registration checks
   */
  private FeelExpressionEngine(List<FeelFunctionDefinition> definitions) {
    this.functionNames = definitions.stream()
        .map(FeelFunctionDefinition::name)
        .collect(Collectors.toUnmodifiableSet());
    this.runtime = new CamundaFeelRuntime(definitions);
  }

  /**
   * Creates an engine exposing only the supplied functions.
   *
   * @param functions function definitions to register, in priority order
   * @return an immutable engine whose registry cannot change afterwards
   * @throws IllegalArgumentException when a definition is invalid, duplicated, or shadows a built-in
   */
  public static FeelExpressionEngine create(List<FeelFunctionDefinition> functions) {
    FeelFunctionRegistry registry = new FeelFunctionRegistry();
    functions.forEach(registry::register);
    return new FeelExpressionEngine(registry.freeze());
  }

  /**
   * Evaluates one expression against read-only variables.
   *
   * <p>A failure inside a registered function becomes a warning with a {@code null} value, matching
   * the TypeScript engine. Engine-level failures keep propagating because they indicate a defect
   * rather than a rule outcome.
   *
   * @param expression FEEL source to evaluate
   * @param variables values visible to the expression; names shadowing functions are dropped
   * @return the evaluated value together with any warnings
   * @throws ReferenceDataException when a controlled query cannot answer, so callers can report it
   *     as an upstream failure instead of a rule outcome
   */
  public FeelEvaluationResult evaluate(String expression, Map<String, Object> variables) {
    try {
      return FeelEvaluationResult.of(runtime.evaluate(expression, createContext(variables)));
    } catch (FeelFunctionInvocationException exception) {
      if (exception.getCause() instanceof ReferenceDataException referenceFailure) {
        throw referenceFailure;
      }
      return FeelEvaluationResult.failed(new FeelWarning("FUNCTION_INVOCATION_FAILED",
          "FEEL function \"" + exception.functionName() + "\" failed"));
    }
  }

  /**
   * Parses an expression without executing it, so syntax can be checked without invoking functions
   * or touching submitted data.
   *
   * @param expression FEEL source to parse
   * @return the syntax-only outcome
   */
  public FeelSyntaxValidationResult parse(String expression) {
    return runtime.parse(expression);
  }

  /**
   * Exposes the registered function names.
   *
   * <p>The engine returns {@code null} rather than an error when an expression calls an unknown
   * function, so callers that need to reject typos must compare against this set themselves.
   *
   * @return the immutable set of registered function names
   */
  public Set<String> registeredFunctionNames() {
    return functionNames;
  }

  /**
   * Builds the evaluation context while protecting registered and built-in function names from
   * caller variables, so submitted data cannot replace an executable capability.
   *
   * @param variables caller-provided values
   * @return values safe to expose to the expression
   */
  private Map<String, Object> createContext(Map<String, Object> variables) {
    Map<String, Object> context = new LinkedHashMap<>();
    variables.forEach((name, value) -> {
      if (!functionNames.contains(name) && !BuiltinFunctionNames.contains(name)) {
        context.put(name, value);
      }
    });
    return context;
  }

  /**
   * Convenience factory for the standard function set, keeping the default capability list in one
   * place instead of repeating it at each call site.
   *
   * @param client reference-data boundary backing {@code existsInReferenceSet}
   * @param calendar working-day policy backing {@code businessDay}
   * @return an engine with the standard functions registered
   */
  public static FeelExpressionEngine createDefault(
      com.tech.feelers.expression.functions.ReferenceDataClient client,
      com.tech.feelers.expression.functions.BusinessCalendar calendar) {
    List<FeelFunctionDefinition> definitions = new ArrayList<>();
    definitions.add(com.tech.feelers.expression.functions.DateFunctions.businessDay(calendar));
    definitions.add(com.tech.feelers.expression.functions.DateFunctions.calendarDay());
    definitions.add(
        com.tech.feelers.expression.functions.ExistsInReferenceSetFunction.create(client));
    return create(definitions);
  }
}
