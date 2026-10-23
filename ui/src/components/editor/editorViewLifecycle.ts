import type { Editor } from '@tiptap/core'

/** TipTap 3 may exist before EditorContent mounts its view (e.g. while PRD detail loads). */
export function bindEditorDomEvent(editor: Editor, event: string, listener: EventListener): () => void {
  let dom: HTMLElement | null = null
  const detach = () => { dom?.removeEventListener(event, listener); dom = null }
  const attach = () => {
    detach()
    try { dom = editor.view.dom } catch { return }
    dom.addEventListener(event, listener)
  }
  editor.on('mount', attach)
  editor.on('unmount', detach)
  attach()
  return () => { detach(); editor.off('mount', attach); editor.off('unmount', detach) }
}
