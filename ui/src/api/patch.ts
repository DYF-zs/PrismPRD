import { get, post } from './client'
import type { PatchDetail } from './types'

export const createFindingFix = (findingId: string) =>
  post<PatchDetail>(`/api/findings/${findingId}/fix`)

/** 校验并应用 Patch（成功后关联 Finding 自动标记为已解决） */
export const applyPatch = (patchId: string) =>
  post<PatchDetail>(`/api/patches/${patchId}/apply`)

export const rejectPatch = (patchId: string) =>
  post<PatchDetail>(`/api/patches/${patchId}/reject`)

export const fetchPatches = (prdId: string) =>
  get<PatchDetail[]>(`/api/prds/${prdId}/patches`)
