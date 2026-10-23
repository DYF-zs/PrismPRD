import { get, patch, post, request } from './client'
import type { FindingDetail, FindingStatus, ReviewDetail, ReviewJobSummary } from './types'

export const fetchReviewHistory = (prdId: string) =>
  get<ReviewJobSummary[]>(`/api/prds/${prdId}/reviews`)

export const createReview = (versionId: string, rubricId?: string) =>
  post<ReviewJobSummary>(`/api/prd-versions/${versionId}/reviews`, rubricId ? { rubricId } : {})

export const fetchReviewDetail = (reviewId: string) =>
  get<ReviewDetail>(`/api/reviews/${reviewId}`)

export const deleteReview = (reviewId: string) => request<void>('DELETE', `/api/reviews/${encodeURIComponent(reviewId)}`)

export const updateFindingStatus = (findingId: string, status: FindingStatus) =>
  patch<FindingDetail>(`/api/findings/${findingId}`, { status })

export interface ReviewSseHandlers {
  onStage?: (stage: string, message: string) => void
  onComplete?: (status: string) => void
  onError?: (message: string) => void
}

export function subscribeReviewEvents(reviewId: string, handlers: ReviewSseHandlers): () => void {
  const source = new EventSource(`/api/reviews/${reviewId}/events`)
  const close = () => source.close()
  source.addEventListener('stage', (e) => {
    try {
      const data = JSON.parse((e as MessageEvent).data) as { stage: string; message: string }
      handlers.onStage?.(data.stage, data.message)
    } catch {
      /* 忽略无法解析的事件 */
    }
  })
  source.addEventListener('complete', (e) => {
    try {
      const data = JSON.parse((e as MessageEvent).data) as { status: string }
      handlers.onComplete?.(data.status)
    } catch {
      handlers.onError?.('评审通知格式异常，请重新加载结果')
    } finally {
      close()
    }
  })
  source.addEventListener('error', (e) => {
    if (e instanceof MessageEvent) {
      try {
        const data = JSON.parse(e.data) as { message: string }
        handlers.onError?.(data.message)
      } catch {
        /* 忽略 */
      }
      close()
    }
    // Transport errors reconnect automatically; closing here would strand a running job.
  })
  return close
}
