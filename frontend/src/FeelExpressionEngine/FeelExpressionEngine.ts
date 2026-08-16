import {
  evaluate as feelinEvaluate,
  parseExpression,
  parseUnaryTests,
  unaryTest as feelinUnaryTest,
  type EvaluationResult
} from '@bpmn-io/feelin';
import { FeelEngineStatic } from './common/FeelEngineStatic';
import { FeelFunctionInvocationError } from './common/FeelFunctionInvocationError';
import { FeelFunctionRegistry } from './common/FeelFunctionRegistry';
import type {
  FeelExpressionEngineOptions,
  FeelFunctionDefinition,
  FeelFunctionHandler,
  FeelSyntaxValidationResult,
  FeelVariables
} from './common/FeelExpressionTypes';
import { wrapFunction } from './utils/FeelFunctionUtils';
import { collectSyntaxValidationResult } from './utils/FeelSyntaxUtils';
import { createFunctionFailureWarning } from './utils/FeelWarningUtils';
import { createBusinessDayFunction } from './functions/BusinessDay';
import { createCalendarDayFunction } from './functions/CalendarDay';

/**
 * Immutable FEEL engine backed by @bpmn-io/feelin. Register all business functions during
 * application startup, then reuse this instance for expression evaluation.
 */
export class FeelExpressionEngine {
  private static readonly INSTANCE = FeelExpressionEngine.create();

  private readonly functions: Readonly<Record<string, FeelFunctionHandler>>;
  private readonly functionNames: ReadonlySet<string>;
  private readonly options: FeelExpressionEngineOptions;

  private constructor(
    definitions: readonly FeelFunctionDefinition[],
    options: FeelExpressionEngineOptions
  ) {
    this.functionNames = new Set(definitions.map(({ name }) => name));
    this.functions = Object.freeze(Object.fromEntries(definitions.map((definition) => [
      definition.name,
      wrapFunction(definition)
    ])));
    this.options = options;
  }

  static create(
    extraFunctions: readonly FeelFunctionDefinition[] = [],
    options: FeelExpressionEngineOptions = {}
  ): FeelExpressionEngine {
    const registry = new FeelFunctionRegistry();
    [ ...FeelExpressionEngine.createDefaultFunctions(), ...extraFunctions ]
      .forEach((definition) => registry.register(definition));
    return new FeelExpressionEngine(registry.freeze(), options);
  }

  /** Returns the application-wide engine with all built-in business functions registered. */
  static getInstance(): FeelExpressionEngine {
    return FeelExpressionEngine.INSTANCE;
  }

  private static createDefaultFunctions(): readonly FeelFunctionDefinition[] {
    return [ createBusinessDayFunction(), createCalendarDayFunction() ];
  }

  evaluate(expression: string, variables: FeelVariables = {}): EvaluationResult<unknown> {
    return this.execute(expression, () => feelinEvaluate(expression, this.createContext(variables)));
  }

  unaryTest(
    expression: string,
    variables: FeelVariables = {}
  ): EvaluationResult<boolean | null> {
    return this.execute(expression, () => feelinUnaryTest(expression, this.createContext(variables)));
  }

  /** Validates a complete FEEL expression without executing it. */
  validate(
    expression: string,
    variables: FeelVariables = {}
  ): FeelSyntaxValidationResult {
    return collectSyntaxValidationResult(parseExpression(expression, this.createContext(variables), undefined));
  }

  /** Validates a FEEL unary test without executing it. */
  validateUnaryTest(
    expression: string,
    variables: FeelVariables = {}
  ): FeelSyntaxValidationResult {
    return collectSyntaxValidationResult(parseUnaryTests(expression, this.createContext(variables), undefined));
  }

  private createContext(variables: FeelVariables): Record<string, unknown> {
    const allowedVariables = Object.fromEntries(Object.entries(variables).filter(([name]) =>
      !this.functionNames.has(name) && !FeelEngineStatic.BUILTIN_FUNCTION_NAMES.has(name)
    ));
    return { ...allowedVariables, ...this.functions };
  }

  private execute<T>(
    expression: string,
    operation: () => EvaluationResult<T>
  ): EvaluationResult<T> {
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
        value: null as T,
        warnings: [ createFunctionFailureWarning(expression, error) ]
      };
    }
  }
}
