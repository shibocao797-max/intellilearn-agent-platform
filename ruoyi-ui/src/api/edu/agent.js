import request from '@/utils/request'

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
