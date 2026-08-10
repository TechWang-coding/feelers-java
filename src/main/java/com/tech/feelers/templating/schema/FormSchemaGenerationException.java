package com.tech.feelers.templating.schema;

/** Raised when a Form definition cannot be safely compiled into JSON Schema. */
public class FormSchemaGenerationException extends IllegalArgumentException {
    public FormSchemaGenerationException(String message) {
        super(message);
    }
}
