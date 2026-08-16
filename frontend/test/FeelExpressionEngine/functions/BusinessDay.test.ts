import { describe, expect, test } from '@jest/globals';
import { date } from '@bpmn-io/feelin';
import { FeelExpressionEngine } from '../../../src/FeelExpressionEngine';
import {
  createBusinessDayFunction,
  createWeekendBusinessCalendar
} from '../../../src/FeelExpressionEngine/functions/BusinessDay';

describe('businessDay FEEL function', () => {
  test('moves Friday to Monday when the base date is an ISO string', () => {
    const engine = FeelExpressionEngine.getInstance();

    expect(getIsoDate(engine.evaluate('businessDay("2026-08-14", 1)').value)).toBe('2026-08-17');
  });

  test('moves forward and backward while excluding weekends', () => {
    const engine = FeelExpressionEngine.getInstance();

    expect(getIsoDate(engine.evaluate('businessDay(date("2026-08-14"), 1)').value)).toBe('2026-08-17');
    expect(getIsoDate(engine.evaluate('businessDay(date("2026-08-17"), -3)').value)).toBe('2026-08-12');
  });

  test('honours holiday and make-up-workday overrides', () => {
    const calendar = createWeekendBusinessCalendar({
      holidays: ['2026-08-17'],
      workingDays: ['2026-08-15']
    });
    const businessDay = createBusinessDayFunction(calendar).handler;

    expect(getIsoDate(businessDay(date('2026-08-14'), 1))).toBe('2026-08-15');
    expect(getIsoDate(businessDay(date('2026-08-15'), 1))).toBe('2026-08-18');
  });

  test('uses the engine warning contract for invalid arguments', () => {
    const engine = FeelExpressionEngine.getInstance();

    expect(engine.evaluate('businessDay(date("2026-08-14"), 1.5)').warnings[0])
      .toMatchObject({
        type: 'FUNCTION_INVOCATION_FAILURE',
        message: "Function 'businessDay' failed: businessDay offset must be an integer"
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
