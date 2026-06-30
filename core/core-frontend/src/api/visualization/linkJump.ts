import request from '@/config/axios'

// 查询指定视图关联表的字段列表
export function getTableFieldWithViewId(viewId) {
  return request.get({
    url: '/link-jump/table-fields/by-view/' + viewId
  })
}
// 查询指定可视化和视图的跳转配置
export function queryWithViewId(dvId, viewId) {
  return request.get({
    url: '/link-jump/by-view/' + dvId + '/' + viewId
  })
}

// 保存跳转配置
export function updateJumpSet(requestInfo) {
  return request.post({
    url: '/link-jump/jump-set',
    data: requestInfo,
    loading: true
  })
}
// 查询目标可视化跳转所需的信息
export function queryTargetVisualizationJumpInfo(requestInfo) {
  return request.post({
    url: '/link-jump/target-visualization-jump-info',
    data: requestInfo,
    loading: true
  })
}

// 查询可视化资源的跳转配置概览
export function queryVisualizationJumpInfo(dvId, resourceTable = 'snapshot') {
  return request.get({
    url: '/link-jump/visualization-jump-info/' + dvId + '/' + resourceTable,
    loading: false
  })
}

// 查询可视化资源下的明细表列表
export function viewTableDetailList(dvId, resourceTable = 'snapshot') {
  return request.get({
    url: '/link-jump/view-table-detail-list/' + dvId + '/' + resourceTable,
    loading: false
  })
}

// 更新跳转配置启用状态
export function updateJumpSetActive(requestInfo) {
  return request.post({
    url: '/link-jump/jump-set-active',
    data: requestInfo,
    loading: true
  })
}

// 删除跳转配置
export function deleteJumpSet(requestInfo) {
  return request.delete({
    url: '/link-jump/jump-set',
    data: requestInfo,
    loading: true
  })
}
