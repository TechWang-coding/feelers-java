package com.tech.feelers.expression.functions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import com.tech.feelers.expression.FeelFunctionDefinition;

/**
 * Date-offset functions shared with the TypeScript engine.
 *
 * <p>Both functions take their base date from the expression rather than a machine clock, so a
 * validation result never depends on which host evaluated it.
 */
public final class DateFunctions {

  private DateFunctions() {
  }

  /**
   * Creates {@code businessDay(baseDate, offset)}, which moves through working days only.
   *
   * <p>The base date is not counted, so Friday plus one is Monday under a weekend calendar.
   *
   * @param calendar working-day policy applied to each traversed date
   * @return the registered-function definition
   */
  public static FeelFunctionDefinition businessDay(BusinessCalendar calendar) {
    Objects.requireNonNull(calendar, "calendar");
    return new FeelFunctionDefinition("businessDay", List.of("baseDate", "offset"), args -> {
      LocalDate base = toLocalDate(args.get(0), "businessDay baseDate");
      int offset = toInteger(args.get(1), "businessDay offset");

      LocalDate result = base;
      int direction = Integer.signum(offset);
      int remaining = Math.abs(offset);
      while (remaining > 0) {
        result = result.plusDays(direction);
        if (calendar.isWorkingDay(result)) {
          remaining--;
        }
      }
      return result;
    });
  }

  /**
   * Creates {@code calendarDay(baseDate, offset)} for date arithmetic that deliberately ignores
   * working-day policy.
   *
   * @return the registered-function definition
   */
  public static FeelFunctionDefinition calendarDay() {
    return new FeelFunctionDefinition("calendarDay", List.of("baseDate", "offset"), args -> {
      LocalDate base = toLocalDate(args.get(0), "calendarDay baseDate");
      return base.plusDays(toInteger(args.get(1), "calendarDay offset"));
    });
  }

  /**
   * Accepts the two supported date inputs so a rule may reference either a FEEL date value or an
   * ISO string from submitted data.
   *
   * @param value argument supplied by the expression
   * @param label argument description used in failure messages
   * @return the parsed date
   * @throws IllegalArgumentException when the value is not a usable date
   */
  private static LocalDate toLocalDate(Object value, String label) {
    if (value instanceof LocalDate date) {
      return date;
    }
    if (value instanceof String text) {
      try {
        return LocalDate.parse(text);
      } catch (DateTimeParseException exception) {
        throw new IllegalArgumentException(label + " must be a valid ISO date", exception);
      }
    }
    throw new IllegalArgumentException(label + " must be an ISO date string or FEEL date");
  }

  /**
   * Requires a whole-number offset because a fractional day offset has no defined meaning for
   * either calendar or working-day traversal.
   *
   * @param value argument supplied by the expression
   * @param label argument description used in failure messages
   * @return the offset as an int
   * @throws IllegalArgumentException when the value is not an integral number
   */
  private static int toInteger(Object value, String label) {
    if (value instanceof BigDecimal number && number.stripTrailingZeros().scale() <= 0) {
      return number.intValueExact();
    }
    if (value instanceof Integer || value instanceof Long) {
      return ((Number) value).intValue();
    }
    throw new IllegalArgumentException(label + " must be an integer");
  }
}
