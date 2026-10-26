export interface StockIconStyle {
  label: string;
  background: string;
  color: string;
}

// Same badge styles used by the portfolio, home and trades pages.
const STOCK_ICON_STYLES: Record<string, StockIconStyle> = {
  AAPL: { label: 'A', background: '#111827', color: '#ffffff' },
  TSLA: { label: 'T', background: '#dc2626', color: '#ffffff' },
  MSFT: { label: 'M', background: '#2563eb', color: '#ffffff' },
  NVDA: { label: 'N', background: '#65a30d', color: '#ffffff' },
  GOOGL: { label: 'G', background: '#f3f4f6', color: '#2563eb' },
  AMZN: { label: 'a', background: '#111827', color: '#f59e0b' },
  META: { label: 'M', background: '#2563eb', color: '#ffffff' },
  'BRK.B': { label: 'B', background: '#1e3a8a', color: '#ffffff' },
};

export function getStockIcon(symbol: string | null | undefined): StockIconStyle {
  const ticker = (symbol ?? '').toUpperCase();
  return STOCK_ICON_STYLES[ticker] ?? {
    label: ticker.slice(0, 2) || '?',
    background: 'var(--secondary)',
    color: 'var(--primary)',
  };
}
