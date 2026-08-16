import { describe, expect, test } from '@jest/globals';
import { FeelExpressionEngine } from '../../src/FeelExpressionEngine/FeelExpressionEngine';
import { createCalendarDayFunction } from '../../src/FeelExpressionEngine/functions/CalendarDay';

describe('calendarDay FEEL function', () => {
  test('moves by calendar days including weekends', () => {
    const engine = FeelExpressionEngine.create([ createCalendarDayFunction() ]);

    expect(engine.evaluate('calendarDay(date("2026-08-14"), 1)').value.toISODate())
      .toBe('2026-08-15');
    expect(engine.evaluate('calendarDay(date("2026-08-17"), -3)').value.toISODate())
      .toBe('2026-08-14');
    expect(engine.evaluate('calendarDay(date("2026-08-15"), 0)').value.toISODate())
      .toBe('2026-08-15');
  });

  test('supports FEEL named arguments', () => {
    const engine = FeelExpressionEngine.create([ createCalendarDayFunction() ]);

    expect(engine.evaluate('calendarDay(offset: 2, baseDate: date("2026-08-14"))').value.toISODate())
      .toBe('2026-08-16');
  });

  test('uses the engine warning contract for invalid arguments', () => {
    const engine = FeelExpressionEngine.create([ createCalendarDayFunction() ]);

    expect(engine.evaluate('calendarDay(date("2026-08-14"), 1.5)').warnings[0])
      .toMatchObject({
        type: 'FUNCTION_INVOCATION_FAILURE',
        message: "Function 'calendarDay' failed: calendarDay offset must be an integer"
      });
  });
});
