<script setup lang="ts">
import { findNewComponentFromList } from '@/custom-component/component-list' // 左侧列表数据
import { computed, nextTick, onMounted, reactive, ref, toRefs, onBeforeUnmount, watch } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { storeToRefs } from 'pinia'
import eventBus from '../../utils/eventBus'
import { guid } from '@/views/visualized/data/dataset/form/util.js'
import elementResizeDetectorMaker from 'element-resize-detector'
import { getCanvasStyle, syncShapeItemStyle } from '@/utils/style'
import { adaptCurThemeCommonStyle } from '@/utils/canvasStyle'
import CanvasCore from '@/components/data-visualization/canvas/CanvasCore.vue'
import { isMainCanvas, isDashboard } from '@/utils/canvasUtils'

/** 画布渲染器入参，承接画布样式、组件列表和当前画布上下文 */
const props = defineProps({
  canvasStyleData: {
    type: Object,
    required: true
  },
  componentData: {
    type: Array,
    required: true
  },
  canvasViewInfo: {
    type: Object,
    required: true
  },
  canvasId: {
    type: String,
    required: false,
    default: 'canvas-main'
  },
  canvasActive: {
    type: Boolean,
    default: true
  },
  outerScale: {
    type: Number,
    required: false,
    default: 1
  },
  // 仪表板字体
  fontFamily: {
    type: String,
    required: false,
    default: 'inherit'
  },
  // 画布位置
  canvasPosition: {
    type: String,
    required: false,
    default: 'main'
  }
})
/** 将画布入参转为响应式引用，便于监听和传递给子组件 */
const { canvasStyleData, componentData, canvasViewInfo, canvasId, canvasActive, canvasPosition } =
  toRefs(props)
/** 画布滚动容器的 DOM 编号 */
const domId = ref('crest-canvas-' + canvasId.value)

/** 主画布仓库提供矩阵、主题和移动端状态 */
const dvMainStore = dvMainStoreWithOut()
/** 快照仓库记录新增、拖拽和初始化后的画布状态 */
const snapshotStore = snapshotStoreWithOut()
/** 画布矩阵、主题和移动端预览状态引用 */
const { pcMatrixCount, curOriginThemes, mobileInPc } = storeToRefs(dvMainStore)
/** 外层画布容器引用，用于计算可用尺寸 */
const canvasOut = ref(null)
/** 内层滚动画布引用，用于滚动定位和记录滚动位置 */
const canvasInner = ref(null)
/** 画布核心是否可以挂载 */
const canvasInitStatus = ref(false)
/** 按宽度计算出的缩放百分比 */
const scaleWidth = ref(100)
/** 按高度计算出的缩放百分比 */
const scaleHeight = ref(100)
/** 宽高缩放中的最小值，作为仪表板实际缩放 */
const scaleMin = ref(100)

/** 画布容器尺寸和滚动状态 */
const state = reactive({
  screenWidth: 1920,
  screenHeight: 1080,
  curScrollTop: 0
})

// 仪表板矩阵信息适配
/** 仪表板单个矩阵单元宽度 */
const baseWidth = ref(0)
/** 仪表板单个矩阵单元高度 */
const baseHeight = ref(0)
/** 仪表板渲染中状态，用于控制初始化阶段样式 */
const renderState = ref(false) // 仪表板默认
/** 仪表板矩阵左侧边距 */
const baseMarginLeft = ref(0)
/** 仪表板矩阵顶部边距 */
const baseMarginTop = ref(0)
/** 栅格画布核心组件实例 */
const cyGridster = ref(null)
/** 画布编辑区 DOM 编号 */
const editDomId = ref('edit-' + canvasId.value)

/** 主画布编辑容器样式，非主画布使用空样式 */
const editStyle = computed(() => {
  if (canvasStyleData.value && isMainCanvas(canvasId.value)) {
    return {
      ...getCanvasStyle(canvasStyleData.value)
    }
  } else {
    return {}
  }
})

// 通过实时监听的方式直接添加组件
/** 从画布主区域事件创建新组件并加入栅格布局 */
const handleNewFromCanvasMain = newComponentInfo => {
  const { componentName, innerType, staticMap } = newComponentInfo
  if (componentName) {
    const component = findNewComponentFromList(componentName, innerType, curOriginThemes, staticMap)
    syncShapeItemStyle(component, baseWidth.value, baseHeight.value)
    component.id = guid()
    component.y = undefined
    component.x = cyGridster.value.findPositionX(component)
    dvMainStore.addComponent({
      component: component,
      index: undefined
    })
    adaptCurThemeCommonStyle(component)
    nextTick(() => {
      cyGridster.value.addItemBox(component) // 在适当的时候初始化布局组件。
      nextTick(() => {
        scrollTo(component.y)
      })
    })
    snapshotStore.recordSnapshotCacheWithPositionChange('renderChart', component.id)
  }
}

/** 处理组件拖入主画布后的放置和快照记录 */
const handleDrop = e => {
  if (isMainCanvas(canvasId.value)) {
    e.preventDefault()
    e.stopPropagation()
    const addComponent = cyGridster.value.getMoveItem()
    // 当前isShow =  true 则确定已经移入画布中
    addComponent.isShow = true
    syncShapeItemStyle(addComponent, baseWidth.value, baseHeight.value)
    cyGridster.value.handleMouseUp(e, addComponent, componentData.value.length - 1)
    snapshotStore.recordSnapshotCacheWithPositionChange('renderChart', addComponent.id)
  }
}

/** 处理拖拽悬停，驱动栅格占位预览 */
const handleDragOver = e => {
  if (isMainCanvas(canvasId.value)) {
    e.preventDefault()
    e.dataTransfer.dropEffect = 'copy'
    cyGridster.value.handleDragOver(e)
  }
}

/** 点击画布空白区域时清空当前组件选择 */
const handleMouseDown = e => {
  if (isMainCanvas(canvasId.value)) {
    e.stopPropagation()
    dvMainStore.setClickComponentStatus(false)
    dvMainStore.setInEditorStatus(true)
    dvMainStore.setCurComponent({ component: null, index: null })
  }
}

/** 立即触发栅格画布初始化 */
const canvasInitImmediately = () => {
  cyGridster.value.canvasInit()
}

/** 初始化画布尺寸和栅格布局，首次加载时记录渲染快照 */
const canvasInit = (isFistLoad = true) => {
  if (canvasActive.value || canvasPosition.value === 'tab') {
    renderState.value = true
    setTimeout(function () {
      if (canvasOut.value) {
        dashboardCanvasSizeInit()
        nextTick(() => {
          cyGridster.value.canvasInit() // 在适当的时候初始化布局组件。
          cyGridster.value.afterInitOk(function () {
            renderState.value = false
          })
        })
      }
      // 初始化完成后标记数据准备状态
      dvMainStore.setDataPrepareState(true)
      if (isMainCanvas(canvasId.value) && isFistLoad) {
        snapshotStore.recordSnapshotCache('renderChart')
      }
    }, 500)
  }
}

/** 重新计算画布尺寸并刷新栅格布局 */
const canvasSizeInit = () => {
  nextTick(() => {
    if (canvasOut.value) {
      // div 容器获取 tableBox.value.clientWidth
      dashboardCanvasSizeInit()
      nextTick(() => {
        cyGridster.value.canvasSizeInit() // 在适当的时候初始化布局组件。
        // 缩放比例变化
        scaleInit()
      })
    }
  })
}

/** 根据画布容器与设计尺寸计算缩放比例 */
const scaleInit = () => {
  nextTick(() => {
    if (canvasOut.value) {
      // div 容器获取 tableBox.value.clientWidth
      let canvasWidth = canvasOut.value.clientWidth
      let canvasHeight = canvasOut.value.clientHeight
      scaleWidth.value = Math.floor((canvasWidth * 100) / canvasStyleData.value.width)
      scaleHeight.value = Math.floor((canvasHeight * 100) / canvasStyleData.value.height)
      scaleMin.value = Math.min(scaleWidth.value, scaleHeight.value)
      if (isDashboard() && isMainCanvas(canvasId.value)) {
        const offset = mobileInPc.value ? 4 : 1
        dvMainStore.setCanvasStyleScale(scaleMin.value * offset)
      }
    }
  })
}

/** 计算仪表板矩阵单元尺寸，并同步到主画布仓库 */
const dashboardCanvasSizeInit = () => {
  // div 容器获取 tableBox.value.clientWidth
  state.screenWidth = canvasOut.value.clientWidth - 4
  state.screenHeight = canvasOut.value.clientHeight
  baseWidth.value = state.screenWidth / pcMatrixCount.value.x
  baseHeight.value = state.screenHeight / pcMatrixCount.value.y
  baseMarginLeft.value = 0
  baseMarginTop.value = 0
  canvasInitStatus.value = true
  if (isMainCanvas(canvasId.value)) {
    dvMainStore.setBashMatrixInfo({
      baseWidth: baseWidth.value,
      baseHeight: baseHeight.value,
      baseMarginLeft: baseMarginLeft.value,
      baseMarginTop: baseMarginTop.value
    })
  }
}
/** 将组件盒子加入当前栅格实例 */
const addItemBox = component => {
  cyGridster.value.addItemBox(component)
}

/** 将 Tabs 内组件移出到当前画布并重新初始化布局 */
const moveOutFromTab = component => {
  setTimeout(() => {
    component.canvasId = canvasId.value
    dvMainStore.addComponent({
      component,
      index: undefined,
      isFromGroup: true
    })
    addItemBox(component)
    canvasInit()
  }, 500)
}

/** 挂载后注册窗口、尺寸和画布事件监听 */
onMounted(() => {
  window.addEventListener('resize', canvasSizeInit)
  const erd = elementResizeDetectorMaker()
  erd.listenTo(document.getElementById(domId.value), () => {
    canvasSizeInit()
  })
  canvasInit()
  if (isMainCanvas(canvasId.value)) {
    eventBus.on('handleNew', handleNewFromCanvasMain)
    eventBus.on('event-canvas-size-init', canvasSizeInit)
  }
  eventBus.on('moveOutFromTab-' + canvasId.value, moveOutFromTab)
  eventBus.on('matrix-canvasInit', canvasInit)
})

/** 卸载前移除画布事件监听，避免重复触发 */
onBeforeUnmount(() => {
  if (isMainCanvas(canvasId.value)) {
    eventBus.off('handleNew', handleNewFromCanvasMain)
    eventBus.off('event-canvas-size-init', canvasSizeInit)
  }
  eventBus.off('moveOutFromTab-' + canvasId.value, moveOutFromTab)
  eventBus.off('matrix-canvasInit', canvasInit)
})

/** 返回当前仪表板矩阵单元尺寸 */
const getBaseMatrixSize = () => {
  return {
    baseWidth: baseWidth.value,
    baseHeight: baseHeight.value
  }
}

/** 滚动画布到指定矩阵行，并同步刷新水印 */
const scrollTo = (y = 1) => {
  setTimeout(() => {
    canvasInner.value.scrollTo({
      top: (y - 1) * baseHeight.value,
      behavior: 'smooth'
    })
    cyGridster.value?.watermarkUpdate()
  })
}

/** 记录主画布滚动位置，供其他面板保持同步 */
const scrollCanvas = () => {
  if (isMainCanvas(canvasId.value)) {
    dvMainStore.mainScrollTop = canvasInner.value.scrollTop
  }
}

/** 画布重新激活时刷新尺寸，保证 Tabs 场景尺寸正确 */
watch(
  () => canvasActive.value,
  () => {
    if (canvasActive.value) {
      canvasSizeInit()
    }
  }
)

defineExpose({
  addItemBox,
  canvasInit,
  canvasInitImmediately,
  getBaseMatrixSize
})
</script>

<template>
  <div ref="canvasOut" :id="editDomId" class="content" :class="{ 'render-active': renderState }">
    <canvas-opt-bar
      :canvas-style-data="canvasStyleData"
      :component-data="componentData"
      :canvas-id="canvasId"
    ></canvas-opt-bar>
    <div
      :id="domId"
      ref="canvasInner"
      class="db-canvas"
      :class="{ 'db-canvas-dashboard': !isDashboard() }"
      :style="editStyle"
      @drop="handleDrop"
      @dragover="handleDragOver"
      @mousedown="handleMouseDown"
      @scroll="scrollCanvas"
    >
      <canvas-core
        ref="cyGridster"
        v-if="canvasInitStatus"
        :component-data="componentData"
        :canvas-style-data="canvasStyleData"
        :canvas-view-info="canvasViewInfo"
        :canvas-id="canvasId"
        :base-margin-left="baseMarginLeft"
        :base-margin-top="baseMarginTop"
        :base-width="baseWidth"
        :base-height="baseHeight"
        :font-family="fontFamily"
        @scrollCanvasAdjust="scrollTo"
      ></canvas-core>
    </div>
  </div>
</template>

<style lang="less" scoped>
.db-canvas-dashboard {
  padding: 0 !important;
}
.content {
  width: 100%;
  height: 100%;
  .db-canvas {
    padding: 2px;
    background-size: 100% 100% !important;
    overflow-y: auto;
    width: 100%;
    height: 100%;
  }
  ::-webkit-scrollbar {
    display: none;
  }
}

.render-active {
  opacity: 1;
}
</style>
