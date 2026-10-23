import type { FindingSeverity } from '../../api/types'

export const FINDING_SEVERITY_META: Record<FindingSeverity, { label: string; color: string }> = {
  CRITICAL: { label: '严重', color: 'red' },
  MAJOR: { label: '主要', color: 'volcano' },
  MINOR: { label: '次要', color: 'orange' },
  SUGGESTION: { label: '建议', color: 'blue' },
}

export function findingSeverityMeta(value: string) {
  return FINDING_SEVERITY_META[value as FindingSeverity] ?? { label: value, color: 'default' }
}
