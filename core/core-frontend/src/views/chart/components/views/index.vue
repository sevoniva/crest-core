<script lang="ts" setup>
import icon_info_outlined from '@/assets/svg/icon_info_outlined.svg'
import icon_linkRecord_outlined from '@/assets/svg/icon_link-record_outlined.svg'
import icon_viewinchat_outlined from '@/assets/svg/icon_viewinchat_outlined.svg'
import { cancelRequestBatch } from '@/config/axios/service'
import icon_drilling_outlined from '@/assets/svg/icon_drilling_outlined.svg'
import { useI18n } from '@/hooks/web/useI18n'
import ChartComponentG2Plot from './components/ChartComponentG2Plot.vue'
import Indicator from '@/custom-component/indicator/Indicator.vue'
import { useAppearanceStoreWithOut } from '@/store/modules/appearance'
import { useAppStoreWithOut } from '@/store/modules/app'
import { useEmbedded } from '@/store/modules/embedded'
import {
  computed,
  CSSProperties,
  nextTick,
  onBeforeUnmount,
  onMounted,
  PropType,
  provide,
  reactive,
  ref,
  shallowRef,
  toRefs,
  watch
} from 'vue'
import { useEmitt } from '@/hooks/web/useEmitt'
import { hexColorToRGBA, parseJson } from '@/views/chart/components/js/util.js'
import {
  CHART_FONT_FAMILY_MAP,
  DEFAULT_TITLE_STYLE
} from '@/views/chart/components/editor/util/chart'
import DrillPath from '@/views/chart/components/views/components/DrillPath.vue'
import { ElIcon, ElInput, ElMessage } from 'element-plus-secondary'
import { useFilter } from '@/hooks/web/useFilter'
import { useCache } from '@/hooks/web/useCache'

import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { cloneDeep, debounce } from 'lodash-es'
import ChartComponentS2 from '@/views/chart/components/views/components/ChartComponentS2.vue'
import { ChartLibraryType } from '@/views/chart/components/js/panel/types'
import chartViewManager from '@/views/chart/components/js/panel'
import { storeToRefs } from 'pinia'
import { checkAddHttp, setIdValueTrans } from '@/utils/canvasUtils'
import { Base64 } from 'js-base64'
import CrestRichTextView from '@/custom-component/rich-text/CrestRichTextView.vue'
import PictureGroup from '@/custom-component/picture-group/Component.vue'
import ChartEmptyInfo from '@/views/chart/components/views/components/ChartEmptyInfo.vue'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { viewFieldTimeTrans } from '@/utils/viewUtils'
import { CHART_TYPE_CONFIGS } from '@/views/chart/components/editor/util/chart'
import request from '@/config/axios'
import { store } from '@/store'
import { clearExtremum } from '@/views/chart/components/js/extremumUitl'
import CrestPreviewPopDialog from '@/components/visualization/CrestPreviewPopDialog.vue'
import { useRoute } from 'vue-router_2'
import { buildPublicLinkTargetUrl, isPublicLinkHash } from './publicLinkJump.mjs'
const route = useRoute()
const { wsCache } = useCache()
// 子图表组件实例，统一承接渲染、计算和联动清理调用
const chartComponent = ref<any>()
const { t } = useI18n()
const dvMainStore = dvMainStoreWithOut()
const { emitter } = useEmitt()
// 预览弹窗实例，用于内部仪表板和外链跳转的浮层打开
const crestPreviewPopDialogRef = ref(null)
let innerRefreshTimer = null
let innerSearchCount = 0
const appStore = useAppStoreWithOut()
const appearanceStore = useAppearanceStoreWithOut()
// 当前是否处于嵌入式集成模式，跳转和权限逻辑会按该状态分支
const isCrestBi = computed(() => appStore.getIsCrestBi)
// 当前是否处于 iframe 预览环境，内部跳转需要避免破坏宿主页面
const isIframe = computed(() => appStore.getIsIframe)

// 对外抛出图表点击和组件事件，供画布层继续处理
const emit = defineEmits(['onPointClick', 'onComponentEvent'])

const { nowPanelJumpInfo, dvInfo, curComponent, canvasStyleData, mobileInPc, inMobile, editMode } =
  storeToRefs(dvMainStore)

// 视图渲染入参，覆盖图表配置、展示位置、缩放和刷新触发源
const props = defineProps({
  // 公共参数集
  commonParams: {
    type: Object,
    required: false
  },
  active: {
    type: Boolean,
    default: false
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
  themes: {
    type: String,
    required: false,
    default: 'dark'
  },
  showPosition: {
    type: String,
    required: false,
    default: 'preview'
  },
  // 仪表板刷新计时器
  searchCount: {
    type: Number,
    required: false,
    default: 0
  },
  disabled: {
    type: Boolean,
    required: false,
    default: false
  },
  scale: {
    type: Number,
    required: false,
    default: 1
  },
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
  optType: {
    type: String,
    required: false
  }
})
// 动态区域编号，用于图表容器和模板区域关联
const dynamicAreaId = ref('')
const { view, showPosition, element, active, searchCount, scale, suffixId } = toRefs(props)
// 标题是否可见，富文本、图片组和弹窗视图不走普通标题栏
const titleShow = computed(() => {
  return (
    !['rich-text', 'picture-group'].includes(element.value.innerType) &&
    state.title_show &&
    showPosition.value !== 'viewDialog'
  )
})
const snapshotStore = snapshotStoreWithOut()

// 图表渲染态，集中保存标题样式、备注、下钻路径和当前视图数据
const state = reactive({
  initReady: true, //curComponent 切换期间 不接收外部的calcData 和 renderChart 事件
  title_show: true,
  title_remark: {
    show: false,
    remark: ''
  },
  title_class: {
    fontSize: '18px',
    color: '#303133',
    textAlign: 'left',
    fontStyle: 'normal',
    fontWeight: 'normal',
    background: '',
    fontFamily: '',
    textShadow: 'none',
    letterSpacing: '0px',
    fontSynthesis: 'style weight',
    width: 'fit-content',
    maxWidth: '100%',
    wordBreak: 'break-word',
    whiteSpace: 'pre-wrap!important'
  } as CSSProperties,
  drillFilters: [],
  viewInfoData: null,
  drillClickDimensionList: []
})

// 当前下钻层级数量，用于标题栏路径和重置逻辑判断
const drillClickLength = computed(() => state.drillClickDimensionList.length)

// 将标题水平对齐方式映射到 flex 容器的 justify-content
const titleAlign = computed<string>(() => {
  if (!titleShow.value) {
    return 'flex-start'
  }

  if (state.title_class.textAlign === 'center') {
    return 'center'
  } else if (state.title_class.textAlign === 'right') {
    return 'flex-end'
  }

  return 'flex-start'
})

// 当前图表支持的操作菜单，由具体图表组件在运行时暴露
const trackMenu = computed<Array<string>>(() => {
  return chartComponent?.value?.trackMenu ?? []
})

// 是否展示联动图标，联动和联动下钻复合菜单都需要入口
const hasLinkIcon = computed(() => {
  return trackMenu.value.indexOf('linkage') > -1 || trackMenu.value.indexOf('linkageAndDrill') > -1
})
// 是否展示跳转图标，移动端套壳内不展示跳转入口
const hasJumpIcon = computed(() => {
  return trackMenu.value.indexOf('jump') > -1 && !mobileInPc.value
})
// 是否展示下钻图标，普通下钻和联动下钻复合菜单都需要入口
const hasDrillIcon = computed(() => {
  return trackMenu.value.indexOf('drill') > -1 || trackMenu.value.indexOf('linkageAndDrill') > -1
})

// 图表数据请求状态，和仪表板刷新策略共同决定加载遮罩
const loading = ref(false)

// 仪表板结果展示模式，传入后端用于限制或合并返回结果
const resultMode = computed(() => {
  return canvasStyleData.value.dashboard?.resultMode || null
})

// 仪表板结果数量限制，作为图表数据查询的附加条件
const resultCount = computed(() => {
  return canvasStyleData.value.dashboard?.resultCount || null
})

const embeddedStore = useEmbedded()
// 编辑状态下 不启动刷新
const buildInnerRefreshTimer = (
  refreshViewEnable = false,
  refreshUnit = 'minute',
  refreshTime = 5
) => {
  if (showPosition.value === 'preview' && !innerRefreshTimer && refreshViewEnable) {
    innerRefreshTimer && clearInterval(innerRefreshTimer)
    const timerRefreshTime = refreshUnit === 'second' ? refreshTime * 1000 : refreshTime * 60000
    innerRefreshTimer = setInterval(() => {
      clearViewLinkage()
      queryData(false, true)
      innerSearchCount++
    }, timerRefreshTime)
  }
}

// 清除相同sourceViewId 的 联动条件
const clearViewLinkage = () => {
  dvMainStore.clearViewLinkage(element.value.id)
  useEmitt().emitter.emit('clearPanelLinkage', { viewId: element.value.id })
}

// 缩放比例变化后重新计算标题样式
watch([() => scale.value], () => {
  initTitle()
})

// 外部刷新计数变化时触发图表查询，内部定时器已启动时避免重复刷新
watch([() => searchCount.value], () => {
  // 内部计时器启动 忽略外部计时器
  if (!innerRefreshTimer) {
    queryData(false, true)
  }
})
// 仪表板的查询结果设置变化 图表数据需要刷新
// 结果数量变化会影响查询返回，必须重新拉取图表数据
watch([() => resultCount.value], () => {
  queryData()
})

// 结果模式变化会影响后端聚合输出，触发重新查询
watch([() => resultMode.value], () => {
  queryData()
})

// 缩放变化后通知图表组件重绘，保证画布尺寸和坐标重新适配
watch([() => scale.value], () => {
  nextTick(() => {
    chartComponent?.value?.renderChart?.(view.value)
  })
})

// 当前编辑组件切换时短暂屏蔽事件，避免旧组件触发重复计算
watch([() => curComponent.value], () => {
  if (curComponent.value && curComponent.value.id === view.value.id) {
    state.initReady = false
    nextTick(() => {
      state.initReady = true
    })
  }
})

// 向子图表透传最近一次扩展查询参数，插件和内置图表统一读取
const chartExtRequest = shallowRef(null)
provide('chartExtRequest', chartExtRequest)

// 根据图表自定义样式刷新标题显示、字体、阴影、背景和备注
const initTitle = () => {
  if (view.value.customStyle) {
    const customStyle = view.value.customStyle
    if (customStyle.text) {
      state.title_show = customStyle.text.show
      state.title_class.fontSize = customStyle.text.fontSize * scale.value + 'px'
      state.title_class.color = customStyle.text.color
      state.title_class.textAlign = customStyle.text.hPosition as CSSProperties['textAlign']
      state.title_class.fontStyle = customStyle.text.isItalic ? 'italic' : 'normal'
      state.title_class.fontWeight = customStyle.text.isBolder ? 'bold' : 'normal'
      if (!!appearanceStore.fontList.length) {
        appearanceStore.fontList.forEach(ele => {
          CHART_FONT_FAMILY_MAP[ele.name] = ele.name
        })
      }
      state.title_class.fontFamily = customStyle.text.fontFamily
        ? CHART_FONT_FAMILY_MAP[customStyle.text.fontFamily]
        : DEFAULT_TITLE_STYLE.fontFamily
      if (!CHART_FONT_FAMILY_MAP[customStyle.text.fontFamily]) {
        state.title_class.fontFamily = appearanceStore.fontList.find(ele => ele.isDefault)?.name
        customStyle.text.fontFamily = state.title_class.fontFamily
      }
      appearanceStore.setCurrentFont(state.title_class.fontFamily)
      state.title_class.letterSpacing =
        (customStyle.text.letterSpace
          ? customStyle.text.letterSpace
          : DEFAULT_TITLE_STYLE.letterSpace) + 'px'
      state.title_class.textShadow = customStyle.text.fontShadow ? '2px 2px 4px' : 'none'
    }
    if (customStyle.background) {
      state.title_class.background = hexColorToRGBA(
        customStyle.background.color,
        customStyle.background.alpha
      )
    }

    state.title_remark.show = customStyle.text.show && customStyle.text.remarkShow
    state.title_remark.remark = customStyle.text.remark
  }
}

// 回退到指定下钻层级，并用新的下钻过滤条件重新计算数据
const drillJump = (index: number) => {
  state.drillClickDimensionList.splice(index)
  view.value.chartExtRequest = filter()
  calcData(view.value)
}

// 将图表点选参数编码后抛给父级，供外部嵌入场景消费
const onPointClick = param => {
  try {
    const msg = {
      sourceDvId: dvInfo.value.id,
      sourceViewId: view.value.id,
      message: Base64.encode(JSON.stringify(param))
    }
    emit('onPointClick', msg)
  } catch (e) {
    console.warn('crest_inner_params send error')
  }
}

// 处理图表点击下钻，校验下钻字段后累积路径并重新查询
const chartClick = param => {
  if (!props.view.drillFields?.length) {
    return
  }
  // 下钻字段第一个没有在维度中不允许下钻
  const xIds = view.value.xAxis.map(ele => ele.id)
  if (xIds.indexOf(props.view.drillFields[0].id) == -1) {
    ElMessage.error(t('chart.drill_field_error'))
    return
  }
  if (view.value.type === 'circle-packing' && param.data.name === t('commons.all')) {
    ElMessage.error(t('chart.last_layer'))
    return
  }
  if (state.drillClickDimensionList.length < props.view.drillFields.length - 1) {
    state.drillClickDimensionList.push({
      dimensionList: param.data.dimensionList,
      extra: param.extra
    })
    view.value.chartExtRequest = filter()
    calcData(view.value)
  } else if (props.view.drillFields.length > 0) {
    ElMessage.error(t('chart.last_layer'))
  }
}

// 仪表板和大屏所有额外过滤参数都在此处
const filter = (firstLoad?: boolean) => {
  const { filter } = useFilter(view.value.id, firstLoad)
  const result = {
    user: wsCache.get('user.uid'),
    filter,
    linkageFilters: element.value.linkageFilters,
    outerParamsFilters: element.value.outerParamsFilters,
    webParamsFilters: element.value.webParamsFilters,
    drill: state.drillClickDimensionList,
    resultCount: resultCount.value,
    resultMode: resultMode.value
  }
  // 定时报告相关勿动
  if (route.path === '/preview' && route.query.taskId) {
    const sceneId = view.value['sceneId']
    const filterJson = window[`crest-report-filter-${sceneId}`]
    let filterObj = {}
    if (filterJson) {
      filterObj = JSON.parse(filterJson)
    }
    filterObj[view.value.id] = result
    window[`crest-report-filter-${sceneId}`] = JSON.stringify(filterObj)
  }
  return result
}

// 接收子图表返回的下钻过滤描述，用于标题栏路径展示
const onDrillFilters = param => {
  state.drillFilters = param ? param : []
}
// 外部窗口句柄代理，供嵌入式宿主接管新窗口行为
const openHandler = ref(null)
// 初始化外部窗口句柄，兼容插件或宿主容器提供的 invokeMethod 协议
const initOpenHandler = newWindow => {
  if (openHandler?.value) {
    const pm = {
      methodName: 'initOpenHandler',
      args: newWindow
    }
    openHandler.value.invokeMethod(pm)
  }
}

// 切换嵌入式宿主内的当前组件类型
const divEmbedded = type => {
  useEmitt().emitter.emit('changeCurrentComponent', type)
}

// 按跳转类型打开内部弹窗、当前窗口或新窗口
const windowsJump = (url, jumpType, size = 'middle') => {
  try {
    let newWindow
    if ('newPop' === jumpType) {
      crestPreviewPopDialogRef.value.previewInit({ url, size })
    } else if ('_self' === jumpType) {
      newWindow = window.open(url, jumpType)
    } else {
      newWindow = window.open(url, jumpType)
    }
    initOpenHandler(newWindow)
  } catch (e) {
    console.warn(t('visualization.url_check_error') + ':' + url)
  }
}

// 处理图表字段跳转，支持内部仪表板、外部链接和嵌入式容器分支
const jumpClick = param => {
  let dimension, jumpInfo, sourceInfo
  // 如果有名称name 获取和name匹配的dimension 否则倒序取最后一个能匹配的
  if (param.name) {
    const colList = [...param.dimensionList, ...param.quotaList]
    colList.forEach(dimensionItem => {
      if (
        dimensionItem.id === param.name ||
        dimensionItem.value === param.name ||
        dimensionItem.name === param.name
      ) {
        dimension = dimensionItem
        sourceInfo = param.viewId + '#' + dimension.id
        jumpInfo = nowPanelJumpInfo.value[sourceInfo]
      }
    })
  } else {
    for (let i = param.dimensionList.length - 1; i >= 0; i--) {
      dimension = param.dimensionList[i]
      sourceInfo = param.viewId + '#' + dimension.id
      jumpInfo = nowPanelJumpInfo.value[sourceInfo]
      if (jumpInfo) {
        break
      }
    }
  }
  if (jumpInfo) {
    // 维度日期类型转换
    viewFieldTimeTrans(dvMainStore.getViewDataDetails(param.viewId), param)
    param.sourceDvId = dvInfo.value.id
    param.sourceViewId = param.viewId
    param.sourceFieldId = dimension.id
    let embeddedBaseUrl = ''
    const divSelf = isCrestBi.value && jumpInfo.jumpType === '_self'
    const iframeSelf = isIframe.value && jumpInfo.jumpType === '_self'
    if (isCrestBi.value) {
      embeddedBaseUrl = embeddedStore.baseUrl
    }
    const jumpInfoParam = `&jumpInfoParam=${encodeURIComponent(
      Base64.encode(JSON.stringify(param))
    )}`

    // 内部仪表板跳转
    if (jumpInfo.linkType === 'inner') {
      if (jumpInfo.targetDvId) {
        const editPreviewParams = ['canvas', 'edit-preview'].includes(showPosition.value)
          ? '&editPreview=true'
          : ''
        const filterOuterParams = {}
        const curFilter = dvMainStore.getLastViewRequestInfo(param.viewId)
        const targetViewInfoList = jumpInfo.targetViewInfoList
        if (
          curFilter &&
          curFilter.filter &&
          curFilter.filter.length > 0 &&
          targetViewInfoList &&
          targetViewInfoList.length > 0
        ) {
          // do filter
          curFilter.filter.forEach(filterItem => {
            if (filterItem.filterFrom !== 'optionFilter') {
              targetViewInfoList.forEach(targetViewInfo => {
                if (targetViewInfo.sourceFieldActiveId === filterItem.filterId) {
                  const outerFilterItem = filterOuterParams[targetViewInfo.outerParamsName]
                  if (outerFilterItem) {
                    // 当前已经存在 根据arrayType 放置位置
                    if (filterItem['arrayType'] === 'END') {
                      outerFilterItem.value[outerFilterItem.value.length - 1] = filterItem.value[0]
                    } else {
                      outerFilterItem.value[0] = filterItem.value[0]
                    }
                  } else {
                    filterOuterParams[targetViewInfo.outerParamsName] = {
                      operator: filterItem.operator,
                      value: filterItem.value
                    }
                  }
                }
              })
            }
          })
        }
        let attachParamsInfo
        if (Object.keys(filterOuterParams).length > 0) {
          filterOuterParams['outerParamsVersion'] = 'v2'
          attachParamsInfo =
            '&attachParams=' + encodeURIComponent(Base64.encode(JSON.stringify(filterOuterParams)))
        }
        let url = `${embeddedBaseUrl}#/preview?dvId=${jumpInfo.targetDvId}&fromLink=true&dvType=${jumpInfo.targetDvType}`
        if (attachParamsInfo) {
          url = url + attachParamsInfo + jumpInfoParam + editPreviewParams
        } else {
          url = url + '&ignoreParams=true' + jumpInfoParam + editPreviewParams
        }
        if (isPublicLinkHash(window.location.hash)) {
          const publicLinkUrl = buildPublicLinkTargetUrl({
            hash: window.location.hash,
            targetDvId: jumpInfo.targetDvId,
            targetDvType: jumpInfo.targetDvType,
            attachParamsInfo,
            jumpInfoParam,
            editPreviewParams
          })
          crestPreviewPopDialogRef.value.previewInit({
            url: publicLinkUrl,
            size: jumpInfo.windowSize || 'large'
          })
          return
        }
        const currentUrl = window.location.href
        localStorage.setItem('beforeJumpUrl', currentUrl)
        if (divSelf || iframeSelf) {
          embeddedStore.setDvId(jumpInfo.targetDvId)
          embeddedStore.setJumpInfoParam(encodeURIComponent(Base64.encode(JSON.stringify(param))))
          divEmbedded('Preview')
          return
        }
        windowsJump(url, jumpInfo.jumpType, jumpInfo.windowSize)
      } else {
        ElMessage.warning('未指定跳转仪表盘')
      }
    } else {
      const colList = [...param.dimensionList, ...param.quotaList]
      let url = setIdValueTrans('id', 'value', jumpInfo.content, colList)
      url = checkAddHttp(url)

      if (isIframe.value || isCrestBi.value) {
        embeddedStore.clearState()
      }
      if (divSelf) {
        embeddedStore.setOuterUrl(url)
        divEmbedded('Iframe')
        return
      }

      windowsJump(url, jumpInfo.jumpType, jumpInfo.windowSize)
    }
  } else {
  }
}

// 从下拉筛选触发查询前取消旧请求，避免旧响应覆盖新图表
const queryDataFromSelect = (firstLoad = false) => {
  cancelRequestBatch(`/chart-data/data/${view.value.id}`)
  loading.value = false
  queryData(firstLoad)
}

// 组合最新过滤条件并防抖查询图表数据
const queryData = debounce((firstLoad = false, autoRefresh = false) => {
  if (loading.value) {
    return
  }
  const searched = dvMainStore.firstLoadMap.includes(element.value.id)
  let queryFilter = filter(searched ? false : firstLoad)
  if (showPosition.value.includes('viewDialog') || autoRefresh) {
    queryFilter = dvMainStore.getLastViewRequestInfo(view.value.id)
  }
  let params = cloneDeep(view.value)
  params['chartExtRequest'] = queryFilter
  chartExtRequest.value = queryFilter
  calcData(params)
}, 300)

// 调用内置或插件图表的数据计算入口，并维护加载状态
const calcData = params => {
  dvMainStore.setLastViewRequestInfo(params.id, params.chartExtRequest)
  if (chartComponent?.value) {
    loading.value = true
    if (view.value.isPlugin) {
      chartComponent?.value?.invokeMethod({
        methodName: 'calcData',
        args: [
          params,
          () => {
            loading.value = false
          }
        ]
      })
    } else {
      chartComponent?.value?.calcData?.(params, () => {
        loading.value = false
      })
    }
  }
}

// 判断当前图表是否应由指定图表库组件渲染
const showChartView = (...libs: ChartLibraryType[]) => {
  if (view.value?.render && view.value?.type) {
    const chartView = chartViewManager.getChartView(view.value.render, view.value.type)
    return chartView && libs?.includes(chartView.library)
  } else {
    return false
  }
}

// 部分场景不需要更新图表，例如放大页面
const listenerEnable = computed(() => {
  return !showPosition.value.includes('viewDialog')
})
// 存储所有数据集字段，用于判断图表拖入的字段是否存在
const viewAllDatasetFields = new Map()
// 是否展示空图表提示
const showEmpty = ref(false)
// 校验当前图表字段和必填轴配置是否满足渲染条件
const checkFieldIsAllowEmpty = (allField?) => {
  showEmpty.value = false
  if (view.value?.render && view.value?.type) {
    const chartView = chartViewManager.getChartView(view.value.render, view.value.type)
    // 插件
    if (!chartView) {
      return
    }
    const map = parseJson(view.value.customAttr).map
    if (['bubble-map', 'map'].includes(view.value?.type) && !map?.id) {
      showEmpty.value = true
      return
    }
    const axisConfigMap = new Map(Object.entries(chartView.axisConfig))
    // 验证拖入的字段是否包含在当前数据集字段中，如果有一个不在数据集字段中，则显示空图表
    let includatasetUiField = false
    if (allField && allField.length > 0) {
      viewAllDatasetFields.set(view.value.id, allField)
      outerLoop: for (const [key, value] of axisConfigMap) {
        // 只判断必须的
        if (value['allowEmpty']) continue
        if (!view.value?.[key]?.length) continue
        for (const item of view.value[key]) {
          if (!allField.find(field => field.id === item.id)) {
            includatasetUiField = true
            break outerLoop
          }
        }
      }
    }
    if (includatasetUiField) {
      showEmpty.value = true
      return
    }
    for (const [key, value] of axisConfigMap) {
      // 跳过允许为空的配置项
      if (value['allowEmpty']) continue

      // 如果有数据集字段并且字段值存在且不为空
      if (viewAllDatasetFields.get(view.value?.id)) {
        if (view.value?.[key]?.length) {
          // 检查图表字段是否有不在数据集中
          for (const item of view.value[key]) {
            if (!viewAllDatasetFields.get(view.value?.id).find(field => field.id === item.id)) {
              includatasetUiField = true
              break
            }
          }
        }
        // 如果有不在数据集中
        if (includatasetUiField) {
          showEmpty.value = true
          break
        }
      }

      // 如果没有限制长度，且值为空，标记为空并跳出
      if (!value['limit'] && view.value?.[key]?.length === 0) {
        showEmpty.value = true
        break
      }

      // 如果有限制长度，且字段长度不足，标记为空并跳出
      if (
        value['limit'] &&
        (!view.value?.[key] || view.value?.[key]?.length < parseInt(value['limit']))
      ) {
        showEmpty.value = true
        break
      }

      // 如果是table-info类型且字段为空，标记为空并跳出
      if (view.value?.type === 'table-info' && view.value?.[key]?.length === 0) {
        showEmpty.value = true
        break
      }
    }
  }
}
// 图表类型切换后重新判断字段和轴配置是否为空
const changeChartType = () => {
  checkFieldIsAllowEmpty()
}
// 数据集切换后重新判断已选字段是否仍存在
const changeDataset = () => {
  checkFieldIsAllowEmpty()
}

// 插件脚本是否已加载，控制插件组件挂载时机
const loadPlugin = ref(false)

// 渲染图表回调
const renderChartCallback = val => {
  if (!state.initReady) {
    return
  }
  initTitle()
  const viewInfo = val ? val : view.value
  nextTick(() => {
    if (view.value?.plugin?.isPlugin) {
      chartComponent?.value?.invokeMethod({
        methodName: 'renderChart',
        args: [viewInfo]
      })
      return
    }
    chartComponent?.value?.renderChart?.(viewInfo)
  })
}

// 注册可被展示位置开关拦截的事件监听
const registerEnabledEmitt = option => {
  useEmitt({
    name: option.name,
    callback: (...args) => {
      if (!listenerEnable.value) {
        return
      }
      option.callback(...args)
    }
  })
}

registerEnabledEmitt({
  name: `query-data-${view.value.id}`,
  callback: queryDataFromSelect
})
registerEnabledEmitt({
  name: 'checkShowEmpty',
  callback: param => {
    if (param.view?.id === view.value.id) {
      checkFieldIsAllowEmpty(param.allFields)
    }
  }
})
registerEnabledEmitt({ name: 'chart-type-change', callback: changeChartType })
registerEnabledEmitt({ name: 'dataset-change', callback: changeDataset })
registerEnabledEmitt({
  name: 'clearPanelLinkage',
  callback: param => {
    if (param.viewId === 'all' || param.viewId === element.value.id) {
      chartComponent?.value?.clearLinkage?.()
    }
  }
})
registerEnabledEmitt({
  name: 'snapshotChangeToView',
  callback: cacheViewInfo => {
    initTitle()
    nextTick(() => {
      if (
        cacheViewInfo.snapshotCacheViewCalc.includes(view.value.id) ||
        cacheViewInfo.snapshotCacheViewCalc.includes('all')
      ) {
        view.value.chartExtRequest = filter(false)
        calcData(view.value)
      } else if (
        cacheViewInfo.snapshotCacheViewRender.includes(view.value.id) ||
        cacheViewInfo.snapshotCacheViewRender.includes('all')
      ) {
        chartComponent?.value?.renderChart?.(view.value)
      }
    })
  }
})
registerEnabledEmitt({
  name: 'calcData-' + view.value.id,
  callback: val => {
    if (!state.initReady) {
      return
    }
    initTitle()
    nextTick(() => {
      view.value.chartExtRequest = filter(false)
      const targetVal = val || view.value
      calcData(targetVal)
    })
  }
})
registerEnabledEmitt({
  name: 'calcData-all',
  callback: () => {
    if (!state.initReady) {
      return
    }
    initTitle()
    nextTick(() => {
      view.value.chartExtRequest = filter(false)
      calcData(view.value)
    })
  }
})
registerEnabledEmitt({
  name: 'renderChart-' + view.value.id,
  callback: val => {
    renderChartCallback(val)
  }
})
registerEnabledEmitt({
  name: 'resetDrill-' + view.value.id,
  callback: val => {
    nextTick(() => {
      drillJump(val)
    })
  }
})
registerEnabledEmitt({
  name: 'tabCanvasChange-' + element.value.canvasId,
  callback: () => {
    if (!state.initReady && !view.value.type.includes('table')) {
      return
    }
    setTimeout(() => {
      chartComponent?.value?.renderChart?.(view.value)
    }, 200)
  }
})
registerEnabledEmitt({
  name: 'updateTitle-' + view.value.id,
  callback: () => {
    initTitle()
  }
})
registerEnabledEmitt({
  name: 'chart-type-change-' + view.value.id,
  callback: () => {
    const chart = cloneDeep(view.value)
    chart.container = 'container-' + showPosition.value + '-' + view.value.id + '-' + suffixId.value
    clearExtremum(chart)
    // 切换到不支持下钻的图表类型时，清除下钻状态
    const chartView = chartViewManager.getChartView(view.value.render, view.value.type)
    if (chartView && !chartView.axis.includes('drill')) {
      state.drillClickDimensionList = []
      state.drillFilters = []
    }
  }
})
if (showPosition.value === 'viewDialog') {
  useEmitt({
    name: 'renderChart-viewDialog-' + view.value.id,
    callback: val => {
      renderChartCallback(val)
    }
  })
}

onMounted(() => {
  if (!view.value.isPlugin) {
    state.drillClickDimensionList = view.value?.chartExtRequest?.drill ?? []
    queryData(!showPosition.value.includes('viewDialog'))
  } else {
    const searched = dvMainStore.firstLoadMap.includes(element.value.id)
    const queryFilter = filter(!searched)
    view.value['chartExtRequest'] = queryFilter
    chartExtRequest.value = queryFilter
    loadPlugin.value = true
  }
  if (!listenerEnable.value) {
    return
  }

  const { refreshViewEnable, refreshUnit, refreshTime } = view.value
  buildInnerRefreshTimer(refreshViewEnable, refreshUnit, refreshTime)

  initTitle()
})

onBeforeUnmount(() => {
  if (innerRefreshTimer) {
    clearInterval(innerRefreshTimer)
    innerRefreshTimer = null
  }
})

// 1.开启仪表板刷新 2.首次加载（searchCount =0 ）3.正在请求数据 则显示加载状态
const loadingFlag = computed(() => {
  return (
    (canvasStyleData.value.refreshViewLoading ||
      (searchCount.value === 0 && innerSearchCount === 0)) &&
    loading.value
  )
})

// 当前图表区域是否具备渲染条件，模板、插件和地图类走额外分支
const chartAreaShow = computed(() => {
  if (view.value.tableId) {
    if (element.value['state'] === undefined || element.value['state'] === 'ready') {
      return true
    }
  }
  if (['rich-text', 'picture-group'].includes(view.value.type)) {
    return true
  }
  if (view.value?.isPlugin) {
    return true
  }
  if (view.value['dataFrom'] === 'template') {
    return true
  }
  if (view.value.customAttr?.map?.id) {
    const MAP_CHARTS = ['map', 'bubble-map', 'flow-map']
    if (MAP_CHARTS.includes(view.value.type)) {
      return true
    }
  }
  return false
})

// 标题输入框引用，用于进入编辑态后自动聚焦
const titleInputRef = ref()
// 标题是否处于内联编辑状态
const titleEditStatus = ref(false)
// 进入标题编辑态，非激活或移动端预览时禁止编辑
function changeEditTitle() {
  if (!props.active || mobileInPc.value) {
    return
  }
  if (!titleEditStatus.value) {
    titleEditStatus.value = true
    nextTick(() => {
      titleInputRef.value?.focus()
      element.value['editing'] = true
    })
  }
}

// 离开标题输入框时恢复普通展示态
function onLeaveTitleInput() {
  element.value['editing'] = false
  titleEditStatus.value = false
}

//v-click-outside 指令
const vClickOutside = {
  beforeMount(el, binding) {
    // 在元素上绑定一个事件监听器
    el.clickOutsideEvent = function (event) {
      // 判断点击事件是否发生在元素外部
      if (!(el === event.target || el.contains(event.target))) {
        // 如果是外部点击，则执行绑定的函数
        binding.value(event)
      }
    }
    // 在全局添加点击事件监听器
    document.addEventListener('click', el.clickOutsideEvent)
  },
  unmounted(el) {
    // 在组件销毁前，移除事件监听器以避免内存泄漏
    document.removeEventListener('click', el.clickOutsideEvent)
  }
}

// 标题变更后同步画布组件名称并记录快照
function onTitleChange() {
  element.value.name = view.value.title
  element.value.label = view.value.title
  snapshotStore.recordSnapshotCache('onTitleChange')
}

// 提示气泡主题与画布主题反向，确保图标说明可读
const toolTip = computed(() => {
  return props.themes === 'dark' ? 'light' : 'dark'
})

// 标题区与图表区的间距，只有标题、菜单或备注存在时保留
const marginBottom = computed<string | 0>(() => {
  if (!titleShow.value) {
    return 0
  }
  if (titleShow.value || trackMenu.value.length > 0 || state.title_remark.show) {
    return 12 * scale.value + 'px'
  }
  return 0
})

// 标题栏操作图标尺寸随画布缩放同步变化
const iconSize = computed<string>(() => {
  return 16 * scale.value + 'px'
})

/**
 * 修改透明度
 * 边框透明度为0时会是存色，顾配置低透明度
 * @param {boolean} isBorder 是否为边框
 */
const modifyAlpha = isBorder => {
  const {
    backgroundColor = 'rgba(255,255,255,1)',
    backgroundType,
    backgroundImageEnable,
    backgroundColorSelect = true
  } = element.value.commonBackground || {}
  const safeBackgroundColor = String(backgroundColor || 'rgba(255,255,255,1)')
  // 透明
  const transparent = 'rgba(0,0,0,0.01)'
  // 背景图时，设置透明度为0.01
  if (backgroundType === 'outerImage' && backgroundImageEnable) return transparent
  // hex转rgba
  if (safeBackgroundColor.includes('#'))
    return isBorder || !backgroundColorSelect ? transparent : safeBackgroundColor
  const match = safeBackgroundColor.match(/rgba\((\d+),\s*(\d+),\s*(\d+),\s*(\d+|0?\.\d+)\)/)
  if (!match) return safeBackgroundColor
  const [r, g, b, a] = match.slice(1).map(Number)
  // 边框或者不设置背景色时，设置透明度为0.01，否则原透明度
  return `rgba(${r}, ${g}, ${b}, ${!backgroundColorSelect || isBorder ? 0.01 : a})`
}

// 标题隐藏时仍需给操作图标补背景和边框，避免悬浮在图表上不可辨认
const titleIconStyle = computed<CSSProperties>(() => {
  const bgColor = modifyAlpha(false)
  const borderColor = modifyAlpha(true)
  // 不显示标题时，图标的样式
  const style: CSSProperties = {
    position: 'absolute',
    border: `1px solid ${borderColor}`,
    'background-color': bgColor,
    'border-radius': '2px',
    padding: '0 2px 0 2px',
    'z-index': 1,
    top: '2px',
    left: '2px',
    ...(trackMenu.value.length ? {} : { display: 'none' })
  }
  return {
    color: canvasStyleData.value.component.seniorStyleSetting.linkageIconColor,
    ...(titleShow.value ? {} : style)
  }
})
// 鼠标是否悬停在图表区域内，用于延迟展示标题栏操作入口
const chartHover = ref(false)
// 是否展示标题栏操作图标
const showActionIcons = computed(() => {
  if (!chartHover.value) {
    return false
  }
  return trackMenu.value.length > 0 || state.title_remark.show
})
// 图表分类配置，插件加载后会追加到对应分类
const chartConfigs = ref(CHART_TYPE_CONFIGS)
// 当前图表类型是否已存在于本地图表分类配置
const pluginLoaded = computed(() => {
  let result = false
  chartConfigs.value.forEach(cat => {
    result = cat.details.find(chart => view.value?.type === chart.value) !== undefined
  })
  return result
})
// 将插件图表注册到现有图表分类中，保持编辑器侧的分类展示一致
const loadPluginCategory = data => {
  data.forEach(item => {
    const { category, title, render, chartValue, chartTitle, icon, staticMap } = item
    const node = {
      render,
      category,
      icon,
      value: chartValue,
      title: chartTitle,
      isPlugin: true,
      staticMap
    }
    if (view.value?.type === node.value) {
      view.value.plugin = {
        isPlugin: true,
        staticMap
      }
    }
    const stack = [...chartConfigs.value]
    let findParent = false
    while (stack?.length) {
      const parent = stack.pop()
      if (parent.category === category) {
        const chart = parent.details.find(chart => chart.value === node.value)
        if (!chart) {
          parent.details.push(node)
        }
        findParent = true
      }
    }
    if (!findParent) {
      stack.push({
        category,
        title,
        display: 'show',
        details: [node]
      })
    }
  })
}

// 富文本和图片组本身允许空数据渲染
const allEmptyCheck = computed(() => {
  return ['rich-text', 'picture-group'].includes(element.value.innerType)
})
/**
 * 标题提示的最大宽度
 */
const titleTooltipWidth = computed(() => {
  if (inMobile.value) {
    return `${screen.width - 10}px`
  }
  if (mobileInPc.value) {
    return '270px'
  }
  return '500px'
})
// 隐藏残留的 G2 提示层，避免图表切换后悬浮提示滞留
const clearG2Tooltip = () => {
  const g2TooltipWrapper = document.getElementById('g2-tooltip-wrapper')
  if (g2TooltipWrapper) {
    for (const ele of g2TooltipWrapper.children) {
      ele.style.display = 'none'
    }
  }
}
</script>

<template>
  <div
    class="chart-area report-load"
    :class="{ 'report-load-finish': !loadingFlag }"
    v-loading="loadingFlag"
    element-loading-background="rgba(0,0,0,0)"
    @mouseover="chartHover = true"
    @mouseleave="chartHover = false"
  >
    <div
      class="title-container"
      :style="{
        'justify-content': titleAlign,
        'margin-bottom': marginBottom
      }"
    >
      <template v-if="!titleEditStatus">
        <p class="ellipsis" v-if="titleShow" :style="state.title_class" @dblclick="changeEditTitle">
          {{ view.title }}
        </p>
      </template>
      <template v-else>
        <el-input
          style="flex: 1"
          :effect="canvasStyleData.dashboard.themeColor"
          ref="titleInputRef"
          v-model="view.title"
          @keydown.stop
          @keydown.enter="onLeaveTitleInput"
          v-click-outside="onLeaveTitleInput"
          @change="onTitleChange"
        />
      </template>
      <transition name="fade">
        <div v-show="showActionIcons" class="icons-container-out">
          <div
            class="icons-container"
            :class="{ 'is-editing': titleEditStatus }"
            :style="titleIconStyle"
          >
            <el-tooltip :effect="toolTip" placement="top" v-if="state.title_remark.show">
              <template #content>
                <div
                  :style="{
                    maxWidth: titleTooltipWidth,
                    wordBreak: 'break-all',
                    wordWrap: 'break-word',
                    whiteSpace: 'pre-wrap'
                  }"
                >
                  {{ state.title_remark.remark }}
                </div>
              </template>
              <el-icon :size="iconSize" class="inner-icon">
                <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
              </el-icon>
            </el-tooltip>
            <el-tooltip :effect="toolTip" placement="top" content="已设置联动" v-if="hasLinkIcon">
              <el-icon :size="iconSize" class="inner-icon">
                <Icon name="icon_link-record_outlined"
                  ><icon_linkRecord_outlined class="svg-icon"
                /></Icon>
              </el-icon>
            </el-tooltip>
            <el-tooltip
              :effect="toolTip"
              placement="top"
              :content="t('visualization.jump_set_tips')"
              v-if="hasJumpIcon"
            >
              <el-icon :size="iconSize" class="inner-icon">
                <Icon name="icon_viewinchat_outlined"
                  ><icon_viewinchat_outlined class="svg-icon"
                /></Icon>
              </el-icon>
            </el-tooltip>
            <el-tooltip
              :effect="toolTip"
              placement="top"
              :content="t('visualization.drill_set_tips')"
              v-if="hasDrillIcon"
            >
              <el-icon :size="iconSize" class="inner-icon">
                <Icon name="icon_drilling_outlined"
                  ><icon_drilling_outlined class="svg-icon"
                /></Icon>
              </el-icon>
            </el-tooltip>
          </div>
        </div>
      </transition>
    </div>
    <!--这里去渲染不同图库的图表-->
    <div v-if="allEmptyCheck || (chartAreaShow && !showEmpty)" style="flex: 1; overflow: hidden">
      <PictureGroup
        v-if="showChartView(ChartLibraryType.PICTURE_GROUP)"
        :themes="canvasStyleData.dashboard.themeColor"
        ref="chartComponent"
        :element="element"
        :active="active"
        :view="view"
        :show-position="showPosition"
        :suffixId="suffixId"
      >
      </PictureGroup>
      <CrestRichTextView
        v-else-if="showChartView(ChartLibraryType.RICH_TEXT)"
        :scale="scale"
        :themes="canvasStyleData.dashboard.themeColor"
        ref="chartComponent"
        :element="element"
        :disabled="!['canvas', 'canvasDataV'].includes(showPosition) || disabled"
        :active="active"
        :show-position="showPosition"
        :edit-mode="editMode"
        :suffixId="suffixId"
      />
      <Indicator
        :scale="scale"
        v-else-if="showChartView(ChartLibraryType.INDICATOR)"
        :themes="canvasStyleData.dashboard.themeColor"
        ref="chartComponent"
        :view="view"
        :element="element"
        :show-position="showPosition"
        :suffixId="suffixId"
        :font-family="fontFamily"
        :common-params="commonParams"
        @touchstart="clearG2Tooltip"
        @onChartClick="chartClick"
        @onPointClick="onPointClick"
        @onDrillFilters="onDrillFilters"
        @onJumpClick="jumpClick"
        @onComponentEvent="() => emit('onComponentEvent')"
      />
      <chart-component-g2-plot
        :scale="scale"
        :dynamic-area-id="dynamicAreaId"
        :view="view"
        :show-position="showPosition"
        :element="element"
        :suffixId="suffixId"
        :font-family="fontFamily"
        :active="active"
        v-else-if="
          showChartView(ChartLibraryType.G2_PLOT, ChartLibraryType.L7_PLOT, ChartLibraryType.L7)
        "
        ref="chartComponent"
        @onChartClick="chartClick"
        @onPointClick="onPointClick"
        @onDrillFilters="onDrillFilters"
        @onJumpClick="jumpClick"
        @resetLoading="() => (loading = false)"
      />
      <chart-component-s2
        :view="view"
        :scale="scale"
        :show-position="showPosition"
        :element="element"
        :drill-length="drillClickLength"
        :font-family="fontFamily"
        v-else-if="showChartView(ChartLibraryType.S2)"
        ref="chartComponent"
        @onPointClick="onPointClick"
        @onChartClick="chartClick"
        @onDrillFilters="onDrillFilters"
        @onJumpClick="jumpClick"
        :suffixId="suffixId"
      />
    </div>
    <chart-empty-info
      v-if="(!chartAreaShow || showEmpty) && !allEmptyCheck"
      :themes="canvasStyleData.dashboard.themeColor"
      :view-icon="view.type"
      @touchstart="clearG2Tooltip"
    ></chart-empty-info>
    <drill-path
      :disabled="optType === 'enlarge'"
      :drill-filters="state.drillFilters"
      @onDrillJump="drillJump"
    />
    <CrestPreviewPopDialog ref="crestPreviewPopDialogRef"></CrestPreviewPopDialog>
  </div>
</template>

<style lang="less" scoped>
.chart-area {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.title-container {
  position: relative;
  margin: 0;
  width: 100%;

  display: inline-flex;
  flex-wrap: nowrap;
  justify-content: center;

  gap: 8px;

  .icons-container-out {
    position: relative;
    .icons-container {
      position: absolute;
      left: 0;
      display: inline-flex;
      flex-direction: row;
      align-items: center;
      flex-wrap: nowrap;
      gap: 8px;

      color: #646a73;

      &.icons-container__dark {
        color: #a6a6a6;
      }

      &.is-editing {
        gap: 6px;
      }

      .inner-icon {
        cursor: pointer;
      }
    }
  }
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.5s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.ellipsis {
  white-space: nowrap !important;
  overflow: hidden;
  text-overflow: ellipsis;
  width: 100%;
}
</style>
