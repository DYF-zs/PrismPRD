import { useEffect, useState } from 'react'
import CodeBlock from '@tiptap/extension-code-block'
import { NodeViewContent, NodeViewWrapper, ReactNodeViewRenderer } from '@tiptap/react'
import type { NodeViewProps } from '@tiptap/react'
import { renderMermaid } from './mermaidRenderer'

function DiagramBlock({ node }: NodeViewProps) {
  const language = String(node.attrs.language ?? '').toLowerCase().split(/\s/)[0]
  const diagram = language === 'mermaid'
  const source = node.textContent
  const [showSource, setShowSource] = useState(false)
  const [preview, setPreview] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  useEffect(() => {
    if (!diagram) return
    let alive = true
    setPreview(''); setError(''); setLoading(true)
    const timer = setTimeout(() => {
      renderMermaid(source).then(image => { if (alive) setPreview(image) })
        .catch(e => { if (alive) setError(e instanceof Error && /最多|统一配置|外部资源|请输入/.test(e.message)
          ? e.message : '图形暂时无法预览，请检查 Mermaid 语法；原始代码可继续编辑。') })
        .finally(() => { if (alive) setLoading(false) })
    }, 350)
    return () => { alive = false; clearTimeout(timer) }
  }, [diagram, source])
  if (!diagram) return <NodeViewWrapper as="pre" data-block-id={node.attrs.blockId ?? undefined}>
    <NodeViewContent<'code'> as="code" className={language ? `language-${language}` : undefined} />
  </NodeViewWrapper>
  return <NodeViewWrapper className="mermaid-block" data-block-id={node.attrs.blockId ?? undefined}>
    <div className="mermaid-block-header" contentEditable={false}><span>Mermaid 图形</span>
      <button type="button" onClick={() => setShowSource(value => !value)}>{showSource ? '收起代码' : '查看 / 编辑代码'}</button></div>
    <div className="mermaid-preview" contentEditable={false} aria-live="polite">
      {loading && <span>图形加载中…</span>}
      {error && <div role="alert" className="mermaid-error">{error}</div>}
      {preview && <img src={preview} alt="Mermaid 图形预览" />}
    </div>
    <pre className={!showSource && !error ? 'mermaid-source-hidden' : undefined}><NodeViewContent<'code'> as="code" className="language-mermaid" /></pre>
  </NodeViewWrapper>
}

export const DiagramCodeBlock = CodeBlock.extend({
  addNodeView() { return ReactNodeViewRenderer(DiagramBlock) },
})
