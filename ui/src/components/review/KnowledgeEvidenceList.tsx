import { useRef, useState, useEffect } from 'react'
import { Button, Modal, Typography, message } from 'antd'
import type { ReviewEvidence } from '../../api/types'
import { fetchKnowledgeContent } from '../../api/knowledge'

export default function KnowledgeEvidenceList({ evidence }: { evidence: ReviewEvidence[] | null | undefined }) {
  const [source, setSource] = useState<{ title: string; content: string } | null>(null)
  const [loading, setLoading] = useState(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  const citations = (evidence ?? []).filter(item => item.sourceType === 'KNOWLEDGE_DOCUMENT' && item.knowledgeDocumentId)
  if (citations.length === 0) return null
  return <div style={{ margin: '8px 0' }}>
    <Typography.Text type="secondary">知识依据：</Typography.Text>
    {citations.map((item, index) => <div key={`${item.chunkId}-${index}`}>
      <Button size="small" type="link" disabled={loading} onClick={async () => {
        setLoading(true)
        try {
          const content = await fetchKnowledgeContent(item.knowledgeDocumentId!)
          if (mounted.current) setSource({ title: item.documentTitle ?? '知识来源', content: content ?? '来源正文不可用' })
        } catch (e) { message.error((e as Error).message) }
        finally { if (mounted.current) setLoading(false) }
      }}>{item.documentTitle ?? '参考资料'} · {item.sectionPath ?? '正文'}</Button>
      <blockquote style={{ whiteSpace: 'pre-wrap', fontSize: 12, margin: '4px 0 8px' }}>{item.quote}</blockquote>
    </div>)}
    <Modal title={source?.title} open={source !== null} onCancel={() => setSource(null)} footer={null} width={800}>
      <pre style={{ whiteSpace: 'pre-wrap', maxHeight: '65vh', overflow: 'auto' }}>{source?.content}</pre>
    </Modal>
  </div>
}
