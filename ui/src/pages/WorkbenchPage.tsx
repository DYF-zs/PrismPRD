import { useEffect, useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import { Alert, Button, Drawer, Empty, Input, Spin, Typography, message } from 'antd'
import { FileTextOutlined, PlusOutlined, RobotOutlined, SendOutlined } from '@ant-design/icons'
import { Link, useNavigate } from 'react-router-dom'
import { createPrd, fetchPrdList } from '../api/prd'
import type { PrdDocumentSummary } from '../api/types'
import ImportPrdButton from '../components/ImportPrdButton'
import PanelResizeHandle from '../components/workspace/PanelResizeHandle'
import { useWorkbenchLayout } from '../components/workspace/workbenchLayout'

export default function WorkbenchPage() {
  const navigate = useNavigate()
  const [input, setInput] = useState('')
  const [recent, setRecent] = useState<PrdDocumentSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [starting, setStarting] = useState(false)
  const [recentOpen, setRecentOpen] = useState(false)
  const busy = useRef(false)
  const layout = useWorkbenchLayout()
  useEffect(() => {
    let alive = true
    fetchPrdList({ page: 1, size: 12 }).then(result => { if (alive) setRecent(result.items) })
      .catch(e => { if (alive) setError((e as Error).message) })
      .finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [])
  const start = async () => {
    const requirement = input.trim()
    if (!requirement || busy.current) return
    busy.current = true; setStarting(true)
    try {
      const created = await createPrd({ creationMode: 'AI_DRAFT', title: requirement.split('\n')[0].slice(0, 80), initialRequirement: requirement })
      navigate(`/prds/${created.prdId}?view=chat`, { state: { startSessionId: created.defaultSessionId } })
    } catch (e) { message.error((e as Error).message) }
    finally { busy.current = false; setStarting(false) }
  }
  const recentDocuments = loading ? <Spin className="workbench-loading" /> : error ? <Alert type="error" message={error} /> : recent.length ?
    <div className="recent-documents">{recent.map(prd => <Link key={prd.id} title={prd.title} to={`/prds/${prd.id}?view=chat`}><FileTextOutlined /><span>{prd.title}</span></Link>)}</div> :
    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="开始你的第一段需求讨论" />
  return <div ref={layout.containerRef} className="workbench-home" style={{
    '--workbench-recent-width': `${layout.sizes.recent}px`,
    '--workbench-preview-width': `${layout.sizes.preview}px`,
  } as CSSProperties}>
    <aside className="workbench-recent" aria-label="最近的 PRD 对话">
      <PanelResizeHandle label="调整最近对话列表宽度" value={layout.sizes.recent} {...layout.bounds.recent}
        onResize={value => layout.resize('recent', value)} onReset={() => layout.reset('recent')} />
      <div className="workbench-recent-content">
      <div className="workbench-section-title">最近的 PRD 对话</div>
      <Button block type="primary" icon={<PlusOutlined />} onClick={() => setInput('')} disabled={starting}>新对话</Button>
      {recentDocuments}
      <Link className="workbench-library-link" to="/prds">查看全部文档 →</Link>
      </div>
    </aside>
    <section className="workbench-conversation" aria-label="AI 对话">
      <Button className="workbench-recent-toggle" onClick={() => setRecentOpen(true)}>最近对话</Button>
      <div className="workbench-welcome">
        <RobotOutlined className="workbench-robot" />
        <Typography.Title level={2}>从一个想法，开始你的 PRD</Typography.Title>
        <Typography.Paragraph type="secondary">直接描述需求，与 AI 一起澄清目标、梳理流程和完善方案。也可以导入已有文档继续工作。</Typography.Paragraph>
        <div className="workbench-prompts">{['帮我梳理一个新产品的需求', '一起设计功能流程和验收标准', '帮我补充需求的边界与异常场景'].map(prompt =>
          <Button key={prompt} onClick={() => setInput(prompt)} disabled={starting}>{prompt}</Button>)}</div>
      </div>
      <div className="workbench-composer">
        <Input.TextArea aria-label="描述你的需求" placeholder="描述你的想法，或提出想讨论的需求问题…" autoSize={{ minRows: 3, maxRows: 8 }}
          maxLength={4000} showCount value={input} disabled={starting} onChange={e => setInput(e.target.value)}
          onPressEnter={e => { if (!e.shiftKey && !e.nativeEvent.isComposing && e.keyCode !== 229) { e.preventDefault(); void start() } }} />
        <div className="workbench-composer-actions"><ImportPrdButton disabled={starting} /><span className="workbench-composer-hint">Enter 发送 · Shift + Enter 换行</span>
          <Button type="primary" icon={<SendOutlined />} loading={starting} disabled={!input.trim()} onClick={() => void start()}>发送</Button></div>
      </div>
    </section>
    <aside className="workbench-preview" aria-label="PRD 草稿预览">
      <PanelResizeHandle label="调整草稿预览宽度" edge="start" value={layout.sizes.preview} {...layout.bounds.preview}
        onResize={value => layout.resize('preview', value)} onReset={() => layout.reset('preview')} />
      <div className="workbench-preview-content">
      <Typography.Title level={4}>PRD 草稿</Typography.Title>
      <div className="workbench-draft-placeholder"><FileTextOutlined /><p>你的需求文档将在这里展开</p><Typography.Text type="secondary">AI 提出的正文修改会先供你预览，确认后写入草稿。</Typography.Text></div>
      <ImportPrdButton disabled={starting} />
      </div>
    </aside>
    <Drawer title="最近的 PRD 对话" placement="left" width={320} open={recentOpen} onClose={() => setRecentOpen(false)}>
      {recentDocuments}
      <Link className="workbench-library-link" to="/prds">查看全部文档 →</Link>
    </Drawer>
  </div>
}
