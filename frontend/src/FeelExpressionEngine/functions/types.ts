/** Minimal date operations required by the registered calendar functions. */
export interface FeelDate {
  /** Whether the runtime successfully parsed or constructed this date. */
  readonly isValid: boolean;
  /** ISO weekday number used by the default business-calendar policy. */
  readonly weekday: number;
  /** Returns a date offset by the supplied number of calendar days. */
  plus(duration: { readonly days: number }): FeelDate;
  /** Serializes the date as an ISO calendar date, or `null` when no date is available. */
  toISODate(): string | null;
}
