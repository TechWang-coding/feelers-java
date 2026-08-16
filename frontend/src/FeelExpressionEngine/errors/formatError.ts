/**
 * Produces a safe, stable message from an unknown thrown value so error reporting does not assume
 * every JavaScript throw is an `Error` instance.
 *
 * @param error Value caught from application or runtime code.
 * @returns Human-readable error text.
 */
export function formatError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
