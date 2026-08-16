/** A synchronous host function that can be invoked from a FEEL expression. */
export type FeelFunctionHandler = (...args: unknown[]) => unknown;

/** Read-only values made available to an expression during evaluation. */
export type FeelVariables = Readonly<Record<string, unknown>>;

/** Declares a host function and the argument names used by FEEL named-argument calls. */
export interface FeelFunctionDefinition {
  /** Name used by FEEL expressions. */
  name: string;
  /** Parameter names used by FEEL named-argument calls. */
  args: readonly string[];
  /** Performs the synchronous host-side operation for the supplied positional arguments. */
  handler: FeelFunctionHandler;
}

/** Describes one host-function failure reported to an application logger. */
export interface FeelFunctionFailureLogEntry {
  /** Name of the registered function that failed. */
  functionName: string;
  /** Expression being evaluated when the failure occurred. */
  expression: string;
  /** Original failure retained for application-specific diagnostics. */
  error: unknown;
}

/** Application-provided, structured logging boundary for custom function failures. */
export interface FeelExpressionLogger {
  /** Records a function failure without changing the engine's warning result. */
  functionFailed(entry: FeelFunctionFailureLogEntry): void;
}

/** Optional collaborators that customize engine behaviour without exposing runtime internals. */
export interface FeelExpressionEngineOptions {
  /** Receives registered-function failures that are converted into evaluation warnings. */
  logger?: FeelExpressionLogger;
}

/** A diagnostic emitted while evaluating an expression without necessarily aborting evaluation. */
export interface FeelWarning {
  /** Stable diagnostic category supplied by the expression runtime. */
  type: string;
  /** Human-readable description suitable for display or logging. */
  message: string;
  /** Zero-based source range of the relevant expression fragment. */
  position: {
    from: number;
    to: number;
  };
  /** Message template and values retained for structured rendering or localization. */
  details: {
    template: string;
    values: Readonly<Record<string, unknown>>;
  };
}

/** Result of evaluating an expression; failures represented as warnings have a `null` value. */
export interface FeelEvaluationResult<T> {
  /** Evaluated value, or `null` when a registered function failure is converted to a warning. */
  value: T | null;
  /** Diagnostics emitted by the underlying runtime or the engine. */
  warnings: readonly FeelWarning[];
}

/** Source range of a syntax error found without executing an expression. */
export interface FeelSyntaxError {
  from: number;
  to: number;
}

/** Syntax-only validation outcome used to keep parsing separate from evaluation. */
export interface FeelSyntaxValidationResult {
  /** Whether parsing found no syntax errors. */
  valid: boolean;
  /** All syntax error ranges discovered during parsing. */
  errors: readonly FeelSyntaxError[];
}
