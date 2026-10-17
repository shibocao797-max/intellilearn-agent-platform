import request from '@/utils/request'
import { getToken } from '@/utils/auth'

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

/**
 * Build animation playback endpoint.
 */
export function getAnimationPlayUrl(id) {
  return `${process.env.VUE_APP_BASE_API}/edu/resource/animation/${id}/play`
}

/**
 * Fetch self-contained animation HTML with the same token policy as API calls.
 * The caller should render it through an iframe blob URL, not v-html.
 */
export function fetchAnimationHtml(id) {
  const token = getToken()
  return fetch(getAnimationPlayUrl(id), {
    method: 'GET',
    headers: token ? { Authorization: 'Bearer ' + token } : {}
  }).then(async response => {
    const text = await response.text()
    if (!response.ok) {
      throw new Error(text || `动画资源加载失败: ${response.status}`)
    }
    return text
  })
}
