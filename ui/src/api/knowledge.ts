import { get, post, put, request } from './client'
import type { CreatePrdResponse, SaveDraftResponse } from './types'

export interface KnowledgeBase { id: string; name: string; description: string; owned: boolean; bound: boolean }
export interface KnowledgeDocument {
  id: string; knowledgeBaseId: string; title: string; mimeType: string | null; fileSize: number
  processingStatus: 'PROCESSING' | 'READY' | 'FAILED'; errorMessage: string | null
  chunkCount: number; embeddingModel: string | null; createdAt: string
}
export interface KnowledgeHit {
  knowledgeDocumentId: string; chunkId: string; documentTitle: string; sectionPath: string; content: string; score: number
}
export const fetchKnowledgeBases = (prd: string) => get<KnowledgeBase[]>(`/api/prds/${prd}/knowledge-bases`)
export const createKnowledgeBase = (prd: string, name: string) => post<KnowledgeBase>(`/api/prds/${prd}/knowledge-bases`, { name })
export const bindKnowledgeBase = (prd: string, base: string, enabled: boolean) => put<void>(`/api/prds/${prd}/knowledge-bases/${base}`, { enabled })
export const fetchKnowledgeDocuments = (base: string) => get<KnowledgeDocument[]>(`/api/knowledge-bases/${base}/documents`)
export const uploadKnowledgeFile = (base: string, file: File) => {
  const body = new FormData(); body.append('file', file)
  return post<{ id: string }>(`/api/knowledge-bases/${base}/documents`, body)
}
export const retryKnowledgeDocument = (id: string) => post<void>(`/api/knowledge-documents/${id}/reindex`)
export const archiveKnowledgeDocument = (id: string) => request<void>('DELETE', `/api/knowledge-documents/${id}`)
export const fetchKnowledgeContent = (id: string) => get<string | null>(`/api/knowledge-documents/${id}/content`)
export const searchKnowledge = (prd: string, query: string) => post<KnowledgeHit[]>(`/api/prds/${prd}/knowledge-search`, { query })
export const importPrdFile = (prd: string, revision: number, file: File) => {
  const body = new FormData(); body.append('file', file)
  return post<SaveDraftResponse>(`/api/prds/${prd}/import-file?revision=${revision}`, body)
}
export const createPrdFromFile = (file: File) => {
  const body = new FormData(); body.append('file', file)
  return post<CreatePrdResponse>('/api/prds/import-file', body)
}
