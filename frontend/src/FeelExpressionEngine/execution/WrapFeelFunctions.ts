import { FeelFunctionInvocationError } from '../errors/FeelFunctionInvocationError';
import type { FeelFunctionDefinition, FeelFunctionHandler } from '../types';

/** Runtime function shape required for FEEL named-argument invocation. */
export type WrappedFeelFunction = FeelFunctionHandler & {
  readonly $args: readonly string[];
};

/**
 * Converts validated definitions into the immutable runtime function map consumed by FEEL.
 * The wrapper establishes one error and synchronous-return policy for every registered function.
 *
 * @param definitions Definitions accepted by the startup registry.
 * @returns Functions keyed by their FEEL names and annotated with argument names.
 */
export function wrapFeelFunctions(
  definitions: readonly FeelFunctionDefinition[]
): Readonly<Record<string, WrappedFeelFunction>> {
  return Object.freeze(Object.fromEntries(definitions.map((definition) => [
    definition.name,
    wrapFeelFunction(definition)
  ])));
}

/**
 * Wraps one handler so host failures are distinguishable from runtime failures and Promises cannot
 * enter a synchronous FEEL evaluation.
 *
 * @param definition Validated function definition to expose to FEEL.
 * @returns A handler carrying its named-argument metadata.
 * @throws {FeelFunctionInvocationError} When the handler throws or returns a Promise.
 */
function wrapFeelFunction(definition: FeelFunctionDefinition): WrappedFeelFunction {
  const handler = (...args: unknown[]): unknown => {
    try {
      const result = definition.handler(...args);
      if (isThenable(result)) {
        throw new TypeError('Custom FEEL functions must return synchronously, not a Promise');
      }
      return result;
    } catch (error) {
      throw new FeelFunctionInvocationError(definition.name, error);
    }
  };

  return Object.assign(handler, { $args: definition.args });
}

/**
 * Detects promise-like results structurally because host functions may return values from another
 * JavaScript realm or promise implementation.
 *
 * @param value Handler result to inspect.
 * @returns Whether the value advertises a `then` member.
 */
function isThenable(value: unknown): value is { then: unknown } {
  return typeof value === 'object' && value !== null && 'then' in value;
}
