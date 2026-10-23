import { Extension } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import type { JSONContent } from '@tiptap/react'

export const ANCHORED_TYPES = [
  'heading',
  'paragraph',
  'blockquote',
  'codeBlock',
  'listItem',
  'tableRow',
  'horizontalRule',
]

const pluginKey = new PluginKey('blockIdAssign')

function randomId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export const BlockId = Extension.create({
  name: 'blockId',

  addGlobalAttributes() {
    return [
      {
        types: ANCHORED_TYPES,
        attributes: {
          blockId: {
            default: null,
            keepOnSplit: false,
            parseHTML: (element) => element.getAttribute('data-block-id'),
            renderHTML: (attributes) =>
              attributes.blockId ? { 'data-block-id': attributes.blockId } : {},
          },
        },
      },
    ]
  },

  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: pluginKey,
        appendTransaction: (transactions, _oldState, newState) => {
          if (!transactions.some((tr) => tr.docChanged)) return null
          if (transactions.some((tr) => tr.getMeta(pluginKey))) return null
          const tr = newState.tr
          const seen = new Set<string>()
          let modified = false
          newState.doc.descendants((node, pos) => {
            if (!ANCHORED_TYPES.includes(node.type.name)) return
            const id = node.attrs.blockId
            if (id && !seen.has(id)) {
              seen.add(id)
              return
            }
            tr.setNodeMarkup(pos, undefined, { ...node.attrs, blockId: randomId() })
            modified = true
          })
          return modified ? tr.setMeta(pluginKey, true) : null
        },
      }),
    ]
  },
})

export function normalizeBlockIds(json: JSONContent): JSONContent {
  const seen = new Set<string>()
  const walk = (node: JSONContent): JSONContent => {
    const clone: JSONContent = { ...node }
    if (ANCHORED_TYPES.includes(node.type ?? '')) {
      const attrs = { ...(node.attrs ?? {}) }
      const id = attrs.blockId
      if (!id || seen.has(id)) {
        attrs.blockId = randomId()
      }
      seen.add(attrs.blockId)
      clone.attrs = attrs
    }
    if (Array.isArray(node.content)) {
      clone.content = node.content.map(walk)
    }
    return clone
  }
  return walk(json)
}
