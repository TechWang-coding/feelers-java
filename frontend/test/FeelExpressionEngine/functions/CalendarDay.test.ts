import { describe, expect, test } from '@jest/globals';
import { FeelExpressionEngine } from '../../../src/FeelExpressionEngine';

describe('calendarDay FEEL function', () => {
  test('moves by calendar days including weekends', () => {
    const engine = FeelExpressionEngine.getInstance();

    expect(getIsoDate(engine.evaluate('calendarDay(date("2026-08-14"), 1)').value)).toBe('2026-08-15');
    expect(getIsoDate(engine.evaluate('calendarDay(date("2026-08-17"), -3)').value)).toBe('2026-08-14');
    expect(getIsoDate(engine.evaluate('calendarDay(date("2026-08-15"), 0)').value)).toBe('2026-08-15');
  });

  test('supports FEEL named arguments', () => {
    const engine = FeelExpressionEngine.getInstance();

    expect(getIsoDate(engine.evaluate('calendarDay(offset: 2, baseDate: date("2026-08-14"))').value))
      .toBe('2026-08-16');
  });

  test('uses the engine warning contract for invalid arguments', () => {
    const engine = FeelExpressionEngine.getInstance();

    expect(engine.evaluate('calendarDay(date("2026-08-14"), 1.5)').warnings[0])
      .toMatchObject({
        type: 'FUNCTION_INVOCATION_FAILURE',
        message: "Function 'calendarDay' failed: calendarDay offset must be an integer"
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
