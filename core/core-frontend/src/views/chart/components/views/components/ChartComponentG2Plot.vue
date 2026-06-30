<script lang="ts" setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  shallowRef,
  toRefs,
  watch
} from 'vue'
import { getData } from '@/api/chart'
import { ChartLibraryType } from '@/views/chart/components/js/panel/types'
import { G2PlotChartView } from '@/views/chart/components/js/panel/types/impl/g2plot'
import chartViewManager from '@/views/chart/components/js/panel'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import ViewTrackBar from '@/components/visualization/ViewTrackBar.vue'
import { storeToRefs } from 'pinia'
import { parseJson } from '@/views/chart/components/js/util'
import { defaultsDeep, cloneDeep, concat } from 'lodash-es'
import ChartError from '@/views/chart/components/views/components/ChartError.vue'
import { BASE_VIEW_CONFIG } from '../../editor/util/chart'
import { customAttrTrans, customStyleTrans, recursionTransObj } from '@/utils/canvasStyle'
import { deepCopy, isMobile } from '@/utils/utils'
import { isDashboard, trackBarStyleCheck } from '@/utils/canvasUtils'
import { useEmitt } from '@/hooks/web/useEmitt'
import { useI18n } from '@/hooks/web/useI18n'
import { configEmptyDataStyle } from '@/views/chart/components/js/panel/common/common_antv'
// 图表组件内置文案的翻译入口
const { t } = useI18n()
// 大屏主状态提供联动、跳转和移动端上下文
const dvMainStore = dvMainStoreWithOut()
// 解构当前面板联动、跳转和嵌入式状态
const { nowPanelTrackInfo, nowPanelJumpInfo, mobileInPc, embeddedCallBack, inMobile } =
  storeToRefs(dvMainStore)
// 全局事件总线用于图表与画布工具栏通信
const { emitter } = useEmitt()
// G2Plot 图表组件入参，包含视图配置、展示位置和终端信息
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
    type: Object,
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
  },
  active: {
    type: Boolean,
    required: false,
    default: true
  }
})

// 图表点击、联动、跳转和加载状态事件透传给父组件
const emit = defineEmits([
  'onPointClick',
  'onChartClick',
  'onDrillFilters',
  'onJumpClick',
  'resetLoading'
])

// 该类图表的系列字段取自 field
const g2TypeSeries1 = ['bidirectional-bar']
// 该类图表的系列字段取自 category
const g2TypeSeries0 = ['bar-range']
// 树形图表需要按路径和名称判断选中
const g2TypeTree = ['circle-packing']
// 堆叠图表需要同时处理维度、扩展维度和堆叠字段
const g2TypeStack = [
  'bar-stack',
  'bar-group-stack',
  'percentage-bar-stack',
  'bar-stack-horizontal',
  'percentage-bar-stack-horizontal'
]
// 分组图表需要按名称和分类组合判断选中
const g2TypeGroup = ['bar-group']

// 常用入参保持响应式引用
const { view, showPosition, scale, terminal, suffixId } = toRefs(props)

// 图表渲染错误标记
const isError = ref(false)
// 图表渲染错误文案
const errMsg = ref('')
// 是否已经应用过联动高亮
const linkageActiveHistory = ref(false)

// 大屏移动端预览需要走专门的交互分支
const dataVMobile = !isDashboard() && isMobile()

// 图表运行时状态，包含轨迹条位置、联动参数和渲染数据
const state = reactive({
  trackBarStyle: {
    position: 'absolute',
    left: '50px',
    top: '50px'
  },
  trackBarStyleMobile: {
    position: 'absolute',
    left: '50px',
    top: '50px'
  },
  linkageActiveParam: null,
  pointParam: null,
  data: { fields: [] } // 图表数据
})
// 图表数据体积较大，使用浅响应式避免深层代理开销
let chartData = shallowRef<Partial<Chart['data']>>({
  fields: []
})

// 当前图表容器编号，组合展示位置、图表编号和后缀避免冲突
const containerId = 'container-' + showPosition.value + '-' + view.value.id + '-' + suffixId.value
// 联动轨迹条组件引用
const viewTrack = ref(null)

// 清除图表联动高亮状态
const clearLinkage = () => {
  linkageActiveHistory.value = false
  try {
    myChart?.setState('active', () => true, false)
    myChart?.setState('inactive', () => true, false)
    myChart?.setState('selected', () => true, false)
  } catch (e) {
    console.warn('clearLinkage error')
  }
}
// 需要重新应用联动前，先按图表能力决定是否重绘
const reDrawView = () => {
  linkageActiveHistory.value = false
  const slider = myChart?.chart?.getController('slider')
  if (!slider) {
    myChart?.render()
  }
}
// 等待 DOM 更新后重新应用联动状态
const linkageActivePre = () => {
  if (linkageActiveHistory.value) {
    reDrawView()
  }
  nextTick(() => {
    linkageActive()
  })
}
// 根据当前联动参数设置图形选中和置灰状态
const linkageActive = () => {
  linkageActiveHistory.value = true
  myChart?.setState('active', () => true, false)
  myChart?.setState('inactive', () => true, false)
  myChart?.setState('selected', () => true, false)
  myChart?.setState('active', param => {
    if (Array.isArray(param)) {
      return false
    } else {
      return checkSelected(param)
    }
  })
  myChart?.setState('inactive', param => {
    if (Array.isArray(param)) {
      return false
    } else {
      return !checkSelected(param)
    }
  })
  myChart?.setState('selected', param => {
    if (Array.isArray(param)) {
      return false
    } else {
      return checkSelected(param)
    }
  })
}
// 判断单个图形元素是否命中当前联动条件
const checkSelected = param => {
  // 获取当前视图的所有联动字段ID
  const mappingFieldIds = Array.from(
    new Set(
      (view.value.type.includes('chart-mix')
        ? concat(chartData.value?.left?.fields, chartData.value?.right?.fields)
        : chartData.value?.fields
      )
        .map(item => item?.id)
        .filter(id =>
          Object.keys(nowPanelTrackInfo.value).some(
            key => key.startsWith(view.value.id) && key.split('#')[1] === id
          )
        )
    )
  )
  // 维度字段匹配
  const [xAxis, xAxisExt, extStack] = ['xAxis', 'xAxisExt', 'extStack'].map(key =>
    view.value[key].find(item => mappingFieldIds.includes(item.id))
  )
  // 选中字段数据
  const { group, name, category } = state.linkageActiveParam
  // 选中字段数据匹配
  if (g2TypeSeries1.includes(view.value.type)) {
    return name === param.field
  } else if (g2TypeSeries0.includes(view.value.type)) {
    return category === param.category
  } else if (g2TypeTree.includes(view.value.type)) {
    if (param.path?.startsWith(name) || name === t('commons.all')) {
      return true
    }
    return name === param.name
  } else if (g2TypeGroup.includes(view.value.type)) {
    const isNameMatch = name === param.name || (name === 'NO_DATA' && !param.name)
    const isCategoryMatch = category === param.category
    if (xAxis && xAxisExt) {
      return isNameMatch && isCategoryMatch
    }
    if (xAxis && !xAxisExt) {
      return isNameMatch
    }
    if (!xAxis && xAxisExt) {
      return isCategoryMatch
    }
    return false
  } else if (g2TypeStack.includes(view.value.type)) {
    const isGroupMatch = group === param.group || (group === 'NO_DATA' && !param.group)
    const isNameMatch = name === param.name || (name === 'NO_DATA' && !param.name)
    const isCategoryMatch = category === param.category
    // 全部匹配
    if (xAxis && xAxisExt && extStack) {
      return isNameMatch && isGroupMatch && isCategoryMatch
    }
    // 只匹配到维度
    if (xAxis && !xAxisExt && !extStack) {
      return isNameMatch
    } else if (!xAxis && xAxisExt && !extStack) {
      return isGroupMatch
    } else if (!xAxis && !xAxisExt && extStack) {
      return isCategoryMatch
    } else if (xAxis && xAxisExt && !extStack) {
      return isNameMatch && isGroupMatch
    } else if (xAxis && !xAxisExt && extStack) {
      return isNameMatch && isCategoryMatch
    } else if (!xAxis && xAxisExt && extStack) {
      return isGroupMatch && isCategoryMatch
    } else {
      return false
    }
  } else {
    return (
      (name === param.name || (name === 'NO_DATA' && !param.name)) && category === param.category
    )
  }
}

/**
 * 查询图表数据并触发后续渲染流程
 */
const calcData = async (view, callback) => {
  if (view.tableId || view['dataFrom'] === 'template') {
    isError.value = false
    const v = JSON.parse(JSON.stringify(view))
    getData(v)
      .then(async res => {
        const dataRes = res as any
        if (res.code && res.code !== 0) {
          isError.value = true
          errMsg.value = res.msg
          callback?.()
        } else {
          chartData.value = res?.data as Partial<Chart['data']>
          emit('onDrillFilters', dataRes?.drillFilters)
          if (!dataRes?.drillFilters?.length) {
            dynamicAreaId.value = ''
            scope = null
            gadmName = null
          } else {
            const chartExtRequest = view.chartExtRequest || view.value?.chartExtRequest
            const extra = chartExtRequest?.drill?.[dataRes?.drillFilters?.length - 1].extra
            dynamicAreaId.value = extra?.adcode + ''
            scope = extra?.scope
            gadmName = extra?.gadmName
            // 地图
            const map = parseJson(view.customAttr)?.map
            if (map) {
              let areaId = map.id
              country.value = areaId.slice(0, 3)
              // 世界下钻到国家，切换路径
              if (country.value === '000' || dynamicAreaId.value?.startsWith('000')) {
                country.value = chartExtRequest?.drill?.[0]?.extra?.adcode
              }
            }
            if (!dynamicAreaId.value?.startsWith(country.value)) {
              if (country.value === 'cus') {
                dynamicAreaId.value = '156' + dynamicAreaId.value
              } else {
                dynamicAreaId.value = country.value + dynamicAreaId.value
              }
            }
          }
          dvMainStore.setViewDataDetails(view.id, res)
          if (!dataRes.drill && !dataRes.chartExtRequest?.linkageFilters?.length) {
            dvMainStore.setViewOriginData(view.id, chartData.value)
            emitter.emit('chart-data-change')
          }
          await renderChart(res, callback)
        }
      })
      .catch(() => {
        callback?.()
      })
  } else {
    if (['bubble-map', 'map', 'flow-map', 'heat-map'].includes(view.type)) {
      await renderChart(view, callback)
    }
    callback?.()
  }
}
/**
 * 当前正在渲染的图表视图
 */
let curView
/**
 * 合并图表配置并按渲染库分发渲染流程
 */
const renderChart = async (view, callback?) => {
  if (!view) {
    return
  }
  curView = view
  // view 为引用对象 需要存库 view.data 直接赋值会导致保存不必要的数据
  // 与默认图表对象合并，方便增加配置项
  const chart = deepCopy({
    ...defaultsDeep(view, cloneDeep(BASE_VIEW_CONFIG)),
    data: chartData.value,
    ...(props.fontFamily && props.fontFamily !== 'inherit' ? { fontFamily: props.fontFamily } : {})
  })
  const chartView = chartViewManager.getChartView(view.render, view.type)
  recursionTransObj(customAttrTrans, chart.customAttr, scale.value, terminal.value)
  recursionTransObj(customStyleTrans, chart.customStyle, scale.value, terminal.value)
  switch (chartView.library) {
    case ChartLibraryType.L7_PLOT:
      emit('resetLoading')
      break
    case ChartLibraryType.L7:
      emit('resetLoading')
      break
    case ChartLibraryType.G2_PLOT:
      await renderG2Plot(chart, chartView as G2PlotChartView<any, any>)
      callback?.()
      break
    default:
      break
  }
}
/**
 * 当前 G2Plot 图表实例
 */
let myChart = null
/**
 * G2Plot 延迟渲染定时器
 */
let g2Timer: ReturnType<typeof setTimeout>
/**
 * 渲染 G2Plot 图表并恢复联动高亮状态
 */
const renderG2Plot = async (chart, chartView: G2PlotChartView<any, any>) => {
  g2Timer && clearTimeout(g2Timer)
  g2Timer = setTimeout(async () => {
    try {
      // 在这里清理掉之前图表的空dom
      configEmptyDataStyle([1], containerId)
      myChart?.destroy()
      chart.container = containerId
      myChart = await chartView.drawChart({
        chartObj: myChart,
        container: containerId,
        chart: chart,
        scale: 1,
        action,
        quadrantDefaultBaseline
      })
      myChart?.render()
      if (linkageActiveHistory.value) {
        linkageActive()
      }
    } catch (e) {
      console.error('renderG2Plot error', e)
    }
  }, 300)
}

/**
 * 地图下钻后的动态区域编码
 */
const dynamicAreaId = ref('')
/**
 * 当前地图国家或区域编码前缀
 */
const country = ref('')
/**
 * 当前地图 GADM 名称
 */
let gadmName
/**
 * 图表容器引用
 */
const chartContainer = ref<HTMLElement>(null)
/**
 * 当前地图边界范围
 */
let scope
/**
 * 地图渲染定时器
 */
let mapTimer: ReturnType<typeof setTimeout>
/**
 * 渲染 L7Plot 地图类图表
 */
const renderL7Plot = async (chart: ChartObj, chartView: any, callback) => {
  const map = parseJson(chart.customAttr).map
  let areaId = map.id
  country.value = areaId.slice(0, 3)
  if (dynamicAreaId.value) {
    // 世界下钻到国家，切换路径
    if (country.value === '000' && dynamicAreaId.value.startsWith('000')) {
      country.value = dynamicAreaId.value.slice(3)
      areaId = country.value
    } else {
      areaId = dynamicAreaId.value
    }
  }
  mapTimer && clearTimeout(mapTimer)
  mapTimer = setTimeout(async () => {
    if (myChart?.tooltip && typeof myChart.tooltip.destroy !== 'function') {
      myChart.tooltip = null
    }
    myChart?.destroy()
    if (chartContainer.value) {
      chartContainer.value.textContent = ''
    }
    myChart = await chartView.drawChart({
      chartObj: myChart,
      container: containerId,
      chart,
      areaId,
      action,
      scope,
      gadmName
    })
    callback?.()
    emit('resetLoading')
  }, 500)
}

let mapL7Timer: ReturnType<typeof setTimeout>
/**
 * 延迟渲染 L7 图表，避免地图容器频繁重建
 */
const renderL7 = async (chart: ChartObj, chartView: any, callback) => {
  mapL7Timer && clearTimeout(mapL7Timer)
  mapL7Timer = setTimeout(async () => {
    chart.container = containerId
    myChart = await chartView.drawChart({
      chartObj: myChart,
      container: containerId,
      chart: chart,
      action
    })
    myChart?.render()
    callback?.()
    emit('resetLoading')
  }, 500)
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
 * 处理图表库上报的默认交互事件
 */
const actionDefault = param => {
  if (param.from === 'map') {
    emitter.emit('map-default-range', param)
  }
  if (param.from === 'word-cloud') {
    emitter.emit('word-cloud-default-data-range', param)
  }
  if (param.from === 'gauge' || param.from === 'liquid') {
    emitter.emit('gauge-liquid-y-value', param)
  }
}

/**
 * 处理图表点位点击后的下钻、联动和跳转入口
 */
const action = param => {
  if (param.from) {
    actionDefault(param)
    return
  }
  if (view.value.type === 'map') {
    if (!(param?.data?.data?.quotaList && param?.data?.data?.quotaList.length > 0)) {
      return
    }
  }
  state.pointParam = param.data
  // 点击
  pointClickTrans()
  // 下钻 联动 跳转
  state.linkageActiveParam = {
    category: state.pointParam.data.category ? state.pointParam.data.category : 'NO_DATA',
    name: state.pointParam.data.name ? state.pointParam.data.name : 'NO_DATA',
    group: state.pointParam.data.group ? state.pointParam.data.group : 'NO_DATA'
  }
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
    const trackBarX = barStyleTemp.left
    let trackBarY = 50
    state.trackBarStyle.left = barStyleTemp.left + 'px'
    if (curView.type === 'symbolic-map') {
      trackBarY = param.y + 10
      state.trackBarStyle.top = param.y + 10 + 'px'
    } else {
      trackBarY = barStyleTemp.top
      state.trackBarStyle.top = barStyleTemp.top + 'px'
    }
    if (dataVMobile) {
      state.trackBarStyle.left = trackBarX + 40 + 'px'
      state.trackBarStyle.top = trackBarY + 70 + 'px'
    } else {
      state.trackBarStyle.left = trackBarX + 'px'
      state.trackBarStyle.top = trackBarY + 'px'
    }

    viewTrack.value.trackButtonClick(view.value.id)
  }
}

/**
 * 根据当前点击点位执行指定追踪动作
 */
const trackClick = trackAction => {
  const param = state.pointParam
  if (!param?.data?.dimensionList) {
    return
  }
  let checkName = undefined
  if (param.data.dimensionList.length > 1) {
    // 分组堆叠处理 去能比较出来值的那个维度
    if (view.value.type === 'bar-group-stack') {
      const length = param.data.dimensionList.length
      // 存在最后一个id
      if (param.data.dimensionList[length - 1].id === param.data.dimensionList[length - 2].id) {
        param.data.dimensionList.pop()
      }
      param.data.dimensionList.forEach(dimension => {
        if (dimension.value === param.data.category) {
          checkName = dimension.id
        }
      })
    }
    if (!checkName) {
      // 对多维度的处理 取第一个
      checkName = param.data.dimensionList[0].id
    }
  }
  if (!checkName) {
    checkName = param.data.name
  }
  // 跳转字段处理
  let jumpName = state.pointParam.data.name
  if (state.pointParam.data.dimensionList.length > 1) {
    const fieldIds = []
    // 优先下钻字段
    if (curView.drill) {
      const curFiled = curView.drillFields[curView.drillFilters.length]
      if (curFiled?.id) {
        fieldIds.push(curFiled.id)
      }
    }
    if (curView.type.includes('chart-mix')) {
      chartData.value?.left?.fields?.forEach(field => {
        if (!fieldIds.includes(field.id)) {
          fieldIds.push(field.id)
        }
      })
      chartData.value?.right?.fields?.forEach(field => {
        if (!fieldIds.includes(field.id)) {
          fieldIds.push(field.id)
        }
      })
    } else {
      chartData.value?.fields?.forEach(field => {
        if (!fieldIds.includes(field.id)) {
          fieldIds.push(field.id)
        }
      })
    }
    for (let i = 0; i < fieldIds.length; i++) {
      const id = fieldIds[i]
      const sourceInfo = view.value.id + '#' + id
      if (nowPanelJumpInfo.value[sourceInfo]) {
        jumpName = id
        break
      }
    }
  }
  let quotaList = state.pointParam.data.quotaList || []
  if (['bar-range', 'bullet-graph'].includes(curView.type)) {
    quotaList = state.pointParam.data.dimensionList
  } else if (curView.type === 'multi-scatter') {
    // 多维散点图 dimensionList 包含颜色维度+横轴+纵轴的值
    quotaList = []
  } else if (quotaList.length) {
    quotaList[0]['value'] = state.pointParam.data.value
  }
  const linkageParam = {
    option: 'linkage',
    name: checkName,
    viewId: view.value.id,
    dimensionList: state.pointParam.data.dimensionList,
    quotaList: quotaList
  }
  const jumpParam = {
    option: 'jump',
    name: jumpName,
    viewId: view.value.id,
    dimensionList: state.pointParam.data.dimensionList,
    quotaList: quotaList
  }

  const clickParams = {
    option: 'pointClick',
    name: checkName,
    viewId: view.value.id,
    dimensionList: state.pointParam.data.dimensionList,
    quotaList: quotaList
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
      linkageActivePre()
      dvMainStore.addViewTrackFilter(linkageParam)
      break
    case 'jump':
      if (mobileInPc.value && !inMobile.value) return
      emit('onJumpClick', jumpParam)
      break
    default:
      break
  }
}

/**
 * 计算当前点位可展示的追踪操作菜单
 */
const trackMenu = computed(() => {
  let trackMenuInfo = []
  // 复用、放大状态的仪表板不进行联动、跳转和下钻的动作
  if (!['multiplexing', 'viewDialog'].includes(showPosition.value)) {
    let drillFields =
      curView?.drill && curView?.drillFilters?.length
        ? curView.drillFilters.map(item => item.fieldId)
        : []
    let linkageCount = 0
    let jumpCount = 0
    if (curView?.type?.includes('chart-mix')) {
      Array.of('left', 'right').forEach(side => {
        chartData.value?.[side]?.fields
          ?.filter(item => !drillFields.includes(item.id))
          .forEach(item => {
            const sourceInfo = view.value.id + '#' + item.id
            if (nowPanelTrackInfo.value[sourceInfo]) {
              linkageCount++
            }
            if (nowPanelJumpInfo.value[sourceInfo]) {
              jumpCount++
            }
          })
      })
    } else {
      chartData.value?.fields
        ?.filter(item => !drillFields.includes(item.id))
        .forEach(item => {
          const sourceInfo = view.value.id + '#' + item.id
          if (nowPanelTrackInfo.value[sourceInfo]) {
            linkageCount++
          }
          if (nowPanelJumpInfo.value[sourceInfo]) {
            jumpCount++
          }
        })
    }
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
  }
  return trackMenuInfo
})
/**
 * 同步象限图默认基准线
 */
const quadrantDefaultBaseline = defaultQuadrant => {
  emitter.emit('quadrant-default-baseline', defaultQuadrant)
}

/**
 * 将地图 canvas 临时替换为截图图片
 */
const canvas2Picture = (pictureData, online) => {
  const mapDom = document.getElementById(containerId)
  const childNodeList = mapDom.querySelectorAll('.l7-scene')
  if (childNodeList?.length) {
    childNodeList.forEach(child => {
      child['style'].display = 'none'
    })
  }
  if (online) {
    const canvasContainerList = mapDom.querySelectorAll('.amap-maps')
    canvasContainerList?.forEach(canvasContainer => {
      canvasContainer['style'].display = 'none'
    })
  }
  const imgDom = document.createElement('img')
  imgDom.style.width = '100%'
  imgDom.style.height = '100%'
  imgDom.style.position = 'absolute'
  imgDom.style.objectFit = 'cover'
  imgDom.style['z-index'] = '2'
  imgDom.classList.add('prepare-picture-img')
  imgDom.src = pictureData
  mapDom?.appendChild(imgDom)
}
/**
 * 预处理地图截图所需的图表实例
 */
const preparePicture = async id => {
  if (id !== curView?.id) {
    return
  }
  const chartView = chartViewManager.getChartView(curView.render, curView.type)
  void chartView
}
/**
 * 恢复地图截图前隐藏的地图层
 */
const unPreparePicture = id => {
  if (id !== curView?.id) {
    return
  }
  const chartView = chartViewManager.getChartView(curView.render, curView.type)
  if (chartView.library === ChartLibraryType.L7_PLOT || chartView.library === ChartLibraryType.L7) {
    const mapDom = document.getElementById(containerId)
    const childNodeList = mapDom.querySelectorAll('.l7-scene')
    if (childNodeList?.length) {
      childNodeList.forEach(child => {
        child['style'].display = 'block'
      })
    }
    const imgDomList = mapDom.querySelectorAll('.prepare-picture-img')
    imgDomList?.forEach(child => {
      child.remove()
    })
    const canvasContainerList = mapDom.querySelectorAll('.amap-maps')
    canvasContainerList?.forEach(canvasContainer => {
      canvasContainer['style'].display = 'block'
    })
  }
}
defineExpose({
  calcData,
  renderChart,
  trackMenu,
  clearLinkage
})
let intersectionObserver
let resizeObserver
const TOLERANCE = 0.01
const RESIZE_MONITOR_CHARTS = ['map', 'bubble-map', 'flow-map', 'heat-map']
onMounted(() => {
  const containerDom = document.getElementById(containerId)
  const { offsetWidth, offsetHeight } = containerDom
  const preSize = [offsetWidth, offsetHeight]
  resizeObserver = new ResizeObserver(([entry] = []) => {
    if (!RESIZE_MONITOR_CHARTS.includes(view.value.type)) {
      return
    }
    const [size] = entry.borderBoxSize || []
    const widthOffsetPercent = (size.inlineSize - preSize[0]) / preSize[0]
    const heightOffsetPercent = (size.blockSize - preSize[1]) / preSize[1]
    if (Math.abs(widthOffsetPercent) < TOLERANCE && Math.abs(heightOffsetPercent) < TOLERANCE) {
      return
    }
    if (myChart && preSize[1] > 1) {
      renderChart(curView)
    }
    preSize[0] = size.inlineSize
    preSize[1] = size.blockSize
  })
  resizeObserver.observe(containerDom)
  intersectionObserver = new IntersectionObserver(([entry]) => {
    if (RESIZE_MONITOR_CHARTS.includes(view.value.type)) {
      return
    }
    if (entry.intersectionRatio <= 0) {
      // G2Plot 图表使用 emit，L7/L7Plot 图表没有 emit 方法
      if (myChart && typeof myChart.emit === 'function') {
        myChart.emit('tooltip:hidden')
      }
    }
  })
  intersectionObserver.observe(containerDom)
  useEmitt({ name: 'l7-prepare-picture', callback: preparePicture })
  useEmitt({ name: 'l7-unprepare-picture', callback: unPreparePicture })
})
const MAP_CHARTS = ['map', 'bubble-map', 'flow-map', 'heat-map', 'symbolic-map']
/**
 * 地图未选中时阻止滚轮事件穿透到画布
 */
const onWheel = (e: WheelEvent) => {
  if (!MAP_CHARTS.includes(view.value.type)) {
    return
  }
  if (!props.active) {
    e.stopPropagation()
  }
}
onBeforeUnmount(() => {
  try {
    myChart?.destroy()
    resizeObserver?.disconnect()
    intersectionObserver?.disconnect()
  } catch (e) {
    console.warn(e)
  }
})

/**
 * 监听图表选中状态,处理地图在移动端的交互
 * active = true 时：图表选中 → 事件穿透画布容器，不拦截，能够直接与画布交互
 * active = false 时：图表未选中 → 画布容器正常响应事件，能够滑动页面
 */
watch(
  () => props.active,
  newVal => {
    if (!MAP_CHARTS.includes(view.value.type) || !isMobile()) return
    const containerDiv = document.getElementById(containerId)
    if (!containerDiv) return
    // 腾讯 / 天地图：容器配置 pointer-events
    const isQQOrTianMap = !!containerDiv.style.pointerEvents
    const containerEvents = newVal ? 'auto' : 'none'
    const sceneEvents = isQQOrTianMap ? containerEvents : newVal ? 'none' : 'auto'
    if (isQQOrTianMap) {
      containerDiv.style.pointerEvents = containerEvents
    }
    containerDiv
      .querySelectorAll<HTMLElement>('.l7-scene')
      .forEach(el => (el.style.pointerEvents = sceneEvents))
    // 容器添加活跃标识，方便后续样式调整
    containerDiv.setAttribute('crest-chart-active', String(newVal))
  }
)
</script>

<template>
  <div class="canvas-area">
    <view-track-bar
      ref="viewTrack"
      :track-menu="trackMenu"
      :font-family="fontFamily"
      :is-data-v-mobile="dataVMobile"
      class="track-bar"
      :style="state.trackBarStyle"
      @trackClick="trackClick"
    />
    <div
      @wheel.capture="onWheel"
      v-if="!isError"
      ref="chartContainer"
      class="canvas-content"
      :id="containerId"
    ></div>
    <chart-error v-else :err-msg="errMsg" />
  </div>
</template>

<style lang="less" scoped>
.canvas-area {
  position: relative;
  width: 100%;
  height: 100%;
  z-index: 0;
  .canvas-content {
    width: 100% !important;
    height: 100% !important;
    :deep(.g2-tooltip) {
      position: fixed !important;
    }
  }
}
</style>
