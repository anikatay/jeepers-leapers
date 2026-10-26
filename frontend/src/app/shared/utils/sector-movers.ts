import { MarketHeatmapSector } from '../../core/models/market.model';
import { PerformancePeriod, QuoteInput, percentChange } from './market-performance';
import { PriceBar } from '../../core/models/market.model';

/** A ticker's performance within its sector, ready to render. */
export interface SectorMover {
  ticker: string;
  sectorId: string;
  sectorName: string;
  change: number;
  price: number | null;
  /** Worst performer in the sector rather than one of the leaders. */
  laggard: boolean;
}

export interface SectorMoverOptions {
  /** Leaders taken from the top of each sector. */
  top?: number;
  /** Laggards taken from the bottom of each sector. */
  bottom?: number;
}

const DEFAULT_TOP = 3;
const DEFAULT_BOTTOM = 1;

/**
 * Best and worst performers within each sector, using the same percent-change rule the heatmap
 * applies so the two views cannot disagree about how a ticker is doing.
 *
 * A ticker whose change cannot be established — no quote, no usable reference close — is left
 * out rather than ranked as flat, because a missing reference is not a zero return. Sectors are
 * kept in the order the market service returns them, which is its own sector ordering.
 */
export function selectSectorMovers(
  sectors: MarketHeatmapSector[],
  histories: Map<string, PriceBar[] | null>,
  period: PerformancePeriod,
  options: SectorMoverOptions = {}
): SectorMover[] {
  const top = Math.max(0, options.top ?? DEFAULT_TOP);
  const bottom = Math.max(0, options.bottom ?? DEFAULT_BOTTOM);
  if (top === 0 && bottom === 0) return [];

  return sectors.flatMap(sector => {
    const ranked = sector.tickers
      .map((ticker): SectorMover | null => {
        const quote: QuoteInput = {
          price: ticker.price,
          quoteTime: ticker.quoteTime,
          status: ticker.status
        };
        const change = percentChange(quote, histories.get(ticker.ticker), period);
        if (change === null) return null;

        return {
          ticker: ticker.ticker,
          sectorId: sector.id,
          sectorName: sector.name,
          change,
          price: ticker.price,
          laggard: false
        };
      })
      .filter((mover): mover is SectorMover => mover !== null)
      .sort((a, b) => b.change - a.change || a.ticker.localeCompare(b.ticker));

    if (ranked.length === 0) return [];

    const leaders = ranked.slice(0, top);
    // Never show the same ticker twice: in a sector with fewer usable tickers than
    // top + bottom, the leaders win and the laggard slot goes unfilled.
    const laggards = ranked
      .slice(Math.max(leaders.length, ranked.length - bottom))
      .map(mover => ({ ...mover, laggard: true }));

    return [...leaders, ...laggards];
  });
}
