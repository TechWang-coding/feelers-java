package com.tech.feelers.expression.functions;

import java.util.Objects;
import java.util.Optional;

/**
 * Field details returned by the field-metadata service. The data source is one property among
 * several, so a field may legitimately exist without one.
 *
 * @param fieldKey stable key identifying the field
 * @param dataSourceUniqueName unique name of the dictionary backing this field, or {@code null}
 */
public record FieldMetadata(String fieldKey, String dataSourceUniqueName) {

  /** Requires the identifying key so a metadata record can always be traced back to a field. */
  public FieldMetadata {
    Objects.requireNonNull(fieldKey, "fieldKey");
  }

  /**
   * Exposes the data source as an {@link Optional} because a field without one is a valid response
   * that callers must handle rather than a transport failure.
   *
   * @return the dictionary unique name when configured
   */
  public Optional<String> optionalDataSourceUniqueName() {
    return Optional.ofNullable(dataSourceUniqueName);
  }
}
