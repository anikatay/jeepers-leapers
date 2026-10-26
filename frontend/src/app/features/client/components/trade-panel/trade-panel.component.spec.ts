import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AccountService } from '../../../../core/services/account.service';
import { AuthService } from '../../../../core/services/auth.service';
import { InstrumentService } from '../../../../core/services/instrument.service';
import { MarketDataService } from '../../../../core/services/market-data.service';
import { TradeModalService } from '../../../../core/services/trade-modal.service';
import { TradesService } from '../../../../core/services/trades.service';
import { QuoteStatus } from '../../../../core/models/market.model';
import { TradePanelComponent, TradePanelInstrument } from './trade-panel.component';

const AAPL: TradePanelInstrument = { instrumentId: 'i-1', ticker: 'AAPL', name: 'Apple Inc.', currency: 'USD' };

function setup(instrument: TradePanelInstrument, quote: () => Observable<{ status: QuoteStatus }>, createTrade = vi.fn()) {
  const getInstrumentByTicker = vi.fn(() => of({ exchangeId: 'NASDAQ' }));
  TestBed.configureTestingModule({
    imports: [TradePanelComponent],
    providers: [
      { provide: AuthService, useValue: { session: () => ({ accountId: 'acc-1' }) } },
      { provide: AccountService, useValue: { getAccount: () => of({ accountId: 'acc-1', currency: 'USD', balance: 10_000 }) } },
      { provide: MarketDataService, useValue: { getQuote: quote, getExchangeRate: () => of({ rate: 1 }) } },
      { provide: InstrumentService, useValue: { getInstrumentByTicker } },
      { provide: TradesService, useValue: { createTrade } },
      { provide: TradeModalService, useValue: { notifyTradeCompleted: vi.fn() } }
    ]
  });
  const fixture = TestBed.createComponent(TradePanelComponent);
  fixture.componentRef.setInput('instrument', instrument);
  fixture.componentRef.setInput('price', 200);
  fixture.componentRef.setInput('holdingsStatus', 'ready');
  fixture.componentRef.setInput('ownedShares', 5);
  fixture.detectChanges();
  return { panel: fixture.componentInstance, getInstrumentByTicker, createTrade };
}

describe('TradePanelComponent market availability', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('looks up the exchange when the opener does not provide it, then blocks a confirmed close', async () => {
    const { panel, getInstrumentByTicker } = setup(AAPL, () => of({ status: 'CLOSED' }));
    await vi.waitFor(() => expect(panel.marketSession()?.state).toBe('closed'));

    expect(getInstrumentByTicker).toHaveBeenCalledWith('AAPL');
    panel.onSharesChange('1');
    expect(panel.canSubmit()).toBe(false);
    expect(panel.notice()?.text).toContain('NASDAQ market is closed');
  });

  it('uses the provided exchange without an extra lookup', async () => {
    const { panel, getInstrumentByTicker } = setup({ ...AAPL, exchangeId: 'NASDAQ' }, () => of({ status: 'LIVE' }));
    await vi.waitFor(() => expect(panel.marketSession()?.state).toBe('open'));

    expect(getInstrumentByTicker).not.toHaveBeenCalled();
    panel.onSharesChange('1');
    expect(panel.canSubmit()).toBe(true);
  });

  it('leaves uncertain status to backend validation and shows the returned reason', async () => {
    const rejection = {
      status: 409,
      error: {
        code: 'MARKET_CLOSED',
        message: 'The market is closed',
        details: 'Unable to make trade at the moment; trading schedule is unavailable for TSX'
      }
    };
    const createTrade = vi.fn(() => throwError(() => rejection));
    const { panel } = setup({ ...AAPL, exchangeId: 'TSX' }, () => throwError(() => ({ status: 404 })), createTrade);
    await vi.waitFor(() => expect(panel.marketSession()?.state).toBe('unavailable'));

    panel.onSharesChange('1');
    expect(panel.canSubmit()).toBe(true);
    vi.spyOn(console, 'error').mockImplementation(() => {});
    panel.submit();

    expect(createTrade).toHaveBeenCalledWith({ accountId: 'acc-1', instrumentId: 'i-1', side: 'BUY', quantity: 1 });
    expect(panel.submitError()).toBe(rejection.error.details);
  });
});
