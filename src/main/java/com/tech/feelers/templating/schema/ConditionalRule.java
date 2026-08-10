package com.tech.feelers.templating.schema;

import java.util.List;

/** A conditional requirement authored in the Form designer. */
public record ConditionalRule(String id, String when, List<String> required) {

    public ConditionalRule {
        required = required == null ? List.of() : List.copyOf(required);
    }
}
