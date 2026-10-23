import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Input, Modal, Spin, Tag, message } from 'antd'
import {
  CheckOutlined,
  CloseOutlined,
  PlusOutlined,
  RobotOutlined,
  SendOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import {
  createSession,
  fetchMessages,
  fetchSessions,
  sendMessage,
  subscribeSessionEvents,
} from '../api/chat'
import { rejectPatch, fetchPatches } from '../api/patch'
import { restoreSessionPatches, sortPatches } from './chatPatchState'
import type { ChatMessageDetail, ChatSessionSummary, PatchDetail } from '../api/types'

const AGENT_STATE_LABEL: Record<string, string> = {
  ANALYZING: '正在分析…',
  MODIFYING: '正在生成修改…',
  ERROR: '执行出错',
  DONE: '已完成',
}

interface Props {
  prdId: string
  defaultSessionId?: string | null
  autoStartSessionId?: string
  onAutoStarted?: () => void
  onFocusEditor?: () => void
  localDirty: boolean
  onApplyPatch: (patchId: string) => Promise<PatchDetail>
  onClose: () => void
  onDraftRefreshNeeded?: () => void
}

export default function ChatPanel({
  prdId,
  defaultSessionId,
  autoStartSessionId,
  onAutoStarted,
  onFocusEditor,
  localDirty,
  onApplyPatch,
  onClose,
  onDraftRefreshNeeded,
}: Props) {
  const [sessions, setSessions] = useState<ChatSessionSummary[]>([])
  const [activeSessionId, setActiveSessionId] = useState<string | null>(defaultSessionId ?? null)
  const [messages, setMessages] = useState<ChatMessageDetail[]>([])
  const [patches, setPatches] = useState<Record<string, PatchDetail>>({})
  const [patchBusyIds, setPatchBusyIds] = useState<Set<string>>(new Set())
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [agentState, setAgentState] = useState<string | null>(null)
  const [reconnecting, setReconnecting] = useState(false)
  const [connected, setConnected] = useState(false)
  const autoStartedRef = useRef<string | null>(null)
  const bodyRef = useRef<HTMLDivElement>(null)
  const completionRef = useRef(0)
  const activeSessionRef = useRef(activeSessionId)
  const sendBusyRef = useRef(false)
  activeSessionRef.current = activeSessionId

  const busy = agentState === 'ANALYZING' || agentState === 'MODIFYING' || sending

  // 加载会话列表
  const loadSessions = useCallback(async () => {
    try {
      const list = await fetchSessions(prdId)
      setSessions(list)
      if (!activeSessionRef.current && list.length > 0) {
        setActiveSessionId(list[0].id)
      } else if (!activeSessionRef.current) {
        const created = await createSession(prdId)
        setSessions((prev) => [created, ...prev])
        setActiveSessionId(created.id)
      }
    } catch (e) {
      message.error((e as Error).message)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [prdId])

  useEffect(() => {
    if (!prdId) return
    setActiveSessionId(defaultSessionId ?? null)
    void loadSessions()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [prdId, defaultSessionId])

  // 加载历史消息 + 订阅 SSE
  useEffect(() => {
    if (!activeSessionId) return
    let source: Pick<EventSource, 'close'> | null = null
    let alive = true
    setPatches({})
    setAgentState(null)
    setReconnecting(false)
    setConnected(false)
    setMessages([])
    const restore = () => {
    fetchPatches(prdId)
      .then(history => {
        if (alive) setPatches(live => restoreSessionPatches(history, live, activeSessionId))
      })
      .catch(e => { if (alive) message.error((e as Error).message) })
    fetchMessages(activeSessionId)
      .then((list) => {
        if (alive) setMessages(live => {
          const merged = new Map(list.map(m => [m.id, m]))
          live.forEach(m => merged.set(m.id, m))
          return [...merged.values()].sort((a, b) => (a.createdAt ?? '').localeCompare(b.createdAt ?? ''))
        })
      })
      .catch((e) => { if (alive) message.error((e as Error).message) })
    }
    restore()
    source = subscribeSessionEvents(activeSessionId, {
      onOpen: () => { if (alive) { setConnected(true); setReconnecting(false); restore() } },
      onConnectionError: () => { if (alive) { setConnected(false); setReconnecting(true) } },
      onMessage: (msg) => {
        if (!alive) return
        setMessages((prev) =>
          prev.some((m) => m.id === msg.id) ? prev : [...prev, msg],
        )
      },
      onPatch: (patch) => {
        if (!alive) return
        setPatches((prev) => ({ ...prev, [patch.id]: patch }))
      },
      onState: (state) => {
        if (alive) {
          if (state === 'IDLE' || state === 'DONE' || state === 'ERROR') completionRef.current += 1
          setAgentState(state === 'IDLE' ? null : state)
        }
      },
      onDone: () => { if (alive) { completionRef.current += 1; setAgentState(null) } },
      onError: (msg) => {
        if (!alive) return
        completionRef.current += 1
        setAgentState(null)
        message.error(msg || 'Agent 执行失败')
      },
    })
    return () => {
      alive = false
      source?.close()
    }
  }, [activeSessionId, prdId])

  // 消息变化时滚动到底部
  useEffect(() => {
    const el = bodyRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [messages, agentState])

  const doSend = async (content?: string) => {
    const text = (content ?? input).trim()
    if (!activeSessionId || (!text && !hasUnansweredInitial())) return
    if (busy || sendBusyRef.current) return
    const sessionId = activeSessionId
    const completion = completionRef.current
    sendBusyRef.current = true
    setSending(true)
    try {
      const userMsg = await sendMessage(activeSessionId, text || undefined)
      if (activeSessionRef.current !== sessionId) return
      setMessages((prev) => (prev.some((m) => m.id === userMsg.id) ? prev : [...prev, userMsg]))
      setInput('')
      if (completionRef.current === completion) setAgentState(state => state ?? 'ANALYZING')
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      sendBusyRef.current = false
      setSending(false)
    }
  }

  /** AI_DRAFT：会话里有初始需求但没有 AI 回复 */
  const hasUnansweredInitial = () => {
    if (messages.length === 0) return false
    return messages[messages.length - 1].role === 'USER'
  }

  useEffect(() => {
    if (!autoStartSessionId || autoStartedRef.current === autoStartSessionId || activeSessionId !== autoStartSessionId
      || !connected || messages.length === 0 || busy) return
    autoStartedRef.current = autoStartSessionId
    if (messages[messages.length - 1].role === 'USER') void doSend('')
    onAutoStarted?.()
  }, [autoStartSessionId, activeSessionId, connected, messages, busy])

  const doApplyPatch = (patch: PatchDetail) => {
    const run = async () => {
      setPatchBusyIds((prev) => new Set(prev).add(patch.id))
      try {
        const applied = await onApplyPatch(patch.id)
        setPatches((prev) => ({ ...prev, [applied.id]: applied }))
        message.success('补丁已应用')
        onDraftRefreshNeeded?.()
      } catch (e) {
        const err = e as { isConflict?: boolean; message?: string }
        if (err.isConflict) {
          message.warning('草稿已更新，补丁已过期，请让 AI 重新生成')
          setPatches((prev) => ({
            ...prev,
            [patch.id]: { ...prev[patch.id], status: 'STALE' },
          }))
        } else {
          message.error(err.message ?? '应用失败')
        }
      } finally {
        setPatchBusyIds((prev) => {
          const next = new Set(prev)
          next.delete(patch.id)
          return next
        })
      }
    }
    if (localDirty) {
      Modal.confirm({
        title: '当前有未保存的本地修改',
        content: '将先保存本地修改，再校验并应用补丁。若补丁已过期，请基于最新内容重新生成。',
        okText: '保存并校验',
        cancelText: '取消',
        onOk: () => run(),
      })
      return
    }
    void run()
  }

  const doRejectPatch = async (patch: PatchDetail) => {
    setPatchBusyIds((prev) => new Set(prev).add(patch.id))
    try {
      const rejected = await rejectPatch(patch.id)
      setPatches((prev) => ({ ...prev, [rejected.id]: rejected }))
    } catch (e) {
      message.error((e as Error).message)
    } finally {
      setPatchBusyIds((prev) => {
        const next = new Set(prev)
        next.delete(patch.id)
        return next
      })
    }
  }

  const handleCreateSession = async () => {
    try {
      const created = await createSession(prdId)
      setSessions((prev) => [created, ...prev])
      setActiveSessionId(created.id)
    } catch (e) {
      message.error((e as Error).message)
    }
  }

  const patchList = sortPatches(Object.values(patches))

  return (
    <div className="chat-panel">
      <div className="panel-section-header">
        <RobotOutlined />
        <span>AI 助手</span>
        {onFocusEditor && <Button size="small" type="text" onClick={onFocusEditor}>查看草稿</Button>}
        <Button size="small" type="text" aria-label="收起AI对话" icon={<CloseOutlined />} onClick={onClose} />
        <Button
          size="small"
          type="text"
          icon={<PlusOutlined />}
          onClick={handleCreateSession}
          title="新建会话"
          aria-label="新建会话"
        />
      </div>

      {sessions.length > 1 && (
        <div className="chat-sessions">
          {sessions.map((s) => (
            <Button
              key={s.id}
              size="small"
              type={s.id === activeSessionId ? 'primary' : 'text'}
              onClick={() => setActiveSessionId(s.id)}
            >
              {s.title}
            </Button>
          ))}
        </div>
      )}

      <div className="chat-body" ref={bodyRef}>
        {reconnecting && <Alert type="warning" showIcon message="实时连接中断，正在重连并恢复消息…" style={{ marginBottom: 8 }} />}
        {messages.length === 0 && (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="向 AI 描述你的需求，例如：帮我补充异常处理流程"
          />
        )}
        {messages.map((m) => (
          <ChatMessageBubble key={m.id} message={m} />
        ))}
        {patchList.map((p) => (
          <PatchCard
            key={p.id}
            patch={p}
            busy={patchBusyIds.has(p.id)}
            onApply={() => doApplyPatch(p)}
            onReject={() => doRejectPatch(p)}
          />
        ))}
        {agentState && agentState !== 'DONE' && agentState !== 'ERROR' && (
          <div className="chat-state">
            <Spin size="small" /> {AGENT_STATE_LABEL[agentState] ?? agentState}
          </div>
        )}
        {agentState === 'ERROR' && (
          <Alert type="error" showIcon message="Agent 执行出错，请重试或修改提问" />
        )}
      </div>

      <div className="chat-input">
        <Input.TextArea
          aria-label="输入消息"
          rows={2}
          maxLength={8000}
          placeholder={busy ? 'AI 正在处理…' : '输入消息，Enter 发送，Shift+Enter 换行'}
          disabled={busy || !activeSessionId}
          value={input}
          style={{ resize: 'none' }}
          onChange={(e) => setInput(e.target.value)}
          onPressEnter={(e) => {
            if (!e.shiftKey && !e.nativeEvent.isComposing && e.keyCode !== 229) {
              e.preventDefault()
              void doSend()
            }
          }}
        />
        <Button
          type="primary"
          aria-label="发送消息"
          icon={<SendOutlined />}
          loading={sending}
          disabled={busy || !activeSessionId || (!input.trim() && !hasUnansweredInitial())}
          onClick={() => doSend()}
        />
      </div>
      {hasUnansweredInitial() && !busy && (
        <Button
          block
          size="small"
          type="dashed"
          onClick={() => doSend('')}
          style={{ margin: '0 12px 8px', width: 'calc(100% - 24px)' }}
        >
          处理初始需求（触发 AI 分析）
        </Button>
      )}
    </div>
  )
}

function ChatMessageBubble({ message: m }: { message: ChatMessageDetail }) {
  if (m.role === 'ASSISTANT' && !m.content?.trim()) return null
  if (m.role === 'TOOL') {
    return (
      <div className="chat-msg chat-msg-tool">
        <ToolOutlined />
        <span className="chat-msg-tool-name">{m.toolName ?? 'tool'}</span>
        <span className="chat-msg-tool-content">{truncate(m.content, 120)}</span>
      </div>
    )
  }
  return (
    <div className={`chat-msg ${m.role === 'USER' ? 'chat-msg-user' : 'chat-msg-assistant'}`}>
      <div className="chat-msg-content">{m.content}</div>
    </div>
  )
}

function PatchCard({
  patch,
  busy,
  onApply,
  onReject,
}: {
  patch: PatchDetail
  busy: boolean
  onApply: () => void
  onReject: () => void
}) {
  const done = patch.status !== 'PENDING'
  return (
    <div className="patch-preview-card">
      <div className="patch-preview-header">
        <span>AI 修改建议</span>
        <Tag color={patch.status === 'APPLIED' ? 'success' : patch.status === 'STALE' ? 'warning' : 'processing'}>
          {patch.status === 'APPLIED'
            ? '已应用'
            : patch.status === 'REJECTED'
              ? '已拒绝'
              : patch.status === 'STALE'
                ? '已过期'
                : '待确认'}
        </Tag>
      </div>
      {patch.reason && <div className="patch-reason">{patch.reason}</div>}
      {patch.sourceQuote && (
        <pre className="patch-diff-old">{patch.sourceQuote}</pre>
      )}
      {patch.newContentMarkdown && (
        <pre className="patch-diff-new">{patch.newContentMarkdown}</pre>
      )}
      {!done && (
        <div className="review-finding-actions">
          <Button size="small" type="primary" icon={<CheckOutlined />} loading={busy} onClick={onApply}>
            接受
          </Button>
          <Button size="small" icon={<CloseOutlined />} loading={busy} onClick={onReject}>
            拒绝
          </Button>
        </div>
      )}
    </div>
  )
}

function truncate(text: string, max: number) {
  return text.length <= max ? text : text.slice(0, max) + '…'
}
