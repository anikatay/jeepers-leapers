import { TimedPoint, lowerBound, withAlpha } from './portfolio-chart.component';

function series(...timestamps: number[]): TimedPoint[] {
  return timestamps.map(timestamp => ({
    date: new Date(timestamp).toISOString(),
    value: 0,
    timestamp
  }));
}

describe('lowerBound', () => {
  const points = series(1_000, 2_000, 3_000, 4_000);

  it('finds the first index at or after the target', () => {
    expect(lowerBound(points, 1_000)).toBe(0);
    expect(lowerBound(points, 2_500)).toBe(2);
    expect(lowerBound(points, 3_000)).toBe(2);
  });

  it('clamps outside the series', () => {
    expect(lowerBound(points, 0)).toBe(0);
    expect(lowerBound(points, 9_000)).toBe(points.length);
  });

  it('handles an empty series', () => {
    expect(lowerBound([], 1_000)).toBe(0);
  });
});

describe('withAlpha', () => {
  it('converts six and three digit hex', () => {
    expect(withAlpha('#1a6b3c', 0.3)).toBe('rgba(26, 107, 60, 0.3)');
    expect(withAlpha('#abc', 1)).toBe('rgba(170, 187, 204, 1)');
  });

  it('converts rgb and rgba input', () => {
    expect(withAlpha('rgb(10, 20, 30)', 0.5)).toBe('rgba(10, 20, 30, 0.5)');
    expect(withAlpha('rgba(10, 20, 30, 0.9)', 0.2)).toBe('rgba(10, 20, 30, 0.2)');
  });

  it('returns null for a colour space it cannot parse, so the caller can fall back', () => {
    expect(withAlpha('oklch(0.6 0.12 150)', 0.3)).toBeNull();
    expect(withAlpha('not-a-colour', 0.3)).toBeNull();
  });
});
