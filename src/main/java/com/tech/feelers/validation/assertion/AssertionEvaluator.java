package com.tech.feelers.validation.assertion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.tech.feelers.expression.FeelEvaluationResult;
import com.tech.feelers.expression.FeelExpressionEngine;
import com.tech.feelers.expression.errors.ReferenceDataException;
import com.tech.feelers.validation.SchemaConfigurationException;
import com.tech.feelers.validation.ValidationError;

/**
 * Evaluates the {@code x-feel-assertions} declared on a form definition.
 *
 * <p>Assertions are read straight from the form definition rather than from the generated schema,
 * because they are deliberately not representable as JSON Schema keywords. They run only after
 * schema validation has passed, so an assertion never has to defend against a missing field or a
 * wrong type.
 */
public final class AssertionEvaluator {

  private static final String FEEL_ASSERTIONS = "x-feel-assertions";
  private static final String DEFAULT_MESSAGE_SUFFIX = " does not satisfy the business rule";

  private final FeelExpressionEngine engine;

  /**
   * Binds the evaluator to the engine that executes its expressions.
   *
   * @param engine engine used to evaluate assertions
   */
  public AssertionEvaluator(FeelExpressionEngine engine) {
    this.engine = engine;
  }

  /**
   * Evaluates every assertion declared at the root of the form definition.
   *
   * @param formDsl authoritative form definition
   * @param variables FEEL variables for this run
   * @return one error per failing assertion, in declaration order
   * @throws SchemaConfigurationException when an assertion is malformed or does not yield a boolean
   */
  public List<ValidationError> evaluate(JsonNode formDsl, Map<String, Object> variables) {
    JsonNode assertions = formDsl.get(FEEL_ASSERTIONS);
    if (assertions == null || assertions.isNull()) {
      return List.of();
    }
    if (!assertions.isArray()) {
      throw new SchemaConfigurationException(ValidationError.schema("schema.extensionShape", "",
          "#/" + FEEL_ASSERTIONS, FEEL_ASSERTIONS,
          Map.of("reason", "x-feel-assertions must be an array")));
    }

    List<ValidationError> errors = new ArrayList<>();
    for (int index = 0; index < assertions.size(); index++) {
      evaluateOne(assertions.get(index), index, variables).ifPresent(errors::add);
    }
    return errors;
  }

  /**
   * Evaluates one assertion, translating each distinct failure mode into the error category the
   * caller must react to differently.
   *
   * @param assertion assertion node
   * @param index position used to build the schema pointer
   * @param variables FEEL variables for this run
   * @return the error when the assertion failed or its lookup could not answer
   */
  private java.util.Optional<ValidationError> evaluateOne(JsonNode assertion, int index,
      Map<String, Object> variables) {
    String schemaPath = "#/" + FEEL_ASSERTIONS + "/" + index;
    String id = text(assertion, "id");
    String expression = text(assertion, "assert");
    String targetField = text(assertion, "targetField");

    if (id == null || expression == null || targetField == null) {
      throw new SchemaConfigurationException(ValidationError.schema("schema.extensionShape", "",
          schemaPath, FEEL_ASSERTIONS,
          Map.of("reason", "an assertion requires id, assert and targetField")));
    }

    FeelEvaluationResult result;
    try {
      result = engine.evaluate(expression, variables);
    } catch (ReferenceDataException lookupFailure) {
      return java.util.Optional.of(toUpstreamError(lookupFailure, id, targetField, schemaPath));
    } catch (IllegalArgumentException syntaxFailure) {
      throw new SchemaConfigurationException(ValidationError.schema("schema.feelSyntax",
          targetField, schemaPath, FEEL_ASSERTIONS, Map.of("assertionId", id)));
    }

    if (!result.isSuccess()) {
      throw new SchemaConfigurationException(ValidationError.schema("schema.feelEvaluation",
          targetField, schemaPath, FEEL_ASSERTIONS, Map.of("assertionId", id)));
    }
    if (!(result.value() instanceof Boolean satisfied)) {
      throw new SchemaConfigurationException(ValidationError.schema("schema.feelResultType",
          targetField, schemaPath, FEEL_ASSERTIONS,
          Map.of("assertionId", id, "expected", "boolean")));
    }
    if (satisfied) {
      return java.util.Optional.empty();
    }

    String message = text(assertion, "errorMessage");
    return java.util.Optional.of(ValidationError.assertionFailed(id, targetField, schemaPath,
        message != null ? message : targetField + DEFAULT_MESSAGE_SUFFIX));
  }

  /**
   * Maps a lookup failure onto its upstream error code, keeping "could not check" separate from
   * "checked and failed" so callers can retry instead of asking the user to change the value.
   *
   * @param failure lookup failure raised by a controlled query function
   * @param assertionId assertion whose lookup failed
   * @param targetField field the assertion targets
   * @param schemaPath pointer to the assertion
   * @return the upstream error
   */
  private static ValidationError toUpstreamError(ReferenceDataException failure, String assertionId,
      String targetField, String schemaPath) {
    if (failure.reason() == ReferenceDataException.Reason.NOT_CONFIGURED) {
      throw new SchemaConfigurationException(ValidationError.schema("schema.extensionShape",
          targetField, schemaPath, FEEL_ASSERTIONS,
          Map.of("assertionId", assertionId, "reason", "field declares no data source")));
    }
    String code = failure.reason() == ReferenceDataException.Reason.TIMEOUT
        ? "upstream.lookupTimeout"
        : "upstream.lookupUnavailable";
    return ValidationError.upstream(code, assertionId, targetField, schemaPath, Map.of());
  }

  /**
   * Reads an optional string member, treating a missing and a non-textual member alike so callers
   * only handle one absent case.
   *
   * @param node containing object
   * @param field member name
   * @return the text, or {@code null}
   */
  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isTextual() ? value.asText() : null;
  }
}
