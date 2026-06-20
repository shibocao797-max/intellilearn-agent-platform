import request from '@/utils/request'

/**
 * 获取当前用户画像
 */
export function getProfile() {
  return request({
    url: '/edu/profile',
    method: 'get'
  })
}

/**
 * 画像详情（含维度解析）
 */
export function getProfileDetail() {
  return request({
    url: '/edu/profile/detail',
    method: 'get'
  })
}
