import request from '@/config/axios'

/** 分页查询源端同步数据源列表 */
export const sourceDsPageApi = (page: number, limit: number, data) => {
  return request.post({ url: `/sync/datasource/source/page/${page}/${limit}`, data })
}

/** 分页查询目标端同步数据源列表 */
export const targetDsPageApi = (page: number, limit: number, data) => {
  return request.post({ url: `/sync/datasource/target/page/${page}/${limit}`, data })
}
/** 查询指定类型最近使用的数据源配置 */
export const latestUseApi = (sourceType: string) => {
  return request.post({ url: `/sync/datasource/latest-use/${sourceType}`, data: {} })
}

/** 校验同步数据源连接参数是否可用 */
export const validateApi = data => {
  return request.post({ url: '/sync/datasource/validate', data })
}

/** 查询同步数据源可用的数据库模式集合 */
export const getSchemaApi = data => {
  return request.post({ url: '/sync/datasource/schemas', data })
}

/** 保存新的同步数据源记录 */
export const saveApi = data => {
  return request.post({ url: '/sync/datasource/record', data })
}

/** 根据编号查询同步数据源详情 */
export const getByIdApi = (id: string) => {
  return request.get({ url: `/sync/datasource/detail/${id}` })
}

/** 更新已有同步数据源配置 */
export const updateApi = data => {
  return request.put({ url: '/sync/datasource', data })
}

/** 根据编号删除同步数据源 */
export const deleteByIdApi = (id: string) => {
  return request.delete({ url: `/sync/datasource/${id}` })
}

/** 批量删除同步数据源 */
export const batchDelApi = (ids: string[]) => {
  return request.delete({ url: `/sync/datasource/batch`, data: ids })
}

/**
 * 获取源数据库字段集合以及目标数据库数据类型集合
 */
export const getFieldListApi = data => {
  return request.post({ url: `/sync/datasource/fields`, data })
}

/** 根据编号重新校验同步数据源连接 */
export const validateByIdApi = (id: string) => {
  return request.get({ url: `/sync/datasource/validate/${id}` })
}
