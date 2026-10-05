/**
 * Account Response DTO
 * Returned from account operations
 */
export interface AccountResponse {
  accountId: string;       // UUID
  userId: string;          // UUID
  currency: string;
  balance: number;         // BigDecimal as number
  status: string;
  createdAt: string;       // OffsetDateTime as ISO string
}
