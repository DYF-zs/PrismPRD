import { useCallback, useEffect, useRef, useState } from 'react'
import RubricSnapshotModal from './RubricSnapshotModal'
import { RequestEpoch } from '../../api/requestEpoch'
import {
  Alert,
  Button,
  Drawer,
  Empty,
  Modal,
  Popconfirm,
  Space,
  Spin,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  AimOutlined,
  CheckCircleOutlined,
  CheckOutlined,
  CloseOutlined,
  RedoOutlined,
  StopOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import {
  fetchReviewDetail,
  subscribeReviewEvents,
  updateFindingStatus,
} from '../../api/review'
import { createFindingFix, rejectPatch } from '../../api/patch'
import type {
  FindingDetail,
  FindingSeverity,
  FindingStatus,
  PatchDetail,
  PatchOperation,
  PatchScope,
  Readiness,
  ReviewDetail,
} from '../../api/types'
import RadarChart, { scoreColor } from './RadarChart'
import KnowledgeEvidenceList from './KnowledgeEvidenceList'
import { FINDING_SEVERITY_META as SEVERITY_META, findingSeverityMeta } from './findingSeverity'

const READINESS_META: Record<Readiness, { label: string; color: string }> = {
  READY: { label: '就绪', color: 'success' },
  CONDITIONALLY_READY: { label: '有条件就绪', color: 'cyan' },
  NEEDS_IMPROVEMENT: { label: '待改进', color: 'warning' },
  BLOCKED: { label: '阻塞', color: 'error' },
}

const FINDING_STATUS_META: Record<FindingStatus, { label: string; color: string }> = {
  OPEN: { label: '待处理', color: 'error' },
  RESOLVED: { label: '已解决', color: 'success' },
  IGNORED: { label: '已忽略', color: 'default' },
  WONT_FIX: { label: '不修复', color: 'default' },
}

const PRIORITY_COLOR: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'blue' }

const PATCH_OPERATION_LABEL: Record<PatchOperation, string> = {
  REPLACE: '替换',
  INSERT_BEFORE: '前插入',
  INSERT_AFTER: '后插入',
  DELETE: '删除',
}

const PATCH_SCOPE_LABEL: Record<PatchScope, string> = {
  INLINE: '内联',
  BLOCK: '整块',
}

const PATCH_STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待确认', color: 'processing' },
  APPLIED: { label: '已应用', color: 'success' },
  REJECTED: { label: '已拒绝', color: 'default' },
  STALE: { label: '已过期', color: 'warning' },
}

interface Props {
  reviewId: string | null
  open: boolean
  onClose: () => void
  onLocate: (blockId: string, quote: string | null) => void
  onFinished?: () => void
  /** 编辑器本地是否存在未保存修改（应用补丁前提示） */
  localDirty?: boolean
  /** 补丁应用成功后刷新编辑器内容 */
  onApplyPatch: (patchId: string) => Promise<PatchDetail>
}

export default function ReviewReportDrawer({
  reviewId,
  open,
  onClose,
  onLocate,
  onFinished,
  localDirty = false,
  onApplyPatch,
}: Props) {
  const [detail, setDetail] = useState<ReviewDetail | null>(null)
  const [rubricSnapshotId, setRubricSnapshotId] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [stageMessage, setStageMessage] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState('overview')
  const [severityFilter, setSeverityFilter] = useState<FindingSeverity | 'ALL'>('ALL')
  const [statusFilter, setStatusFilter] = useState<FindingStatus | 'ALL' | 'OPEN'>('OPEN')
  const [updatingIds, setUpdatingIds] = useState<Set<string>>(new Set())

  const [fixingIds, setFixingIds] = useState<Set<string>>(new Set())
  const [patchByFinding, setPatchByFinding] = useState<Record<string, PatchDetail>>({})
  const [patchBusyIds, setPatchBusyIds] = useState<Set<string>>(new Set())
  const requests = useRef(new RequestEpoch())

  const load = useCallback(async () => {
    if (!reviewId) return
    const token = requests.current.next()
    setLoading(true)
    try {
      const result = await fetchReviewDetail(reviewId)
      if (requests.current.isCurrent(token)) setDetail(result)
    } catch (e) {
      if (requests.current.isCurrent(token)) message.error((e as Error).message)
    } finally {
      if (requests.current.isCurrent(token)) setLoading(false)
    }
  }, [reviewId])

  useEffect(() => {
    if (!open || !reviewId) return
    setDetail(null)
    setStageMessage(null)
    setActiveTab('overview')
    setSeverityFilter('ALL')
    setStatusFilter('OPEN')
    setPatchByFinding({})
    void load()
    return () => requests.current.invalidate()
  }, [open, reviewId, load])

  useEffect(() => {
    if (!open || !reviewId) return
    const job = detail?.job
    if (job && job.status !== 'PENDING' && job.status !== 'RUNNING') return
    const close = subscribeReviewEvents(reviewId, {
      onStage: (_stage, msg) => setStageMessage(msg),
      onComplete: () => {
        setStageMessage(null)
        void load()
        onFinished?.()
      },
      onError: () => {
        setStageMessage(null)
        void load()
        onFinished?.()
      },
    })
    return close
  }, [open, reviewId, detail?.job?.status, load, onFinished])

  useEffect(() => {
    if (!open || !['PENDING', 'RUNNING'].includes(detail?.job.status ?? '')) return
    const timer = setInterval(() => void load(), 3000)
    return () => clearInterval(timer)
  }, [open, detail?.job.status, load])

  const changeFindingStatus = async (finding: FindingDetail, status: FindingStatus) => {
    setUpdatingIds((prev) => new Set(prev).add(finding.id))
    try {
      const updated = await updateFindingStatus(finding.id, status)
      setDetail((prev) =>
        prev && prev.report
          ? {
              ...prev,
              report: {
                ...prev.report,
                findings:
                  prev.report.findings?.map((f) => (f.id === updated.id ? updated : f)) ?? null,
              },
            }
          : prev,
      )
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setUpdatingIds((prev) => {
        const next = new Set(prev)
        next.delete(finding.id)
        return next
      })
    }
  }

  const setPatch = (patch: PatchDetail) => {
    setPatchByFinding((prev) => ({ ...prev, [patch.sourceFindingId ?? '']: patch }))
  }

  const startFix = async (finding: FindingDetail) => {
    setFixingIds((prev) => new Set(prev).add(finding.id))
    try {
      setPatch(await createFindingFix(finding.id))
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setFixingIds((prev) => {
        const next = new Set(prev)
        next.delete(finding.id)
        return next
      })
    }
  }

  const doApplyPatch = async (patch: PatchDetail) => {
    setPatchBusyIds((prev) => new Set(prev).add(patch.id))
    try {
      const applied = await onApplyPatch(patch.id)
      setPatch(applied)
      message.success('AI 修复已应用，该问题标记为已解决')
      void load()
    } catch (e) {
      if ((e as { isConflict?: boolean }).isConflict) {
        message.warning('草稿已更新，补丁已过期，请重新生成修复建议')
        setPatch({ ...patch, status: 'STALE' })
      } else {
        message.error((e as Error).message)
      }
    } finally {
      setPatchBusyIds((prev) => {
        const next = new Set(prev)
        next.delete(patch.id)
        return next
      })
    }
  }

  const confirmApplyPatch = (patch: PatchDetail) => {
    if (localDirty) {
      Modal.confirm({
        title: '当前有未保存的本地修改',
        content: '将先保存本地修改，再校验并应用补丁。若补丁已过期，请基于最新内容重新生成。',
        okText: '保存并校验',
        cancelText: '取消',
        onOk: () => doApplyPatch(patch),
      })
      return
    }
    void doApplyPatch(patch)
  }

  const doRejectPatch = async (patch: PatchDetail) => {
    setPatchBusyIds((prev) => new Set(prev).add(patch.id))
    try {
      await rejectPatch(patch.id)
      setPatchByFinding((prev) => {
        const next = { ...prev }
        delete next[patch.sourceFindingId ?? '']
        return next
      })
      message.success('已拒绝该修复建议')
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setPatchBusyIds((prev) => {
        const next = new Set(prev)
        next.delete(patch.id)
        return next
      })
    }
  }

  const job = detail?.job
  const report = detail?.report ?? null
  const running = job?.status === 'PENDING' || job?.status === 'RUNNING'

  const findings = (report?.findings ?? []).filter(
    (f) =>
      (severityFilter === 'ALL' || f.severity === severityFilter) &&
      (statusFilter === 'ALL' || f.status === statusFilter),
  )

  const openFindingCount = report?.findings?.filter((f) => f.status === 'OPEN').length ?? 0

  return (
    <><Drawer
      title={job ? `评审报告 · v${job.versionNo ?? '-'}` : '评审报告'}
      open={open}
      onClose={onClose}
      width={760}
      destroyOnClose
    >
      {job?.rubricId && <Space style={{ marginBottom: 16 }} wrap>
        <Typography.Text>{job.rubricName ?? '评审标准'} · 标准 v{job.rubricVersion ?? '-'}</Typography.Text>
        <Button size="small" onClick={() => setRubricSnapshotId(job.rubricId)}>查看本次标准</Button>
      </Space>}
      {loading && !detail && (
        <div style={{ padding: 60, textAlign: 'center' }}>
          <Spin size="large" />
        </div>
      )}

      {running && (
        <div className="review-progress">
          <Spin />
          <div className="review-progress-text">
            <Typography.Text strong>评审进行中</Typography.Text>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              {stageMessage ?? '已提交，等待执行…'}
            </Typography.Paragraph>
          </div>
        </div>
      )}

      {job?.status === 'FAILED' && (
        <Alert
          type="error"
          showIcon
          message="评审执行失败"
          description={job.errorMessage ?? '未知错误，请重试'}
          style={{ marginBottom: 16 }}
        />
      )}

      {job && job.status === 'SUCCESS' && report && (
        <div className="review-report">
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={[
              {
                key: 'overview',
                label: '概览',
                children: <OverviewTab report={report} onLocate={onLocate} />,
              },
              {
                key: 'dimensions',
                label: '维度评分',
                children: <DimensionsTab report={report} />,
              },
              {
                key: 'findings',
                label: (
                  <span>
                    问题清单
                    {openFindingCount > 0 && (
                      <Tag color="error" style={{ marginLeft: 6, marginRight: 0 }}>
                        {openFindingCount}
                      </Tag>
                    )}
                  </span>
                ),
                children: (
                  <FindingsTab
                    report={report}
                    findings={findings}
                    severityFilter={severityFilter}
                    statusFilter={statusFilter}
                    onSeverityFilter={setSeverityFilter}
                    onStatusFilter={setStatusFilter}
                    updatingIds={updatingIds}
                    onChangeStatus={changeFindingStatus}
                    onLocate={onLocate}
                    fixingIds={fixingIds}
                    patchByFinding={patchByFinding}
                    patchBusyIds={patchBusyIds}
                    onFix={startFix}
                    onApplyPatch={confirmApplyPatch}
                    onRejectPatch={doRejectPatch}
                  />
                ),
              },
            ]}
          />
        </div>
      )}
    </Drawer><RubricSnapshotModal id={open ? rubricSnapshotId : null} onClose={() => setRubricSnapshotId(null)} /></>
  )
}

function OverviewTab({
  report,
  onLocate,
}: {
  report: NonNullable<ReviewDetail['report']>
  onLocate: (blockId: string, quote: string | null) => void
}) {
  const readiness = report.readiness ? READINESS_META[report.readiness] : null
  return (
    <div>
      <div className="review-score-row">
        <div className="review-overall">
          <div className="review-overall-number" style={{ color: scoreColor(report.overallScore) }}>
            {report.overallScore != null ? Number(report.overallScore).toFixed(1) : '-'}
          </div>
          <div className="review-overall-caption">综合得分</div>
        </div>
        <div className="review-overall-meta">
          {readiness && <Tag color={readiness.color}>{readiness.label}</Tag>}
          <span className="review-meta-line">
            {report.modelName ?? '-'} · {report.rubricName ?? '评审标准'} v{report.rubricVersion ?? '-'} ·{' '}
            {dayjs(report.createdAt).format('YYYY-MM-DD HH:mm')}
          </span>
        </div>
      </div>

      {report.summary && <Typography.Paragraph className="review-summary">{report.summary}</Typography.Paragraph>}

      <RadarChart scores={report.scores ?? []} />

      {(report.strengths?.length || report.majorRisks?.length) && (
        <div className="review-two-col">
          <div className="review-col">
            <Typography.Text strong>
              <Tag color="success" style={{ marginRight: 6 }}>
                亮点
              </Tag>
            </Typography.Text>
            <ul className="review-bullet-list">
              {report.strengths?.map((s, i) => <li key={i}>{s}</li>)}
            </ul>
          </div>
          <div className="review-col">
            <Typography.Text strong>
              <Tag color="warning" style={{ marginRight: 6 }}>
                主要风险
              </Tag>
            </Typography.Text>
            <ul className="review-bullet-list">
              {report.majorRisks?.map((s, i) => <li key={i}>{s}</li>)}
            </ul>
          </div>
        </div>
      )}

      {report.priorities && report.priorities.length > 0 && (
        <div className="review-priorities">
          <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
            改进优先级
          </Typography.Text>
          {report.priorities.map((p, i) => (
            <div key={i} className="review-priority-item">
              <Tag color={PRIORITY_COLOR[p.level] ?? 'default'}>{p.level}</Tag>
              <div>
                <div>{p.title}</div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {p.reason}
                </Typography.Text>
              </div>
            </div>
          ))}
        </div>
      )}

      {report.findings && report.findings.length > 0 && (
        <div className="review-top-findings">
          <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
            待处理问题（Top 3）
          </Typography.Text>
          {report.findings
            .filter((f) => f.status === 'OPEN')
            .slice(0, 3)
            .map((f) => {
              const blockId = f.blockId
              return (
              <div key={f.id} className="review-top-finding-item">
                <Tag color={findingSeverityMeta(f.severity).color}>{findingSeverityMeta(f.severity).label}</Tag>
                <span style={{ flex: 1 }}>{f.title}</span>
                {blockId && (
                  <Button size="small" type="link" icon={<AimOutlined />} onClick={() => onLocate(blockId, f.quote)}>
                    定位
                  </Button>
                )}
              </div>
              )
            })}
        </div>
      )}
    </div>
  )
}

function DimensionsTab({ report }: { report: NonNullable<ReviewDetail['report']> }) {
  const scores = report.scores ?? []
  if (scores.length === 0) {
    return <Empty description="暂无维度评分" image={Empty.PRESENTED_IMAGE_SIMPLE} />
  }
  return (
    <div className="review-dimension-list">
      {scores.map((s) => (
        <div key={s.dimensionKey} className="review-dimension-item">
          <div className="review-dimension-head">
            <span className="review-dimension-name">{s.dimensionName}</span>
            {s.applicable ? (
              <span className="review-dimension-score" style={{ color: scoreColor(s.score) }}>
                {s.score != null ? Number(s.score).toFixed(1) : '-'}
              </span>
            ) : (
              <Tag>不适用</Tag>
            )}
          </div>
          {s.applicable && s.rationale && (
            <Typography.Paragraph type="secondary" style={{ marginBottom: 6, fontSize: 13 }}>
              {s.rationale}
            </Typography.Paragraph>
          )}
          {!s.applicable && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              本 PRD 不涉及该维度
            </Typography.Text>
          )}
          {s.suggestions && s.suggestions.length > 0 && (
            <ul className="review-bullet-list review-suggestion-list">
              {s.suggestions.map((sg, i) => <li key={i}>{sg}</li>)}
            </ul>
          )}
          <KnowledgeEvidenceList evidence={s.evidence} />
        </div>
      ))}
    </div>
  )
}

interface FindingsTabProps {
  report: NonNullable<ReviewDetail['report']>
  findings: FindingDetail[]
  severityFilter: FindingSeverity | 'ALL'
  statusFilter: FindingStatus | 'ALL' | 'OPEN'
  onSeverityFilter: (v: FindingSeverity | 'ALL') => void
  onStatusFilter: (v: FindingStatus | 'ALL' | 'OPEN') => void
  updatingIds: Set<string>
  onChangeStatus: (finding: FindingDetail, status: FindingStatus) => void
  onLocate: (blockId: string, quote: string | null) => void
  fixingIds: Set<string>
  patchByFinding: Record<string, PatchDetail>
  patchBusyIds: Set<string>
  onFix: (finding: FindingDetail) => void
  onApplyPatch: (patch: PatchDetail) => void
  onRejectPatch: (patch: PatchDetail) => void
}

function FindingsTab({
  findings,
  severityFilter,
  statusFilter,
  onSeverityFilter,
  onStatusFilter,
  updatingIds,
  onChangeStatus,
  onLocate,
  fixingIds,
  patchByFinding,
  patchBusyIds,
  onFix,
  onApplyPatch,
  onRejectPatch,
}: FindingsTabProps) {
  const severityOptions: { label: string; value: FindingSeverity | 'ALL' }[] = [
    { label: '全部严重级', value: 'ALL' },
    { label: SEVERITY_META.CRITICAL.label, value: 'CRITICAL' },
    { label: SEVERITY_META.MAJOR.label, value: 'MAJOR' },
    { label: SEVERITY_META.MINOR.label, value: 'MINOR' },
    { label: SEVERITY_META.SUGGESTION.label, value: 'SUGGESTION' },
  ]
  const statusOptions: { label: string; value: FindingStatus | 'ALL' | 'OPEN' }[] = [
    { label: '待处理', value: 'OPEN' },
    { label: '已解决', value: 'RESOLVED' },
    { label: '已忽略', value: 'IGNORED' },
    { label: '不修复', value: 'WONT_FIX' },
    { label: '全部状态', value: 'ALL' },
  ]

  return (
    <div>
      <div className="review-finding-filters">
        <Space wrap>
          {severityOptions.map((o) => (
            <Tag.CheckableTag
              key={`sev-${o.value}`}
              checked={severityFilter === o.value}
              onChange={() => onSeverityFilter(o.value)}
            >
              {o.label}
            </Tag.CheckableTag>
          ))}
          <span className="review-filter-divider" />
          {statusOptions.map((o) => (
            <Tag.CheckableTag
              key={`st-${o.value}`}
              checked={statusFilter === o.value}
              onChange={() => onStatusFilter(o.value)}
            >
              {o.label}
            </Tag.CheckableTag>
          ))}
        </Space>
      </div>

      {findings.length === 0 ? (
        <Empty description="没有符合条件的问题" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        findings.map((f) => {
          const severity = findingSeverityMeta(f.severity)
          const status = FINDING_STATUS_META[f.status] ?? { label: f.status, color: 'default' }
          const updating = updatingIds.has(f.id)
          return (
            <div key={f.id} className="review-finding-card">
              <div className="review-finding-head">
                <span className="review-finding-title">{f.title}</span>
                <Tag color={severity.color}>{severity.label}</Tag>
                <Tag color={status.color}>{status.label}</Tag>
              </div>
              {f.dimensions.length > 0 && (
                <div className="review-finding-dims">
                  {f.dimensions.map((d) => (
                    <Tag key={d} color="geekblue" style={{ fontSize: 11 }}>
                      {d}
                    </Tag>
                  ))}
                </div>
              )}
              {f.quote && (
                <div className="review-finding-quote">
                  <Button
                    size="small"
                    type="text"
                    icon={<AimOutlined />}
                    disabled={!f.blockId}
                    onClick={() => f.blockId && onLocate(f.blockId, f.quote)}
                  >
                    定位原文
                  </Button>
                  <blockquote>{f.quote}</blockquote>
                </div>
              )}
              {f.reason && (
                <Typography.Paragraph style={{ marginBottom: 6, fontSize: 13 }}>
                  <Typography.Text type="secondary">原因：</Typography.Text>
                  {f.reason}
                </Typography.Paragraph>
              )}
              {f.suggestion && (
                <Typography.Paragraph style={{ marginBottom: 6, fontSize: 13 }}>
                  <Typography.Text type="secondary">建议：</Typography.Text>
                  {f.suggestion}
                </Typography.Paragraph>
              )}
              <KnowledgeEvidenceList evidence={f.evidence} />
              {patchByFinding[f.id] && (
                <PatchPreviewCard
                  finding={f}
                  patch={patchByFinding[f.id]}
                  busy={patchBusyIds.has(patchByFinding[f.id].id)}
                  onApply={() => onApplyPatch(patchByFinding[f.id])}
                  onReject={() => onRejectPatch(patchByFinding[f.id])}
                  onRegenerate={() => onFix(f)}
                />
              )}
              <div className="review-finding-actions">
                {f.status === 'OPEN' ? (
                  <>
                    <Button
                      size="small"
                      type="primary"
                      ghost
                      icon={<ToolOutlined />}
                      loading={fixingIds.has(f.id)}
                      onClick={() => onFix(f)}
                    >
                      AI 修复
                    </Button>
                    <Button
                      size="small"
                      type="primary"
                      icon={<CheckOutlined />}
                      loading={updating}
                      onClick={() => onChangeStatus(f, 'RESOLVED')}
                    >
                      标记已解决
                    </Button>
                    <Popconfirm title="忽略该问题？" onConfirm={() => onChangeStatus(f, 'IGNORED')}>
                      <Button size="small" icon={<CloseOutlined />} loading={updating}>
                        忽略
                      </Button>
                    </Popconfirm>
                    <Popconfirm title="标记为不修复？" onConfirm={() => onChangeStatus(f, 'WONT_FIX')}>
                      <Button size="small" icon={<StopOutlined />} loading={updating}>
                        不修复
                      </Button>
                    </Popconfirm>
                  </>
                ) : (
                  <Button
                    size="small"
                    icon={<RedoOutlined />}
                    loading={updating}
                    onClick={() => onChangeStatus(f, 'OPEN')}
                  >
                    重新打开
                  </Button>
                )}
              </div>
            </div>
          )
        })
      )}
    </div>
  )
}

interface PatchPreviewCardProps {
  finding: FindingDetail
  patch: PatchDetail
  busy: boolean
  onApply: () => void
  onReject: () => void
  onRegenerate: () => void
}

function PatchPreviewCard({ patch, busy, onApply, onReject, onRegenerate }: PatchPreviewCardProps) {
  const statusMeta = PATCH_STATUS_META[patch.status] ?? { label: patch.status, color: 'default' }
  const applied = patch.status === 'APPLIED'
  const stale = patch.status === 'STALE'

  return (
    <div className="patch-preview-card">
      <div className="patch-preview-head">
        <ToolOutlined className="patch-preview-icon" />
        <span className="patch-preview-title">AI 修复建议</span>
        <Tag>{PATCH_SCOPE_LABEL[patch.scope]}</Tag>
        <Tag color="geekblue">{PATCH_OPERATION_LABEL[patch.operation]}</Tag>
        <Tag color={statusMeta.color} style={{ marginLeft: 'auto' }}>
          {statusMeta.label}
        </Tag>
      </div>

      {stale && (
        <Alert
          type="warning"
          showIcon
          message="补丁已过期"
          description="草稿在补丁生成后被修改，无法应用。请基于当前草稿重新生成修复建议。"
          style={{ marginBottom: 10 }}
        />
      )}

      {patch.sourceQuote && (
        <div className="patch-diff-block patch-diff-old">
          <div className="patch-diff-label">原文</div>
          <blockquote>{patch.sourceQuote}</blockquote>
        </div>
      )}
      {patch.operation === 'DELETE' ? (
        <div className="patch-diff-block patch-diff-old">
          <div className="patch-diff-label">将删除以上内容</div>
        </div>
      ) : (
        patch.newContentMarkdown && (
          <div className="patch-diff-block patch-diff-new">
            <div className="patch-diff-label">修改为</div>
            <pre className="patch-diff-content">{patch.newContentMarkdown}</pre>
          </div>
        )
      )}

      {patch.reason && (
        <Typography.Paragraph type="secondary" style={{ marginBottom: 8, fontSize: 12 }}>
          {patch.reason}
        </Typography.Paragraph>
      )}

      {!applied && (
        <Space>
          <Button
            size="small"
            type="primary"
            icon={<CheckCircleOutlined />}
            loading={busy}
            disabled={stale}
            onClick={onApply}
          >
            应用到草稿
          </Button>
          {!stale && (
            <Button size="small" icon={<CloseOutlined />} loading={busy} onClick={onReject}>
              拒绝
            </Button>
          )}
          <Button size="small" icon={<RedoOutlined />} loading={busy} onClick={onRegenerate}>
            重新生成
          </Button>
        </Space>
      )}
      {applied && (
        <Typography.Text type="success" style={{ fontSize: 12 }}>
          <CheckCircleOutlined style={{ marginRight: 4 }} />
          已应用到当前草稿，问题标记为已解决
        </Typography.Text>
      )}
    </div>
  )
}
