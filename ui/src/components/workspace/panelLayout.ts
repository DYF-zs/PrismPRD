export interface PanelWidths { resources: number; outline: number; chat: number }
export const DEFAULT_PANEL_WIDTHS: PanelWidths = { resources: 320, outline: 176, chat: 360 }
const LIMITS = { resources: [240, 640], outline: [128, 360], chat: [280, 800] } as const
export const PANEL_LAYOUT_KEY = 'prismprd.workspace.widths'

export const clampWidth = (value: number, min: number, max: number) => Math.min(Math.max(value, min), Math.max(min, max))

export function readPanelWidths(value: unknown): PanelWidths {
  const input = value && typeof value === 'object' ? value as Record<string, unknown> : {}
  return Object.fromEntries(Object.entries(DEFAULT_PANEL_WIDTHS).map(([key, fallback]) => {
    const width = input[key], [min, max] = LIMITS[key as keyof PanelWidths]
    return [key, typeof width === 'number' && Number.isFinite(width) ? clampWidth(width, min, max) : fallback]
  })) as unknown as PanelWidths
}

export function fitPanelWidths(preferences: PanelWidths, containerWidth: number, visible: {
  resources: boolean; outline: boolean; chat: boolean; conversation: boolean
}) {
  const width = Number.isFinite(containerWidth) ? Math.max(0, containerWidth) : 0
  const inline = {
    resources: visible.resources && width >= 1200,
    outline: visible.outline && width > 1100,
    chat: visible.chat && (width > 1100 || (visible.conversation && width > 700)),
  }
  const sizes = readPanelWidths(preferences)
  const keys = (Object.keys(sizes) as (keyof PanelWidths)[]).filter(key => inline[key])
  const budget = Math.max(0, width - 68 - 352)
  const total = keys.reduce((sum, key) => sum + sizes[key], 0)
  const capacity = keys.reduce((sum, key) => sum + sizes[key] - LIMITS[key][0], 0)
  if (total > budget && capacity > 0) {
    const ratio = Math.min(1, (total - budget) / capacity)
    keys.forEach(key => { sizes[key] -= (sizes[key] - LIMITS[key][0]) * ratio })
  }
  const bounds = Object.fromEntries((Object.keys(sizes) as (keyof PanelWidths)[]).map(key => {
    const others = keys.filter(item => item !== key).reduce((sum, item) => sum + sizes[item], 0)
    const [min, max] = LIMITS[key]
    const available = inline[key] ? budget - others : width <= 700 ? width : width - 92
    const upper = Math.max(0, Math.min(max, available))
    const lower = Math.min(min, upper)
    sizes[key] = clampWidth(sizes[key], lower, upper)
    return [key, { min: lower, max: upper }]
  })) as Record<keyof PanelWidths, { min: number; max: number }>
  return { sizes, bounds, inline }
}
