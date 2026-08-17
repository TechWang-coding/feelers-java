package com.tech.feelers.expression.adapters.camunda;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.camunda.feel.api.EvaluationResult;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;
import org.camunda.feel.api.ParseResult;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.context.JavaFunctionProvider;
import org.camunda.feel.valuemapper.CustomValueMapper;
import org.camunda.feel.valuemapper.ValueMapper;

import com.tech.feelers.expression.FeelFunctionDefinition;
import com.tech.feelers.expression.FeelSyntaxValidationResult;
import com.tech.feelers.expression.errors.FeelFunctionInvocationException;

import scala.jdk.javaapi.CollectionConverters;

/**
 * Adapter isolating the Camunda FEEL engine behind a small, JSON-friendly surface.
 *
 * <p>All Camunda and Scala types stop here: callers pass plain Java values and receive plain Java
 * values. External function invocation stays disabled, so an expression can only reach functions
 * registered through this runtime.
 */
public final class CamundaFeelRuntime {

  /**
   * Carries a host-function failure past the engine.
   *
   * <p>The engine catches whatever a function throws and reports it as a generic evaluation
   * failure string, which would erase the distinction between "the application's function broke"
   * and "the expression is wrong". Stashing the original exception for the duration of one
   * evaluation is what preserves that distinction.
   */
  private static final ThreadLocal<RuntimeException> FUNCTION_FAILURE = new ThreadLocal<>();

  private final FeelEngineApi engine;
  private final ValueMapper valueMapper;

  /**
   * Builds a runtime exposing exactly the supplied functions.
   *
   * <p>The same value mapper is given to the engine and used to unpack function arguments, so a
   * handler and an expression result never see two different representations of the same value.
   *
   * @param definitions already validated function definitions to expose to expressions
   */
  public CamundaFeelRuntime(List<FeelFunctionDefinition> definitions) {
    JavaTypeValueMapper javaTypes = new JavaTypeValueMapper();
    this.valueMapper = new ValueMapper.CompositeValueMapper(
        CollectionConverters.asScala(List.<CustomValueMapper>of(javaTypes)).toList());
    this.engine = FeelEngineBuilder.forJava()
        .withEnabledExternalFunctions(false)
        .withCustomValueMapper(javaTypes)
        .withFunctionProvider(new DefinitionFunctionProvider(definitions))
        .build();
  }

  /**
   * Evaluates a complete expression against read-only variables.
   *
   * @param expression FEEL source to evaluate
   * @param variables values visible to the expression
   * @return the evaluated value as a plain Java value
   * @throws FeelFunctionInvocationException when a registered function threw
   * @throws IllegalArgumentException when the engine itself reported a failure
   */
  public Object evaluate(String expression, Map<String, Object> variables) {
    FUNCTION_FAILURE.remove();
    try {
      EvaluationResult result = engine.evaluateExpression(expression, variables);
      RuntimeException functionFailure = FUNCTION_FAILURE.get();
      if (functionFailure != null) {
        throw functionFailure;
      }
      if (result.isSuccess()) {
        return result.result();
      }
      throw new IllegalArgumentException(result.failure().message());
    } finally {
      FUNCTION_FAILURE.remove();
    }
  }

  /**
   * Parses an expression without executing it, so syntax problems can be reported without invoking
   * any function or touching submitted data.
   *
   * @param expression FEEL source to parse
   * @return the syntax-only outcome
   */
  public FeelSyntaxValidationResult parse(String expression) {
    ParseResult result = engine.parseExpression(expression);
    return result.isSuccess()
        ? FeelSyntaxValidationResult.valid()
        : FeelSyntaxValidationResult.invalid(result.failure().message());
  }

  /**
   * Bridges validated definitions into the engine's function-provider SPI, applying one uniform
   * error and value-conversion policy to every registered function.
   */
  private final class DefinitionFunctionProvider extends JavaFunctionProvider {

    private final Map<String, JavaFunction> functions = new LinkedHashMap<>();

    private DefinitionFunctionProvider(List<FeelFunctionDefinition> definitions) {
      definitions.forEach(definition ->
          functions.put(definition.name(), toJavaFunction(definition)));
    }

    @Override
    public Optional<JavaFunction> resolveFunction(String functionName) {
      return Optional.ofNullable(functions.get(functionName));
    }

    @Override
    public Collection<String> getFunctionNames() {
      return functions.keySet();
    }

    /**
     * Wraps one definition so handlers never see engine or Scala types, and so a handler failure is
     * recorded before the engine can flatten it into an opaque message.
     *
     * @param definition validated definition to expose
     * @return the engine-facing function
     */
    private JavaFunction toJavaFunction(FeelFunctionDefinition definition) {
      return new JavaFunction(definition.args(), args -> {
        List<Object> arguments = new ArrayList<>(args.size());
        args.forEach(arg -> arguments.add(valueMapper.unpackVal(arg)));
        try {
          return valueMapper.toVal(definition.handler().apply(arguments));
        } catch (RuntimeException exception) {
          RuntimeException failure = exception instanceof FeelFunctionInvocationException
              ? exception
              : new FeelFunctionInvocationException(definition.name(), exception);
          FUNCTION_FAILURE.set(failure);
          throw failure;
        }
      });
    }
  }
}
