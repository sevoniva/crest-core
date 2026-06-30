<script setup lang="ts">
import dvBatch from '@/assets/svg/dv-batch.svg'
import dvDashboard from '@/assets/svg/dv-dashboard.svg'
import dvHidden from '@/assets/svg/dv-hidden.svg'
import dvFilter from '@/assets/svg/dv-filter.svg'
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
import icon_pc_fullscreen from '@/assets/svg/icon_pc_fullscreen.svg'
import dvPreviewOuter from '@/assets/svg/dv-preview-outer.svg'
import dvRecoverOutlined from '@/assets/svg/dv-recover_outlined.svg'
import dvCancelPublish from '@/assets/svg/icon_undo_outlined.svg'
import { ElIcon, ElMessage, ElMessageBox } from 'element-plus-secondary'
import eventBus from '@/utils/eventBus'
import { useEmbedded } from '@/store/modules/embedded'
import { deepCopy } from '@/utils/utils'
import { nextTick, reactive, ref, computed, onBeforeUnmount, onMounted } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { useAppStoreWithOut } from '@/store/modules/app'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { storeToRefs } from 'pinia'
import Icon from '../icon-custom/src/Icon.vue'
import ComponentGroup from '@/components/visualization/ComponentGroup.vue'
import UserViewGroup from '@/custom-component/component-group/UserViewGroup.vue'
import QueryGroup from '@/custom-component/component-group/QueryGroup.vue'
import MediaGroup from '@/custom-component/component-group/MediaGroup.vue'
import TextGroup from '@/custom-component/component-group/TextGroup.vue'
import ComponentButton from '@/components/visualization/ComponentButton.vue'
import ComponentButtonLabel from '@/components/visualization/ComponentButtonLabel.vue'
import MultiplexingCanvas from '@/views/common/MultiplexingCanvas.vue'
import { useI18n } from '@/hooks/web/useI18n'
import { getPanelAllLinkageInfo, saveLinkage } from '@/api/visualization/linkage'
import { queryVisualizationJumpInfo } from '@/api/visualization/linkJump'
import {
  canvasSave,
  canvasSaveWithParams,
  checkCanvasChangePre,
  findAllViewsId,
  initCanvasData
} from '@/utils/canvasUtils'
import { useEmitt } from '@/hooks/web/useEmitt'
import { copyStoreWithOut } from '@/store/modules/data-visualization/copy'
import TabsGroup from '@/custom-component/component-group/TabsGroup.vue'
import CrestResourceGroupActions from '@/views/common/CrestResourceGroupActions.vue'
import OuterParamsSet from '@/components/visualization/OuterParamsSet.vue'
import DbMoreComGroup from '@/custom-component/component-group/DbMoreComGroup.vue'
import { useCache } from '@/hooks/web/useCache'
import CrestFullscreen from '@/components/visualization/common/CrestFullscreen.vue'
import CrestAppApply from '@/views/common/CrestAppApply.vue'
import { useUserStoreWithOut } from '@/store/modules/user'
import { updatePublishStatus } from '@/api/visualization/dataVisualization'
const { t } = useI18n()
const dvMainStore = dvMainStoreWithOut()
const snapshotStore = snapshotStoreWithOut()
const copyStore = copyStoreWithOut()
const { styleChangeTimes, snapshotIndex } = storeToRefs(snapshotStore)
// 应用资源保存弹窗引用，仪表板被作为应用创建时使用
const resourceAppOpt = ref(null)
const {
  linkageSettingStatus,
  curLinkageView,
  componentData,
  dvInfo,
  canvasViewInfo,
  editMode,
  batchOptStatus,
  targetLinkageInfo,
  curBatchOptComponents,
  appData
} = storeToRefs(dvMainStore)
// 当前工具栏固定服务仪表板模型，传给组件面板过滤可用组件
const dvModel = 'dashboard'
// 组件复用弹窗引用，用于打开复制到当前画布的复用流程
const multiplexingRef = ref(null)
// 内部全屏预览组件引用，负责编辑器内预览切换
const fullScreeRef = ref(null)
// 画布名称编辑态，双击标题后进入输入模式
let nameEdit = ref(false)
// 画布名称临时输入值，校验通过后写回全局画布信息
let inputName = ref('')
// 名称输入框引用，用于进入编辑态后自动聚焦
let nameInput = ref(null)
// 批量操作前的画布快照，保留取消或后续恢复所需的基准状态
const state = reactive({
  preBatchComponentData: [],
  preBatchCanvasViewInfo: {}
})
// 资源分组选择弹窗引用，首次保存普通仪表板时选择目录
const resourceGroupOpt = ref(null)
// 外部参数设置弹窗引用，打开前必须先保证画布已保存
const outerParamsSetRef = ref(null)
const { wsCache } = useCache('localStorage')
const userStore = useUserStoreWithOut()
// iframe 状态用于控制工具栏内部分导航行为
const isIframe = computed(() => appStore.getIsIframe)
// 工具栏向父级通知恢复到已发布版本的操作
const emits = defineEmits(['recoverToPublished'])

// 创建类型由父组件传入，默认保持普通创建流程
defineProps({
  createType: {
    type: String,
    default: 'create'
  }
})

// 开始编辑画布名称，并在下一轮渲染后聚焦输入框
const editCanvasName = () => {
  nameEdit.value = true
  inputName.value = dvInfo.value.name
  nextTick(() => {
    nameInput.value.focus()
  })
}
// 结束名称编辑时做空值、重复值和长度校验，通过后只更新本地画布信息
const closeEditCanvasName = () => {
  nameEdit.value = false
  if (!inputName.value || !inputName.value.trim()) {
    return
  }
  if (inputName.value.trim() === dvInfo.value.name) {
    return
  }
  if (inputName.value.trim().length > 64 || inputName.value.trim().length < 1) {
    ElMessage.warning(t('components.length_1_64_characters'))
    editCanvasName()
    return
  }
  dvInfo.value.name = inputName.value
  inputName.value = ''
}

// 撤销最近一次快照，并通知画布重新初始化矩阵布局
const undo = () => {
  if (snapshotIndex.value > 0) {
    snapshotStore.undo()
    eventBus.emit('matrix-canvasInit', false)
  }
}

// 恢复被撤销的快照，并同步刷新画布矩阵
const redo = () => {
  if (snapshotIndex.value !== snapshotStore.snapshotData.length - 1) {
    snapshotStore.redo()
    eventBus.emit('matrix-canvasInit', false)
  }
}

// 在当前编辑器内打开全屏预览，不改变外部路由
const previewInner = () => {
  fullScreeRef.value.toggleFullscreen()
}

// 保存后打开新窗口预览，未落库的画布不允许生成外部预览地址
const previewOuter = () => {
  if (!dvInfo.value.id || dvInfo.value.dataState === 'prepare') {
    ElMessage.warning(t('components.current_page_first'))
    return
  }
  canvasSave(() => {
    let url =
      '#/preview?dvId=' + dvInfo.value.id + '&dvType=dashboard&ignoreParams=true&editPreview=true'
    if (embeddedStore.baseUrl) {
      url = `${embeddedStore.baseUrl}${url}`.replaceAll('\/\/#', '\/#')
    }
    const newWindow = window.open(url, '_blank')
    initOpenHandler(newWindow)
  })
}

// 从预览态切回编辑态
const edit = () => {
  dvMainStore.setEditMode('edit')
}

// 汇总画布和 Tab 内的查询组件，保存前用于刷新查询条件
const queryList = computed(() => {
  let arr = []
  componentData.value.forEach(com => {
    if (com.innerType === 'VQuery') {
      arr.push(com)
    }
    if ('Tabs' === com.innerType) {
      com.propValue.forEach(itx => {
        arr = [...itx.componentData.filter(item => item.innerType === 'VQuery'), ...arr]
      })
    }
  })
  return arr
})

// 首次选择资源目录后回填画布基础信息，并继续执行保存或发布
const resourceOptFinish = param => {
  if (param && param.opt === 'newLeaf') {
    dvInfo.value.dataState = 'ready'
    dvInfo.value.pid = param.pid
    dvInfo.value.name = param.name
    saveCanvasWithCheck(param.withPublish, param.status)
  }
}

// 将恢复到已发布版本的动作透传给父级处理
const recoverToPublished = () => {
  emits('recoverToPublished')
}

// 更新发布状态时同步当前画布内所有视图编号，保证发布态包含最新组件集合
const publishStatusChange = status => {
  const targetViewIds = []
  findAllViewsId(componentData.value, targetViewIds)
  // 发布状态更新必须携带当前移动端布局和活动视图清单，后端据此生成发布快照
  updatePublishStatus({
    id: dvInfo.value.id,
    name: dvInfo.value.name,
    mobileLayout: dvInfo.value.mobileLayout,
    activeViewIds: targetViewIds,
    status,
    type: 'dashboard'
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

// 保存前检查组织上下文和首次保存目录，必要时先引导资源归档再继续
const saveCanvasWithCheck = (withPublish = false, status?) => {
  if (userStore.getOid && wsCache.get('user.oid') && userStore.getOid !== wsCache.get('user.oid')) {
    ElMessageBox.confirm(t('components.from_other_organizations'), {
      confirmButtonType: 'primary',
      type: 'warning',
      confirmButtonText: t('components.close_the_page'),
      cancelButtonText: t('common.cancel'),
      autofocus: false,
      showClose: false
    }).then(() => {
      window.close()
    })
    return
  }
  if (dvInfo.value.dataState === 'prepare') {
    if (appData.value) {
      // 应用创建需要先收集应用资源参数，再由应用保存弹窗完成归档
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
      const params = { name: dvInfo.value.name, leaf: true, id: dvInfo.value.pid || '0' }
      resourceGroupOpt.value.optInit('leaf', params, 'newLeaf', true, { withPublish, status })
      return
    }
  }
  checkCanvasChangePre(() => {
    saveResource({ withPublish, status })
  })
}

// 执行实际画布保存，并在需要发布时串联发布状态更新
const saveResource = (checkParams?) => {
  wsCache.delete('CREST-DV-CACHE-' + dvInfo.value.id)
  if (styleChangeTimes.value > 0 || checkParams.withPublish) {
    dvMainStore.matrixSizeAdaptor()
    queryList.value.forEach(ele => {
      useEmitt().emitter.emit(`updateQueryCriteria${ele.id}`)
    })
    try {
      canvasSaveWithParams(checkParams, () => {
        snapshotStore.resetStyleChangeTimes()
        let url = window.location.href
        url = url.replace(/(#\/[^?]*)(?:\?[^#]*)?/, `$1?resourceId=${dvInfo.value.id}`)
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
          initCanvasData(
            dvInfo.value.id,
            { busiFlag: 'dashboard', resourceTable: 'snapshot' },
            () => {
              useEmitt().emitter.emit('refresh-dataset-selector')
              useEmitt().emitter.emit('calcData-all')
              resourceAppOpt.value.close()
              dvMainStore.setAppDataInfo(null)
              snapshotStore.resetSnapshot()
            }
          )
        }
        if (checkParams.withPublish) {
          publishStatusChange(checkParams.status)
        } else {
          ElMessage.success(t('commons.save_success'))
        }
      })
    } catch (e) {
      console.error(e)
    }
  }
}

// 清空当前画布组件并记录快照，便于后续撤销恢复
const clearCanvas = () => {
  dvMainStore.setCurComponent({ component: null, index: null })
  dvMainStore.setComponentData([])
  snapshotStore.recordSnapshotCache('renderChart')
}

// 返回列表前检查未保存样式变更，避免直接离开导致编辑内容丢失
const backToMain = () => {
  let url = '#/panel/index'
  if (dvInfo.value.id) {
    url = url + '?dvId=' + dvInfo.value.id
  }
  if (styleChangeTimes.value > 0) {
    ElMessageBox.confirm(t('components.sure_to_exit'), {
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

// 根据嵌入式宿主、浏览器历史和普通页面三种场景执行返回
const backHandler = (url: string) => {
  if (isEmbedded.value) {
    wsCache.set(`db-info-id`, dvInfo.value.id)
    embeddedStore.clearState()
    useEmitt().emitter.emit('changeCurrentComponent', 'DashboardPanel')
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
  wsCache.delete('CREST-DV-CACHE-' + dvInfo.value.id)
  wsCache.set('db-info-id', dvInfo.value.id)
  if (!!history.state.back) {
    history.back()
  } else {
    window.open(url, '_self')
  }
}

// 打开组件复用弹窗，实际组件选择和写入由弹窗内部处理
const multiplexingCanvasOpen = () => {
  multiplexingRef.value.dialogInit()
}
// 监听全局工具栏事件，和快捷键、外部按钮保持同一套保存预览入口
onMounted(() => {
  eventBus.on('preview', previewInner)
  eventBus.on('save', saveCanvasWithCheck)
  eventBus.on('clearCanvas', clearCanvas)
})
// 组件卸载时移除全局事件，防止重复进入编辑器后触发多次保存
onBeforeUnmount(() => {
  eventBus.off('preview', previewInner)
  eventBus.off('save', saveCanvasWithCheck)
  eventBus.off('clearCanvas', clearCanvas)
  dvMainStore.setAppDataInfo(null)
})
// 打开仪表板设置前清空当前组件选择，确保设置面板针对画布本身
const openDataBoardSetting = () => {
  dvMainStore.setCurComponent({ component: null, index: null })
  dvMainStore.setHiddenListStatus(false)
}

// 切换隐藏组件列表的显隐状态
const openHiddenList = () => {
  dvMainStore.setHiddenListStatus()
}

// 批量删除选中组件，包含 Tab 容器内部的嵌套组件
const batchDelete = () => {
  const componentDataTemp = deepCopy(componentData.value)
  componentDataTemp.forEach(component => {
    if (curBatchOptComponents.value.includes(component.id)) {
      eventBus.emit('removeMatrixItemById-' + component.canvasId, component.id)
    }
    if (component.component === 'Tabs') {
      component.propValue?.forEach(tabItem => {
        tabItem.componentData?.forEach(tabComponent => {
          if (curBatchOptComponents.value.includes(tabComponent.id)) {
            eventBus.emit('removeMatrixItemById-' + tabComponent.canvasId, tabComponent.id)
          }
        })
      })
    }
  })
  nextTick(() => {
    saveBatchChange()
  })
}

// 批量复制选中组件，复制数据保留画布视图上下文供粘贴时重建位置
const batchCopy = () => {
  const multiplexingComponents = {}
  componentData.value.forEach(component => {
    if (curBatchOptComponents.value.includes(component.id)) {
      multiplexingComponents[component.id] = component
    }
    if (component.component === 'Tabs') {
      component.propValue?.forEach(tabItem => {
        tabItem.componentData?.forEach(tabComponent => {
          if (curBatchOptComponents.value.includes(tabComponent.id)) {
            multiplexingComponents[tabComponent.id] = tabComponent
          }
        })
      })
    }
  })
  copyStore.copyMultiplexingComponents(
    canvasViewInfo.value,
    multiplexingComponents,
    true,
    'batchOpt'
  )
  saveBatchChange()
}

// 切换批量模式时保存进入前快照，并关闭隐藏列表避免状态冲突
const batchOptStatusChange = value => {
  if (value) {
    // 进入批量操作前保留组件和画布信息，后续可以基于该状态做恢复
    state.preBatchComponentData = deepCopy(componentData.value)
    state.preBatchCanvasViewInfo = deepCopy(canvasViewInfo.value)
  } else {
    state.preBatchComponentData = []
    state.preBatchCanvasViewInfo = {}
  }
  dvMainStore.setHiddenListStatus(false)
  dvMainStore.setBatchOptStatus(value)
}

// 打开外部参数设置前确保已有组件且画布已落库，避免参数绑定到临时资源
const openOuterParamsSet = () => {
  if (componentData.value.length === 0) {
    ElMessage.warning(t('components.add_components_first'))
    return
  }
  if (!dvInfo.value.id || dvInfo.value.dataState === 'prepare') {
    ElMessage.warning(t('components.current_page_first'))
    return
  }
  // 参数配置依赖最新画布结构，打开弹窗前必须先完成一次保存
  canvasSave(() => {
    outerParamsSetRef.value.optInit()
  })
}

// 批量操作保存后退出批量模式
const saveBatchChange = () => {
  batchOptStatusChange(false)
}

// 取消联动配置并清理当前联动编辑上下文
const cancelLinkageSetting = () => {
  dvMainStore.clearLinkageSettingInfo()
}

// 保存当前视图联动配置，并刷新面板联动与跳转缓存
const saveLinkageSetting = () => {
  // 每组联动必须同时选择来源字段和目标字段，否则后端无法生成有效过滤条件
  for (const key in targetLinkageInfo.value) {
    let subCheckCount = 0
    const linkageInfo = targetLinkageInfo.value[key]
    const linkageFields = linkageInfo['linkageFields']
    if (linkageFields) {
      linkageFields.forEach(function (linkage) {
        if (!(linkage.sourceField && linkage.targetField)) {
          subCheckCount++
        }
      })
    }

    if (subCheckCount > 0) {
      ElMessage.error(
        t('visualization.datalist') +
          '【' +
          linkageInfo.targetViewName +
          '】' +
          t('visualization.exit_un_march_linkage_field')
      )
      return
    }
  }
  const request = {
    dvId: dvInfo.value.id,
    sourceViewId: curLinkageView.value.id,
    linkageInfo: targetLinkageInfo.value
  }
  saveLinkage(request).then(() => {
    ElMessage.success(t('save_success.common'))
    // 联动保存后刷新面板级联动缓存，保证后续预览和编辑读取一致
    getPanelAllLinkageInfo(dvInfo.value.id).then(rsp => {
      dvMainStore.setNowPanelTrackInfo(rsp.data)
    })
    cancelLinkageSetting()
    // 跳转配置可能与联动目标共享视图上下文，需要同步刷新
    queryVisualizationJumpInfo(dvInfo.value.id).then(rsp => {
      dvMainStore.setNowPanelJumpInfo(rsp.data)
    })
  })
}

// 名称变化时记录快照，使标题编辑也能进入撤销历史
const onDvNameChange = () => {
  snapshotStore.recordSnapshotCache('onDvNameChange')
}
const appStore = useAppStoreWithOut()
// 嵌入式运行态包含产品内嵌和 iframe 两类宿主
const isEmbedded = computed(() => appStore.getIsCrestBi || appStore.getIsIframe)

// 外部窗口处理器引用，嵌入式宿主可通过它接管新窗口对象
const openHandler = ref(null)
// 预览窗口创建后交给宿主处理器，便于嵌入式场景统一管理窗口
const initOpenHandler = newWindow => {
  if (openHandler?.value) {
    const pm = {
      methodName: 'initOpenHandler',
      args: newWindow
    }
    openHandler.value.invokeMethod(pm)
  }
}
</script>

<template>
  <div class="toolbar-main">
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
        <el-icon v-if="!batchOptStatus" class="custom-el-icon back-icon" @click="backToMain()">
          <Icon name="icon_left_outlined"
            ><icon_left_outlined class="svg-icon toolbar-icon"
          /></Icon>
        </el-icon>
        <div class="left-area" v-if="editMode === 'edit' && !batchOptStatus">
          <span id="canvas-name" class="name-area" @dblclick="editCanvasName">
            {{ dvInfo.name }}
          </span>
          <div class="opt-area">
            <el-tooltip effect="dark" :content="$t('visualization.undo')" placement="bottom">
              <el-icon
                class="toolbar-hover-icon"
                :class="{ 'toolbar-icon-disabled': snapshotIndex < 1 }"
                :disabled="snapshotIndex < 1"
                @click="undo()"
              >
                <Icon name="icon_undo_outlined"><icon_undo_outlined class="svg-icon" /></Icon>
              </el-icon>
            </el-tooltip>

            <el-tooltip effect="dark" :content="$t('commons.reduction')" placement="bottom">
              <el-icon
                class="toolbar-hover-icon opt-icon-redo"
                :class="{
                  'toolbar-icon-disabled': snapshotIndex === snapshotStore.snapshotData.length - 1
                }"
                @click="redo()"
              >
                <Icon name="icon_redo_outlined"><icon_redo_outlined class="svg-icon" /></Icon>
              </el-icon>
            </el-tooltip>
          </div>
        </div>
        <div class="left-area" v-if="batchOptStatus">
          <el-col class="adapt-count">
            <span>{{ t('user.selection_info', [curBatchOptComponents.length]) }}</span>
          </el-col>
        </div>
        <div class="middle-area" v-if="!batchOptStatus && !linkageSettingStatus">
          <component-group
            :base-width="410"
            :show-split-line="true"
            is-label
            :icon-name="dvView"
            themes="light"
            :title="t('chart.datalist')"
          >
            <user-view-group themes="light" :dv-model="dvModel"></user-view-group>
          </component-group>
          <component-group
            :base-width="115"
            :show-split-line="true"
            is-label
            themes="light"
            :icon-name="dvFilter"
            :title="t('visualization.filter_component')"
          >
            <query-group themes="light" :dv-model="dvModel"></query-group>
          </component-group>
          <component-group
            is-label
            themes="light"
            :base-width="115"
            :icon-name="dvText"
            :title="t('components.rich_text')"
          >
            <text-group themes="light" :dv-model="dvModel"></text-group>
          </component-group>
          <component-group
            is-label
            themes="light"
            placement="bottom"
            :base-width="328"
            :icon-name="dvMedia"
            :title="t('components.media')"
          >
            <media-group themes="light" :dv-model="dvModel"></media-group>
          </component-group>
          <component-group themes="light" is-label :base-width="115" :icon-name="dvTab" title="Tab">
            <tabs-group themes="light" :dv-model="dvModel"></tabs-group>
          </component-group>
          <component-group
            themes="light"
            show-split-line
            is-label
            :base-width="215"
            :icon-name="dvMoreCom"
            :title="t('visualization.more')"
          >
            <db-more-com-group themes="light" :dv-model="dvModel"></db-more-com-group>
          </component-group>
          <component-button-label
            :icon-name="icon_copy_filled"
            :title="t('visualization.multiplexing')"
            is-label
            @customClick="multiplexingCanvasOpen"
          ></component-button-label>
        </div>
      </template>

      <div class="right-area" v-if="!batchOptStatus && !linkageSettingStatus">
        <template v-if="editMode !== 'preview'">
          <el-tooltip
            effect="dark"
            :content="t('visualization.outer_param_set')"
            placement="bottom"
          >
            <component-button
              :tips="t('visualization.outer_param_set')"
              @custom-click="openOuterParamsSet"
              :icon-name="icon_params_setting"
            />
          </el-tooltip>
          <el-tooltip effect="dark" :content="t('visualization.batch_opt')" placement="bottom">
            <component-button
              :tips="t('visualization.batch_opt')"
              @custom-click="batchOptStatusChange(true)"
              :icon-name="dvBatch"
            />
          </el-tooltip>

          <el-tooltip
            effect="dark"
            :content="t('components.dashboard_configuration')"
            placement="bottom"
          >
            <component-button
              :tips="t('components.dashboard_configuration')"
              @custom-click="openDataBoardSetting"
              :icon-name="dvDashboard"
            />
          </el-tooltip>
          <el-tooltip
            effect="dark"
            :content="t('visualization.hidden_components')"
            placement="bottom"
          >
            <component-button
              :tips="t('visualization.hidden_components')"
              @custom-click="openHiddenList"
              :icon-name="dvHidden"
            />
          </el-tooltip>
        </template>

        <el-dropdown v-if="editMode === 'edit'" trigger="hover">
          <el-button class="preview-button" style="float: right; margin-right: 12px">
            {{ t('visualization.preview') }}
          </el-button>
          <template #dropdown>
            <el-dropdown-menu class="drop-style">
              <el-dropdown-item @click="previewInner" v-if="!isIframe">
                <el-icon style="margin-right: 8px; font-size: 16px">
                  <Icon name="icon_pc_fullscreen"><icon_pc_fullscreen class="svg-icon" /></Icon>
                </el-icon>
                {{ t('visualization.fullscreen_preview') }}
              </el-dropdown-item>
              <el-dropdown-item @click="previewOuter()">
                <el-icon style="margin-right: 8px; font-size: 16px">
                  <Icon><dvPreviewOuter class="svg-icon" /></Icon>
                </el-icon>
                {{ t('work_branch.new_page_preview') }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-button
          class="custom-normal-button"
          v-if="editMode === 'preview'"
          icon="EditPen"
          @click="edit()"
          type="primary"
        >
          {{ t('data_set.edit') }}
        </el-button>
        <template v-if="editMode === 'edit' || editMode === 'preview'">
          <el-button
            v-if="editMode === 'edit' || editMode === 'preview'"
            :disabled="styleChangeTimes < 1"
            @click="saveCanvasWithCheck()"
            style="float: right; margin-right: 12px"
            type="primary"
          >
            {{ t('data_set.save') }}
          </el-button>
          <el-dropdown
            :disabled="dvInfo.status === 0"
            popper-class="menu-outer-dv_popper"
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
                    <Icon name="icon_left_outlined"
                      ><dv-recover-outlined class="svg-icon toolbar-icon"
                    /></Icon>
                  </el-icon>
                  {{ t('visualization.publish_recover') }}
                </el-dropdown-item>
                <el-dropdown-item
                  @click="publishStatusChange(0)"
                  v-if="[1, 2].includes(dvInfo.status)"
                >
                  <el-icon class="handle-icon">
                    <Icon name="icon_left_outlined"
                      ><dv-cancel-publish class="svg-icon toolbar-icon"
                    /></Icon>
                  </el-icon>
                  {{ t('visualization.cancel_publish') }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </div>

      <div class="right-area full-area" v-if="batchOptStatus">
        <el-button
          text
          icon="CopyDocument"
          class="custom-normal-button"
          @click="batchCopy"
          :disabled="curBatchOptComponents.length === 0"
          style="float: right; margin-right: 12px"
        >
          {{ t('data_set.copy') }}</el-button
        >

        <el-button
          text
          icon="Delete"
          class="custom-normal-button"
          @click="batchDelete"
          :disabled="curBatchOptComponents.length === 0"
          style="float: right; margin-right: 12px"
        >
          {{ t('data_set.delete') }}</el-button
        >

        <el-button
          @click="saveBatchChange"
          style="float: right; margin-right: 12px"
          type="primary"
          >{{ t('components.complete') }}</el-button
        >
      </div>

      <div class="right-area full-area" v-if="linkageSettingStatus">
        <el-button
          class="custom-normal-button"
          @click="cancelLinkageSetting()"
          style="float: right; margin-right: 12px"
        >
          {{ t('userimport.cancel') }}</el-button
        >
        <el-button
          @click="saveLinkageSetting"
          style="float: right; margin-right: 12px"
          type="primary"
          >{{ t('userimport.sure') }}</el-button
        >
      </div>
    </div>
    <Teleport v-if="nameEdit" :to="'#canvas-name'">
      <input
        @change="onDvNameChange"
        ref="nameInput"
        v-model="inputName"
        @blur="closeEditCanvasName"
      />
    </Teleport>

    <multiplexing-canvas ref="multiplexingRef"></multiplexing-canvas>
    <CrestResourceGroupActions
      @finish="resourceOptFinish"
      cur-canvas-type="dashboard"
      ref="resourceGroupOpt"
    />
    <outer-params-set ref="outerParamsSetRef"> </outer-params-set>
  </div>
  <CrestFullscreen show-position="edit" ref="fullScreeRef"></CrestFullscreen>
  <CrestAppApply
    ref="resourceAppOpt"
    :component-data="componentData"
    :dv-info="dvInfo"
    :canvas-view-info="canvasViewInfo"
    cur-canvas-type="dashboard"
    @saveAppCanvas="saveCanvasWithCheck"
  ></CrestAppApply>
</template>

<style lang="less" scoped>
.group_icon + .ed-dropdown,
.group_icon + .ed-button {
  margin-left: 10px;
}
.drop-style {
  :deep(.ed-dropdown-menu__item) {
    padding: 5px 12px !important;
  }
  :deep(.ed-dropdown-menu__item:not(.is_disabled):focus) {
    color: inherit;
    background-color: rgba(31, 35, 41, 0.1);
  }
}
.full-area {
  flex: 1;
}
.edit-button {
  right: 10px;
  top: 10px;
  position: absolute;
  z-index: 10;
}
.toolbar-main {
  position: relative;
}
.preview-state-head {
  height: 0px !important;
  overflow: hidden;
  padding: 0;
  margin: 0;
}
.toolbar {
  height: @top-bar-height;
  white-space: nowrap;
  overflow-x: auto;
  background: #050e21;
  color: #ffffff;
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
      input {
        position: absolute;
        left: 0;
        width: 100%;
        color: #fff;
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

    .divider {
      background: #ffffff4d;
      width: 1px;
      height: 18px;
      margin: 0 10px;
    }
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
  background-color: #050e21;
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
.custom-normal-button {
  background-color: transparent;
  border-color: #a6a6a6 !important;
  color: #ffffff !important;
  &:hover {
    color: #ffffff;
    background-color: #ffffff1a !important;
  }
  &:active {
    color: #ffffff;
    background-color: #ffffff33 !important;
  }
  &.is-disabled {
    color: var(--ed-button-disabled-text-color) !important;
  }
}

.adapt-count {
  color: #ffffff;
  margin-left: 10px;
  font-size: 14px;
  font-weight: 400;
  padding-top: 14px;
}
</style>
