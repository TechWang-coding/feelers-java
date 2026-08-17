package com.tech.feelers.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.tech.feelers.expression.errors.ReferenceDataException;
import com.tech.feelers.expression.functions.BusinessCalendar;
import com.tech.feelers.expression.functions.FieldMetadata;
import com.tech.feelers.expression.functions.ReferenceDataClient;

/** Verifies the engine's registration invariants, failure classification, and function contracts. */
class FeelExpressionEngineTest {

  /** Deterministic reference data so engine tests never depend on a running service. */
  private static final class StubClient implements ReferenceDataClient {
    private final ReferenceDataException failure;

    private StubClient() {
      this(null);
    }

    private StubClient(ReferenceDataException failure) {
      this.failure = failure;
    }

    @Override
    public FieldMetadata findField(String fieldKey) {
      if (failure != null) {
        throw failure;
      }
      if ("channel".equals(fieldKey)) {
        return new FieldMetadata("channel", null);
      }
      return new FieldMetadata(fieldKey, "CUSTOMER_TYPE");
    }

    @Override
    public boolean containsValue(String dataSourceUniqueName, String value) {
      return "CUSTOMER_TYPE".equals(dataSourceUniqueName)
          && List.of("ENTERPRISE", "RETAIL").contains(value);
    }
  }

  private static FeelExpressionEngine engineWith(ReferenceDataClient client) {
    return FeelExpressionEngine.createDefault(client, BusinessCalendar.weekendBased());
  }

  @Test
  @DisplayName("keeps decimal arithmetic exact where native floating point would not")
  void evaluatesDecimalArithmeticExactly() {
    FeelExpressionEngine engine = engineWith(new StubClient());

    // Each of these is false under IEEE754 double arithmetic.
    List.of("0.1 + 0.2 = 0.3",
            "4.35 + 1.05 = 5.40",
            "100.10 + 200.20 = 300.30",
            "0.7 + 0.1 = 0.8",
            "1.1 * 3 = 3.3")
        .forEach(expression ->
            assertEquals(Boolean.TRUE, engine.evaluate(expression, Map.of()).value(), expression));
  }

  @Test
  @DisplayName("resolves a field's data source before querying the dictionary")
  void evaluatesExistsInReferenceSet() {
    FeelExpressionEngine engine = engineWith(new StubClient());
    Map<String, Object> variables = Map.of("value", Map.of("customerType", "ENTERPRISE"));

    assertEquals(Boolean.TRUE,
        engine.evaluate("existsInReferenceSet(\"customerType\", value.customerType)", variables)
            .value());
    assertEquals(Boolean.FALSE,
        engine.evaluate("existsInReferenceSet(\"customerType\", \"NOPE\")", Map.of()).value());
  }

  @Test
  @DisplayName("reports a lookup that could not answer as an upstream failure, not a false result")
  void propagatesReferenceDataFailure() {
    ReferenceDataException timeout =
        new ReferenceDataException(ReferenceDataException.Reason.TIMEOUT, "lookup timed out");
    FeelExpressionEngine engine = engineWith(new StubClient(timeout));

    ReferenceDataException thrown = assertThrows(ReferenceDataException.class, () ->
        engine.evaluate("existsInReferenceSet(\"customerType\", \"ENTERPRISE\")", Map.of()));
    assertEquals(ReferenceDataException.Reason.TIMEOUT, thrown.reason());
  }

  @Test
  @DisplayName("treats a field without a configured data source as a configuration defect")
  void rejectsFieldWithoutDataSource() {
    FeelExpressionEngine engine = engineWith(new StubClient());

    ReferenceDataException thrown = assertThrows(ReferenceDataException.class, () ->
        engine.evaluate("existsInReferenceSet(\"channel\", \"email\")", Map.of()));
    assertEquals(ReferenceDataException.Reason.NOT_CONFIGURED, thrown.reason());
  }

  @Test
  @DisplayName("converts a registered function failure into a warning instead of an exception")
  void convertsFunctionFailureIntoWarning() {
    FeelExpressionEngine engine = engineWith(new StubClient());

    FeelEvaluationResult result = engine.evaluate("businessDay(\"not-a-date\", 1)", Map.of());

    assertFalse(result.isSuccess());
    assertEquals(null, result.value());
    assertEquals("FUNCTION_INVOCATION_FAILED", result.warnings().get(0).type());
  }

  @Test
  @DisplayName("skips weekends when moving by business days")
  void evaluatesBusinessDay() {
    FeelExpressionEngine engine = engineWith(new StubClient());
    Map<String, Object> clock = Map.of("clock", Map.of("today", LocalDate.of(2026, 8, 21)));

    // 2026-08-21 is a Friday, so one business day later is Monday 2026-08-24.
    assertEquals(LocalDate.of(2026, 8, 24),
        engine.evaluate("businessDay(clock.today, 1)", clock).value());
  }

  @Test
  @DisplayName("moves by calendar days without applying working-day policy")
  void evaluatesCalendarDay() {
    FeelExpressionEngine engine = engineWith(new StubClient());
    Map<String, Object> clock = Map.of("clock", Map.of("today", LocalDate.of(2026, 8, 21)));

    assertEquals(LocalDate.of(2026, 8, 22),
        engine.evaluate("calendarDay(clock.today, 1)", clock).value());
  }

  @Test
  @DisplayName("prevents submitted data from shadowing a registered function")
  void variablesCannotShadowFunctions() {
    FeelExpressionEngine engine = engineWith(new StubClient());
    Map<String, Object> hostile = Map.of("existsInReferenceSet", "attacker-supplied");

    assertEquals(Boolean.TRUE,
        engine.evaluate("existsInReferenceSet(\"customerType\", \"RETAIL\")", hostile).value());
  }

  @Test
  @DisplayName("parses without executing so syntax can be checked before data is submitted")
  void parsesWithoutEvaluating() {
    FeelExpressionEngine engine = engineWith(new StubClient());

    assertTrue(engine.parse("value.amount > 1").isValid());
    assertFalse(engine.parse("value.amount >").isValid());
  }

  @Test
  @DisplayName("exposes registered names because unknown functions evaluate to null silently")
  void exposesRegisteredFunctionNames() {
    FeelExpressionEngine engine = engineWith(new StubClient());

    assertEquals(java.util.Set.of("businessDay", "calendarDay", "existsInReferenceSet"),
        engine.registeredFunctionNames());
    // The engine itself does not fail on a typo, which is why the name set must be public.
    assertEquals(null, engine.evaluate("existsInReferenceSt(\"customerType\", \"X\")", Map.of())
        .value());
  }

  @Test
  @DisplayName("refuses duplicate and built-in-shadowing registrations at startup")
  void rejectsConflictingRegistrations() {
    FeelFunctionDefinition duplicate =
        new FeelFunctionDefinition("calendarDay", List.of("a"), args -> null);
    assertThrows(IllegalArgumentException.class,
        () -> FeelExpressionEngine.create(List.of(DateFunctionsFixture.calendarDay(), duplicate)));

    FeelFunctionDefinition builtin =
        new FeelFunctionDefinition("substring", List.of("a"), args -> null);
    assertThrows(IllegalArgumentException.class,
        () -> FeelExpressionEngine.create(List.of(builtin)));
  }

  @Test
  @DisplayName("returns numbers as BigDecimal so downstream comparisons stay exact")
  void returnsDecimalNumbers() {
    FeelExpressionEngine engine = engineWith(new StubClient());

    Object value = engine.evaluate("100.10 + 200.20", Map.of()).value();
    assertEquals(new BigDecimal("300.30"), value);
  }

  /** Keeps the duplicate-registration test independent of the default function factory. */
  private static final class DateFunctionsFixture {
    private static FeelFunctionDefinition calendarDay() {
      return com.tech.feelers.expression.functions.DateFunctions.calendarDay();
    }
  }
}
