import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import {
  Button,
  Input,
  Modal,
  Space,
  Spin,
  Tooltip,
  Typography,
  message,
  Upload,
} from 'antd'
import {
  ArrowLeftOutlined,
  CommentOutlined,
  LoadingOutlined,
  SaveOutlined,
  UploadOutlined,
  MenuOutlined,
  CloseOutlined,
  HistoryOutlined,
  ExperimentOutlined,
  DatabaseOutlined,
} from '@ant-design/icons'
import { useParams, useNavigate, useBlocker, useLocation, useSearchParams } from 'react-router-dom'
import { EditorContent, useEditor } from '@tiptap/react'
import type { JSONContent } from '@tiptap/react'
import { editorDocumentExtensions } from '../components/editor/documentExtensions'
import { DiagramCodeBlock } from '../components/editor/DiagramCodeBlock'
import dayjs from 'dayjs'
import { ApiError } from '../api/client'
import { fetchMe, importMarkdown, fetchPrdDetail, saveDraft, updatePrd } from '../api/prd'
import { createReview } from '../api/review'
import { deleteVersion, useVersion } from '../api/version'
import type { PrdDetailResponse, VersionSummary } from '../api/types'
import { BlockId, normalizeBlockIds } from '../components/editor/blockId'
import { InlineAI } from '../components/editor/inlineAI'
import { SourceLocation, locateSourceInEditor, resolveSourceLocation } from '../components/editor/sourceLocation'
import { useInlineAI } from '../components/editor/useInlineAI'
import InlineAIController from '../components/editor/InlineAIController'
import EditorToolbar from '../components/editor/EditorToolbar'
import Outline from '../components/editor/Outline'
import VersionPanel from '../components/VersionPanel'
import TestCasePanel from '../components/TestCasePanel'
import { proposeAcceptance } from '../api/testcase'
import { applyPatch } from '../api/patch'
import ChatPanel from '../components/ChatPanel'
import StartReviewModal from '../components/review/StartReviewModal'
import ReviewReportDrawer from '../components/review/ReviewReportDrawer'
import KnowledgePanel from '../components/KnowledgePanel'
import { importPrdFile } from '../api/knowledge'
import PanelResizeHandle from '../components/workspace/PanelResizeHandle'
import { usePanelLayout } from '../components/workspace/usePanelLayout'

type SaveState = 'saved' | 'dirty' | 'saving' | 'conflict'

const AUTOSAVE_DELAY_MS = 1500

export default function PrdWorkspacePage() {
  const { prdId = '' } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const chatView = searchParams.get('view') === 'chat'
  const startSessionId = (location.state as { startSessionId?: string } | null)?.startSessionId
  const [initialSessionId] = useState(startSessionId)

  const [detail, setDetail] = useState<PrdDetailResponse | null>(null)
  const [currentUserId, setCurrentUserId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saveState, setSaveState] = useState<SaveState>('saved')
  const [lastSavedAt, setLastSavedAt] = useState<string | null>(null)
  const [importOpen, setImportOpen] = useState(false)
  const [importText, setImportText] = useState('')
  const [importFile, setImportFile] = useState<File | null>(null)
  const [importing, setImporting] = useState(false)
  const [chatCollapsed, setChatCollapsed] = useState(() => !chatView && window.innerWidth <= 1100)
  const [chatMounted, setChatMounted] = useState(() => !chatCollapsed)
  const [outlineCollapsed, setOutlineCollapsed] = useState(() => window.innerWidth <= 1100)
  const [reviewDrawerId, setReviewDrawerId] = useState<string | null>(null)
  const [reviewDrawerOpen, setReviewDrawerOpen] = useState(false)
  const [reviewStarting, setReviewStarting] = useState(false)
  const [reviewTarget, setReviewTarget] = useState<{ id: string; versionNo: number; rubricId?: string } | null>(null)
  const reviewStartingRef = useRef(false)
  const [reviewRefreshKey, setReviewRefreshKey] = useState(0)
  const [draftBusy, setDraftBusy] = useState(false)
  const [preview, setPreview] = useState(false)
  const [toolPanel, setToolPanel] = useState<'knowledge' | 'tests' | 'versions' | null>(() => window.innerWidth >= 1400 ? 'versions' : null)
  const mutationRef = useRef(false)
  const mountedRef = useRef(true)
  const layout = usePanelLayout({ resources: toolPanel !== null, outline: !outlineCollapsed, chat: !chatCollapsed, conversation: chatView })

  useEffect(() => {
    let alive = true
    fetchMe().then(me => { if (alive) setCurrentUserId(me.userId) }).catch(() => {})
    return () => { alive = false }
  }, [])

  useEffect(() => { if (!chatCollapsed) setChatMounted(true) }, [chatCollapsed])

  useEffect(() => {
    const compact = window.matchMedia('(max-width: 1100px)')
    const adapt = () => {
      if (!compact.matches) return
      setOutlineCollapsed(true)
      setToolPanel(null)
      if (!chatView) setChatCollapsed(true)
    }
    compact.addEventListener('change', adapt)
    return () => compact.removeEventListener('change', adapt)
  }, [chatView])

  useEffect(() => {
    const closePanel = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || [...document.querySelectorAll('.ant-modal-wrap, .ant-drawer-open')].some(element => getComputedStyle(element).display !== 'none')) return
      if (toolPanel && !layout.inline.resources) setToolPanel(null)
      else if (!chatCollapsed && !chatView && !layout.inline.chat) setChatCollapsed(true)
      else if (!outlineCollapsed && !layout.inline.outline) setOutlineCollapsed(true)
    }
    window.addEventListener('keydown', closePanel)
    return () => window.removeEventListener('keydown', closePanel)
  }, [toolPanel, chatCollapsed, chatView, outlineCollapsed, layout.inline.resources, layout.inline.chat, layout.inline.outline])

  const revisionRef = useRef(0)
  const savingRef = useRef(false)
  const savePromiseRef = useRef<Promise<boolean> | null>(null)
  const changeSerialRef = useRef(0)
  const savedSerialRef = useRef(0)
  const conflictRef = useRef(false)
  const loadingContentRef = useRef(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const editorWrapRef = useRef<HTMLDivElement>(null)
  const [sourceLocation, setSourceLocation] = useState<{ prdId: string; blockId: string; quote?: string | null } | null>(null)

  const editor = useEditor({
    extensions: [...editorDocumentExtensions.map(extension => extension.name === 'codeBlock' ? DiagramCodeBlock : extension), BlockId, InlineAI, SourceLocation],
    content: { type: 'doc', content: [{ type: 'paragraph' }] },
    onUpdate: ({ transaction }) => { if (transaction.docChanged) changeRef.current() },
  })

  const applyServerContent = useCallback(
    (d: PrdDetailResponse) => {
      const json = JSON.parse(d.draftJson) as JSONContent
      loadingContentRef.current = true
      try {
        editor?.commands.setContent(normalizeBlockIds(json), { emitUpdate: false, errorOnInvalidContent: true })
      } finally { loadingContentRef.current = false }
      revisionRef.current = d.revision
      savedSerialRef.current = changeSerialRef.current
      conflictRef.current = false
      setSaveState('saved')
    },
    [editor],
  )

  const doSave = useCallback((): Promise<boolean> => {
    if (savePromiseRef.current) return savePromiseRef.current
    if (!editor || editor.isDestroyed || !mountedRef.current || conflictRef.current) return Promise.resolve(false)
    const serial = changeSerialRef.current
    const content = editor.getJSON()
    savingRef.current = true
    setSaveState('saving')
    const pending = (async () => {
    try {
      const resp = await saveDraft(prdId, revisionRef.current, content)
      revisionRef.current = resp.revision
      savedSerialRef.current = serial
      setLastSavedAt(resp.updatedAt)
      setSaveState(changeSerialRef.current === serial ? 'saved' : 'dirty')
      return true
    } catch (e) {
      if (e instanceof ApiError && e.isConflict) {
        conflictRef.current = true
        setSaveState('conflict')
        showConflictModal()
      } else {
        setSaveState('dirty')
        message.error((e as Error).message)
      }
      return false
    } finally {
      savingRef.current = false
      savePromiseRef.current = null
      if (mountedRef.current && !conflictRef.current && !mutationRef.current && changeSerialRef.current > serial) {
        if (debounceRef.current) clearTimeout(debounceRef.current)
        debounceRef.current = setTimeout(() => { debounceRef.current = null; void doSave() }, AUTOSAVE_DELAY_MS)
      }
    }
    })()
    savePromiseRef.current = pending
    return pending
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editor, prdId])

  const showConflictModal = () => {
    Modal.confirm({
      title: '草稿已被其他会话修改',
      content: <Space direction="vertical" style={{ width: '100%' }}>
        <span>本地修改已保留，但不能覆盖服务器的新内容。可先复制下方本地正文；重新加载会用服务器内容替换本地草稿。</span>
        <Input.TextArea aria-label="冲突时的本地正文备份" readOnly value={editor?.getText() ?? ''} rows={5} />
      </Space>,
      okText: '重新加载',
      cancelText: '稍后处理',
      onOk: async () => {
        const d = await fetchPrdDetail(prdId)
        setDetail(d)
        applyServerContent(d)
        message.success('已加载最新内容')
      },
    })
  }

  const changeRef = useRef(() => {})
  changeRef.current = () => {
    if (loadingContentRef.current || !mountedRef.current) return
    changeSerialRef.current += 1
    if (conflictRef.current) return
    if (!savingRef.current) setSaveState('dirty')
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => {
      debounceRef.current = null
      void doSave()
    }, AUTOSAVE_DELAY_MS)
  }

  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false }
  }, [])

  useEffect(() => {
    if (!editor || !prdId) return
    let alive = true
    setLoading(true)
    fetchPrdDetail(prdId)
      .then((d) => {
        if (!alive) return
        setDetail(d)
        applyServerContent(d)
      })
      .catch((e) => {
        if (!alive) return
        message.error((e as Error).message)
        navigate('/')
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
      if (debounceRef.current) {
        clearTimeout(debounceRef.current)
        debounceRef.current = null
      }
    }
  }, [editor, prdId, applyServerContent, navigate])

  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (saveState === 'dirty' || saveState === 'saving' || saveState === 'conflict' || mutationRef.current) {
        e.preventDefault()
        e.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [saveState])

  const ensureSaved = useCallback(async () => {
    if (conflictRef.current) throw new Error('请先解决草稿保存冲突')
    if (savePromiseRef.current || changeSerialRef.current !== savedSerialRef.current) {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current)
        debounceRef.current = null
      }
      if (!await doSave()) throw new Error('草稿保存失败，请重试后继续')
      if (changeSerialRef.current !== savedSerialRef.current) {
        throw new Error('保存期间正文已变化，请保存最新内容后继续')
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [doSave])

  const blocker = useBlocker(({ currentLocation, nextLocation }) => currentLocation.pathname !== nextLocation.pathname
    && (mutationRef.current || conflictRef.current || savingRef.current || changeSerialRef.current !== savedSerialRef.current))
  useEffect(() => {
    if (blocker.state !== 'blocked') return
    const modal = Modal.confirm({
      title: '离开前保存草稿？',
      content: '将先保存本地修改，保存成功后离开。存在冲突或正在写入时，请留在页面处理。',
      okText: '保存并离开', cancelText: '留在页面',
      onOk: async () => {
        if (mutationRef.current) { message.warning('正文正在写入，请稍后再离开'); throw new Error('正文正在写入') }
        await ensureSaved()
        blocker.proceed()
      },
      onCancel: () => blocker.reset(),
    })
    return () => modal.destroy()
  }, [blocker, ensureSaved])

  const refreshMeta = useCallback(async () => {
    try {
      setDetail(await fetchPrdDetail(prdId))
    } catch {
      // 忽略元信息刷新失败
    }
  }, [prdId])

  const openReview = useCallback((reviewId: string) => {
    setReviewDrawerId(reviewId)
    setReviewDrawerOpen(true)
  }, [])

  /** 正文提交成功后加载服务器最新草稿。 */
  const reloadAfterPatch = useCallback(async () => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current)
      debounceRef.current = null
    }
    const d = await fetchPrdDetail(prdId)
    setDetail(d)
    applyServerContent(d)
    setReviewRefreshKey((k) => k + 1)
  }, [prdId, applyServerContent])

  /** Serialize all server-side draft replacements; freeze editing until the committed content is loaded. */
  const mutateDraft = async <T,>(write: () => Promise<T>, allowPreview = false): Promise<T> => {
    if (!editor || mutationRef.current) throw new Error('请等待当前正文操作结束')
    if (!editor.isEditable && !allowPreview) throw new Error('请先切换到编辑模式再修改正文')
    const wasEditable = editor.isEditable
    mutationRef.current = true
    setDraftBusy(true)
    editor.setEditable(false, false)
    let committed = false, synchronized = false
    try {
      await ensureSaved()
      const result = await write()
      committed = true
      await reloadAfterPatch()
      synchronized = true
      return result
    } catch (e) {
      if (committed) message.error('修改已保存，加载失败。请刷新页面后继续，避免覆盖已保存的正文。')
      throw e
    } finally {
      mutationRef.current = false
      setDraftBusy(false)
      if (!editor.isDestroyed && (!committed || synchronized)) editor.setEditable(wasEditable, false)
    }
  }

  const applyDraftPatch = (patchId: string) => mutateDraft(() => applyPatch(patchId))

  const inlineAI = useInlineAI(editor, prdId, editorWrapRef, {
    prepareDraft: async () => { await ensureSaved(); return revisionRef.current },
    onApplied: reloadAfterPatch,
  })

  const startReview = (version: VersionSummary, rubricId?: string) => {
    if (reviewStartingRef.current) return
    setReviewTarget({ id: version.id, versionNo: version.versionNo, rubricId })
  }

  const confirmReview = async (rubricId: string) => {
    if (!reviewTarget || reviewStartingRef.current) return
    const target = reviewTarget
    reviewStartingRef.current = true
    setReviewStarting(true)
    try {
      const job = await createReview(target.id, rubricId)
      setReviewTarget(null)
      setReviewRefreshKey((k) => k + 1)
      openReview(job.id)
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      reviewStartingRef.current = false
      setReviewStarting(false)
    }
  }

  const locateBlock = (blockId: string, quote?: string | null) => {
    if (!editor || editor.isDestroyed || !resolveSourceLocation(editor.state.doc, blockId, quote)) {
      message.info('当前草稿中未找到该内容，原文可能已被修改')
      return
    }
    if (chatView) { const next = new URLSearchParams(searchParams); next.delete('view'); setSearchParams(next) }
    if (window.innerWidth <= 1100) setChatCollapsed(true)
    if (!layout.inline.resources) setToolPanel(null)
    if (!layout.inline.outline) setOutlineCollapsed(true)
    setReviewDrawerOpen(false)
    setSourceLocation({ prdId, blockId, quote })
  }

  useLayoutEffect(() => {
    if (!sourceLocation || !editor || editor.isDestroyed || chatView || loading) return
    if (sourceLocation.prdId === prdId && !locateSourceInEditor(editor, sourceLocation.blockId, sourceLocation.quote, editorWrapRef.current)) {
      message.info('原文位置已变化，请基于当前草稿重新生成建议')
    }
    setSourceLocation(null)
  }, [sourceLocation, editor, chatView, loading, prdId])

  const doImport = async () => {
    if (mutationRef.current) return
    if (!importFile && !importText.trim()) {
      message.warning('请选择文件或粘贴 Markdown 内容')
      return
    }
    setImporting(true)
    try {
      const resp = await mutateDraft(() => importFile
        ? importPrdFile(prdId, revisionRef.current, importFile)
        : importMarkdown(prdId, revisionRef.current, importText.trim()))
      setLastSavedAt(resp.updatedAt)
      setImportOpen(false)
      setImportText('')
      setImportFile(null)
      message.success('导入成功，草稿已更新')
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setImporting(false)
    }
  }

  const acceptCriteria = async (versionId: string, generationId: string, index: number) => {
    try {
      await mutateDraft(async () => {
        const patch = await proposeAcceptance(versionId, generationId, index, revisionRef.current)
        if (patch.status !== 'APPLIED') await applyPatch(patch.id)
      })
      message.success('验收标准已写入并保存到草稿')
    } catch (e) {
      message.error((e as Error).message)
      throw e
    }
  }

  const doUseVersion = (version: VersionSummary) => {
    if (mutationRef.current || inlineAI.mode !== 'idle' || reviewStartingRef.current) return
    Modal.confirm({
      title: `使用 v${version.versionNo} 继续编辑？`,
      content: '正文将恢复为该版本内容，并将其设为草稿基线、展示对应评分。其他版本保留。当前草稿需要保留为历史时，请先创建版本。',
      okText: '使用此版本',
      cancelText: '取消',
      onOk: async () => {
        try {
          if (inlineAI.mode !== 'idle' || reviewStartingRef.current) throw new Error('请等待当前操作完成')
          await mutateDraft(() => useVersion(version.id, revisionRef.current), true)
          setReviewDrawerOpen(false)
          setReviewDrawerId(null)
          message.success(`已使用 v${version.versionNo}`)
        } catch (e) {
          message.error((e as Error).message)
          throw e
        }
      },
    })
  }

  const doDeleteVersion = async (version: VersionSummary) => {
    if (inlineAI.mode !== 'idle' || reviewStartingRef.current) throw new Error('请等待当前操作完成')
    await mutateDraft(() => deleteVersion(version.id), true)
    setReviewDrawerOpen(false)
    setReviewDrawerId(null)
  }

  const saveTitle = async (value: string) => {
    const next = value.trim()
    if (!detail || !next || next === detail.title) return
    try {
      setDetail(await updatePrd(prdId, { title: next }))
      message.success('标题已更新')
    } catch (e) {
      message.error((e as Error).message)
    }
  }

  if (loading && !detail) {
    return (
      <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Spin size="large" />
      </div>
    )
  }

  const saveIndicator = {
    saved: (
      <span className="save-state" style={{ color: '#16a34a' }}>
        <span className="save-state-dot" style={{ background: '#16a34a' }} />
        已保存{lastSavedAt ? ` · ${dayjs(lastSavedAt).format('HH:mm:ss')}` : ''}
      </span>
    ),
    dirty: (
      <span className="save-state" style={{ color: '#d97706' }}>
        <span className="save-state-dot" style={{ background: '#d97706' }} />
        未保存
      </span>
    ),
    saving: (
      <span className="save-state" style={{ color: '#2563eb' }}>
        <LoadingOutlined style={{ marginRight: 6 }} />
        保存中…
      </span>
    ),
    conflict: (
      <span className="save-state" style={{ color: '#dc2626' }}>
        <span className="save-state-dot" style={{ background: '#dc2626' }} />
        内容冲突
      </span>
    ),
  }[saveState]

  return (
    <div ref={layout.containerRef} className={`workspace${chatView ? ' workspace--conversation' : ''}`}>
      <nav className="workspace-resource-nav" aria-label="文档资源导航">
        {([
          { key: 'versions', label: '版本评审', icon: <HistoryOutlined /> },
          { key: 'tests', label: '测试用例', icon: <ExperimentOutlined /> },
          { key: 'knowledge', label: '知识库', icon: <DatabaseOutlined /> },
        ] as const).map(item => <button key={item.key} type="button" aria-pressed={toolPanel === item.key}
          aria-expanded={toolPanel === item.key} aria-controls="workspace-resources"
          onClick={() => setToolPanel(value => value === item.key ? null : item.key)}>
          {item.icon}<span>{item.label}</span>
        </button>)}
        <button type="button" className="workspace-library-link" onClick={() => navigate('/prds')}>
          <ArrowLeftOutlined /><span>文档库</span>
        </button>
      </nav>
      {toolPanel && <>
        <button className="workspace-resource-backdrop" aria-label="关闭文档资源面板" onClick={() => setToolPanel(null)} />
        <aside className="workspace-resources" id="workspace-resources" aria-label="文档资源面板" style={{ width: layout.sizes.resources }}>
          <PanelResizeHandle label="调整资源面板宽度" value={layout.sizes.resources} {...layout.bounds.resources}
            onResize={value => layout.resize('resources', value)} onReset={() => layout.reset('resources')} />
          <div className="workspace-resource-heading">
            <strong>{{ versions: '版本与评审', tests: '测试用例', knowledge: '知识库' }[toolPanel]}</strong>
            <Button size="small" type="text" aria-label="收起资源面板" icon={<CloseOutlined />} onClick={() => setToolPanel(null)} />
          </div>
          <div className="workspace-resource-content">
            {toolPanel === 'versions' && <VersionPanel key={prdId} prdId={prdId} currentVersionId={detail?.currentVersionId ?? null}
              onBeforeCreate={ensureSaved} onAfterChange={refreshMeta} onUseVersion={doUseVersion} onDeleteVersion={doDeleteVersion}
              versionBusy={draftBusy || inlineAI.mode !== 'idle'}
              onOpenReview={openReview} onStartReview={startReview} reviewStarting={reviewStarting} reviewRefreshKey={reviewRefreshKey}
              canDelete={!!detail && detail.ownerId === currentUserId}
              onReviewDeleted={id => {
                if (reviewDrawerId === id) { setReviewDrawerOpen(false); setReviewDrawerId(null) }
                setReviewRefreshKey(k => k + 1)
              }} />}
            {toolPanel === 'tests' && <TestCasePanel key={prdId} prdId={prdId} currentVersionId={detail?.currentVersionId ?? null}
              onLocate={id => { if (!layout.inline.resources) setToolPanel(null); locateBlock(id) }} onAccept={acceptCriteria} />}
            {toolPanel === 'knowledge' && <KnowledgePanel key={prdId} prdId={prdId} />}
          </div>
        </aside>
      </>}

      <div className="workspace-main">
        <div className="workspace-header">
          <Space size={12} wrap>
            {detail && <Typography.Title level={4} style={{ margin: 0 }}
              editable={{ onChange: saveTitle, tooltip: '点击编辑标题', enterIcon: null }}>{detail.title}</Typography.Title>}
            {saveIndicator}
            {saveState === 'conflict' && <Button size="small" danger onClick={showConflictModal}>处理保存冲突</Button>}
          </Space>
          <div className="workspace-header-actions">
            <Button size="small" aria-label={outlineCollapsed ? '展开目录' : '收起目录'} aria-expanded={!outlineCollapsed}
              icon={<MenuOutlined />} onClick={() => setOutlineCollapsed(value => !value)}>目录</Button>
            <Tooltip title={preview ? '阅读模式 · 单击打开链接' : '编辑模式 · Ctrl/⌘ + 单击打开链接'}>
              <Button size="small" type={preview ? 'primary' : 'default'} disabled={draftBusy || inlineAI.mode !== 'idle'} onClick={() => {
                if (!editor || mutationRef.current) return
                editor.setEditable(preview, false)
                setPreview(value => !value)
              }}>{preview ? '返回编辑' : '阅读预览'}</Button>
            </Tooltip>
            <Button size="small" icon={<UploadOutlined />} disabled={draftBusy} onClick={() => setImportOpen(true)}>
              导入文档
            </Button>
            {chatCollapsed && <Button size="small" icon={<CommentOutlined />} aria-label="打开 AI 助手" onClick={() => setChatCollapsed(false)}>AI 助手</Button>}
            <Button className="workspace-save-button" size="small" type="primary" icon={<SaveOutlined />} loading={saveState === 'saving'}
              disabled={draftBusy || saveState === 'conflict'} onClick={() => {
                if (debounceRef.current) { clearTimeout(debounceRef.current); debounceRef.current = null }
                void doSave()
              }}>保存</Button>
          </div>
        </div>

        <div className="workspace-document-body">
          {!outlineCollapsed && <aside className="workspace-outline" aria-label="文档目录侧栏" style={{ width: layout.sizes.outline }}>
            <PanelResizeHandle label="调整目录宽度" value={layout.sizes.outline} {...layout.bounds.outline}
              onResize={value => layout.resize('outline', value)} onReset={() => layout.reset('outline')} />
            <div className="panel-section-header"><span>目录</span>
              <Button size="small" type="text" aria-label="收起目录侧栏" icon={<CloseOutlined />} onClick={() => setOutlineCollapsed(true)} />
            </div>
            <div className="workspace-outline-scroll"><Outline editor={editor} containerRef={editorWrapRef} /></div>
          </aside>}
        <div className="workspace-editor-card">
          <EditorToolbar editor={editor} disabled={draftBusy} />
          <div className="editor-doc-scroll" ref={editorWrapRef}>
            <div className="editor-doc prd-editor">
              <EditorContent editor={editor} />
            </div>
            {!preview && <InlineAIController ai={inlineAI} />}
          </div>
        </div>
        </div>
      </div>

      {(chatMounted || !chatCollapsed) && (
        <aside className="workspace-chat" hidden={chatCollapsed} aria-label="AI 对话侧栏" style={{ width: layout.sizes.chat }}>
          <PanelResizeHandle label="调整 AI 对话宽度" edge="start" value={layout.sizes.chat} {...layout.bounds.chat}
            onResize={value => layout.resize('chat', value)} onReset={() => layout.reset('chat')} />
          <ChatPanel key={prdId} prdId={prdId} defaultSessionId={initialSessionId} autoStartSessionId={startSessionId}
            onAutoStarted={() => navigate(location.pathname + location.search, { replace: true, state: null })}
            onFocusEditor={chatView ? () => { setSearchParams({}); setChatCollapsed(window.innerWidth <= 1100) } : undefined}
            localDirty={saveState === 'dirty' || saveState === 'saving'} onApplyPatch={applyDraftPatch}
            onLocateSource={patch => { if (patch.targetBlockId) locateBlock(patch.targetBlockId, patch.sourceQuote) }}
            onClose={() => { setChatCollapsed(true); if (chatView) setSearchParams({}) }} />
        </aside>
      )}

      <StartReviewModal open={reviewTarget !== null} versionNo={reviewTarget?.versionNo ?? null} starting={reviewStarting}
        initialRubricId={reviewTarget?.rubricId}
        onClose={() => { if (!reviewStartingRef.current) setReviewTarget(null) }} onConfirm={id => void confirmReview(id)} />
      <ReviewReportDrawer
        key={reviewDrawerId}
        reviewId={reviewDrawerId}
        open={reviewDrawerOpen}
        onClose={() => setReviewDrawerOpen(false)}
        onLocate={(blockId, quote) => locateBlock(blockId, quote)}
        onFinished={() => {
          setReviewRefreshKey((k) => k + 1)
          void refreshMeta()
        }}
        localDirty={saveState === 'dirty' || saveState === 'saving'}
        onApplyPatch={applyDraftPatch}
      />

      <Modal
        title="导入文档"
        open={importOpen}
        onOk={doImport}
        onCancel={() => { if (!importing) { setImportOpen(false); setImportFile(null) } }}
        confirmLoading={importing}
        okText="导入并覆盖草稿"
        cancelText="取消"
        width={680}
      >
        <Typography.Paragraph type="secondary">
          导入 MD、UTF-8 TXT 或 DOCX 文件，或粘贴 Markdown。确认后覆盖当前草稿；如需保留内容，请先创建版本。
          DOCX 提取正文、标题、基础列表和表格，不保留图片及复杂排版。PDF 请在「知识」中上传为参考资料。
        </Typography.Paragraph>
        <Upload accept=".md,.markdown,.txt,.docx" maxCount={1} fileList={[]} disabled={importing} beforeUpload={file => {
          if (file.size > 10 * 1024 * 1024) { message.error('文件不能超过 10 MiB'); return false }
          setImportFile(file); return false
        }}><Button disabled={importing}>选择导入文件</Button></Upload>
        {importFile && <Space style={{ margin: '8px 0' }}><Typography.Text>{importFile.name}</Typography.Text>
          <Button size="small" disabled={importing} onClick={() => setImportFile(null)}>取消文件选择</Button></Space>}
        <Input.TextArea
          className="import-markdown-textarea"
          rows={14}
          placeholder="# PRD 标题&#10;&#10;粘贴 Markdown 内容…"
          value={importText}
          disabled={importing || importFile !== null}
          onChange={(e) => setImportText(e.target.value)}
        />
      </Modal>
    </div>
  )
}
