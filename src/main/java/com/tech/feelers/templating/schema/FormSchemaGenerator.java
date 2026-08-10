package com.tech.feelers.templating.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/** Compiles a published Form definition into a JSON Schema Draft 2020-12 document. */
public final class FormSchemaGenerator {
    public static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> SUPPORTED_TYPES = Set.of("string", "integer", "number", "boolean", "object", "array");

    private final ObjectMapper objectMapper;

    public FormSchemaGenerator() {
        this(new ObjectMapper());
    }

    public FormSchemaGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode generate(FormDefinition definition) {
        validateDefinition(definition);

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("$schema", DRAFT_2020_12);
        schema.put("$id", "https://schemas.example.com/forms/%s/%d".formatted(definition.formKey(), definition.version()));
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");
        for (FieldDefinition field : definition.fields()) {
            properties.set(field.key(), fieldSchema(field));
            if (field.required()) {
                required.add(field.key());
            }
        }
        if (required.isEmpty()) {
            schema.remove("required");
        }

        if (!definition.conditionalRules().isEmpty()) {
            ArrayNode allOf = schema.putArray("allOf");
            Map<String, String> fieldTypes = definition.fields().stream()
                    .collect(Collectors.toUnmodifiableMap(FieldDefinition::key, FieldDefinition::type));
            FeelConditionCompiler compiler = new FeelConditionCompiler(objectMapper, fieldTypes);
            for (ConditionalRule rule : definition.conditionalRules()) {
                ObjectNode branch = allOf.addObject();
                branch.set("if", compiler.compile(rule.when()));
                ObjectNode then = branch.putObject("then");
                ArrayNode conditionallyRequired = then.putArray("required");
                rule.required().forEach(conditionallyRequired::add);
            }
        }
        return schema;
    }

    private ObjectNode fieldSchema(FieldDefinition field) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", field.type());
        putIfPresent(schema, "minLength", field.minLength());
        putIfPresent(schema, "maxLength", field.maxLength());
        putIfPresent(schema, "minimum", field.minimum());
        putIfPresent(schema, "maximum", field.maximum());
        putIfPresent(schema, "format", field.format());
        putIfPresent(schema, "pattern", field.pattern());
        if (!field.enumValues().isEmpty()) {
            ArrayNode values = schema.putArray("enum");
            field.enumValues().forEach(values::add);
        }
        return schema;
    }

    private void validateDefinition(FormDefinition definition) {
        if (definition == null || definition.formKey() == null || definition.formKey().isBlank()) {
            throw new FormSchemaGenerationException("formKey must not be blank");
        }
        if (definition.version() < 1) {
            throw new FormSchemaGenerationException("version must be at least 1");
        }
        Set<String> keys = new HashSet<>();
        for (FieldDefinition field : definition.fields()) {
            if (field.key() == null || !KEY_PATTERN.matcher(field.key()).matches()) {
                throw new FormSchemaGenerationException("invalid field key: " + field.key());
            }
            if (!keys.add(field.key())) {
                throw new FormSchemaGenerationException("duplicate field key: " + field.key());
            }
            if (!SUPPORTED_TYPES.contains(field.type())) {
                throw new FormSchemaGenerationException("unsupported type for %s: %s".formatted(field.key(), field.type()));
            }
            if ((field.minLength() != null || field.maxLength() != null || field.pattern() != null)
                    && !"string".equals(field.type())) {
                throw new FormSchemaGenerationException("string constraints require a string field: " + field.key());
            }
            if ((field.minimum() != null || field.maximum() != null)
                    && !("integer".equals(field.type()) || "number".equals(field.type()))) {
                throw new FormSchemaGenerationException("numeric constraints require a number field: " + field.key());
            }
        }
        for (ConditionalRule rule : definition.conditionalRules()) {
            if (rule.id() == null || rule.id().isBlank() || rule.when() == null || rule.when().isBlank() || rule.required().isEmpty()) {
                throw new FormSchemaGenerationException("conditional rule must have id, when and required fields");
            }
            for (String key : rule.required()) {
                if (!keys.contains(key)) {
                    throw new FormSchemaGenerationException("conditional rule %s references an unbound field: %s".formatted(rule.id(), key));
                }
            }
        }
    }

    private static void putIfPresent(ObjectNode target, String name, Integer value) {
        if (value != null) {
            target.put(name, value);
        }
    }

    private static void putIfPresent(ObjectNode target, String name, Double value) {
        if (value != null) {
            if (value >= Long.MIN_VALUE && value <= Long.MAX_VALUE && value == Math.rint(value)) {
                target.put(name, value.longValue());
            } else {
                target.put(name, value);
            }
        }
    }

    private static void putIfPresent(ObjectNode target, String name, String value) {
        if (value != null) {
            target.put(name, value);
        }
    }
}
