import request from '@/utils/request'

/**
 * 获取学习总览
 */
export function getReportSummary() {
  return request({
    url: '/edu/report/summary',
    method: 'get'
  })
}

/**
 * 获取详细报告
 */
export function getReportDetailed() {
  return request({
    url: '/edu/report/detailed',
    method: 'get'
  })
}
