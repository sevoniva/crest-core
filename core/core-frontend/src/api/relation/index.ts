import request from '@/config/axios'

// 查询指定数据源的血缘关系图
export function datasourceRelationship(id) {
  return request.post({ url: `/relation/datasource/${id}` }).then(res => {
    return res?.data
  })
}

// 查询指定数据集的血缘关系图
export function datasetRelationship(id) {
  return request.post({ url: `/relation/dataset/${id}` }).then(res => {
    return res?.data
  })
}

// 查询指定仪表板或大屏的血缘关系图
export function getPanelRelationship(id) {
  return request.post({ url: `/relation/dv/${id}` }).then(res => {
    return res?.data
  })
}

// 查询全局血缘关系概览
export function getRelationshipOverview() {
  return request.post({ url: '/relation/overview' }).then(res => {
    return res?.data
  })
}

// 按资源类型查询血缘图可选资源
export function listRelationResources(type = 'all', data = {}) {
  return request.post({ url: `/relation/resources/${type}`, data }).then(res => {
    return res?.data
  })
}

// 检查指定资源的访问权限
export function resourceCheckPermission(id) {
  return request.post({
    url: `/resource/permission-check/${id}`
  })
}
