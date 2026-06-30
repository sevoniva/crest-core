<script setup lang="ts">
import dvRuler from '@/assets/svg/dv-ruler.svg'
import CanvasAttr from '@/components/data-visualization/CanvasAttr.vue'
import { computed, watch, onMounted, reactive, ref, nextTick, onUnmounted } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { contextmenuStoreWithOut } from '@/store/modules/data-visualization/contextmenu'
import { composeStoreWithOut } from '@/store/modules/data-visualization/compose'
import { useAppStoreWithOut } from '@/store/modules/app'
import { storeToRefs } from 'pinia'
import DvToolbar from '../../components/data-visualization/DvToolbar.vue'
import ComponentToolBar from '../../components/data-visualization/ComponentToolBar.vue'
import eventBus from '../../utils/eventBus'
import { findComponentAttr } from '../../utils/components'
import DvSidebar from '../../components/visualization/DvSidebar.vue'
import router from '@/router'
import Editor from '@/views/chart/components/editor/index.vue'
import { guid } from '@/views/visualized/data/dataset/form/util.js'
import { datasetTree } from '@/api/dataset'
import { Tree } from '@/views/visualized/data/dataset/form/CreatDsGroup.vue'
import {
  consumeUploadedTemplatePayload,
  decompressionPre,
  findDragComponent,
  findNewComponent,
  initCanvasData,
  onInitReady,
  uploadedTemplatePre
} from '@/utils/canvasUtils'
import CanvasCore from '@/components/data-visualization/canvas/CanvasCore.vue'
import { listenGlobalKeyDown, releaseAttachKey } from '@/utils/shortcutKey'
import { adaptCurThemeCommonStyle } from '@/utils/canvasStyle'
import { useEmbedded } from '@/store/modules/embedded'
import { changeComponentSizeWithScale } from '@/utils/changeComponentsSizeWithScale'
import { useEmitt } from '@/hooks/web/useEmitt'
import { check, compareStorage } from '@/utils/CrossPermission'
import { useCache } from '@/hooks/web/useCache'
import RealTimeListTree from '@/components/data-visualization/RealTimeListTree.vue'
import { interactiveStoreWithOut } from '@/store/modules/interactive'
import { watermarkFind } from '@/api/watermark'
import { Base64 } from 'js-base64'
import CanvasCacheDialog from '@/components/visualization/CanvasCacheDialog.vue'
import { deepCopy } from '@/utils/utils'
import DvPreview from '@/views/data-visualization/DvPreview.vue'
import Ruler from '@/custom-component/common/Ruler.vue'
import { useRequestStoreWithOut } from '@/store/modules/request'
import { usePermissionStoreWithOut } from '@/store/modules/permission'
import ChartStyleBatchSet from '@/views/chart/components/editor/editor-style/ChartStyleBatchSet.vue'
import CustomTabsSort from '@/custom-component/tabs/CustomTabsSort.vue'
import { useI18n } from '@/hooks/web/useI18n'
import { recoverToPublished } from '@/api/visualization/dataVisualization'
import { ElMessage } from 'element-plus-secondary'
const interactiveStore = interactiveStoreWithOut()
const embeddedStore = useEmbedded()
const { wsCache } = useCache()
/**
 * 全屏预览组件引用
 */
const dvPreviewRef = ref(null)
const { t } = useI18n()
/**
 * 监听跨窗口权限缓存变化并触发编辑权限校验
 */
const eventCheck = e => {
  if (e.key === 'screen-weight' && !compareStorage(e.oldValue, e.newValue)) {
    const opt = embeddedStore.opt || router.currentRoute.value.query.opt
    if (!(opt && opt === 'create')) {
      check(
        wsCache.get('screen-weight'),
        embeddedStore.dvId || (router.currentRoute.value.query.dvId as string),
        4
      )
    }
  }
}
/**
 * 主画布核心组件引用
 */
const mainCanvasCoreRef = ref(null)
/**
 * 标签页排序弹窗引用
 */
const customTabsSortRef = ref(null)
const appStore = useAppStoreWithOut()
/**
 * 是否处于嵌入式分析环境
 */
const isCrestBi = computed(() => appStore.getIsCrestBi)
const dvMainStore = dvMainStoreWithOut()
const snapshotStore = snapshotStoreWithOut()
const contextmenuStore = contextmenuStoreWithOut()
const composeStore = composeStoreWithOut()
/**
 * 本地缓存恢复弹窗引用
 */
const canvasCacheOutRef = ref(null)
/**
 * 横向标尺组件引用
 */
const widthRulerRef = ref(null)
/**
 * 纵向标尺组件引用
 */
const heightRulerRef = ref(null)
const requestStore = useRequestStoreWithOut()
const permissionStore = usePermissionStoreWithOut()
const {
  fullscreenFlag,
  componentData,
  curComponent,
  isClickComponent,
  canvasStyleData,
  canvasViewInfo,
  editMode,
  dvInfo,
  canvasState,
  batchOptStatus
} = storeToRefs(dvMainStore)
const { editorMap, isSpaceDown } = storeToRefs(composeStore)
/**
 * 画布滚动容器引用
 */
const canvasOut = ref(null)
/**
 * 画布内容容器引用
 */
const canvasInner = ref(null)
/**
 * 右侧属性面板外层引用
 */
const leftSidebarRef = ref(null)
/**
 * 整体编辑布局引用
 */
const dvLayout = ref(null)
/**
 * 画布中间区域引用
 */
const canvasCenterRef = ref(null)
/**
 * 纵向标尺可用高度
 */
const mainHeight = ref(300)
let createType = null
let isDragging = false // 标记是否在拖动
let startX, startY, scrollLeft, scrollTop
/**
 * 大屏编辑页的本地运行状态
 */
const state = reactive({
  sideShow: true,
  countTime: 0,
  datasetTree: [],
  scaleHistory: null,
  canvasId: 'canvas-main',
  canvasInitStatus: false,
  sourcePid: null,
  resourceId: null,
  opt: null,
  baseWidth: 10,
  baseHeight: 10
})

/**
 * 打开当前标签页组件的排序配置
 */
const tabSort = () => {
  if (curComponent.value) {
    customTabsSortRef.value.sortInit(curComponent.value)
  }
}

// 启用拖动
const enableDragging = e => {
  if (isSpaceDown.value) {
    // 仅在空格键按下时启用拖动
    isDragging = true
    startX = e.pageX - canvasOut.value.wrapRef.offsetLeft
    startY = e.pageY - canvasOut.value.wrapRef.offsetTop
    scrollLeft = canvasOut.value.wrapRef.scrollLeft
    scrollTop = canvasOut.value.wrapRef.scrollTop
    e.preventDefault()
    e.stopPropagation()
  }
}

// 执行拖动
const onMouseMove = e => {
  if (!isDragging) return
  e.preventDefault()
  e.stopPropagation()
  const x = e.pageX - canvasOut.value.wrapRef.offsetLeft
  const y = e.pageY - canvasOut.value.wrapRef.offsetTop
  const walkX = x - startX
  const walkY = y - startY
  canvasOut.value.wrapRef.scrollLeft = scrollLeft - walkX
  canvasOut.value.wrapRef.scrollTop = scrollTop - walkY
}

// 禁用拖动
const disableDragging = () => {
  isDragging = false
}

/**
 * 根据编辑模式计算画布内容区尺寸
 */
const contentStyle = computed(() => {
  const { width, height } = canvasStyleData.value
  if (editMode.value === 'preview') {
    return {
      width: '100%',
      height: 'auto',
      overflow: 'hidden'
    }
  } else {
    return {
      minWidth: '1600px',
      width: width * scrollOffset.value + 'px',
      height: height * scrollOffset.value + 'px'
    }
  }
})

// 通过实时监听的方式直接添加组件
const handleNew = newComponentInfo => {
  state.countTime++
  const { componentName, innerType, staticMap } = newComponentInfo
  if (componentName) {
    const { width, height, scale } = canvasStyleData.value
    const component = findNewComponent(componentName, innerType, staticMap)
    component.style.top = ((height - component.style.height) * scale) / 200
    component.style.left = ((width - component.style.width) * scale) / 200
    component.id = guid()
    const popComponents = componentData.value.filter(
      ele => ele.category && ele.category === 'hidden'
    )
    // 弹框区域组件 只允许有一个过滤组件
    if (
      canvasState.value.curPointArea === 'hidden' &&
      component.component === 'VQuery' &&
      (!popComponents || popComponents.length === 0)
    ) {
      component.category = canvasState.value.curPointArea
      component.commonBackground.backgroundColor = 'rgba(41, 41, 41, 1)'
    }
    changeComponentSizeWithScale(component)
    dvMainStore.addComponent({ component: component, index: undefined })
    adaptCurThemeCommonStyle(component)
    snapshotStore.recordSnapshotCache('renderChart', component.id)
    if (state.countTime > 10) {
      state.sideShow = false
      nextTick(() => {
        state.countTime = 0
        state.sideShow = true
      })
    }
    useEmitt().emitter.emit('initScroll')
  }
}

/**
 * 处理从组件栏拖入画布后的落点和组件创建
 */
const handleDrop = e => {
  e.preventDefault()
  e.stopPropagation()
  const componentInfo = e.dataTransfer.getData('id')
  const rectInfo = editorMap.value[state.canvasId].getBoundingClientRect()
  if (componentInfo) {
    const component = findDragComponent(componentInfo)
    component.style.top = e.clientY - rectInfo.y
    component.style.left = e.clientX - rectInfo.x
    component.id = guid()
    changeComponentSizeWithScale(component)
    dvMainStore.addComponent({ component: component, index: undefined })
    adaptCurThemeCommonStyle(component)
    snapshotStore.recordSnapshotCache('renderChart', component.id)
  }
}

/**
 * 允许组件以复制方式拖入画布
 */
const handleDragOver = e => {
  e.preventDefault()
  e.dataTransfer.dropEffect = 'copy'
}

/**
 * 处理画布空白区域鼠标按下并准备框选或拖拽
 */
const handleMouseDown = e => {
  // e.stopPropagation()
  if (isSpaceDown.value) {
    return
  }
  dvMainStore.setClickComponentStatus(false)
  // 点击画布的空区域 提前清空curComponent 防止右击菜单内容抖动
  dvMainStore.setCurComponent({ component: null, index: null })
  dvMainStore.setInEditorStatus(true)
  mainCanvasCoreRef.value.handleMouseDown(e)
}

/**
 * 点击画布空白处时取消当前组件选择并关闭右键菜单
 */
const deselectCurComponent = e => {
  if (isSpaceDown.value) {
    return
  }
  if (!isClickComponent.value) {
    curComponent.value && dvMainStore.setCurComponent({ component: null, index: null })
  }

  // 0 左击 1 滚轮 2 右击
  if (e.button != 2) {
    contextmenuStore.hideContextMenu()
  }
}

/**
 * 加载数据集树供图表编辑器选择数据来源
 */
const initDataset = () => {
  datasetTree({}).then(res => {
    state.datasetTree = (res as unknown as Tree[]) || []
  })
}

/**
 * 根据画布缩放比例计算编辑区滚动留白倍数
 */
const scrollOffset = computed(() => (canvasStyleData.value.scale < 150 ? 1.5 : 2))

// 全局监听按键事件
listenGlobalKeyDown()

/**
 * 将画布滚动条初始化到内容中心位置
 */
const initScroll = () => {
  nextTick(() => {
    if (canvasCenterRef.value) {
      const { width, height } = canvasStyleData.value
      const mainWidth = canvasCenterRef.value.clientWidth
      mainHeight.value = canvasCenterRef.value.clientHeight
      const scrollX = (scrollOffset.value * width - mainWidth) / 2
      const scrollY = (scrollOffset.value * height - mainHeight.value) / 2 + 20
      // 设置画布初始滚动条位置
      canvasOut.value.scrollTo(scrollX, scrollY)
    }
  })
}
/**
 * 根据用户选择恢复本地缓存或重新加载远端画布
 */
const doUseCache = flag => {
  const canvasCache = wsCache.get('CREST-DV-CACHE-' + state.resourceId)
  if (flag && canvasCache) {
    const canvasCacheSeries = deepCopy(canvasCache)
    snapshotStore.snapshotPublish(canvasCacheSeries)
    state.canvasInitStatus = true
    nextTick(() => {
      dvMainStore.setDataPrepareState(true)
      snapshotStore.recordSnapshotCache('renderChart')
      setTimeout(() => {
        // 使用缓存时，初始化的保存按钮为激活状态
        snapshotStore.recordSnapshotCache('renderChart')
      }, 2000)
    })
  } else {
    initLocalCanvasData(() => {
      // do init
    })
    wsCache.delete('CREST-DV-CACHE-' + state.resourceId)
  }
}

/**
 * 初始化当前大屏的本地编辑数据和快照状态
 */
const initLocalCanvasData = async callback => {
  const { opt, sourcePid, resourceId } = state
  const busiFlag = opt === 'copy' ? 'dataV-copy' : 'dataV'
  await initCanvasData(
    resourceId,
    { busiFlag, resourceTable: 'snapshot', source: 'main-edit' },
    function () {
      state.canvasInitStatus = true
      // afterInit
      nextTick(() => {
        dvMainStore.setDataPrepareState(true)
        snapshotStore.recordSnapshotCache('renderChart')
        if (dvInfo.value && opt === 'copy') {
          dvInfo.value.dataState = 'prepare'
          dvInfo.value.optType = 'copy'
          dvInfo.value.pid = sourcePid
          setTimeout(() => {
            snapshotStore.recordSnapshotCache('renderChart')
          }, 1500)
        }
        onInitReady({ resourceId: resourceId })
        callback && callback()
      })
    }
  )
}

/**
 * 进入预览模式时按布局宽度临时调整画布缩放
 */
const previewScaleChange = () => {
  state.scaleHistory = canvasStyleData.value.scale
  nextTick(() => {
    let canvasWidth = dvLayout.value.clientWidth
    const previewScale = (canvasWidth * 100) / canvasStyleData.value.width
    canvasStyleData.value.scale = previewScale
  })
}

// 切换编辑和预览模式时恢复或重算画布缩放
watch(
  () => editMode.value,
  val => {
    if (val === 'edit') {
      if (state.scaleHistory) {
        canvasStyleData.value.scale = state.scaleHistory
      }
      initScroll()
    } else {
      previewScaleChange()
    }
  }
)
/**
 * 校验当前用户是否仍具备大屏编辑权限
 */
const checkPer = async resourceId => {
  if (!window.CrestBi || !resourceId) {
    return true
  }
  const request = { busiFlag: 'dataV', resourceTable: 'core' }
  await interactiveStore.setInteractive(request)
  return check(wsCache.get('screen-weight'), resourceId, 4)
}
// 目标校验： 需要校验targetSourceId 是否是当前可视化资源ID
const winMsgHandle = event => {
  const msgInfo = event.data
  if (msgInfo?.targetSourceId === dvInfo.value.id + '')
    if (msgInfo.type === 'webParams') {
      // 网络消息处理
      winMsgWebParamsHandle(msgInfo)
    }
}

/**
 * 接收嵌入父页面传入的网络参数并写入筛选条件
 */
const winMsgWebParamsHandle = msgInfo => {
  const params = msgInfo.params
  dvMainStore.addWebParamsFilter(params)
}

/**
 * 控制主编辑布局在初始化前不渲染
 */
const loadFinish = ref(false)
/**
 * 标记当前页面是否从大屏编辑窗口独立打开
 */
const newWindowFromDiv = ref(false)
onMounted(async () => {
  document.body.style.overflow = 'hidden'
  dvMainStore.setCurComponent({ component: null, index: null })
  snapshotStore.initSnapShot()
  if (window.location.hash.includes('#/dvCanvas')) {
    newWindowFromDiv.value = true
  }
  loadFinish.value = true
  window.addEventListener('blur', releaseAttachKey)
  window.addEventListener('message', winMsgHandle)
  if (editMode.value === 'edit') {
    window.addEventListener('storage', eventCheck)
  }
  const dvId = embeddedStore.dvId || router.currentRoute.value.query.dvId
  const pid = embeddedStore.pid || router.currentRoute.value.query.pid
  const templateParams =
    embeddedStore.templateParams || router.currentRoute.value.query.templateParams
  const templatePayloadKey =
    embeddedStore.templateParams ||
    router.currentRoute.value.query.templatePayloadKey ||
    router.currentRoute.value.query.templateParams
  createType = embeddedStore.createType || router.currentRoute.value.query.createType
  const opt = embeddedStore.opt || router.currentRoute.value.query.opt
  const checkDvId = opt && opt === 'copy' ? null : dvId
  const checkResult = await checkPer(checkDvId)
  if (!checkResult) {
    return
  }
  initDataset()
  state.resourceId = dvId
  state.sourcePid = pid
  state.opt = opt
  if (dvId) {
    state.canvasInitStatus = false
    const canvasCache = wsCache.get('CREST-DV-CACHE-' + dvId)
    if (canvasCache) {
      canvasCacheOutRef.value?.dialogInit({ canvasType: 'dataV', resourceId: dvId })
    } else {
      await initLocalCanvasData(() => {
        // do init
      })
    }
  } else if (opt && opt === 'create') {
    state.canvasInitStatus = false
    let watermarkBaseInfo
    try {
      await watermarkFind().then(rsp => {
        watermarkBaseInfo = rsp.data
        watermarkBaseInfo.settingContent = JSON.parse(watermarkBaseInfo.settingContent)
      })
    } catch (e) {
      console.error('can not find watermark info')
    }
    let templatePayload
    let preName
    if (createType === 'template') {
      const templateParamsApply = JSON.parse(Base64.decode(decodeURIComponent(templateParams + '')))
      await decompressionPre(templateParamsApply, result => {
        templatePayload = result
        preName = templatePayload.baseInfo?.preName
      })
    } else if (createType === 'uploadedTemplate') {
      const uploadedTemplatePayload = consumeUploadedTemplatePayload(templatePayloadKey)
      if (!uploadedTemplatePayload) {
        ElMessage.error(t('visualization.import_template_expired'))
        window.open('#/screen/index', '_self')
        return
      }
      uploadedTemplatePre(uploadedTemplatePayload, result => {
        templatePayload = result
        preName = templatePayload.baseInfo?.preName
      })
    }
    dvMainStore.createInit('dataV', null, pid, watermarkBaseInfo, preName)
    nextTick(() => {
      state.canvasInitStatus = true
      dvMainStore.setDataPrepareState(true)
      snapshotStore.recordSnapshotCache('renderChart')
      // 从模板新建
      if (createType === 'template' || createType === 'uploadedTemplate') {
        dvMainStore.setComponentData(templatePayload['componentData'])
        dvMainStore.setCanvasStyle(templatePayload['canvasStyleData'])
        dvMainStore.setCanvasViewInfo(templatePayload['canvasViewInfo'])
        dvMainStore.setAppDataInfo(templatePayload['appData'])
        setTimeout(() => {
          snapshotStore.recordSnapshotCache('template')
        }, 1500)
      }
      if (dvMainStore.getAppDataInfo()) {
        eventBus.emit('save')
      }
    })
  } else {
    let url = '#/screen/index'
    window.open(url, '_self')
  }
  initScroll()
  useEmitt({
    name: 'initScroll',
    callback: function () {
      initScroll()
    }
  })
})

onUnmounted(() => {
  document.body.style.overflow = ''
  window.removeEventListener('storage', eventCheck)
  window.removeEventListener('blur', releaseAttachKey)
  eventBus.off('handleNew', handleNew)
  eventBus.off('tabSort', tabSort)
})

/**
 * 判断当前是否处于预览模式
 */
const previewStatus = computed(() => editMode.value === 'preview')

/**
 * 判断是否展示普通组件属性编辑面板
 */
const otherEditorShow = computed(() => {
  return Boolean(
    curComponent.value &&
      (!['UserView', 'GroupArea', 'VQuery'].includes(curComponent.value?.component) ||
        (curComponent.value?.component === 'UserView' &&
          curComponent.value?.innerType === 'picture-group')) &&
      !batchOptStatus.value
  )
})
/**
 * 判断是否展示画布属性编辑面板
 */
const canvasPropertiesShow = computed(
  () => !curComponent.value || ['GroupArea'].includes(curComponent.value.component)
)
/**
 * 判断是否展示图表视图属性编辑器
 */
const viewsPropertiesShow = computed(() => {
  return Boolean(
    curComponent.value &&
      ['UserView', 'VQuery'].includes(curComponent.value.component) &&
      curComponent.value.innerType !== 'picture-group' &&
      !batchOptStatus.value
  )
})

/**
 * 同步画布滚动位置到横向和纵向标尺
 */
const scrollCanvas = e => {
  widthRulerRef.value.rulerScroll(e)
  heightRulerRef.value.rulerScroll(e)
}

/**
 * 主画布区域中可直接渲染的组件列表
 */
const coreComponentData = computed(() =>
  componentData.value.filter(ele => !ele.category || ele.category !== 'hidden')
)

/**
 * 弹框区域中独立渲染的隐藏组件列表
 */
const popComponentData = computed(() =>
  componentData.value.filter(ele => ele.category && ele.category === 'hidden')
)

/**
 * 将当前草稿恢复到已发布版本并刷新画布数据
 */
const doRecoverToPublished = () => {
  recoverToPublished({ id: dvInfo.value.id, type: 'dataV', name: dvInfo.value.name }).then(() => {
    state.resourceId = dvInfo.value.id
    state.sourcePid = dvInfo.value.pid
    state.opt = null
    initLocalCanvasData(() => {
      dvMainStore.updateDvInfoCall(1)
      useEmitt().emitter.emit('calcData-all')
    })
  })
}

eventBus.on('handleNew', handleNew)

eventBus.on('tabSort', tabSort)
</script>

<template>
  <div
    ref="dvLayout"
    class="dv-common-layout"
    :class="isCrestBi && !newWindowFromDiv && 'crest-w-h'"
  >
    <DvToolbar @recover-to-published="doRecoverToPublished" />
    <div class="custom-dv-divider" />
    <el-container
      v-if="loadFinish"
      v-loading="requestStore.loadingMap && requestStore.loadingMap[permissionStore.currentPath]"
      element-loading-background="rgba(0, 0, 0, 0)"
      class="dv-layout-container"
      :class="{ 'preview-layout-container': previewStatus }"
    >
      <!-- 左侧组件列表 -->
      <dv-sidebar
        :title="t('visualization.layer_management')"
        :width="180"
        :scroll-width="3"
        :aside-position="'left'"
        :side-name="'realTimeComponent'"
        class="left-sidebar"
        id="dv-main-left-sidebar"
        :class="{ 'preview-aside': previewStatus }"
      >
        <real-time-list-tree />
      </dv-sidebar>
      <!-- 中间画布 -->
      <main id="dv-main-center" class="center" ref="canvasCenterRef">
        <div class="ruler-icon-outer">
          <el-icon class="ruler-icon">
            <Icon name="dv-ruler"><dvRuler class="svg-icon" /></Icon>
          </el-icon>
        </div>
        <Ruler ref="widthRulerRef" @update:tickSize="val => (state.baseWidth = val)"></Ruler>
        <Ruler
          direction="vertical"
          @update:tickSize="val => (state.baseHeight = val)"
          :size="mainHeight"
          ref="heightRulerRef"
        ></Ruler>
        <el-scrollbar
          ref="canvasOut"
          @scroll="scrollCanvas"
          class="content"
          :class="{ 'preview-content': previewStatus }"
          @mousedown="enableDragging"
          @mouseup="disableDragging"
          @mousemove="onMouseMove"
          @mouseleave="disableDragging"
        >
          <div
            id="canvas-dv-outer"
            ref="canvasInner"
            :style="contentStyle"
            @drop="handleDrop"
            @dragover="handleDragOver"
            @mousedown="handleMouseDown"
            @mouseup="deselectCurComponent"
          >
            <div v-if="isSpaceDown" class="canvas-drag"></div>
            <div class="canvas-dv-inner">
              <canvas-core
                class="canvas-area-shadow editor-main"
                v-if="state.canvasInitStatus"
                ref="mainCanvasCoreRef"
                :component-data="coreComponentData"
                :pop-component-data="popComponentData"
                :canvas-style-data="canvasStyleData"
                :canvas-view-info="canvasViewInfo"
                :canvas-id="state.canvasId"
                :base-height="state.baseHeight"
                :base-width="state.baseWidth"
                :font-family="canvasStyleData.fontFamily"
              >
                <template v-slot:canvasDragTips>
                  <div class="canvas-drag-tip">
                    {{ t('visualization.hold_canvas_tips') }}
                  </div>
                </template>
              </canvas-core>
            </div>
          </div>
        </el-scrollbar>
        <ComponentToolBar :class="{ 'preview-aside-x': previewStatus }"></ComponentToolBar>
      </main>
      <!-- 右侧侧组件列表 -->
      <div style="width: auto; height: 100%" ref="leftSidebarRef">
        <template v-if="!batchOptStatus && state.sideShow">
          <dv-sidebar
            v-if="otherEditorShow"
            :title="curComponent['name']"
            :width="240"
            :side-name="'componentProp'"
            :aside-position="'right'"
            class="left-sidebar"
            :slide-index="2"
            :themes="'dark'"
            :element="curComponent"
            :view="canvasViewInfo[curComponent.id]"
            :class="{ 'preview-aside': editMode === 'preview' }"
          >
            <component :is="findComponentAttr(curComponent)" />
          </dv-sidebar>
          <dv-sidebar
            v-show="canvasPropertiesShow"
            :title="t('visualization.screen_config')"
            :width="240"
            :side-name="'canvas'"
            :aside-position="'right'"
            class="left-sidebar"
            :class="{ 'preview-aside': editMode === 'preview' }"
          >
            <canvas-attr></canvas-attr>
          </dv-sidebar>
          <div
            v-show="viewsPropertiesShow"
            style="height: 100%"
            :class="{ 'preview-aside': editMode === 'preview' }"
          >
            <editor
              :view="canvasViewInfo[curComponent ? curComponent.id : 'default']"
              themes="dark"
              :dataset-tree="state.datasetTree"
            ></editor>
          </div>
        </template>
        <dv-sidebar
          v-if="batchOptStatus"
          :theme-info="'dark'"
          :title="t('visualization.batch_style_set')"
          :width="280"
          aside-position="right"
          class="left-sidebar"
          :side-name="'batchOpt'"
        >
          <chart-style-batch-set themes="dark"></chart-style-batch-set>
        </dv-sidebar>
      </div>
    </el-container>
  </div>
  <canvas-cache-dialog ref="canvasCacheOutRef" @doUseCache="doUseCache"></canvas-cache-dialog>
  <dv-preview
    v-if="fullscreenFlag"
    style="z-index: 10"
    ref="dvPreviewRef"
    show-position="edit-preview"
    :canvas-data-preview="componentData"
    :canvas-style-preview="canvasStyleData"
    :canvas-view-info-preview="canvasViewInfo"
    :dv-info="{ ...dvInfo, status: 1 }"
  ></dv-preview>
  <custom-tabs-sort ref="customTabsSortRef"></custom-tabs-sort>
</template>

<style lang="less">
.preview-layout-container {
}

.preview-content {
  align-items: center;
}

.dv-common-layout {
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  color: @dv-canvas-main-font-color;
  .dv-layout-container {
    height: calc(100vh - @top-bar-height - 1px);
    .left-sidebar {
      height: 100%;
    }
    .center {
      display: flex;
      flex-direction: column;
      height: 100%;
      flex: 1;
      position: relative;
      background-color: rgba(51, 51, 51, 1);
      overflow: auto;
      .content {
        position: relative;
        flex: 1;
        width: 100%;
        overflow: auto;
        margin: auto;
      }
    }
    .right-sidebar {
      height: 100%;
    }
  }
  &.crest-w-h {
    height: 100%;
    width: 100%;
    .dv-layout-container {
      height: calc(100% - @top-bar-height);
    }
  }
}

.preview-aside {
  width: 0px !important;
  overflow: hidden;
  padding: 0px;
}

.preview-aside-x {
  height: 0px !important;
  overflow: hidden;
  padding: 0px;
}

.canvas-area-shadow {
  box-sizing: border-box;
  border: 1px solid rgba(85, 85, 85, 0.4);
}

.custom-dv-divider {
  width: 100%;
  height: 1px;
  background: #000;
}

.canvas-dv-inner {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

.ruler-icon-outer {
  background: #2c2c2c;
  position: absolute;
  width: 30px;
  height: 30px;
  z-index: 3;
  color: #ebebeb;
  .ruler-icon {
    margin-left: 6px;
    margin-top: 6px;
    font-size: 24px;
    color: #ebebeb;
  }
}

.canvas-drag {
  position: absolute;
  z-index: 1;
  opacity: 0.3;
  cursor: pointer;
  width: 100%;
  height: 100%;
}

.canvas-drag-tip {
  position: absolute;
  right: 5px;
  bottom: -20px;
  font-size: 12px;
  color: rgb(169, 175, 184);
}
</style>
