import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Alert, Button, Collapse, Empty, Space, Spin, Tag } from 'antd'
import { DeleteOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { Readiness, ReviewDetail, ReviewJobSummary } from '../../api/types'
import { fetchReviewDetail } from '../../api/review'
import { REVIEW_STATUS } from './versionReviewHistory'

const READINESS_LABEL: Record<Readiness, string> = {
  READY: '就绪', CONDITIONALLY_READY: '有条件就绪', NEEDS_IMPROVEMENT: '待改进', BLOCKED: '阻塞',
}

interface Props {
  versionNo: number | null
  reviews: ReviewJobSummary[]
  onOpenReview: (reviewId: string) => void
  refreshKey: number
  action?: ReactNode
  onDeleteReview?: (review: ReviewJobSummary) => void
  deleteDisabled?: boolean
  deletingReviewId?: string | null
}

export default function ReviewPanel({ versionNo, reviews, onOpenReview, refreshKey, action, onDeleteReview, deleteDisabled, deletingReviewId }: Props) {
  const latest = reviews[0]
  const [detail, setDetail] = useState<ReviewDetail | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    let alive = true
    setDetail(null)
    setError('')
    setLoading(false)
    if (!latest || latest.status !== 'SUCCESS') return
    setLoading(true)
    fetchReviewDetail(latest.id).then(result => { if (alive) setDetail(result) })
      .catch(e => { if (alive) setError((e as Error).message) })
      .finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [latest?.id, latest?.status, refreshKey, retry])
  const report = detail?.job.id === latest?.id ? detail?.report : null
  const entry = (review: ReviewJobSummary, isLatest: boolean) => (
    <div className="version-review-entry" key={review.id}>
      <div>
        {isLatest && <Tag color="blue">最近一次</Tag>}
        <Tag color={REVIEW_STATUS[review.status].color}>{REVIEW_STATUS[review.status].label}</Tag>
        {review.overallScore != null && <strong>{Number(review.overallScore).toFixed(1)} 分</strong>}
      </div>
      <div>{review.rubricName ?? '评审标准'} · 标准 v{review.rubricVersion ?? '-'}</div>
      {review.readiness && <div>{READINESS_LABEL[review.readiness]}</div>}
      <time>{dayjs(review.createdAt).format('YYYY-MM-DD HH:mm')}</time>
      {review.status === 'FAILED' && review.errorMessage && <div className="review-error">{review.errorMessage}</div>}
      <Space wrap>
      <Button size="small" disabled={!!deletingReviewId} onClick={() => onOpenReview(review.id)}>
        {review.status === 'PENDING' || review.status === 'RUNNING' ? '查看进度' : '打开完整报告'}
      </Button>
      {onDeleteReview && <Button size="small" type="text" danger icon={<DeleteOutlined />}
        aria-label={`删除评审 ${dayjs(review.createdAt).format('YYYY-MM-DD HH:mm')}`}
        disabled={deleteDisabled || !!deletingReviewId || review.status === 'PENDING' || review.status === 'RUNNING'}
        loading={deletingReviewId === review.id} onClick={() => onDeleteReview(review)}>删除</Button>}
      </Space>
      {isLatest && <div className="review-inline-report" aria-label="最近评审内容">
        {(report?.summary || review.summary) && <section><h4>评审摘要</h4><p>{report?.summary || review.summary}</p></section>}
        {loading && <Spin size="small" />}
        {error && <Alert type="warning" message="评审详情加载失败" description={error}
          action={<Button size="small" onClick={() => setRetry(value => value + 1)}>重试</Button>} />}
        {!!report?.majorRisks?.length && <section><h4>主要风险</h4><ul>{report.majorRisks.map((risk, index) => <li key={index}>{risk}</li>)}</ul></section>}
        {!!report?.priorities?.length && <section><h4>改进建议</h4>{report.priorities.map((priority, index) => <div className="review-inline-priority" key={index}>
          <Tag color={priority.level === 'P0' ? 'red' : priority.level === 'P1' ? 'orange' : 'blue'}>{priority.level}</Tag>
          <div><strong>{priority.title}</strong>{priority.reason && <p>{priority.reason}</p>}</div>
        </div>)}</section>}
        {!!report?.strengths?.length && <section><h4>亮点</h4><ul>{report.strengths.map((strength, index) => <li key={index}>{strength}</li>)}</ul></section>}
      </div>}
    </div>
  )

  return (
    <section className="review-panel" aria-label="所选版本的评审记录">
      <div className="panel-section-header">
        <div className="review-panel-heading">
          <span>{versionNo == null ? '评审记录' : `v${versionNo} 的评审记录`}</span>
          <span className="version-review-count">{reviews.length} 次</span>
        </div>
        {action}
      </div>
      {reviews.length ? <>
        {entry(reviews[0], true)}
        {reviews.length > 1 && <Collapse ghost size="small" items={[{
          key: 'earlier', label: `较早的评审（${reviews.length - 1}）`,
          children: reviews.slice(1).map(review => entry(review, false)),
        }]} />}
      </> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={versionNo == null ? '选择版本查看评审' : '该版本尚未评审'} />}
    </section>
  )
}
