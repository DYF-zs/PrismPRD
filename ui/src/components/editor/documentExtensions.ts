import { Mark, Node } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import CodeBlock from '@tiptap/extension-code-block'
import { Plugin } from '@tiptap/pm/state'
import { headingAnchors } from './documentLinks.ts'

export function safeHtmlAttributes(...sources: Record<string, unknown>[]): Record<string, unknown> {
  const result: Record<string, unknown> = Object.create(null)
  for (const source of sources) for (const [key, value] of Object.entries(source)) {
    if (['__proto__', 'prototype', 'constructor'].includes(key) || /^on/i.test(key)) continue
    result[key] = value
  }
  return result
}

export function safeDocumentUrl(url: unknown): string | null {
  if (typeof url !== 'string') return null
  const value = url.trim()
  if (/[\u0000-\u001f\u007f\\]/.test(value)) return null
  if (/^[a-z][a-z0-9+.-]*:/i.test(value) && !/^(https?:\/\/|mailto:)/i.test(value)) return null
  return value
}

// Keep the editor schema compatible with the persisted Markdown converter's inline table cells.
const Table = Node.create({
  name: 'table', group: 'block', content: 'tableRow+', isolating: true,
  parseHTML: () => [{ tag: 'table' }],
  renderHTML: ({ HTMLAttributes }) => ['table', HTMLAttributes, ['tbody', 0]],
})
const TableRow = Node.create({
  name: 'tableRow', content: '(tableCell | tableHeader)+',
  parseHTML: () => [{ tag: 'tr' }],
  renderHTML: ({ HTMLAttributes }) => ['tr', HTMLAttributes, 0],
})
const cellAttributes = () => ({
  colspan: { default: 1, parseHTML: (element: HTMLElement) => Math.max(1, Number(element.getAttribute('colspan')) || 1) },
  rowspan: { default: 1, parseHTML: (element: HTMLElement) => Math.max(1, Number(element.getAttribute('rowspan')) || 1) },
})
const TableCell = Node.create({
  name: 'tableCell', content: 'inline*', isolating: true,
  addAttributes: cellAttributes,
  parseHTML: () => [{ tag: 'td' }],
  renderHTML: ({ HTMLAttributes }) => ['td', HTMLAttributes, 0],
})
const TableHeader = TableCell.extend({
  name: 'tableHeader',
  parseHTML: () => [{ tag: 'th' }],
  renderHTML: ({ HTMLAttributes }) => ['th', HTMLAttributes, 0],
})
const SafeLink = Mark.create({
  name: 'link', inclusive: false,
  addAttributes: () => ({ href: { default: null }, title: { default: null } }),
  parseHTML: () => [{ tag: 'a[href]', getAttrs: element => {
    const href = safeDocumentUrl((element as HTMLElement).getAttribute('href'))
    return href === null ? false : { href }
  } }],
  renderHTML: ({ HTMLAttributes }) => ['a', safeHtmlAttributes(HTMLAttributes, {
    href: safeDocumentUrl(HTMLAttributes.href), rel: 'noopener noreferrer', target: '_blank',
    title: HTMLAttributes.title ?? '编辑时 Ctrl/⌘ + 单击打开链接；预览时单击打开',
  }), 0],
  addProseMirrorPlugins() {
    return [new Plugin({ props: { handleDOMEvents: { click(view, event) {
      const anchor = event.target instanceof Element ? event.target.closest('a[href]') : null
      if (!anchor || !view.dom.contains(anchor) || event.button !== 0) return false
      const href = safeDocumentUrl(anchor.getAttribute('href'))
      event.preventDefault()
      if (!href || (view.editable && !event.ctrlKey && !event.metaKey)) return false
      if (href.startsWith('#')) {
        let target: string
        try { target = decodeURIComponent(href.slice(1)) } catch { return true }
        const headings = [...view.dom.querySelectorAll('h1,h2,h3,h4,h5,h6')]
        const slugs = headingAnchors(headings.map(heading => heading.textContent ?? ''))
        const found = headings.find((heading, i) => slugs[i] === target || heading.getAttribute('data-block-id') === target)
        found?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      } else window.open(href, '_blank', 'noopener,noreferrer')
      return true
    } } } })]
  },
})
const SafeImage = Node.create({
  name: 'image', inline: true, group: 'inline', draggable: true,
  addAttributes: () => ({ src: { default: null }, alt: { default: null }, title: { default: null } }),
  parseHTML: () => [{ tag: 'img[src]', getAttrs: element => {
    const src = safeDocumentUrl((element as HTMLElement).getAttribute('src'))
    return src === null ? false : { src }
  } }],
  renderHTML: ({ HTMLAttributes }) => ['img', safeHtmlAttributes(HTMLAttributes, {
    src: safeDocumentUrl(HTMLAttributes.src), referrerpolicy: 'no-referrer', loading: 'lazy',
  })],
})

export const documentExtensions = [Table, TableRow, TableCell, TableHeader, SafeLink, SafeImage]
export const editorDocumentExtensions = [
  StarterKit.configure({ link: false, underline: false, trailingNode: false, codeBlock: false }),
  CodeBlock,
  ...documentExtensions,
]
