import request from '@/config/axios'
import { originNameHandleWithArr, originNameHandleBackWithArr } from '@/utils/CalculateFields'
import { cloneDeep } from 'lodash-es'
export interface Field {
  id: number | string
  datasourceId: number | string
  datasetTableId: number | string
  datasetGroupId: number | string
  originName: string
  name: string
  engineFieldName: string
  groupType: string
  type: string
  fieldType: number
  extractedFieldType: number
  extField: number
  checked: boolean
  fieldShortName: string
  desensitized: boolean
  variableName?: string
  params?: any[]
  [key: string]: any
}

export interface ComponentInfo {
  id: string
  name: string
  fieldType: number
  type: string
  datasetId: string
}

export const getFieldByDQ = async (id, chartId, data): Promise<IResponse> => {
  return request.post({ url: `/chart/by-dataset-query/${id}/${chartId}`, data: data }).then(res => {
    originNameHandleBackWithArr(res?.data, ['dimensionList', 'quotaList'])
    return res?.data
  })
}

export const copyChartField = async (id, chartId): Promise<IResponse> => {
  return request.post({ url: `/chart/fields/copy/${id}/${chartId}`, data: {} }).then(res => {
    return res?.data
  })
}

export const deleteChartField = async (id): Promise<IResponse> => {
  return request.delete({ url: `/chart/fields/${id}`, data: {} }).then(res => {
    return res?.data
  })
}

export const deleteChartFieldByChartId = async (chartId): Promise<IResponse> => {
  return request.delete({ url: `/chart/chart-fields/${chartId}`, data: {} }).then(res => {
    return res?.data
  })
}

// 通过图表对象获取数据
export const getData = async (data): Promise<IResponse> => {
  delete data.data
  const copyData = cloneDeep(data)
  const fields = [
    'xAxis',
    'xAxisExt',
    'yAxis',
    'yAxisExt',
    'extBubble',
    'extLabel',
    'extStack',
    'extTooltip',
    'extColor'
  ]
  const dataFields = ['fields', 'sourceFields']
  originNameHandleWithArr(copyData, fields)
  return request.post({ url: '/chart-data/data', data: copyData }).then(res => {
    if (res?.canceled) {
      return Promise.reject(res)
    }
    if (res.code === 0) {
      originNameHandleBackWithArr(res?.data, fields)
      // 动态计算字段在数据中，也需要转码
      originNameHandleWithArr(res?.data?.data, dataFields)
      originNameHandleBackWithArr(res?.data?.data, dataFields)
      originNameHandleBackWithArr(res?.data?.data?.left, ['fields'])
      originNameHandleBackWithArr(res?.data?.data?.right, ['fields'])
      return res?.data
    } else {
      originNameHandleBackWithArr(res, fields)
      originNameHandleBackWithArr(res?.data, dataFields)
      originNameHandleBackWithArr(res?.data?.left, ['fields'])
      originNameHandleBackWithArr(res?.data?.right, ['fields'])
      return res
    }
  })
}

export const innerExportDetails = async (data): Promise<IResponse> => {
  return request.post({
    url: '/chart-data/internal-export/details',
    method: 'post',
    data: data,
    loading: true,
    responseType: 'blob'
  })
}

export const innerExportDataSetDetails = async (data): Promise<IResponse> => {
  return request.post({
    url: '/chart-data/internal-export/dataset-details',
    method: 'post',
    data: data,
    loading: true,
    responseType: 'blob'
  })
}

// 通过图表id获取数据
export const getChart = async (id): Promise<IResponse> => {
  return request.post({ url: `/chart/detail/${id}`, data: {} }).then(res => {
    return res?.data
  })
}

// 单个图表保存测试
export const saveChart = async (data): Promise<IResponse> => {
  delete data.data
  return request.post({ url: '/chart/record', data }).then(res => {
    return res?.data
  })
}

// 获取单个字段枚举值
export const getFieldData = async ({ fieldId, fieldType, data }): Promise<IResponse> => {
  delete data.data
  return request
    .post({ url: `/chart-data/field-values/${fieldId}/${fieldType}`, data })
    .then(res => {
      return res
    })
}

// 获取下钻字段枚举值
export const getDrillFieldData = async ({ fieldId, data }): Promise<IResponse> => {
  delete data.data
  return request.post({ url: `/chart-data/drill-field-values/${fieldId}`, data }).then(res => {
    return res
  })
}

export const getChartDetail = async (id: string): Promise<IResponse> => {
  return request.post({ url: `chart/detail/${id}`, data: {} }).then(res => {
    return res
  })
}

// 校验当前数据是否满足业务规则
export const checkSameDataSet = async (viewIdSource, viewIdTarget) =>
  request.get({ url: '/chart/same-dataset-check/' + viewIdSource + '/' + viewIdTarget })
