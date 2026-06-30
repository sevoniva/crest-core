import request from '@/config/axios'
import { nameTrim } from '@/utils/utils'

export interface DatasetOrFolder {
  name: string
  id?: number | string
  pid?: number | string
  nodeType: 'folder' | 'dataset'
  union?: Array<{}>
  allFields?: Array<{}>
}

interface Fields {
  fields: Array<{}>
  data: Array<{}>
}
export interface FieldData {
  allFields: Array<{}>
  data: Fields
}

export interface Dataset {
  id: string
  pid: string
  name: string
  union?: Array<{}>
  allFields?: Array<{}>
}

export interface Table {
  datasourceId: string
  name: string
  tableName: string
  type: string
  unableCheck?: boolean
}

// 查询数据源资源树
export const listDatasources = data => {
  return request
    .post({ url: '/datasource/tree', data: { ...data, ...{ busiFlag: 'datasource' } } })
    .then(res => {
      return res?.data
    })
}

// 查询可用的数据源类型
export const listDatasourceType = async (): Promise<IResponse> => {
  return request.get({ url: '/datasource/types' }).then(res => {
    return res?.data
  })
}
// 查询数据源表字段
export const getTableField = (data = {}) => request.post({ url: '/datasource/table-fields', data })

// 同步 API 数据表元信息
export const syncApiTable = (data = {}) =>
  request.post({ url: '/datasource/api-tables/sync', data })

// 同步 API 数据源配置
export const syncApiDs = (data = {}) =>
  request.post({ url: '/datasource/api-data-sources/sync', data })

// 查询数据源下的表列表
export const listDatasourceTables = async (data = {}): Promise<IResponse> => {
  return request.post({ url: '/datasource/tables', data }).then(res => {
    return res
  })
}

// 查询数据源表状态
export const tableStatus = async (data = {}): Promise<IResponse> => {
  return request.post({ url: '/datasource/table-status', data }).then(res => {
    return res
  })
}

// 查询数据源 Schema
export const getSchema = (data = {}) => {
  return request.post({ url: '/datasource/schemas', data })
}

// 预览数据源数据
export const previewData = (data = {}) => {
  return request.post({ url: '/datasource/preview-data', data }).then(res => {
    return res?.data
  })
}

// 分页查询 Excel 数据
export const excelDataPage = (data = {}) => {
  return request.post({ url: '/datasource/excel-data/page', data }).then(res => {
    return res?.data
  })
}

// 保存 Excel 数据记录
export const saveExcelData = (data = {}) => {
  return request.post({ url: '/datasource/excel-data/record', data, loading: true }).then(res => {
    return res?.data
  })
}

// 校验数据源连接信息
export const validate = (data = {}) => {
  return request.post({ url: '/datasource/validate', data })
}

// 查询数据源完成页是否可见
export const isShowFinishPage = async () => {
  return request.get({ url: '/datasource/finish-page/visible' })
}

// 设置数据源完成页显示状态
export const setShowFinishPage = (data = {}) => {
  return request.post({ url: '/datasource/finish-page/visible', data })
}
// 更新数据源最近使用记录
export const latestUse = async (data = {}) => {
  return request.post({ url: '/datasource/latest-use', data })
}

// 按数据源 ID 校验连接状态
export const validateById = (id: number | string) =>
  request.get({ url: '/datasource/validate/' + id })

// 保存数据源基础信息
export const save = async (data = {}): Promise<Dataset> => {
  nameTrim(data)
  return request.post({ url: '/datasource/record', data }).then(res => {
    return res?.data
  })
}

// 预检查删除数据源的影响范围
export const perDeleteDatasource = async (id): Promise<boolean> => {
  return request.post({ url: `/datasource/deletion-impact/${id}`, data: {} }).then(res => {
    return res?.data
  })
}

// 更新数据源基础信息
export const update = async (data = {}): Promise<Dataset> => {
  nameTrim(data)
  return request.put({ url: '/datasource', data }).then(res => {
    return res?.data
  })
}

// 移动数据源或文件夹节点
export const move = async (data = {}): Promise<Dataset> => {
  return request.post({ url: '/datasource/move', data }).then(res => {
    return res?.data
  })
}

// 重命名数据源或文件夹节点
export const reName = async (data = {}): Promise<Dataset> => {
  nameTrim(data)
  return request.post({ url: '/datasource/rename', data }).then(res => {
    return res?.data
  })
}

// 创建数据源文件夹
export const createFolder = async (data = {}): Promise<Dataset> => {
  nameTrim(data)
  return request.post({ url: '/datasource/folders', data }).then(res => {
    return res?.data
  })
}

// 检查同级数据源名称是否重复
export const checkRepeat = async (data = {}): Promise<Dataset> => {
  return request.post({ url: '/datasource/duplicate-check', data }).then(res => {
    return res?.data
  })
}

// 校验 API 数据源条目
export const checkApiItem = async (data = {}): Promise<IResponse> => {
  return request.post({ url: '/datasource/api-data-source-check', data }).then(res => {
    return res
  })
}

// 查询数据集选择树
export const datasetTree = async (data = {}): Promise<IResponse> => {
  return request
    .post({ url: '/dataset-tree/tree', data: { ...data, ...{ busiFlag: 'dataset' } } })
    .then(res => {
      return res?.data
    })
}

// 查询数据源选择树
export const getDsTree = async (data = {}): Promise<IResponse> => {
  return request
    .post({ url: '/datasource/tree', data: { ...data, ...{ busiFlag: 'datasource' } } })
    .then(res => {
      return res?.data
    })
}

// 按 ID 删除数据源
export const deleteById = (id: number) => request.delete({ url: '/datasource/' + id })

// 按 ID 查询数据源详情
export const getById = (id: number | string) => request.get({ url: '/datasource/detail/' + id })

// 按 ID 查询脱敏后的数据源详情
export const getHidePwById = (id: number | string) =>
  request.get({ url: '/datasource/password/masked/' + id })

// 按 ID 查询数据源简要信息
export const getSimpleDs = (id: number | string) =>
  request.get({ url: '/datasource/simple-info/' + id })

// 上传数据源文件
export const uploadFile = async (data): Promise<IResponse> => {
  return request
    .post({
      url: '/datasource/files/upload',
      data,
      loading: true,
      headersType: 'multipart/form-data;'
    })
    .then(res => {
      return res
    })
}

// 加载远程文件数据
export const loadRemoteFile = async (data = {}) => {
  return request.post({ url: '/datasource/remote-files/load', data })
}

// 分页查询数据源同步记录
export const listSyncRecord = (page: number, limit: number, dsId: number | string) =>
  request.post({ url: '/datasource/sync-records/' + dsId + '/' + page + '/' + limit })

// 查询当前计算引擎的数据源能力
export const getEngineDatasource = () => request.get({ url: '/engine/current' })

// 查询当前引擎是否支持设置密钥
export const supportSetKey = () => request.get({ url: '/engine/support-set-key' })
