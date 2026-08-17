package com.tech.feelers.validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tech.feelers.expression.FeelExpressionEngine;
import com.tech.feelers.validation.adapters.jackson.JsonMapperFactory;
import com.tech.feelers.validation.adapters.networknt.SchemaValidatorAdapter;
import com.tech.feelers.validation.assertion.AssertionEvaluator;
import com.tech.feelers.validation.schema.JsonSchemaGenerator;

/**
 * Entry point for validating submitted data against a Form DSL document.
 *
 * <p>A run has three stages: generate a plain JSON Schema for this submission, validate the data
 * against it, then evaluate the relation assertions. The stages are ordered and short-circuited so
 * each one only sees input the previous stage already accepted.
 */
public final class FormValidationService {

  private final JsonSchemaGenerator schemaGenerator;
  private final SchemaValidatorAdapter schemaValidator;
  private final AssertionEvaluator assertionEvaluator;
  private final ObjectMapper mapper;

  /**
   * Builds a service around one expression engine.
   *
   * @param engine engine used for dynamic requirements, dynamic bounds, and assertions
   */
  public FormValidationService(FeelExpressionEngine engine) {
    this.schemaGenerator = new JsonSchemaGenerator(engine);
    this.schemaValidator = new SchemaValidatorAdapter();
    this.assertionEvaluator = new AssertionEvaluator(engine);
    this.mapper = JsonMapperFactory.create();
  }

  /**
   * Validates one submission.
   *
   * <p>Assertions run only when nothing earlier failed. That ordering is deliberate: it keeps a
   * relation rule from being blamed for a value the schema had already rejected as missing or
   * wrongly typed.
   *
   * @param request form definition, data, and clock for this run
   * @return every error found, in execution order
   */
  public ValidationResult validate(ValidationRequest request) {
    Map<String, Object> variables = createVariables(request);

    ObjectNode generatedSchema;
    try {
      generatedSchema = schemaGenerator.generate(request.formDsl(), variables);
    } catch (SchemaConfigurationException exception) {
      return new ValidationResult(List.of(exception.error()));
    }

    List<ValidationError> errors =
        new ArrayList<>(schemaValidator.validate(generatedSchema, request.data()));
    if (!errors.isEmpty()) {
      return new ValidationResult(errors);
    }

    try {
      errors.addAll(assertionEvaluator.evaluate(request.formDsl(), variables));
    } catch (SchemaConfigurationException exception) {
      return new ValidationResult(List.of(exception.error()));
    }
    return new ValidationResult(errors);
  }

  /**
   * Generates the schema without validating any data, so callers can inspect what a submission
   * would be checked against.
   *
   * @param request form definition, data, and clock for this run
   * @return the generated pure JSON Schema
   * @throws SchemaConfigurationException when the form definition is invalid
   */
  public ObjectNode generateSchema(ValidationRequest request) {
    return schemaGenerator.generate(request.formDsl(), createVariables(request));
  }

  /**
   * Builds the FEEL variable scope.
   *
   * <p>Only these three names are exposed, so a business field can never shadow a registered
   * function and nested objects keep one unambiguous meaning.
   *
   * @param request current run
   * @return the read-only variables visible to every expression in this run
   */
  private Map<String, Object> createVariables(ValidationRequest request) {
    Object data = mapper.convertValue(request.data(), Object.class);
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("data", data);
    variables.put("value", data);
    variables.put("clock", request.clockVariables());
    return variables;
  }
}
