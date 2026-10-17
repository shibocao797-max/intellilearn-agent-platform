import { getToken, removeToken } from '@/services/storage'
import { sanitizeDisplayText } from '@/utils/format'

const API_BASE = import.meta.env.VITE_API_BASE || '/dev-api'

function buildUrl(path, params) {
  const url = path.startsWith('http') ? new URL(path) : new URL(`${API_BASE}${path}`, window.location.origin)
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, value)
    }
  })
  return path.startsWith('http') ? url.toString() : `${url.pathname}${url.search}`
}

function normalizeError(payload, fallback) {
  if (payload && typeof payload === 'object') {
    return sanitizeDisplayText(payload.msg || payload.message || fallback)
  }
  return sanitizeDisplayText(fallback)
}

function redirectToLogin() {
  removeToken()
  if (window.location.pathname === '/login') return

  const currentPath = `${window.location.pathname}${window.location.search}`
  const loginUrl = `/login?redirect=${encodeURIComponent(currentPath)}`
  window.location.replace(loginUrl)
}

export async function request(path, options = {}) {
  const method = options.method || 'GET'
  const headers = { ...(options.headers || {}) }
  const token = getToken()

  if (options.auth !== false && token) {
    headers.Authorization = `Bearer ${token}`
  }

  let body
  if (options.data !== undefined) {
    if (options.data instanceof FormData) {
      body = options.data
    } else {
      headers['Content-Type'] = headers['Content-Type'] || 'application/json;charset=utf-8'
      body = JSON.stringify(options.data)
    }
  }

  const response = await fetch(buildUrl(path, options.params), {
    method,
    headers,
    body,
    signal: options.signal
  })

  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('application/json')
    ? await response.json().catch(() => ({}))
    : await response.text()

  if (!response.ok) {
    if (response.status === 401) redirectToLogin()
    throw new Error(normalizeError(payload, `接口请求失败: ${response.status}`))
  }

  if (payload && typeof payload === 'object') {
    const code = payload.code ?? 200
    if (code === 401) {
      redirectToLogin()
      throw new Error(sanitizeDisplayText(payload.msg || '登录状态已过期'))
    }
    if (code !== 200) {
      throw new Error(payload.msg || '接口返回异常')
    }
  }

  return payload
}

export async function requestText(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  const token = getToken()
  if (options.auth !== false && token) {
    headers.Authorization = `Bearer ${token}`
  }
  const response = await fetch(buildUrl(path, options.params), {
    method: options.method || 'GET',
    headers,
    signal: options.signal
  })
  const text = await response.text()
  if (!response.ok) {
    if (response.status === 401) redirectToLogin()
    throw new Error(sanitizeDisplayText(text || `接口请求失败: ${response.status}`))
  }
  return text
}

export function streamRequest(path, data, handlers = {}) {
  const controller = new AbortController()
  const headers = { 'Content-Type': 'application/json;charset=utf-8' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const promise = fetch(buildUrl(path), {
    method: 'POST',
    headers,
    body: JSON.stringify(data || {}),
    signal: controller.signal
  }).then(async response => {
    if (!response.ok) {
      if (response.status === 401) redirectToLogin()
      throw new Error(`SSE 请求失败: ${response.status}`)
    }
    if (!response.body?.getReader) throw new Error('当前浏览器不支持流式读取')
    handlers.onAccepted?.()

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    const dispatch = block => {
      if (!block.trim()) return
      let eventName = 'message'
      const dataLines = []
      block.split(/\r?\n/).forEach(line => {
        if (line.startsWith('event:')) eventName = line.slice(6).trim()
        if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
      })
      handlers.onEvent?.(eventName, dataLines.join('\n'))
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        if (buffer.trim()) dispatch(buffer)
        handlers.onDone?.()
        return
      }
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split(/\r?\n\r?\n/)
      buffer = blocks.pop() || ''
      blocks.forEach(dispatch)
    }
  }).catch(error => {
    if (error.name !== 'AbortError') handlers.onError?.(error)
  })

  return {
    controller,
    promise,
    abort: () => controller.abort()
  }
}
