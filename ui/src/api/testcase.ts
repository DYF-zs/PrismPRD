import { get, post } from './client'
import type { PatchDetail } from './types'

export interface TestCase {
  id: string
  scenario: string
  preconditions: string
  steps: string[]
  inputData: string | null
  expectedResult: string
  caseType: string
  sourceBlockId: string
  createdAt: string | null
}
export interface TestabilityIssue {
  sourceBlockId: string
  sourceQuote: string | null
  issue: string
  suggestedAcceptanceCriteria: string | null
}
export interface TestReport {
  generationId: string | null
  profile: string | null
  summary: string | null
  cases: TestCase[]
  testabilityIssues: TestabilityIssue[]
  appliedIssueIndexes: number[]
}
export const fetchTestReport = (versionId: string) => get<TestReport>(`/api/prd-versions/${versionId}/test-report`)
export const generateTestCases = (versionId: string, instruction: string) =>
  post<TestReport>(`/api/prd-versions/${versionId}/test-cases`, { instruction })
export const proposeAcceptance = (versionId: string, generationId: string, issueIndex: number, baseDraftRevision: number) =>
  post<PatchDetail>(`/api/prd-versions/${versionId}/acceptance-patches`, { generationId, issueIndex, baseDraftRevision })
