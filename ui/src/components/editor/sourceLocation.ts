import { Extension } from '@tiptap/core'
import type { Editor } from '@tiptap/core'
import type { Node as PMNode } from '@tiptap/pm/model'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'

export interface SourceLocationRange {
  blockFrom: number
  blockTo: number
  from: number
  to: number
  exact: boolean
}

export const SourceLocationKey = new PluginKey<DecorationSet | null>('sourceLocation')

export function resolveSourceLocation(doc: PMNode, blockId: string, quote?: string | null): SourceLocationRange | null {
  let target: { node: PMNode; pos: number } | undefined
  doc.descendants((node, pos) => {
    if (target) return false
    if (node.attrs.blockId === blockId) { target = { node, pos }; return false }
  })
  if (!target || !blockId) return null
  const { node, pos } = target
  const fallback = { blockFrom: pos, blockTo: pos + node.nodeSize, from: pos, to: pos + node.nodeSize, exact: false }
  if (!quote?.trim()) return fallback

  const blocks: { node: PMNode; pos: number }[] = []
  if (node.isTextblock) blocks.push({ node, pos })
  else node.descendants((child, offset) => {
    if (child.isTextblock) { blocks.push({ node: child, pos: pos + 1 + offset }); return false }
  })
  const matches: { from: number; to: number }[] = []
  for (const block of blocks) {
    let text = ''
    const positions: number[] = []
    block.node.descendants((child, offset) => {
      const content = child.isText ? child.text! : child.isLeaf ? (child.type.name === 'hardBreak' ? '\n' : '\uFFFC') : ''
      for (let i = 0; i < content.length; i++) positions.push(block.pos + 1 + offset + i)
      text += content
    })
    for (let index = text.indexOf(quote); index >= 0; index = text.indexOf(quote, index + 1)) {
      matches.push({ from: positions[index], to: positions[index + quote.length - 1] + 1 })
      if (matches.length > 1) return fallback
    }
  }
  return matches.length === 1 ? { ...fallback, ...matches[0], exact: true } : fallback
}

export function createSourceLocationPlugin(duration = 3000) {
  return new Plugin<DecorationSet | null>({
    key: SourceLocationKey,
    state: {
      init: () => null,
      apply(transaction, previous) {
        const range: SourceLocationRange | null | undefined = transaction.getMeta(SourceLocationKey)
        if (transaction.docChanged) return null
        if (range === undefined) return previous
        if (!range) return null
        return DecorationSet.create(transaction.doc, [range.exact
          ? Decoration.inline(range.from, range.to, { class: 'source-location-text' })
          : Decoration.node(range.blockFrom, range.blockTo, { class: 'source-location-block' })])
      },
    },
    props: { decorations: state => SourceLocationKey.getState(state) ?? DecorationSet.empty },
    view() {
      let timer: ReturnType<typeof setTimeout> | undefined
      return {
        update(view, previousState) {
          const current = SourceLocationKey.getState(view.state)
          if (current === SourceLocationKey.getState(previousState)) return
          clearTimeout(timer)
          if (current) timer = setTimeout(() => {
            if (!view.isDestroyed) view.dispatch(view.state.tr.setMeta(SourceLocationKey, null).setMeta('addToHistory', false))
          }, duration)
        },
        destroy() { clearTimeout(timer) },
      }
    },
  })
}

export const SourceLocation = Extension.create({
  name: 'sourceLocation',
  addProseMirrorPlugins: () => [createSourceLocationPlugin()],
})

export function locateSourceInEditor(editor: Editor, blockId: string, quote: string | null | undefined, container: HTMLElement | null) {
  const range = resolveSourceLocation(editor.state.doc, blockId, quote)
  if (!range) return false
  editor.view.dispatch(editor.state.tr.setMeta(SourceLocationKey, range).setMeta('addToHistory', false))
  const element = editor.view.dom.querySelector('.source-location-text, .source-location-block')
  if (element && container) {
    const bounds = element.getBoundingClientRect()
    const margin = Math.max(16, (container.clientHeight - bounds.height) / 2)
    container.scrollTo({
      top: container.scrollTop + bounds.top - container.getBoundingClientRect().top - margin,
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
    })
  }
  return true
}
