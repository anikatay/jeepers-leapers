import { Injectable, signal } from '@angular/core';
import { InstrumentResponse } from '../models/instrument.model';

@Injectable({
  providedIn: 'root'
})
export class TradeModalService {
  // Signal to trigger opening trade modal with specific instrument
  instrumentToTrade = signal<InstrumentResponse | null>(null);
  
  // Signal to trigger data refresh after a trade completes
  tradeCompleted = signal<boolean>(false);

  openTradeModal(instrument: InstrumentResponse, mode: 'BUY' | 'SELL' = 'BUY') {
    this.instrumentToTrade.set(instrument);
  }

  clearInstrument() {
    this.instrumentToTrade.set(null);
  }

  notifyTradeCompleted() {
    // Trigger the signal by toggling it
    this.tradeCompleted.set(!this.tradeCompleted());
  }
}
