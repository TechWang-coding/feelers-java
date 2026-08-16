import { describe, expect, test } from '@jest/globals';
import { FeelExpressionEngine, type FeelExpressionLogger } from '../../src/FeelExpressionEngine';

describe('FeelExpressionEngine.create', () => {
  test('evaluates expressions and unary tests through a reusable engine', () => {
    const engine = FeelExpressionEngine.create();

    expect(engine.evaluate('amount * 2', { amount: 21 })).toEqual({ value: 42, warnings: [] });
    expect(engine.unaryTest('> minimum', { '?': 5, minimum: 3 }))
      .toEqual({ value: true, warnings: [] });
    expect(getIsoDate(engine.evaluate('businessDay("2026-08-14", 1)').value)).toBe('2026-08-17');
  });

  test('provides one default instance with calendar functions registered', () => {
    const engine = FeelExpressionEngine.getInstance();

    expect(FeelExpressionEngine.getInstance()).toBe(engine);
    expect(getIsoDate(engine.evaluate('calendarDay(date("2026-08-14"), 1)').value)).toBe('2026-08-15');
    expect(getIsoDate(engine.evaluate('businessDay(date("2026-08-14"), 1)').value)).toBe('2026-08-17');
  });

  test('registers custom functions with positional and named arguments', () => {
    const engine = FeelExpressionEngine.create([ {
      name: 'discounted',
      args: ['amount', 'rate'],
      handler: (amount, rate) => Number(amount) * (1 - Number(rate))
    } ]);

    expect(engine.evaluate('discounted(100, 0.2)')).toEqual({ value: 80, warnings: [] });
    expect(engine.evaluate('discounted(rate: 0.2, amount: 100)')).toEqual({ value: 80, warnings: [] });
    expect(getIsoDate(engine.evaluate('calendarDay(date("2026-08-14"), 1)').value)).toBe('2026-08-15');
  });

  test('rejects duplicate and built-in function names during startup registration', () => {
    expect(() => FeelExpressionEngine.create([ {
      name: 'count', args: [], handler: () => 0
    } ])).toThrow('conflicts with a feelin built-in function');

    expect(() => FeelExpressionEngine.create([
      { name: 'answer', args: [], handler: () => 42 },
      { name: 'answer', args: [], handler: () => 0 }
    ])).toThrow('already registered');

    expect(() => FeelExpressionEngine.create([ {
      name: 'businessDay', args: [], handler: () => null
    } ])).toThrow('already registered');
  });

  test('does not let variables replace registered or built-in functions', () => {
    const engine = FeelExpressionEngine.create([ {
      name: 'answer', args: [], handler: () => 42
    } ]);

    expect(engine.evaluate('answer()', { answer: () => 0 })).toEqual({ value: 42, warnings: [] });
    expect(engine.evaluate('count([1, 2])', { count: () => 0 })).toEqual({ value: 2, warnings: [] });
  });

  test('converts custom-function exceptions into a warning and writes a structured log', () => {
    const failures: unknown[] = [];
    const logger: FeelExpressionLogger = { functionFailed: (entry) => failures.push(entry) };
    const engine = FeelExpressionEngine.create([ {
      name: 'failingFunction',
      args: [],
      handler: () => { throw new TypeError('function failed'); }
    } ], { logger });

    expect(engine.evaluate('failingFunction()')).toEqual({
      value: null,
      warnings: [ {
        type: 'FUNCTION_INVOCATION_FAILURE',
        message: "Function 'failingFunction' failed: function failed",
        position: { from: 0, to: 17 },
        details: {
          template: "Function '{name}' failed: {message}",
          values: { name: 'failingFunction', message: 'function failed' }
        }
      } ]
    });
    expect(failures).toEqual([ expect.objectContaining({
      functionName: 'failingFunction',
      expression: 'failingFunction()',
      error: expect.any(TypeError)
    }) ]);
  });

  test('turns asynchronous custom functions into a warning', () => {
    const engine = FeelExpressionEngine.create([ {
      name: 'asyncValue', args: [], handler: () => Promise.resolve(42)
    } ]);

    expect(engine.evaluate('asyncValue()').warnings[0]).toMatchObject({
      type: 'FUNCTION_INVOCATION_FAILURE',
      message: expect.stringContaining('must return synchronously')
    });
  });
});

function getIsoDate(value: unknown): string | null {
  if (!isFeelDate(value)) throw new TypeError('Expected a FEEL date');
  return value.toISODate();
}

function isFeelDate(value: unknown): value is { toISODate(): string | null } {
  return typeof value === 'object'
    && value !== null
    && 'toISODate' in value
    && typeof value.toISODate === 'function';
}
