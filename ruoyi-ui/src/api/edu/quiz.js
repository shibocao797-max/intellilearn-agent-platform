import request from '@/utils/request'

/**
 * 获取题目列表
 */
export function getQuizList(params) {
  return request({
    url: '/edu/quiz/user/list',
    method: 'get',
    params
  })
}

/**
 * 获取题目详情
 */
export function getQuizDetail(id) {
  return request({
    url: '/edu/quiz/detail/' + id,
    method: 'get'
  })
}

/**
 * 提交答案
 */
export function submitAnswer(data) {
  return request({
    url: '/edu/quiz/submit',
    method: 'post',
    data
  })
}

/**
 * 获取答题记录
 */
export function getQuizRecords(params) {
  return request({
    url: '/edu/quiz/records',
    method: 'get',
    params
  })
}

/**
 * 获取答题统计
 */
export function getQuizStats() {
  return request({
    url: '/edu/quiz/stats',
    method: 'get'
  })
}

/**
 * AI 引导式讲解
 */
export function aiExplain(data) {
  return request({
    url: '/edu/quiz/ai-explain',
    method: 'post',
    data
  })
}
