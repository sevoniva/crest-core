<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { findComponentAttr } from '../../utils/components'
import DvSidebar from '../../components/visualization/DvSidebar.vue'
import router from '@/router'
import { useAppStoreWithOut } from '@/store/modules/app'
import DbToolbar from '@/components/dashboard/DbToolbar.vue'
import ViewEditor from '@/views/chart/components/editor/index.vue'
import { datasetTree } from '@/api/dataset'
import { Tree } from '@/views/visualized/data/dataset/form/CreatDsGroup.vue'
import DbCanvasAttr from '@/components/dashboard/DbCanvasAttr.vue'
import {
  consumeUploadedTemplatePayload,
  decompressionPre,
  initCanvasData,
  onInitReady,
  uploadedTemplatePre
} from '@/utils/canvasUtils'
import ChartStyleBatchSet from '@/views/chart/components/editor/editor-style/ChartStyleBatchSet.vue'
import CanvasRenderer from '@/views/canvas/CanvasRenderer.vue'
import { check, compareStorage } from '@/utils/CrossPermission'
import { useCache } from '@/hooks/web/useCache'
import { useEmbedded } from '@/store/modules/embedded'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { interactiveStoreWithOut } from '@/store/modules/interactive'
import { watermarkFind } from '@/api/watermark'
import { Base64 } from 'js-base64'
import CanvasCacheDialog from '@/components/visualization/CanvasCacheDialog.vue'
import { deepCopy } from '@/utils/utils'
const interactiveStore = interactiveStoreWithOut()
import { useRequestStoreWithOut } from '@/store/modules/request'
import { usePermissionStoreWithOut } from '@/store/modules/permission'
import eventBus from '@/utils/eventBus'
import { useI18n } from '@/hooks/web/useI18n'
import { useEmitt } from '@/hooks/web/useEmitt'
import DashboardHiddenComponent from '@/components/dashboard/DashboardHiddenComponent.vue'
import { recoverToPublished } from '@/api/visualization/dataVisualization'
import { contextmenuStoreWithOut } from '@/store/modules/data-visualization/contextmenu'
import { ElMessage } from 'element-plus-secondary'
/** 交互仓库负责写入嵌入式访问校验所需的业务上下文 */
const contextmenuStore = contextmenuStoreWithOut()
/** 嵌入式仓库承接外部容器传入的资源、模板和操作参数 */
const embeddedStore = useEmbedded()
/** 本地缓存封装用于读取画布缓存、权限缓存和模板暂存数据 */
const { wsCache } = useCache()
/** 画布缓存弹窗实例，用于询问是否恢复未保存编辑内容 */
const canvasCacheOutRef = ref(null)
/** 画布渲染器实例，供隐藏组件恢复和重新初始化画布时调用 */
const canvasRendererRef = ref(null)
/** 监听跨标签页权限缓存变化，并在当前资源仍可编辑时触发权限校验 */
const eventCheck = e => {
  if (e.key === 'panel-weight' && !compareStorage(e.oldValue, e.newValue)) {
    const resourceId = embeddedStore.resourceId || router.currentRoute.value.query.resourceId
    const opt = embeddedStore.opt || router.currentRoute.value.query.opt
    if (!(opt && opt === 'create')) {
      check(wsCache.get('panel-weight'), resourceId as string, 4)
    }
  }
}
/** 仪表板主画布仓库，集中维护组件、画布和编辑模式状态 */
const dvMainStore = dvMainStoreWithOut()
/** 快照仓库负责记录编辑过程中的撤销、恢复和缓存节点 */
const snapshotStore = snapshotStoreWithOut()
/** 请求仓库提供页面级加载状态，避免权限和数据初始化时重复操作 */
const requestStore = useRequestStoreWithOut()
/** 权限仓库提供当前路由标识，用于匹配加载状态和操作权限 */
const permissionStore = usePermissionStoreWithOut()
/** 从画布仓库拆出的响应式编辑状态，供模板和属性面板联动使用 */
const {
  fullscreenFlag,
  componentData,
  curComponent,
  canvasStyleData,
  canvasViewInfo,
  editMode,
  batchOptStatus,
  hiddenListStatus,
  lastHiddenComponent,
  dvInfo
} = storeToRefs(dvMainStore)
/** 画布数据是否已经完成初始化，控制渲染器挂载时机 */
const dataInitState = ref(false)
/** 应用仓库提供产品形态标识，用于适配嵌入式尺寸 */
const appStore = useAppStoreWithOut()
/** 当前是否运行在嵌入式容器内 */
const isCrestBi = computed(() => appStore.getIsCrestBi)
/** 国际化翻译函数，统一页面标题和提示文案 */
const { t } = useI18n()

/** 仪表板编辑页的本地状态，承载数据集树、资源编号和操作类型 */
const state = reactive({
  datasetTree: [],
  sourcePid: null,
  canvasId: 'canvas-main',
  opt: null,
  resourceId: null
})

/** 加载数据集分组树，供右侧图表编辑器选择数据来源 */
const initDataset = () => {
  datasetTree({}).then(res => {
    state.datasetTree = (res as unknown as Tree[]) || []
  })
}

/** 是否展示非图表类组件属性面板 */
const otherEditorShow = computed(() => {
  return Boolean(
    curComponent.value &&
      (!['UserView', 'VQuery'].includes(curComponent.value?.component) ||
        (curComponent.value?.component === 'UserView' &&
          curComponent.value?.innerType === 'picture-group')) &&
      !batchOptStatus.value &&
      !hiddenListStatus.value
  )
})

/** 非图表类组件属性面板标题，图片组使用通用属性文案 */
const otherEditorTitle = computed(() => {
  return curComponent.value?.component === 'UserView'
    ? t('visualization.attribute')
    : curComponent.value?.label || t('visualization.attribute')
})

/** 是否展示图表或查询组件的视图编辑器 */
const viewEditorShow = computed(() => {
  return Boolean(
    curComponent.value &&
      ['UserView', 'VQuery'].includes(curComponent.value.component) &&
      curComponent.value.innerType !== 'picture-group' &&
      !batchOptStatus.value &&
      !hiddenListStatus.value
  )
})
/** 校验当前资源的编辑权限，嵌入式场景会先写入交互上下文 */
const checkPer = async resourceId => {
  if (!window.CrestBi || !resourceId) {
    return true
  }
  const request = { busiFlag: 'dashboard', resourceTable: 'core' }
  await interactiveStore.setInteractive(request)
  return check(wsCache.get('panel-weight'), resourceId, 4)
}

/** 页面基础初始化完成标记，避免布局在关键参数解析前提前渲染 */
const loadFinish = ref(false)
/** 标记是否从独立窗口地址进入仪表板编辑页 */
const newWindowFromDiv = ref(false)

/** 根据用户选择恢复本地画布缓存，或丢弃缓存并重新加载服务端数据 */
const doUseCache = flag => {
  const canvasCache = wsCache.get('CREST-DV-CACHE-' + state.resourceId)
  if (flag && canvasCache) {
    const canvasCacheSeries = deepCopy(canvasCache)
    snapshotStore.snapshotPublish(canvasCacheSeries)
    dataInitState.value = true
    setTimeout(() => {
      snapshotStore.recordSnapshotCache('doUseCache')
      // 使用缓存时，初始化的保存按钮为激活状态
      snapshotStore.recordSnapshotCache('renderChart')
    }, 1500)
  } else {
    initLocalCanvasData()
    wsCache.delete('CREST-DV-CACHE-' + state.resourceId)
  }
}

/** 按当前资源和操作类型初始化本地画布数据，并在复制场景补齐来源信息 */
const initLocalCanvasData = (callBack?) => {
  const { resourceId, opt, sourcePid } = state
  const busiFlag = opt === 'copy' ? 'dashboard-copy' : 'dashboard'
  initCanvasData(
    resourceId,
    { busiFlag, resourceTable: 'snapshot', source: 'main-edit' },
    function () {
      dataInitState.value = true
      if (dvInfo.value && opt === 'copy') {
        dvInfo.value.dataState = 'prepare'
        dvInfo.value.optType = 'copy'
        dvInfo.value.pid = sourcePid
        setTimeout(() => {
          snapshotStore.recordSnapshotCache('initLocalCanvasData')
        }, 1500)
      }
      onInitReady({ resourceId: resourceId })
      callBack && callBack()
    }
  )
}
/** 页面挂载时解析路由和嵌入参数，完成权限、缓存、模板和画布数据初始化 */
onMounted(async () => {
  document.body.style.overflow = 'hidden'
  dvMainStore.setCurComponent({ component: null, index: null })
  dvMainStore.setHiddenListStatus(false)
  snapshotStore.initSnapShot()
  contextmenuStore.hideContextMenu()
  if (window.location.hash.includes('#/dashboard')) {
    newWindowFromDiv.value = true
  }
  loadFinish.value = true
  window.addEventListener('storage', eventCheck)
  window.addEventListener('message', winMsgHandle)
  const resourceId = embeddedStore.resourceId || router.currentRoute.value.query.resourceId
  const pid = embeddedStore.pid || router.currentRoute.value.query.pid
  const opt = embeddedStore.opt || router.currentRoute.value.query.opt
  const createType = embeddedStore.createType || router.currentRoute.value.query.createType
  const templateParams =
    embeddedStore.templateParams || router.currentRoute.value.query.templateParams
  const templatePayloadKey =
    embeddedStore.templateParams ||
    router.currentRoute.value.query.templatePayloadKey ||
    router.currentRoute.value.query.templateParams
  const checkResourceId = opt && opt === 'copy' ? null : resourceId
  const checkResult = await checkPer(checkResourceId as string)
  if (!checkResult) {
    return
  }
  initDataset()

  state.sourcePid = pid
  state.opt = opt
  state.resourceId = resourceId
  if (resourceId) {
    dataInitState.value = false
    const canvasCache = wsCache.get('CREST-DV-CACHE-' + resourceId)
    if (canvasCache) {
      canvasCacheOutRef.value?.dialogInit({ canvasType: 'dashboard', resourceId: resourceId })
    } else {
      initLocalCanvasData(() => {
        // 初始化完成后的回调占位
      })
    }
  } else if (opt && opt === 'create') {
    dataInitState.value = false
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
        window.open('#/panel/index', '_self')
        return
      }
      uploadedTemplatePre(uploadedTemplatePayload, result => {
        templatePayload = result
        preName = templatePayload.baseInfo?.preName
      })
    }
    nextTick(() => {
      dvMainStore.createInit('dashboard', null, pid, watermarkBaseInfo, preName)
      // 从模板新建
      if (createType === 'template' || createType === 'uploadedTemplate') {
        wsCache.delete('crest-template-data')
        dvMainStore.setComponentData(templatePayload['componentData'])
        dvMainStore.setCanvasStyle(templatePayload['canvasStyleData'])
        dvMainStore.setCanvasViewInfo(templatePayload['canvasViewInfo'])
        dvMainStore.setAppDataInfo(templatePayload['appData'])
        setTimeout(() => {
          snapshotStore.recordSnapshotCache('template')
        }, 1500)
        if (dvMainStore.getAppDataInfo()) {
          eventBus.emit('save')
        }
      }
      dataInitState.value = true
      // 新建仪表板默认使用浅色标题
      canvasStyleData.value.component.chartTitle.color = '#000000'
    })
  } else {
    let url = '#/panel/index'
    window.open(url, '_self')
  }
})

/** 处理外部窗口消息，仅接收目标资源与当前仪表板一致的参数更新 */
const winMsgHandle = event => {
  const msgInfo = event.data
  if (msgInfo?.targetSourceId === dvInfo.value.id + '')
    if (msgInfo.type === 'webParams') {
      // 网络消息处理
      winMsgWebParamsHandle(msgInfo)
    }
}

/** 将外部传入的网页参数写入画布过滤器 */
const winMsgWebParamsHandle = msgInfo => {
  const params = msgInfo.params
  dvMainStore.addWebParamsFilter(params)
}

/** 过滤被隐藏的组件，确保画布只渲染当前可见组件 */
const dashboardComponentData = computed(() =>
  componentData.value.filter(item => !item.dashboardHidden)
)

/** 从隐藏列表恢复组件，并在必要时清空纵向坐标重新参与布局 */
const cancelHidden = item => {
  if (canvasRendererRef.value) {
    if (!(lastHiddenComponent.value?.length && lastHiddenComponent.value.includes(item.id))) {
      item.y = undefined
    }
    canvasRendererRef.value.addItemBox(item)
    nextTick(() => {
      canvasRendererRef.value.canvasInit(false)
    })
    snapshotStore.recordSnapshotCache('cancelHidden')
  }
}

/** 恢复到已发布版本后重新加载画布，并触发全量数据重算 */
const doRecoverToPublished = () => {
  recoverToPublished({ id: dvInfo.value.id, type: 'dashboard', name: dvInfo.value.name }).then(
    () => {
      state.resourceId = dvInfo.value.id
      state.sourcePid = dvInfo.value.pid
      state.opt = null
      initLocalCanvasData(() => {
        nextTick(() => {
          canvasRendererRef.value.canvasInit(false)
          dvMainStore.updateDvInfoCall(1)
          useEmitt().emitter.emit('calcData-all')
        })
      })
    }
  )
}

/** 页面卸载时恢复全局滚动并移除跨窗口事件监听 */
onUnmounted(() => {
  document.body.style.overflow = ''
  window.removeEventListener('storage', eventCheck)
  window.removeEventListener('message', winMsgHandle)
})
</script>

<template>
  <div
    class="dv-common-layout dv-teleport-query"
    :class="isCrestBi && !newWindowFromDiv && 'crest-w-h'"
    v-loading="requestStore.loadingMap[permissionStore.currentPath]"
    v-if="loadFinish"
  >
    <DbToolbar @recoverToPublished="doRecoverToPublished" />
    <el-container
      class="dv-layout-container"
      :class="{ 'preview-content': editMode === 'preview' }"
      element-loading-background="rgba(0, 0, 0, 0)"
    >
      <!-- 中间画布 -->
      <main class="center" :class="{ 'screen-full': fullscreenFlag }">
        <CanvasRenderer
          style="overflow-x: hidden"
          v-if="dataInitState"
          ref="canvasRendererRef"
          :canvas-id="state.canvasId"
          :component-data="dashboardComponentData"
          :canvas-style-data="canvasStyleData"
          :canvas-view-info="canvasViewInfo"
          :font-family="canvasStyleData.fontFamily"
        ></CanvasRenderer>
      </main>
      <!-- 右侧侧组件列表 -->
      <dv-sidebar
        v-if="otherEditorShow"
        :theme-info="'light'"
        :title="otherEditorTitle"
        :width="420"
        :side-name="'componentProp'"
        :aside-position="'right'"
        :view="canvasViewInfo[curComponent.id]"
        :element="curComponent"
        class="left-sidebar"
      >
        <component :is="findComponentAttr(curComponent)" :themes="'light'" />
      </dv-sidebar>
      <dv-sidebar
        v-show="!curComponent && !batchOptStatus && !hiddenListStatus"
        :theme-info="'light'"
        :title="t('visualization.dashboard_configuration')"
        :width="420"
        aside-position="right"
        side-name="canvas"
        class="left-sidebar"
      >
        <DbCanvasAttr></DbCanvasAttr>
      </dv-sidebar>
      <div v-show="viewEditorShow" style="height: 100%">
        <view-editor
          :themes="'light'"
          :view="canvasViewInfo[curComponent ? curComponent.id : 'default']"
          :dataset-tree="state.datasetTree"
        ></view-editor>
      </div>
      <dv-sidebar
        v-if="batchOptStatus"
        :theme-info="'light'"
        :title="t('visualization.batch_style_set')"
        :width="280"
        aside-position="right"
        class="left-sidebar"
        :side-name="'batchOpt'"
      >
        <chart-style-batch-set></chart-style-batch-set>
      </dv-sidebar>
      <dv-sidebar
        v-if="hiddenListStatus"
        :theme-info="'light'"
        :title="t('visualization.hidden_components')"
        :width="280"
        aside-position="right"
        class="left-sidebar"
      >
        <DashboardHiddenComponent @cancel-hidden="cancelHidden"></DashboardHiddenComponent>
      </dv-sidebar>
    </el-container>
  </div>
  <canvas-cache-dialog ref="canvasCacheOutRef" @doUseCache="doUseCache"></canvas-cache-dialog>
</template>

<style lang="less">
.dv-common-layout {
  height: 100vh;
  width: 100vw;

  .dv-layout-container {
    height: calc(100vh - @top-bar-height);
    .left-sidebar {
      height: 100%;
    }
    .center {
      display: flex;
      flex-direction: column;
      height: 100%;
      flex: 1;
      position: relative;
      overflow: auto;
      .content {
        flex: 1;
        width: 100%;
        .db-canvas {
          padding: 2px;
          background-size: 100% 100% !important;
          overflow-y: auto;
          width: 100%;
          height: 100%;
        }
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
  border: 0px !important;
  width: 0px !important;
  overflow: hidden;
  padding: 0px;
}

.preview-content {
  :deep(.editor-light) {
    border: 0 !important;
  }
}
</style>
