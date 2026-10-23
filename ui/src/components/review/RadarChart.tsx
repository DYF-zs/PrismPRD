import type { ScoreDetail } from '../../api/types'
import { createRadarGeometry, polygonPoints, RADAR_LAYOUT } from './radarGeometry'

interface Props {
  scores: ScoreDetail[]
}

export function scoreColor(score: number | null): string {
  if (score == null) return '#9ca3af'
  if (score >= 9) return '#16a34a'
  if (score >= 7) return '#65a30d'
  if (score >= 5) return '#d97706'
  return '#dc2626'
}

export default function RadarChart({ scores }: Props) {
  const { axes, rings } = createRadarGeometry(scores)
  if (axes.length < 3) {
    return <div className="radar-empty">可用维度不足，无法绘制雷达图</div>
  }

  const { width, height, cx, cy } = RADAR_LAYOUT

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="radar-chart" role="img" aria-label="维度评分雷达图">
      {rings.map((ring) => (
        <polygon key={ring.score} points={polygonPoints(ring.points)} className="radar-ring" />
      ))}
      {axes.map(({ dimension, end: [x, y] }) => {
        return <line key={dimension.dimensionKey} x1={cx} y1={cy} x2={x} y2={y} className="radar-axis" />
      })}
      <polygon points={polygonPoints(axes.map((axis) => axis.value))} className="radar-value" />
      {axes.map(({ dimension: s, value: [x, y] }) => {
        return <circle key={s.dimensionKey} cx={x} cy={y} r={3.5} fill={scoreColor(s.score)} />
      })}
      {axes.map(({ dimension: s, label: [x, y] }) => {
        const isRight = Math.abs(x - cx) < 1
        const anchor = isRight ? 'middle' : x > cx ? 'start' : 'end'
        return (
          <g key={s.dimensionKey}>
            <text x={x} y={y - 4} textAnchor={anchor} className="radar-label">
              {s.dimensionName}
            </text>
            <text x={x} y={y + 11} textAnchor={anchor} className="radar-score" fill={scoreColor(s.score)}>
              {Number(s.score).toFixed(1)}
            </text>
          </g>
        )
      })}
      {rings.map(({ score, tick: [x, y] }) => (
        <text key={score} x={x} y={y} className="radar-tick">{score}</text>
      ))}
    </svg>
  )
}
