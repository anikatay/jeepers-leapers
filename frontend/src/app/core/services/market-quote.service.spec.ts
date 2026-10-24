import { MarketQuoteService } from './market-quote.service';

class FakeSocket {
  static readonly OPEN = 1;
  static readonly CLOSED = 3;
  static last: FakeSocket;

  readyState = FakeSocket.OPEN;
  readonly sent: Array<{ action: string; ticker: string }> = [];
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;

  constructor() {
    FakeSocket.last = this;
  }

  send(payload: string): void {
    this.sent.push(JSON.parse(payload));
  }

  close(): void {
    this.readyState = FakeSocket.CLOSED;
  }
}

describe('MarketQuoteService subscriptions', () => {
  let service: MarketQuoteService;
  const unsubscribes = () => FakeSocket.last.sent.filter(message => message.action === 'UNSUBSCRIBE').map(message => message.ticker);

  beforeEach(() => {
    vi.stubGlobal('WebSocket', FakeSocket);
    service = new MarketQuoteService();
  });

  afterEach(() => vi.unstubAllGlobals());

  it('keeps a ticker subscribed while another owner still needs it', () => {
    const heatmap = {};
    service.subscribe('AAPL');
    service.subscribe('aapl', heatmap);

    service.unsubscribe('AAPL', heatmap);
    expect(unsubscribes()).toEqual([]);

    service.unsubscribe('AAPL');
    expect(unsubscribes()).toEqual(['AAPL']);
  });

  it('keeps the existing shared behaviour for callers without an owner', () => {
    service.subscribe('MSFT');
    service.subscribe('MSFT');
    service.unsubscribe('MSFT');
    expect(unsubscribes()).toEqual(['MSFT']);
  });

  it('ignores releases from owners that never subscribed', () => {
    service.subscribe('NVDA');
    service.unsubscribe('NVDA', {});
    expect(unsubscribes()).toEqual([]);
  });

  it('resubscribes every owned ticker once after reconnecting', () => {
    service.subscribe('AAPL');
    service.subscribe('AAPL', {});
    service.subscribe('MSFT', {});
    FakeSocket.last.sent.length = 0;

    FakeSocket.last.onopen?.();
    expect(FakeSocket.last.sent).toEqual([
      { action: 'SUBSCRIBE', ticker: 'AAPL' },
      { action: 'SUBSCRIBE', ticker: 'MSFT' }
    ]);
  });
});
