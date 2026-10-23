import { useEffect, useState } from 'react'
import { DEFAULT_PANEL_WIDTHS, fitPanelWidths, PANEL_LAYOUT_KEY, readPanelWidths } from './panelLayout'
import type { PanelWidths } from './panelLayout'

export function usePanelLayout(visible: { resources: boolean; outline: boolean; chat: boolean; conversation: boolean }) {
  const [container, setContainer] = useState<HTMLDivElement | null>(null)
  const [width, setWidth] = useState(window.innerWidth)
  const [preferences, setPreferences] = useState(() => {
    try { return readPanelWidths(JSON.parse(localStorage.getItem(PANEL_LAYOUT_KEY) ?? 'null')) }
    catch { return { ...DEFAULT_PANEL_WIDTHS } }
  })
  useEffect(() => {
    if (!container) return
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width))
    observer.observe(container)
    return () => observer.disconnect()
  }, [container])
  useEffect(() => {
    try { localStorage.setItem(PANEL_LAYOUT_KEY, JSON.stringify(preferences)) } catch {}
  }, [preferences])

  const fitted = fitPanelWidths(preferences, width, visible)
  return {
    ...fitted,
    containerRef: setContainer,
    resize: (key: keyof PanelWidths, value: number) => {
      const current = { ...preferences }
      for (const panel of Object.keys(current) as (keyof PanelWidths)[]) {
        if (fitted.inline[panel]) current[panel] = fitted.sizes[panel]
      }
      setPreferences(readPanelWidths({ ...current, [key]: value }))
    },
    reset: (key: keyof PanelWidths) => setPreferences(previous => ({ ...previous, [key]: DEFAULT_PANEL_WIDTHS[key] })),
  }
}
