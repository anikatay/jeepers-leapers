export interface Rect {
  x: number;
  y: number;
  w: number;
  h: number;
}

export type Placed<T> = Rect & { data: T };

interface Sized<T> {
  data: T;
  area: number;
}

/** Squarified treemap (Bruls, Huizing & van Wijk): keeps tiles close to square while preserving area. */
export function squarify<T>(nodes: { data: T; value: number }[], bounds: Rect): Placed<T>[] {
  const total = nodes.reduce((sum, node) => sum + Math.max(0, node.value), 0);
  if (!total || bounds.w <= 0 || bounds.h <= 0) return [];

  const scale = (bounds.w * bounds.h) / total;
  const items = nodes
    .filter(node => node.value > 0)
    .sort((left, right) => right.value - left.value)
    .map(node => ({ data: node.data, area: node.value * scale }));

  const placed: Placed<T>[] = [];
  let free = { ...bounds };
  let row: Sized<T>[] = [];

  for (let i = 0; i < items.length; ) {
    const side = Math.min(free.w, free.h);
    if (!row.length || worst([...row, items[i]], side) <= worst(row, side)) {
      row.push(items[i++]);
    } else {
      free = placeRow(row, free, placed);
      row = [];
    }
  }
  if (row.length) placeRow(row, free, placed);
  return placed;
}

function worst<T>(row: Sized<T>[], side: number): number {
  const sum = row.reduce((total, item) => total + item.area, 0);
  const max = Math.max(...row.map(item => item.area));
  const min = Math.min(...row.map(item => item.area));
  return Math.max((side * side * max) / (sum * sum), (sum * sum) / (side * side * min));
}

function placeRow<T>(row: Sized<T>[], free: Rect, placed: Placed<T>[]): Rect {
  const sum = row.reduce((total, item) => total + item.area, 0);
  if (free.w >= free.h) {
    const width = sum / free.h;
    let y = free.y;
    for (const item of row) {
      const h = item.area / width;
      placed.push({ data: item.data, x: free.x, y, w: width, h });
      y += h;
    }
    return { x: free.x + width, y: free.y, w: free.w - width, h: free.h };
  }
  const height = sum / free.w;
  let x = free.x;
  for (const item of row) {
    const w = item.area / height;
    placed.push({ data: item.data, x, y: free.y, w, h: height });
    x += w;
  }
  return { x: free.x, y: free.y + height, w: free.w, h: free.h - height };
}
