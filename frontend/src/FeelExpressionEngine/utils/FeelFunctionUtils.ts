import { FeelFunctionInvocationError } from '../common/FeelFunctionInvocationError';
import type { FeelFunctionDefinition, FeelFunctionHandler } from '../common/FeelExpressionTypes';

export function validateFunctionDefinition(definition: FeelFunctionDefinition): void {
  if (!definition || typeof definition !== 'object') {
    throw new TypeError('A FEEL function definition must be an object');
  }
  if (!isTrimmedNonEmptyString(definition.name)) {
    throw new TypeError('A FEEL function name must be a non-empty string without surrounding whitespace');
  }
  if (!Array.isArray(definition.args) || definition.args.some((arg) => !isTrimmedNonEmptyString(arg))) {
    throw new TypeError(`FEEL function "${definition.name}" must declare non-empty argument names`);
  }
  if (new Set(definition.args).size !== definition.args.length) {
    throw new TypeError(`FEEL function "${definition.name}" cannot declare duplicate argument names`);
  }
  if (typeof definition.handler !== 'function') {
    throw new TypeError(`FEEL function "${definition.name}" handler must be a function`);
  }
}

export function wrapFunction(
  definition: FeelFunctionDefinition
): FeelFunctionHandler & { $args: readonly string[] } {
  const wrapped = ((...args: unknown[]): unknown => {
    try {
      const result = definition.handler(...args);
      if (isThenable(result)) {
        throw new TypeError('Custom FEEL functions must return synchronously, not a Promise');
      }
      return result;
    } catch (error) {
      throw new FeelFunctionInvocationError(definition.name, error);
    }
  }) as FeelFunctionHandler & { $args: readonly string[] };
  wrapped.$args = definition.args;
  return wrapped;
}

function isTrimmedNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0 && value.trim() === value;
}

function isThenable(value: unknown): value is { then: unknown } {
  return typeof value === 'object' && value !== null && 'then' in value;
}
