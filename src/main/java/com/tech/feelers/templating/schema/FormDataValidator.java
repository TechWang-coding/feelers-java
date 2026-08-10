package com.tech.feelers.templating.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;

import java.util.List;

/** Validates submitted JSON data using a generated Draft 2020-12 JSON Schema. */
public final class FormDataValidator {
    private final ObjectMapper objectMapper;
    private final SchemaRegistry schemaRegistry;

    public FormDataValidator() {
        this(new ObjectMapper());
    }

    public FormDataValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schemaRegistry = SchemaRegistry.withDialect(Dialects.getDraft202012());
    }

    public List<ValidationError> validate(JsonNode schemaNode, JsonNode data) {
        Schema schema = schemaRegistry.getSchema(schemaNode.toString(), InputFormat.JSON);
        return schema.validate(data.toString(), InputFormat.JSON, executionContext ->
                        executionContext.executionConfig(config -> config.formatAssertionsEnabled(true)))
                .stream()
                .map(error -> new ValidationError(
                        error.getInstanceLocation().toString(),
                        error.getSchemaLocation().toString(),
                        error.getKeyword(),
                        error.getMessage()))
                .toList();
    }

    public List<ValidationError> validate(JsonNode schemaNode, String data) {
        try {
            return validate(schemaNode, objectMapper.readTree(data));
        } catch (Exception exception) {
            throw new IllegalArgumentException("data must be valid JSON", exception);
        }
    }

    public record ValidationError(String instancePath, String schemaPath, String keyword, String message) {
    }
}
