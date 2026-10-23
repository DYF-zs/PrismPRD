import { useCallback, useEffect, useRef, useState } from 'react'
import { RequestEpoch } from '../api/requestEpoch'
import { Alert, Button, Drawer, Dropdown, Empty, Input, List, Modal, Select, Space, Tag, Tooltip, message } from 'antd'
import { AuditOutlined, DeleteOutlined, HistoryOutlined, MoreOutlined, PlusOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { createVersion, fetchVersionDetail, fetchVersions } from '../api/version'
import VersionExportButton from './VersionExportButton'
import type { ReviewJobSummary, VersionDetail, VersionSummary } from '../api/types'
import { deleteReview, fetchReviewHistory } from '../api/review'
import ReviewPanel from './review/ReviewPanel'
import { reviewActionForVersion, reviewsForVersion, REVIEW_STATUS } from './review/versionReviewHistory'
import { fetchVersionDiff } from '../api/version'
import type { VersionDiff } from '../api/version'

const SOURCE_LABEL: Record<string, { label: string; color: string }> = {
  HUMAN: { label: '手动', color: 'green' },
  AI_ASSISTED: { label: 'AI 协作', color: 'purple' },
  MANUAL: { label: '手动', color: 'green' },
  AI: { label: 'AI', color: 'purple' },
  IMPORT: { label: '导入', color: 'blue' },
  REVIEW: { label: '评审', color: 'orange' },
}

interface Props {
  prdId: string
  currentVersionId: string | null
  onBeforeCreate: () => Promise<void>
  onAfterChange: () => void
  onUseVersion: (version: VersionSummary) => void
  onDeleteVersion: (version: VersionSummary) => Promise<void>
  versionBusy?: boolean
  onOpenReview: (reviewId: string) => void
  onStartReview: (version: VersionSummary, rubricId?: string) => void
  reviewStarting: boolean
  reviewRefreshKey: number
  canDelete?: boolean
  onReviewDeleted?: (reviewId: string) => void
}

export default function VersionPanel({
  prdId,
  currentVersionId,
  onBeforeCreate,
  onAfterChange,
  onUseVersion,
  onDeleteVersion,
  versionBusy = false,
  onOpenReview,
  onStartReview,
  reviewStarting,
  reviewRefreshKey,
  canDelete = false,
  onReviewDeleted,
}: Props) {
  const [versions, setVersions] = useState<VersionSummary[]>([])
  const [reviews, setReviews] = useState<ReviewJobSummary[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(currentVersionId)
  const [historyError, setHistoryError] = useState('')
  const [compareOpen, setCompareOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [changeSummary, setChangeSummary] = useState('')
  const [creating, setCreating] = useState(false)
  const [detail, setDetail] = useState<VersionDetail | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [leftId, setLeftId] = useState<string>()
  const [rightId, setRightId] = useState<string>()
  const [diffOpen, setDiffOpen] = useState(false)
  const [comparing, setComparing] = useState(false)
  const [diff, setDiff] = useState<VersionDiff | null>(null)
  const detailRequests = useRef(new RequestEpoch())
  const historyRequests = useRef(new RequestEpoch())
  const creatingRef = useRef(false)
  const deletingRef = useRef(false)
  const [deletingReviewId, setDeletingReviewId] = useState<string | null>(null)
  const [deletingVersionId, setDeletingVersionId] = useState<string | null>(null)

  useEffect(() => () => { detailRequests.current.invalidate(); historyRequests.current.invalidate() }, [])

  const reload = useCallback(async () => {
    const token = historyRequests.current.next()
    setLoading(true)
    try {
      const [list, history] = await Promise.all([fetchVersions(prdId), fetchReviewHistory(prdId)])
      if (!historyRequests.current.isCurrent(token)) return
      setVersions(list)
      setReviews(history)
      setHistoryError('')
      setSelectedId(prev => list.some(v => v.id === prev) ? prev : list.find(v => v.id === currentVersionId)?.id ?? list[0]?.id ?? null)
      setLeftId(prev => list.some(v => v.id === prev) ? prev : list[1]?.id)
      setRightId(prev => list.some(v => v.id === prev) ? prev : list[0]?.id)
    } catch (e) {
      if (historyRequests.current.isCurrent(token)) setHistoryError((e as Error).message)
    } finally {
      if (historyRequests.current.isCurrent(token)) setLoading(false)
    }
  }, [prdId, currentVersionId])

  useEffect(() => {
    void reload()
    return () => historyRequests.current.invalidate()
  }, [reload, reviewRefreshKey])

  useEffect(() => { setSelectedId(currentVersionId) }, [currentVersionId])

  const hasActive = reviews.some(review => review.status === 'PENDING' || review.status === 'RUNNING')
  useEffect(() => {
    if (!hasActive) return
    const timer = setInterval(() => void reload(), 3000)
    return () => clearInterval(timer)
  }, [hasActive, reload])

  const openCreate = () => {
    setChangeSummary('')
    setCreateOpen(true)
  }

  const doCreate = async () => {
    if (creatingRef.current || deletingRef.current || versionBusy) return
    creatingRef.current = true
    setCreating(true)
    try {
      await onBeforeCreate()
      await createVersion(prdId, changeSummary.trim())
      message.success('版本已创建')
      setCreateOpen(false)
      await reload()
      onAfterChange()
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      creatingRef.current = false
      setCreating(false)
    }
  }

  const openDetail = async (id: string) => {
    const token = detailRequests.current.next()
    try {
      const result = await fetchVersionDetail(id)
      if (detailRequests.current.isCurrent(token)) { setDetail(result); setDetailOpen(true) }
    } catch (e) {
      if (detailRequests.current.isCurrent(token)) message.error((e as Error).message)
    }
  }

  const compare = async () => {
    if (!leftId || !rightId) return
    setComparing(true)
    try { setDiff(await fetchVersionDiff(leftId, rightId)); setCompareOpen(false); setDiffOpen(true) }
    catch (e) { message.error((e as Error).message) }
    finally { setComparing(false) }
  }

  const selectedVersion = versions.find(version => version.id === selectedId)
  const reviewAction = reviewActionForVersion(reviews, selectedVersion?.id ?? null)
  const versionActionDisabled = loading || !!historyError || reviewStarting || hasActive || versionBusy || creating || !!deletingVersionId || !!deletingReviewId
  const reviewDisabled = versionActionDisabled || reviewAction.disabled

  const confirmDeleteVersion = (version: VersionSummary) => {
    if (!canDelete || deletingRef.current || versionActionDisabled) return
    const isOnly = versions.length === 1
    if (version.id === currentVersionId && !isOnly) { message.info('请先使用其他版本，再删除当前基线版本'); return }
    Modal.confirm({
      title: `永久删除 v${version.versionNo}？`,
      content: `该版本及其全部评审、测试用例和待处理修改建议将从数据库删除，无法恢复。${isOnly ? '当前正文保留，文档解除版本基线。' : '当前正文和其他版本保留。'}`,
      okText: '永久删除', cancelText: '取消', okButtonProps: { danger: true },
      onOk: async () => {
        if (deletingRef.current) return
        deletingRef.current = true
        setDeletingVersionId(version.id)
        try {
          await onDeleteVersion(version)
          detailRequests.current.invalidate()
          setDetailOpen(false)
          setDiffOpen(false)
          setCompareOpen(false)
          message.success(`v${version.versionNo} 已删除`)
          await reload()
          onAfterChange()
        } catch (e) {
          message.error((e as Error).message)
          throw e
        } finally {
          deletingRef.current = false
          setDeletingVersionId(null)
        }
      },
    })
  }

  const confirmDeleteReview = (review: ReviewJobSummary) => {
    if (!canDelete || deletingRef.current || versionActionDisabled) return
    Modal.confirm({
      title: `永久删除 v${selectedVersion?.versionNo ?? review.versionNo} 的这次评审？`,
      content: '该次评审的报告、评分、问题及待处理修改建议将从数据库删除，无法恢复。文档、其他评审及已应用的正文修改保留。',
      okText: '永久删除', cancelText: '取消', okButtonProps: { danger: true },
      onOk: async () => {
        if (deletingRef.current) return
        deletingRef.current = true
        setDeletingReviewId(review.id)
        try {
          await deleteReview(review.id)
          onReviewDeleted?.(review.id)
          message.success('评审记录已删除')
          await reload()
          onAfterChange()
        } catch (e) {
          message.error((e as Error).message)
          throw e
        } finally {
          deletingRef.current = false
          setDeletingReviewId(null)
        }
      },
    })
  }

  return (
    <div className="version-panel version-review-panel">
      <div className="panel-section-header">
        <HistoryOutlined />
        <span>版本历史</span>
        <Button size="small" type="primary" icon={<PlusOutlined />} disabled={versionActionDisabled} onClick={openCreate}>
          创建版本
        </Button>
      </div>
      <Space className="version-history-actions">
        <Button size="small" disabled={versions.length < 2} onClick={() => setCompareOpen(true)}>对比版本</Button>
        <Button size="small" loading={loading} onClick={() => void reload()}>刷新</Button>
      </Space>
      {historyError && <Alert type="error" showIcon message="版本与评审加载失败" description={historyError}
        action={<Button size="small" onClick={() => void reload()}>重试</Button>} />}
      <section className="version-review-upper" aria-label="版本历史">
      <List
        size="small"
        loading={loading}
        dataSource={versions}
        locale={{ emptyText: <Empty description="暂无版本" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
        renderItem={(v) => {
          const source = SOURCE_LABEL[v.source] ?? { label: v.source, color: 'default' }
          const isCurrent = v.id === currentVersionId
          const latest = reviewsForVersion(reviews, v.id)[0]
          return (
            <List.Item
              className={`version-item${selectedId === v.id ? ' version-item--selected' : ''}`}
              actions={[
                <Button key="use" size="small" type="text" disabled={versionActionDisabled}
                  aria-label={`使用 v${v.versionNo}`} onClick={e => { e.stopPropagation(); if (!versionActionDisabled) onUseVersion(v) }}>使用此版本</Button>,
                  <Button key="view" size="small" type="text" onClick={e => { e.stopPropagation(); void openDetail(v.id) }}>
                    查看
                  </Button>,
                <VersionExportButton key="export" versionId={v.id} compact />,
                canDelete ? <Dropdown key="more" trigger={['click']} menu={{ items: [{
                  key: 'delete', label: '删除版本', danger: true, icon: <DeleteOutlined />,
                  disabled: versionActionDisabled || (isCurrent && versions.length > 1),
                  title: isCurrent && versions.length > 1 ? '请先使用其他版本，再删除当前基线版本' : undefined,
                  onClick: ({ domEvent }) => { domEvent.stopPropagation(); confirmDeleteVersion(v) },
                }] }}>
                  <Button size="small" type="text" aria-label={`v${v.versionNo} 更多操作`} icon={<MoreOutlined />}
                    loading={deletingVersionId === v.id} disabled={versionActionDisabled} onClick={event => event.stopPropagation()} />
                </Dropdown> : null,
              ]}
              onClick={() => setSelectedId(v.id)}
            >
              <List.Item.Meta
                title={
                  <span>
                    <button className="version-select" aria-pressed={selectedId === v.id}
                      onClick={() => setSelectedId(v.id)}>v{v.versionNo}</button>
                    {isCurrent && (
                      <Tag color="green" style={{ marginLeft: 8 }}>
                        当前基线
                      </Tag>
                    )}
                    <Tag color={source.color} style={{ marginLeft: 8 }}>
                      {source.label}
                    </Tag>
                  </span>
                }
                description={
                  <span className="version-item-desc">
                    {v.changeSummary || '（无变更摘要）'}
                    <br />
                    {dayjs(v.createdAt).format('YYYY-MM-DD HH:mm')}
                    <span className="version-latest-review">{latest ? <>
                      最近评审 <Tag color={REVIEW_STATUS[latest.status].color}>{REVIEW_STATUS[latest.status].label}</Tag>
                      {latest.overallScore != null && `${Number(latest.overallScore).toFixed(1)} 分`}
                    </> : '尚未评审'}</span>
                  </span>
                }
              />
            </List.Item>
          )
        }}
      />
      </section>
      <div className="version-review-lower">
        <ReviewPanel versionNo={selectedVersion?.versionNo ?? null}
          reviews={selectedId ? reviewsForVersion(reviews, selectedId) : []} onOpenReview={onOpenReview} refreshKey={reviewRefreshKey}
          onDeleteReview={canDelete ? confirmDeleteReview : undefined}
          deleteDisabled={versionActionDisabled} deletingReviewId={deletingReviewId}
          action={<Tooltip title={reviewAction.hint}>
            <Button className="version-review-start" size="small" type="primary" icon={<AuditOutlined />}
              disabled={reviewDisabled} loading={reviewStarting || reviewAction.reviewing}
              onClick={() => { if (selectedVersion && !reviewDisabled) onStartReview(selectedVersion, reviewAction.initialRubricId) }}>
              {reviewStarting ? '提交中' : reviewAction.label}
            </Button>
          </Tooltip>} />
      </div>

      <Modal title="对比版本" open={compareOpen} onCancel={() => { if (!comparing) setCompareOpen(false) }}
        onOk={() => void compare()} confirmLoading={comparing} okText="对比" cancelText="取消"
        okButtonProps={{ disabled: !leftId || !rightId || leftId === rightId }}>
        <Space direction="vertical" className="artifact-controls">
          <Select aria-label="对比旧版本" placeholder="选择旧版本" style={{ width: '100%' }} value={leftId} disabled={comparing}
            onChange={setLeftId} options={versions.map(v => ({ value: v.id, label: `旧：v${v.versionNo}` }))} />
          <Select aria-label="对比新版本" placeholder="选择新版本" style={{ width: '100%' }} value={rightId} disabled={comparing}
            onChange={setRightId} options={versions.map(v => ({ value: v.id, label: `新：v${v.versionNo}` }))} />
        </Space>
      </Modal>

      <Modal
        title="创建版本"
        open={createOpen}
        onOk={doCreate}
        onCancel={() => setCreateOpen(false)}
        confirmLoading={creating}
        okText="创建"
        cancelText="取消"
      >
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          placeholder="填写本版本的变更摘要，例如：补充异常流程与验收标准"
          value={changeSummary}
          onChange={(e) => setChangeSummary(e.target.value)}
        />
      </Modal>

      <Drawer
        title={detail ? `v${detail.versionNo} · ${detail.changeSummary || '版本内容'}` : '版本内容'}
        open={detailOpen}
        onClose={() => { detailRequests.current.invalidate(); setDetailOpen(false) }}
        width={640}
        extra={
          detail && (
            <Space>
              <VersionExportButton versionId={detail.id} />
              <Button type="primary" disabled={versionActionDisabled} onClick={() => { setDetailOpen(false); onUseVersion(detail) }}>
                使用此版本
              </Button>
            </Space>
          )
        }
      >
        {detail && <>
          {detail.patches?.length > 0 && <List
            header="本版本接受的 AI 修改"
            size="small"
            dataSource={detail.patches}
            renderItem={p => <List.Item>
              <List.Item.Meta
                title={p.findingTitle ?? (p.source === 'ACCEPTANCE' ? '验收标准补充' : p.source === 'INLINE_AI' ? 'Inline AI 修改' : 'AI 对话修改')}
                description={p.reason || p.sourceQuote || p.newContentMarkdown}
              />
            </List.Item>}
          />}
          <pre className="version-markdown">{detail.markdownSnapshot}</pre>
        </>}
      </Drawer>
      <Drawer title={diff ? `版本对比 · v${diff.leftVersionNo} → v${diff.rightVersionNo}` : '版本对比'}
        width={960} open={diffOpen} onClose={() => setDiffOpen(false)}>
        {diff && <>
          <Space><Tag color="green">新增 {diff.addedLines} 行</Tag><Tag color="red">删除 {diff.deletedLines} 行</Tag></Space>
          {diff.addedLines === 0 && diff.deletedLines === 0 && <Alert type="info" message="两个版本的正文内容相同" />}
          <div className="version-diff" aria-label="版本差异">
            {diff.lines.map((line, i) => <div key={i} className={`diff-line diff-${line.type.toLowerCase()}`}>
              <span aria-hidden="true">{line.type === 'INSERT' ? '+' : line.type === 'DELETE' ? '−' : ' '}</span>
              <code>{line.text || '\u00a0'}</code>
            </div>)}
          </div>
          <List header={`目标版本 v${diff.rightVersionNo} 接受的 AI 修改（非跨版本累计清单）`} dataSource={diff.acceptedPatches}
            locale={{ emptyText: '目标版本没有已关联的 AI 修改' }}
            renderItem={p => <List.Item><List.Item.Meta title={p.findingTitle ?? p.reason ?? p.source}
              description={p.newContentMarkdown || p.sourceQuote} /></List.Item>} />
        </>}
      </Drawer>
    </div>
  )
}
