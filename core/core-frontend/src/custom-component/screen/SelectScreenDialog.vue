<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { queryTreeApi } from '@/api/visualization/dataVisualization'
import { filterEmptyFolderTree } from '@/utils/canvasUtils'
// 控制资源选择弹窗的显示状态
const dialogVisible = ref(false)
import dvDashboardSpine from '@/assets/svg/dv-dashboard-spine.svg'
import dvFolder from '@/assets/svg/dv-folder.svg'
import { useI18n } from '@/hooks/web/useI18n'
// 处理弹窗关闭事件并同步显示状态
const closeHandler = () => {
  dialogVisible.value = false
}
const { t } = useI18n()
// 声明资源选中确认时向父组件派发的事件
const emits = defineEmits(['selectConfirm'])

// 保存弹窗资源树、当前选中资源和资源类型
const state = reactive<any>({
  panelList: [],
  dvSelectProps: {
    label: 'name',
    children: 'children',
    value: 'id',
    isLeaf: 'leaf',
    disabled: 'disabled'
  },
  curScreenId: null,
  dvType: 'dashboard'
})
// 根据资源类型生成弹窗标题中的资源名称
const canvasTypeName = computed(() =>
  state.dvType === 'dataV' ? t('work_branch.big_data_screen') : t('work_branch.dashboard')
)

// 初始化弹窗参数并加载对应资源树
const init = param => {
  const { dvType, screenId } = param
  state.dvType = dvType
  dialogVisible.value = true
  state.curScreenId = screenId
  loadRTree(dvType)
}

// 加载指定业务类型下的资源树并过滤空文件夹
const loadRTree = dvType => {
  const request = { busiFlag: dvType, resourceTable: 'core' }
  queryTreeApi(request).then((rsp: any) => {
    const panelList = Array.isArray(rsp) ? rsp : []
    if (panelList[0]?.id === '0') {
      state.panelList = panelList[0].children || []
    } else {
      state.panelList = panelList
    }
    state.panelList = filterEmptyFolderTree(state.panelList)
  })
}

// 点击可用叶子节点时更新当前选中的资源
const dvNodeClick = data => {
  if (data.leaf && data.id !== state.curScreenId) {
    state.curScreenId = data.id
  }
}

// 关闭资源选择弹窗
const close = () => {
  dialogVisible.value = false
}

// 确认当前选择并通知父组件
const confirm = () => {
  emits('selectConfirm', state.curScreenId)
  close()
}

defineExpose({
  init
})
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :show-close="true"
    :close-on-click-modal="false"
    :title="t('visualization.select_resource', [canvasTypeName])"
    append-to-body
    @close="closeHandler"
    width="400"
  >
    <el-form-item style="position: relative" prop="rid">
      <el-tree-select
        v-model="state.curScreenId"
        :data="state.panelList"
        :props="state.dvSelectProps"
        :render-after-expand="false"
        filterable
        @node-click="dvNodeClick"
        class="dv-selector"
      >
        <template #default="{ node, data }">
          <div class="label-content-details">
            <el-icon size="18px" style="display: inline-block" v-if="data.leaf">
              <Icon name="dv-dashboard-spine"><dvDashboardSpine class="svg-icon" /></Icon>
            </el-icon>
            <el-icon size="18px" style="display: inline-block" v-else>
              <Icon name="dv-folder"><dvFolder class="svg-icon" /></Icon>
            </el-icon>
            <span style="margin-left: 8px; font-size: 14px" :title="node.label">{{
              node.label
            }}</span>
          </div>
        </template>
      </el-tree-select>
    </el-form-item>
    <template #footer>
      <span class="m-dialog-footer">
        <el-button secondary @click="close">{{ t('commons.close') }}</el-button>
        <el-button type="primary" @click="confirm">{{ t('commons.confirm') }}</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style scoped lang="less">
.m-dialog-footer {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
}
</style>
