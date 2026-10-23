import { ApiError, fetchWithTimeout, get, post, request } from './client'
import type { VersionDetail, VersionSummary } from './types'

export const fetchVersions = (prdId: string) =>
  get<VersionSummary[]>(`/api/prds/${prdId}/versions`)

export const createVersion = (prdId: string, changeSummary: string) =>
  post<VersionDetail>(`/api/prds/${prdId}/versions`, { changeSummary })

export const fetchVersionDetail = (versionId: string) =>
  get<VersionDetail>(`/api/prd-versions/${versionId}`)

export const useVersion = (versionId: string, revision: number) =>
  post<void>(`/api/prd-versions/${encodeURIComponent(versionId)}/use`, { revision })

export const deleteVersion = (versionId: string) =>
  request<void>('DELETE', `/api/prd-versions/${encodeURIComponent(versionId)}`)

export type ExportFormat = 'markdown' | 'pdf'
export const versionExportUrl = (versionId: string, format: ExportFormat = 'markdown') => `/api/prd-versions/${encodeURIComponent(versionId)}/export?format=${format}`

export async function fetchVersionExport(versionId: string, format: ExportFormat): Promise<Blob> {
  const response = await fetchWithTimeout(versionExportUrl(versionId, format), {}, {
    timeout: '导出超时，请稍后重试',
    network: '无法连接服务器，请检查服务是否启动',
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => null)
    throw new ApiError(response.status, payload?.code ?? response.status, payload?.message ?? `导出失败（HTTP ${response.status}）`)
  }
  const expected = format === 'pdf' ? 'application/pdf' : 'text/markdown'
  if (!response.headers.get('Content-Type')?.toLowerCase().startsWith(expected)) throw new ApiError(response.status, 0, '服务器未返回预期的导出文件，请稍后重试')
  return response.blob()
}

export interface VersionDiff {
  leftId: string
  rightId: string
  leftVersionNo: number
  rightVersionNo: number
  addedLines: number
  deletedLines: number
  lines: { type: 'EQUAL' | 'INSERT' | 'DELETE'; text: string }[]
  acceptedPatches: import('./types').PatchDetail[]
}
export const fetchVersionDiff = (leftId: string, rightId: string) =>
  get<VersionDiff>(`/api/prd-versions/${leftId}/diff/${rightId}`)
