<script setup lang="ts">
import icon_add_outlined from '@/assets/svg/icon_add_outlined.svg'
import CrestResourceTree from '@/views/common/CrestResourceTree.vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import ArrowSide from '@/views/common/CrestResourceArrow.vue'
import { nextTick, onBeforeMount, reactive, ref, computed, onMounted } from 'vue'
import PreviewHead from '@/views/data-visualization/PreviewHead.vue'
import EmptyBackground from '@/components/empty-background/src/EmptyBackground.vue'
import { storeToRefs } from 'pinia'
import { useAppStoreWithOut } from '@/store/modules/app'
import {
  getMapElementIds,
  initCanvasData,
  initCanvasDataPrepare,
  onInitReady
} from '@/utils/canvasUtils'
import { useMoveLine } from '@/hooks/web/useMoveLine'
import { Icon } from '@/components/icon-custom'
import { download2AppTemplate, downloadCanvas2 } from '@/utils/imgUtils'
import MultiplexPreviewShow from '@/views/data-visualization/MultiplexPreviewShow.vue'
import DvPreview from '@/views/data-visualization/DvPreview.vue'
import AppExportForm from '@/components/application/AppExportForm.vue'
import { ElMessage } from 'element-plus-secondary'
import { useEmitt } from '@/hooks/web/useEmitt'
import { useRoute } from 'vue-router_2'

import { useUserStoreWithOut } from '@/store/modules/user'
import { useI18n } from '@/hooks/web/useI18n'
import {
  exportLogApp,
  exportLogImg,
  exportLogPDF,
  exportLogTemplate
} from '@/api/visualization/dataVisualization'
import { deepCopy } from '@/utils/utils'
const userStore = useUserStoreWithOut()

// 当前登录用户名称，用作应用导出默认创建人
const userName = computed(() => userStore.getName)
const { t } = useI18n()

const dvMainStore = dvMainStoreWithOut()
const route = useRoute()
const { dvInfo, canvasViewDataInfo } = storeToRefs(dvMainStore)
// 预览画布容器实例，用于截图、PDF 和模板导出
const previewCanvasContainer = ref(null)
// 数据大屏预览组件实例，用于加载完成后恢复画布
const dvPreviewRef = ref(null)
// 左侧资源树展开状态
const slideShow = ref(true)
// 画布数据是否初始化完成
const dataInitState = ref(true)
// 导出中的加载状态
const downloadStatus = ref(false)
const { width, node } = useMoveLine('DASHBOARD')
// 应用导出表单实例
const appExportFormRef = ref(null)
// 预览页入参，控制打开位置、关闭行为和资源表来源
const props = defineProps({
  showPosition: {
    required: false,
    type: String,
    default: 'preview'
  },
  noClose: {
    required: false,
    type: Boolean,
    default: false
  },
  resourceTable: {
    required: false,
    type: String,
    default: 'core'
  }
})

// 左侧资源树实例
const resourceTreeRef = ref()

// 左侧资源树是否有可展示数据
const hasTreeData = computed(() => {
  return resourceTreeRef.value?.hasData
})

// 资源树组件是否已挂载完成
const mounted = computed(() => {
  return resourceTreeRef.value?.mounted
})

// 当前用户是否具备资源根节点管理权限
const rootManage = computed(() => {
  return resourceTreeRef.value?.rootManage
})
const appStore = useAppStoreWithOut()

// 当前应用是否运行在嵌入式集成模式
const isCrestBi = computed(() => appStore.getIsCrestBi)

// 触发资源树的新建资源流程
function createNew() {
  resourceTreeRef.value?.createNewObject()
}

// 加载指定数据大屏画布，并根据预览或复用模式刷新状态
const loadCanvasData = (dvId, weight?, ext?) => {
  const initMethod = props.showPosition === 'multiplexing' ? initCanvasDataPrepare : initCanvasData
  dataInitState.value = false
  initMethod(
    dvId,
    { busiFlag: 'dataV', resourceTable: 'core' },
    function ({
      canvasDataResult,
      canvasStyleResult,
      dvInfo,
      canvasViewInfoPreview,
      curPreviewGap
    }) {
      dvInfo['weight'] = weight
      dvInfo['ext'] = ext || 0
      state.canvasDataPreview = canvasDataResult
      state.canvasStylePreview = canvasStyleResult
      state.canvasViewInfoPreview = canvasViewInfoPreview
      state.dvInfo = dvInfo
      state.curPreviewGap = curPreviewGap
      dataInitState.value = true
      // 保留原始画布尺寸，避免铺满全屏模板导出时发生位置偏移
      if (props.showPosition !== 'multiplexing') {
        state.canvasDataPreviewSource = deepCopy(canvasDataResult)
        state.canvasStylePreviewSource = deepCopy(canvasStyleResult)
      }

      if (props.showPosition === 'preview') {
        dvMainStore.updateCurDvInfo(dvInfo)
        nextTick(() => {
          dvPreviewRef.value?.restore()
        })
      }
      nextTick(() => {
        onInitReady({ resourceId: dvId })
        state.cacheStatusKey++
      })
    }
  )
}
// 将当前预览画布导出为图片或 PDF
const download = type => {
  downloadStatus.value = true
  const mapElementIds = getMapElementIds(state.canvasDataPreview)
  mapElementIds.forEach(id => useEmitt().emitter.emit('l7-prepare-picture', id))
  setTimeout(() => {
    const vueDom = previewCanvasContainer.value.querySelector('.canvas-container')
    downloadCanvas2(type, vueDom, state.dvInfo.name, () => {
      downloadStatus.value = false
      const param = {
        id: state.dvInfo.id,
        type: state.dvInfo.type === 'dashboard' ? 'panel' : 'screen'
      }
      type === 'img' ? exportLogImg(param) : exportLogPDF(param)
      mapElementIds.forEach(id => useEmitt().emitter.emit('l7-unprepare-picture', id))
    })
  }, 200)
}
// 将当前预览画布导出为应用或模板文件
const fileDownload = (downloadType, attachParams) => {
  downloadStatus.value = true
  const mapElementIds = getMapElementIds(state.canvasDataPreview)
  mapElementIds.forEach(id => useEmitt().emitter.emit('l7-prepare-picture', id))
  setTimeout(() => {
    const vueDom = previewCanvasContainer.value.querySelector('.canvas-container')
    download2AppTemplate(downloadType, vueDom, state.dvInfo.name, attachParams, () => {
      downloadStatus.value = false
      const param = {
        id: state.dvInfo.id,
        type: state.dvInfo.type === 'dashboard' ? 'panel' : 'screen'
      }
      downloadType === 'app' ? exportLogApp(param) : exportLogTemplate(param)
      mapElementIds.forEach(id => useEmitt().emitter.emit('l7-unprepare-picture', id))
    })
  }, 1000)
}

// 根据导出类型进入模板导出或应用导出前置流程
const downloadAsAppTemplate = downloadType => {
  if (downloadType === 'template') {
    fileDownload(downloadType, null)
  } else if (downloadType === 'app') {
    downLoadToAppPre()
  }
}

// 导出应用前检查模板图表，并初始化应用信息表单
const downLoadToAppPre = () => {
  const result = checkTemplate()
  if (result && result.length > 0) {
    ElMessage.warning(`当前仪表盘中[${result}]属于模版图表，无法导出，请先设置数据集！`)
  } else {
    appExportFormRef.value.init({
      appName: state.dvInfo.name,
      icon: null,
      version: '2.0',
      creator: userName.value,
      required: '2.9.0',
      description: null
    })
  }
}
// 检查画布中仍依赖模板数据的图表名称
const checkTemplate = () => {
  let templateViewNames = ','
  Object.keys(canvasViewDataInfo.value).forEach(key => {
    const viewInfo = canvasViewDataInfo.value[key]
    if (viewInfo && viewInfo?.dataFrom === 'template') {
      templateViewNames = templateViewNames + viewInfo.title + ','
    }
  })
  return templateViewNames.slice(1)
}

// 切换左侧资源树展开状态
const slideOpenChange = () => {
  slideShow.value = !slideShow.value
}

// 按当前资源权限重新加载预览画布
const reload = id => {
  loadCanvasData(id, state.dvInfo.weight, state.dvInfo.ext)
}

// 点击资源树节点后加载对应数据大屏
const resourceNodeClick = data => {
  loadCanvasData(data.id, data.weight, data.ext)
}

// 当前画布是否使用固定尺寸适配策略
const dataVKeepSize = computed(() => {
  return state.canvasStylePreview?.screenAdaptor === 'keep'
})

// 预览页核心状态，保存原始画布、展示画布、视图信息和资源信息
const state = reactive({
  canvasDataPreviewSource: null,
  canvasStylePreviewSource: null,
  canvasDataPreview: null,
  canvasStylePreview: null,
  canvasViewInfoPreview: null,
  dvInfo: null,
  curPreviewGap: 0,
  cacheStatusKey: 0
})

// 右侧资源树是否展开
const sideTreeStatus = ref(true)
// 更新右侧资源树展开状态
const changeSideTreeStatus = val => {
  sideTreeStatus.value = val
}

// 暴露当前预览状态，供父组件读取
const getPreviewStateInfo = () => {
  return state
}

// 应用导出表单提交后执行应用文件导出
const downLoadApp = appAttachInfo => {
  fileDownload('app', appAttachInfo)
}

onMounted(() => {
  useEmitt({
    name: 'canvasDownload',
    callback: function () {
      download('img')
    }
  })
  const routeDvId = route.query.dvId
  if (props.showPosition === 'preview' && routeDvId) {
    loadCanvasData(routeDvId)
  }
})

defineExpose({
  getPreviewStateInfo
})

onBeforeMount(() => {
  if (props.showPosition === 'preview') {
    dvMainStore.canvasDataInit()
  }
})
</script>

<template>
  <div class="dv-preview">
    <ArrowSide
      v-if="!noClose"
      :style="{ left: (sideTreeStatus ? width - 12 : 0) + 'px' }"
      @change-side-tree-status="changeSideTreeStatus"
      :isInside="!sideTreeStatus"
    ></ArrowSide>
    <el-aside
      class="resource-area"
      :class="{ 'close-side': !slideShow, retract: !sideTreeStatus }"
      ref="node"
      :style="{ width: width + 'px' }"
    >
      <CrestResourceTree
        ref="resourceTreeRef"
        v-show="slideShow"
        :cur-canvas-type="'dataV'"
        :show-position="showPosition"
        :resource-table="resourceTable"
        @node-click="resourceNodeClick"
      />
    </el-aside>
    <el-container
      class="preview-area"
      :class="{ 'no-data': !state.dvInfo?.id }"
      v-loading="!dataInitState"
    >
      <div @click="slideOpenChange" class="flexible-button-area" v-if="false">
        <el-icon v-if="slideShow"><ArrowLeft /></el-icon>
        <el-icon v-else><ArrowRight /></el-icon>
      </div>
      <template v-if="dvInfo.name">
        <preview-head
          v-if="showPosition === 'preview'"
          @reload="reload"
          @download="download"
          @downloadAsAppTemplate="downloadAsAppTemplate"
          :cache-reload-key="state.cacheStatusKey"
        />
        <div
          v-if="showPosition === 'multiplexing' && dataInitState"
          class="content multiplexing-content"
        >
          <multiplex-preview-show
            :component-data="state.canvasDataPreview"
            :canvas-style-data="state.canvasStylePreview"
            :canvas-view-info="state.canvasViewInfoPreview"
            :dv-info="state.dvInfo"
          ></multiplex-preview-show>
        </div>
        <div
          v-if="showPosition === 'preview'"
          :class="{ 'canvas_keep-size': dataVKeepSize }"
          ref="previewCanvasContainer"
          class="content"
        >
          <dv-preview
            ref="dvPreviewRef"
            v-if="state.canvasStylePreview && dataInitState"
            :show-position="showPosition"
            :canvas-data-preview="state.canvasDataPreview"
            :canvas-style-preview="state.canvasStylePreview"
            :canvas-view-info-preview="state.canvasViewInfoPreview"
            :dv-info="state.dvInfo"
            :cur-preview-gap="state.curPreviewGap"
            :download-status="downloadStatus"
          ></dv-preview>
        </div>
      </template>
      <template v-else-if="hasTreeData && mounted">
        <empty-background
          v-if="dataInitState"
          :description="t('visualization.select_screen_tips')"
          img-type="select"
        />
      </template>
      <template v-else-if="mounted">
        <empty-background
          v-if="dataInitState"
          :description="t('visualization.no_screen')"
          img-type="none"
        >
          <el-button v-if="rootManage && !isCrestBi" @click="createNew" type="primary">
            <template #icon>
              <Icon name="icon_add_outlined"><icon_add_outlined class="svg-icon" /></Icon>
            </template>
            {{ t('commons.create') }}{{ t('work_branch.big_data_screen') }}
          </el-button>
        </empty-background>
      </template>
    </el-container>
  </div>
  <app-export-form
    v-if="state.dvInfo && state.canvasDataPreview && state.canvasViewInfoPreview"
    ref="appExportFormRef"
    :dv-info="state.dvInfo"
    :component-data="state.canvasDataPreview"
    :canvas-view-info="state.canvasViewInfoPreview"
    @downLoadApp="downLoadApp"
  ></app-export-form>
</template>

<style lang="less">
.dv-preview {
  width: 100%;
  height: 100%;
  overflow: hidden;
  display: flex;
  background: #f8fafc;
  color: #0f172a;
  font-family: var(--crest-custom_font, var(--crest-font-sans));
  position: relative;
  .resource-area {
    position: relative;
    height: 100%;
    width: 279px;
    padding: 0;
    overflow: visible;
    border-right: 1px solid #e2e8f0;
    background: #ffffff;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
    z-index: 4;

    &.retract {
      display: none;
    }
  }
  .preview-area {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow-x: hidden;
    overflow-y: auto;
    position: relative;
    //transition: 0.5s;

    &.no-data {
      background-color: #f8fafc;
    }

    .content {
      flex: 1;
      width: 100%;
      overflow-x: hidden;
      overflow-y: auto;
      align-items: center;
    }
  }
}

.close-side {
  width: 0px !important;
  padding: 0px !important;
  border-right: 0px !important;
}

.flexible-button-area {
  position: absolute;
  height: 60px;
  width: 16px;
  left: 0;
  top: calc(50% - 30px);
  background-color: #ffffff;
  border-radius: 0 4px 4px 0;
  cursor: pointer;
  z-index: 10;
  display: flex;
  align-items: center;
  border-top: 1px solid #e2e8f0;
  border-right: 1px solid #e2e8f0;
  border-bottom: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.multiplexing-content {
  padding: 12px;
  background-color: #f8fafc;
}
</style>
