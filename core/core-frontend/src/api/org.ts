import request from '@/config/axios'

// 查询组织树分页数据
export const searchApi = data => request.post({ url: '/org/page/tree', data })
// 新增组织节点
export const saveApi = data => request.post({ url: '/org/page', data })
// 更新组织节点
export const updateApi = data => request.put({ url: '/org/page', data })
// 查询组织下是否存在资源
export const resourceExistApi = oid => request.get({ url: '/org/resource-exists/' + oid })
// 删除组织节点
export const deleteApi = oid => request.delete({ url: '/org/page/' + oid })
