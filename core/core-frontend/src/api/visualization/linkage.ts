import request from '@/config/axios'

// 聚合当前视图联动条件
export const viewLinkageGather = data => request.post({ url: '/linkage/linkage-gather', data })

// 批量聚合多个视图的联动条件
export const viewLinkageGatherArray = data =>
  request.post({ url: '/linkage/linkage-gather-array', data })

// 保存可视化联动配置
export const saveLinkage = data => request.post({ url: '/linkage/linkage', data })

// 查询指定大屏或仪表板的全部联动信息
export const getPanelAllLinkageInfo = (dvId, resourceTable = 'snapshot') =>
  request.get({ url: '/linkage/visualization-linkage-info/' + dvId + '/' + resourceTable })

// 更新联动启用状态
export const updateLinkageActive = data =>
  request.post({ url: '/linkage/linkage-active-state', data })

// 删除指定联动配置
export const deleteLinkage = data => request.delete({ url: '/linkage/linkage', data })
