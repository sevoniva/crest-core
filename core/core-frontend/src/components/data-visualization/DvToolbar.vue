<script setup lang="ts">
import dvFilter from '@/assets/svg/dv-filter.svg'
import dvMaterial from '@/assets/svg/dv-material.svg'
import dvMedia from '@/assets/svg/dv-media.svg'
import dvMoreCom from '@/assets/svg/dv-more-com.svg'
import dvTab from '@/assets/svg/dv-tab.svg'
import dvText from '@/assets/svg/dv-text.svg'
import dvView from '@/assets/svg/dv-view.svg'
import icon_params_setting from '@/assets/svg/icon_params_setting.svg'
import icon_copy_filled from '@/assets/svg/icon_copy_filled.svg'
import icon_left_outlined from '@/assets/svg/icon_left_outlined.svg'
import icon_undo_outlined from '@/assets/svg/icon_undo_outlined.svg'
import icon_redo_outlined from '@/assets/svg/icon_redo_outlined.svg'
import dvRecoverOutlined from '@/assets/svg/dv-recover_outlined.svg'
import dvCancelPublish from '@/assets/svg/icon_undo_outlined.svg'
import { ElIcon, ElMessage, ElMessageBox } from 'element-plus-secondary'
import eventBus from '@/utils/eventBus'
import { ref, nextTick, computed, onBeforeUnmount, onMounted } from 'vue'
import { useEmbedded } from '@/store/modules/embedded'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { useAppStoreWithOut } from '@/store/modules/app'
import { storeToRefs } from 'pinia'
import Icon from '../icon-custom/src/Icon.vue'
import ComponentGroup from '@/components/visualization/ComponentGroup.vue'
import UserViewGroup from '@/custom-component/component-group/UserViewGroup.vue'
import MediaGroup from '@/custom-component/component-group/MediaGroup.vue'
import TextGroup from '@/custom-component/component-group/TextGroup.vue'
import CommonGroup from '@/custom-component/component-group/CommonGroup.vue'
import CrestResourceGroupActions from '@/views/common/CrestResourceGroupActions.vue'
import {
  canvasSave,
  canvasSaveWithParams,
  checkCanvasChangePre,
  findAllViewsId,
  initCanvasData
} from '@/utils/canvasUtils'
import { changeSizeWithScale } from '@/utils/changeComponentsSizeWithScale'
import MoreComGroup from '@/custom-component/component-group/MoreComGroup.vue'
import { useCache } from '@/hooks/web/useCache'
import QueryGroup from '@/custom-component/component-group/QueryGroup.vue'
import ComponentButton from '@/components/visualization/ComponentButton.vue'
import OuterParamsSet from '@/components/visualization/OuterParamsSet.vue'
import MultiplexingCanvas from '@/views/common/MultiplexingCanvas.vue'
import ComponentButtonLabel from '@/components/visualization/ComponentButtonLabel.vue'
import CrestFullscreen from '@/components/visualization/common/CrestFullscreen.vue'
import CrestAppApply from '@/views/common/CrestAppApply.vue'
import { useEmitt } from '@/hooks/web/useEmitt'
import { useUserStoreWithOut } from '@/store/modules/user'
import TabsGroup from '@/custom-component/component-group/TabsGroup.vue'
import { useI18n } from '@/hooks/web/useI18n'
import { updatePublishStatus } from '@/api/visualization/dataVisualization'

// 画布名称是否处于内联编辑状态
let nameEdit = ref(false)
// 画布名称编辑框的临时输入值
let inputName = ref('')
// 画布名称输入框实例，用于进入编辑后自动聚焦
let nameInput = ref(null)
const dvMainStore = dvMainStoreWithOut()
const snapshotStore = snapshotStoreWithOut()
const { styleChangeTimes, snapshotIndex } = storeToRefs(snapshotStore)
// 资源分组选择弹窗实例，用于新建数据大屏资源
const resourceGroupOpt = ref(null)
// 应用保存弹窗实例，用于将当前大屏保存为应用
const resourceAppOpt = ref(null)
// 工具栏根节点实例，用于计算预览缩放比例
const dvToolbarMain = ref(null)
const { componentData, canvasStyleData, canvasViewInfo, dvInfo, editMode, appData } =
  storeToRefs(dvMainStore)
// 进入预览前的编辑缩放比例，退出预览时恢复
let scaleEdit = 100
const { wsCache } = useCache('localStorage')
const dvModel = 'dataV'
// 外部参数设置弹窗实例
const outerParamsSetRef = ref(null)
// 全屏预览组件实例
const fullScreeRef = ref(null)
const userStore = useUserStoreWithOut()
const { t } = useI18n()
// 工具栏向父级通知恢复已发布版本
const emits = defineEmits(['recoverToPublished'])

defineProps({
  createType: {
    type: String,
    default: 'create'
  }
})
// 结束画布名称编辑，并在名称有效且变更时写回状态
const closeEditCanvasName = () => {
  nameEdit.value = false
  if (!inputName.value || !inputName.value.trim()) {
    return
  }
  if (inputName.value.trim() === dvInfo.value.name) {
    return
  }
  if (inputName.value.trim().length > 64 || inputName.value.trim().length < 1) {
    ElMessage.warning('名称字段长度1-64个字符')
    editCanvasName()
    return
  }
  dvInfo.value.name = inputName.value
  inputName.value = ''
}

// 通知父级恢复到当前已发布版本
const recoverToPublished = () => {
  emits('recoverToPublished')
}

// 回退画布快照
const undo = () => {
  snapshotStore.undo()
}

// 前进画布快照
const redo = () => {
  snapshotStore.redo()
}

// 切换到预览模式，并按工具栏宽度重新计算画布缩放
const preview = () => {
  // 记录编辑模式缩放比例，便于退出预览后恢复
  dvMainStore.setEditMode('preview')
  nextTick(() => {
    scaleEdit = canvasStyleData.value.scale
    const newScale = getFullScale()
    changeSizeWithScale(newScale)
  })
}

// 退出预览模式并恢复进入预览前的缩放比例
const edit = () => {
  dvMainStore.setEditMode('edit')
  changeSizeWithScale(scaleEdit)
}

// 资源新建完成后写入基础信息并继续保存画布
const resourceOptFinish = param => {
  if (param && param.opt === 'newLeaf') {
    dvInfo.value.dataState = 'ready'
    dvInfo.value.pid = param.pid
    dvInfo.value.name = param.name
    saveCanvasWithCheck(param.withPublish, param.status)
  }
}

// 保存前检查组织权限和资源状态，必要时先打开资源创建流程
const saveCanvasWithCheck = (withPublish = false, status?) => {
  if (userStore.getOid && wsCache.get('user.oid') && userStore.getOid !== wsCache.get('user.oid')) {
    ElMessageBox.confirm('已切换至新组织，无权保存其他组织的资源', {
      confirmButtonType: 'primary',
      type: 'warning',
      confirmButtonText: '关闭页面',
      cancelButtonText: '取消',
      autofocus: false,
      showClose: false
    }).then(() => {
      window.close()
    })
    return
  }
  if (dvInfo.value.dataState === 'prepare') {
    if (appData.value) {
      // 新应用保存时需要先收集应用基础信息
      const params = {
        base: {
          pid: '',
          name: dvInfo.value.name,
          datasetFolderPid: null,
          datasetFolderName: dvInfo.value.name,
          dataType: dvInfo.value['dataType']
        },
        appData: appData.value
      }
      nextTick(() => {
        resourceAppOpt.value.init(params)
      })
    } else {
      const params = {
        name: dvInfo.value.name,
        leaf: true,
        id: dvInfo.value.pid || '0'
      }
      resourceGroupOpt.value.optInit('leaf', params, 'newLeaf', true, { withPublish, status })
    }
    return
  }
  checkCanvasChangePre(() => {
    saveResource({ withPublish, status })
  })
}

// 执行画布保存，并在保存成功后刷新缓存、应用状态和发布状态
const saveResource = (checkParams?) => {
  if (styleChangeTimes.value > 0 || checkParams.withPublish) {
    eventBus.emit('hideArea-canvas-main')
    nextTick(() => {
      canvasSaveWithParams(checkParams, () => {
        snapshotStore.resetStyleChangeTimes()
        wsCache.delete('CREST-DV-CACHE-' + dvInfo.value.id)
        let url = window.location.href
        url = url.replace(/(#\/[^?]*)(?:\?[^#]*)?/, `$1?dvId=${dvInfo.value.id}`)
        if (!embeddedStore.baseUrl) {
          window.history.replaceState(
            {
              path: url
            },
            '',
            url
          )
        }
        if (appData.value) {
          initCanvasData(dvInfo.value.id, { busiFlag: 'dataV', resourceTable: 'snapshot' }, () => {
            useEmitt().emitter.emit('refresh-dataset-selector')
            resourceAppOpt.value.close()
            dvMainStore.setAppDataInfo(null)
            useEmitt().emitter.emit('calcData-all')
            snapshotStore.resetSnapshot()
          })
        }
        if (checkParams.withPublish) {
          publishStatusChange(checkParams.status)
        } else {
          ElMessage.success(t('commons.save_success'))
        }
      })
    })
  }
}

// 清空当前画布组件并记录快照
const clearCanvas = () => {
  dvMainStore.setCurComponent({ component: null, index: null })
  dvMainStore.setComponentData([])
  snapshotStore.recordSnapshotCache('renderChart')
}

// 进入画布名称编辑状态
const editCanvasName = () => {
  nameEdit.value = true
  inputName.value = dvInfo.value.name
  nextTick(() => {
    nameInput.value.focus()
  })
}

// 返回大屏列表前检查未保存变更
const backToMain = () => {
  let url = '#/screen/index'
  if (dvInfo.value.id) {
    url = url + '?dvId=' + dvInfo.value.id
  }
  if (styleChangeTimes.value > 0) {
    ElMessageBox.confirm(t('visualization.change_save_tips'), {
      confirmButtonType: 'primary',
      type: 'warning',
      autofocus: false,
      showClose: false
    }).then(() => {
      backHandler(url)
    })
  } else {
    backHandler(url)
  }
}
const embeddedStore = useEmbedded()
// 当前编辑器是否运行在嵌入式环境
const isEmbedded = computed(() => appStore.getIsCrestBi || appStore.getIsIframe)

// 执行返回逻辑，嵌入式环境走宿主回调，普通环境走浏览器导航
const backHandler = (url: string) => {
  if (isEmbedded.value) {
    wsCache.set(`dv-info-id`, dvInfo.value.id)
    embeddedStore.clearState()
    useEmitt().emitter.emit('changeCurrentComponent', 'ScreenPanel')
    return
  }
  if (window['crest-embedded-host'] && openHandler?.value) {
    const pm = {
      methodName: 'embeddedInteractive',
      args: {
        eventName: 'crest-dashboard-editor-back',
        args: 'Just a demo that descript Crest embedded interactive'
      }
    }
    openHandler.value.invokeMethod(pm)
    return
  }
  dvMainStore.canvasStateChange({ key: 'curPointArea', value: 'base' })
  wsCache.delete('CREST-DV-CACHE-' + dvInfo.value.id)
  wsCache.set('dv-info-id', dvInfo.value.id)
  if (!!history.state.back) {
    history.back()
  } else {
    window.open(url, '_self')
  }
}
// 嵌入式宿主开放的交互句柄
const openHandler = ref(null)

// 画布名称变更后记录快照
const onDvNameChange = () => {
  snapshotStore.recordSnapshotCache('onDvNameChange')
}

// 根据当前工具栏宽度计算画布完整展示所需缩放比例
const getFullScale = () => {
  const curWidth = dvToolbarMain.value.clientWidth
  return (curWidth * 100) / canvasStyleData.value.width
}
const appStore = useAppStoreWithOut()
// 复用画布弹窗实例
const multiplexingRef = ref(null)

onMounted(() => {
  eventBus.on('preview', preview)
  eventBus.on('save', saveCanvasWithCheck)
  eventBus.on('clearCanvas', clearCanvas)
})

onBeforeUnmount(() => {
  eventBus.off('preview', preview)
  eventBus.off('save', saveCanvasWithCheck)
  eventBus.off('clearCanvas', clearCanvas)
  dvMainStore.setAppDataInfo(null)
})

// 保存当前画布后打开外部参数设置
const openOuterParamsSet = () => {
  if (componentData.value.length === 0) {
    ElMessage.warning(t('components.add_components_first'))
    return
  }
  if (!dvInfo.value.id || dvInfo.value.dataState === 'prepare') {
    ElMessage.warning(t('components.current_page_first'))
    return
  }
  // 外部参数依赖已保存资源，打开设置前先落库当前画布
  canvasSave(() => {
    outerParamsSetRef.value.optInit()
  })
}

// 打开大屏复用弹窗
const multiplexingCanvasOpen = () => {
  multiplexingRef.value.dialogInit('dataV')
}

// 更新发布状态，并同步当前画布中的活跃视图列表
const publishStatusChange = status => {
  const targetViewIds = []
  findAllViewsId(componentData.value, targetViewIds)
  // 发布状态变更需要携带当前画布视图范围，供后端刷新可见资源
  updatePublishStatus({
    id: dvInfo.value.id,
    name: dvInfo.value.name,
    mobileLayout: dvInfo.value.mobileLayout,
    status,
    activeViewIds: targetViewIds,
    type: 'dataV'
  }).then(() => {
    dvMainStore.updateDvInfoCall(status)
    if (status) {
      ElMessage.success(t('visualization.published_success'))
      snapshotStore.initSnapShot()
    } else {
      ElMessage.success(t('visualization.cancel_publish_tips'))
    }
  })
}

// 是否处于 iframe 容器内，用于隐藏不适合嵌入态的全屏入口
const isIframe = computed(() => appStore.getIsIframe)
// 进入全屏预览前先清理当前选区
const fullScreenPreview = () => {
  dvMainStore.canvasStateChange({ key: 'curPointArea', value: 'base' })
  fullScreeRef.value.toggleFullscreen()
}
</script>

<template>
  <div class="toolbar-main" ref="dvToolbarMain">
    <div class="toolbar">
      <template v-if="editMode === 'preview'">
        <div class="left-area">
          <span id="canvas-name" class="name-area" style="height: 100%; padding: 10px">
            {{ dvInfo.name }}
          </span>
        </div>
        <div class="middle-area"></div>
      </template>
      <template v-else>
        <el-icon class="custom-el-icon back-icon" @click="backToMain()">
          <Icon name="icon_left_outlined">
            <icon_left_outlined class="svg-icon toolbar-icon" />
          </Icon>
        </el-icon>
        <div class="left-area">
          <span id="dv-canvas-name" class="name-area" @dblclick="editCanvasName">
            {{ dvInfo.name }}
          </span>
          <div class="opt-area">
            <el-tooltip effect="light" :content="$t('visualization.undo')" placement="bottom">
              <el-icon
                class="toolbar-hover-icon"
                :class="{ 'toolbar-icon-disabled': snapshotIndex < 1 }"
                @click="undo()"
              >
                <Icon name="icon_undo_outlined">
                  <icon_undo_outlined class="svg-icon" />
                </Icon>
              </el-icon>
            </el-tooltip>
            <el-tooltip effect="light" :content="$t('commons.reduction')" placement="bottom">
              <el-icon
                class="toolbar-hover-icon opt-icon-redo"
                :class="{
                  'toolbar-icon-disabled': snapshotIndex === snapshotStore.snapshotData.length - 1
                }"
                @click="redo()"
              >
                <Icon name="icon_redo_outlined">
                  <icon_redo_outlined class="svg-icon" />
                </Icon>
              </el-icon>
            </el-tooltip>
          </div>
        </div>
        <div class="middle-area">
          <component-group
            show-split-line
            is-label
            :base-width="410"
            :icon-name="dvView"
            :title="t('visualization.view')"
          >
            <user-view-group></user-view-group>
          </component-group>
          <component-group
            :base-width="115"
            :show-split-line="true"
            is-label
            :icon-name="dvFilter"
            :title="t('visualization.query_component')"
          >
            <query-group :dv-model="dvModel"></query-group>
          </component-group>
          <component-group
            is-label
            :base-width="215"
            :icon-name="dvText"
            :title="t('visualization.text_html')"
          >
            <text-group></text-group>
          </component-group>
          <component-group
            is-label
            placement="bottom"
            :base-width="328"
            :icon-name="dvMedia"
            :title="t('visualization.media')"
          >
            <media-group></media-group>
          </component-group>
          <component-group is-label :base-width="115" :icon-name="dvTab" title="Tab">
            <tabs-group :dv-model="dvModel"></tabs-group>
          </component-group>
          <component-group
            is-label
            :base-width="328"
            :icon-name="dvMoreCom"
            :title="t('visualization.more')"
          >
            <more-com-group></more-com-group>
          </component-group>
          <component-group
            is-label
            :base-width="410"
            :icon-name="dvMaterial"
            :show-split-line="true"
            :title="t('visualization.source_material')"
          >
            <common-group></common-group>
          </component-group>
          <component-button-label
            :icon-name="icon_copy_filled"
            :title="t('visualization.multiplexing')"
            is-label
            @customClick="multiplexingCanvasOpen"
          ></component-button-label>
        </div>
      </template>
      <div class="right-area">
        <el-tooltip
          effect="dark"
          :offset="12"
          :content="t('visualization.external_parameter_settings')"
          placement="bottom"
        >
          <component-button
            v-show="editMode === 'edit'"
            :tips="t('visualization.external_parameter_settings')"
            @custom-click="openOuterParamsSet"
            :icon-name="icon_params_setting"
          />
        </el-tooltip>
        <div v-show="editMode === 'edit'" class="divider"></div>
        <el-button
          v-if="editMode === 'preview'"
          icon="EditPen"
          @click="edit()"
          class="preview-button"
          type="primary"
        >
          {{ t('visualization.edit') }}
        </el-button>
        <el-button
          v-else-if="!isIframe"
          class="preview-button"
          @click="fullScreenPreview"
          style="float: right"
        >
          {{ t('visualization.preview') }}
        </el-button>
        <el-button
          @click="saveCanvasWithCheck()"
          :disabled="styleChangeTimes < 1"
          style="float: right; margin-right: 12px"
          type="primary"
        >
          {{ t('visualization.save') }}
        </el-button>
        <el-dropdown
          :disabled="dvInfo.status === 0"
          popper-class="menu-outer-dv_popper-toolbar"
          trigger="hover"
        >
          <el-button
            @click="saveCanvasWithCheck(true, 1)"
            style="float: right; margin: 0 12px 0 0"
            type="primary"
          >
            {{ t('visualization.publish') }}
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="recoverToPublished" v-if="dvInfo.status === 2">
                <el-icon class="handle-icon">
                  <Icon name="icon_left_outlined">
                    <dv-recover-outlined class="svg-icon toolbar-icon" />
                  </Icon>
                </el-icon>
                {{ t('visualization.publish_recover') }}
              </el-dropdown-item>
              <el-dropdown-item
                @click.stop="publishStatusChange(0)"
                v-if="[1, 2].includes(dvInfo.status)"
              >
                <el-icon class="handle-icon">
                  <Icon name="icon_left_outlined">
                    <dv-cancel-publish class="svg-icon toolbar-icon" />
                  </Icon>
                </el-icon>
                {{ t('visualization.cancel_publish') }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
    <Teleport v-if="nameEdit" :to="'#dv-canvas-name'">
      <input
        @keydown.stop
        @keyup.stop
        @change="onDvNameChange"
        ref="nameInput"
        minlength="2"
        maxlength="64"
        v-model="inputName"
        @blur="closeEditCanvasName"
      />
    </Teleport>

    <CrestResourceGroupActions
      @finish="resourceOptFinish"
      cur-canvas-type="dataV"
      ref="resourceGroupOpt"
    />
    <CrestAppApply
      ref="resourceAppOpt"
      :component-data="componentData"
      :dv-info="dvInfo"
      :canvas-view-info="canvasViewInfo"
      cur-canvas-type="dataV"
      @saveAppCanvas="saveCanvasWithCheck"
    ></CrestAppApply>
  </div>
  <CrestFullscreen ref="fullScreeRef" show-position="dvEdit"></CrestFullscreen>
  <multiplexing-canvas ref="multiplexingRef"></multiplexing-canvas>
  <outer-params-set ref="outerParamsSetRef"></outer-params-set>
</template>

<style lang="less" scoped>
.toolbar-main {
  position: relative;
}

.preview-state-head {
  height: 0px !important;
  overflow: hidden;
  padding: 0;
  margin: 0;
}

.edit-button {
  right: 10px;
  top: 10px;
  position: absolute;
  z-index: 10;
}

.toolbar {
  height: @top-bar-height;
  white-space: nowrap;
  overflow-x: auto;
  background: #1a1a1a;
  color: #ffffff;
  box-shadow: 0px 2px 4px 0px rgba(31, 35, 41, 0.12);
  display: flex;
  transition: 0.5s;

  .back-icon {
    margin-left: 20px;
    margin-top: 22px;
    font-size: 20px;
  }

  .left-area {
    margin-top: 8px;
    margin-left: 14px;
    width: 300px;
    display: flex;
    flex-direction: column;

    .name-area {
      position: relative;
      line-height: 24px;
      height: 24px;
      font-size: 16px;
      width: 300px;
      overflow: hidden;
      cursor: pointer;
      color: @dv-canvas-main-font-color;

      input {
        position: absolute;
        left: 0;
        width: 100%;
        color: @dv-canvas-main-font-color;
        background-color: #050e21;
        outline: none;
        border: 1px solid #295acc;
        border-radius: 6px;
        padding: 0 4px;
        height: 100%;
      }
    }

    .opt-area {
      width: 300px;
      text-align: left;
      color: #a6a6a6;

      .opt-icon-redo {
        margin-left: 12px;
      }
    }
  }

  .middle-area {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .right-area {
    width: 400px;
    display: flex;
    align-items: center;
    justify-content: right;
  }

  .custom-el-icon {
    margin-left: 15px;
    color: #ffffff;
    cursor: pointer;
    vertical-align: -0.2em;
  }

  .toolbar-icon {
    width: 20px;
    height: 20px;
  }
}

.preview-button {
  border-color: rgba(255, 255, 255, 0.3);
  color: #ffffff;
  background-color: transparent;

  &:hover,
  &:focus {
    background-color: #121a2c;
    border-color: #595f6b;
  }

  &:active {
    border-color: #616774;
    background-color: #1e2637;
  }
}

.divider {
  background: #ffffff4d;
  width: 1px;
  height: 18px;
  margin-right: 20px;
  margin-left: 10px;
}
</style>

<style lang="less">
.menu-outer-dv_popper-toolbar {
  border: 1px solid rgba(67, 67, 67, 1) !important;
  background-color: rgba(41, 41, 41, 1) !important;
  .ed-dropdown-menu {
    background-color: rgba(41, 41, 41, 1) !important;
  }
  .ed-dropdown-menu__item {
    color: rgba(235, 235, 235, 1) !important;
  }
  .handle-icon {
    color: rgba(166, 166, 166, 1) !important;
  }

  .ed-dropdown-menu__item:not(.is-disabled):focus,
  .ed-dropdown-menu__item:not(.is-disabled):hover {
    background-color: #444141cc !important;
  }
}
</style>
