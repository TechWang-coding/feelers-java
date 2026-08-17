package com.tech.feelers.expression.functions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Working-day policy supplied by the host application.
 *
 * <p>Kept separate from the {@code businessDay} algorithm so holiday and make-up-day policy can
 * change without touching date arithmetic, and so both runtimes can be driven by the same calendar
 * data.
 */
public interface BusinessCalendar {

  /**
   * Determines whether a date counts toward a business-day offset.
   *
   * @param date calendar date selected while traversing an offset
   * @return whether the date is a working day under this policy
   */
  boolean isWorkingDay(LocalDate date);

  /**
   * Creates the default weekend-based calendar, letting applications add holiday and make-up-day
   * policy without replacing the traversal algorithm.
   *
   * <p>Explicit working days win over holidays, and holidays win over the weekend rule, so a
   * make-up workday on a Saturday behaves correctly.
   *
   * @param holidays non-working dates, for example public holidays
   * @param workingDays dates that are working days despite a weekend or holiday
   * @return a calendar applying that precedence
   */
  static BusinessCalendar weekendBased(Set<LocalDate> holidays, Set<LocalDate> workingDays) {
    Set<LocalDate> holidaySet = Set.copyOf(holidays);
    Set<LocalDate> workingSet = Set.copyOf(workingDays);
    return date -> {
      if (workingSet.contains(date)) {
        return true;
      }
      if (holidaySet.contains(date)) {
        return false;
      }
      return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    };
  }

  /**
   * Creates the plain weekend calendar used when no holiday data is configured.
   *
   * @return a calendar treating Saturday and Sunday as non-working
   */
  static BusinessCalendar weekendBased() {
    return weekendBased(Set.of(), Set.of());
  }
}
