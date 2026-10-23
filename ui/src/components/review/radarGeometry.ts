import type { ScoreDetail } from '../../api/types'

export const RADAR_LAYOUT = { width: 360, height: 330, cx: 180, cy: 165, radius: 105 }
const RING_SCORES = [2, 4, 6, 8, 10]
type Point = readonly [number, number]

export function polygonPoints(points: readonly Point[]): string {
  return points.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ')
}

export function createRadarGeometry(scores: ScoreDetail[]) {
  const { cx, cy, radius } = RADAR_LAYOUT
  const applicable = scores
    .filter((s) => s.applicable && s.score != null && Number.isFinite(s.score))
    .sort((a, b) => a.displayOrder - b.displayOrder)
  const pointAt = (index: number, r: number): Point => {
    const angle = -Math.PI / 2 + (index * 2 * Math.PI) / applicable.length
    return [cx + r * Math.cos(angle), cy + r * Math.sin(angle)]
  }
  const axes = applicable.map((dimension, i) => ({
    dimension,
    end: pointAt(i, radius),
    label: pointAt(i, radius + 22),
    value: pointAt(i, (Math.max(0, Math.min(dimension.score!, 10)) / 10) * radius),
  }))
  const rings = RING_SCORES.map((score) => ({
    score,
    // Every grid ring has one vertex per visible dimension, just like the score polygon.
    points: applicable.map((_, i) => pointAt(i, (score / 10) * radius)),
    tick: [cx + 4, cy - (score / 10) * radius - 2] as Point,
  }))
  return { axes, rings }
}
