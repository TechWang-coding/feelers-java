import type { FeelFunctionDefinition } from '../types';

/**
 * Verifies the structural contract required before a host function enters the runtime registry.
 * Validation occurs before wrapping so invalid definitions fail predictably during startup.
 *
 * @param definition Candidate function contract.
 * @throws {TypeError} When the name, arguments, or handler violate the registration contract.
 */
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

/**
 * Accepts only stable identifiers without invisible leading or trailing whitespace, preventing
 * function names and argument names that look valid but cannot be called reliably.
 *
 * @param value Candidate identifier.
 * @returns Whether the value is a trimmed, non-empty string.
 */
function isTrimmedNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0 && value.trim() === value;
}
