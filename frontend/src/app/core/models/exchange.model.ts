// Request DTO for creating/updating exchanges
export interface ExchangeRequest {
  exchangeId: string;
  name: string;
  region: string;
  timezone: string;
  currency: string;
}

// Response DTO for exchange data from backend
export interface ExchangeResponse {
  exchangeId: string;
  name: string;
  region: string;
  timezone: string;
  currency: string;
}
