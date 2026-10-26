import { resolveMarketSession } from './market-session.service';

// Instants are UTC; New York is UTC-5 in winter (EST) and UTC-4 in summer (EDT).
describe('resolveMarketSession', () => {
  const regularDay = new Date('2026-10-21T15:00:00Z'); // Wed 11:00 AM ET

  it('reports open when the backend session check passes', () => {
    expect(resolveMarketSession('LIVE', 'NASDAQ', regularDay).state).toBe('open');
    expect(resolveMarketSession('STALE', 'NYSE', regularDay).state).toBe('open');
  });

  it('reports closed when the backend says the session is closed (weekends, holidays, after hours)', () => {
    const session = resolveMarketSession('CLOSED', 'NYSE', new Date('2026-10-24T15:00:00Z'));
    expect(session.state).toBe('closed');
    expect(session.detail).toContain('NYSE market is closed');
  });

  it('accepts the backend Nasdaq aliases', () => {
    expect(resolveMarketSession('LIVE', 'nms', regularDay).state).toBe('open');
  });

  it('is unavailable when no session status could be loaded', () => {
    expect(resolveMarketSession('UNAVAILABLE', 'NYSE', regularDay).state).toBe('unavailable');
    expect(resolveMarketSession(null, 'NYSE', regularDay).state).toBe('unavailable');
  });

  it('is unavailable for exchanges without a known trading schedule instead of guessing', () => {
    expect(resolveMarketSession('LIVE', 'TSX', regularDay).state).toBe('unavailable');
    expect(resolveMarketSession('CLOSED', '', regularDay).state).toBe('unavailable');
  });

  describe('US early closes (1:00 PM ET)', () => {
    it('closes the Friday after Thanksgiving at 1:00 PM ET', () => {
      expect(resolveMarketSession('LIVE', 'NYSE', new Date('2026-11-27T17:59:00Z')).state).toBe('open'); // 12:59 PM EST
      expect(resolveMarketSession('LIVE', 'NYSE', new Date('2026-11-27T18:00:00Z')).state).toBe('closed'); // 1:00 PM EST
    });

    it('closes on Christmas Eve when it falls Monday–Thursday', () => {
      const session = resolveMarketSession('LIVE', 'NASDAQ', new Date('2026-12-24T19:00:00Z')); // Thu 2:00 PM EST
      expect(session.state).toBe('closed');
      expect(session.detail).toContain('closed early');
    });

    it('closes on July 3 when it falls Monday–Thursday', () => {
      expect(resolveMarketSession('LIVE', 'NYSE', new Date('2025-07-03T17:30:00Z')).state).toBe('closed'); // Thu 1:30 PM EDT
    });

    it('does not apply an early close when July 3 is a Friday (observed holiday handled by the backend)', () => {
      expect(resolveMarketSession('LIVE', 'NYSE', new Date('2026-07-03T17:30:00Z')).state).toBe('open');
    });

    it('uses New York time, not UTC date boundaries', () => {
      // 2026-11-28 02:00 UTC is still Friday 9:00 PM EST in New York.
      expect(resolveMarketSession('LIVE', 'NYSE', new Date('2026-11-28T02:00:00Z')).state).toBe('closed');
      // A normal Friday afternoon is unaffected.
      expect(resolveMarketSession('LIVE', 'NYSE', new Date('2026-11-20T19:00:00Z')).state).toBe('open');
    });

    it('does not apply US half days to other exchanges', () => {
      expect(resolveMarketSession('LIVE', 'CRYPTO', new Date('2026-11-27T19:00:00Z')).state).toBe('open');
    });
  });
});
