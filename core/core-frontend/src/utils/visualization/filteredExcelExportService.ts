import { h } from 'vue'
import { cloneDeep } from 'lodash-es'
import { ElButton, ElMessage } from 'element-plus-secondary'
import { RefreshLeft } from '@element-plus/icons-vue'
import { getData } from '@/api/chart'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { useEmitt } from '@/hooks/web/useEmitt'
import { exportExcelDownload } from '@/views/chart/components/js/util'
import { exportPivotExcel } from '@/views/chart/components/js/panel/common/common_table'
import {
  EXCEL_EXPORT_CONTENT,
  EXCEL_EXPORT_SCOPE,
  buildExportChartExtRequest,
  defaultExportButtonConfig,
  defaultViewExcelExportConfig,
  explainExportValidation,
  validateExportTarget
} from './filteredExcelExport.mjs'

// 导出范围限定为当前筛选结果或全量数据
type ExportScope = 'currentFiltered' | 'all'
// 导出内容限定为视图数据、数据集数据或格式化表格
type ExportContent = 'view' | 'dataset' | 'formatted'

// 筛选导出入口所需的目标视图和导出选项
interface FilteredExcelExportOptions {
  targetViewId: string | number
  scope?: ExportScope
  content?: ExportContent
  dvName?: string
  onQueued?: () => void
  validateQueries?: boolean
}

// 将视图 ID 统一转换为字符串，便于和存储中的组件 ID 比较
const normalizeId = (id: string | number) => (id === null || id === undefined ? '' : String(id))

// 打开数据导出中心并定位到全部任务页
const exportCenterOpen = () => {
  useEmitt().emitter.emit('data-export-center', { activeName: 'ALL' })
}

// 展示后台导出排队提示，并提供跳转导出中心的入口
export const showExportQueuedMessage = (openExportCenter = exportCenterOpen) => {
  ElMessage({
    message: h('p', null, [
      '后台导出中,可前往',
      h(
        ElButton,
        {
          text: true,
          size: 'small',
          class: 'btn-text',
          onClick: openExportCenter
        },
        '数据导出中心'
      ),
      '查看进度，进行下载'
    ]),
    iconClass: 'el-icon-loading',
    icon: h(RefreshLeft) as any,
    showClose: true,
    customClass: 'crest-message-loading crest-message-export'
  } as any)
}

// 确保视图组件拥有完整的 Excel 导出配置
export const ensureViewExcelExportConfig = element => {
  element.excelExport = defaultViewExcelExportConfig(element.excelExport)
  return element.excelExport
}

// 确保导出按钮拥有完整的交互配置
export const ensureExportButtonConfig = element => {
  element.exportButton = defaultExportButtonConfig(element.exportButton)
  return element.exportButton
}

// 兼容接口返回包裹 data 和直接返回数据两种结构
const resolveChartData = data => {
  if (!data) {
    return null
  }
  return data.data || data
}

// 拉取目标视图数据并同步到大屏状态缓存
const fetchChartData = async (targetViewId: string, viewInfo, chartExtRequest) => {
  const dvMainStore = dvMainStoreWithOut()
  const response = await getData({
    ...cloneDeep(viewInfo),
    chartExtRequest
  })
  if ((response as any)?.code && (response as any).code !== 0) {
    throw new Error((response as any).msg || '获取图表数据失败')
  }
  dvMainStore.setViewDataDetails(targetViewId, response)
  return resolveChartData((response as any)?.data)
}

// 按当前筛选条件、导出范围和内容类型执行 Excel 导出
export const exportFilteredExcel = async ({
  targetViewId,
  scope = EXCEL_EXPORT_SCOPE.CURRENT_FILTERED,
  content = EXCEL_EXPORT_CONTENT.VIEW,
  dvName,
  onQueued,
  validateQueries = true
}: FilteredExcelExportOptions) => {
  const targetId = normalizeId(targetViewId)
  const dvMainStore = dvMainStoreWithOut()
  const validation = validateExportTarget({
    targetViewId: targetId,
    components: dvMainStore.componentData,
    canvasViewInfo: dvMainStore.canvasViewInfo,
    scope,
    content,
    validateQueries
  })

  if (!validation.valid) {
    ElMessage.error(explainExportValidation(validation))
    return false
  }

  const sourceViewInfo = cloneDeep(dvMainStore.getViewDetails(targetId) || validation.viewInfo)
  const lastRequest = dvMainStore.getLastViewRequestInfo(targetId) || sourceViewInfo.chartExtRequest
  const chartExtRequest = buildExportChartExtRequest({
    lastRequest,
    viewInfo: sourceViewInfo,
    scope
  })

  if (content === EXCEL_EXPORT_CONTENT.FORMATTED) {
    const instance = dvMainStore.getViewInstanceInfo(targetId)
    if (!instance) {
      ElMessage.error('当前表格尚未渲染完成，请稍后再导出')
      return false
    }
    await exportPivotExcel(instance, sourceViewInfo)
    return true
  }

  let viewDataInfo = resolveChartData(dvMainStore.getViewDataDetails(targetId))
  if (!viewDataInfo) {
    try {
      viewDataInfo = await fetchChartData(targetId, sourceViewInfo, chartExtRequest)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '获取图表数据失败')
      return false
    }
  }

  if (!viewDataInfo) {
    ElMessage.error('当前表格暂无可导出的数据')
    return false
  }

  const chart = {
    ...sourceViewInfo,
    chartExtRequest,
    data: viewDataInfo,
    downloadType: content,
    busiFlag: dvMainStore.dvInfo?.type
  }

  await exportExcelDownload(
    chart,
    dvName || dvMainStore.dvInfo?.name || sourceViewInfo.title,
    result => {
      if (result === 'error') {
        return
      }
      if (onQueued) {
        onQueued()
      } else {
        showExportQueuedMessage()
      }
    }
  )
  return true
}
