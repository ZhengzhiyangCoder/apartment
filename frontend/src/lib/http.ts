export class HttpError extends Error {
  constructor(public readonly status: number, message: string, public readonly code = '') { super(message) }
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, { ...options, credentials: 'same-origin' })
  } catch {
    throw new HttpError(0, '暂时无法连接服务，请检查连接后重试')
  }
  if (!response.ok) {
    const error = await response.json().catch(() => null) as { message?: string; code?: string } | null
    throw new HttpError(response.status, error?.message ?? '服务暂时不可用，请稍后重试', error?.code)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export async function write<T>(path: string, body?: BodyInit, contentType?: string): Promise<T> {
  const csrf = await request<{ headerName: string; token: string }>('/api/auth/csrf')
  return request<T>(path, {
    method: 'POST', body,
    headers: { [csrf.headerName]: csrf.token, ...(contentType ? { 'Content-Type': contentType } : {}) },
  })
}
