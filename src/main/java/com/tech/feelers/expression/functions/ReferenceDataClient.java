package com.tech.feelers.expression.functions;

import com.tech.feelers.expression.errors.ReferenceDataException;

/**
 * Boundary to the reference-data service used by {@code existsInReferenceSet}.
 *
 * <p>This interface exists so the expression engine stays free of transport concerns: the function
 * itself contains no HTTP, no URLs, and no credentials, and unit tests can supply a deterministic
 * implementation without starting a server.
 *
 * <p>Implementations must distinguish "answered: not found" from "could not answer". The former is
 * an ordinary {@code false}; the latter must raise {@link ReferenceDataException} so the validation
 * layer can report it as an upstream failure rather than a failed business rule.
 */
public interface ReferenceDataClient {

  /**
   * Resolves a field's details, including the dictionary that backs it.
   *
   * @param fieldKey stable key of the field being validated
   * @return the field's metadata
   * @throws ReferenceDataException when the lookup cannot complete
   */
  FieldMetadata findField(String fieldKey);

  /**
   * Reports whether a value exists in the given dictionary.
   *
   * @param dataSourceUniqueName unique name of the dictionary to query
   * @param value candidate value supplied by the submitted data
   * @return whether the dictionary accepts the value
   * @throws ReferenceDataException when the lookup cannot complete
   */
  boolean containsValue(String dataSourceUniqueName, String value);
}
