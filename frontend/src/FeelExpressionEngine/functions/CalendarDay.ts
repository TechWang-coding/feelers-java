import { feelinRuntime } from '../adapters/feelin/FeelinRuntime';
import type { FeelFunctionDefinition } from '../types';
import type { FeelDate } from './types';

/**
 * Creates the `calendarDay(baseDate, offset)` definition for date arithmetic that deliberately
 * ignores working-day policy. A positive offset moves forward and a negative offset moves back.
 *
 * @returns A registered-function definition accepting a FEEL date and an integer offset.
 * @throws {TypeError} At invocation time when the date is not a FEEL date or the offset is invalid.
 */
export function createCalendarDayFunction(): FeelFunctionDefinition {
  return {
    name: 'calendarDay',
    args: ['baseDate', 'offset'],
    handler(baseDate: unknown, offset: unknown): FeelDate {
      if (!feelinRuntime.isDate(baseDate)) {
        throw new TypeError('calendarDay baseDate must be a FEEL date');
      }
      if (typeof offset !== 'number' || !Number.isInteger(offset)) {
        throw new TypeError('calendarDay offset must be an integer');
      }
      return baseDate.plus({ days: offset });
    }
  };
}
