import request from '@/config/axios'
import type { BusiTreeRequest } from '@/models/tree/TreeNode'
import { originNameHandleWithArr } from '@/utils/CalculateFields'
import { cloneDeep } from 'lodash-es'
export interface ResourceOrFolder {
  name: string
  id?: number | string
  pid?: number | string
  nodeType: 'folder' | 'leaf'
  type: string
  mobileLayout: boolean
  status: boolean
}

export interface Panel {
  name: string
  type: string
  updateTime: number
  createBy: string
  updateBy: string
}

// 查询可复制资源详情，用于复用或复制前预取资源信息
export const findCopyResource = async (dvId, busiFlag): Promise<IResponse> => {
  return request.get({ url: '/data-visualization/copy-resource/' + dvId + '/' + busiFlag })
}

// 查询可视化资源详情，缺少业务类型时先回查资源类型
export const findById = async (
  dvId,
  busiFlag,
  attachInfo = { source: 'main', taskId: null }
): Promise<IResponse> => {
  let busiFlagResult = busiFlag
  if (!busiFlagResult) {
    await findDvType(dvId).then(res => {
      busiFlagResult = res.data
    })
  }
  const data = { id: dvId, busiFlag: busiFlagResult, ...attachInfo }
  return request.post({ url: '/data-visualization/detail', data })
}

// 校验当前资源版本是否仍可编辑
export const updateCheckVersion = dvId =>
  request.get({ url: `/data-visualization/check-version/${dvId}` })

// 查询资源树
export const queryTreeApi = async (data: BusiTreeRequest): Promise<IResponse> => {
  return request.post({ url: '/data-visualization/tree', data }).then(res => {
    return res?.data
  })
}

// 查询带交互状态的业务资源树
export const queryBusiTreeApi = async (data): Promise<IResponse> => {
  return request.post({ url: '/data-visualization/interactive-tree', data }).then(res => {
    return res?.data
  })
}

// 查询指定资源所属业务类型
export const findDvType = async dvId => request.get({ url: `/data-visualization/type/${dvId}` })

// 保存画布数据
export const save = data => request.post({ url: '/data-visualization/canvas', data })

// 检查画布变更对发布和缓存的影响
export const checkCanvasChange = data =>
  request.post({ url: '/data-visualization/canvas-change-impact', data, loading: true })

// 保存画布数据并显示加载态
export const saveCanvas = data =>
  request.post({ url: '/data-visualization/canvas', data, loading: true })

// 更新资源发布状态
export const updatePublishStatus = data =>
  request.post({ url: '/data-visualization/publish-status', data, loading: false })

// 将资源恢复到已发布状态
export const recoverToPublished = data =>
  request.post({ url: '/data-visualization/published-state/recover', data, loading: true })
// 校验应用画布名称是否可用
export const appCanvasNameCheck = async data =>
  request.post({ url: '/data-visualization/canvas-name-check', data, loading: false })

// 更新资源基础信息
export const updateBase = data => request.put({ url: '/data-visualization/base', data })

// 更新画布内容，并在提交前恢复字段原始名称
export const updateCanvas = data => {
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

  for (const key in copyData.canvasViewInfo) {
    originNameHandleWithArr(copyData.canvasViewInfo[key], fields)
  }
  return request.put({ url: '/data-visualization/canvas', data: copyData, loading: true })
}

// 移动资源到目标目录
export const moveResource = data => request.post({ url: '/data-visualization/move', data })

// 复制资源
export const copyResource = data => request.post({ url: '/data-visualization/copy', data })

// 将资源移入回收站
export const deleteLogic = (dvId, busiFlag) =>
  request.delete({ url: '/data-visualization/trash/' + dvId + '/' + busiFlag })

// 查询可视化主题及其分组
export const querySubjectWithGroupApi = data =>
  request.post({ url: '/visualization-subject/subjects-with-groups', data })

// 保存或更新可视化主题
export const saveOrUpdateSubject = data => request.put({ url: '/visualization-subject', data })

// 删除可视化主题
export const deleteSubject = id => request.delete({ url: '/visualization-subject/' + id })

// 校验数据大屏名称是否可用
export const dvNameCheck = async data =>
  request.post({ url: '/data-visualization/name-check', data })

// 收藏资源
export const storeApi = (data): Promise<IResponse> => {
  return request.post({ url: '/store/runs', data })
}

// 查询资源收藏状态
export const storeStatusApi = (id: string): Promise<IResponse> => {
  return request.get({ url: `/store/favorited/${id}` })
}

// 解压服务端资源包
export const decompression = async data =>
  request.post({ url: '/data-visualization/decompression', data, loading: true })

// 上传并解压本地资源包
export const decompressionLocalFile = async data =>
  request.post({
    url: '/data-visualization/decompression/local-file',
    data,
    loading: true,
    headersType: 'multipart/form-data'
  })

// 查询资源下的图表明细列表
export const viewDetailList = dvId => {
  return request.get({
    url: '/data-visualization/view-detail-list/' + dvId,
    method: 'get',
    loading: false
  })
}

// 查询面板内组件信息
export const getComponentInfo = dvId => {
  return request.get({
    url: '/panel/view/getComponentInfo/' + dvId,
    loading: false
  })
}

// 导出为应用前执行可导出性校验
export const export2AppCheck = params => {
  return request.post({
    url: '/data-visualization/app-export-check',
    data: params,
    loading: true
  })
}

// 查询可视化资源外部参数关联的数据源信息
export const queryOuterParamsDsInfo = async dvId => {
  return request.get({
    url: '/outer-params/datasources/by-visualization/' + dvId,
    method: 'get',
    loading: false
  })
}

// 查询分享基础配置
export const queryShareBaseApi = () => {
  return request.get({
    url: '/sys-parameter/share-base',
    loading: false
  })
}

// 记录应用导出日志
export const exportLogApp = data =>
  request.post({ url: '/data-visualization/export-logs/app', data })

// 记录模板导出日志
export const exportLogTemplate = data =>
  request.post({ url: '/data-visualization/export-logs/template', data })

// 记录 PDF 导出日志
export const exportLogPDF = data =>
  request.post({ url: '/data-visualization/export-logs/pdf', data })

// 记录图片导出日志
export const exportLogImg = data =>
  request.post({ url: '/data-visualization/export-logs/image', data })
