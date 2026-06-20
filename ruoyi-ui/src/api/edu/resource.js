import request from '@/utils/request'

/**
 * 获取用户资源列表
 */
export function getResourceList(params) {
  return request({
    url: '/edu/resource/user/list',
    method: 'get',
    params
  })
}

/**
 * 获取资源详情
 */
export function getResourceDetail(id) {
  return request({
    url: '/edu/resource/detail/' + id,
    method: 'get'
  })
}
