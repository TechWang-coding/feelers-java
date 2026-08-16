import { formatError } from '../utils/FeelErrorUtils';

/** Internal marker used to distinguish an application function failure from a feelin failure. */
export class FeelFunctionInvocationError extends Error {
  constructor(
    readonly functionName: string,
    readonly cause: unknown
  ) {
    super(`Custom FEEL function "${functionName}" failed: ${formatError(cause)}`);
    this.name = 'FeelFunctionInvocationError';
  }
}
