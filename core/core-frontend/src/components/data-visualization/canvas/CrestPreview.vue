<script setup lang="ts">
import { getCanvasStyle, getShapeItemStyle } from '@/utils/style'
import ComponentWrapper from './ComponentWrapper.vue'
import { changeStyleWithScale } from '@/utils/translate'
import {
  computed,
  nextTick,
  PropType,
  ref,
  toRefs,
  watch,
  onBeforeUnmount,
  onMounted,
  reactive
} from 'vue'
import { changeRefComponentsSizeWithScalePoint } from '@/utils/changeComponentsSizeWithScale'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import elementResizeDetectorMaker from 'element-resize-detector'
import UserViewEnlarge from '@/components/visualization/UserViewEnlarge.vue'
import CanvasOptBar from '@/components/visualization/CanvasOptBar.vue'
import { isDashboard, isMainCanvas, refreshOtherComponent } from '@/utils/canvasUtils'
import { activeWatermarkCheckUser } from '@/components/watermark/watermark'
import router from '@/router'
import PopArea from '@/custom-component/pop-area/Component.vue'
import CanvasFilterBtn from '@/custom-component/canvas-filter-btn/Component.vue'
import { useEmitt } from '@/hooks/web/useEmitt'
import DatasetParamsComponent from '@/components/visualization/DatasetParamsComponent.vue'
import CrestFullscreen from '@/components/visualization/common/CrestFullscreen.vue'
import EmptyBackground from '../../empty-background/src/EmptyBackground.vue'
import LinkOptBar from '@/components/data-visualization/canvas/LinkOptBar.vue'
import { isDesktop } from '@/utils/ModelUtil'
import { isMobile } from '@/utils/utils'
import { useI18n } from '@/hooks/web/useI18n'
/** 主画布仓库提供预览渲染所需的全局状态 */
const dvMainStore = dvMainStoreWithOut()
/** 画布矩阵、当前组件、移动端和画布状态引用 */
const { pcMatrixCount, curComponent, mobileInPc, canvasState, inMobile } = storeToRefs(dvMainStore)
/** 原生宿主交互句柄 */
const openHandler = ref(null)
/** 自定义数据集参数弹窗实例 */
const customDatasetParamsRef = ref(null)
/** 预览组件向父级通知重置布局事件 */
const emits = defineEmits(['onResetLayout'])
/** 全屏控制组件实例 */
const fullScreeRef = ref(null)
/** 当前预览内容是否超出容器高度 */
const isOverSize = ref(false)
/** 当前运行环境是否为桌面客户端 */
const isDesktopFlag = isDesktop()
/** 国际化翻译函数 */
const { t } = useI18n()
/** 预览画布入参，承接画布样式、组件数据和外部控制参数 */
const props = defineProps({
  canvasStyleData: {
    type: Object as PropType<Record<string, any>>,
    required: true
  },
  componentData: {
    type: Object as PropType<any[] | Record<string, any>>,
    required: true
  },
  canvasViewInfo: {
    type: Object as PropType<Record<string, any>>,
    required: true
  },
  dvInfo: {
    type: Object as PropType<Record<string, any>>,
    required: true
  },
  canvasId: {
    type: String,
    required: false,
    default: 'canvas-main'
  },
  showPosition: {
    required: false,
    type: String,
    default: 'preview'
  },
  previewActive: {
    type: Boolean,
    default: true
  },
  downloadStatus: {
    type: Boolean,
    default: false
  },
  userId: {
    type: String,
    require: false
  },
  outerScale: {
    type: Number,
    required: false,
    default: 1
  },
  outerSearchCount: {
    type: Number,
    required: false,
    default: 0
  },
  isSelector: {
    type: Boolean,
    default: false
  },
  // 显示悬浮按钮
  showPopBar: {
    type: Boolean,
    default: false
  },
  // 字体
  fontFamily: {
    type: String,
    required: false,
    default: 'inherit'
  },
  // 联动按钮位置
  showLinkageButton: {
    type: Boolean,
    default: true
  },
  outerScreenAdaptor: {
    type: String,
    required: false,
    default: null
  },
  curGap: {
    type: Number,
    required: false,
    default: 0
  }
})

/** 将常用入参转换为响应式引用 */
const {
  canvasStyleData,
  componentData,
  dvInfo,
  canvasId,
  canvasViewInfo,
  showPosition,
  previewActive,
  downloadStatus,
  outerScale,
  outerSearchCount,
  showPopBar,
  fontFamily,
  outerScreenAdaptor
} = toRefs(props)
/** 预览画布 DOM 编号 */
const domId = 'preview-' + canvasId.value
/** 按宽度计算的缩放比例 */
const scaleWidthPoint = ref(100)
/** 按高度计算的缩放比例 */
const scaleHeightPoint = ref(100)
/** 当前预览采用的最终缩放比例 */
const scaleMin = ref(100)
/** 预览画布 DOM 引用 */
const previewCanvas = ref(null)
/** 仪表板矩阵单元宽度 */
const cellWidth = ref(10)
/** 仪表板矩阵单元高度 */
const cellHeight = ref(10)
/** 图表放大弹窗实例 */
const userViewEnlargeRef = ref(null)
/** 预览内部刷新计数 */
const searchCount = ref(0)
/** 定时刷新任务句柄 */
const refreshTimer = ref(null)
/** 布局计算是否完成 */
const renderReady = ref(false)
/** 当前预览资源是否为仪表板 */
const dashboardActive = computed(() => {
  return dvInfo.value.type === 'dashboard'
})
/** 预览运行状态，包含初始化和滚动位置 */
const state = reactive({
  initState: true,
  scrollMain: 0
})

/** 当前大屏自适应策略，优先使用外部覆盖值 */
const screenAdaptor = computed(() => {
  return outerScreenAdaptor.value || canvasStyleData.value?.screenAdaptor
})

/** 当前合并后的刷新计数 */
const curSearchCount = computed(() => {
  return outerSearchCount.value + searchCount.value
})
// 大屏是否保持宽高比例 非全屏 full 都需要保持宽高比例
/** 大屏预览是否需要保持宽高比例 */
const dataVKeepRadio = computed(() => {
  return screenAdaptor.value !== 'full'
})

// 仪表板是否跟随宽度缩放 非全屏 full 都需要保持宽高比例
/** 仪表板是否按宽度缩放 */
const dashboardScaleWithWidth = computed(() => {
  return isDashboard() && canvasStyleData.value?.dashboardAdaptor === 'withWidth'
})
/** 当前预览是否来自报表页面 */
const isReport = computed(() => {
  return !!router.currentRoute.value.query?.report
})

/** 隐藏区域组件列表，移动端会保留仪表板隐藏组件入口 */
const popComponentData = computed(() =>
  componentData.value.filter(
    ele =>
      ele.category &&
      ele.category === 'hidden' &&
      (!ele?.dashboardHidden || (ele?.dashboardHidden && isMobile()))
  )
)

/** 基础画布组件列表，排除隐藏区和框选区域组件 */
const baseComponentData = computed(() =>
  componentData.value.filter(
    ele =>
      ele?.category !== 'hidden' &&
      ele.component !== 'GroupArea' &&
      (!ele?.dashboardHidden || (ele?.dashboardHidden && isMobile()))
  )
)
/** 预览画布容器样式，按资源类型和自适应策略生成 */
const canvasStyle = computed<Record<string, any>>(() => {
  let style: Record<string, any> = {}
  if (isMainCanvas(canvasId.value) && !isDashboard()) {
    style['overflowY'] = 'hidden !important'
  }
  if (canvasStyleData.value && canvasStyleData.value.width && isMainCanvas(canvasId.value)) {
    style = getCanvasStyle(canvasStyleData.value)
    if (screenAdaptor.value === 'keep') {
      style['height'] = canvasStyleData.value?.height + 'px'
      style['width'] = canvasStyleData.value?.width + 'px'
      style['margin'] = 'auto'
    } else if (screenAdaptor.value === 'keepProportion') {
      style['aspect-ratio'] = canvasStyleData.value?.width / canvasStyleData.value?.height
      style['height'] = 'auto'
      style['width'] = 'auto'
    } else {
      style['height'] = dashboardActive.value
        ? downloadStatus.value
          ? getDownloadStatusMainHeight()
          : '100%'
        : !screenAdaptor.value || screenAdaptor.value === 'widthFirst'
        ? changeStyleWithScale(canvasStyleData.value?.height, scaleMin.value) + 'px'
        : '100%'
      style['width'] =
        !dashboardActive.value && screenAdaptor.value === 'heightFirst'
          ? changeStyleWithScale(canvasStyleData.value?.width, scaleHeightPoint.value) + 'px'
          : '100%'
    }
  }
  return style
})

/** 下载场景下按子节点样式计算主画布高度 */
const getDownloadStatusMainHeightV2 = () => {
  if (!previewCanvas.value?.childNodes) {
    nextTick(() => {
      canvasStyle.value.height = getDownloadStatusMainHeight()
    })
    return '100%'
  }
  const children = previewCanvas.value.childNodes
  let maxBottomPosition = 0

  children.forEach(child => {
    const childElement = child as HTMLElement
    // 获取style中的top值
    const styleTop = childElement.style?.top || '0'
    // 获取style中的height
    const styleHeight = childElement.style?.height || '0'

    // 转换为数字
    const top = parseFloat(styleTop) || 0
    const height = parseFloat(styleHeight) || 0

    // 计算底部位置
    const bottomPosition = top + height

    if (bottomPosition > maxBottomPosition) {
      maxBottomPosition = bottomPosition
    }
  })
  return `${maxBottomPosition}px`
}

/** 下载场景下按子节点实际占位计算主画布高度 */
const getDownloadStatusMainHeight = () => {
  if (!previewCanvas.value?.childNodes) {
    nextTick(() => {
      canvasStyle.value.height = getDownloadStatusMainHeight()
    })
    return '100%'
  }
  const children = previewCanvas.value.childNodes
  let maxHeight = 0

  children.forEach(child => {
    const childElement = child as HTMLElement
    const height = (childElement.offsetHeight || 0) + (childElement.offsetTop || 0)
    if (height > maxHeight) {
      maxHeight = height
    }
  })
  return `${maxHeight}px!important`
}

/** 预览重新激活时恢复布局 */
watch(
  () => previewActive.value,
  () => {
    if (previewActive.value) {
      restore()
    }
  }
)

useEmitt({
  name: 'tabCanvasChange-' + canvasId.value,
  callback: function () {
    restore()
  }
})

useEmitt({
  name: 'componentRefresh',
  callback: function () {
    if (isMainCanvas(canvasId.value)) {
      refreshDataV()
    }
  }
})

useEmitt({
  name: 'canvasFullscreen',
  callback: function () {
    if (isMainCanvas(canvasId.value)) {
      fullScreeRef.value.toggleFullscreen()
    }
  }
})

/** 重新计算预览容器尺寸、缩放比例和组件样式 */
const resetLayout = () => {
  if (downloadStatus.value) {
    return
  }
  nextTick(() => {
    if (previewCanvas.value) {
      // div 容器获取 tableBox.value.clientWidth
      let canvasWidth = previewCanvas.value.clientWidth
      let canvasHeight = previewCanvas.value.clientHeight
      scaleWidthPoint.value = (canvasWidth * 100) / canvasStyleData.value.width
      if (dashboardScaleWithWidth.value) {
        scaleHeightPoint.value = scaleWidthPoint.value * 0.7
      } else {
        scaleHeightPoint.value = (canvasHeight * 100) / canvasStyleData.value.height
      }
      scaleMin.value =
        isDashboard() && !dashboardScaleWithWidth.value
          ? Math.floor(Math.min(scaleWidthPoint.value, scaleHeightPoint.value))
          : scaleWidthPoint.value
      if (dashboardActive.value) {
        cellWidth.value = canvasWidth / pcMatrixCount.value.x
        // 如果是保持比例 则宽高相同
        if (dashboardScaleWithWidth.value) {
          cellHeight.value = cellWidth.value * 1.6
        } else {
          cellHeight.value = canvasHeight / pcMatrixCount.value.y
        }
        scaleMin.value = isMainCanvas(canvasId.value)
          ? scaleMin.value * 1.2
          : outerScale.value * 100
      } else {
        // 需要保持宽高比例时 高度伸缩和宽度伸缩保持一致 否则 高度伸缩单独计算
        // tip 当当前画布是tab时 使用的事 outerScale.value 因为 canvasStyleData.value为 {} 此处取数逻辑需进一步优化
        const scaleMinHeight = dataVKeepRadio.value ? scaleMin.value : scaleHeightPoint.value
        changeRefComponentsSizeWithScalePoint(
          baseComponentData.value,
          canvasStyleData.value,
          scaleMin.value || outerScale.value * 100,
          scaleMinHeight || outerScale.value * 100,
          outerScale.value * 100
        )
        scaleMin.value = isMainCanvas(canvasId.value) ? scaleMin.value : outerScale.value * 100
      }
      renderReady.value = true
      emits('onResetLayout')
      isOverSize.value = false
      if (previewCanvas.value?.clientHeight - previewCanvas.value?.parentNode?.clientHeight > 0) {
        isOverSize.value = true
      }
    }
  })
}
/** 非报表场景恢复预览布局 */
const restore = () => {
  if (isReport.value) {
    return
  }
  resetLayout()
}

/** 获取组件在预览画布中的定位样式 */
const getShapeItemShowStyle = item => {
  return getShapeItemStyle(item, {
    dvModel: dvInfo.value.type,
    cellWidth: cellWidth.value,
    cellHeight: cellHeight.value,
    curGap: previewCurGap.value
  })
}

/** 仪表板预览下的组件间距 */
const previewCurGap = computed(() => {
  return dashboardActive.value && dvMainStore.canvasStyleData.dashboard?.gap === 'yes'
    ? dvMainStore.canvasStyleData?.dashboard?.gapSize
    : 0
})

/** 初始化预览定时刷新任务 */
const initRefreshTimer = () => {
  // 数据刷新计时器 (仪表开启刷新并且是预览状态才启动刷新)
  if (canvasStyleData.value.refreshViewEnable && showPosition.value === 'preview') {
    searchCount.value = 0
    refreshTimer.value && clearInterval(refreshTimer.value)
    let refreshTime = 300000
    if (canvasStyleData.value.refreshTime && canvasStyleData.value.refreshTime > 0) {
      if (canvasStyleData.value.refreshUnit === 'second') {
        refreshTime = canvasStyleData.value.refreshTime * 1000
      } else {
        refreshTime = canvasStyleData.value.refreshTime * 60000
      }
    }
    refreshTimer.value = setInterval(() => {
      refreshDataV()
    }, refreshTime)
  }
}

/** 触发预览数据刷新，并通知主画布其他组件刷新 */
const refreshDataV = () => {
  searchCount.value++
  if (isMainCanvas(canvasId.value)) {
    refreshOtherComponent(dvInfo.value.id, dvInfo.value.type)
  }
}

/** 初始化预览水印 */
const initWatermark = (waterDomId = 'preview-canvas-main') => {
  if (dvInfo.value.watermarkInfo && isMainCanvas(canvasId.value) && !downloadStatus.value) {
    activeWatermarkCheckUser(waterDomId, canvasId.value, scaleMin.value / 100)
  }
}

/** 处理外部参数消息，仅接收目标资源匹配的主画布消息 */
const winMsgHandle = event => {
  const msgInfo = event.data
  if (msgInfo?.targetSourceId === dvInfo.value.id + '' && isMainCanvas(canvasId.value))
    if (msgInfo.type === 'attachParams') {
      winMsgOuterParamsHandle(msgInfo)
    } else if (msgInfo.type === 'webParams') {
      // 网络消息处理
      winMsgWebParamsHandle(msgInfo)
    }
}

/** 将网页参数写入基础组件过滤器 */
const winMsgWebParamsHandle = msgInfo => {
  const params = msgInfo.params
  dvMainStore.addWebParamsFilter(params, baseComponentData.value)
}

/** 将外部附加参数写入基础组件过滤器 */
const winMsgOuterParamsHandle = msgInfo => {
  const attachParams = msgInfo.params
  state.initState = false
  dvMainStore.addOuterParamsFilter(attachParams, baseComponentData.value, 'outer')
  state.initState = true
}

/** 挂载后初始化刷新、布局、尺寸监听和外部消息监听 */
onMounted(() => {
  initRefreshTimer()
  resetLayout()
  window.addEventListener('resize', restore)
  const erd = elementResizeDetectorMaker()
  erd.listenTo(document.getElementById(domId), () => {
    restore()
    initWatermark()
  })
  window.addEventListener('message', winMsgHandle)
})

/** 卸载前清理隐藏区状态、刷新计时器和消息监听 */
onBeforeUnmount(() => {
  // 初始化隐藏弹框区
  dvMainStore.canvasStateChange({ key: 'curPointArea', value: 'base' })
  clearInterval(refreshTimer.value)
  window.removeEventListener('message', winMsgHandle)
})

/** 打开图表放大弹窗 */
const userViewEnlargeOpen = (opt, item) => {
  userViewEnlargeRef.value.dialogInit(
    canvasStyleData.value,
    canvasViewInfo.value[item.id],
    item,
    opt,
    { scale: scaleMin.value / 100 }
  )
}
/** 点击预览空白区域时清空当前组件选择 */
const handleMouseDown = () => {
  if (showPosition.value !== 'viewDialog') {
    dvMainStore.setCurComponent({ component: null, index: null })
    if (!curComponent.value || (curComponent.value && curComponent.value.category !== 'hidden')) {
      dvMainStore.canvasStateChange({ key: 'curPointArea', value: 'base' })
    }
  }
}

/** 将图表点击参数发送到原生宿主或父窗口 */
const onPointClick = param => {
  try {
    if (window['crest-embedded-host'] && openHandler?.value) {
      const pm = {
        methodName: 'embeddedInteractive',
        args: {
          eventName: 'crest_inner_params',
          args: param
        }
      }
      openHandler.value.invokeMethod(pm)
    } else {
      const targetPm = {
        type: 'crest-embedded-interactive',
        eventName: 'crest_inner_params',
        args: param
      }
      window.parent.postMessage(targetPm, '*')
    }
  } catch (e) {
    console.warn('crest_inner_params send error')
  }
}

// v-if 使用 内容不渲染 默认参数不起用
/** 弹框区是否在当前主画布预览中可用 */
const popAreaAvailable = computed(
  () => canvasStyleData.value?.popupAvailable && isMainCanvas(canvasId.value)
)

/** 是否展示隐藏组件弹框按钮 */
const filterBtnShow = computed(
  () =>
    popAreaAvailable.value &&
    popComponentData.value &&
    popComponentData.value.length > 0 &&
    !inMobile.value &&
    canvasStyleData.value.popupButtonAvailable
)
/** 打开自定义数据集参数面板 */
const datasetParamsInit = item => {
  customDatasetParamsRef.value?.optInit(item)
}
/** 当前是否为大屏主画布预览 */
const dataVPreview = computed(
  () => dvInfo.value.type === 'dataV' && canvasId.value === 'canvas-main'
)

/** 是否展示悬浮联动操作栏 */
const linkOptBarShow = computed(() => {
  return Boolean(
    canvasStyleData.value.suspensionButtonAvailable &&
      ((!inMobile.value && !mobileInPc.value) || !isDashboard()) &&
      showPopBar.value &&
      !isDesktopFlag
  )
})

/** 预留的 PDF 下载入口 */
const downloadAsPDF = () => {
  // 当前由其他下载入口承接，保留方法供模板绑定
}

/** 记录预览画布滚动位置 */
const scrollPreview = () => {
  state.scrollMain = previewCanvas.value.scrollTop
}

/** 未发布资源提示是否展示 */
const showUnpublishFlag = computed(() => dvInfo.value?.status === 0 && isMainCanvas(canvasId.value))
/** 获取预览画布可视尺寸 */
const getPreviewCanvasSize = () => {
  return {
    innerWidth: previewCanvas.value.clientWidth,
    innerHeight: previewCanvas.value.clientHeight
  }
}
/** 预览内容超出且非保持尺寸时使用固定操作栏 */
const isFixedFlag = computed(
  () => isOverSize.value && canvasStyleData.value?.screenAdaptor !== 'keep'
)

defineExpose({
  restore,
  getPreviewCanvasSize,
  getDownloadStatusMainHeightV2
})
</script>

<template>
  <div
    :id="domId"
    class="canvas-container"
    :style="canvasStyle"
    :class="{
      'crest-download-custom': downloadStatus,
      'datav-preview': dataVPreview,
      'datav-preview-unpublish': showUnpublishFlag
    }"
    ref="previewCanvas"
    @mousedown="handleMouseDown"
    @scroll="scrollPreview"
    v-if="state.initState"
  >
    <!--弹框触发区域-->
    <canvas-filter-btn :is-fixed="isOverSize" v-if="filterBtnShow"></canvas-filter-btn>
    <!-- 弹框区域 -->
    <PopArea
      v-if="popAreaAvailable"
      :dv-info="dvInfo"
      :canvas-id="canvasId"
      :canvas-style-data="canvasStyleData"
      :canvasViewInfo="canvasViewInfo"
      :pop-component-data="popComponentData"
      :scale="scaleMin"
      :canvas-state="canvasState"
      :show-position="'preview'"
    ></PopArea>
    <canvas-opt-bar
      v-if="showLinkageButton"
      :canvas-id="canvasId"
      :canvas-style-data="canvasStyleData"
      :component-data="baseComponentData"
      :is-fixed="isFixedFlag"
    ></canvas-opt-bar>
    <template v-if="renderReady && !showUnpublishFlag">
      <component-wrapper
        v-for="(item, index) in baseComponentData"
        v-show="item.isShow"
        :active="item.id === (curComponent || {})['id']"
        :canvas-id="canvasId"
        :canvas-style-data="canvasStyleData"
        :dv-info="dvInfo"
        :cur-style="getShapeItemShowStyle(item)"
        :canvas-view-info="canvasViewInfo"
        :view-info="canvasViewInfo[item.id]"
        :key="index"
        :config="item"
        :style="getShapeItemShowStyle(item)"
        :show-position="showPosition"
        :search-count="curSearchCount"
        :scale="mobileInPc && isDashboard() ? 100 : scaleMin"
        :is-selector="props.isSelector"
        :font-family="canvasStyleData.fontFamily || fontFamily"
        :scroll-main="state.scrollMain"
        @userViewEnlargeOpen="userViewEnlargeOpen($event, item)"
        @datasetParamsInit="datasetParamsInit(item)"
        @onPointClick="onPointClick"
        :index="index"
      />
    </template>
    <empty-background
      v-if="showUnpublishFlag"
      :description="t('visualization.resource_not_published')"
      img-type="none"
    >
    </empty-background>
    <user-view-enlarge ref="userViewEnlargeRef"></user-view-enlarge>
  </div>
  <empty-background v-if="!state.initState" description="参数不能为空" img-type="noneWhite" />
  <CrestFullscreen ref="fullScreeRef"></CrestFullscreen>
  <dataset-params-component ref="customDatasetParamsRef"></dataset-params-component>
  <link-opt-bar
    v-if="linkOptBarShow"
    ref="link-opt-bar"
    :terminal="'pc'"
    :canvas-style-data="canvasStyleData"
    @link-export-pdf="downloadAsPDF"
  />
</template>

<style lang="less" scoped>
.canvas-container {
  background-size: 100% 100% !important;
  width: 100%;
  height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  position: relative;
  div::-webkit-scrollbar {
    width: 0px !important;
    height: 0px !important;
  }
  div {
    -ms-overflow-style: none; /* IE and Edge */
    scrollbar-width: none; /* Firefox */
  }
}

.fix-button {
  position: fixed !important;
}

.datav-preview {
  overflow-y: hidden !important;
}

.datav-preview-unpublish {
  background-color: inherit !important;
}
</style>
