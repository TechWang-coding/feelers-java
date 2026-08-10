package com.tech.feelers.templating.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormSchemaGeneratorTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Comparator<JsonNode> JSON_COMPARATOR = (left, right) -> {
        if (left.isNumber() && right.isNumber()) {
            return left.decimalValue().compareTo(right.decimalValue());
        }
        return left.equals(right) ? 0 : 1;
    };
    private final FormSchemaGenerator generator = new FormSchemaGenerator(OBJECT_MAPPER);

    @Test
    void generatesTheExpectedSchemaForEveryContractFixture() throws IOException {
        for (String fixture : List.of(
                "basic-fields.case.json",
                "optional-value-constraint.case.json",
                "conditional-and.case.json",
                "conditional-grouping.case.json")) {
            JsonNode testCase = readFixture(fixture);
            FormDefinition definition = toFormDefinition(testCase.path("formDefinition"));

            JsonNode expectedSchema = testCase.path("expectedSchema");
            JsonNode actualSchema = generator.generate(definition);
            assertTrue(expectedSchema.equals(JSON_COMPARATOR, actualSchema), () -> fixture
                    + "\nexpected: " + expectedSchema
                    + "\nactual: " + actualSchema);
        }
    }

    @Test
    void validatesConditionalRequirementsAndOptionalFieldConstraints() throws IOException {
        JsonNode testCase = readFixture("conditional-grouping.case.json");
        JsonNode schema = generator.generate(toFormDefinition(testCase.path("formDefinition")));
        FormDataValidator validator = new FormDataValidator(OBJECT_MAPPER);

        assertTrue(validator.validate(schema, """
                {"age": 30, "sex": "Male"}
                """).isEmpty());

        List<FormDataValidator.ValidationError> missingDescription = validator.validate(schema, """
                {"age": 16, "sex": "Female"}
                """);
        assertFalse(missingDescription.isEmpty());
        assertTrue(missingDescription.stream().anyMatch(error -> "required".equals(error.keyword())));

        List<FormDataValidator.ValidationError> shortDescription = validator.validate(schema, """
                {"age": 30, "sex": "Male", "description": "too short"}
                """);
        assertFalse(shortDescription.isEmpty());
        assertTrue(shortDescription.stream().anyMatch(error -> "minLength".equals(error.keyword())));
    }

    @Test
    void rejectsUnsupportedRuntimeFeelFunctionsDuringPublishing() {
        FormDefinition definition = new FormDefinition(
                "runtime-rule",
                1,
                List.of(new FieldDefinition("effectiveDate", "string", false, null, null,
                        null, null, null, "date", List.of())),
                List.of(new ConditionalRule("requires-description", "date(effectiveDate) > today()", List.of("effectiveDate"))));

        assertThrows(FormSchemaGenerationException.class, () -> generator.generate(definition));
    }

    @Test
    void rejectsConditionsThatReferenceUnboundFields() {
        FormDefinition definition = new FormDefinition(
                "unknown-field",
                1,
                List.of(new FieldDefinition("age", "integer", false, null, null,
                        null, null, null, null, List.of())),
                List.of(new ConditionalRule("invalid-reference", "sex = \"Female\"", List.of("age"))));

        assertThrows(FormSchemaGenerationException.class, () -> generator.generate(definition));
    }

    private static JsonNode readFixture(String filename) throws IOException {
        String resource = "form-schema-generator/" + filename;
        try (InputStream input = FormSchemaGeneratorTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("missing test fixture: " + resource);
            }
            return OBJECT_MAPPER.readTree(input);
        }
    }

    private static FormDefinition toFormDefinition(JsonNode source) {
        List<FieldDefinition> fields = new ArrayList<>();
        for (JsonNode field : source.path("fields")) {
            fields.add(new FieldDefinition(
                    field.path("key").asText(),
                    field.path("type").asText(),
                    field.path("required").asBoolean(false),
                    nullableInteger(field, "minLength"),
                    nullableInteger(field, "maxLength"),
                    nullableDouble(field, "minimum"),
                    nullableDouble(field, "maximum"),
                    nullableText(field, "pattern"),
                    nullableText(field, "format"),
                    stringList(field.path("enum"))));
        }

        List<ConditionalRule> rules = new ArrayList<>();
        for (JsonNode rule : source.path("conditionalRules")) {
            rules.add(new ConditionalRule(
                    rule.path("id").asText(),
                    rule.path("when").asText(),
                    stringList(rule.path("then").path("required"))));
        }
        return new FormDefinition(source.path("formKey").asText(), source.path("version").asInt(), fields, rules);
    }

    private static Integer nullableInteger(JsonNode node, String name) {
        return node.has(name) ? node.get(name).intValue() : null;
    }

    private static Double nullableDouble(JsonNode node, String name) {
        return node.has(name) ? node.get(name).doubleValue() : null;
    }

    private static String nullableText(JsonNode node, String name) {
        return node.has(name) ? node.get(name).asText() : null;
    }

    private static List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }
}
