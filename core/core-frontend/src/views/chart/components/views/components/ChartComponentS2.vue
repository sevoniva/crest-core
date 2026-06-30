<script lang="ts" setup>
import {
  computed,
  CSSProperties,
  inject,
  nextTick,
  onBeforeUnmount,
  onMounted,
  PropType,
  reactive,
  ref,
  shallowRef,
  ShallowRef,
  toRaw,
  toRefs
} from 'vue'
import { getData } from '@/api/chart'
import chartViewManager from '@/views/chart/components/js/panel'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import ViewTrackBar from '@/components/visualization/ViewTrackBar.vue'
import { storeToRefs } from 'pinia'
import { S2ChartView } from '@/views/chart/components/js/panel/types/impl/s2'
import { ElPagination } from 'element-plus-secondary'
import ChartError from '@/views/chart/components/views/components/ChartError.vue'
import { defaultsDeep, cloneDeep, debounce } from 'lodash-es'
import { BASE_VIEW_CONFIG } from '../../editor/util/chart'
import { customAttrTrans, customStyleTrans, recursionTransObj } from '@/utils/canvasStyle'
import { deepCopy, isISOMobile, isMobile } from '@/utils/utils'
import { useEmitt } from '@/hooks/web/useEmitt'
import { isDashboard, trackBarStyleCheck } from '@/utils/canvasUtils'
import { type SpreadSheet } from '@antv/s2'
import { parseJson } from '../../js/util'
import { useI18n } from '@/hooks/web/useI18n'

// 大屏主状态提供图表联动、跳转和移动端上下文
const dvMainStore = dvMainStoreWithOut()
// 解构 S2 图表渲染需要使用的全局状态
const {
  nowPanelTrackInfo,
  nowPanelJumpInfo,
  mobileInPc,
  canvasStyleData,
  embeddedCallBack,
  inMobile
} = storeToRefs(dvMainStore)
// 全局事件总线用于图表和外部工具栏通信
const { emitter } = useEmitt()
// S2 图表组件内置文案的翻译入口
const { t } = useI18n()

// S2 图表组件入参，包含视图配置、展示位置、终端和下钻层级
const props = defineProps({
  element: {
    type: Object,
    default() {
      return {
        propValue: null
      }
    }
  },
  view: {
    type: Object as PropType<ChartObj>,
    default() {
      return {
        propValue: null
      }
    }
  },
  showPosition: {
    type: String,
    required: false,
    default: 'canvas'
  },
  scale: {
    type: Number,
    required: false,
    default: 1
  },
  terminal: {
    type: String,
    default: 'pc'
  },
  drillLength: {
    type: Number,
    required: false,
    default: 0
  },
  //图表渲染id后缀
  suffixId: {
    type: String,
    required: false,
    default: 'common'
  },
  fontFamily: {
    type: String,
    required: false,
    default: 'inherit'
  }
})

// S2 图表交互事件透传给父组件处理
const emit = defineEmits(['onPointClick', 'onChartClick', 'onDrillFilters', 'onJumpClick'])
// 大屏移动端预览需要使用移动端交互逻辑
const dataVMobile = !isDashboard() && isMobile()

// 常用入参保持响应式引用，避免在异步渲染中丢失最新值
const { view, showPosition, scale, terminal, drillLength, suffixId } = toRefs(props)

// 图表渲染错误标记
const isError = ref(false)
// 图表渲染错误文案
const errMsg = ref('')
// 外部注入的扩展请求参数
const chartExtRequest = inject('chartExtRequest') as ShallowRef<object>

// S2 图表运行时状态，包含联动、分页、加载和图片放大信息
const state = reactive({
  curActionId: null,
  curTrackMenu: [],
  trackBarStyle: {
    position: 'absolute',
    left: '50px',
    top: '50px'
  },
  linkageActiveParam: null,
  pointParam: null,
  loading: false,
  data: { fields: [] }, // 图表数据
  pageInfo: {
    total: 0,
    pageSize: 20,
    currentPage: 1
  },
  totalItems: 0,
  showPage: false,
  pageStyle: 'simple',
  currentPageSize: 0,
  imgEnlarge: false,
  imgSrc: ''
})
// 支持分页的表格图表类型
const PAGE_CHARTS = ['table-info', 'table-normal']
// 图表数据体积较大，使用浅响应式避免深层代理开销
let chartData = shallowRef<Partial<Chart['data']>>({
  fields: []
})

// 当前图表容器编号，组合展示位置、图表编号和后缀避免冲突
const containerId = 'container-' + showPosition.value + '-' + view.value.id + '-' + suffixId.value
// 联动轨迹条组件引用
const viewTrack = ref(null)

// 拉取图表数据，并根据分页配置更新扩展请求参数
const calcData = (viewInfo: Chart, callback, resetPageInfo = true) => {
  const customAttr = viewInfo.customAttr as any
  viewInfo.chartExtRequest ||= {}
  if (customAttr.basicStyle.tablePageStyle === 'general') {
    if (state.currentPageSize !== 0) {
      viewInfo.chartExtRequest.pageSize = state.currentPageSize
      state.pageInfo.pageSize = state.currentPageSize
    } else {
      viewInfo.chartExtRequest.pageSize = state.pageInfo.pageSize
    }
  } else {
    delete viewInfo.chartExtRequest?.pageSize
  }
  if (viewInfo.tableId || viewInfo['dataFrom'] === 'template') {
    isError.value = false
    const v = JSON.parse(JSON.stringify(viewInfo))
    getData(v)
      .then(res => {
        const dataRes = res as any
        if (res.code && res.code !== 0) {
          isError.value = true
          errMsg.value = res.msg
        } else {
          chartData.value = res?.data as Partial<Chart['data']>
          state.totalItems = dataRes?.totalItems
          dvMainStore.setViewDataDetails(viewInfo.id, res)
          emit('onDrillFilters', dataRes?.drillFilters)
          renderChart(res as unknown as Chart, resetPageInfo)
        }
        callback?.()
      })
      .catch(() => {
        callback?.()
      })
  } else {
    callback?.()
  }
}
// S2 图表对象不用响应式，避免第三方实例被代理
let myChart: SpreadSheet = null
// 实际渲染的图表信息，已按缩放和终端适配
let actualChart: ChartObj
// 弹窗回传数据后复用现有渲染入口刷新图表
const renderChartFromDialog = (viewInfo: Chart, chartDataInfo) => {
  chartData.value = chartDataInfo
  renderChart(viewInfo, false)
}
// 处理存量图表的默认值，避免旧配置缺字段导致渲染异常
const handleDefaultVal = (chart: Chart) => {
  const customAttr = parseJson(chart.customAttr)
  // 明细表默认合并单元格，存量的不合并
  if (customAttr.tableCell.mergeCells === undefined) {
    customAttr.tableCell.mergeCells = false
  }
  if (chart.type === 'table-pivot') {
    if (!customAttr.tableTotal?.row?.subTotalsDimensionsNew) {
      customAttr.tableTotal.row.subTotalsDimensionsNew =
        !!customAttr.tableTotal.row.subTotalsDimensionsNew
    }
    const { tableHeader } = customAttr
    // 存量透视表处理
    if (!tableHeader.tableHeaderColBgColor) {
      tableHeader.tableHeaderColBgColor = tableHeader.tableHeaderBgColor
      tableHeader.tableHeaderColFontColor = tableHeader.tableHeaderFontColor
      tableHeader.tableTitleColFontSize = tableHeader.tableTitleFontSize
      ;(tableHeader as any).tableHeaderColAlign = tableHeader.tableHeaderAlign
      tableHeader.isColBolder = tableHeader.isBolder
      tableHeader.isColItalic = tableHeader.isItalic

      tableHeader.tableHeaderCornerBgColor = tableHeader.tableHeaderBgColor
      tableHeader.tableHeaderCornerFontColor = tableHeader.tableHeaderFontColor
      tableHeader.tableTitleCornerFontSize = tableHeader.tableTitleFontSize
      ;(tableHeader as any).tableHeaderCornerAlign = tableHeader.tableHeaderAlign
      tableHeader.isCornerBolder = tableHeader.isBolder
      tableHeader.isCornerItalic = tableHeader.isItalic
    }
  }
}
// 组装实际渲染配置并触发防抖渲染
const renderChart = (viewInfo: Chart, resetPageInfo: boolean) => {
  if (!viewInfo) {
    return
  }
  handleDefaultVal(viewInfo)
  // view 为引用对象 需要存库 view.data 直接赋值会导致保存不必要的数据
  actualChart = deepCopy({
    ...defaultsDeep(viewInfo, cloneDeep(BASE_VIEW_CONFIG)),
    data: chartData.value,
    fontFamily: props.fontFamily
  } as ChartObj)

  recursionTransObj(customAttrTrans, actualChart.customAttr, scale.value, terminal.value)
  recursionTransObj(customStyleTrans, actualChart.customStyle, scale.value, terminal.value)

  setupPage(actualChart, resetPageInfo)
  nextTick(() => debounceRender())
}

// 防抖销毁旧 S2 实例并创建新图表实例
const debounceRender = debounce(() => {
  ;(myChart?.facet as any)?.timer?.stop()
  myChart?.facet?.cancelScrollFrame()
  myChart?.destroy()
  myChart?.getCanvasElement()?.remove()
  const chartView = chartViewManager.getChartView(
    actualChart.render,
    actualChart.type
  ) as S2ChartView<any>
  myChart = chartView.drawChart({
    container: containerId,
    chart: toRaw(actualChart),
    chartObj: myChart,
    pageInfo: state.pageInfo,
    action,
    resizeAction,
    touchAction
  })
  myChart?.render()
  dvMainStore.setViewInstanceInfo(actualChart.id, myChart)
  initScroll()
}, 500)

// 根据图表类型和样式配置初始化分页显示状态
const setupPage = (chart: ChartObj, resetPageInfo?: boolean) => {
  const customAttr = chart.customAttr
  if (!PAGE_CHARTS.includes(chart.type) || customAttr.basicStyle.tablePageMode !== 'page') {
    state.showPage = false
    return
  }
  const pageInfo = state.pageInfo
  state.pageStyle = customAttr.basicStyle.tablePageStyle
  if (state.pageStyle !== 'general') {
    pageInfo.pageSize = customAttr.basicStyle.tablePageSize ?? 20
  }
  if (state.totalItems > state.pageInfo.pageSize || state.pageStyle === 'general') {
    pageInfo.total = state.totalItems
    state.showPage = true
  } else {
    state.showPage = false
  }
  if (resetPageInfo) {
    state.pageInfo.currentPage = 1
  }
  dvMainStore.setViewPageInfo(chart.id, state.pageInfo)
}

/**
 * 鼠标移入表格时暂停自动滚动
 */
const mouseMove = () => {
  ;(myChart?.facet as any)?.timer?.stop()
}

/**
 * 鼠标移出表格后恢复自动滚动
 */
const mouseLeave = () => {
  initScroll()
}

/**
 * 表格自动滚动定时器
 */
let scrollTimer
/**
 * 初始化或重置表格自动滚动
 */
const initScroll = () => {
  scrollTimer && clearTimeout(scrollTimer)
  scrollTimer = setTimeout(() => {
    // 首先回到最顶部，然后计算行高*行数作为top，最后判断：如果top<数据量*行高，继续滚动，否则回到顶部
    const customAttr = actualChart?.customAttr
    const senior = actualChart?.senior
    if (
      myChart &&
      senior?.scrollCfg?.open &&
      chartData.value.tableRow?.length &&
      PAGE_CHARTS.includes(props.view.type) &&
      !state.showPage
    ) {
      // 防止多次渲染
      ;(myChart.facet as any).timer?.stop()
      // 已滚动的距离
      let scrolledOffset = myChart.store.get('scrollY') || 0
      // 平滑滚动，兼容原有的滚动速率设置
      // 假设原设定为 2 行间隔 2 秒，换算公式为: 滚动到底部的时间 = 未展示部分行数 / 2行 * 2秒
      const offsetHeight = document.getElementById(containerId).offsetHeight
      // 没显示就不滚了
      if (!offsetHeight) {
        return
      }
      const rowHeight = customAttr.tableCell.tableItemHeight
      const headerHeight =
        customAttr.tableHeader.showTableHeader === false
          ? 1
          : customAttr.tableHeader.tableTitleHeight
      const scrollBarSize = myChart.theme.scrollBar.size
      const basicStyle = customAttr.basicStyle

      // 开启自动换行时，使用 facet 的 viewCellHeights 或 scrollTargetMaxOffset 获取实际的最大滚动距离
      let maxScrollY: number
      if (basicStyle?.autoWrap) {
        // 从滚动条获取实际的滚动范围
        const vScrollBar = myChart.facet?.vScrollBar
        if (vScrollBar) {
          // 直接取滚动条配置的最大滚动距离
          maxScrollY = vScrollBar.scrollTargetMaxOffset
        } else {
          // 如果无法获取滚动条信息，尝试使用 viewCellHeights
          const viewCellHeights = myChart.facet?.viewCellHeights
          if (viewCellHeights) {
            const rowsHeight = viewCellHeights.getTotalHeight()
            const viewHeight = offsetHeight - headerHeight
            maxScrollY = Math.max(0, rowsHeight - viewHeight + scrollBarSize)
          } else {
            maxScrollY =
              rowHeight * chartData.value.tableRow.length +
              headerHeight -
              offsetHeight +
              scrollBarSize
          }
        }
      } else {
        maxScrollY =
          rowHeight * chartData.value.tableRow.length + headerHeight - offsetHeight + scrollBarSize
      }

      // 显示内容没撑满
      if (maxScrollY < scrollBarSize) {
        return
      }
      // 到底了重置一下，使用实际的最大滚动距离判断
      if (scrolledOffset >= maxScrollY - 1) {
        myChart.store.set('scrollY', 0)
        myChart.render()
        scrolledOffset = 0
      }

      let scrollViewCount: number
      if (basicStyle?.autoWrap && myChart.facet?.viewCellHeights) {
        // 如果开启了自动换行，计算当前未展示的内容高度所对应的比例，再乘以总行数，以获得大致的未展示行数
        const totalHeight = myChart.facet.viewCellHeights.getTotalHeight()
        const unViewedRatio = totalHeight > 0 ? (maxScrollY - scrolledOffset) / totalHeight : 0
        scrollViewCount = chartData.value.tableRow.length * unViewedRatio
      } else {
        const viewedHeight = offsetHeight - headerHeight - scrollBarSize + scrolledOffset
        scrollViewCount = chartData.value.tableRow.length - viewedHeight / rowHeight
      }

      const duration = (scrollViewCount / senior.scrollCfg.row) * senior.scrollCfg.interval
      myChart.facet.scrollWithAnimation(
        { offsetY: { value: maxScrollY, animate: false } },
        duration,
        initScroll
      )
    }
  }, 1500)
}

/**
 * 判断当前表格是否展示分页控件
 */
const showPage = computed(() => {
  if (!PAGE_CHARTS.includes(view.value.type)) {
    return false
  }
  return state.showPage
})

/**
 * 处理分页页码切换
 */
const handleCurrentChange = pageNum => {
  let extReq = { goPage: pageNum }
  if (chartExtRequest.value) {
    extReq = { ...extReq, ...chartExtRequest.value }
  }
  const chart = { ...view.value, chartExtRequest: extReq }
  calcData(chart, null, false)
}

/**
 * 处理分页大小切换
 */
const handlePageSizeChange = pageSize => {
  if (state.pageStyle === 'general') {
    state.currentPageSize = pageSize
    emitter.emit('set-page-size', pageSize)
    state.pageInfo.currentPage = 1
  }
  let extReq = { pageSize: pageSize }
  if (chartExtRequest.value) {
    extReq = { ...extReq, ...chartExtRequest.value }
  }
  const chart = { ...view.value, chartExtRequest: extReq }
  calcData(chart, null, false)
}

/**
 * 嵌入模式下转发点位点击事件
 */
const pointClickTrans = () => {
  if (embeddedCallBack.value === 'yes') {
    trackClick('pointClick')
  }
}

/**
 * 处理触摸端图表操作菜单触发
 */
const touchAction = (callback, fieldId) => {
  if (fieldId) {
    state.curActionId = fieldId
  }
  if (!trackMenu.value.length) {
    callback?.()
  }
}

/**
 * 处理表格单元格点击后的下钻、联动和跳转动作
 */
const action = param => {
  state.pointParam = param
  state.curActionId = param.data.name
  state.curTrackMenu = trackMenuCalc(state.curActionId)
  // 点击
  pointClickTrans()
  // 下钻 联动 跳转
  if (trackMenu.value.length < 2) {
    // 只有一个事件直接调用
    trackClick(trackMenu.value[0])
  } else {
    // 图表关联多个事件
    const barStyleTemp = {
      left: param.x - 50,
      top: param.y + 10
    }
    trackBarStyleCheck(props.element, barStyleTemp, props.scale, trackMenu.value.length)
    if (dataVMobile) {
      state.trackBarStyle.left = barStyleTemp.left + 40 + 'px'
      state.trackBarStyle.top = barStyleTemp.top + 70 + 'px'
    } else {
      state.trackBarStyle.left = barStyleTemp.left + 'px'
      state.trackBarStyle.top = barStyleTemp.top + 'px'
    }

    viewTrack.value.trackButtonClick(view.value.id)
  }
}

// 执行当前点击菜单对应的联动、下钻、跳转或放大动作
const trackClick = trackAction => {
  const param = state.pointParam
  if (!param?.data?.dimensionList) {
    return
  }
  const linkageParam = {
    option: 'linkage',
    name: state.pointParam.data.name,
    viewId: view.value.id,
    dimensionList: state.pointParam.data.dimensionList,
    quotaList: state.pointParam.data.quotaList
  }
  // 明细表 汇总表特殊处理 1.点击维度传递触发字段的值 2.点击指标传递的值非触发的维度字段值
  if (['table-info', 'table-normal'].includes(view.value.type)) {
    linkageParam.quotaList = []
    const dimensionIds = []
    const quotaIds = []
    view.value.xAxis.forEach(xd => {
      if (xd.groupType === 'd') {
        dimensionIds.push(xd.id)
      } else {
        quotaIds.push(xd.id)
      }
    })
    view.value.yAxis.forEach(xd => {
      if (xd.groupType === 'd') {
        dimensionIds.push(xd.id)
      } else {
        quotaIds.push(xd.id)
      }
    })
    if (dimensionIds.includes(param.data.name)) {
      linkageParam.dimensionList = linkageParam.dimensionList.filter(
        dimension => dimension.id === param.data.name
      )
    } else if (quotaIds.includes(param.data.name)) {
      linkageParam.dimensionList = linkageParam.dimensionList.filter(dimension =>
        dimensionIds.includes(dimension.id)
      )
    }
    view.value
  }
  const jumpParam = {
    option: 'jump',
    name: state.pointParam.data.name,
    viewId: view.value.id,
    dimensionList: state.pointParam.data.dimensionList,
    quotaList: state.pointParam.data.quotaList,
    sourceType: state.pointParam.data.sourceType
  }

  const clickParams = {
    option: 'pointClick',
    name: state.pointParam.data.name,
    viewId: view.value.id,
    dimensionList: state.pointParam.data.dimensionList,
    quotaList: state.pointParam.data.quotaList
  }

  switch (trackAction) {
    case 'pointClick':
      emit('onPointClick', clickParams)
      break
    case 'linkageAndDrill':
      dvMainStore.addViewTrackFilter(linkageParam)
      emit('onChartClick', param)
      break
    case 'drill':
      emit('onChartClick', param)
      break
    case 'linkage':
      dvMainStore.addViewTrackFilter(linkageParam)
      break
    case 'jump':
      if (mobileInPc.value && !inMobile.value) return
      emit('onJumpClick', jumpParam)
      break
    case 'enlarge':
      if (view.value.type === 'table-info') {
        param.data.dimensionList?.forEach(d => {
          if (d.id === state.curActionId) {
            state.imgSrc = d.value
            state.imgEnlarge = true
          }
        })
      }
      break
    default:
      break
  }
}

// 根据表格字段和当前点击位置计算实际菜单
const trackMenu = computed(() => {
  if (['table-info', 'table-normal'].includes(view.value.type) && state.curActionId) {
    return trackMenuCalc(state.curActionId)
  } else {
    return trackMenuCmp.value
  }
})

// 根据图表配置汇总可用的通用点击菜单
const trackMenuCmp = computed(() => {
  let trackMenuInfo = []
  if (showPosition.value === 'viewDialog') {
    return trackMenuInfo
  }
  let linkageCount = 0
  let jumpCount = 0
  chartData.value?.fields?.forEach(item => {
    const sourceInfo = view.value.id + '#' + item.id
    if (nowPanelTrackInfo.value[sourceInfo]) {
      linkageCount++
    }
    if (nowPanelJumpInfo.value[sourceInfo]) {
      jumpCount++
    }
  })
  jumpCount &&
    view.value?.jumpActive &&
    (!mobileInPc.value || inMobile.value) &&
    trackMenuInfo.push('jump')
  linkageCount && view.value?.linkageActive && trackMenuInfo.push('linkage')
  view.value.drillFields.length && trackMenuInfo.push('drill')
  // 如果同时配置jump linkage drill 切配置联动时同时下钻 在实际只显示两个 '跳转' '联动和下钻'
  if (trackMenuInfo.length === 3 && props.element.actionSelection.linkageActive === 'auto') {
    trackMenuInfo = ['jump', 'linkageAndDrill']
  } else if (
    trackMenuInfo.length === 2 &&
    props.element.actionSelection.linkageActive === 'auto' &&
    !trackMenuInfo.includes('jump')
  ) {
    trackMenuInfo = ['linkageAndDrill']
  }
  return trackMenuInfo
})

// 按指定字段重新计算表格单元格菜单
const trackMenuCalc = itemId => {
  let trackMenuInfo = []
  if (showPosition.value === 'viewDialog') {
    return trackMenuInfo
  }
  let linkageCount = 0
  let jumpCount = 0
  let drillCount = 0
  const sourceInfo = view.value.id + '#' + itemId
  if (nowPanelTrackInfo.value[sourceInfo]) {
    linkageCount++
  }
  if (nowPanelJumpInfo.value[sourceInfo]) {
    jumpCount++
  }
  jumpCount &&
    view.value?.jumpActive &&
    (!mobileInPc.value || inMobile.value) &&
    trackMenuInfo.push('jump')
  linkageCount && view.value?.linkageActive && trackMenuInfo.push('linkage')
  // 判断是否有下钻 同时判断下钻到第几层
  if (view.value.drillFields.length && view.value.drillFields[drillLength.value]?.id === itemId) {
    drillCount++
  }
  drillCount && trackMenuInfo.push('drill')
  // 如果同时配置jump linkage drill 切配置联动时同时下钻 在实际只显示两个 '跳转' '联动和下钻'
  if (trackMenuInfo.length === 3 && props.element.actionSelection.linkageActive === 'auto') {
    trackMenuInfo = ['jump', 'linkageAndDrill']
  } else if (
    trackMenuInfo.length === 2 &&
    props.element.actionSelection.linkageActive === 'auto' &&
    !trackMenuInfo.includes('jump')
  ) {
    trackMenuInfo = ['linkageAndDrill']
  }
  // 明细表 URL 字段图片放大
  if (view.value.type === 'table-info') {
    view.value.xAxis?.forEach(axis => {
      if (axis.id === itemId && axis.fieldType === 7) {
        trackMenuInfo.push('enlarge')
      }
    })
  }
  return trackMenuInfo
}

// 表格列宽调整后写回基础样式配置
const resizeAction = resizeColumn => {
  // 从头开始滚动
  if ((myChart?.facet as any)?.timer) {
    ;(myChart?.facet as any).timer.stop()
    nextTick(initScroll)
  }
  if (showPosition.value !== 'canvas') {
    return
  }
  const fieldId: string = resizeColumn.info.meta.field
  const { basicStyle } = view.value.customAttr
  const containerWidth = document.getElementById(containerId).offsetWidth
  const column = basicStyle.tableFieldWidth?.find(i => i.fieldId === fieldId)
  let tableWidth: ChartBasicStyle['tableFieldWidth']
  const width = parseFloat(((resizeColumn.info.resizedWidth / containerWidth) * 100).toFixed(2))
  if (column) {
    column.width = width
    tableWidth = [...basicStyle.tableFieldWidth]
  } else {
    const tmp = { fieldId, width }
    tableWidth = basicStyle.tableFieldWidth?.length ? [...basicStyle.tableFieldWidth, tmp] : [tmp]
  }
  emitter.emit('set-table-column-width', tableWidth)
}
defineExpose({
  calcData,
  renderChart,
  renderChartFromDialog,
  trackMenu
})

let timer
// 容器尺寸变化后延迟重绘表格或透视表
const resize = (width, height) => {
  if (timer) {
    clearTimeout(timer)
  }
  timer = setTimeout(() => {
    if (!myChart?.facet) {
      debounceRender()
    } else {
      ;(myChart?.facet as any)?.timer?.stop()
      myChart?.changeSheetSize(width, height)
      myChart?.render()
    }
    initScroll()
  }, 500)
}
const preSize = [0, 0]
const TOLERANCE = 1
let resizeObserver: ResizeObserver
onMounted(() => {
  resizeObserver = new ResizeObserver(([entry] = []) => {
    const [size] = entry.borderBoxSize || []
    // 拖动的时候宽高重新计算，误差范围内不重绘，误差先设置为1
    if (!(preSize[0] || preSize[1])) {
      preSize[0] = size.inlineSize
      preSize[1] = size.blockSize
    }
    const widthOffset = Math.abs(size.inlineSize - preSize[0])
    const heightOffset = Math.abs(size.blockSize - preSize[1])
    if (widthOffset < TOLERANCE && heightOffset < TOLERANCE) {
      return
    }
    preSize[0] = size.inlineSize
    preSize[1] = size.blockSize
    resize(size.inlineSize, Math.round(size.blockSize))
  })

  resizeObserver.observe(document.getElementById(containerId))
})
onBeforeUnmount(() => {
  try {
    ;(myChart?.facet as any)?.timer?.stop()
    myChart?.destroy()
    myChart = null
    resizeObserver?.disconnect()
  } catch (e) {
    console.warn(e)
  }
})

// 按画布缩放和分页字号计算分页器样式
const autoStyle = computed(() => {
  const adaptorScale =
    (scale.value * (canvasStyleData.value.component.seniorStyleSetting?.pagerSize || 14)) / 14
  if (isISOMobile()) {
    return {
      height: 20 * adaptorScale + 8 + 'px',
      width: 100 / adaptorScale + '%!important',
      left: 50 * (1 - 1 / adaptorScale) + '%', // 放大余量 除以 2
      transform: 'scale(' + adaptorScale + ') translateZ(0)'
    } as CSSProperties
  } else {
    return { zoom: adaptorScale }
  }
})

// 将分页器颜色透传为样式变量
const tabStyle = computed(() => [
  { '--crest-pager-color': canvasStyleData.value.component.seniorStyleSetting?.pagerColor }
])

// 浅色分页器文字自动切换为深色样式
const tablePageClass = computed(() => {
  return (
    ['#ffffff', '#ffffffff', '#a6a6a6ff'].includes(
      canvasStyleData.value.component.seniorStyleSetting?.pagerColor.toLowerCase()
    ) && 'table-page-info_dark'
  )
})
</script>

<template>
  <div class="canvas-area">
    <view-track-bar
      ref="viewTrack"
      :track-menu="trackMenu"
      :font-family="fontFamily"
      class="track-bar"
      :style="state.trackBarStyle"
      @trackClick="trackClick"
      :is-data-v-mobile="dataVMobile"
      @mousemove="mouseMove"
    />
    <div v-if="!isError" class="canvas-content">
      <div
        :id="containerId"
        style="position: relative; height: 100%"
        @mousemove="mouseMove"
        @mouseleave="mouseLeave"
      ></div>
    </div>
    <el-row :style="autoStyle" v-if="showPage && !isError">
      <div
        class="table-page-info"
        :class="tablePageClass"
        :style="tabStyle"
        @keydown.stop
        @keyup.stop
      >
        <div>{{ t('chart.total') }} {{ state.pageInfo.total }} {{ t('chart.items') }}</div>
        <el-pagination
          v-if="state.pageStyle !== 'general'"
          class="table-page-content"
          layout="prev, pager, next"
          v-model:page-size="state.pageInfo.pageSize"
          v-model:current-page="state.pageInfo.currentPage"
          :pager-count="5"
          :total="state.pageInfo.total"
          @update:current-page="handleCurrentChange"
        />
        <el-pagination
          v-else
          class="table-page-content"
          layout="prev, pager, next, sizes, jumper"
          v-model:page-size="state.pageInfo.pageSize"
          v-model:current-page="state.pageInfo.currentPage"
          :pager-count="5"
          :total="state.pageInfo.total"
          @update:current-page="handleCurrentChange"
          @update:page-size="handlePageSizeChange"
        />
      </div>
    </el-row>
    <chart-error v-if="isError" :err-msg="errMsg" />
  </div>
  <el-dialog v-model="state.imgEnlarge" append-to-body class="image-dialog">
    <div class="enlarge-image">
      <img :src="state.imgSrc" style="width: 100%; height: 100%; object-fit: contain" />
    </div>
  </el-dialog>
</template>

<style lang="less" scoped>
.canvas-area {
  z-index: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  width: 100%;
  height: 100%;
  .canvas-content {
    flex: 1;
    width: 100%;
    overflow: hidden;
  }
}

.table-page-info_dark {
  --ed-fill-color-blank: #00000000;
}

.table-page-info {
  --ed-text-color-regular: var(--crest-pager-color);
  position: relative;
  padding-left: 4px;
  margin: 4px;
  height: 20px;
  display: flex;
  width: 100%;
  font-size: 14px;
  color: var(--crest-pager-color);
  :deep(.table-page-content) {
    button,
    button[disabled] {
      color: var(--crest-pager-color);
      background: transparent !important;
    }
    ul li {
      &:not(.is-active) {
        color: var(--crest-pager-color);
      }
      background: transparent !important;
    }
  }
}
</style>
<style lang="less">
.image-dialog {
  height: 100%;
  .ed-dialog__body {
    height: calc(100% - 24px);
    width: 100%;
  }
}
.enlarge-image {
  display: flex;
  width: 100%;
  height: 100%;
  overflow: hidden;
  flex-direction: row;
  justify-content: center;
}
.antv-s2-tooltip-container {
  max-width: 400px;
  min-width: 80px;
}
</style>
