import { formatError } from './formatError';

/** Internal marker used to distinguish an application function failure from a feelin failure. */
export class FeelFunctionInvocationError extends Error {
  /**
   * Marks a host-function failure so the engine can convert it to a warning without swallowing
   * unrelated runtime failures.
   *
   * @param functionName Registered function that failed.
   * @param cause Original thrown value retained for logging.
   */
  constructor(
    readonly functionName: string,
    readonly cause: unknown
  ) {
    super(`Custom FEEL function "${functionName}" failed: ${formatError(cause)}`);
    this.name = 'FeelFunctionInvocationError';
  }
}
