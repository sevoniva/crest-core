<script setup lang="ts">
import { getData } from '@/api/chart'
import { ref, reactive, shallowRef, computed, CSSProperties, toRefs, PropType } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { customAttrTrans, customStyleTrans, recursionTransObj } from '@/utils/canvasStyle'
import { deepCopy, isMobile } from '@/utils/utils'
import { cloneDeep, defaultsDeep, defaultTo } from 'lodash-es'
import {
  BASE_VIEW_CONFIG,
  CHART_FONT_FAMILY_MAP,
  DEFAULT_INDICATOR_NAME_STYLE,
  DEFAULT_INDICATOR_STYLE
} from '@/views/chart/components/editor/util/chart'
import { valueFormatter } from '@/views/chart/components/js/formatter'
import { storeToRefs } from 'pinia'
import { isDashboard, trackBarStyleCheck } from '@/utils/canvasUtils'
import ViewTrackBar from '@/components/visualization/ViewTrackBar.vue'

// 指标卡组件入参，覆盖数据上下文、渲染位置、终端类型和字体配置
const props = defineProps({
  // 组件事件等公共运行参数
  commonParams: {
    type: Object,
    required: false
  },
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
  // 图表渲染 ID 后缀，用于区分同一视图在不同容器中的实例
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

const { view, scale, terminal, showPosition, commonParams } = toRefs(props)

const dvMainStore = dvMainStoreWithOut()
const dataVMobile = !isDashboard() && isMobile()
const { embeddedCallBack, nowPanelTrackInfo, nowPanelJumpInfo, mobileInPc, inMobile } =
  storeToRefs(dvMainStore)
// 交互菜单组件实例，用于展示联动、跳转和下钻操作
const viewTrack = ref(null)
// 指标卡根节点实例，用于换算点击坐标
const indicatorRef = ref(null)
// 数据加载或渲染失败时展示的错误信息
const errMsg = ref('')
// 指标卡是否处于错误展示状态
const isError = ref(false)
// 指标卡运行态，保存点击参数、数据加载状态和交互菜单位置
const state = reactive({
  pointParam: null,
  data: null,
  loading: false,
  totalItems: 0,
  trackBarStyle: {
    position: 'absolute',
    left: '50%',
    top: '50%'
  }
})

// 指标卡原始数据，使用浅层引用避免大数据对象触发深层响应式开销
const chartData = shallowRef<Partial<Chart['data']>>({
  fields: []
})

// 当前指标卡第一组序列数据
const resultObject = computed(() => {
  const list = chartData.value?.series
  if (list && list.length > 0) {
    return list[0]
  }
  return undefined
})

// 指标字段展示名称，优先使用图表配置中的别名
const resultName = computed(() => {
  if (view.value?.yAxis?.length) {
    const axis = view.value.yAxis[0]
    return axis.chartShowName ? axis.chartShowName : axis.name
  }
  return undefined
})

// 指标卡最终展示值，空值按高级配置决定是否补零
const result = computed(() => {
  const list = resultObject.value?.data
  let _result = undefined
  if (list && list.length > 0) {
    _result = list[0]
  }
  if (_result === null || _result === undefined) {
    if (view.value.senior && view.value.senior?.functionCfg?.emptyDataStrategy === 'setZero') {
      _result = 0
    } else {
      return '-'
    }
  }
  return _result
})

// 指标默认颜色，阈值命中后会参与计算展示颜色
const indicatorColor = ref(DEFAULT_INDICATOR_STYLE.color)
// 根据阈值配置计算指标文字色和背景色
const thresholdColor = computed(() => {
  let color: string = indicatorColor.value
  let backgroundColor: string = DEFAULT_INDICATOR_STYLE.backgroundColor
  if (result.value === '-') {
    return { color, backgroundColor }
  }
  const value = result.value
  if (
    view.value.senior &&
    view.value.senior.threshold?.enable &&
    view.value.senior.threshold?.labelThreshold?.length > 0
  ) {
    const senior = view.value.senior
    for (let i = 0; i < senior.threshold.labelThreshold.length; i++) {
      let flag = false
      const t = senior.threshold.labelThreshold[i]
      const tv = t.value
      if (t.term === 'eq') {
        if (value === tv) {
          color = t.color
          backgroundColor = t.backgroundColor
          flag = true
        }
      } else if (t.term === 'not_eq') {
        if (value !== tv) {
          color = t.color
          backgroundColor = t.backgroundColor
          flag = true
        }
      } else if (t.term === 'lt') {
        if (value < tv) {
          color = t.color
          backgroundColor = t.backgroundColor
          flag = true
        }
      } else if (t.term === 'gt') {
        if (value > tv) {
          color = t.color
          backgroundColor = t.backgroundColor
          flag = true
        }
      } else if (t.term === 'le') {
        if (value <= tv) {
          color = t.color
          backgroundColor = t.backgroundColor
          flag = true
        }
      } else if (t.term === 'ge') {
        if (value >= tv) {
          color = t.color
          backgroundColor = t.backgroundColor
          flag = true
        }
      } else if (t.term === 'between') {
        if (t.min <= value && value <= t.max) {
          color = t.color
          backgroundColor = t.backgroundColor
          flag = true
        }
      }
      if (flag) {
        break
      }
    }
  }
  return { color, backgroundColor }
})

// 指标值格式化结果，优先使用度量字段的格式化配置
const formattedResult = computed(() => {
  let _result = result.value

  if (_result === '-') {
    return _result
  }

  // 按度量字段配置格式化数值展示
  if (view.value.yAxis && view.value.yAxis.length > 0 && view.value.yAxis[0].formatterCfg) {
    return valueFormatter(_result, view.value.yAxis[0].formatterCfg)
  }
  return _result
})

// 指标卡向上抛出的点击、下钻、跳转和组件事件
const emit = defineEmits([
  'onPointClick',
  'onChartClick',
  'onDrillFilters',
  'onJumpClick',
  'onComponentEvent'
])
// 指标卡内容容器样式，承载水平和垂直对齐配置
const contentStyle = ref<CSSProperties>({
  display: 'flex',
  'flex-direction': 'column',
  'align-items': 'center',
  'justify-content': 'center',
  height: '100%',
  'background-color': thresholdColor.value.backgroundColor
})

// 指标主数值样式，渲染时会被视图自定义样式覆盖
const indicatorClass = ref<CSSProperties>({
  color: thresholdColor.value.color,
  'font-size': DEFAULT_INDICATOR_STYLE.fontSize + 'px',
  'font-family': defaultTo(
    props.fontFamily,
    CHART_FONT_FAMILY_MAP[DEFAULT_INDICATOR_STYLE.fontFamily]
  ),
  'font-weight': DEFAULT_INDICATOR_STYLE.isBolder ? 'bold' : 'normal',
  'font-style': DEFAULT_INDICATOR_STYLE.isItalic ? 'italic' : 'normal',
  'letter-spacing': DEFAULT_INDICATOR_STYLE.letterSpace + 'px',
  'text-shadow': DEFAULT_INDICATOR_STYLE.fontShadow ? '2px 2px 4px' : 'none',
  'font-synthesis': 'weight style'
})

// 指标后缀样式，保持和主数值相同的字体族兜底逻辑
const indicatorSuffixClass = ref<CSSProperties>({
  color: DEFAULT_INDICATOR_STYLE.suffixColor,
  'font-size': DEFAULT_INDICATOR_STYLE.suffixFontSize + 'px',
  'font-family': defaultTo(
    props.fontFamily,
    CHART_FONT_FAMILY_MAP[DEFAULT_INDICATOR_STYLE.fontFamily]
  ),
  'font-weight': DEFAULT_INDICATOR_STYLE.suffixIsBolder ? 'bold' : 'normal',
  'font-style': DEFAULT_INDICATOR_STYLE.suffixIsItalic ? 'italic' : 'normal',
  'letter-spacing': DEFAULT_INDICATOR_STYLE.suffixLetterSpace + 'px',
  'text-shadow': DEFAULT_INDICATOR_STYLE.suffixFontShadow ? '2px 2px 4px' : 'none',
  'font-synthesis': 'weight style'
})

// 是否展示指标后缀
const showSuffix = ref<boolean>(DEFAULT_INDICATOR_STYLE.suffixEnable)

// 指标后缀文本内容
const suffixContent = ref('')

// 是否展示指标名称
const indicatorNameShow = ref(false)
// 指标名称是否展示在数值下方
const indicatorNamePositionBottom = ref(true)

// 指标名称外层样式，主要控制名称与数值间距
const indicatorNameWrapperStyle = reactive<CSSProperties>({
  'margin-top': DEFAULT_INDICATOR_NAME_STYLE.nameValueSpacing + 'px'
})

// 指标名称文本样式，渲染时会被视图自定义样式覆盖
const indicatorNameClass = ref<CSSProperties>({
  color: DEFAULT_INDICATOR_NAME_STYLE.color,
  'font-size': DEFAULT_INDICATOR_NAME_STYLE.fontSize + 'px',
  'font-family': defaultTo(
    props.fontFamily,
    CHART_FONT_FAMILY_MAP[DEFAULT_INDICATOR_STYLE.fontFamily]
  ),
  'font-weight': DEFAULT_INDICATOR_NAME_STYLE.isBolder ? 'bold' : 'normal',
  'font-style': DEFAULT_INDICATOR_NAME_STYLE.isItalic ? 'italic' : 'normal',
  'letter-spacing': DEFAULT_INDICATOR_NAME_STYLE.letterSpace + 'px',
  'text-shadow': DEFAULT_INDICATOR_NAME_STYLE.fontShadow ? '2px 2px 4px' : 'none',
  'font-synthesis': 'weight style'
})

// 根据视图配置刷新指标卡样式和名称展示状态
const renderChart = async view => {
  if (!view) {
    return
  }

  const TEMP_DEFAULT_CHART = cloneDeep(BASE_VIEW_CONFIG)
  delete TEMP_DEFAULT_CHART.customAttr.basicStyle.alpha

  const chart = deepCopy({
    ...defaultsDeep(view, TEMP_DEFAULT_CHART),
    data: chartData.value
  }) as ChartObj

  recursionTransObj(customAttrTrans, chart.customAttr, scale.value, terminal.value)
  recursionTransObj(customStyleTrans, chart.customStyle, scale.value, terminal.value)

  if (chart.customAttr) {
    const { indicator, indicatorName } = chart.customAttr

    if (indicator) {
      switch (indicator.hPosition) {
        case 'left':
          contentStyle.value['align-items'] = 'flex-start'
          break
        case 'right':
          contentStyle.value['align-items'] = 'flex-end'
          break
        default:
          contentStyle.value['align-items'] = 'center'
      }
      switch (indicator.vPosition) {
        case 'top':
          contentStyle.value['justify-content'] = 'flex-start'
          break
        case 'bottom':
          contentStyle.value['justify-content'] = 'flex-end'
          break
        default:
          contentStyle.value['justify-content'] = 'center'
      }

      indicatorColor.value = indicator.color
      let suffixColor = indicator.suffixColor

      indicatorClass.value = {
        color: thresholdColor.value.color,
        'font-size': indicator.fontSize + 'px',
        'font-family': defaultTo(
          indicator.fontFamily,
          CHART_FONT_FAMILY_MAP[DEFAULT_INDICATOR_STYLE.fontFamily]
        ),
        'font-weight': indicator.isBolder ? 'bold' : 'normal',
        'font-style': indicator.isItalic ? 'italic' : 'normal',
        'letter-spacing': indicator.letterSpace + 'px',
        'text-shadow': indicator.fontShadow ? '2px 2px 4px' : 'none',
        'font-synthesis': 'weight style'
      }
      contentStyle.value['background-color'] = thresholdColor.value.backgroundColor

      indicatorSuffixClass.value = {
        color: suffixColor,
        'font-size': indicator.suffixFontSize + 'px',
        'font-family': defaultTo(
          indicator.suffixFontFamily,
          CHART_FONT_FAMILY_MAP[DEFAULT_INDICATOR_STYLE.suffixFontFamily]
        ),
        'font-weight': indicator.suffixIsBolder ? 'bold' : 'normal',
        'font-style': indicator.suffixIsItalic ? 'italic' : 'normal',
        'letter-spacing': indicator.suffixLetterSpace + 'px',
        'text-shadow': indicator.suffixFontShadow ? '2px 2px 4px' : 'none',
        'font-synthesis': 'weight style'
      }

      showSuffix.value = indicator.suffixEnable
      suffixContent.value = defaultTo(indicator.suffix, '')
    }
    if (indicatorName?.show) {
      let nameColor = indicatorName.color

      indicatorNameShow.value = true
      indicatorNameClass.value = {
        color: nameColor,
        'font-size': indicatorName.fontSize + 'px',
        'font-family': defaultTo(
          indicatorName.fontFamily,
          CHART_FONT_FAMILY_MAP[DEFAULT_INDICATOR_NAME_STYLE.fontFamily]
        ),
        'font-weight': indicatorName.isBolder ? 'bold' : 'normal',
        'font-style': indicatorName.isItalic ? 'italic' : 'normal',
        'letter-spacing': indicatorName.letterSpace + 'px',
        'text-shadow': indicatorName.fontShadow ? '2px 2px 4px' : 'none',
        'font-synthesis': 'weight style'
      }
      indicatorNameWrapperStyle['margin-top'] =
        (indicatorName.nameValueSpacing ?? DEFAULT_INDICATOR_NAME_STYLE.nameValueSpacing) + 'px'
      indicatorNamePositionBottom.value = indicatorName.namePosition !== 'top'
    } else {
      indicatorNameShow.value = false
      indicatorNamePositionBottom.value = false
    }
  }
}

// 请求指标卡数据并在成功后刷新图表状态
const calcData = (view, callback) => {
  if (view.tableId || view['dataFrom'] === 'template') {
    state.loading = true
    isError.value = false
    const v = JSON.parse(JSON.stringify(view))
    getData(v)
      .then(res => {
        if (res.code && res.code !== 0) {
          isError.value = true
          errMsg.value = res.msg
        } else {
          chartData.value = res?.data as Partial<Chart['data']>
          emit('onDrillFilters', res?.drillFilters)

          dvMainStore.setViewDataDetails(view.id, res)
          renderChart(res)
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

// 执行交互菜单中的单项动作，包括联动、下钻、跳转和组件事件
const trackClick = trackAction => {
  const param = state.pointParam
  if (!param?.data?.dimensionList && !param?.data?.quotaList) {
    return
  }
  const linkageParam = {
    option: 'linkage',
    innerType: 'indicator',
    name: state.pointParam.data.name,
    viewId: view.value.id,
    dimensionList: state.pointParam.data.dimensionList,
    quotaList: state.pointParam.data.quotaList,
    customFilter: state.pointParam.data.customFilter
  }
  const jumpParam = {
    option: 'jump',
    innerType: 'indicator',
    name: state.pointParam.data.name,
    viewId: view.value.id,
    dimensionList: state.pointParam.data.dimensionList,
    quotaList: state.pointParam.data.quotaList,
    sourceType: state.pointParam.data.sourceType
  }

  const clickParams = {
    option: 'pointClick',
    innerType: 'indicator',
    name: state.pointParam.data.name,
    viewId: view.value.id,
    dimensionList: state.pointParam.data.dimensionList,
    quotaList: state.pointParam.data.quotaList,
    customFilter: state.pointParam.data.customFilter
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
    case 'event_jump':
    case 'event_download':
    case 'event_share':
    case 'event_fullScreen':
    case 'event_showHidden':
    case 'event_refreshDataV':
    case 'event_refreshView':
      emit('onComponentEvent', jumpParam)
      break
    default:
      break
  }
}

// 计算当前指标卡可用的交互菜单项
const trackMenu = computed(() => {
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
  // 跳转、联动、下钻同时存在且联动模式为自动时，合并为“跳转”和“联动并下钻”两个入口
  if (trackMenuInfo.length === 3 && props.element.actionSelection.linkageActive === 'auto') {
    trackMenuInfo = ['jump', 'linkageAndDrill']
  } else if (
    trackMenuInfo.length === 2 &&
    props.element.actionSelection.linkageActive === 'auto' &&
    !trackMenuInfo.includes('jump')
  ) {
    trackMenuInfo = ['linkageAndDrill']
  }
  if (commonParams.value?.eventEnable) {
    trackMenuInfo.push('event_' + commonParams.value?.eventType)
  }
  return trackMenuInfo
})

// 是否展示可点击光标，嵌入式回调模式即使无菜单也需要保留点击提示
const showCursor = computed(() => {
  return trackMenu.value.length || embeddedCallBack.value === 'yes'
})

// 嵌入式回调模式下直接触发点选事件
const pointClickTrans = () => {
  if (embeddedCallBack.value === 'yes') {
    trackClick('pointClick')
  }
}

// 统一处理指标卡点击，根据菜单数量直接执行或展示交互菜单
const action = param => {
  state.pointParam = param
  // 先处理嵌入式点选回调
  pointClickTrans()
  // 只有一个交互入口时直接执行，多个入口时展示选择菜单
  if (trackMenu.value.length < 2) {
    trackClick(trackMenu.value[0])
  } else {
    setTimeout(() => {
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
      viewTrack.value.trackButtonClick()
    }, 200)
  }
}

// 将指标卡 DOM 点击事件转换为图表通用点击参数
const onPointClick = event => {
  if (view.value?.yAxis?.length) {
    const axis = view.value.yAxis[0]
    // 读取鼠标在视口中的点击坐标
    const mouseX = event.clientX
    const mouseY = event.clientY

    // 获取指标卡根节点相对视口的位置
    const rect = indicatorRef.value.getBoundingClientRect()
    const offsetX = rect.left
    const offsetY = rect.top

    // 换算为指标卡内部坐标，供交互菜单定位使用
    const left = mouseX - offsetX
    let top = mouseY - offsetY
    // 构造与图表点选一致的参数结构
    const params = {
      x: left,
      y: top,
      data: {
        name: axis.name,
        dimensionList: view.value.xAxis,
        quotaList: view.value.yAxis,
        customFilter: view.value.customFilter
      }
    }
    action(params)
  }
}

defineExpose({
  calcData,
  renderChart
})
</script>

<template>
  <div
    ref="indicatorRef"
    :class="{ 'menu-point': showCursor }"
    :style="contentStyle"
    @mouseup="onPointClick"
  >
    <view-track-bar
      ref="viewTrack"
      :track-menu="trackMenu"
      :font-family="fontFamily"
      class="track-bar"
      :style="state.trackBarStyle"
      @trackClick="trackClick"
      :is-data-v-mobile="dataVMobile"
    />
    <div v-if="indicatorNameShow && !indicatorNamePositionBottom">
      <span :style="indicatorNameClass">{{ resultName }}</span>
      <div :style="indicatorNameWrapperStyle"></div>
    </div>
    <div>
      <span :style="indicatorClass">{{ formattedResult }}</span>
      <span :style="indicatorSuffixClass" v-if="showSuffix">{{ suffixContent }}</span>
    </div>
    <div v-if="indicatorNameShow && indicatorNamePositionBottom">
      <div :style="indicatorNameWrapperStyle"></div>
      <span :style="indicatorNameClass">{{ resultName }}</span>
    </div>
  </div>
</template>

<style scoped lang="less">
.menu-point {
  cursor: pointer;
}
</style>
