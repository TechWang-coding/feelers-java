import { date as feelDate } from '@bpmn-io/feelin';
import type { FeelFunctionDefinition } from '../common/FeelExpressionTypes';

/** A FEEL date value as represented by the browser FEEL runtime. */
export type FeelDate = ReturnType<typeof feelDate>;

/** The business-calendar boundary supplied by the host application. */
export interface BusinessCalendar {
  isWorkingDay(isoDate: string): boolean;
}

export interface WeekendBusinessCalendarOptions {
  /** Non-working ISO dates, for example public holidays. */
  holidays?: Iterable<string>;
  /** Working ISO dates that override a weekend or holiday, for example make-up workdays. */
  workingDays?: Iterable<string>;
}

/**
 * Creates the default weekend-based business calendar. `workingDays` wins over `holidays`.
 */
export function createWeekendBusinessCalendar(
  options: WeekendBusinessCalendarOptions = {}
): BusinessCalendar {
  const holidays = new Set(options.holidays ?? []);
  const workingDays = new Set(options.workingDays ?? []);

  return {
    isWorkingDay(isoDate: string): boolean {
      if (workingDays.has(isoDate)) return true;
      if (holidays.has(isoDate)) return false;

      const candidate = feelDate(isoDate);
      if (!candidate.isValid) {
        throw new Error(`Business calendar received an invalid ISO date: ${isoDate}`);
      }
      return candidate.weekday <= 5;
    }
  };
}

/**
 * Registers `businessDay(baseDate, offset)`. `baseDate` may be an ISO date string or a FEEL date.
 * Positive offsets move forward; the base date is not counted, so Friday plus one business day is
 * Monday.
 */
export function createBusinessDayFunction(
  calendar: BusinessCalendar = createWeekendBusinessCalendar()
): FeelFunctionDefinition {
  return {
    name: 'businessDay',
    args: ['baseDate', 'offset'],
    handler(baseDate: unknown, offset: unknown): FeelDate {
      const initialDate = toFeelDate(baseDate);
      if (typeof offset !== 'number' || !Number.isInteger(offset)) {
        throw new TypeError('businessDay offset must be an integer');
      }

      let result = initialDate;
      const direction = Math.sign(offset);
      let remaining = Math.abs(offset);

      while (remaining > 0) {
        result = result.plus({ days: direction });
        const isoDate = result.toISODate();
        if (isoDate === null) {
          throw new Error('businessDay produced an invalid date');
        }
        if (calendar.isWorkingDay(isoDate)) remaining--;
      }

      return result;
    }
  };
}

function isFeelDate(value: unknown): value is FeelDate {
  return typeof value === 'object'
    && value !== null
    && 'isValid' in value
    && (value as { isValid: unknown }).isValid === true
    && 'plus' in value
    && 'toISODate' in value;
}

function toFeelDate(value: unknown): FeelDate {
  if (isFeelDate(value)) return value;

  if (typeof value === 'string') {
    const parsed = feelDate(value);
    if (parsed.isValid) return parsed;
  }

  throw new TypeError('businessDay baseDate must be an ISO date string or FEEL date');
}
