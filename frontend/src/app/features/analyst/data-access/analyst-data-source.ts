import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

export interface AnalystInstrument {
  instrumentId: string;
  ticker: string;
  name: string;
  currentPrice: number;
}

export interface AnalystTrade {
  tradeId: string;
  accountId: string;
  instrumentId: string;
  ticker: string;
  name: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  executionPrice: number;
  tradeValue: number;
  executedAt: string;
}

export interface AnalystSnapshot {
  instruments: AnalystInstrument[];
  trades: AnalystTrade[];
}

export abstract class AnalystDataSource {
  abstract load(): Observable<AnalystSnapshot>;
}

@Injectable()
export class AnalystMockDataSource implements AnalystDataSource {
  private readonly instruments: AnalystInstrument[] = [
    { instrumentId: 'mock-aapl', ticker: 'AAPL', name: 'Apple Inc.', currentPrice: 226.40 },
    { instrumentId: 'mock-tsla', ticker: 'TSLA', name: 'Tesla Inc.', currentPrice: 248.98 },
    { instrumentId: 'mock-nvda', ticker: 'NVDA', name: 'NVIDIA Corp.', currentPrice: 142.62 },
    { instrumentId: 'mock-msft', ticker: 'MSFT', name: 'Microsoft Corp.', currentPrice: 510.20 },
    { instrumentId: 'mock-amzn', ticker: 'AMZN', name: 'Amazon.com Inc.', currentPrice: 231.15 },
    { instrumentId: 'mock-goog', ticker: 'GOOGL', name: 'Alphabet Inc.', currentPrice: 248.16 }
  ];

  private readonly trades: AnalystTrade[] = Array.from({ length: 30 }, (_, index) => {
    const instrument = this.instruments[index % this.instruments.length];
    const side: AnalystTrade['side'] = index % 3 === 0 ? 'SELL' : 'BUY';
    const quantity = 8 + ((index * 9) % 38);
    const executionPrice = Number((instrument.currentPrice * (0.94 + (index % 8) / 100)).toFixed(2));
    const day = String(Math.max(1, 15 - Math.floor(index / 2))).padStart(2, '0');

    return {
      tradeId: `mock-trade-${index + 1}`,
      accountId: `ANALYST-${String((index % 4) + 1).padStart(2, '0')}`,
      instrumentId: instrument.instrumentId,
      ticker: instrument.ticker,
      name: instrument.name,
      side,
      quantity,
      executionPrice,
      tradeValue: Number((quantity * executionPrice).toFixed(2)),
      executedAt: `2026-10-${day}T${String(9 + (index % 8)).padStart(2, '0')}:15:00Z`
    };
  });

  load(): Observable<AnalystSnapshot> {
    return of({
      instruments: this.instruments,
      trades: this.trades
    });
  }
}
