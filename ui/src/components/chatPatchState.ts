import type { PatchDetail } from '../api/types'

export function sortPatches(patches: PatchDetail[]) {
  return [...patches].sort((a, b) =>
    (a.createdAt ?? '').localeCompare(b.createdAt ?? '') || a.id.localeCompare(b.id),
  )
}

/** 恢复会话时合并历史补丁与实时事件，保留已完成的补丁状态。 */
export function restoreSessionPatches(history: PatchDetail[], live: Record<string, PatchDetail>, sessionId: string) {
  const restored = Object.fromEntries(history.filter(p => p.sessionId === sessionId).map(p => [p.id, p]))
  for (const patch of Object.values(live)) {
    if (patch.sessionId !== sessionId) continue
    if (restored[patch.id]?.status !== 'PENDING' && restored[patch.id] && patch.status === 'PENDING') continue
    restored[patch.id] = patch
  }
  return restored
}
