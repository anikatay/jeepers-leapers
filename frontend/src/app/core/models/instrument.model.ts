/**
 * Instrument Request DTO
 * Used when creating or updating an instrument
 */
export interface InstrumentRequest {
  exchangeId: string;
  ticker: string;
  name: string;
  currentPrice: number;  // BigDecimal as number
}

/**
 * Instrument Response DTO
 * Returned from instrument operations
 */
export interface InstrumentResponse {
  instrumentId: string;  // UUID as string
  ticker: string;
  name: string;
  currentPrice: number;  // BigDecimal as number
  exchangeId: string;
}
