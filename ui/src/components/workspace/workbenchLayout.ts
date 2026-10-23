import { useEffect, useState } from 'react'
import { clampWidth } from './panelLayout.ts'

interface WorkbenchWidths { recent: number; preview: number }
export const DEFAULT_WORKBENCH_WIDTHS: WorkbenchWidths = { recent: 220, preview: 320 }
const LIMITS = { recent: [180, 400], preview: [260, 760] } as const
const STORAGE_KEY = 'prismprd.workbench.widths'
const panelKeys = ['recent', 'preview'] as const

export function readWorkbenchWidths(value: unknown): WorkbenchWidths {
  const input = value && typeof value === 'object' ? value as Record<string, unknown> : {}
  const result = { ...DEFAULT_WORKBENCH_WIDTHS }
  for (const key of panelKeys) {
    const width = input[key], [min, max] = LIMITS[key]
    if (typeof width === 'number' && Number.isFinite(width)) result[key] = clampWidth(width, min, max)
  }
  return result
}

export function fitWorkbenchWidths(preferences: WorkbenchWidths, containerWidth: number) {
  const width = Number.isFinite(containerWidth) ? Math.max(0, containerWidth) : 0
  const sizes = readWorkbenchWidths(preferences)
  const visible = { recent: width > 900, preview: width > 700 }
  const keys = panelKeys.filter(key => visible[key])
  const budget = Math.max(0, width - 360)
  const total = keys.reduce((sum, key) => sum + sizes[key], 0)
  const capacity = keys.reduce((sum, key) => sum + sizes[key] - LIMITS[key][0], 0)
  if (total > budget && capacity > 0) {
    const ratio = Math.min(1, (total - budget) / capacity)
    keys.forEach(key => { sizes[key] -= (sizes[key] - LIMITS[key][0]) * ratio })
  }
  return {
    sizes, visible,
    bounds: {
      recent: { min: 180, max: Math.max(180, Math.min(400, budget - (visible.preview ? sizes.preview : 0))) },
      preview: { min: 260, max: Math.max(260, Math.min(760, budget - (visible.recent ? sizes.recent : 0))) },
    },
  }
}

export function useWorkbenchLayout() {
  const [container, setContainer] = useState<HTMLDivElement | null>(null)
  const [width, setWidth] = useState(window.innerWidth)
  const [preferences, setPreferences] = useState(() => {
    try { return readWorkbenchWidths(JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null')) }
    catch { return { ...DEFAULT_WORKBENCH_WIDTHS } }
  })
  useEffect(() => {
    if (!container) return
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width))
    observer.observe(container)
    return () => observer.disconnect()
  }, [container])
  useEffect(() => {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences)) } catch {}
  }, [preferences])
  const fitted = fitWorkbenchWidths(preferences, width)
  return {
    ...fitted,
    containerRef: setContainer,
    resize: (key: keyof WorkbenchWidths, value: number) => {
      const current = { ...preferences }
      for (const panel of panelKeys) { if (fitted.visible[panel]) current[panel] = fitted.sizes[panel] }
      setPreferences(readWorkbenchWidths({ ...current, [key]: value }))
    },
    reset: (key: keyof WorkbenchWidths) => setPreferences(previous => ({ ...previous, [key]: DEFAULT_WORKBENCH_WIDTHS[key] })),
  }
}
