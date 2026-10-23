import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Input, List, Modal, Popconfirm, Select, Space, Switch, Tag, Typography, Upload, message } from 'antd'
import {
  archiveKnowledgeDocument, bindKnowledgeBase, createKnowledgeBase, fetchKnowledgeBases,
  fetchKnowledgeContent, fetchKnowledgeDocuments, retryKnowledgeDocument, searchKnowledge, uploadKnowledgeFile,
} from '../api/knowledge'
import type { KnowledgeBase, KnowledgeDocument, KnowledgeHit } from '../api/knowledge'

export default function KnowledgePanel({ prdId }: { prdId: string }) {
  const [bases, setBases] = useState<KnowledgeBase[]>([])
  const [selected, setSelected] = useState<string>()
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [name, setName] = useState('')
  const [query, setQuery] = useState('')
  const [hits, setHits] = useState<KnowledgeHit[] | null>(null)
  const [source, setSource] = useState<{ title: string; content: string } | null>(null)
  const [refresh, setRefresh] = useState(0)
  const busyRef = useRef(false)
  const mounted = useRef(true)
  const selectionRef = useRef(selected)
  const loadedSelectionRef = useRef<string>()
  selectionRef.current = selected
  const base = bases.find(item => item.id === selected)

  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  useEffect(() => {
    let alive = true
    fetchKnowledgeBases(prdId).then(items => {
      if (!alive) return
      setBases(items)
      setSelected(value => items.some(item => item.id === value) ? value : items.find(item => item.bound)?.id ?? items[0]?.id)
    }).catch(e => { if (alive) setError((e as Error).message) })
    return () => { alive = false }
  }, [prdId, refresh])

  useEffect(() => {
    let alive = true
    let timer: ReturnType<typeof setTimeout> | undefined
    if (loadedSelectionRef.current !== selected) { setDocuments([]); loadedSelectionRef.current = selected }
    if (!selected) return
    const load = async () => {
      try {
        const items = await fetchKnowledgeDocuments(selected)
        if (!alive) return
        setDocuments(items)
        if (items.some(item => item.processingStatus === 'PROCESSING')) timer = setTimeout(load, 2000)
      } catch (e) { if (alive) setError((e as Error).message) }
    }
    void load()
    return () => { alive = false; if (timer) clearTimeout(timer) }
  }, [selected, refresh])

  const run = async (action: () => Promise<void>) => {
    if (busyRef.current) return
    busyRef.current = true; setBusy(true); setError(null)
    try { await action() }
    catch (e) { if (mounted.current) { setError((e as Error).message); message.error((e as Error).message) } }
    finally { busyRef.current = false; if (mounted.current) { setBusy(false); setRefresh(value => value + 1) } }
  }
  const showSource = (id: string, title: string) => run(async () => {
    const content = await fetchKnowledgeContent(id)
    if (mounted.current) setSource({ title, content: content ?? '尚未提取正文，请检查索引状态。' })
  })

  return <div style={{ padding: 12, overflowWrap: 'anywhere' }}>
    <Space direction="vertical" style={{ width: '100%' }}>
      <Typography.Text strong>知识与参考附件</Typography.Text>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>对话与评审只检索已绑定的知识库。PDF 用作参考资料，不覆盖 PRD 正文。</Typography.Text>
      {error && <Alert type="error" message={error} showIcon closable onClose={() => setError(null)} />}
      <Select aria-label="选择知识库" style={{ width: '100%' }} placeholder="选择知识库" value={selected} disabled={busy}
        options={bases.map(item => ({ value: item.id, label: `${item.bound ? '已绑定 · ' : ''}${item.name}` }))}
        onChange={value => { setSelected(value); setHits(null); setError(null) }} />
      <Button onClick={() => setCreateOpen(true)} disabled={busy}>新建并绑定知识库</Button>
      {base && <>
        <Space><Switch aria-label="绑定当前PRD" checked={base.bound} disabled={busy || (!base.owned && !base.bound)}
          onChange={enabled => void run(async () => { await bindKnowledgeBase(prdId, base.id, enabled); setHits(null) })} />
          <Typography.Text>绑定当前 PRD</Typography.Text></Space>
        <Space wrap>
          {base.owned && <Upload accept=".md,.markdown,.txt,.docx,.pdf" showUploadList={false} disabled={busy}
            beforeUpload={file => {
              if (file.size > 10 * 1024 * 1024) { message.error('文件不能超过 10 MiB'); return false }
              void run(async () => { await uploadKnowledgeFile(base.id, file); message.success('资料已上传，正在解析和建立向量索引') })
              return false
            }}><Button loading={busy}>上传参考资料</Button></Upload>}
          <Button disabled={busy} onClick={() => setRefresh(value => value + 1)}>刷新</Button>
        </Space>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>支持 MD / UTF-8 TXT / DOCX / 文本型 PDF，单文件 ≤10 MiB。扫描件需先 OCR。</Typography.Text>
        <List size="small" dataSource={documents} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚无参考资料" /> }}
          renderItem={doc => <List.Item><div style={{ width: '100%' }}>
            <Typography.Text strong>{doc.title}</Typography.Text><br />
            <Tag color={doc.processingStatus === 'READY' ? 'green' : doc.processingStatus === 'FAILED' ? 'red' : 'blue'}>
              {doc.processingStatus === 'READY' ? `可检索 · ${doc.chunkCount} 块` : doc.processingStatus === 'FAILED' ? '索引失败' : '索引中'}
            </Tag>
            {doc.errorMessage && <Typography.Paragraph type="danger" style={{ fontSize: 12 }}>{doc.errorMessage}</Typography.Paragraph>}
            <Space size={4} wrap>
              <Button size="small" disabled={busy || doc.processingStatus !== 'READY'} onClick={() => void showSource(doc.id, doc.title)}>查看正文</Button>
              {base.owned && <>
                <Button size="small" disabled={busy || doc.processingStatus === 'PROCESSING'} onClick={() => void run(() => retryKnowledgeDocument(doc.id))}>重新索引</Button>
                <Popconfirm title="从检索中移除此资料？原文件保留在本地存储。" onConfirm={() => run(() => archiveKnowledgeDocument(doc.id))}>
                  <Button size="small" danger disabled={busy}>移除</Button>
                </Popconfirm>
              </>}
            </Space>
          </div></List.Item>} />
      </>}
      <Input.TextArea aria-label="知识检索问题" placeholder="输入问题，验证当前 PRD 的知识检索" maxLength={4000} rows={3} value={query} onChange={e => setQuery(e.target.value)} />
      <Button disabled={busy || !query.trim()} loading={busy} onClick={() => void run(async () => {
        const found = await searchKnowledge(prdId, query.trim())
        if (mounted.current && selectionRef.current === selected) setHits(found)
      })}>检索已绑定知识</Button>
      {hits && <List size="small" dataSource={hits} locale={{ emptyText: '未命中参考资料，请检查绑定和索引状态，或调整问题。' }} renderItem={hit => <List.Item>
        <div><Button type="link" size="small" onClick={() => void showSource(hit.knowledgeDocumentId, hit.documentTitle)}>{hit.documentTitle}</Button>
          <div style={{ fontSize: 12 }}>{hit.sectionPath} · 相似度 {hit.score.toFixed(3)}</div>
          <blockquote style={{ whiteSpace: 'pre-wrap', margin: '8px 0', fontSize: 12 }}>{hit.content}</blockquote></div>
      </List.Item>} />}
    </Space>
    <Modal title="新建知识库" open={createOpen} confirmLoading={busy} onCancel={() => setCreateOpen(false)} okButtonProps={{ disabled: !name.trim() }}
      onOk={() => void run(async () => { const created = await createKnowledgeBase(prdId, name.trim()); if (mounted.current) { setSelected(created.id); setName(''); setCreateOpen(false) } })}>
      <Input aria-label="知识库名称" placeholder="例如：公司产品与安全规范" maxLength={255} value={name} onChange={e => setName(e.target.value)} />
    </Modal>
    <Modal title={source?.title} open={source !== null} onCancel={() => setSource(null)} footer={null} width={800}>
      <pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', maxHeight: '65vh', overflow: 'auto' }}>{source?.content}</pre>
    </Modal>
  </div>
}
