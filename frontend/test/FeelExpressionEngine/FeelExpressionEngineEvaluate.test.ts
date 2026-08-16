import { describe, expect, test } from '@jest/globals';
import { date } from '@bpmn-io/feelin';
import { FeelExpressionEngine } from '../../src/FeelExpressionEngine';

describe('FeelExpressionEngine.evaluate', () => {
  const engine = FeelExpressionEngine.getInstance();

  test('evaluates the registered calendarDay function', () => {
    expect(getIsoDate(engine.evaluate('calendarDay(date("2026-08-14"), 1)').value)).toBe('2026-08-15');
  });

  test('evaluates the registered businessDay function', () => {
    expect(getIsoDate(engine.evaluate('businessDay("2026-08-14", 1)').value)).toBe('2026-08-17');
  });

  test('uses registered custom functions in unary tests', () => {
    expect(engine.unaryTest('>= businessDay("2026-08-14", 1)', { '?': date('2026-08-17') }))
      .toEqual({ value: true, warnings: [] });
  });

  test('compares UTC timestamps from startDate and endDate', () => {
    expect(engine.evaluate(
      'date and time(startDate) < date and time(endDate)',
      {
        startDate: '2026-08-14T08:00:00Z',
        endDate: '2026-08-15T08:00:00Z'
      }
    )).toEqual({ value: true, warnings: [] });

    expect(engine.evaluate(
      'date and time(startDate) = date and time(endDate)',
      {
        startDate: '2026-08-14T08:00:00Z',
        endDate: '2026-08-14T08:00:00Z'
      }
    )).toEqual({ value: true, warnings: [] });
  });

  test('returns a warning when a registered function receives invalid input', () => {
    expect(engine.evaluate('businessDay("2026-08-14", 1.5)').warnings[0]).toMatchObject({
      type: 'FUNCTION_INVOCATION_FAILURE',
      message: "Function 'businessDay' failed: businessDay offset must be an integer"
    });
  });

  test('validates complete FEEL expressions without evaluating them', () => {
    expect(engine.validate('businessDay("2026-08-14", 1) > date("2026-08-15")'))
      .toEqual({ valid: true, errors: [] });

    expect(engine.validate('businessDay("2026-08-14", )')).toEqual({
      valid: false,
      errors: [ { from: 26, to: 26 } ]
    });
  });

  test('validates FEEL unary tests without evaluating them', () => {
    expect(engine.validateUnaryTest('>= businessDay("2026-08-14", 1)'))
      .toEqual({ valid: true, errors: [] });

    expect(engine.validateUnaryTest('>=')).toEqual({
      valid: false,
      errors: [ { from: 2, to: 2 } ]
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
