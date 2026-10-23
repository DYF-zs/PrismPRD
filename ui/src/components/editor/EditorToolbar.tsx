import { useState } from 'react'
import { Button, Divider, Input, Modal, Space, Tooltip, message } from 'antd'
import {
  BoldOutlined,
  ClearOutlined,
  CodeOutlined,
  ItalicOutlined,
  LinkOutlined,
  LineOutlined,
  OrderedListOutlined,
  StrikethroughOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons'
import type { Editor } from '@tiptap/react'
import { useEditorState } from '@tiptap/react'
import type { Level } from '@tiptap/extension-heading'
import { safeDocumentUrl } from './documentExtensions'

interface Props {
  editor: Editor | null
  disabled?: boolean
}

function ToolbarButton({
  tooltip,
  active,
  onClick,
  children,
}: {
  tooltip: string
  active: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <Tooltip title={tooltip}>
      <Button
        size="small"
        aria-label={tooltip}
        type="text"
        style={active ? { background: '#dcfce7', color: '#15803d' } : undefined}
        onMouseDown={(e) => e.preventDefault()}
        onClick={onClick}
      >
        {children}
      </Button>
    </Tooltip>
  )
}

const headingLabels = ['标题 1', '标题 2', '标题 3', '标题 4', '标题 5', '标题 6']

export default function EditorToolbar({ editor, disabled }: Props) {
  const [linkOpen, setLinkOpen] = useState(false)
  const [link, setLink] = useState('')
  useEditorState({ editor, selector: ({ editor: current }) => current ? [
    current.isEditable,
    ...headingLabels.map((_, i) => current.isActive('heading', { level: i + 1 })),
    ...['bold', 'italic', 'strike', 'code', 'blockquote', 'bulletList', 'orderedList'].map(mark => current.isActive(mark)),
  ] : [] })
  if (!editor || !editor.isEditable || disabled) return null
  return (
    <div className="editor-toolbar">
      <Space size={2} wrap>
        {headingLabels.map((label, i) => {
          const level = (i + 1) as Level
          return (
            <ToolbarButton
              key={label}
              tooltip={label}
              active={editor.isActive('heading', { level })}
              onClick={() => editor.chain().focus().toggleHeading({ level }).run()}
            >
              H{i + 1}
            </ToolbarButton>
          )
        })}
        <Divider type="vertical" />
        <ToolbarButton
          tooltip="加粗"
          active={editor.isActive('bold')}
          onClick={() => editor.chain().focus().toggleBold().run()}
        >
          <BoldOutlined />
        </ToolbarButton>
        <ToolbarButton
          tooltip="斜体"
          active={editor.isActive('italic')}
          onClick={() => editor.chain().focus().toggleItalic().run()}
        >
          <ItalicOutlined />
        </ToolbarButton>
        <ToolbarButton
          tooltip="删除线"
          active={editor.isActive('strike')}
          onClick={() => editor.chain().focus().toggleStrike().run()}
        >
          <StrikethroughOutlined />
        </ToolbarButton>
        <ToolbarButton
          tooltip="行内代码"
          active={editor.isActive('code')}
          onClick={() => editor.chain().focus().toggleCode().run()}
        >
          <CodeOutlined />
        </ToolbarButton>
        <Divider type="vertical" />
        <ToolbarButton
          tooltip="引用"
          active={editor.isActive('blockquote')}
          onClick={() => editor.chain().focus().toggleBlockquote().run()}
        >
          <LineOutlined rotate={90} />
        </ToolbarButton>
        <ToolbarButton
          tooltip="无序列表"
          active={editor.isActive('bulletList')}
          onClick={() => editor.chain().focus().toggleBulletList().run()}
        >
          <UnorderedListOutlined />
        </ToolbarButton>
        <ToolbarButton
          tooltip="有序列表"
          active={editor.isActive('orderedList')}
          onClick={() => editor.chain().focus().toggleOrderedList().run()}
        >
          <OrderedListOutlined />
        </ToolbarButton>
        <Divider type="vertical" />
        <ToolbarButton
          tooltip="清除格式"
          active={false}
          onClick={() => editor.chain().focus().unsetAllMarks().clearNodes().run()}
        >
          <ClearOutlined />
        </ToolbarButton>
      </Space>
      <Button size="small" type="text" aria-label="插入或编辑链接" icon={<LinkOutlined />} onClick={() => {
        setLink(String(editor.getAttributes('link').href ?? '')); setLinkOpen(true)
      }}>链接</Button>
      <Modal title="编辑链接" open={linkOpen} onCancel={() => setLinkOpen(false)} okText="保存链接" cancelText="取消"
        onOk={() => {
          const href = safeDocumentUrl(link)
          if (!href) { message.warning('请输入 HTTP、HTTPS、邮件、章节锚点或站内相对地址'); return }
          editor.chain().focus().extendMarkRange('link').setMark('link', { href }).run()
          setLinkOpen(false)
        }}>
        <Input aria-label="链接地址" value={link} onChange={event => setLink(event.target.value)} maxLength={2048} placeholder="https://example.com 或 #章节标题" />
        <Button type="link" danger onClick={() => { editor.chain().focus().extendMarkRange('link').unsetMark('link').run(); setLinkOpen(false) }}>移除链接</Button>
      </Modal>
    </div>
  )
}
