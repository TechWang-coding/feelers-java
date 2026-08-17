package com.tech.feelers.validation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One validation run: the form definition, the submitted data, and the caller-supplied clock.
 *
 * <p>The clock is part of the request rather than read from the machine, so a date rule cannot
 * produce different results depending on which host evaluated it.
 *
 * @param formDsl authoritative form definition, including its {@code x-feel-*} extensions
 * @param data submitted data to validate
 * @param today business date used by date rules
 * @param now business timestamp used by date rules
 * @param timeZone business time zone identifier
 */
public record ValidationRequest(JsonNode formDsl, JsonNode data, LocalDate today,
    OffsetDateTime now, String timeZone) {

  /** Requires every field, because a missing clock would silently fall back to machine time. */
  public ValidationRequest {
    Objects.requireNonNull(formDsl, "formDsl");
    Objects.requireNonNull(data, "data");
    Objects.requireNonNull(today, "today");
    Objects.requireNonNull(now, "now");
    Objects.requireNonNull(timeZone, "timeZone");
  }

  /**
   * Builds the read-only clock context exposed to expressions.
   *
   * @return the {@code clock} variable contents
   */
  public Map<String, Object> clockVariables() {
    Map<String, Object> clock = new LinkedHashMap<>();
    clock.put("today", today);
    clock.put("now", now);
    clock.put("timeZone", timeZone);
    return clock;
  }
}
