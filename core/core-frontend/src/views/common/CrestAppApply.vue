<template>
  <el-drawer
    :title="t('visualization.save_app')"
    v-model="state.appApplyDrawer"
    modal-class="app-drawer"
    :show-close="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    size="500px"
    direction="rtl"
    :z-index="1000"
  >
    <div class="app-export">
      <el-form
        ref="appSaveForm"
        :model="state.form"
        :rules="isDatasourceMatch ? state.ruleDatasource : state.ruleDataset"
        class="crest-form-item app-form create-dialog"
        label-width="180px"
        label-position="top"
      >
        <div class="crest-row-rules" style="margin: 0 0 16px">
          <span>{{ t('visualization.base_info') }}</span>
        </div>
        <el-form-item :label="dvPreName + t('visualization.name')" prop="name">
          <el-input
            v-model="state.form.name"
            autocomplete="off"
            :placeholder="t('visualization.input_tips')"
          />
        </el-form-item>
        <el-form-item :label="dvPreName + t('visualization.position')" prop="pid">
          <el-tree-select
            style="width: 100%"
            @keydown.stop
            @keyup.stop
            v-model="state.form.pid"
            :data="state.dvTree"
            :props="state.propsTree"
            @node-click="dvTreeSelect"
            :render-after-expand="false"
            filterable
          >
            <template #default="{ data: { name } }">
              <span class="custom-tree-node">
                <el-icon>
                  <Icon name="dv-folder"><dvFolder class="svg-icon" /></Icon>
                </el-icon>
                <span :title="name">{{ name }}</span>
              </span>
            </template>
          </el-tree-select>
        </el-form-item>
        <el-form-item :label="t('visualization.data_match_type')" prop="dataType">
          <el-select v-model="state.form.dataType" :placeholder="t('chart.pls_select_field')">
            <el-option key="datasource" :label="t('datasource.datasource')" value="datasource" />
            <el-option key="dataset" :label="t('dataset.datalist')" value="dataset" />
          </el-select>
        </el-form-item>
        <template v-if="isDatasourceMatch">
          <el-form-item :label="t('visualization.ds_group_name')" prop="datasetFolderName">
            <el-input
              v-model="state.form.datasetFolderName"
              autocomplete="off"
              :placeholder="t('visualization.input_tips')"
            />
          </el-form-item>
          <el-form-item :label="t('visualization.ds_group_position')" prop="datasetFolderPid">
            <el-tree-select
              style="width: 100%"
              @keydown.stop
              @keyup.stop
              v-model="state.form.datasetFolderPid"
              :data="state.dsTree"
              :props="state.propsTree"
              @node-click="dsTreeSelect"
              :render-after-expand="false"
              filterable
            >
              <template #default="{ data: { name } }">
                <span class="custom-tree-node">
                  <el-icon>
                    <Icon name="dv-folder"><dvFolder class="svg-icon" /></Icon>
                  </el-icon>
                  <span :title="name">{{ name }}</span>
                </span>
              </template>
            </el-tree-select>
          </el-form-item>
          <div class="crest-row-rules" style="margin: 0 0 16px">
            <span>{{ t('visualization.datasource_info') }}</span>
          </div>
          <el-row class="datasource-link">
            <el-row class="head">
              <el-col :span="11">{{ t('visualization.app_datasource') }}</el-col
              ><el-col :span="2"></el-col
              ><el-col :span="11">{{ t('visualization.sys_datasource') }}</el-col>
            </el-row>
            <el-row
              :key="index"
              class="content"
              v-for="(appDatasource, index) in state.appData.datasourceInfo"
            >
              <el-col :span="11">
                <el-select style="width: 100%" v-model="appDatasource.name" disabled>
                  <el-option
                    :key="appDatasource.name"
                    :label="appDatasource.name"
                    :value="appDatasource.name"
                  >
                  </el-option>
                </el-select> </el-col
              ><el-col :span="2" class="icon-center">
                <Icon name="dv-link-target"
                  ><dvLinkTarget
                    class="svg-icon"
                    style="width: 20px; height: 20px" /></Icon></el-col
              ><el-col :span="11">
                <dataset-select
                  ref="datasetSelector"
                  v-model="appDatasource.systemDatasourceId"
                  style="flex: 1"
                  :state-obj="state"
                  themes="light"
                  source-type="datasource"
                  @add-ds-window="addDatasourceWindow"
                  view-id="0"
                />
              </el-col>
            </el-row>
          </el-row>
        </template>
        <template v-if="!isDatasourceMatch">
          <div class="crest-row-rules" style="margin: 0 0 16px">
            <span>{{ t('visualization.dataset_info') }}</span>
          </div>
          <el-row class="datasource-link">
            <el-row class="head">
              <el-col :span="11">{{ t('visualization.app_dataset') }}</el-col
              ><el-col :span="2"></el-col
              ><el-col :span="11">{{ t('visualization.sys_dataset') }}</el-col>
            </el-row>
            <el-row
              :key="index"
              class="content"
              v-for="(appDataset, index) in state.appData.datasetGroupsInfo"
            >
              <el-col :span="11">
                <el-select style="width: 100%" v-model="appDataset.name" disabled>
                  <el-option
                    :key="appDataset.name"
                    :label="appDataset.name"
                    :value="appDataset.name"
                  >
                  </el-option>
                </el-select> </el-col
              ><el-col :span="2" class="icon-center">
                <Icon name="dv-link-target"
                  ><dvLinkTarget
                    class="svg-icon"
                    style="width: 20px; height: 20px" /></Icon></el-col
              ><el-col :span="11">
                <dataset-select
                  ref="datasetSelector"
                  v-model="appDataset.systemDatasetId"
                  style="flex: 1"
                  :state-obj="state"
                  themes="light"
                  @add-ds-window="addDatasetWindow"
                  view-id="0"
                />
              </el-col>
            </el-row>
          </el-row>
        </template>
      </el-form>
    </div>
    <template #footer>
      <div class="apply" style="width: 100%">
        <el-button v-if="isDesktop() || openType === '_self'" @click="goBack">{{
          t('visualization.back')
        }}</el-button>
        <el-button type="primary" @click="saveApp">{{ t('visualization.save') }}</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script lang="ts" setup>
import dvFolder from '@/assets/svg/dv-folder.svg'
import dvLinkTarget from '@/assets/svg/dv-link-target.svg'
import {
  ElButton,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElTreeSelect
} from 'element-plus-secondary'
import { computed, PropType, reactive, ref, toRefs } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { dvNameCheck, queryTreeApi } from '@/api/visualization/dataVisualization'
import { BusiTreeNode, BusiTreeRequest } from '@/models/tree/TreeNode'
import { datasetTree } from '@/api/dataset'
import DatasetSelect from '@/views/chart/components/editor/dataset-select/DatasetSelect.vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { deepCopy } from '@/utils/utils'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { useCache } from '@/hooks/web/useCache'
import { isDesktop } from '@/utils/ModelUtil'
import { filterFreeFolder } from '@/utils/utils'

const { wsCache } = useCache('localStorage')
const { t } = useI18n()
// 定义应用保存抽屉事件
const emits = defineEmits(['closeDraw', 'saveAppCanvas'])
// 应用保存表单实例
const appSaveForm = ref(null)
const dvMainStore = dvMainStoreWithOut()
const { dvInfo, appData } = storeToRefs(dvMainStore)
const snapshotStore = snapshotStoreWithOut()
// 接收应用保存所需的画布和主题信息
const props = defineProps({
  componentData: {
    type: Object,
    required: true
  },
  canvasViewInfo: {
    type: Object,
    required: true
  },
  curCanvasType: {
    type: String,
    required: true
  },
  themes: {
    type: String as PropType<EditorTheme>,
    default: 'dark'
  }
})

const { curCanvasType } = toRefs(props)
// 后台内嵌打开时复用当前窗口，独立门户进入时新窗口打开数据源或数据集维护页。
const openType = wsCache.get('open-backend') === '1' ? '_self' : '_blank'

// 根据画布类型生成预览名称
const dvPreName = computed(() =>
  curCanvasType.value === 'dashboard'
    ? t('work_branch.dashboard')
    : t('work_branch.big_data_screen')
)
// 判断当前是否按数据源匹配
const isDatasourceMatch = computed(() => state.form.dataType === 'datasource')
// 打开新增数据源窗口
const addDatasourceWindow = () => {
  // 跳转到数据源创建页
  const url = '#/data/datasource?opt=create'
  window.open(url, openType)
}

// 打开新增数据集窗口
const addDatasetWindow = () => {
  // 跳转到数据集创建页
  const url = '#/data/dataset?opt=create'
  window.open(url, openType)
}

// 维护应用保存抽屉、树数据和表单状态
const state = reactive({
  appApplyDrawer: false,
  // 大屏和仪表板目录树，用于选择应用保存后的目标位置。
  dvTree: [],
  // 数据集目录树，仅在按数据源匹配时用于选择自动生成数据集的归属目录。
  dsTree: [],
  propsTree: {
    label: 'name',
    children: 'children',
    isLeaf: node => !node.children?.length
  },
  // 保存模板中的数据源、数据集引用，提交前需要映射到当前系统内的资源。
  appData: {
    datasourceInfo: [],
    datasetGroupsInfo: []
  },
  form: {
    pid: '',
    name: t('visualization.new'),
    datasetFolderPid: null,
    datasetFolderName: null,
    dataType: 'datasource'
  },
  ruleDataset: {
    name: [
      {
        required: true,
        min: 2,
        max: 25,
        message: t('datasource.input_limit_2_25', [2, 25]),
        trigger: 'blur'
      }
    ],
    pid: [
      {
        required: true,
        message: t('visualization.select_folder'),
        trigger: 'blur'
      }
    ],
    dataType: [
      {
        required: true
      }
    ]
  },
  ruleDatasource: {
    name: [
      {
        required: true,
        min: 2,
        max: 25,
        message: t('datasource.input_limit_2_25', [2, 25]),
        trigger: 'blur'
      }
    ],
    pid: [
      {
        required: true,
        message: t('visualization.select_folder'),
        trigger: 'blur'
      }
    ],
    datasetFolderName: [
      {
        required: true,
        min: 2,
        max: 25,
        message: t('datasource.input_limit_2_25', [2, 25]),
        trigger: 'blur'
      }
    ],
    datasetFolderPid: [
      {
        required: true,
        message: t('visualization.select_ds_group_folder'),
        trigger: 'blur'
      }
    ],
    dataType: [
      {
        required: true
      }
    ]
  }
})

// 返回上一页
const goBack = () => {
  window.history.back()
}

// 初始化可选仪表板目录和数据集目录
const initData = () => {
  const request = { busiFlag: curCanvasType.value, resourceTable: 'core', leaf: false, weight: 7 }
  queryTreeApi(request).then(res => {
    // 保存应用只允许选择业务目录，过滤掉无归属或不可保存的虚拟目录。
    filterFreeFolder(res, curCanvasType.value)
    const resultTree = res || []
    dfs(resultTree as unknown as BusiTreeNode[])
    state.dvTree = (resultTree as unknown as BusiTreeNode[]) || []
    if (state.dvTree.length && state.dvTree[0].name === 'root' && state.dvTree[0].id === '0') {
      state.dvTree[0].name =
        curCanvasType.value === 'dataV'
          ? t('work_branch.big_data_screen')
          : t('work_branch.dashboard')
    }
  })

  const requestDs = { leaf: false, weight: 7 } as BusiTreeRequest
  datasetTree(requestDs).then(res => {
    // 数据源匹配会自动生成数据集，目录树同样只保留可落库的业务目录。
    filterFreeFolder(res, 'dataset')
    dfs(res as unknown as BusiTreeNode[])
    state.dsTree = (res as unknown as BusiTreeNode[]) || []
    if (state.dsTree.length && state.dsTree[0].name === 'root' && state.dsTree[0].id === '0') {
      state.dsTree[0].name = t('visualization.dataset')
    }
  })
}

// 为业务树节点补充选择器 value
const dfs = (arr: BusiTreeNode[]) => {
  arr.forEach(ele => {
    ele['value'] = ele.id
    if (ele.children?.length) {
      dfs(ele.children)
    }
  })
}

// 打开应用保存抽屉并装载当前应用数据
const init = params => {
  state.appApplyDrawer = true
  state.form = params.base
  // 默认按数据源匹配，保留模板数据集结构并降低用户逐个选择数据集的成本。
  state.form.dataType = 'datasource'
  state.appData.datasourceInfo = deepCopy(appData.value?.datasourceInfo)
  state.appData.datasetGroupsInfo = deepCopy(appData.value?.datasetGroupsInfo)
  initData()
}

// 记录画布目录选择结果
const dvTreeSelect = element => {
  state.form.pid = element.id
}

// 记录数据集目录选择结果
const dsTreeSelect = element => {
  state.form.datasetFolderPid = element.id
}

// 关闭应用保存抽屉
const close = () => {
  emits('closeDraw')
  snapshotStore.recordSnapshotCache('renderChart')
  state.appApplyDrawer = false
}

// 校验数据匹配关系并保存为应用
const saveApp = () => {
  let datasourceMatchReady = true
  let datasetMatchReady = true
  // 数据源匹配要求模板内每个数据源都有当前系统中的目标数据源。
  state.appData.datasourceInfo.forEach(datasource => {
    if (!datasource.systemDatasourceId) {
      datasourceMatchReady = false
    }
  })

  // 数据集匹配要求模板内每个数据集分组都有当前系统中的目标数据集。
  state.appData.datasetGroupsInfo.forEach(dataset => {
    if (!dataset.systemDatasetId) {
      datasetMatchReady = false
    }
  })
  if (!datasourceMatchReady && isDatasourceMatch.value) {
    ElMessage.error(t('visualization.app_no_datasource_tips'))
    return
  }

  if (!datasetMatchReady && !isDatasourceMatch.value) {
    ElMessage.error(t('visualization.app_no_dataset_tips'))
    return
  }
  appSaveForm.value?.validate(async valid => {
    if (valid) {
      // 还原数据源和数据集匹配关系
      appData.value['datasourceInfo'] = state.appData.datasourceInfo
      appData.value['datasetGroupsInfo'] = state.appData.datasetGroupsInfo
      dvInfo.value['pid'] = state.form.pid
      dvInfo.value['name'] = state.form.name
      dvInfo.value['datasetFolderPid'] = state.form.datasetFolderPid
      dvInfo.value['datasetFolderName'] = state.form.datasetFolderName
      dvInfo.value['dataType'] = state.form.dataType
      dvInfo.value['dataState'] = 'ready'
      await dvNameCheck({
        opt: 'newLeaf',
        nodeType: 'leaf',
        name: dvInfo.value['name'],
        type: curCanvasType.value,
        pid: dvInfo.value['pid']
      })
      snapshotStore.recordSnapshotCache('renderChart')

      emits('saveAppCanvas')
    } else {
      return false
    }
  })
}

defineExpose({
  init,
  close
})
</script>
<style lang="less" scoped>
.app-export {
  width: 100%;
  height: calc(100% - 56px);
}

.app-export-bottom {
  width: 100%;
  height: 56px;
  text-align: right;
}

:deep(.ed-drawer__body) {
  padding-bottom: 0 !important;
}

.crest-row-rules {
  display: flex;
  align-items: center;
  position: relative;
  font-size: 14px;
  font-weight: 500;
  line-height: 22px;
  padding-left: 10px;
  margin: 24px 0 16px 0;
  color: var(--ed-text-color-regular);

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%);
    height: 14px;
    width: 2px;
    background: var(--ed-color-primary, #3b82f6);
  }
}

.custom-tree-node {
  display: flex;
  align-items: center;
  span {
    margin-left: 8.75px;
    width: 120px;
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
  }
}
.datasource-link {
  color: var(--ed-text-color-regular);
  font-size: 12px;
  font-weight: 500;
  width: 100%;
  .head_type {
    width: 100%;
    margin-bottom: 16px;
  }
  .head {
    width: 100%;
  }
  .content {
    width: 100%;
    margin-top: 8px;
  }
}

.icon-center {
  padding: 0 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.app-form {
  padding-bottom: 95px;
}
</style>

<style lang="less">
.app-drawer {
  z-index: 1000;
}
</style>
