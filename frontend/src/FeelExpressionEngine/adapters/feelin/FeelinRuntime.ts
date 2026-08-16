import {
  date,
  evaluate,
  parseExpression,
  parseUnaryTests,
  unaryTest
} from '@bpmn-io/feelin';
import type {
  FeelEvaluationResult,
  FeelSyntaxError,
  FeelSyntaxValidationResult,
  FeelVariables,
  FeelWarning
} from '../../types';
import type { FeelDate } from '../../functions/types';

/**
 * Isolates the Feelin package behind local contracts so third-party result types, parse trees, and
 * temporal values do not leak into the engine's public API.
 */
export const feelinRuntime = Object.freeze({
  /**
   * Evaluates a complete expression through Feelin and maps its diagnostics to local contracts.
   *
   * @param expression Source expression to evaluate.
   * @param variables Runtime context supplied by the engine.
   * @returns Evaluated value and mapped warnings.
   */
  evaluate(expression: string, variables: FeelVariables): FeelEvaluationResult<unknown> {
    const result = evaluate(expression, variables);
    return { value: result.value, warnings: mapWarnings(result.warnings) };
  },

  /**
   * Evaluates a unary test while keeping Feelin's result type behind this adapter.
   *
   * @param expression Unary-test source.
   * @param variables Runtime context supplied by the engine.
   * @returns Boolean or `null` result and mapped warnings.
   */
  unaryTest(expression: string, variables: FeelVariables): FeelEvaluationResult<boolean> {
    const result = unaryTest(expression, variables);
    return { value: result.value, warnings: mapWarnings(result.warnings) };
  },

  /**
   * Parses an expression and extracts only local syntax ranges, avoiding parser-tree leakage.
   *
   * @param expression Source expression to parse.
   * @param variables Runtime context needed by the parser.
   * @returns Local syntax-validation outcome without evaluating the expression.
   */
  validateExpression(expression: string, variables: FeelVariables): FeelSyntaxValidationResult {
    return collectSyntaxValidationResult(parseExpression(expression, variables, undefined));
  },

  /**
   * Parses a unary test without executing it, using the same local validation contract as regular
   * expressions.
   *
   * @param expression Unary-test source to parse.
   * @param variables Runtime context needed by the parser.
   * @returns Local syntax-validation outcome.
   */
  validateUnaryTest(expression: string, variables: FeelVariables): FeelSyntaxValidationResult {
    return collectSyntaxValidationResult(parseUnaryTests(expression, variables, undefined));
  },

  /**
   * Creates a runtime date at the only boundary that knows Feelin's temporal implementation.
   *
   * @param isoDate ISO date input to parse.
   * @returns A local structural date view, which may have `isValid` set to `false`.
   */
  createDate(isoDate: string): FeelDate {
    return date(isoDate);
  },

  /**
   * Narrows an untyped FEEL value to the minimal date contract needed by calendar functions.
   * Structural checking supports values created by the runtime without exposing its concrete class.
   *
   * @param value Runtime value to inspect.
   * @returns Whether the value is a valid FEEL date with the required operations.
   */
  isDate(value: unknown): value is FeelDate {
    if (!isRecord(value) || value.isValid !== true) return false;
    return typeof value.weekday === 'number'
      && typeof value.plus === 'function'
      && typeof value.toISODate === 'function';
  }
});

/**
 * Traverses a third-party parse tree once to convert parser errors into the engine's stable syntax
 * result. Keeping this here prevents parser implementation details from entering validation code.
 *
 * @param tree Feelin parser tree.
 * @returns Local validation result containing every error range.
 */
function collectSyntaxValidationResult(tree: { cursor(): SyntaxCursor }): FeelSyntaxValidationResult {
  const errors: FeelSyntaxError[] = [];
  const cursor = tree.cursor();

  do {
    if (cursor.type.isError) {
      errors.push({ from: cursor.from, to: cursor.to });
    }
  } while (cursor.next());

  return { valid: errors.length === 0, errors };
}

/**
 * Copies runtime warnings into plain local data so callers cannot depend on third-party object
 * identities or mutable nested values.
 *
 * @param warnings Runtime diagnostics emitted by Feelin.
 * @returns Immutable-by-contract warning data owned by this feature.
 */
function mapWarnings(warnings: readonly FeelWarning[]): readonly FeelWarning[] {
  return warnings.map((warning) => ({
    type: warning.type,
    message: warning.message,
    position: { ...warning.position },
    details: {
      template: warning.details.template,
      values: { ...warning.details.values }
    }
  }));
}

/**
 * Establishes a safe object boundary before reading unknown runtime values during structural type
 * checks.
 *
 * @param value Candidate runtime value.
 * @returns Whether the value is a non-null object with readable properties.
 */
function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null;
}

/** Minimal parser-cursor contract required to collect syntax errors without exporting parser types. */
interface SyntaxCursor {
  /** Current parser node classification. */
  readonly type: { readonly isError: boolean };
  /** Start offset of the current parser node. */
  readonly from: number;
  /** End offset of the current parser node. */
  readonly to: number;
  /** Advances to the next parser node. */
  next(): boolean;
}
