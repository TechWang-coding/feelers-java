package com.tech.feelers.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tech.feelers.expression.FeelExpressionEngine;
import com.tech.feelers.expression.errors.ReferenceDataException;
import com.tech.feelers.expression.functions.BusinessCalendar;
import com.tech.feelers.expression.functions.FieldMetadata;
import com.tech.feelers.expression.functions.ReferenceDataClient;
import com.tech.feelers.validation.adapters.jackson.JsonMapperFactory;

/**
 * Exercises the scenarios defined in {@code docs/json-schema-validation-test-fixtures.md} so the
 * Java runtime and the published contract cannot drift apart.
 */
class FormValidationServiceTest {

  private static final ObjectMapper MAPPER = JsonMapperFactory.create();

  /** Reference data matching the fixture document, with sentinel values for upstream faults. */
  private static final class StubClient implements ReferenceDataClient {
    @Override
    public FieldMetadata findField(String fieldKey) {
      return new FieldMetadata(fieldKey, "CUSTOMER_TYPE");
    }

    @Override
    public boolean containsValue(String dataSourceUniqueName, String value) {
      if ("__TIMEOUT__".equals(value)) {
        throw new ReferenceDataException(ReferenceDataException.Reason.TIMEOUT, "lookup timed out");
      }
      return List.of("ENTERPRISE", "RETAIL", "GOVERNMENT").contains(value);
    }
  }

  private static final String FORM_DSL = """
      {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "type": "object",
        "required": ["channel", "customerType", "minAmount", "maxAmount", "discount", "dueDate"],
        "x-feel-required": {
          "email": "data.channel = \\"email\\"",
          "phone": "data.channel = \\"sms\\""
        },
        "properties": {
          "channel": { "type": "string", "enum": ["email", "sms"] },
          "email": { "type": "string" },
          "phone": { "type": "string" },
          "customerType": { "type": "string", "minLength": 1 },
          "minAmount": { "type": "number" },
          "maxAmount": { "type": "number" },
          "discount": {
            "type": "number",
            "minimum": "{{ data.minAmount }}",
            "maximum": "{{ data.maxAmount }}"
          },
          "dueDate": { "type": "string" }
        },
        "additionalProperties": false,
        "x-feel-assertions": [
          {
            "id": "customer-type-exists",
            "assert": "existsInReferenceSet(\\"customerType\\", value.customerType)",
            "targetField": "/customerType",
            "errorMessage": "Customer type not found, please choose another"
          },
          {
            "id": "due-date-not-before-tomorrow",
            "assert": "date(value.dueDate) >= businessDay(clock.today, 1)",
            "targetField": "/dueDate",
            "errorMessage": "Due date must not be earlier than the next business day"
          }
        ]
      }
      """;

  private static final String VALID_DATA = """
      {
        "channel": "email",
        "email": "alice@example.com",
        "customerType": "ENTERPRISE",
        "minAmount": 0,
        "maxAmount": 100,
        "discount": 25.5,
        "dueDate": "2026-08-19"
      }
      """;

  private static FormValidationService service() {
    return new FormValidationService(
        FeelExpressionEngine.createDefault(new StubClient(), BusinessCalendar.weekendBased()));
  }

  private static ValidationRequest request(String dataJson) throws Exception {
    return new ValidationRequest(MAPPER.readTree(FORM_DSL), MAPPER.readTree(dataJson),
        LocalDate.of(2026, 8, 17), OffsetDateTime.parse("2026-08-17T09:00:00+08:00"),
        "Asia/Shanghai");
  }

  private static String withoutField(String field) throws Exception {
    ObjectNode data = (ObjectNode) MAPPER.readTree(VALID_DATA);
    data.remove(field);
    return MAPPER.writeValueAsString(data);
  }

  private static String replacing(String field, Object value) throws Exception {
    ObjectNode data = (ObjectNode) MAPPER.readTree(VALID_DATA);
    data.putPOJO(field, value);
    return MAPPER.writeValueAsString(data);
  }

  @Test
  @DisplayName("scenario 1: valid submission produces no errors")
  void acceptsValidSubmission() throws Exception {
    ValidationResult result = service().validate(request(VALID_DATA));

    assertTrue(result.isValid(), () -> "unexpected errors: " + result.errors());
  }

  @Test
  @DisplayName("scenario 1: dynamic bounds are resolved into plain numbers")
  void generatesPureJsonSchema() throws Exception {
    JsonNode schema = service().generateSchema(request(VALID_DATA));

    assertEquals(0, schema.at("/properties/discount/minimum").decimalValue().intValue());
    assertEquals(100, schema.at("/properties/discount/maximum").decimalValue().intValue());
    assertTrue(schema.at("/properties/discount/minimum").isNumber());
    // The extensions are consumed, never emitted.
    assertTrue(schema.get("x-feel-required") == null);
    assertTrue(schema.get("x-feel-assertions") == null);
    // The conditional requirement was merged in.
    assertTrue(schema.get("required").toString().contains("email"));
  }

  @Test
  @DisplayName("scenario 2: a conditionally required field is reported as a data error")
  void reportsConditionallyRequiredField() throws Exception {
    ValidationResult result = service().validate(request(withoutField("email")));

    assertEquals(1, result.errors().size());
    ValidationError error = result.errors().get(0);
    assertEquals(ErrorCategory.DATA, error.category());
    assertEquals("data.required", error.code());
  }

  @Test
  @DisplayName("scenario 3: a dynamic upper bound is enforced by the generated schema")
  void reportsDynamicBoundViolation() throws Exception {
    ValidationResult result = service().validate(request(replacing("discount", 150)));

    assertEquals(1, result.errors().size());
    ValidationError error = result.errors().get(0);
    assertEquals(ErrorCategory.DATA, error.category());
    assertEquals("data.maximum", error.code());
    assertEquals("/discount", error.instancePath());
  }

  @Test
  @DisplayName("scenario 4: a failing assertion carries the author's message")
  void reportsAssertionFailure() throws Exception {
    ValidationResult result = service().validate(request(replacing("dueDate", "2026-08-17")));

    assertEquals(1, result.errors().size());
    ValidationError error = result.errors().get(0);
    assertEquals(ErrorCategory.ASSERTION, error.category());
    assertEquals("assertion.failed", error.code());
    assertEquals("due-date-not-before-tomorrow", error.assertionId());
    assertEquals("Due date must not be earlier than the next business day", error.message());
    assertEquals(null, error.messageKey());
  }

  @Test
  @DisplayName("scenario 5: a lookup that times out is an upstream error, not a rule failure")
  void reportsUpstreamTimeout() throws Exception {
    ValidationResult result = service().validate(request(replacing("customerType", "__TIMEOUT__")));

    assertEquals(1, result.errors().size());
    ValidationError error = result.errors().get(0);
    assertEquals(ErrorCategory.UPSTREAM, error.category());
    assertEquals("upstream.lookupTimeout", error.code());
    assertEquals("customer-type-exists", error.assertionId());
    // The assertion's own message describes "checked and absent", which is not what happened.
    assertEquals(null, error.message());
    assertEquals("system.lookupUnavailable", error.messageKey());
  }

  @Test
  @DisplayName("assertions do not run while the data still has schema errors")
  void shortCircuitsAssertionsOnDataErrors() throws Exception {
    ObjectNode data = (ObjectNode) MAPPER.readTree(VALID_DATA);
    data.remove("email");
    data.put("dueDate", "2026-08-17");
    data.put("customerType", "UNKNOWN");

    ValidationResult result = service().validate(request(MAPPER.writeValueAsString(data)));

    // Only the required-field error surfaces; the two failing assertions stay silent.
    assertEquals(1, result.errors().size());
    assertEquals("data.required", result.errors().get(0).code());
  }

  @Test
  @DisplayName("a non-boolean x-feel-required expression is a schema configuration error")
  void reportsNonBooleanDynamicRequired() throws Exception {
    JsonNode broken = MAPPER.readTree(FORM_DSL.replace("data.channel = \\\"email\\\"", "data.channel"));
    ValidationRequest brokenRequest = new ValidationRequest(broken, MAPPER.readTree(VALID_DATA),
        LocalDate.of(2026, 8, 17), OffsetDateTime.parse("2026-08-17T09:00:00+08:00"),
        "Asia/Shanghai");

    ValidationResult result = service().validate(brokenRequest);

    assertTrue(result.hasSchemaErrors());
    ValidationError error = result.errors().get(0);
    assertEquals("schema.feelResultType", error.code());
    assertEquals("string", error.arguments().get("actual"));
  }

  @Test
  @DisplayName("an assertion without errorMessage falls back to the engine's default text")
  void fallsBackToDefaultAssertionMessage() throws Exception {
    ObjectNode form = (ObjectNode) MAPPER.readTree(FORM_DSL);
    ((ObjectNode) form.withArray("x-feel-assertions").get(1)).remove("errorMessage");
    ValidationRequest fallbackRequest = new ValidationRequest(form,
        MAPPER.readTree(replacing("dueDate", "2026-08-17")), LocalDate.of(2026, 8, 17),
        OffsetDateTime.parse("2026-08-17T09:00:00+08:00"), "Asia/Shanghai");

    ValidationResult result = service().validate(fallbackRequest);

    assertEquals(1, result.errors().size());
    assertEquals("/dueDate does not satisfy the business rule",
        result.errors().get(0).message());
  }

  @Test
  @DisplayName("submitted decimals keep full precision through parsing and bound resolution")
  void preservesDecimalPrecision() throws Exception {
    ObjectNode data = (ObjectNode) MAPPER.readTree(VALID_DATA);
    data.put("maxAmount", new java.math.BigDecimal("300.30"));
    data.put("discount", new java.math.BigDecimal("300.30"));

    ValidationResult result = service().validate(request(MAPPER.writeValueAsString(data)));

    assertTrue(result.isValid(), () -> "unexpected errors: " + result.errors());
  }
}
