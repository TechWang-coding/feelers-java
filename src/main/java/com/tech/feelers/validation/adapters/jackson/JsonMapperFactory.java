package com.tech.feelers.validation.adapters.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Builds the JSON mapper used for validation.
 *
 * <p>Decimal-exact float parsing is mandatory rather than optional: without it a high-precision
 * number is truncated to {@code double} while the JSON is being read, before any expression runs,
 * and no amount of {@code BigDecimal} arithmetic afterwards can recover the lost digits.
 */
public final class JsonMapperFactory {

  private JsonMapperFactory() {
  }

  /**
   * Creates a mapper configured for exact decimal parsing.
   *
   * @return a mapper that reads JSON floats as {@link java.math.BigDecimal}
   */
  public static ObjectMapper create() {
    return JsonMapper.builder()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .build();
  }
}
