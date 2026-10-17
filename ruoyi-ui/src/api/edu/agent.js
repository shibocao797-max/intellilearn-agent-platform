import request from '@/utils/request'
import { getToken } from '@/utils/auth'

/**
 * 普通对话接口
 */
export function chat(data) {
  return request({
    url: '/edu/ai/chat',
    method: 'post',
    data
  })
}

/**
 * 获取用户会话列表
 */
export function getSessions() {
  return request({
    url: '/edu/ai/sessions',
    method: 'get'
  })
}

/**
 * 新建会话
 */
export function newSession() {
  return request({
    url: '/edu/ai/session/new',
    method: 'post'
  })
}

/**
 * 删除会话
 */
export function deleteSession(sessionId) {
  return request({
    url: '/edu/ai/session/' + sessionId,
    method: 'delete'
  })
}

/**
 * 获取会话历史
 */
export function getConversation(sessionId) {
  return request({
    url: '/edu/ai/conversation/' + sessionId,
    method: 'get'
  })
}

export function transcribeSpeech(data) {
  return request({
    url: '/edu/ai/speech/transcribe',
    method: 'post',
    headers: {
      repeatSubmit: false
    },
    data
  })
}

/**
 * Stream AI chat events.
 *
 * Returns an object with abort() and promise. The parser keeps SSE framing in
 * the API layer so Vue components only react to named events.
 */
export function chatStream(data, handlers = {}) {
  const controller = new AbortController()
  const token = getToken()
  const url = `${process.env.VUE_APP_BASE_API}/edu/ai/chat/stream`

  const promise = fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {})
    },
    body: JSON.stringify(data || {}),
    signal: controller.signal
  })
    .then(response => {
      if (!response.ok) {
        throw new Error(`SSE request failed: ${response.status}`)
      }
      if (!response.body || !response.body.getReader) {
        throw new Error('Current browser does not support stream reading')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      const dispatchBlock = block => {
        if (!block || !block.trim()) return

        let eventName = 'message'
        const dataLines = []
        block.split(/\r?\n/).forEach(line => {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            dataLines.push(line.slice(5).replace(/^ /, ''))
          }
        })

        if (handlers.onEvent) {
          handlers.onEvent(eventName, dataLines.join('\n'))
        }
      }

      const read = () => reader.read().then(({ done, value }) => {
        if (done) {
          if (buffer.trim()) dispatchBlock(buffer)
          if (handlers.onDone) handlers.onDone()
          return
        }

        buffer += decoder.decode(value, { stream: true })
        const blocks = buffer.split(/\r?\n\r?\n/)
        buffer = blocks.pop() || ''
        blocks.forEach(dispatchBlock)
        return read()
      })

      return read()
    })
    .catch(error => {
      if (error.name !== 'AbortError' && handlers.onError) {
        handlers.onError(error)
      }
    })

  return {
    controller,
    promise,
    abort: () => controller.abort()
  }
}
