package com.tech.feelers.templating.schema;

import java.util.List;

/** Immutable Form version submitted by the form designer. */
public record FormDefinition(String formKey, int version, List<FieldDefinition> fields, List<ConditionalRule> conditionalRules) {

    public FormDefinition {
        fields = fields == null ? List.of() : List.copyOf(fields);
        conditionalRules = conditionalRules == null ? List.of() : List.copyOf(conditionalRules);
    }
}
