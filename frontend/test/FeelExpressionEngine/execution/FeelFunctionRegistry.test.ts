import { describe, expect, test } from '@jest/globals';
import { FeelFunctionRegistry } from '../../../src/FeelExpressionEngine/execution/FeelFunctionRegistry';

describe('FeelFunctionRegistry', () => {
  test('freezes the function registry after startup', () => {
    const registry = new FeelFunctionRegistry();
    registry.register({ name: 'answer', args: [], handler: () => 42 });
    registry.freeze();

    expect(() => registry.register({ name: 'other', args: [], handler: () => 0 }))
      .toThrow('has been frozen');
  });
});
