/**
 * The exchange timezone every market-facing view labels its timestamps in. Quote and bar
 * timestamps arrive as UTC instants; rendering them in the browser's zone (or in UTC) makes
 * a 09:30 ET open read as something else, so views format against this zone explicitly.
 */
export const MARKET_TIMEZONE = 'America/New_York';

/** Short suffix shown next to a market-time clock reading. */
export const MARKET_TIMEZONE_LABEL = 'ET';
