import { useEffect, useState } from 'react'
import type { Editor } from '@tiptap/react'

interface HeadingEntry {
  level: number
  text: string
}

interface Props {
  editor: Editor | null
  containerRef: React.RefObject<HTMLDivElement>
}

export default function Outline({ editor, containerRef }: Props) {
  const [headings, setHeadings] = useState<HeadingEntry[]>([])

  useEffect(() => {
    if (!editor) return
    const update = () => {
      const entries: HeadingEntry[] = []
      editor.state.doc.descendants((node) => {
        if (node.type.name === 'heading') {
          entries.push({ level: node.attrs.level as number, text: node.textContent })
        }
      })
      setHeadings(entries)
    }
    // transaction 覆盖 setContent 等不触发 update 事件的变化
    editor.on('transaction', update)
    update()
    return () => {
      editor.off('transaction', update)
    }
  }, [editor])

  if (headings.length === 0) {
    return <div className="outline-empty">暂无标题，编辑器中的标题会显示在这里</div>
  }

  const scrollTo = (index: number) => {
    const els = containerRef.current?.querySelectorAll('h1,h2,h3,h4,h5,h6')
    els?.[index]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <nav className="outline-list" aria-label="文档大纲">
      {headings.map((h, i) => (
        <button
          type="button"
          key={i}
          className="outline-item"
          style={{ paddingLeft: (h.level - 1) * 12 + 4 }}
          onClick={() => scrollTo(i)}
          title={h.text}
        >
          {h.text || '(空标题)'}
        </button>
      ))}
    </nav>
  )
}
