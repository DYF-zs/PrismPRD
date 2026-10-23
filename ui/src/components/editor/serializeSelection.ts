import type { Editor } from '@tiptap/core'
import type { Node as PMNode } from '@tiptap/pm/model'
import { ANCHORED_TYPES } from './blockId'

/** 简化 Markdown 序列化：支持 bold/italic/code/strike 与标题层级，供 Inline Edit 上下文使用 */

function wrapMarks(text: string, markNames: string[]): string {
  let result = text
  for (const name of markNames) {
    if (name === 'bold') result = `**${result}**`
    else if (name === 'italic') result = `*${result}*`
    else if (name === 'code') result = `\`${result}\``
    else if (name === 'strike') result = `~~${result}~~`
  }
  return result
}

export function rangeToMarkdown(editor: Editor, from: number, to: number): string {
  const { doc } = editor.state
  const parts: string[] = []
  doc.nodesBetween(from, to, (node, pos) => {
    if (node.isText && node.text) {
      // nodesBetween 回调传入整个节点，需按 [from, to) 裁剪出选区切片
      const start = Math.max(from, pos)
      const end = Math.min(to, pos + node.nodeSize)
      if (end > start) {
        parts.push(wrapMarks(node.text.slice(start - pos, end - pos), node.marks.map((m) => m.type.name)))
      }
    }
  })
  return parts.join('')
}

export function blockToMarkdown(node: PMNode): string {
  if (node.type.name === 'heading') {
    const level = Math.max(1, Math.min(6, node.attrs.level ?? 1))
    return `${'#'.repeat(level)} ${inlineOf(node)}`
  }
  return inlineOf(node)
}

function inlineOf(node: PMNode): string {
  const parts: string[] = []
  node.descendants((child) => {
    if (child.isText && child.text) {
      parts.push(wrapMarks(child.text, child.marks.map((m) => m.type.name)))
    }
  })
  return parts.join('')
}

/** 选区/光标所在 Block 及其前后邻近 Block 的信息 */
export interface SelectionContext {
  blockId: string | null
  from: number
  to: number
  selectionMarkdown: string
  blockMarkdown: string
  contextMarkdown: string
}

export function buildSelectionContext(editor: Editor): SelectionContext | null {
  const { state } = editor
  const { from, to } = state.selection
  const doc = state.doc
  const $from = doc.resolve(from)

  // 定位选区所在锚定块：从选区起点向上找；起点在文档层（如全选 AllSelection）则取选区覆盖的第一个锚定块
  let target: { node: PMNode; pos: number } | null = null
  for (let d = $from.depth; d >= 1; d--) {
    if (isAnchoredBlock($from.node(d))) {
      target = { node: $from.node(d), pos: $from.before(d) }
      break
    }
  }
  if (!target) {
    doc.nodesBetween(from, to, (node, pos) => {
      if (target) return false
      if (isAnchoredBlock(node)) {
        target = { node, pos }
        return false
      }
      return true
    })
  }
  if (!target) return null
  const block = target.node
  // 一个 Patch 只定位一个语义 Block，跨块选区需要分块处理。
  if (from < target.pos + 1 || to > target.pos + block.nodeSize - 1) return null

  const blockId = (block.attrs.blockId as string) ?? null
  const $block = doc.resolve(target.pos)
  const index = $block.index($block.depth)
  const parent = $block.parent
  const siblings: PMNode[] = []
  if (index > 0) siblings.push(parent.child(index - 1))
  if (index + 1 < parent.childCount) siblings.push(parent.child(index + 1))

  return {
    blockId,
    from,
    to,
    selectionMarkdown: from === to ? '' : rangeToMarkdown(editor, from, to),
    blockMarkdown: blockToMarkdown(block),
    contextMarkdown: siblings.map(blockToMarkdown).join('\n\n'),
  }
}

function isAnchoredBlock(node: PMNode): boolean {
  return ANCHORED_TYPES.includes(node.type.name)
}
