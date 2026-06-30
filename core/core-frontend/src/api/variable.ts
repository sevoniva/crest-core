import request from '@/config/axios'

// 创建系统变量
export const variableCreateApi = data => request.post({ url: '/sys-variable', data })

// 编辑系统变量
export const variableEditApi = data => request.put({ url: '/sys-variable', data })

// 查询系统变量详情
export const variableDetailApi = id => request.get({ url: '/sys-variable/detail/' + id })

// 删除系统变量
export const variableDeletelApi = id => request.delete({ url: '/sys-variable/' + id })

// 查询系统变量列表
export const searchVariableApi = async data => request.post({ url: '/sys-variable/list', data })

// 分页查询变量可选值
export const valueSelectedForVariableApi = (page: number, limit: number, data) =>
  request.post({ url: `/sys-variable/value/selected/${page}/${limit}`, data })

// 查询指定变量的可选值
export const valueForVariable = id => request.get({ url: '/sys-variable/value/selected/' + id })

// 创建变量值
export const variableValueCreateApi = data => request.post({ url: '/sys-variable/value', data })

// 删除变量值
export const variableValueDeletelApi = id => request.delete({ url: '/sys-variable/value/' + id })

// 编辑变量值
export const variableValueEditApi = data => request.put({ url: '/sys-variable/value', data })

// 批量删除变量值
export const batchDelApi = data => request.delete({ url: '/sys-variable/value/batch', data })
