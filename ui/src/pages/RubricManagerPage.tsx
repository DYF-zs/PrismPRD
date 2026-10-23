import { useEffect, useRef, useState } from 'react'
import { useBlocker, useNavigate } from 'react-router-dom'
import { Alert, Button, Card, ConfigProvider, Input, Modal, Select, Space, Spin, Typography, message } from 'antd'
import { fetchRubrics, fetchRubricWithVersions, publishRubric, publishRubricVersion } from '../api/rubric'
import { RequestEpoch } from '../api/requestEpoch'
import type { RubricDetail, RubricInput, RubricSummary } from '../api/rubric'
import RubricViewer from '../components/review/RubricViewer'
import RubricDimensionEditor from '../components/review/RubricDimensionEditor'
import { newDimension, rubricInput, validateRubric } from '../components/review/rubricRules'

export default function RubricManagerPage() {
  const navigate = useNavigate()
  const [list, setList] = useState<RubricSummary[]>([])
  const [versions, setVersions] = useState<RubricSummary[]>([])
  const [detail, setDetail] = useState<RubricDetail | null>(null)
  const [input, setInput] = useState<RubricInput | null>(null)
  const [base, setBase] = useState<RubricDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [publishing, setPublishing] = useState(false)
  const [error, setError] = useState('')
  const requests = useRef(new RequestEpoch())
  const busy = useRef(false)
  const dirty = input !== null
  const blocker = useBlocker(dirty || publishing)

  const load = async (id?: string) => {
    const token = requests.current.next()
    setLoading(true); setError('')
    try {
      const items = await fetchRubrics()
      const selectedId = id ?? items[0]?.id
      const [selected, history] = selectedId ? await fetchRubricWithVersions(selectedId) : [null, []]
      if (!requests.current.isCurrent(token)) return
      setList(items); setDetail(selected); setVersions(history)
    } catch (e) { if (requests.current.isCurrent(token)) setError((e as Error).message) }
    finally { if (requests.current.isCurrent(token)) setLoading(false) }
  }
  useEffect(() => { void load(); return () => requests.current.invalidate() }, [])
  useEffect(() => {
    if (!dirty && !publishing) return
    const warn = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = '' }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty, publishing])

  const discard = (next: () => void) => {
    if (!dirty) { next(); return }
    Modal.confirm({ title: '放弃未发布的标准内容？', content: '未发布的内容只保留在当前页面，离开后无法恢复。', okText: '放弃内容', cancelText: '继续编辑', onOk: () => { setInput(null); setBase(null); next() } })
  }
  const edit = (source: RubricDetail | null, copy = false) => {
    setBase(copy ? null : source)
    setInput(source ? { ...rubricInput(source), name: copy ? `${source.name.slice(0, 124)} 副本` : source.name } : { name: '', description: '', dimensions: [newDimension(1)] })
  }
  const publish = async () => {
    if (!input || busy.current) return
    const invalid = validateRubric(input)
    if (invalid) { message.warning(invalid); return }
    busy.current = true; setPublishing(true); setError('')
    try {
      const result = base ? await publishRubricVersion(base.rubricKey, { ...input, baseRubricId: base.id }) : await publishRubric(input)
      setInput(null); setBase(null)
      message.success(`已发布 ${result.name} v${result.version}`)
      await load(result.id)
    } catch (e) { setError((e as Error).message) }
    finally { busy.current = false; setPublishing(false) }
  }

  return <div className="rubric-page">
    <div className="page-container">
      <Space wrap style={{ marginBottom: 20 }}>
        <Button onClick={() => navigate('/prds')}>返回文档库</Button>
        <Typography.Title level={3} style={{ margin: 0 }}>评审标准管理</Typography.Title>
      </Space>
      <Alert type="info" showIcon message="已发布版本只读；修改内容通过发布新版本生效，历史评审始终使用当时的标准版本。" style={{ marginBottom: 16 }} />
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} action={!input ? <Button onClick={() => void load(detail?.id)}>重试</Button> : undefined} />}
      <Card>
        <div className="rubric-toolbar">
          <Select aria-label="选择评审标准" placeholder="选择标准" className="rubric-selector" value={detail?.rubricKey} disabled={loading || publishing || dirty}
            options={list.map(r => ({ value: r.rubricKey, label: `${r.name} · 最新 v${r.version}` }))}
            onChange={key => void load(list.find(r => r.rubricKey === key)?.id)} />
          <Select aria-label="选择标准版本" className="rubric-version-selector" value={detail?.id} disabled={loading || publishing || dirty}
            options={versions.map(r => ({ value: r.id, label: `标准 v${r.version}` }))} onChange={id => void load(id)} />
          {!input && <>
            <Button onClick={() => void load(detail?.id)} disabled={loading}>刷新标准</Button>
            <Button onClick={() => edit(null)} disabled={loading}>新建标准</Button>
            <Button type={detail?.builtin ? 'primary' : 'default'} onClick={() => detail && edit(detail, true)} disabled={!detail || loading}>复制为自定义标准</Button>
            {detail?.editable && <Button type="primary" onClick={() => edit(detail)} disabled={loading || list.find(r => r.rubricKey === detail.rubricKey)?.id !== detail.id}>编辑并发布新版本</Button>}
          </>}
        </div>
        {loading ? <Spin /> : input ? <>
          <Alert type="warning" message={base ? `正在编辑 ${base.name} v${base.version}，发布后生成新版本。未发布内容仅保留在当前页面。` : '正在创建独立标准，发布后生成 v1。未发布内容仅保留在当前页面。'} style={{ marginBottom: 16 }} />
          <div className="rubric-edit-actions">
            <Button type="primary" loading={publishing} onClick={() => void publish()}>发布{base ? '新版本' : '标准 v1'}</Button>
            <Button disabled={publishing} onClick={() => discard(() => { void load(detail?.id) })}>放弃编辑</Button>
            <Typography.Text type="secondary">{input.dimensions.length} 个评审维度</Typography.Text>
          </div>
          <ConfigProvider componentDisabled={publishing}>
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <label>标准名称<Input aria-label="标准名称" maxLength={128} value={input.name} onChange={e => setInput({ ...input, name: e.target.value })} /></label>
              <label>标准说明<Input.TextArea aria-label="标准说明" maxLength={4000} value={input.description} onChange={e => setInput({ ...input, description: e.target.value })} /></label>
              <RubricDimensionEditor disabled={publishing} dimensions={input.dimensions} onChange={dimensions => { if (!busy.current) setInput({ ...input, dimensions }) }} />
              <Button disabled={input.dimensions.length >= 24 || publishing} onClick={() => {
                let next = 1
                while (input.dimensions.some(d => d.dimensionKey === `CUSTOM_${next}`)) next++
                setInput({ ...input, dimensions: [...input.dimensions, newDimension(next)] })
              }}>添加评审维度</Button>
            </Space>
          </ConfigProvider>
        </> : detail ? <RubricViewer rubric={detail} /> : <Typography.Text>暂无标准，请新建标准。</Typography.Text>}
      </Card>
    </div>
    <Modal open={blocker.state === 'blocked'} title={publishing ? '标准发布中' : '放弃未发布内容并离开？'} okText="放弃并离开" cancelText="继续编辑"
      okButtonProps={{ disabled: publishing }} cancelButtonProps={{ disabled: publishing }} closable={!publishing} maskClosable={false}
      onOk={() => { setInput(null); blocker.proceed?.() }} onCancel={() => blocker.reset?.()}>
      {publishing ? '请等待发布完成后离开。' : '未发布的内容只保留在当前页面，离开后无法恢复。'}
    </Modal>
  </div>
}
