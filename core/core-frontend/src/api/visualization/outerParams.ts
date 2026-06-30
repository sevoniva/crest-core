import request from '@/config/axios'

export function queryWithVisualizationId(dvId) {
  return request.get({
    url: '/outer-params/by-visualization/' + dvId
  })
}

// 更新当前配置并同步相关状态
export function updateOuterParamsSet(requestInfo) {
  return request.post({
    url: '/outer-params/outer-params-settings',
    data: requestInfo,
    loading: true
  })
}

export function getOuterParamsInfo(dvId) {
  return request.get({
    url: '/outer-params/outer-params-info/' + dvId,
    method: 'get',
    loading: false
  })
}
