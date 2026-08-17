package com.tech.feelers.expression.functions;

import java.util.List;
import java.util.Objects;

import com.tech.feelers.expression.FeelFunctionDefinition;
import com.tech.feelers.expression.errors.ReferenceDataException;

/**
 * Creates the {@code existsInReferenceSet(fieldKey, value)} definition, which answers whether a
 * submitted value exists in the dictionary configured for a field.
 *
 * <p>This is a controlled query function: unlike every other registered function it reaches an
 * external system, so it is backend-only and its failures are reported separately from a failed
 * business rule.
 */
public final class ExistsInReferenceSetFunction {

  private ExistsInReferenceSetFunction() {
  }

  /**
   * Builds the definition bound to one reference-data boundary.
   *
   * <p>Resolution happens in two steps because the dictionary is a property of the field rather
   * than something a rule author writes by hand: the field is resolved first, then its dictionary
   * is queried.
   *
   * @param client boundary used to resolve field metadata and query dictionaries
   * @return a registered-function definition taking a field key and a candidate value
   */
  public static FeelFunctionDefinition create(ReferenceDataClient client) {
    Objects.requireNonNull(client, "client");
    return new FeelFunctionDefinition("existsInReferenceSet", List.of("fieldKey", "value"),
        args -> evaluate(client, args));
  }

  /**
   * Performs the two-step lookup, keeping argument checking separate from the transport boundary so
   * a malformed rule fails with a clear message instead of an obscure transport error.
   *
   * @param client reference-data boundary
   * @param args positional arguments supplied by the expression
   * @return whether the value exists in the field's dictionary
   * @throws IllegalArgumentException when the arguments are not the expected types
   * @throws ReferenceDataException when the field declares no data source or a lookup fails
   */
  private static Object evaluate(ReferenceDataClient client, List<Object> args) {
    if (args.size() != 2) {
      throw new IllegalArgumentException("existsInReferenceSet expects exactly two arguments");
    }
    if (!(args.get(0) instanceof String fieldKey) || fieldKey.isBlank()) {
      throw new IllegalArgumentException("existsInReferenceSet fieldKey must be a non-blank string");
    }
    Object value = args.get(1);
    if (value == null) {
      return false;
    }
    if (!(value instanceof String candidate)) {
      throw new IllegalArgumentException("existsInReferenceSet value must be a string");
    }

    FieldMetadata metadata = client.findField(fieldKey);
    String dataSource = metadata.optionalDataSourceUniqueName()
        .orElseThrow(() -> new ReferenceDataException(ReferenceDataException.Reason.NOT_CONFIGURED,
            "Field \"" + fieldKey + "\" declares no data source"));
    return client.containsValue(dataSource, candidate);
  }
}
