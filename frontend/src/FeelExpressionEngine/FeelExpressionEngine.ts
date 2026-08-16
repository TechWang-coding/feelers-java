import { builtinFunctionNames } from './adapters/feelin/builtinFunctionNames';
import { feelinRuntime } from './adapters/feelin/FeelinRuntime';
import { mapFunctionInvocationWarning } from './adapters/feelin/mapFunctionInvocationWarning';
import { FeelFunctionInvocationError } from './errors/FeelFunctionInvocationError';
import { FeelFunctionRegistry } from './execution/FeelFunctionRegistry';
import { wrapFeelFunctions } from './execution/WrapFeelFunctions';
import { createBusinessDayFunction, createCalendarDayFunction } from './functions';
import type {
  FeelEvaluationResult,
  FeelExpressionEngineOptions,
  FeelFunctionDefinition,
  FeelFunctionHandler,
  FeelSyntaxValidationResult,
  FeelVariables
} from './types';

/**
 * Immutable FEEL engine backed by @bpmn-io/feelin. Register all business functions during
 * application startup, then reuse this instance for expression evaluation.
 */
export class FeelExpressionEngine {
  private static readonly INSTANCE = FeelExpressionEngine.create();

  private readonly functions: Readonly<Record<string, FeelFunctionHandler>>;
  private readonly functionNames: ReadonlySet<string>;
  private readonly options: FeelExpressionEngineOptions;

  /**
   * Creates an immutable runtime from definitions that have already passed registration checks.
   * This stays private so every instance preserves the registry's duplicate and built-in-name
   * invariants.
   */
  private constructor(
    definitions: readonly FeelFunctionDefinition[],
    options: FeelExpressionEngineOptions
  ) {
    this.functionNames = new Set(definitions.map(({ name }) => name));
    this.functions = wrapFeelFunctions(definitions);
    this.options = options;
  }

  /**
   * Creates an engine with the standard functions and optional application functions.
   *
   * @param extraFunctions Additional synchronous functions validated before evaluation is allowed.
   * @param options Optional runtime collaborators, including structured failure logging.
   * @returns An immutable engine whose function registry cannot change after construction.
   * @throws {Error} When a function definition is invalid, duplicated, or conflicts with a built-in.
   */
  static create(
    extraFunctions: readonly FeelFunctionDefinition[] = [],
    options: FeelExpressionEngineOptions = {}
  ): FeelExpressionEngine {
    const registry = new FeelFunctionRegistry();
    [ ...FeelExpressionEngine.createDefaultFunctions(), ...extraFunctions ]
      .forEach((definition) => registry.register(definition));
    return new FeelExpressionEngine(registry.freeze(), options);
  }

  /**
   * Returns the immutable default engine so callers can reuse one consistent function registry.
   *
   * @returns The application-wide engine with standard functions registered.
   */
  static getInstance(): FeelExpressionEngine {
    return FeelExpressionEngine.INSTANCE;
  }

  /**
   * Builds the standard function definitions in one place so every default instance has the same
   * capability set.
   *
   * @returns Definitions registered before caller-supplied functions.
   */
  private static createDefaultFunctions(): readonly FeelFunctionDefinition[] {
    return [ createBusinessDayFunction(), createCalendarDayFunction() ];
  }

  /**
   * Evaluates one FEEL expression against read-only variables.
   *
   * @param expression Source expression to evaluate.
   * @param variables Values available to the expression; names colliding with functions are ignored.
   * @returns The evaluated value and runtime warnings; a registered-function failure returns `null`.
   * @throws {Error} When the FEEL runtime fails for a reason other than a registered function.
   */
  evaluate(expression: string, variables: FeelVariables = {}): FeelEvaluationResult<unknown> {
    return this.execute(expression, () => feelinRuntime.evaluate(expression, this.createContext(variables)));
  }

  /**
   * Evaluates a FEEL unary test against the special `?` input and optional variables.
   *
   * @param expression Unary-test source to evaluate.
   * @param variables Values available to the test, including the optional `?` input.
   * @returns A boolean result or `null`, together with runtime warnings.
   * @throws {Error} When evaluation fails outside a registered-function invocation.
   */
  unaryTest(
    expression: string,
    variables: FeelVariables = {}
  ): FeelEvaluationResult<boolean> {
    return this.execute(expression, () => feelinRuntime.unaryTest(expression, this.createContext(variables)));
  }

  /**
   * Parses a complete FEEL expression without executing it, allowing callers to report syntax
   * problems before submitting data or invoking functions.
   *
   * @param expression Source expression to parse.
   * @param variables Names available while parsing function and variable references.
   * @returns Every syntax-error range found by the parser.
   */
  validate(
    expression: string,
    variables: FeelVariables = {}
  ): FeelSyntaxValidationResult {
    return feelinRuntime.validateExpression(expression, this.createContext(variables));
  }

  /**
   * Parses a FEEL unary test without executing it, keeping validation side-effect free.
   *
   * @param expression Unary-test source to parse.
   * @param variables Names available while parsing function and variable references.
   * @returns Every syntax-error range found by the parser.
   */
  validateUnaryTest(
    expression: string,
    variables: FeelVariables = {}
  ): FeelSyntaxValidationResult {
    return feelinRuntime.validateUnaryTest(expression, this.createContext(variables));
  }

  /**
   * Builds a runtime context while protecting registered and built-in function names from caller
   * variables. This prevents data input from replacing executable capabilities.
   *
   * @param variables Caller-provided evaluation values.
   * @returns Runtime values combined with the immutable registered-function map.
   */
  private createContext(variables: FeelVariables): Record<string, unknown> {
    const allowedVariables = Object.fromEntries(Object.entries(variables).filter(([name]) =>
      !this.functionNames.has(name) && !builtinFunctionNames.has(name)
    ));
    return { ...allowedVariables, ...this.functions };
  }

  /**
   * Executes a runtime operation and converts only registered-function failures into the stable
   * warning contract. Other failures remain visible because they indicate an engine/runtime error.
   *
   * @param expression Source expression used for diagnostic source ranges.
   * @param operation Deferred runtime operation so its errors can be classified consistently.
   * @returns The runtime result, or a `null` value with one function-failure warning.
   * @throws {Error} When the operation fails outside a registered-function invocation.
   */
  private execute<T>(
    expression: string,
    operation: () => FeelEvaluationResult<T>
  ): FeelEvaluationResult<T> {
    try {
      return operation();
    } catch (error) {
      if (!(error instanceof FeelFunctionInvocationError)) throw error;

      this.options.logger?.functionFailed({
        functionName: error.functionName,
        expression,
        error: error.cause
      });
      return {
        value: null,
        warnings: [ mapFunctionInvocationWarning(expression, error) ]
      };
    }
  }
}
