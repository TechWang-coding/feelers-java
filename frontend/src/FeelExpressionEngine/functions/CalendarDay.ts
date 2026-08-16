import type { FeelFunctionDefinition } from '../common/FeelExpressionTypes';
import type { FeelDate } from './BusinessDay';

/**
 * Registers `calendarDay(baseDate, offset)`, which moves by calendar days without excluding
 * weekends or holidays. A positive offset moves forward and a negative offset moves backward.
 */
export function createCalendarDayFunction(): FeelFunctionDefinition {
  return {
    name: 'calendarDay',
    args: ['baseDate', 'offset'],
    handler(baseDate: unknown, offset: unknown): FeelDate {
      if (!isFeelDate(baseDate)) {
        throw new TypeError('calendarDay baseDate must be a FEEL date');
      }
      if (typeof offset !== 'number' || !Number.isInteger(offset)) {
        throw new TypeError('calendarDay offset must be an integer');
      }

      return baseDate.plus({ days: offset });
    }
  };
}

function isFeelDate(value: unknown): value is FeelDate {
  return typeof value === 'object'
    && value !== null
    && 'isValid' in value
    && (value as { isValid: unknown }).isValid === true
    && 'plus' in value;
}
