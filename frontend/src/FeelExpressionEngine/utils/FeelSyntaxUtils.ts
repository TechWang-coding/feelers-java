import type { Tree } from '@lezer/common';
import type { FeelSyntaxValidationResult } from '../common/FeelExpressionTypes';

export function collectSyntaxValidationResult(tree: Tree): FeelSyntaxValidationResult {
  const errors = [];
  const cursor = tree.cursor();

  do {
    if (cursor.type.isError) {
      errors.push({ from: cursor.from, to: cursor.to });
    }
  } while (cursor.next());

  return { valid: errors.length === 0, errors };
}
