import { useRef, useState } from 'react'
import { clampWidth } from './panelLayout'

interface Props {
  label: string
  value: number
  min: number
  max: number
  edge?: 'start' | 'end'
  onResize: (width: number) => void
  onReset: () => void
}

export default function PanelResizeHandle({ label, value, min, max, edge = 'end', onResize, onReset }: Props) {
  const drag = useRef<{ id: number; x: number; width: number } | null>(null)
  const [dragging, setDragging] = useState(false)
  const direction = edge === 'end' ? 1 : -1
  return <div className={`panel-resize-handle panel-resize-handle--${edge}${dragging ? ' is-dragging' : ''}`}
    role="separator" aria-label={label} aria-orientation="vertical" tabIndex={0}
    aria-valuenow={Math.round(value)} aria-valuemin={Math.round(min)} aria-valuemax={Math.round(max)}
    title="拖动调整宽度；方向键微调；双击恢复默认宽度"
    onDoubleClick={onReset}
    onPointerDown={event => {
      if (event.button !== 0 || !event.isPrimary) return
      event.preventDefault()
      event.currentTarget.focus({ preventScroll: true })
      event.currentTarget.setPointerCapture(event.pointerId)
      drag.current = { id: event.pointerId, x: event.clientX, width: value }
      setDragging(true)
    }}
    onPointerMove={event => {
      const start = drag.current
      if (start?.id === event.pointerId) onResize(clampWidth(start.width + direction * (event.clientX - start.x), min, max))
    }}
    onPointerUp={event => {
      if (drag.current?.id !== event.pointerId) return
      drag.current = null
      setDragging(false)
      event.currentTarget.releasePointerCapture(event.pointerId)
    }}
    onLostPointerCapture={() => { drag.current = null; setDragging(false) }}
    onPointerCancel={() => { drag.current = null; setDragging(false) }}
    onKeyDown={event => {
      const step = event.shiftKey ? 50 : 10
      if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
        event.preventDefault()
        onResize(clampWidth(value + (event.key === 'ArrowRight' ? step : -step) * direction, min, max))
      } else if (event.key === 'Home' || event.key === 'End') {
        event.preventDefault(); onResize(event.key === 'Home' ? min : max)
      } else if (event.key === 'Enter') { event.preventDefault(); onReset() }
    }} />
}
