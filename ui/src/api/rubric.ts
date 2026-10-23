import { get, post } from './client'
export interface ScoreBand { min: number | null; max: number | null; description: string }
export interface RubricDimension {
  dimensionKey: string; name: string; description: string; checklist: string[]
  scoreAnchor: ScoreBand[]; severityAnchor: Record<string, string>; applicabilityRule: string | null
}
export interface RubricSummary {
  id: string; rubricKey: string; name: string; version: number; description: string
  dimensionCount?: number; builtin: boolean; editable: boolean; createdAt: string
}
export interface RubricDetail extends RubricSummary { dimensions: RubricDimension[] }
export interface RubricInput { name: string; description: string; baseRubricId?: string; dimensions: RubricDimension[] }
export const fetchRubrics = () => get<RubricSummary[]>('/api/review-rubrics')
export const fetchRubric = (id: string) => get<RubricDetail>(`/api/review-rubrics/${id}`)
export const fetchRubricVersions = (id: string) => get<RubricSummary[]>(`/api/review-rubrics/${id}/versions`)
export const fetchRubricWithVersions = (id: string) => Promise.all([fetchRubric(id), fetchRubricVersions(id)])
export const publishRubric = (input: RubricInput) => post<RubricDetail>('/api/review-rubrics', input)
export const publishRubricVersion = (key: string, input: RubricInput) => post<RubricDetail>(`/api/review-rubrics/${key}/versions`, input)
