import { get, patch, post, put, request } from './client'
import type {
  CreatePrdRequest,
  CreatePrdResponse,
  MeResponse,
  PageResult,
  PrdDetailResponse,
  PrdDocumentSummary,
  SaveDraftResponse,
  TemplateResponse,
} from './types'

export const fetchMe = () => get<MeResponse>('/api/me')

export const fetchTemplates = () => get<TemplateResponse[]>('/api/prd-templates')

export const createPrd = (body: CreatePrdRequest) => post<CreatePrdResponse>('/api/prds', body)

export interface PrdListQuery {
  query?: string
  status?: string
  page?: number
  size?: number
}

export const fetchPrdList = (q: PrdListQuery) => {
  const params = new URLSearchParams()
  if (q.query) params.set('query', q.query)
  if (q.status) params.set('status', q.status)
  params.set('page', String(q.page ?? 1))
  params.set('size', String(q.size ?? 20))
  return get<PageResult<PrdDocumentSummary>>(`/api/prds?${params.toString()}`)
}

export const fetchPrdDetail = (prdId: string) => get<PrdDetailResponse>(`/api/prds/${prdId}`)

export const deletePrd = (prdId: string) => request<void>('DELETE', `/api/prds/${encodeURIComponent(prdId)}`)

export interface UpdatePrdRequest {
  title?: string
  status?: string
}

export const updatePrd = (prdId: string, body: UpdatePrdRequest) =>
  patch<PrdDetailResponse>(`/api/prds/${prdId}`, body)

export const saveDraft = (prdId: string, revision: number, editorJson: unknown) =>
  put<SaveDraftResponse>(`/api/prds/${prdId}/draft`, { revision, editorJson })

export const importMarkdown = (prdId: string, revision: number, markdown: string) =>
  post<SaveDraftResponse>(`/api/prds/${prdId}/import`, { revision, markdown })
