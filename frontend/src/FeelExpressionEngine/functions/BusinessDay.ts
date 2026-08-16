import { feelinRuntime } from '../adapters/feelin/FeelinRuntime';
import type { FeelFunctionDefinition } from '../types';
import type { FeelDate } from './types';

/** The business-calendar boundary supplied by the host application. */
export interface BusinessCalendar {
  /**
   * Determines whether an ISO date counts toward a business-day offset.
   *
   * @param isoDate Valid ISO calendar date selected by `businessDay`.
   * @returns Whether the date is a working day under this calendar's policy.
   */
  isWorkingDay(isoDate: string): boolean;
}

/** Optional overrides for the default weekend-based business-calendar policy. */
export interface WeekendBusinessCalendarOptions {
  /** Non-working ISO dates, for example public holidays. */
  holidays?: Iterable<string>;
  /** Working ISO dates that override a weekend or holiday, for example make-up workdays. */
  workingDays?: Iterable<string>;
}

/**
 * Creates the default weekend-based calendar so applications can add holiday and make-up-day
 * policy without replacing the `businessDay` algorithm.
 *
 * @param options Optional non-working and explicit working ISO-date overrides.
 * @returns A calendar where explicit working days take precedence over holidays and weekends.
 * @throws {Error} When a calendar lookup receives an invalid ISO date.
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

      const candidate = feelinRuntime.createDate(isoDate);
      if (!candidate.isValid) {
        throw new Error(`Business calendar received an invalid ISO date: ${isoDate}`);
      }
      return candidate.weekday <= 5;
    }
  };
}

/**
 * Creates the `businessDay(baseDate, offset)` definition used to move through a supplied working
 * calendar. Positive offsets move forward; the base date is not counted, so Friday plus one is
 * Monday.
 *
 * @param calendar Working-day policy used for each traversed date.
 * @returns A registered-function definition accepting an ISO date or FEEL date and an integer.
 * @throws {TypeError} At invocation time when the date or offset is invalid.
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

/**
 * Normalizes the two supported date inputs before date arithmetic begins, keeping the public
 * function contract independent of the underlying runtime representation.
 *
 * @param value ISO date string or runtime date value supplied by FEEL.
 * @returns A valid runtime date.
 * @throws {TypeError} When the value is not a supported valid date input.
 */
function toFeelDate(value: unknown): FeelDate {
  if (feelinRuntime.isDate(value)) return value;
  if (typeof value === 'string') {
    const parsed = feelinRuntime.createDate(value);
    if (parsed.isValid) return parsed;
  }
  throw new TypeError('businessDay baseDate must be an ISO date string or FEEL date');
}
