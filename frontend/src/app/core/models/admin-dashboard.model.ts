import { TradeResponse } from './trade.model';

export interface AdminDashboardRow {
  userId: string;
  firstName: string;
  lastName: string;
  email: string;
  userRole: string;
  userStatus: string;
  accountId: string;
  currency: string;
  balance: number;
  accountStatus: string;
  freezeReason: string | null;
  createdAt: string;
  tradeCount: number;
  portfolioValue: number;
  allTimeProfit: number;
}

export interface AdminDashboardResponse {
  items: AdminDashboardRow[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type AdminAccountTrades = TradeResponse[];
