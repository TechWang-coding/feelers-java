/** A synchronous JavaScript function that can be invoked from a FEEL expression. */
export type FeelFunctionHandler = (...args: unknown[]) => unknown;

export type FeelVariables = Readonly<Record<string, unknown>>;

export interface FeelFunctionDefinition {
  /** Name used by FEEL expressions. */
  name: string;
  /** Parameter names used by FEEL named-argument calls. */
  args: readonly string[];
  handler: FeelFunctionHandler;
}

export interface FeelFunctionFailureLogEntry {
  functionName: string;
  expression: string;
  error: unknown;
}

/** Application-provided, structured logging boundary for custom function failures. */
export interface FeelExpressionLogger {
  functionFailed(entry: FeelFunctionFailureLogEntry): void;
}

export interface FeelExpressionEngineOptions {
  logger?: FeelExpressionLogger;
}

export interface FeelSyntaxError {
  from: number;
  to: number;
}

export interface FeelSyntaxValidationResult {
  valid: boolean;
  errors: readonly FeelSyntaxError[];
}
