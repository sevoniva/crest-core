<template>
  <div
    class="bar-main"
    v-if="!mobileInPc && !isMobile()"
    :class="[
      showEditPosition,
      {
        'bar-main-background': mainBackgroundShow
      }
    ]"
    @mousedown="fieldsAreaDown"
  >
    <el-tooltip
      effect="dark"
      placement="top"
      :content="t('visualization.sort')"
      v-if="element.component === 'Tabs' && showPosition === 'canvas'"
    >
      <el-icon class="bar-base-icon" @click="tabSort">
        <Sort />
      </el-icon>
    </el-tooltip>
    <template v-if="element.component === 'VQuery' && showPosition === 'canvas'">
      <span :title="t('visualization.add_query_filter')">
        <el-icon class="bar-base-icon" @click="addQueryCriteria">
          <Icon name="icon_add_outlined"><icon_add_outlined class="svg-icon" /></Icon
        ></el-icon>
      </span>
      <span :title="t('visualization.edit_query_filter')">
        <el-icon class="bar-base-icon" @click="editQueryCriteria">
          <Icon name="icon_edit_outlined"><icon_edit_outlined class="svg-icon" /></Icon
        ></el-icon>
      </span>
    </template>
    <el-tooltip
      effect="dark"
      placement="top"
      :content="t('visualization.enlarge')"
      v-if="element.innerType !== 'rich-text' && barShowCheck('enlarge')"
    >
      <span>
        <el-icon class="bar-base-icon" @click="userViewEnlargeOpen($event, 'enlarge')">
          <Icon name="dv-bar-enlarge"><dvBarEnlarge class="svg-icon" /></Icon>
        </el-icon>
      </span>
    </el-tooltip>
    <el-tooltip
      effect="dark"
      :placement="showBarTooltipPosition"
      :content="t('visualization.show_data_info')"
      v-if="!['picture-group', 'rich-text'].includes(element.innerType) && barShowCheck('details')"
    >
      <span>
        <el-icon class="bar-base-icon" @click="userViewEnlargeOpen($event, 'details')">
          <Icon name="dv-details"><dvDetails class="svg-icon" /></Icon>
        </el-icon>
      </span>
    </el-tooltip>
    <el-tooltip
      effect="dark"
      placement="top"
      :content="t('visualization.input_calc_data')"
      v-if="barShowCheck('datasetParams') && datasetParamsSetShow"
    >
      <span>
        <el-icon class="bar-base-icon" @click="datasetParamsInit">
          <Icon name="icon_params_setting"><icon_params_setting class="svg-icon" /></Icon>
        </el-icon>
      </span>
    </el-tooltip>

    <div v-if="barShowCheck('multiplexing')" class="bar-checkbox-area">
      <el-checkbox
        @click.stop
        style="height: 26px; padding: 5px"
        v-model="state.multiplexingCheckModel"
        @change="multiplexingCheck"
      />
    </div>
    <span
      :title="t('visualization.cancel_linkage')"
      v-if="barShowCheck('unLinkage') && existLinkage"
    >
      <el-icon class="bar-base-icon" @click="clearLinkage">
        <Icon name="dv-bar-unLinkage"><dvBarUnLinkage class="svg-icon" /></Icon>
      </el-icon>
    </span>
    <div v-if="barShowCheck('batchOpt')" class="bar-checkbox-area">
      <el-checkbox v-model="state.batchOptCheckModel" @change="batchOptChange" />
    </div>

    <el-dropdown
      trigger="click"
      placement="right-start"
      v-if="barShowCheck('setting')"
      ref="curDropdown"
    >
      <el-icon class="bar-base-icon">
        <el-tooltip :content="t('visualization.more')" effect="dark" placement="bottom">
          <icon name="icon_more_outlined"><icon_more_outlined class="svg-icon" /></icon>
        </el-tooltip>
      </el-icon>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item @click="copyComponent" v-if="barShowCheck('copy')">{{
            t('visualization.copy')
          }}</el-dropdown-item>
          <template v-if="element.innerType !== 'rich-text' && barShowCheck('enlarge')">
            <el-dropdown-item
              :divided="showPosition === 'canvas'"
              @click="userViewEnlargeOpen($event, 'enlarge')"
              >{{ t('visualization.enlarge') }}</el-dropdown-item
            >
            <el-dropdown-item
              @click="userViewEnlargeOpen($event, 'details')"
              v-if="
                !['picture-group', 'rich-text'].includes(element.innerType) &&
                barShowCheck('details')
              "
              >{{ t('visualization.show_data_info') }}</el-dropdown-item
            >
            <el-dropdown-item
              style="padding: 0"
              v-if="
                !['picture-group', 'rich-text'].includes(element.innerType) &&
                barShowCheck('download') &&
                showDownload &&
                (exportPermissions[0] || exportPermissions[1])
              "
              @click.prevent
            >
              <el-dropdown style="width: 100%" trigger="hover" placement="right-start">
                <div
                  class="flex-align-center"
                  style="
                    position: relative;
                    width: 100%;
                    padding: 5px 32px 5px 16px;
                    line-height: 24px;
                  "
                >
                  {{ t('visualization.export_as') }}
                  <el-icon size="16px" style="position: absolute; right: 8px; margin-right: 0"
                    ><ArrowRight
                  /></el-icon>
                </div>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-if="exportPermissions[1] && excelExportEnabled"
                      @click="exportAsExcel"
                      >{{ excelExportLabel }}</el-dropdown-item
                    >
                    <el-dropdown-item
                      v-if="exportPermissions[1] && element.innerType === 'table-pivot'"
                      @click="exportAsFormattedExcel"
                    >
                      <span>{{ t('visualization.excel_with_format') }}</span>
                    </el-dropdown-item>
                    <el-dropdown-item v-if="exportPermissions[0]" @click="exportAsImage">{{
                      t('visualization.image')
                    }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </el-dropdown-item>
          </template>
          <el-dropdown-item
            @click="hiddenComponent"
            v-if="barShowCheck('hidden') && isMainCanvas(canvasId)"
            >{{ t('visualization.hidden') }}</el-dropdown-item
          >

          <el-dropdown-item divided @click="deleteComponent" v-if="barShowCheck('delete')">{{
            t('visualization.delete')
          }}</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
    <el-dropdown
      trigger="click"
      placement="right-start"
      v-if="
        !['picture-group', 'rich-text'].includes(element.innerType) &&
        barShowCheck('previewDownload') &&
        showDownload &&
        (exportPermissions[0] || exportPermissions[1])
      "
    >
      <el-icon @click="downloadClick" class="bar-base-icon">
        <el-tooltip :content="t('chart.export')" effect="dark" placement="bottom">
          <icon name="dv-preview-download"><dvPreviewDownload class="svg-icon" /></icon>
        </el-tooltip>
      </el-icon>
      <template #dropdown>
        <el-dropdown-menu style="width: 118px">
          <el-dropdown-item
            @click="exportAsExcel"
            v-if="exportPermissions[1] && excelExportEnabled"
            >{{ excelExportLabel }}</el-dropdown-item
          >
          <el-dropdown-item
            v-if="exportPermissions[1] && element.innerType === 'table-pivot'"
            @click="exportAsFormattedExcel"
          >
            <span>{{ t('visualization.excel_with_format') }}</span>
          </el-dropdown-item>
          <el-dropdown-item v-if="exportPermissions[0]" @click="exportAsImage">{{
            t('visualization.image')
          }}</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
    <el-popover v-if="selectFieldShow" width="200" trigger="click" @mousedown="fieldsAreaDown">
      <template #reference>
        <el-icon class="bar-base-icon">
          <Icon name="database"><database class="svg-icon" /></Icon
        ></el-icon>
      </template>
      <fields-list :fields="state.curFields" :element="element" />
    </el-popover>
    <custom-tabs-sort ref="customTabsSortRef"></custom-tabs-sort>
  </div>
</template>

<script lang="ts" setup>
import icon_edit_outlined from '@/assets/svg/icon_edit_outlined.svg'
import icon_add_outlined from '@/assets/svg/icon_add_outlined.svg'
import dvBarEnlarge from '@/assets/svg/dv-bar-enlarge.svg'
import dvDetails from '@/assets/svg/dv-details.svg'
import icon_params_setting from '@/assets/svg/icon_params_setting.svg'
import dvBarUnLinkage from '@/assets/svg/dv-bar-unLinkage.svg'
import database from '@/assets/svg/database.svg'
import icon_more_outlined from '@/assets/svg/icon_more_outlined.svg'
import dvPreviewDownload from '@/assets/svg/icon_download_outlined.svg'
import { computed, onBeforeUnmount, onMounted, reactive, ref, toRefs, watch } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { useI18n } from '@/hooks/web/useI18n'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import eventBus from '@/utils/eventBus'
import { useEmitt } from '@/hooks/web/useEmitt'
import { copyStoreWithOut } from '@/store/modules/data-visualization/copy'
import { exportExcelDownload } from '@/views/chart/components/js/util'
import FieldsList from '@/custom-component/rich-text/FieldsList.vue'
import { ElTooltip } from 'element-plus-secondary'
import CustomTabsSort from '@/custom-component/tabs/CustomTabsSort.vue'
import { exportPivotExcel } from '@/views/chart/components/js/panel/common/common_table'
import { exportPermission, isMobile } from '@/utils/utils'
import { isMainCanvas } from '@/utils/canvasUtils'
import {
  exportFilteredExcel,
  showExportQueuedMessage
} from '@/utils/visualization/filteredExcelExportService'
import {
  EXCEL_EXPORT_SCOPE,
  defaultViewExcelExportConfig,
  isExportableTableView
} from '@/utils/visualization/filteredExcelExport.mjs'
import { canShowDownloadAction } from './componentEditBarState.mjs'
const dvMainStore = dvMainStoreWithOut()
const snapshotStore = snapshotStoreWithOut()
const copyStore = copyStoreWithOut()
/**
 * 标签页排序弹窗引用
 */
const customTabsSortRef = ref(null)
/**
 * 当前资源导出权限集合
 */
const exportPermissions = computed(() =>
  exportPermission(dvInfo.value['weight'], dvInfo.value['ext'])
)
/**
 * 工具条向画布核心转发的操作事件
 */
const emits = defineEmits([
  'userViewEnlargeOpen',
  'datasetParamsInit',
  'closePreview',
  'showViewDetails',
  'amRemoveItem',
  'linkJumpSetOpen',
  'linkageSetOpen',
  'componentImageDownload'
])
const { t } = useI18n()
const { emitter } = useEmitt()
// bar所在位置可以显示的功能按钮
const positionBarShow = {
  canvas: [
    'datasetParams',
    'enlarge',
    'hidden',
    'details',
    'setting',
    'copy',
    'delete',
    'download',
    'unLinkage',
    'linkageSetting',
    'linkJumpSetting'
  ],
  preview: ['enlarge', 'details', 'download', 'unLinkage', 'previewDownload', 'datasetParams'],
  multiplexing: ['multiplexing'],
  batchOpt: ['batchOpt'],
  linkage: ['linkage']
}

// bar所属组件类型可以显示的功能按钮
const componentTypeBarShow = {
  UserView: [
    'datasetParams',
    'enlarge',
    'hidden',
    'details',
    'setting',
    'copy',
    'download',
    'previewDownload',
    'delete',
    'multiplexing',
    'batchOpt',
    'linkage',
    'unLinkage',
    'linkageSetting',
    'linkJumpSetting'
  ],
  default: ['setting', 'delete', 'copy', 'multiplexing', 'batchOpt', 'hidden']
}

/**
 * 判断指定按钮是否可在当前组件和位置中展示
 */
const barShowCheck = barName => {
  return (
    positionBarShow[showPosition.value] &&
    positionBarShow[showPosition.value].includes(barName) &&
    (componentTypeBarShow[element.value.component]
      ? componentTypeBarShow[element.value.component]
      : componentTypeBarShow['default']
    ).includes(barName)
  )
}

/**
 * 判断工具条是否需要展示主色背景
 */
const mainBackgroundShow = computed(() => {
  return !['batchOpt', 'multiplexing'].includes(showPosition.value)
})
/**
 * 组件工具条的目标组件和展示位置入参
 */
const props = defineProps({
  element: {
    type: Object,
    required: true
  },
  active: {
    type: Boolean,
    required: false,
    default: false
  },
  index: {
    required: true,
    type: [Number, String],
    default: 0
  },
  showPosition: {
    required: false,
    type: String,
    default: 'canvas'
  },
  canvasId: {
    type: String,
    default: 'canvas-main'
  }
})

const { element, index, showPosition, canvasId } = toRefs(props)
const {
  batchOptStatus,
  pcMatrixCount,
  curComponent,
  componentData,
  canvasViewInfo,
  mobileInPc,
  dvInfo,
  hiddenListStatus
} = storeToRefs(dvMainStore)

/**
 * 工具条本地交互状态
 */
const state = reactive({
  systemOS: 'Mac',
  maxImageSize: 15000000,
  boardSetVisible: false,
  linkJumpSetVisible: false,
  linkJumpSetViewId: null,
  curFields: [],
  multiplexingCheckModel: false,
  // Currently selected Multiplexing components
  curMultiplexingComponents: {},
  barWidth: 24,
  componentType: null,
  linkageActiveStatus: false,
  editFilter: ['view', 'custom', 'custom-button'],
  timer: null,
  viewXArray: [],
  batchOptCheckModel: false
})
/**
 * 判断当前主画布是否展示隐藏组件入口
 */
const showHiddenIcon = computed(() => hiddenListStatus.value && isMainCanvas(canvasId.value))

/**
 * 打开当前标签页组件的排序弹窗
 */
const tabSort = () => {
  customTabsSortRef.value.sortInit(element.value)
}

/**
 * 点击下载按钮时同步当前选中组件
 */
const downloadClick = () => {
  dvMainStore.setCurComponent({ component: element.value, index: index.value })
}

/**
 * 通知查询组件新增筛选条件
 */
const addQueryCriteria = () => {
  emitter.emit(`addQueryCriteria${element.value.id}`)
}

/**
 * 通知查询组件编辑筛选条件
 */
const editQueryCriteria = () => {
  emitter.emit(`editQueryCriteria${element.value.id}`)
}

/**
 * 根据矩阵位置和展示场景计算工具条方向
 */
const showEditPosition = computed(() => {
  if (showPosition.value === 'canvas') {
    const baseLeft = element.value.x - 1
    const baseRight = pcMatrixCount.value.x - (element.value.x + element.value.sizeX - 1)
    if ((baseLeft === 0 && baseRight === 0) || baseRight < 0) {
      if (showHiddenIcon.value) {
        return 'bar-main-left-inner'
      } else {
        return 'bar-main-right-inner'
      }
    } else if (baseRight === 0) {
      return 'bar-main-left-outer'
    } else {
      return 'bar-main-right'
    }
  } else if (showPosition.value === 'preview') {
    return 'bar-main-right-inner'
  } else if (showPosition.value === 'canvasDataV') {
    return 'bar-main-right'
  } else {
    return 'bar-main-preview-right-inner'
  }
})

/**
 * 根据工具条方向计算提示框位置
 */
const showBarTooltipPosition = computed(() => {
  if (showEditPosition.value.indexOf('right') >= 0) {
    return 'right'
  } else {
    return 'left'
  }
})

/**
 * 打开数据导出中心
 */
const callbackExport = () => {
  useEmitt().emitter.emit('data-export-center', { activeName: 'ALL' })
}
/**
 * 导出保留表格格式的透视表 Excel
 */
const exportAsFormattedExcel = () => {
  const s2Instance = dvMainStore.getViewInstanceInfo(element.value.id)
  if (!s2Instance) {
    return
  }
  const chart = dvMainStore.getViewDetails(element.value.id)
  exportPivotExcel(s2Instance, chart)
}

/**
 * 根据图表类型导出普通 Excel 或筛选结果 Excel
 */
const exportAsExcel = () => {
  if (!isExportableTableView(currentViewInfo.value)) {
    const viewDataInfo = dvMainStore.getViewDataDetails(element.value.id)
    const chartExtRequest = dvMainStore.getLastViewRequestInfo(element.value.id)
    const viewInfo = dvMainStore.getViewDetails(element.value.id)
    const chart = { ...viewInfo, chartExtRequest, data: viewDataInfo, busiFlag: dvInfo.value.type }
    exportExcelDownload(chart, dvInfo.value.name, result => {
      if (result === 'error') {
        return
      }
      showExportQueuedMessage(callbackExport)
    })
    return
  }

  exportFilteredExcel({
    targetViewId: element.value.id,
    scope: tableExcelConfig.value.scope,
    content: tableExcelConfig.value.content,
    onQueued: () => {
      showExportQueuedMessage(callbackExport)
    }
  })
}
/**
 * 触发组件图片导出
 */
const exportAsImage = () => {
  emits('componentImageDownload')
}
/**
 * 删除当前组件并记录快照
 */
const deleteComponent = () => {
  eventBus.emit('removeMatrixItem-' + canvasId.value, index.value)
  dvMainStore.setCurComponent({ component: null, index: null })
  snapshotStore.recordSnapshotCache('deleteComponent')
}

/**
 * 打开数据集参数配置
 */
const datasetParamsInit = () => {
  // do init
  emits('datasetParamsInit')
}

/**
 * 复制并粘贴当前组件
 */
const copyComponent = () => {
  copyStore.copy()
  copyStore.paste(false)
}

/**
 * 打开图表放大或明细查看
 */
const userViewEnlargeOpen = (e, opt) => {
  e.preventDefault()
  e.stopPropagation()
  emits('userViewEnlargeOpen', opt)
}

/**
 * 将当前组件移入隐藏列表
 */
const hiddenComponent = () => {
  if (curComponent.value) {
    curComponent.value.dashboardHidden = true
    eventBus.emit('removeMatrixItemPosition-' + canvasId.value, curComponent.value)
    dvMainStore.setHiddenListStatus(true)
    snapshotStore.recordSnapshotCache('hide')
    dvMainStore.setLastHiddenComponent(curComponent.value.id)
  }
}

// 复用-Begin

/**
 * 从外部切换当前组件复用勾选状态
 */
const multiplexingCheckOut = () => {
  state.multiplexingCheckModel = !state.multiplexingCheckModel
  multiplexingCheck(state.multiplexingCheckModel)
}

/**
 * 切换当前组件是否加入复用选择集合
 */
const multiplexingCheck = val => {
  if (val) {
    // push
    dvMainStore.addCurMultiplexingComponent({
      component: element.value,
      componentId: element.value.id
    })
  } else {
    // remove
    dvMainStore.removeCurMultiplexingComponentWithId(element.value.id)
  }
}
// 复用-End

// 批量操作-Begin

/**
 * 从外部切换当前组件批量操作勾选状态
 */
const batchOptCheckOut = () => {
  if (showPosition.value === 'batchOpt') {
    state.batchOptCheckModel = !state.batchOptCheckModel
    batchOptChange(state.batchOptCheckModel)
  }
}

/**
 * 切换当前组件是否加入批量操作集合
 */
const batchOptChange = val => {
  if (val) {
    // push
    dvMainStore.addCurBatchComponent(element.value)
  } else {
    // remove
    dvMainStore.removeCurBatchComponentWithId(element.value.id)
  }
}
// 批量操作-End

/**
 * 判断目标组件是否存在来自当前组件的联动条件
 */
const linkageChange = item => {
  let checkResult = false
  if (item.linkageFilters && item.linkageFilters.length > 0) {
    item.linkageFilters.forEach(linkage => {
      if (element.value.id === linkage?.sourceViewId) {
        checkResult = true
      }
    })
  }
  return checkResult
}

/**
 * 统计当前组件作为源视图的联动数量
 */
const existLinkage = computed(() => {
  let linkageFiltersCount = 0
  componentData.value.forEach(item => {
    if (item.component === 'UserView' && item.innerType != 'VQuery') {
      if (linkageChange(item)) {
        linkageFiltersCount++
      }
    } else if (item.component === 'Group') {
      item.propValue.forEach(groupItem => {
        if (linkageChange(groupItem)) {
          linkageFiltersCount++
        }
      })
    } else if (item.component === 'Tabs') {
      item.propValue.forEach(tabItem => {
        tabItem.componentData?.forEach(tabComponent => {
          if (linkageChange(tabComponent)) {
            linkageFiltersCount++
          }
        })
      })
    }
  })
  return linkageFiltersCount
})

// 清除相同sourceViewId 的 联动条件
const clearLinkage = () => {
  dvMainStore.clearViewLinkage(element.value.id)
  useEmitt().emitter.emit('clearPanelLinkage', { viewId: element.value.id })
}

// 富文本-Begin

/**
 * 阻止富文本字段面板触发画布拖拽
 */
const fieldsAreaDown = e => {
  // ignore
  e.stopPropagation()
  e.preventDefault()
}

/**
 * 判断富文本字段选择器是否展示
 */
const selectFieldShow = computed(() => {
  return (
    ['canvasDataV', 'canvas'].includes(showPosition.value) &&
    curComponent.value?.innerType === 'rich-text' &&
    curComponent.value.editing &&
    state.curFields.length > 0
  )
})

/**
 * 初始化富文本可插入字段列表
 */
const initCurFields = () => {
  if (element.value.component === 'UserView') {
    const chartInfo = canvasViewInfo.value[element.value.id]
    if (chartInfo) {
      state.curFields = []
      if (chartInfo.type === 'rich-text' && chartInfo.curFields) {
        state.curFields = chartInfo.curFields
      }
    }
  }
}

/**
 * 判断当前图表是否展示下载入口
 */
const showDownload = computed(() =>
  canShowDownloadAction(canvasViewInfo.value[element.value.id]?.dataFrom)
)
/**
 * 当前组件绑定的图表视图信息
 */
const currentViewInfo = computed(() => canvasViewInfo.value[element.value.id])
/**
 * 当前图表的 Excel 导出配置
 */
const tableExcelConfig = computed(() =>
  defaultViewExcelExportConfig(currentViewInfo.value?.excelExport || element.value?.excelExport)
)
/**
 * 判断 Excel 导出是否启用
 */
const excelExportEnabled = computed(() => {
  if (!isExportableTableView(currentViewInfo.value)) {
    return true
  }
  return tableExcelConfig.value.enabled
})
/**
 * 根据导出范围生成 Excel 菜单文案
 */
const excelExportLabel = computed(() => {
  if (
    isExportableTableView(currentViewInfo.value) &&
    tableExcelConfig.value.scope === EXCEL_EXPORT_SCOPE.CURRENT_FILTERED
  ) {
    return '导出筛选结果'
  }
  return 'Excel'
})
// 富文本-End

/**
 * 判断数据集参数设置入口是否展示
 */
const datasetParamsSetShow = computed(() => {
  return canvasViewInfo.value[element.value.id]?.calParams?.length > 0
})

/**
 * 更多操作下拉菜单引用
 */
const curDropdown = ref()
/**
 * 关闭更多操作下拉菜单
 */
const closeItem = () => {
  curDropdown.value.handleClose()
}

onMounted(() => {
  if (element.value.component === 'UserView') {
    eventBus.on('initCurFields-' + element.value.id, initCurFields)
  }
  initCurFields()
})

onBeforeUnmount(() => {
  if (element.value.component === 'UserView') {
    eventBus.off('initCurFields-' + element.value.id, initCurFields)
  }
})

defineExpose({
  multiplexingCheckOut,
  batchOptCheckOut
})

// 批量状态关闭后重置当前组件勾选态
watch(
  () => batchOptStatus.value,
  () => {
    state.batchOptCheckModel = false
  }
)
</script>

<style lang="less" scoped>
.bar-main {
  position: absolute;
  float: right;
  z-index: 10;
  border-radius: 2px;
  cursor: pointer !important;
  font-size: 16px !important;
}
.bar-main-background {
  background-color: var(--ed-color-primary, #3b82f6);
}

.bar-main-right {
  width: 24px;
  right: -26px;
}

.bar-main-preview-right-inner {
  right: 0px;
}

.bar-main-right-inner {
  width: 24px;
  right: 0px;
}

.bar-main-left-inner {
  width: 24px;
  left: 0px;
}

.bar-main-left-outer {
  width: 24px;
  left: -26px;
}

.bar-base-icon {
  height: 24px;
  width: 24px;
  font-size: 16px;
  color: #ffffff;
  &:hover {
    color: rgba(255, 255, 255, 0.5);
  }
  &:active {
    color: rgba(255, 255, 255, 0.7);
  }
}

.bar-checkbox-area {
  padding: 0 5px;
  height: 24px;
}
</style>
