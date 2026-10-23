import { Extension } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { DOMSerializer, Fragment, Node as PMNode } from '@tiptap/pm/model'
import type { EditorState, Transaction } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'

export interface InlineAIProposal {
  scope: 'INLINE' | 'BLOCK'
  operation: 'REPLACE' | 'INSERT_BEFORE' | 'INSERT_AFTER' | 'DELETE'
  targetBlockId: string | null
  sourceQuote: string | null
  newContentJson: unknown[]
}

export interface InlineAIPluginState {
  /** compose: 输入指令中；preview: 展示 Diff 预览；idle: 关闭 */
  mode: 'idle' | 'compose' | 'generating' | 'preview'
  /** 正在处理的目标选区（INLINE 模式淡红高亮） */
  range: { from: number; to: number } | null
  /** BLOCK 模式目标 Block 的 blockId（定位插入 widget） */
  targetBlockId: string | null
  proposal: InlineAIProposal | null
}

export const InlineAIKey = new PluginKey<InlineAIPluginState>('inlineAI')

const INITIAL: InlineAIPluginState = {
  mode: 'idle',
  range: null,
  targetBlockId: null,
  proposal: null,
}

function blockPos(doc: PMNode, blockId: string | null): number | null {
  if (!blockId) return null
  let found: number | null = null
  doc.descendants((node, pos) => {
    if (found != null) return false
    if (node.attrs.blockId === blockId) {
      found = pos
      return false
    }
    return true
  })
  return found
}

function renderNodes(state: EditorState, nodes: unknown[]): HTMLElement {
  const container = document.createElement('span')
  container.className = 'inlineai-new-content'
  try {
    const fragment = Fragment.fromJSON(state.schema, nodes)
    const dom = DOMSerializer.fromSchema(state.schema).serializeFragment(fragment)
    container.appendChild(dom)
  } catch {
    container.textContent = ''
  }
  return container
}

function buildDecorations(state: EditorState, ai: InlineAIPluginState): DecorationSet {
  const { doc } = state
  const decos: Decoration[] = []

  if (ai.mode === 'compose' || ai.mode === 'generating') {
    if (ai.range && ai.range.from < ai.range.to) {
      decos.push(Decoration.inline(ai.range.from, ai.range.to, { class: 'inlineai-old-text' }))
    }
    return DecorationSet.create(doc, decos)
  }

  if (ai.mode === 'preview' && ai.proposal) {
    const p = ai.proposal
    if (p.scope === 'INLINE') {
      if (ai.range && ai.range.from < ai.range.to) {
        decos.push(Decoration.inline(ai.range.from, ai.range.to, { class: 'inlineai-old-text' }))
      }
      if (p.operation === 'REPLACE' || p.operation === 'DELETE') {
        const at = ai.range ? ai.range.to : null
        if (at != null) {
          const widget = renderNodes(state, p.newContentJson)
          widget.classList.add('inlineai-new-inline')
          decos.push(Decoration.widget(at, () => widget, { side: 1 }))
        }
      } else if (p.operation === 'INSERT_BEFORE' || p.operation === 'INSERT_AFTER') {
        const at = ai.range ? (p.operation === 'INSERT_BEFORE' ? ai.range.from : ai.range.to) : null
        if (at != null) {
          const widget = renderNodes(state, p.newContentJson)
          widget.classList.add('inlineai-new-inline')
          decos.push(Decoration.widget(at, () => widget, { side: 1 }))
        }
      }
    } else {
      const pos = blockPos(doc, p.targetBlockId)
      if (pos != null) {
        const target = doc.nodeAt(pos)
        if (target) {
          const at = p.operation === 'INSERT_BEFORE' || p.operation === 'REPLACE' || p.operation === 'DELETE'
            ? pos
            : pos + target.nodeSize
          if (p.operation === 'REPLACE' || p.operation === 'DELETE') {
            decos.push(Decoration.node(pos, pos + target.nodeSize, { class: 'inlineai-old-block' }, {}))
          }
          const wrap = document.createElement('div')
          wrap.className = 'inlineai-new-block'
          const inner = renderNodes(state, p.newContentJson)
          if (inner.childNodes.length > 0) wrap.appendChild(inner)
          decos.push(Decoration.widget(at, () => wrap, { side: 1 }))
        }
      }
    }
  }
  return DecorationSet.create(doc, decos)
}

/** Inline AI 状态以 transaction meta 驱动，React 侧 dispatch 更新，插件派生 Diff 装饰 */
export const InlineAI = Extension.create({
  name: 'inlineAI',

  addProseMirrorPlugins() {
    return [
      new Plugin<InlineAIPluginState>({
        key: InlineAIKey,
        state: {
          init: () => INITIAL,
          apply(tr: Transaction, prev: InlineAIPluginState): InlineAIPluginState {
            const meta = tr.getMeta(InlineAIKey)
            if (meta) return meta as InlineAIPluginState
            if (tr.docChanged && prev.mode !== 'idle') {
              // 文档变化时保持状态但失效选区范围（Accept/Reject 由 React 侧显式清理）
              return { ...prev, range: null, proposal: null, mode: prev.mode === 'preview' ? 'idle' : prev.mode }
            }
            return prev
          },
        },
        props: {
          decorations(state: EditorState) {
            const ai = InlineAIKey.getState(state)
            if (!ai || ai.mode === 'idle') return DecorationSet.empty
            return buildDecorations(state, ai)
          },
        },
      }),
    ]
  },
})
