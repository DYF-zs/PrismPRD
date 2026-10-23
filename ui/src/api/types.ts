export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface MeResponse {
  userId: string
  username: string
  displayName: string
  email: string
  authSource: string
}

export interface TemplateResponse {
  key: string
  name: string
  description: string
}

export type CreationMode = 'BLANK' | 'TEMPLATE' | 'AI_DRAFT'

export interface CreatePrdRequest {
  creationMode: CreationMode
  title: string
  templateKey?: string
  initialRequirement?: string
}

export interface CreatePrdResponse {
  prdId: string
  defaultSessionId: string
  revision: number
}

export type PrdStatus = 'DRAFT' | 'REVIEWING' | 'NEED_CHANGES' | 'APPROVED' | 'ARCHIVED'

export interface PrdDocumentSummary {
  id: string
  title: string
  status: PrdStatus
  revision: number
  currentVersionId: string | null
  currentVersionNo: number | null
  currentScore: number | null
  reviewCount: number
  ownerId: string
  ownerName: string
  updatedAt: string
}

export interface PrdDetailResponse {
  id: string
  title: string
  status: PrdStatus
  ownerId: string
  ownerName: string
  draftJson: string
  draftMarkdown: string
  revision: number
  currentVersionId: string | null
  currentVersionNo: number | null
  currentScore: number | null
  createdAt: string
  updatedAt: string
}

export interface SaveDraftResponse {
  revision: number
  updatedAt: string
}

export interface VersionSummary {
  id: string
  prdId: string
  versionNo: number
  source: string
  changeSummary: string
  createdBy: string
  createdAt: string
}

export interface VersionDetail {
  id: string
  prdId: string
  versionNo: number
  editorJson: string
  markdownSnapshot: string
  source: string
  changeSummary: string
  createdBy: string
  createdAt: string
  draftRevision: number | null
  patches: PatchDetail[]
}

export type ReviewJobStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export type Readiness = 'READY' | 'CONDITIONALLY_READY' | 'NEEDS_IMPROVEMENT' | 'BLOCKED'

export type FindingSeverity = 'CRITICAL' | 'MAJOR' | 'MINOR' | 'SUGGESTION'

export type FindingStatus = 'OPEN' | 'RESOLVED' | 'IGNORED' | 'WONT_FIX'

export interface ReviewJobSummary {
  id: string
  prdId: string
  prdVersionId: string
  versionNo: number | null
  rubricId: string | null
  rubricName?: string | null
  rubricVersion?: number | null
  status: ReviewJobStatus
  modelProfile: string | null
  errorMessage: string | null
  overallScore: number | null
  readiness: Readiness | null
  summary: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}

export interface ReviewPriority {
  level: string
  title: string
  reason: string
}

export interface ReviewEvidence {
  sourceType: string
  blockId?: string | null
  quote?: string | null
  knowledgeDocumentId?: string | null
  chunkId?: string | null
  documentTitle?: string | null
  sectionPath?: string | null
}

export interface ScoreDetail {
  dimensionKey: string
  dimensionName: string
  applicable: boolean
  score: number | null
  rationale: string | null
  evidence: ReviewEvidence[] | null
  suggestions: string[] | null
  displayOrder: number
}

export interface FindingDetail {
  id: string
  title: string
  dimensions: string[]
  severity: FindingSeverity
  blockId: string | null
  quote: string | null
  evidence: ReviewEvidence[] | null
  reason: string | null
  suggestion: string | null
  status: FindingStatus
  createdAt: string
  updatedAt: string
}

export interface ReportDetail {
  id: string
  overallScore: number | null
  readiness: Readiness | null
  summary: string | null
  strengths: string[] | null
  majorRisks: string[] | null
  priorities: ReviewPriority[] | null
  modelName: string | null
  promptVersion: string | null
  rubricVersion: number | null
  rubricId?: string | null
  rubricName?: string | null
  createdAt: string
  scores: ScoreDetail[] | null
  findings: FindingDetail[] | null
}

export interface ReviewDetail {
  job: ReviewJobSummary
  report: ReportDetail | null
}

export interface ChatSessionSummary {
  id: string
  prdId: string
  title: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface ChatMessageDetail {
  id: string
  sessionId: string
  role: 'USER' | 'ASSISTANT' | 'TOOL'
  content: string
  toolName: string | null
  toolPayload: string | null
  createdAt: string
}

export type PatchScope = 'INLINE' | 'BLOCK'

export type PatchOperation = 'REPLACE' | 'INSERT_BEFORE' | 'INSERT_AFTER' | 'DELETE'

export type PatchStatus = 'PENDING' | 'APPLIED' | 'REJECTED' | 'STALE'

export interface PatchDetail {
  id: string
  prdId: string
  sessionId: string | null
  source: string
  sourceFindingId: string | null
  sourceVersionId: string | null
  baseDraftRevision: number
  scope: PatchScope
  operation: PatchOperation
  targetBlockId: string | null
  sourceQuote: string | null
  newContentMarkdown: string | null
  reason: string | null
  status: PatchStatus
  createdAt: string | null
  appliedAt: string | null
  findingTitle: string | null
  findingSeverity: FindingSeverity | null
}
