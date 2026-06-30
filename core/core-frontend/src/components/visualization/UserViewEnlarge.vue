<template>
  <el-dialog
    ref="enlargeDialog"
    :title="viewInfo?.title"
    :append-to-body="true"
    v-model="dialogShow"
    width="70vw"
    trigger="click"
    class="userViewEnlarge-class"
    :style="dialogStyle"
    @close="handleClose"
  >
    <template #header v-if="!isIframe">
      <div class="header-title">
        <div>{{ viewInfo?.title }}</div>
        <div class="export-button">
          <el-select
            v-if="optType === 'enlarge' && exportPermissions[0]"
            v-model="pixel"
            class="pixel-select"
            size="small"
          >
            <el-option-group v-for="group in pixelOptions" :key="group.label" :label="group.label">
              <el-option
                v-for="item in group.options"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-option-group>
          </el-select>
          <el-button
            class="m-button"
            v-if="optType === 'enlarge' && exportPermissions[0]"
            link
            @click="downloadViewImage"
          >
            <el-icon size="16" style="margin-right: 3px"><icon_download_outlined /></el-icon>
            {{ t('chart.export_img') }}
          </el-button>
          <el-button
            class="m-button"
            v-if="optType === 'details' && exportPermissions[1] && excelExportEnabled"
            link
            :loading="exportLoading"
            :disabled="
              requestStore.loadingMap[permissionStore.currentPath] > 0 ||
              state.dataFrom === 'template'
            "
            @click="downloadViewDetails('view')"
          >
            <el-icon size="16" style="margin-right: 3px"><icon_download_outlined /></el-icon>
            {{ t('chart.export_excel') }}
          </el-button>
          <el-button
            class="m-button"
            v-if="optType === 'details' && exportPermissions[2] && excelExportEnabled"
            link
            :loading="exportLoading"
            @click="downloadViewDetails('dataset')"
            :disabled="
              requestStore.loadingMap[permissionStore.currentPath] > 0 ||
              state.dataFrom === 'template'
            "
          >
            <el-icon size="16" style="margin-right: 3px"><icon_download_outlined /></el-icon>
            {{ t('chart.export_raw_details') }}
          </el-button>
          <el-button
            class="m-button"
            v-if="
              optType === 'details' &&
              exportPermissions[2] &&
              excelExportEnabled &&
              viewInfo.type === 'table-pivot'
            "
            link
            :loading="exportLoading"
            @click="exportAsFormattedExcel"
          >
            <el-icon color="#1F2329" size="16" style="margin-right: 3px"
              ><icon_download_outlined
            /></el-icon>
            {{ t('chart.export_excel_formatter') }}
          </el-button>
          <el-divider
            class="close-divider"
            direction="vertical"
            v-if="
              exportPermissions[0] ||
              (excelExportEnabled && (exportPermissions[1] || exportPermissions[2]))
            "
          />
        </div>
      </div>
    </template>
    <div
      v-loading="downLoading"
      :element-loading-text="t('visualization.export_loading')"
      element-loading-background="rgba(122, 122, 122, 1)"
      class="enlarge-outer"
      v-if="dialogShow"
    >
      <div
        id="enlarge-inner-content"
        class="enlarge-inner"
        :class="{
          'enlarge-inner-with-header': optType === 'details' && sourceViewType.includes('chart-mix')
        }"
        v-loading="requestStore.loadingMap[permissionStore.currentPath]"
        ref="viewContainer"
        :style="customExport"
      >
        <component-wrapper
          v-if="optType === 'enlarge'"
          class="enlarge-wrapper"
          :opt-type="optType"
          :view-info="viewInfo"
          :config="config"
          :dv-info="dvInfo"
          :font-family="canvasStyleData?.fontFamily"
          show-position="viewDialog"
        />
        <template v-if="optType === 'details' && !sourceViewType.includes('chart-mix')">
          <chart-component-s2
            v-if="!detailsError"
            :view="viewInfo"
            show-position="viewDialog"
            ref="chartComponentDetails"
          />
          <empty-background
            v-if="detailsError"
            :description="t('visualization.no_details')"
            img-type="noneWhite"
          />
        </template>
        <template v-else-if="optType === 'details' && sourceViewType.includes('chart-mix')">
          <el-tabs class="tab-header" v-model="activeName" @tab-change="handleClick">
            <el-tab-pane :label="t('chart.drag_block_value_axis_left')" name="left"></el-tab-pane>
            <el-tab-pane :label="t('chart.drag_block_value_axis_right')" name="right"></el-tab-pane>
          </el-tabs>
          <div style="flex: 1">
            <chart-component-s2
              v-if="activeName === 'left'"
              :view="viewInfo"
              show-position="viewDialog"
              ref="chartComponentDetails"
            />
            <chart-component-s2
              v-else-if="activeName === 'right'"
              :view="viewInfo"
              show-position="viewDialog"
              ref="chartComponentDetails2"
            />
          </div>
        </template>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import ComponentWrapper from '@/components/data-visualization/canvas/ComponentWrapper.vue'
import { computed, h, nextTick, reactive, ref } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { deepCopy } from '@/utils/utils'
import icon_download_outlined from '@/assets/svg/icon_download_outlined.svg'
import ChartComponentS2 from '@/views/chart/components/views/components/ChartComponentS2.vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { exportExcelDownload } from '@/views/chart/components/js/util'
import { storeToRefs } from 'pinia'
import { RefreshLeft } from '@element-plus/icons-vue'
import { assign, merge } from 'lodash-es'
import { useEmitt } from '@/hooks/web/useEmitt'
import { ElMessage, ElButton } from 'element-plus-secondary'
import { exportPivotExcel } from '@/views/chart/components/js/panel/common/common_table'
import { useRequestStoreWithOut } from '@/store/modules/request'
import { usePermissionStoreWithOut } from '@/store/modules/permission'
import { activeWatermarkCheckUser } from '@/components/watermark/watermark'
import { getCanvasStyle } from '@/utils/style'
import { exportPermission } from '@/utils/utils'
import EmptyBackground from '../empty-background/src/EmptyBackground.vue'
import { supportExtremumChartType } from '@/views/chart/components/js/extremumUitl'
import {
  defaultViewExcelExportConfig,
  isExportableTableView
} from '@/utils/visualization/filteredExcelExport.mjs'
import ChartCarouselTooltip from '@/views/chart/components/js/g2plot_tooltip_carousel'
import html2canvas from 'html2canvas'
import { getData } from '@/api/chart'
// 截图下载期间的临时渲染状态，用于固定导出尺寸并隐藏不适合截图的动态效果
const downLoading = ref(false)
const dvMainStore = dvMainStoreWithOut()
// 放大和明细弹窗的显隐状态
const dialogShow = ref(false)
const requestStore = useRequestStoreWithOut()
const permissionStore = usePermissionStoreWithOut()
// 当前弹窗内使用的图表配置副本
let viewInfo = ref<any>(null)
// 当前弹窗内使用的画布组件配置副本
const config = ref<any>(null)
// 弹窗内容容器实例，用于截图导出
const viewContainer = ref(null)
const { t } = useI18n()
// 当前弹窗操作类型，区分放大、明细等入口
const optType = ref(null)
// 明细弹窗主图表组件实例
const chartComponentDetails = ref(null)
// 组合图明细弹窗右侧图表组件实例
const chartComponentDetails2 = ref(null)
const { dvInfo, isIframe, canvasStyleData } = storeToRefs(dvMainStore)
// 明细导出接口调用期间的加载状态
const exportLoading = ref(false)
// 源图表类型，明细渲染时需要保留原图类型判断
const sourceViewType = ref()
// 组合图明细弹窗当前激活的左右标签页
const activeName = ref('left')
// 明细数据或渲染失败状态
const detailsError = ref(false)
const DETAIL_CHART_ATTR: DeepPartial<ChartObj> = {
  render: 'antv',
  type: 'table-info',
  customAttr: {
    basicStyle: {
      tableColumnMode: 'dialog',
      tablePageMode: 'pull'
    },
    tableHeader: {
      tableHeaderBgColor: 'rgba(255,255,255,0.3)',
      tableHeaderFontColor: '#7C7E81'
    },
    tableCell: {
      tableItemBgColor: 'rgba(255,255,255,0)',
      tableFontColor: '#7C7E81',
      enableTableCrossBG: false,
      mergeCells: false
    },
    tooltip: {
      show: false
    }
  },
  senior: {
    scrollCfg: {
      open: false
    }
  },
  showPosition: 'dialog'
}

// 弹窗运行态参数，保存缩放比例、源组件类型和数据来源
const state = reactive<any>({
  scale: 0.5,
  componentSourceType: null,
  dataFrom: null
})
const DETAIL_TABLE_ATTR: DeepPartial<ChartObj> = {
  senior: {
    scrollCfg: {
      open: false
    }
  },
  showPosition: 'dialog'
}

// 将画布主题色同步到 Element Plus 弹窗变量
const dialogStyle = computed(() => {
  return [
    { '--ed-dialog-bg-color': canvasStyleData.value.dialogBackgroundColor },
    { '--crest-dialog-text': canvasStyleData.value.dialogButton }
  ]
})

// 当前仪表板的导出权限集合
const exportPermissions = computed(() =>
  exportPermission(dvInfo.value['weight'], dvInfo.value['ext'])
)
// 判断当前表格类图表是否允许使用格式化 Excel 导出
const excelExportEnabled = computed(() => {
  if (!isExportableTableView(viewInfo.value)) {
    return true
  }
  return defaultViewExcelExportConfig(config.value?.excelExport).enabled
})

// 截图导出时临时覆盖弹窗画布样式，保证图片尺寸和画布主题一致
const customExport = computed(() => {
  const style =
    canvasStyleData.value && optType.value === 'enlarge'
      ? getCanvasStyle(canvasStyleData.value, 'canvas-main')
      : {}
  if (downLoading.value) {
    const bashStyle = pixel.value.split(' * ')
    style['width'] = bashStyle[0] + 'px!important'
    style['height'] = bashStyle[1] + 'px!important'
    return style
  } else {
    return style
  }
})

// 默认截图分辨率，用户可在导出前切换
const pixel = ref('1280 * 720')

const pixelOptions = [
  {
    label: 'Windows(16:9)',
    options: [
      {
        value: '1920 * 1080',
        label: '1920 * 1080'
      },
      {
        value: '1600 * 900',
        label: '1600 * 900'
      },
      {
        value: '1280 * 720',
        label: '1280 * 720'
      }
    ]
  },
  {
    label: 'MacOS(16:10)',
    options: [
      {
        value: '2560 * 1600',
        label: '2560 * 1600'
      },
      {
        value: '1920 * 1200',
        label: '1920 * 1200'
      },
      {
        value: '1680 * 1050',
        label: '1680 * 1050'
      }
    ]
  }
]
// 初始化放大或明细弹窗上下文，并按入口类型准备图表副本
const dialogInit = (canvasStyle, view, item, opt, params = { scale: 0.5 }) => {
  state.scale = params.scale
  sourceViewType.value = view.type || ''
  detailsError.value = false
  optType.value = opt
  dialogShow.value = true
  state.componentSourceType = view.type
  state.dataFrom = view.dataFrom
  viewInfo.value = deepCopy(view) as DeepPartial<ChartObj>
  viewInfo.value.customStyle.text.show = false
  config.value = deepCopy(item)
  if (opt === 'details') {
    if (!viewInfo.value.type?.includes('table')) {
      assign(viewInfo.value, DETAIL_CHART_ATTR)
      viewInfo.value.xAxis.forEach(i => (i.hide = false))
      viewInfo.value.yAxis.forEach(i => (i.hide = false))
      viewInfo.value['customAttr']['tableHeader']['tableHeaderFontColor'] =
        canvasStyleData.value.dialogButton
      viewInfo.value['customAttr']['tableCell']['tableFontColor'] =
        canvasStyleData.value.dialogButton
    } else {
      assign(viewInfo.value, DETAIL_TABLE_ATTR)
    }
    dataDetailsOpt()
  }
  nextTick(() => {
    initWatermark()
    ChartCarouselTooltip.paused()
    useEmitt().emitter.emit('showEnlargeDialog', true)
  })
}

// 优先复用主状态中的明细数据，缺失时再发起明细查询
const dataDetailsOpt = () => {
  nextTick(() => {
    const viewDataInfo = dvMainStore.getViewDataDetails(viewInfo.value.id)
    if (viewDataInfo) {
      renderDetailsChart(viewDataInfo)
      return
    }
    queryDetailsData()
  })
}

// 将明细数据渲染到弹窗图表，组合图需要分别渲染左右图表实例
const renderDetailsChart = (viewDataInfo, retry = 0) => {
  if (!viewDataInfo) {
    detailsError.value = true
    return
  }
  if (sourceViewType.value.includes('chart-mix')) {
    if (!viewDataInfo.left || !viewDataInfo.right) {
      detailsError.value = true
      return
    }
    if (!chartComponentDetails.value || !chartComponentDetails2.value) {
      retryRenderDetails(viewDataInfo, retry)
      return
    }
    chartComponentDetails.value.renderChartFromDialog(viewInfo.value, viewDataInfo.left)
    chartComponentDetails2.value.renderChartFromDialog(viewInfo.value, viewDataInfo.right)
    return
  }
  if (!chartComponentDetails.value) {
    retryRenderDetails(viewDataInfo, retry)
    return
  }
  chartComponentDetails.value.renderChartFromDialog(viewInfo.value, viewDataInfo)
}

// 等待弹窗内图表组件挂载完成，超过重试次数后标记为明细错误
const retryRenderDetails = (viewDataInfo, retry) => {
  if (retry >= 6) {
    detailsError.value = true
    return
  }
  window.requestAnimationFrame(() => renderDetailsChart(viewDataInfo, retry + 1))
}

// 根据源图表最近一次查询参数重新拉取明细数据
const queryDetailsData = async () => {
  const sourceViewInfo = deepCopy(dvMainStore.getViewDetails(viewInfo.value.id) || viewInfo.value)
  if (!sourceViewInfo?.tableId && sourceViewInfo?.dataFrom !== 'template') {
    detailsError.value = true
    return
  }
  try {
    const chartExtRequest = dvMainStore.getLastViewRequestInfo(viewInfo.value.id) || {
      filter: [],
      drill: [],
      resultCount: sourceViewInfo.resultCount || 1000,
      resultMode: sourceViewInfo.resultMode || 'all'
    }
    const res = await getData({
      ...sourceViewInfo,
      chartExtRequest
    })
    if (res?.code && res.code !== 0) {
      detailsError.value = true
      return
    }
    if (!res?.data) {
      detailsError.value = true
      return
    }
    dvMainStore.setViewDataDetails(viewInfo.value.id, res)
    detailsError.value = false
    renderDetailsChart(res?.data)
  } catch {
    detailsError.value = true
  }
}

// 组合图明细切换左右标签页时，重绘对应图表
const handleClick = tab => {
  nextTick(() => {
    const viewDataInfo = dvMainStore.getViewDataDetails(viewInfo.value.id)
    if (tab === 'left') {
      chartComponentDetails.value?.renderChartFromDialog(viewInfo.value, viewDataInfo.left)
    } else if (tab === 'right') {
      chartComponentDetails2.value?.renderChartFromDialog(viewInfo.value, viewDataInfo.right)
    }
  })
}

// 触发当前弹窗图表截图下载
const downloadViewImage = () => {
  htmlToImage()
}

// 导出当前图表明细数据，保留最近一次查询上下文
const downloadViewDetails = (downloadType = 'view') => {
  const viewDataInfo = dvMainStore.getViewDataDetails(viewInfo.value.id)
  const viewInfoSource = deepCopy(dvMainStore.getViewDetails(viewInfo.value.id))
  if (!viewDataInfo) {
    ElMessage.error(t('chart.field_is_empty_export_error'))
    return
  }
  const chartExtRequest = dvMainStore.getLastViewRequestInfo(viewInfo.value.id)
  const chart = {
    ...viewInfoSource,
    chartExtRequest,
    data: viewDataInfo,
    type: sourceViewType.value,
    downloadType: downloadType,
    busiFlag: dvInfo.value.type
  }
  exportLoading.value = true
  exportExcelDownload(chart, dvInfo.value.name, () => {
    openMessageLoading(exportData)
  })
  exportLoading.value = false
}

// 使用透视表实例导出带格式的 Excel
const exportAsFormattedExcel = () => {
  const s2Instance = dvMainStore.getViewInstanceInfo(viewInfo.value.id)
  if (!s2Instance) {
    return
  }
  const chart = dvMainStore.getViewDetails(viewInfo.value.id)
  exportPivotExcel(s2Instance, chart)
}
// 打开数据导出中心并定位到全部任务页
const exportData = () => {
  useEmitt().emitter.emit('data-export-center', { activeName: 'ALL' })
}

// 提示用户导出任务已提交，并提供跳转导出中心的入口
const openMessageLoading = cb => {
  const iconClass = `el-icon-loading`
  const customClass = `crest-message-loading crest-message-export`
  ElMessage({
    message: h('p', null, [
      t('data_fill.exporting'),
      h(
        ElButton,
        {
          text: true,
          size: 'small',
          class: 'btn-text',
          onClick: () => {
            cb()
          }
        },
        t('data_export.export_center')
      ),
      t('data_fill.progress_to_download')
    ]),
    iconClass,
    icon: h(RefreshLeft) as any,
    showClose: true,
    customClass
  } as any)
}
// 地图类图表截图前需要额外等待地图渲染事件，不直接进入全局下载遮罩
const mapChartTypes = ['bubble-map', 'flow-map', 'heat-map', 'map', 'symbolic-map']
// 将弹窗内容渲染为图片并触发浏览器下载
const htmlToImage = () => {
  downLoading.value = mapChartTypes.includes(viewInfo.value.type) ? false : true
  useEmitt().emitter.emit('renderChart-viewDialog-' + viewInfo.value.id)
  useEmitt().emitter.emit('l7-prepare-picture', viewInfo.value.id)
  // 表格和支持最值标记的图表渲染链路更长，需要预留更充分的截图等待时间
  const renderTime =
    viewInfo.value.type?.includes('table') ||
    supportExtremumChartType({ type: viewInfo.value.type })
      ? 2000
      : 500
  setTimeout(() => {
    initWatermark()
    html2canvas(viewContainer.value)
      .then(canvas => {
        const dom = document.body.appendChild(canvas)
        dom.style.display = 'none'
        document.body.removeChild(dom)
        const dataUrl = dom.toDataURL('image/png', 1)
        downLoading.value = false
        const a = document.createElement('a')
        a.setAttribute('download', viewInfo.value.title)
        a.href = dataUrl
        a.click()
        useEmitt().emitter.emit('l7-unprepare-picture', viewInfo.value.id)
        useEmitt().emitter.emit('renderChart-viewDialog-' + viewInfo.value.id)
        initWatermark()
      })
      .catch(error => {
        downLoading.value = false
        initWatermark()
        useEmitt().emitter.emit('l7-unprepare-picture', viewInfo.value.id)
        useEmitt().emitter.emit('renderChart-viewDialog-' + viewInfo.value.id)
        console.error('oops, something went wrong!', error)
      })
  }, renderTime)
}

// 按弹窗缩放比例重新初始化水印，保证截图和预览一致
const initWatermark = () => {
  activeWatermarkCheckUser('enlarge-inner-content', 'canvas-main', state.scale)
}
// 关闭弹窗时同步外部悬浮提示状态，并销毁放大图提示实例
const handleClose = () => {
  useEmitt().emitter.emit('showEnlargeDialog', false)
  ChartCarouselTooltip.closeEnlargeDialogDestroy(viewInfo.value.id)
}
defineExpose({
  dialogInit
})
</script>

<style lang="less">
.userViewEnlarge-class {
  .ed-dialog__close {
    color: var(--crest-dialog-text) !important;
  }
  .ed-dialog__header {
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    margin-right: unset;
  }
  .ed-dialog__headerbtn {
    position: unset;
  }
  .header-title {
    width: 100%;
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    color: var(--crest-dialog-text);

    font-size: 16px;
    font-weight: 500;
    line-height: 24px;
  }
}
</style>
<style lang="less" scoped>
.export-button {
  .pixel-select {
    width: 125px;
    margin-right: 8px;
    :deep(.ed-select__wrapper) {
      background-color: rgba(255, 255, 255, 0) !important;
    }
    :deep(.ed-select__placeholder) {
      color: var(--crest-dialog-text);
    }
  }

  .m-button {
    color: var(--crest-dialog-text);
    font-size: 14px;
    font-style: normal;
    font-weight: 400;
  }

  .ed-button.is-link {
    font-size: 14px;
    font-weight: 400;
    padding: 4px;

    &:not(.is-disabled):focus,
    &:not(.is-disabled):hover {
      color: var(--crest-dialog-text) !important;
      opacity: 0.5;
      border-color: transparent;
      background-color: rgba(31, 35, 41, 0.1);
    }
    &:not(.is-disabled):active {
      color: #1f2329;
      border-color: transparent;
      background-color: rgba(31, 35, 41, 0.2);
    }
  }
}
.close-divider {
  margin: 0 16px 0 12px;
}
.enlarge-outer {
  position: relative;
  height: 65vh;
  overflow: hidden;
  .enlarge-inner {
    position: relative;
    width: 100%;
    height: 100%;
    background-size: 100% 100% !important;
  }
  .enlarge-inner-with-header {
    display: flex;
    flex-direction: column;
  }
  .enlarge-wrapper {
    width: 100%;
    height: 100%;
  }
}
.tab-header {
  margin-top: -10px;
  margin-bottom: 10px;
  --ed-tabs-header-height: 34px;
  --custom-tab-color: #646a73;

  :deep(.ed-tabs__nav-wrap::after) {
    background-color: unset;
  }

  &.dark {
    --custom-tab-color: #a6a6a6;
  }

  :deep(.ed-tabs__item) {
    font-weight: 400;
    font-size: 12px;
    padding: 0 8px !important;
    margin-right: 12px;
    color: var(--custom-tab-color);
  }
  :deep(.is-active) {
    font-weight: 500;
    color: var(--ed-color-primary, #3b82f6);
  }

  :deep(.ed-tabs__nav-scroll) {
    padding-left: 0 !important;
  }

  :deep(.ed-tabs__header) {
    margin: 0 !important;
  }

  :deep(.ed-tabs__content) {
    height: calc(100% - 35px);
    overflow-y: auto;
    overflow-x: hidden;
  }
}
</style>
