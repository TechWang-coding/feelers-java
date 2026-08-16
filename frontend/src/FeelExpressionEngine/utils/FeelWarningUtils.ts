import type { Warning } from '@bpmn-io/feelin';
import { FeelFunctionInvocationError } from '../common/FeelFunctionInvocationError';
import { formatError } from './FeelErrorUtils';

export function createFunctionFailureWarning(
  expression: string,
  error: FeelFunctionInvocationError
): Warning {
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
