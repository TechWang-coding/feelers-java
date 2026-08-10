package com.tech.feelers.templating.schema;

import java.util.List;

/** A field selected from the field configuration table and bound to a Form. */
public record FieldDefinition(
        String key,
        String type,
        boolean required,
        Integer minLength,
        Integer maxLength,
        Double minimum,
        Double maximum,
        String pattern,
        String format,
        List<String> enumValues) {

    public FieldDefinition {
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }
}
