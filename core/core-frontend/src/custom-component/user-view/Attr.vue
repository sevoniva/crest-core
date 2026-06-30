<script setup lang="ts">
import CommonAttr from '@/custom-component/common/CommonAttr.vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { computed, toRefs, watch } from 'vue'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import {
  EXCEL_EXPORT_SCOPE,
  defaultViewExcelExportConfig,
  isExportableTableView
} from '@/utils/visualization/filteredExcelExport.mjs'
// 定义用户视图属性面板接收的主题参数
const props = defineProps({
  themes: {
    type: String,
    default: 'dark'
  }
})

const { themes } = toRefs(props)
const dvMainStore = dvMainStoreWithOut()
const snapshotStore = snapshotStoreWithOut()
const { curComponent, canvasViewInfo } = storeToRefs(dvMainStore)

// 获取当前组件对应的视图配置
const currentViewInfo = computed(() => canvasViewInfo.value[curComponent.value?.id])
// 判断当前视图是否展示 Excel 导出配置
const excelExportShow = computed(() => isExportableTableView(currentViewInfo.value))
// 初始化当前视图的 Excel 导出配置
const initExcelExportConfig = () => {
  if (!curComponent.value) {
    return
  }
  curComponent.value.excelExport = defaultViewExcelExportConfig(
    currentViewInfo.value?.excelExport || curComponent.value.excelExport
  )
  if (currentViewInfo.value) {
    currentViewInfo.value.excelExport = curComponent.value.excelExport
  }
}

// 计算当前组件使用的 Excel 导出配置
const excelExport = computed(
  () => curComponent.value?.excelExport || defaultViewExcelExportConfig()
)

// 同步 Excel 导出配置并记录快照
const onExcelExportChange = () => {
  if (currentViewInfo.value) {
    currentViewInfo.value.excelExport = curComponent.value.excelExport
  }
  snapshotStore.recordSnapshotCache('excelExport')
}

// 监听当前组件切换并初始化导出配置
watch(
  () => curComponent.value?.id,
  () => initExcelExportConfig(),
  { immediate: true }
)

// 监听视图导出配置变化并同步到当前组件
watch(
  () => currentViewInfo.value?.excelExport,
  value => {
    if (curComponent.value && value) {
      curComponent.value.excelExport = defaultViewExcelExportConfig(value)
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="attr-list">
    <CommonAttr :themes="themes" :element="curComponent">
      <el-collapse-item
        v-if="excelExportShow"
        :effect="themes"
        title="Excel导出"
        name="excelExport"
        class="common-style-area"
      >
        <el-form-item label="启用导出" :class="'form-item-' + themes">
          <el-switch v-model="excelExport.enabled" @change="onExcelExportChange" />
        </el-form-item>
        <el-form-item label="导出范围" :class="'form-item-' + themes">
          <el-radio-group v-model="excelExport.scope" @change="onExcelExportChange">
            <el-radio :effect="themes" :label="EXCEL_EXPORT_SCOPE.CURRENT_FILTERED">
              当前筛选结果
            </el-radio>
            <el-radio :effect="themes" :label="EXCEL_EXPORT_SCOPE.ALL">全部数据</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-collapse-item>
    </CommonAttr>
  </div>
</template>
