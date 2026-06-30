<script lang="ts" setup>
import dvFolder from '@/assets/svg/dv-folder.svg'
import icon_searchOutline_outlined from '@/assets/svg/icon_search-outline_outlined.svg'
import { ref, reactive, computed, watch, nextTick, unref } from 'vue'
import treeSort from '@/utils/treeSortUtils'
import { useCache } from '@/hooks/web/useCache'
import { ElMessage } from 'element-plus-secondary'
import { cloneDeep } from 'lodash-es'
import { useI18n } from '@/hooks/web/useI18n'
import { datasetTree, moveDatasetTree, createDatasetTree, renameDatasetTree } from '@/api/dataset'
import type { DatasetOrFolder } from '@/api/dataset'
import nothingTree from '@/assets/img/nothing-tree.png'
import { BusiTreeRequest } from '@/models/tree/TreeNode'
import { filterFreeFolder } from '@/utils/utils'
// 数据集目录树节点结构，弹窗保存时会复用其中的联合数据集和字段信息
export interface Tree {
  isCross: boolean
  name: string
  value?: string | number
  id: string | number
  nodeType: string
  createBy?: string
  level: number
  leaf?: boolean
  pid: string | number
  mode?: number
  union?: Array<{}>
  createTime: number
  allfields?: Array<{}>
  children?: Tree[]
}
const { t } = useI18n()
const { wsCache } = useCache()
// 弹窗目录树和同级名称缓存，移动和创建时作为校验上下文
const state = reactive({
  tData: [],
  nameList: []
})

// 名称输入占位文案，随目录或数据集类型切换
const placeholder = ref('')
// 当前操作的节点类型，区分目录和数据集保存参数
const nodeType = ref()
// 当前父目录编号，创建、移动和重命名流程共用
const pid = ref()
// 当前节点编号，仅在移动或重命名已有节点时使用
const id = ref()
// 当前命令类型，空值表示创建，move 和 rename 表示已有节点操作
const cmd = ref('')
// 移动目录树实例，用于过滤和空态读取
const treeRef = ref()
// 移动目录时的搜索关键字
const filterText = ref('')
// 联合数据集配置由上一步表单暂存，目录选择完成后随数据集一起保存
let union = []
// 数据集字段配置由上一步表单暂存，避免切换目录时丢失字段定义
let allfields = []
// 跨源标识随数据集保存，后端根据它区分 SQL 生成策略
let isCross = false
// 数据集模式随保存参数传递，兼容普通、SQL 和联合数据集
let mode = 0
// 弹窗表单模型，保存目标父级和名称
const datasetForm = reactive({
  pid: '',
  name: ''
})
// 搜索后目录树是否为空，用于展示自定义空态
const searchEmpty = ref(false)

// 目录树过滤函数，同时同步树组件的空态
const filterNode = (value: string, data: Tree) => {
  nextTick(() => {
    searchEmpty.value = treeRef.value.isEmpty
  })
  if (!value) return true
  return data.name.includes(value)
}

// 搜索关键字变化后过滤目录树，并重绘命中文案高亮
watch(filterText, val => {
  showAll.value = !val
  treeRef.value.filter(val)
  nextTick(() => {
    document.querySelectorAll('.node-text').forEach(ele => {
      renderHighlightedTreeText(ele, val)
    })
  })
})

// 用文本节点拼接搜索高亮，避免目录名称被当作 HTML 注入
const renderHighlightedTreeText = (ele: Element, keyword: string) => {
  const content = ele.getAttribute('title') || ''
  ele.textContent = ''
  if (!keyword) {
    ele.textContent = content
    return
  }
  let offset = 0
  let cursor = content.indexOf(keyword, offset)
  while (cursor !== -1) {
    ele.appendChild(document.createTextNode(content.slice(offset, cursor)))
    const mark = document.createElement('span')
    mark.className = 'highLight'
    mark.textContent = keyword
    ele.appendChild(mark)
    offset = cursor + keyword.length
    cursor = content.indexOf(keyword, offset)
  }
  ele.appendChild(document.createTextNode(content.slice(offset)))
}

// 创建到已有父级时展示父目录选择，移动和重命名由独立流程处理
const showPid = computed(() => {
  if (nodeType.value === 'folder' && !!pid.value) {
    return false
  }
  return !['rename', 'move'].includes(cmd.value) && !!pid.value
})

// 名称字段标签随节点类型切换
const labelName = computed(() => {
  return nodeType.value === 'folder' ? t('datasetUi.folder_name') : t('dataset.name')
})

// 弹窗标题由节点类型和命令类型共同决定，移动与重命名优先
const dialogTitle = computed(() => {
  let title = ''

  switch (nodeType.value) {
    case 'folder':
      title = t('datasetUi.new_folder')
      break
    case 'dataset':
      title = t('common.save') + t('auth.dataset')
      break
    default:
      break
  }
  switch (cmd.value) {
    case 'move':
      title = t('chart.move_to')
      break
    case 'rename':
      title = t('chart.rename')
      break
    default:
      break
  }
  return title
})

// 移动操作只选择目标目录，不允许同时修改名称
const showName = computed(() => {
  return cmd.value !== 'move'
})

// 表单校验规则在每次初始化时按当前类型动态生成
const datasetFormRules = ref()
// 根目录选择标记，根目录没有常规父级编号
const activeAll = ref(false)
// 目录树全量展示状态，搜索时关闭全量模式
const showAll = ref(true)
// 表单实例引用，用于校验、重置和清理校验提示
const dataset = ref()
// 保存中状态，防止重复提交
const loading = ref(false)
// 弹窗显隐状态
const createDataset = ref(false)
// tree-select 的过滤函数，按目录名称进行本地匹配
const filterMethod = (value, data) => data.name.includes(value)
// 关闭弹窗时只收起视图，下一次打开由 createInit 重置表单上下文
const resetForm = () => {
  createDataset.value = false
}

// 为树选择组件补齐 value 字段，并递归处理所有子目录
const dfs = (arr: Tree[]) => {
  arr?.forEach(ele => {
    ele.value = ele.id
    if (ele.children?.length) {
      dfs(ele.children)
    }
  })
}
// 兼容后端未返回 root 节点的目录树，避免默认根目录编号无法命中
const formatRootMiss = (id: string | number, treeData: Tree[]) => {
  if (!treeData?.length) {
    return ''
  }
  if (id === '0' && treeData[0].id !== '0') {
    return treeData[0].id
  }
  return id
}
// 原始目录树缓存，切换排序时以它作为稳定数据源
const originResourceTree = ref([])
// 目录排序枚举必须和缓存值保持一致
const sortList = ['time_asc', 'time_desc', 'name_asc', 'name_desc']
// 读取旧版排序索引缓存，非法值回退到时间倒序
const getDefaultSortType = () => {
  const sortIndex = Number(wsCache.get('TreeSort-backend') ?? 1)
  return sortList[Number.isInteger(sortIndex) && sortList[sortIndex] ? sortIndex : 1]
}
// 读取指定目录树排序缓存，缓存缺失或非法时使用兜底排序
const getStoredSortType = (cacheKey: string, fallback: string) => {
  const sortType = wsCache.get(cacheKey)
  return sortList.includes(sortType) ? sortType : fallback
}
// 初始化创建、移动或重命名弹窗，并按当前节点加载可选父目录树
const createInit = (type, data: Tree, exec, name: string) => {
  pid.value = ''
  id.value = ''
  cmd.value = ''
  datasetForm.pid = ''
  datasetForm.name = ''
  filterText.value = ''
  nodeType.value = type
  placeholder.value =
    type === 'folder' ? t('data_set.a_folder_name') : t('data_set.the_dataset_name')
  if (type === 'dataset') {
    union = data.union
    allfields = data.allfields
    isCross = data.isCross
    mode = data.mode || 0
  }
  if (data.id) {
    const request = { leaf: false, weight: 7 } as BusiTreeRequest
    datasetTree(request).then(res => {
      filterFreeFolder(res, 'dataset')
      dfs(res as unknown as Tree[])
      state.tData = (res as unknown as Tree[]) || []
      const curSortType = getStoredSortType('TreeSort-dataset', getDefaultSortType())
      originResourceTree.value = cloneDeep(unref(state.tData))
      state.tData = treeSort(originResourceTree.value, curSortType)
      if (state.tData.length && state.tData[0].name === 'root' && state.tData[0].id === '0') {
        state.tData[0].name = t('data_set.data_set')
      }
      data.id = formatRootMiss(data.id, state.tData)
      if (exec) {
        pid.value = data.pid
        id.value = data.id
        datasetForm.pid = data.pid as string
        datasetForm.name = data.name
      } else {
        datasetForm.pid = data.id as string
        pid.value = data.id
      }
    })

    cmd.value = exec
  }
  name && (datasetForm.name = name)
  createDataset.value = true
  datasetFormRules.value = {
    name: [
      {
        required: true,
        message: placeholder.value,
        trigger: 'change'
      },
      {
        required: true,
        message: placeholder.value,
        trigger: 'blur'
      },
      {
        min: 1,
        max: 64,
        message: t('datasource.input_limit_1_64', [1, 64]),
        trigger: 'blur'
      }
    ],
    pid: [
      {
        required: true,
        message: t('common.please_select'),
        trigger: 'blur'
      }
    ]
  }
  setTimeout(() => {
    dataset.value.clearValidate()
  }, 50)
}

// 编辑入口只回填节点和父级，命令细节由 createInit 统一处理
const editeInit = (param: Tree) => {
  pid.value = param.pid
  id.value = param.id
}

// 目录树字段映射，el-tree 和 tree-select 共用同一套结构
const props = {
  label: 'name',
  children: 'children',
  isLeaf: node => !node.children?.length
}

// 点击目录节点后取消根目录选择，并写回目标父级
const nodeClick = (data: Tree) => {
  activeAll.value = false
  datasetForm.pid = data.id as string
}
// 移动操作必须选择有效目标目录，根目录 0 是合法特殊值
const checkPid = pid => {
  if (pid !== 0 && !pid) {
    ElMessage.error(t('data_set.the_destination_folder'))
    return false
  }
  return true
}
// 保存入口统一处理目录创建、数据集保存、移动和重命名
const saveDataset = () => {
  dataset.value.validate(result => {
    if (result) {
      const params: DatasetOrFolder = {
        nodeType: nodeType.value as 'folder' | 'dataset',
        name: datasetForm.name
      }

      switch (cmd.value) {
        case 'move':
          params.pid = activeAll.value ? '0' : (datasetForm.pid as string)
          params.id = id.value
          break
        case 'rename':
          params.pid = pid.value as string
          params.id = id.value
          break
        default:
          params.pid = datasetForm.pid || pid.value || '0'
          break
      }
      if (nodeType.value === 'dataset') {
        params.union = union
        params.allFields = allfields
        params.isCross = isCross
        params.mode = mode
      }
      if (cmd.value === 'move' && !checkPid(params.pid)) {
        return
      }
      loading.value = true
      const req =
        cmd.value === 'move' ? moveDatasetTree : params.id ? renameDatasetTree : createDatasetTree
      req(params)
        .then(res => {
          dataset.value.resetFields()
          createDataset.value = false
          emits('finish', res)
          switch (cmd.value) {
            case 'move':
              ElMessage.success(t('data_set.moved_successfully'))
              break
            case 'rename':
              ElMessage.success(t('data_set.rename_successful'))
              break
            default:
              emits('onDatasetSave')
              ElMessage.success(t('common.save_success'))
              break
          }
        })
        .finally(() => {
          loading.value = false
        })
    }
  })
}

defineExpose({
  createInit,
  editeInit
})

// 向父组件声明保存完成和新数据集保存后的刷新事件
const emits = defineEmits(['finish', 'onDatasetSave'])
</script>

<template>
  <el-dialog
    :title="dialogTitle"
    v-model="createDataset"
    class="create-dialog"
    :width="cmd === 'move' ? '600px' : '420px'"
    :before-close="resetForm"
  >
    <el-form
      label-position="top"
      require-asterisk-position="right"
      ref="dataset"
      @keydown.stop.prevent.enter
      :model="datasetForm"
      :rules="datasetFormRules"
    >
      <el-form-item v-if="showName" :label="labelName" prop="name">
        <el-input :placeholder="placeholder" v-model="datasetForm.name" />
      </el-form-item>
      <el-form-item v-if="showPid" :label="t('datasetUi.folder')" prop="pid">
        <el-tree-select
          v-model="datasetForm.pid"
          :data="state.tData"
          popper-class="dataset-tree-select"
          :render-after-expand="false"
          style="width: 100%"
          :props="props"
          @node-click="nodeClick"
          :filter-node-method="filterMethod"
          filterable
        >
          <template #default="{ data: { name } }">
            <el-icon>
              <Icon name="dv-folder"><dvFolder class="svg-icon" /></Icon>
            </el-icon>
            <span :title="name">{{ name }}</span>
          </template>
        </el-tree-select>
      </el-form-item>
      <div v-if="cmd === 'move'">
        <el-input style="margin-bottom: 12px" v-model="filterText" clearable>
          <template #prefix>
            <el-icon>
              <Icon name="icon_search-outline_outlined"
                ><icon_searchOutline_outlined class="svg-icon"
              /></Icon>
            </el-icon>
          </template>
        </el-input>
        <div class="tree-content">
          <el-tree
            ref="treeRef"
            :filter-node-method="filterNode"
            filterable
            v-model="datasetForm.pid"
            menu
            empty-text=""
            :data="state.tData"
            :props="props"
            @node-click="nodeClick"
          >
            <template #default="{ data }">
              <span class="custom-tree-node">
                <el-icon style="font-size: 18px">
                  <Icon name="dv-folder"><dvFolder class="svg-icon" /></Icon>
                </el-icon>
                <span class="node-text" :title="data.name">{{ data.name }}</span>
              </span>
            </template>
          </el-tree>
          <div v-if="searchEmpty" class="empty-search">
            <img :src="nothingTree" />
            <span>{{ t('data_set.relevant_content_found') }}</span>
          </div>
        </div>
      </div>
    </el-form>
    <template #footer>
      <el-button secondary @click="resetForm">{{ t('dataset.cancel') }} </el-button>
      <el-button v-loading="loading" type="primary" @click="saveDataset"
        >{{ t('dataset.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style lang="less" scoped>
.tree-content {
  width: 552px;
  height: 380px;
  border: 1px solid #dee0e3;
  border-radius: 6px;
  padding: 8px;
  overflow-y: auto;
  .custom-tree-node {
    display: flex;
    align-items: center;
    .node-text {
      margin-left: 8.75px;
      width: 120px;
      white-space: nowrap;
      text-overflow: ellipsis;
      overflow: hidden;
      :deep(.highLight) {
        color: var(--el-color-primary, #3b82f6);
      }
    }
  }

  .empty-search {
    width: 100%;
    margin-top: 57px;
    display: flex;
    flex-direction: column;
    align-items: center;
    img {
      width: 100px;
      height: 100px;
      margin-bottom: 8px;
    }
    span {
      font-family: var(--crest-custom_font, 'PingFang');
      font-size: 14px;
      font-weight: 400;
      line-height: 22px;
      color: #646a73;
    }
  }
}
</style>
<style lang="less">
.dataset-tree-select {
  .ed-select-dropdown__item {
    display: flex;
    align-items: center;
    .ed-icon {
      margin-right: 5px;
    }
  }
}
</style>
