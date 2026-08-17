package com.tech.feelers.validation.adapters.networknt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.tech.feelers.validation.ErrorCategory;
import com.tech.feelers.validation.ValidationError;

/**
 * Adapter around the networknt JSON Schema validator.
 *
 * <p>All networknt types stop here: the rest of the service sees only the shared error contract.
 * Format is asserted rather than annotated, because Draft 2020-12 treats {@code format} as an
 * annotation by default and a form that declares {@code format: "date"} expects it to be enforced.
 */
public final class SchemaValidatorAdapter {

  private final JsonSchemaFactory factory =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

  /**
   * Validates data against a generated schema.
   *
   * @param schema pure Draft 2020-12 schema produced for this submission
   * @param data submitted data
   * @return one error per violation, in the validator's reporting order
   */
  public List<ValidationError> validate(JsonNode schema, JsonNode data) {
    SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
        .formatAssertionsEnabled(true)
        .build();
    JsonSchema compiled = factory.getSchema(schema, config);

    Set<ValidationMessage> messages = compiled.validate(data);
    List<ValidationError> errors = new ArrayList<>(messages.size());
    messages.forEach(message -> errors.add(toValidationError(message)));
    return errors;
  }

  /**
   * Converts one validator message into the shared error contract.
   *
   * <p>The validator's own localized text is discarded: callers render messages from the catalogue
   * key so both runtimes can produce identical wording.
   *
   * @param message validator message
   * @return the mapped error
   */
  private static ValidationError toValidationError(ValidationMessage message) {
    String keyword = message.getType();
    Map<String, Object> arguments = new LinkedHashMap<>();
    if (message.getArguments() != null) {
      for (int index = 0; index < message.getArguments().length; index++) {
        arguments.put("arg" + index, String.valueOf(message.getArguments()[index]));
      }
    }
    return new ValidationError(ErrorCategory.DATA, "data." + keyword,
        message.getInstanceLocation().toString(), message.getSchemaLocation().toString(), keyword,
        null, "validation." + keyword, null, arguments);
  }
}
