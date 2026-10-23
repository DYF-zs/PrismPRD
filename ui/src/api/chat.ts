import { get, post } from './client'
import type { ChatMessageDetail, ChatSessionSummary } from './types'

export function fetchSessions(prdId: string): Promise<ChatSessionSummary[]> {
  return get<ChatSessionSummary[]>(`/api/prds/${prdId}/sessions`)
}

export function createSession(prdId: string, title?: string): Promise<ChatSessionSummary> {
  return post<ChatSessionSummary>(`/api/prds/${prdId}/sessions`, title ? { title } : {})
}

export function fetchMessages(sessionId: string): Promise<ChatMessageDetail[]> {
  return get<ChatMessageDetail[]>(`/api/sessions/${sessionId}/messages`)
}

/** 提交用户消息并触发 PrdAgent；content 为空时处理会话中未回复的初始需求 */
export function sendMessage(sessionId: string, content?: string): Promise<ChatMessageDetail> {
  return post<ChatMessageDetail>(`/api/sessions/${sessionId}/messages`, content ? { content } : {})
}

/** 订阅会话 SSE（message / patch / state / done / error） */
export function subscribeSessionEvents(
  sessionId: string,
  handlers: {
    onMessage?: (message: ChatMessageDetail) => void
    onPatch?: (patch: import('./types').PatchDetail) => void
    onState?: (state: string) => void
    onDone?: () => void
    onError?: (message: string) => void
    onOpen?: () => void
    onConnectionError?: () => void
  },
): Pick<EventSource, 'close'> {
  let source: EventSource
  let stopped = false
  let retry: ReturnType<typeof setTimeout> | undefined
  let lastActivity = Date.now()
  const reconnect = () => {
    if (stopped || retry) return
    source.close()
    handlers.onConnectionError?.()
    retry = setTimeout(connect, 3000)
  }
  const connect = () => {
    if (stopped) return
    retry = undefined
    lastActivity = Date.now()
    const active = new EventSource(`/api/sessions/${sessionId}/events`)
    source = active
    const on = (event: string, callback: (event: Event) => void) => active.addEventListener(event, e => {
      if (stopped || active !== source) return
      lastActivity = Date.now()
      callback(e)
    })
    on('open', () => handlers.onOpen?.())
    on('ready', () => {})
    on('heartbeat', () => {})
    on('message', (e) => {
      try { handlers.onMessage?.(JSON.parse((e as MessageEvent).data)) }
      catch { handlers.onError?.('消息通知格式异常，请重新加载会话') }
    })
    on('patch', (e) => {
      try { handlers.onPatch?.(JSON.parse((e as MessageEvent).data)) }
      catch { handlers.onError?.('补丁通知格式异常，请重新加载会话') }
    })
    on('state', e => handlers.onState?.(decodeTextEvent((e as MessageEvent).data)))
    on('done', () => handlers.onDone?.())
    on('error', e => {
      const data = (e as MessageEvent).data
      if (data) handlers.onError?.(decodeTextEvent(String(data)))
      else reconnect() // Recreate native EventSource even after a proxy 502 / HTTP 429.
    })
  }
  connect()
  const watchdog = setInterval(() => { if (Date.now() - lastActivity > 45000) reconnect() }, 15000)
  return { close: () => { stopped = true; if (retry) clearTimeout(retry); clearInterval(watchdog); source.close() } }
}

export function decodeTextEvent(data: string): string {
  try {
    const parsed: unknown = JSON.parse(data)
    return typeof parsed === 'string' ? parsed : data
  } catch { return data }
}
