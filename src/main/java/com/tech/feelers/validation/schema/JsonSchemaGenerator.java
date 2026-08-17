package com.tech.feelers.validation.schema;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tech.feelers.expression.FeelEvaluationResult;
import com.tech.feelers.expression.FeelExpressionEngine;
import com.tech.feelers.validation.ValidationError;
import com.tech.feelers.validation.SchemaConfigurationException;

/**
 * Turns a Form DSL document into a plain Draft 2020-12 JSON Schema for one set of submitted data.
 *
 * <p>The generated schema contains no {@code x-feel-*} keyword and no {@code {{ }}} string: dynamic
 * required fields are merged into {@code required} and dynamic bounds are replaced with the numbers
 * they evaluated to. The Form DSL document is never modified.
 */
public final class JsonSchemaGenerator {

  private static final String FEEL_REQUIRED = "x-feel-required";
  private static final String FEEL_ASSERTIONS = "x-feel-assertions";
  private static final List<String> DYNAMIC_BOUND_KEYWORDS =
      List.of("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum");

  private final FeelExpressionEngine engine;

  /**
   * Binds the generator to the engine that resolves its expressions.
   *
   * @param engine engine used for {@code x-feel-required} and dynamic bounds
   */
  public JsonSchemaGenerator(FeelExpressionEngine engine) {
    this.engine = engine;
  }

  /**
   * Generates the schema for one submission.
   *
   * @param formDsl authoritative form definition; not modified
   * @param variables FEEL variables for this run, containing {@code data}, {@code value}, {@code clock}
   * @return a pure JSON Schema ready for a standard validator
   * @throws SchemaConfigurationException when an expression or extension keyword is invalid
   */
  public ObjectNode generate(JsonNode formDsl, Map<String, Object> variables) {
    if (!formDsl.isObject()) {
      throw new SchemaConfigurationException(ValidationError.schema("schema.extensionShape", "",
          "#", null, Map.of("reason", "form definition must be an object")));
    }
    ObjectNode generated = formDsl.deepCopy();
    resolveNode(generated, "", "#", variables);
    return generated;
  }

  /**
   * Resolves one schema node, recursing into {@code properties} and {@code items}.
   *
   * <p>Resolution is depth-first so each object's own extensions are applied with the pointers that
   * identify it, which is what makes the reported error paths usable.
   *
   * @param node schema node being rewritten in place
   * @param instancePath JSON Pointer to the data this node describes
   * @param schemaPath JSON Pointer to this node inside the form definition
   * @param variables FEEL variables for this run
   */
  private void resolveNode(ObjectNode node, String instancePath, String schemaPath,
      Map<String, Object> variables) {
    resolveDynamicRequired(node, instancePath, schemaPath, variables);
    resolveDynamicBounds(node, instancePath, schemaPath, variables);
    node.remove(FEEL_ASSERTIONS);

    JsonNode properties = node.get("properties");
    if (properties instanceof ObjectNode propertyContainer) {
      Iterator<Map.Entry<String, JsonNode>> fields = propertyContainer.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (field.getValue() instanceof ObjectNode child) {
          resolveNode(child, instancePath + "/" + field.getKey(),
              schemaPath + "/properties/" + field.getKey(), variables);
        }
      }
    }
    if (node.get("items") instanceof ObjectNode items) {
      resolveNode(items, instancePath + "/*", schemaPath + "/items", variables);
    }
  }

  /**
   * Merges dynamically required field names into {@code required}.
   *
   * <p>Only a strict {@code true} adds a field, and static entries are always kept, so a dynamic
   * rule can add a requirement but never remove one.
   *
   * @param node schema node carrying the extension
   * @param instancePath pointer to the data this node describes
   * @param schemaPath pointer to this node inside the form definition
   * @param variables FEEL variables for this run
   */
  private void resolveDynamicRequired(ObjectNode node, String instancePath, String schemaPath,
      Map<String, Object> variables) {
    JsonNode dynamicRequired = node.remove(FEEL_REQUIRED);
    if (dynamicRequired == null) {
      return;
    }
    if (!dynamicRequired.isObject()) {
      throw new SchemaConfigurationException(ValidationError.schema("schema.extensionShape",
          instancePath, schemaPath + "/" + FEEL_REQUIRED, FEEL_REQUIRED,
          Map.of("reason", "x-feel-required must be an object")));
    }

    ArrayNode required = node.withArray("required");
    Iterator<Map.Entry<String, JsonNode>> entries = dynamicRequired.fields();
    while (entries.hasNext()) {
      Map.Entry<String, JsonNode> entry = entries.next();
      String field = entry.getKey();
      String fieldSchemaPath = schemaPath + "/" + FEEL_REQUIRED + "/" + field;
      String fieldInstancePath = instancePath + "/" + field;

      Object value = evaluate(entry.getValue(), fieldInstancePath, fieldSchemaPath, FEEL_REQUIRED,
          variables);
      if (!(value instanceof Boolean flag)) {
        throw new SchemaConfigurationException(ValidationError.schema("schema.feelResultType",
            fieldInstancePath, fieldSchemaPath, FEEL_REQUIRED,
            Map.of("expected", "boolean", "actual", typeNameOf(value))));
      }
      if (flag && !containsValue(required, field)) {
        required.add(field);
      }
    }
  }

  /**
   * Replaces {@code {{ }}} bound expressions with the numbers they evaluate to.
   *
   * @param node schema node possibly carrying dynamic bounds
   * @param instancePath pointer to the data this node describes
   * @param schemaPath pointer to this node inside the form definition
   * @param variables FEEL variables for this run
   */
  private void resolveDynamicBounds(ObjectNode node, String instancePath, String schemaPath,
      Map<String, Object> variables) {
    for (String keyword : DYNAMIC_BOUND_KEYWORDS) {
      JsonNode bound = node.get(keyword);
      if (bound == null || !bound.isTextual()) {
        continue;
      }
      String expression = DynamicBoundSyntax.unwrap(bound.asText())
          .orElseThrow(() -> new SchemaConfigurationException(
              ValidationError.schema("schema.extensionShape", instancePath,
                  schemaPath + "/" + keyword, keyword,
                  Map.of("reason", "a textual bound must be a single {{ }} expression"))));

      Object value = evaluate(expression, instancePath, schemaPath + "/" + keyword, keyword,
          variables);
      if (!(value instanceof BigDecimal number)) {
        throw new SchemaConfigurationException(ValidationError.schema("schema.feelResultType",
            instancePath, schemaPath + "/" + keyword, keyword,
            Map.of("expected", "number", "actual", typeNameOf(value))));
      }
      node.put(keyword, number);
    }
  }

  /**
   * Evaluates one expression node, converting a missing or non-textual expression into a schema
   * error before the engine is reached.
   *
   * @param expressionNode node expected to hold a FEEL expression
   * @param instancePath pointer used in error reporting
   * @param schemaPath pointer used in error reporting
   * @param keyword extension keyword used in error reporting
   * @param variables FEEL variables for this run
   * @return the evaluated value
   */
  private Object evaluate(JsonNode expressionNode, String instancePath, String schemaPath,
      String keyword, Map<String, Object> variables) {
    if (!expressionNode.isTextual()) {
      throw new SchemaConfigurationException(ValidationError.schema("schema.extensionShape",
          instancePath, schemaPath, keyword, Map.of("reason", "expression must be a string")));
    }
    return evaluate(expressionNode.asText(), instancePath, schemaPath, keyword, variables);
  }

  /**
   * Evaluates one expression, mapping a syntax failure and a function failure onto the two distinct
   * schema error codes rather than a single opaque one.
   *
   * @param expression FEEL source
   * @param instancePath pointer used in error reporting
   * @param schemaPath pointer used in error reporting
   * @param keyword extension keyword used in error reporting
   * @param variables FEEL variables for this run
   * @return the evaluated value
   */
  private Object evaluate(String expression, String instancePath, String schemaPath, String keyword,
      Map<String, Object> variables) {
    FeelEvaluationResult result;
    try {
      result = engine.evaluate(expression, variables);
    } catch (IllegalArgumentException exception) {
      throw new SchemaConfigurationException(ValidationError.schema("schema.feelSyntax",
          instancePath, schemaPath, keyword, Map.of("expression", expression)));
    }
    if (!result.isSuccess()) {
      throw new SchemaConfigurationException(ValidationError.schema("schema.feelEvaluation",
          instancePath, schemaPath, keyword, Map.of("expression", expression)));
    }
    return result.value();
  }

  /**
   * Reports a value's type without exposing the value itself, keeping diagnostics free of
   * submitted data.
   *
   * @param value evaluated value
   * @return a stable type name
   */
  private static String typeNameOf(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof BigDecimal) {
      return "number";
    }
    if (value instanceof Boolean) {
      return "boolean";
    }
    if (value instanceof String) {
      return "string";
    }
    if (value instanceof List<?>) {
      return "list";
    }
    if (value instanceof Map<?, ?>) {
      return "context";
    }
    return value.getClass().getSimpleName();
  }

  /**
   * Checks membership without relying on Jackson node equality semantics, so a statically required
   * field is not duplicated by a dynamic rule.
   *
   * @param array the {@code required} array
   * @param value field name to look for
   * @return whether the array already lists the field
   */
  private static boolean containsValue(ArrayNode array, String value) {
    for (JsonNode element : array) {
      if (element.isTextual() && element.asText().equals(value)) {
        return true;
      }
    }
    return false;
  }
}
