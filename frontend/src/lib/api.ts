/**
 * One place that talks to Spring. Errors arrive as a message the interface can
 * show rather than a status code: when a schedule cannot exist the server
 * already explains why — which subject has no specialist, which hour is short
 * of people — and throwing that away would leave the administrator guessing.
 */

import { LANGUAGES, storedLanguage } from '../i18n'

export class ApiError extends Error {
  readonly status: number
  readonly details?: unknown

  constructor(status: number, message: string, details?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      // the server writes its refusals as sentences, so it has to be told
      // which language to write them in
      'Accept-Language': LANGUAGES[storedLanguage()].tag,
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  })

  if (!response.ok) {
    let message = response.statusText
    let details: unknown
    try {
      const body = await response.json()
      details = body
      if (typeof body?.error === 'string') message = body.error
    } catch {
      // a server that fell over without JSON: the status line is all there is
    }
    throw new ApiError(response.status, message, details)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
