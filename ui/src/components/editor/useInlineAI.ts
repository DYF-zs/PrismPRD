import { useCallback, useEffect, useRef, useState } from 'react'
import type { Editor } from '@tiptap/core'
import { message } from 'antd'
import { generateInlineEdit } from '../../api/inlineEdit'
import { InlineAIKey } from './inlineAI'
import type { InlineAIPluginState } from './inlineAI'
import { buildSelectionContext } from './serializeSelection'
import type { SelectionContext } from './serializeSelection'
import { applyPatch, rejectPatch } from '../../api/patch'
import { InlineRequestGuard } from './inlineRequestGuard'
import { bindEditorDomEvent } from './editorViewLifecycle'

export type InlineAIMode = 'idle' | 'compose' | 'generating' | 'preview' | 'applying'

export interface UseInlineAI {
  mode: InlineAIMode
  instruction: string
  reason: string | null
  anchorRect: { top: number; left: number } | null
  askButtonRect: { top: number; left: number } | null
  open: () => void
  close: () => void
  setInstruction: (v: string) => void
  submit: () => Promise<void>
  regenerate: () => Promise<void>
  accept: () => Promise<void>
  reject: () => void
}

export function useInlineAI(editor: Editor | null, prdId: string, editorWrap: React.RefObject<HTMLDivElement | null>,
  options: { prepareDraft: () => Promise<number>; onApplied: () => Promise<void> }): UseInlineAI {
  const [mode, setMode] = useState<InlineAIMode>('idle')
  const [instruction, setInstruction] = useState('')
  const [reason, setReason] = useState<string | null>(null)
  const [anchorRect, setAnchorRect] = useState<{ top: number; left: number } | null>(null)
  const [askButtonRect, setAskButtonRect] = useState<{ top: number; left: number } | null>(null)
  const ctxRef = useRef<SelectionContext | null>(null)
  const attemptRef = useRef(1)
  const instructionRef = useRef('')
  const modeRef = useRef<InlineAIMode>(mode)
  const patchIdRef = useRef<string | null>(null)
  const guardRef = useRef(new InlineRequestGuard())
  const optionsRef = useRef(options)
  optionsRef.current = options
  modeRef.current = mode

  const dispatchAI = useCallback(
    (state: Partial<InlineAIPluginState>) => {
      if (!editor) return
      const prev = InlineAIKey.getState(editor.state) ?? { mode: 'idle', range: null, targetBlockId: null, proposal: null }
      editor.view.dispatch(editor.state.tr.setMeta(InlineAIKey, { ...prev, ...state }))
    },
    [editor],
  )

  const computeAnchor = useCallback(() => {
    if (!editor || !editorWrap.current) return
    const wrap = editorWrap.current
    try {
      const from = ctxRef.current?.from ?? editor.state.selection.from
      const coords = editor.view.coordsAtPos(from)
      const wrapRect = wrap.getBoundingClientRect()
      // coordsAtPos 是视口坐标，绝对定位在滚动容器内需换算为内容坐标
      setAnchorRect({
        top: coords.top - wrapRect.top + wrap.scrollTop + 24,
        left: Math.max(12, coords.left - wrapRect.left + wrap.scrollLeft),
      })
    } catch {
      setAnchorRect({ top: 80, left: 40 })
    }
  }, [editor, editorWrap])

  const open = useCallback(() => {
    if (!editor) return
    const ctx = buildSelectionContext(editor)
    if (!ctx) {
      message.info('请在正文段落处使用 Inline AI')
      return
    }
    if (!editor.isEditable || modeRef.current !== 'idle') return
    ctxRef.current = ctx
    attemptRef.current = 1
    setInstruction('')
    instructionRef.current = ''
    setReason(null)
    setMode('compose')
    dispatchAI({ mode: 'compose', range: { from: ctx.from, to: ctx.to }, targetBlockId: ctx.blockId, proposal: null })
    // 等 decorations 挂上后计算锚点位置
    requestAnimationFrame(() => computeAnchor())
  }, [editor, dispatchAI, computeAnchor])

  const close = useCallback(() => {
    if (modeRef.current === 'applying') return
    guardRef.current.invalidate()
    const pending = patchIdRef.current
    patchIdRef.current = null
    if (pending) void rejectPatch(pending).catch(e => message.error((e as Error).message))
    ctxRef.current = null
    modeRef.current = 'idle'
    setMode('idle')
    setInstruction('')
    setReason(null)
    setAnchorRect(null)
    dispatchAI({ mode: 'idle', range: null, targetBlockId: null, proposal: null })
  }, [dispatchAI])

  const generate = useCallback(async (attempt: number) => {
    const ctx = ctxRef.current
    if (!editor || !ctx || !instructionRef.current.trim()) return
    if (modeRef.current === 'generating' || modeRef.current === 'applying') return
    const request = guardRef.current.begin(editor.state.doc)
    modeRef.current = 'generating'
    setMode('generating')
    try {
      const previous = patchIdRef.current
      patchIdRef.current = null
      if (previous) await rejectPatch(previous)
      const revision = await optionsRef.current.prepareDraft()
      if (!guardRef.current.isCurrent(request, editor.state.doc)) return
      const resp = await generateInlineEdit(prdId, {
        instruction: instructionRef.current.trim(),
        targetBlockId: ctx.blockId,
        selectionMarkdown: ctx.selectionMarkdown,
        blockMarkdown: ctx.blockMarkdown,
        contextMarkdown: ctx.contextMarkdown,
        attempt,
        baseDraftRevision: revision,
        selectionText: editor.state.doc.textBetween(ctx.from, ctx.to, '\n'),
      })
      if (!guardRef.current.isCurrent(request, editor.state.doc)) {
        await rejectPatch(resp.patchId)
        return
      }
      patchIdRef.current = resp.patchId
      dispatchAI({
        mode: 'preview',
        range: { from: ctx.from, to: ctx.to },
        targetBlockId: resp.targetBlockId ?? ctx.blockId,
        proposal: {
          scope: resp.scope,
          operation: resp.operation,
          targetBlockId: resp.targetBlockId ?? ctx.blockId,
          sourceQuote: resp.sourceQuote,
          newContentJson: resp.newContentJson ?? [],
        },
      })
      setReason(resp.reason)
      modeRef.current = 'preview'
      setMode('preview')
      requestAnimationFrame(() => computeAnchor())
    } catch (e) {
      if (!guardRef.current.isCurrent(request, editor.state.doc)) return
      message.error((e as Error).message)
      modeRef.current = 'compose'
      setMode('compose')
    }
  }, [editor, prdId, dispatchAI, computeAnchor])

  const submit = useCallback(async () => {
    if (!instructionRef.current.trim()) return
    attemptRef.current = 1
    await generate(1)
  }, [generate])

  const regenerate = useCallback(async () => {
    attemptRef.current += 1
    await generate(attemptRef.current)
  }, [generate])

  const accept = useCallback(async () => {
    if (!editor || !editor.isEditable) return
    const ctx = ctxRef.current
    const state = InlineAIKey.getState(editor.state)
    const patchId = patchIdRef.current
    if (!ctx || !state?.proposal || !patchId || modeRef.current !== 'preview') return
    modeRef.current = 'applying'
    setMode('applying')
    editor.setEditable(false, false)
    let applied = false
    let synchronized = false
    try {
      await applyPatch(patchId)
      applied = true
      patchIdRef.current = null
      await optionsRef.current.onApplied()
      synchronized = true
      modeRef.current = 'preview'
      close()
      message.success('AI 修改已应用并保存')
    } catch (e) {
      modeRef.current = 'preview'
      setMode('preview')
      message.error(applied
        ? `修改已保存，加载失败，请刷新页面：${(e as Error).message}`
        : `应用失败：${(e as Error).message}`)
      if (applied || (e as { isConflict?: boolean }).isConflict) close()
    } finally {
      // 提交成功且重新加载失败时保持编辑器只读，等待正文同步。
      if (!editor.isDestroyed && (!applied || synchronized)) editor.setEditable(true, false)
    }
  }, [editor, close])

  const reject = useCallback(() => {
    close()
  }, [close])

  useEffect(() => {
    if (!editor) return
    const invalidate = ({ transaction }: { transaction: { docChanged: boolean } }) => {
      if (transaction.docChanged && modeRef.current !== 'idle' && modeRef.current !== 'applying') {
        close()
        message.info('正文已变化，请基于最新选区重新生成 AI 建议')
      }
    }
    editor.on('transaction', invalidate)
    return () => {
      editor.off('transaction', invalidate)
      guardRef.current.invalidate()
      const pending = patchIdRef.current
      patchIdRef.current = null
      if (pending) void rejectPatch(pending).catch(() => {})
    }
  }, [editor, prdId, close])

  // Ctrl+K / Cmd+K 唤起；Esc 关闭
  useEffect(() => {
    if (!editor) return
    const onKeydown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        open()
      } else if (e.key === 'Escape' && mode !== 'idle') {
        close()
      }
    }
    return bindEditorDomEvent(editor, 'keydown', onKeydown as EventListener)
  }, [editor, open, close, mode])

  // 非空选区时显示「Ask AI」浮动按钮（仅 idle 模式）
  useEffect(() => {
    if (!editor) return
    const update = () => {
      if (modeRef.current !== 'idle') {
        setAskButtonRect(null)
        return
      }
      const { from, to, empty } = editor.state.selection
      if (empty || from === to) {
        setAskButtonRect(null)
        return
      }
      if (!editorWrap.current) return
      try {
        const wrap = editorWrap.current
        const start = editor.view.coordsAtPos(from)
        const end = editor.view.coordsAtPos(to)
        const wrapRect = wrap.getBoundingClientRect()
        const top = Math.max(8, Math.min(start.top, end.top) - wrapRect.top + wrap.scrollTop - 34)
        const left = Math.max(8, Math.min(start.left, end.left) - wrapRect.left + wrap.scrollLeft)
        setAskButtonRect({
          top: Math.min(top, wrap.scrollHeight - 32),
          left: Math.min(left, Math.max(8, wrap.scrollWidth - 110)),
        })
      } catch {
        setAskButtonRect(null)
      }
    }
    editor.on('selectionUpdate', update)
    return () => {
      editor.off('selectionUpdate', update)
    }
  }, [editor, editorWrap])

  // 模式切换时隐藏选区按钮
  useEffect(() => {
    if (mode !== 'idle') setAskButtonRect(null)
  }, [mode])

  return {
    mode,
    instruction,
    reason,
    anchorRect,
    askButtonRect,
    open,
    close,
    setInstruction: (v) => { instructionRef.current = v; setInstruction(v) },
    submit,
    regenerate,
    accept,
    reject,
  }
}
