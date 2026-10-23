import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Collapse, Modal, Select, Spin } from 'antd'
import { useNavigate } from 'react-router-dom'
import { fetchRubrics, fetchRubricWithVersions } from '../../api/rubric'
import { RequestEpoch } from '../../api/requestEpoch'
import type { RubricDetail, RubricSummary } from '../../api/rubric'
import RubricViewer from './RubricViewer'

interface Props {
  open: boolean; versionNo: number | null; starting: boolean
  initialRubricId?: string
  onClose: () => void; onConfirm: (rubricId: string) => void
}
export default function StartReviewModal({ open, versionNo, starting, initialRubricId, onClose, onConfirm }: Props) {
  const navigate = useNavigate()
  const [list, setList] = useState<RubricSummary[]>([])
  const [versions, setVersions] = useState<RubricSummary[]>([])
  const [selected, setSelected] = useState<RubricDetail | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const requests = useRef(new RequestEpoch())
  const choose = async (id: string) => {
    const token = requests.current.next()
    setSelected(null); setLoading(true); setError('')
    try {
      const [detail, history] = await fetchRubricWithVersions(id)
      if (!requests.current.isCurrent(token)) return
      setSelected(detail); setVersions(history)
    } catch (e) { if (requests.current.isCurrent(token)) setError((e as Error).message) }
    finally { if (requests.current.isCurrent(token)) setLoading(false) }
  }
  useEffect(() => {
    if (!open) return
    const token = requests.current.next()
    setSelected(null); setList([]); setVersions([]); setLoading(true); setError('')
    fetchRubrics().then(items => {
      if (!requests.current.isCurrent(token)) return
      setList(items)
      if (initialRubricId) { void choose(initialRubricId); return }
      const first = items.find(r => r.rubricKey === 'default') ?? items[0]
      if (first) void choose(first.id)
      else { setError('暂无可用评审标准，请先创建并发布标准'); setLoading(false) }
    }).catch(e => { if (requests.current.isCurrent(token)) { setError((e as Error).message); setLoading(false) } })
    return () => requests.current.invalidate()
  }, [open, initialRubricId])
  return <Modal title="选择评审标准" open={open} width={760} okText="确认并发起评审" cancelText="取消" confirmLoading={starting}
    okButtonProps={{ disabled: loading || !selected }} cancelButtonProps={{ disabled: starting }} closable={!starting} maskClosable={!starting}
    onCancel={onClose} onOk={() => selected && !starting && onConfirm(selected.id)}>
    <Alert type="info" message={`评审基于 PRD 固定快照 v${versionNo ?? '-'}。当前未创建版本的草稿修改不参与本次评审。`} style={{ marginBottom: 16 }} />
    {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}
    <div className="rubric-toolbar">
      <Select aria-label="本次评审标准" className="rubric-selector" placeholder="选择标准" value={selected?.rubricKey} disabled={loading || starting}
        options={list.map(r => ({ value: r.rubricKey, label: `${r.name} · 最新 v${r.version}` }))} onChange={key => {
          const item = list.find(r => r.rubricKey === key)
          if (item) void choose(item.id)
        }} />
      <Select aria-label="本次标准版本" className="rubric-version-selector" value={selected?.id} disabled={loading || starting}
        options={versions.map(r => ({ value: r.id, label: `标准 v${r.version}` }))} onChange={id => void choose(id)} />
      <Button disabled={starting} onClick={() => { onClose(); navigate('/settings/review-rubrics') }}>管理标准</Button>
    </div>
    {loading && <Spin />}
    {selected && <Collapse items={[{ key: 'preview', label: `查看规则 · ${selected.dimensions.length} 个维度`, children: <RubricViewer rubric={selected} /> }]} />}
  </Modal>
}
