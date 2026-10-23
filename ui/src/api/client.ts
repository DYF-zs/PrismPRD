export class ApiError extends Error {
  readonly status: number
  readonly code: number

  constructor(status: number, code: number, message: string) {
    super(message)
    this.status = status
    this.code = code
  }

  get isConflict(): boolean {
    return this.status === 409
  }
}

interface NetworkMessages {
  timeout: string
  network: string
}

const DEFAULT_NETWORK_MESSAGES: NetworkMessages = {
  timeout: '请求超时，请检查服务器状态后重试',
  network: '无法连接服务器，请检查网络和服务是否已启动',
}

/** Shared transport only; callers retain their JSON/binary response handling. */
export async function fetchWithTimeout(
  path: string,
  init: RequestInit = {},
  messages: NetworkMessages = DEFAULT_NETWORK_MESSAGES,
): Promise<Response> {
  try {
    return await fetch(path, { ...init, signal: AbortSignal.timeout(180000) })
  } catch (e) {
    const timeout = ['TimeoutError', 'AbortError'].includes((e as Error).name)
    throw new ApiError(0, 0, timeout ? messages.timeout : messages.network)
  }
}

export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const multipart = typeof FormData !== 'undefined' && body instanceof FormData
  const init: RequestInit = {
    method,
    headers: multipart ? {} : { 'Content-Type': 'application/json' },
  }
  if (body !== undefined) {
    init.body = multipart ? body as FormData : JSON.stringify(body)
  }
  const resp = await fetchWithTimeout(path, init)
  const text = await resp.text()
  let payload: { code?: number; message?: string; data?: T } | null = null
  try {
    payload = text ? JSON.parse(text) : null
  } catch {
    payload = null
  }
  if (!resp.ok || !payload || payload.code !== 0) {
    const message = payload?.message ?? `请求失败（HTTP ${resp.status}）`
    throw new ApiError(resp.status, payload?.code ?? resp.status, message)
  }
  return payload.data as T
}

export const get = <T>(path: string) => request<T>('GET', path)
export const post = <T>(path: string, body?: unknown) => request<T>('POST', path, body)
export const put = <T>(path: string, body?: unknown) => request<T>('PUT', path, body)
export const patch = <T>(path: string, body?: unknown) => request<T>('PATCH', path, body)
