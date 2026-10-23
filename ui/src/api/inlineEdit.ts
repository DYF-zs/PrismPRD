import { post } from './client'
import type { JSONContent } from '@tiptap/react'

export interface InlineEditRequest {
  instruction: string
  targetBlockId: string | null
  selectionMarkdown: string
  blockMarkdown: string
  contextMarkdown: string
  attempt: number
  baseDraftRevision: number
  selectionText: string
}

export interface InlineEditProposalResponse {
  patchId: string
  baseDraftRevision: number
  scope: 'INLINE' | 'BLOCK'
  operation: 'REPLACE' | 'INSERT_BEFORE' | 'INSERT_AFTER' | 'DELETE'
  targetBlockId: string | null
  sourceQuote: string | null
  newContentMarkdown: string | null
  newContentJson: JSONContent[]
  reason: string | null
  profile: string
}

export const generateInlineEdit = (prdId: string, request: InlineEditRequest) =>
  post<InlineEditProposalResponse>(`/api/prds/${prdId}/inline-edit`, request)
