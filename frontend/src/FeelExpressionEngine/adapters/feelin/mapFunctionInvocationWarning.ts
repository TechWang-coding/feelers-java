import type { FeelWarning } from '../../types';
import { FeelFunctionInvocationError } from '../../errors/FeelFunctionInvocationError';
import { formatError } from '../../errors/formatError';

/**
 * Maps a classified host-function failure to the engine's stable warning contract. This preserves
 * a runtime-independent public result while retaining source location and structured details.
 *
 * @param expression Source expression used to calculate the diagnostic range.
 * @param error Classified host-function failure.
 * @returns Warning returned instead of propagating the host-function failure.
 */
export function mapFunctionInvocationWarning(
  expression: string,
  error: FeelFunctionInvocationError
): FeelWarning {
  const message = formatError(error.cause);
  return {
    type: 'FUNCTION_INVOCATION_FAILURE',
    message: `Function '${error.functionName}' failed: ${message}`,
    position: { from: 0, to: expression.length },
    details: {
      template: "Function '{name}' failed: {message}",
      values: { name: error.functionName, message }
    }
  };
}
