import type { ReviewJobSummary, ReviewJobStatus } from '../../api/types'

export const REVIEW_STATUS: Record<ReviewJobStatus, { label: string; color: string }> = {
  PENDING: { label: '排队中', color: 'default' },
  RUNNING: { label: '评审中', color: 'processing' },
  SUCCESS: { label: '已完成', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
}

export function reviewsForVersion(reviews: ReviewJobSummary[], versionId: string): ReviewJobSummary[] {
  const time = (value: string) => {
    const timestamp = Date.parse(value)
    return Number.isFinite(timestamp) ? timestamp : 0
  }
  return reviews.filter(review => review.prdVersionId === versionId)
    .sort((a, b) => time(b.createdAt) - time(a.createdAt) || b.id.localeCompare(a.id))
}

export function reviewActionForVersion(reviews: ReviewJobSummary[], versionId: string | null) {
  const history = versionId ? reviewsForVersion(reviews, versionId) : []
  const active = reviews.find(review => review.status === 'PENDING' || review.status === 'RUNNING')
  const reviewing = !!active && active.prdVersionId === versionId
  return {
    label: reviewing ? '评审中' : history.length ? '再次评审' : '开始评审',
    disabled: !versionId || !!active,
    reviewing,
    initialRubricId: history[0]?.rubricId ?? undefined,
    hint: !versionId ? '请先创建并选择版本' : active && !reviewing ? '该 PRD 已有其他版本正在评审，请等待完成' : undefined,
  }
}
