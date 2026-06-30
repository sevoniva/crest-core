<script lang="ts" setup>
import dvFolder from '@/assets/svg/dv-folder.svg'
import icon_searchOutline_outlined from '@/assets/svg/icon_search-outline_outlined.svg'
import { ref, reactive, computed, watch, nextTick, shallowRef, unref } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { checkRepeat, listDatasources, save, update } from '@/api/datasource'
import { ElMessage, ElMessageBox, ElMessageBoxOptions } from 'element-plus-secondary'
import treeSort from '@/utils/treeSortUtils'
import type { DatasetOrFolder } from '@/api/dataset'
import { cloneDeep } from 'lodash-es'
import nothingTree from '@/assets/img/nothing-tree.png'
import { useCache } from '@/hooks/web/useCache'
import { filterFreeFolder } from '@/utils/utils'
// 数据源目录树节点在弹窗中的展示字段和保存上下文
export interface Tree {
  name: string
  value?: string | number
  id: string | number
  nodeType: string
  createBy?: string
  level: number
  leaf?: boolean
  pid: string | number
  type?: string
  createTime: number
  children?: Tree[]
  request: any
}
const { t } = useI18n()
const { wsCache } = useCache()

// 弹窗可选目录树，移动和新建时作为父级选择数据源
const state = reactive({
  tData: []
})

// 当前操作对象类型，区分目录和数据源保存路径
const nodeType = ref()
// 当前父目录编号，移动、重命名和创建时共同复用
const pid = ref()
// 当前节点编号，仅在移动或重命名已有节点时使用
const id = ref()
// 重命名前的名称，用于识别无变化提交并直接关闭弹窗
const oldName = ref()
// 当前命令类型，空值表示创建，move 和 rename 表示已有节点操作
const cmd = ref('')
// 目录树实例，负责过滤和空态读取
const treeRef = ref()
// 移动目录时的搜索关键字
const filterText = ref('')
// 弹窗表单模型，保存目标父级和新名称
const datasetForm = reactive({
  pid: '',
  name: ''
})
// 目录搜索空态，依赖树组件过滤后的结果状态
const searchEmpty = ref(false)

// 目录树过滤函数，同时同步空态用于展示无结果提示
const filterNode = (value: string, data: Tree) => {
  nextTick(() => {
    searchEmpty.value = treeRef.value.isEmpty
  })
  if (!value) return true
  return data.name.includes(value)
}

// 搜索关键字变化后刷新树过滤，并重绘命中文案的高亮片段
watch(filterText, val => {
  showAll.value = !val
  treeRef.value.filter(val)
  nextTick(() => {
    document.querySelectorAll('.node-text').forEach(ele => {
      renderHighlightedTreeText(ele, val)
    })
  })
})

// 用安全的 DOM 节点拼接实现高亮，避免把目录名称作为 HTML 写入
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

// 只有创建到已有父级时展示父目录选择，移动和重命名由独立流程处理
const showPid = computed(() => {
  if (nodeType.value === 'folder' && !!pid.value) {
    return false
  }
  return !['rename', 'move'].includes(cmd.value) && !!pid.value
})

// 名称字段的标签随操作对象变化，避免目录和数据源共用含糊文案
const labelName = computed(() => {
  return nodeType.value === 'folder'
    ? t('datasetUi.folder_name')
    : t('data_source.data_source_name')
})

// 弹窗标题由对象类型和命令类型共同决定，移动、重命名优先级更高
const dialogTitle = computed(() => {
  let title = ''
  switch (nodeType.value) {
    case 'folder':
      title = t('datasetUi.new_folder')
      break
    case 'datasource':
      title = t('datasetUi.create') + t('auth.datasource')
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

// 移动操作只选择目标目录，不允许在同一弹窗里修改名称
const showName = computed(() => {
  return cmd.value !== 'move'
})

// 名称输入占位文案随目录或数据源类型切换
const placeholder = ref('')
// 表单校验规则在初始化时按当前操作动态生成
const datasetFormRules = ref()
// 标记是否移动到根目录，根目录没有常规父级编号
const activeAll = ref(false)
// 控制目录树是否展示完整数据，搜索时关闭全量状态
const showAll = ref(true)
// 表单实例引用，用于校验、重置和清理校验提示
const datasource = ref()
// 保存中状态，防止重复提交和关闭过程中的二次请求
const loading = ref(false)
// 弹窗显隐状态，由外部 createInit 或 editeInit 驱动
const createDataset = ref(false)
// tree-select 的过滤函数，保持与目录树搜索入口一致的名称匹配规则
const filterMethod = (value, data) => {
  if (!data) return false
  data.name.includes(value)
}
// 关闭弹窗时只重置显隐，表单字段由下一次初始化重新灌入
const resetForm = () => {
  createDataset.value = false
}
// 未排序的原始目录树，切换排序时以它作为稳定数据源
const originResourceTree = shallowRef([])

// 按当前排序方式刷新目录树，避免直接修改原始树缓存
const sortTypeChange = sortType => {
  state.tData = treeSort(originResourceTree.value, sortType)
}
// 为树选择组件补齐 value 字段，并递归处理所有子目录
const dfs = (arr: Tree[]) => {
  arr.forEach(ele => {
    ele.value = ele.id
    if (ele.children?.length) {
      dfs(ele.children)
    }
  })
}
// 数据源创建时暂存上一步配置，用于在选择目录后继续保存完整数据源
let request = null
// 数据源类型传给父组件，便于父级区分 API、Excel 等后续流程
let dsType = ''
// 目录排序枚举必须与缓存中的排序索引保持一致
const sortList = ['time_asc', 'time_desc', 'name_asc', 'name_desc']
// 读取旧版索引缓存并转换为排序枚举，异常值回退到时间倒序
const getDefaultSortType = () => {
  const sortIndex = Number(wsCache.get('TreeSort-backend') ?? 1)
  return sortList[Number.isInteger(sortIndex) && sortList[sortIndex] ? sortIndex : 1]
}
// 读取指定目录树排序缓存，缓存缺失或非法时使用传入兜底值
const getStoredSortType = (cacheKey: string, fallback: string) => {
  const sortType = wsCache.get(cacheKey)
  return sortList.includes(sortType) ? sortType : fallback
}
// 初始化创建、移动、重命名弹窗，并按当前节点拉取可选父目录树
const createInit = (type, data: Tree, exec, name: string) => {
  pid.value = ''
  id.value = ''
  cmd.value = ''
  datasetForm.pid = ''
  datasetForm.name = ''
  nodeType.value = type
  filterText.value = ''
  placeholder.value =
    type === 'folder'
      ? t('data_source.a_folder_name')
      : t('data_source.data_source_name_placeholder')
  dsType = data.type
  if (type === 'datasource') {
    request = data.request
  }
  if (data.id) {
    if (exec !== 'rename') {
      listDatasources({ leaf: false, id: data.id, weight: 7 }).then(res => {
        filterFreeFolder(res, 'datasource')
        dfs(res as unknown as Tree[])
        state.tData = (res as unknown as Tree[]) || []
        if (state.tData.length && state.tData[0].name === 'root' && state.tData[0].id === '0') {
          state.tData[0].name = t('data_source.data_source')
        }
        originResourceTree.value = cloneDeep(unref(state.tData))
        const curSortType = getStoredSortType('TreeSort-datasource', getDefaultSortType())
        sortTypeChange(curSortType)
      })
    }
    if (exec) {
      pid.value = data.pid
      id.value = data.id
      datasetForm.pid = data.pid as string
      datasetForm.name = data.name
      oldName.value = data.name
    } else {
      datasetForm.pid = data.id as string
      pid.value = data.id
    }
  }
  cmd.value = data.id ? exec : ''
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
    datasource.value.clearValidate()
  }, 50)
}

// 编辑入口只回填节点和父级，后续命令由 createInit 统一处理
const editeInit = (param: Tree) => {
  pid.value = param.pid
  id.value = param.id
}

// 目录树字段映射保持在脚本侧，模板和 tree-select 共用同一套契约
const props = {
  label: 'name',
  children: 'children',
  isLeaf: node => !node.children?.length
}

// 点击目标目录后取消根目录选择，并把当前节点写回表单父级
const nodeClick = (data: Tree) => {
  activeAll.value = false
  datasetForm.pid = data.id as string
}

// 保存成功后通知外层刷新，并清理本弹窗持有的表单和数据源暂存
const successCb = () => {
  wsCache.set('ds-new-success', true)
  datasource.value.resetFields()
  request = null
  datasetForm.pid = ''
  datasetForm.name = ''
  createDataset.value = false
}

// 传给父组件的结束回调，统一释放保存锁
const finallyCb = () => {
  loading.value = false
}
// 移动操作必须选择有效目标目录，根目录用 0 作为合法特殊值
const checkPid = pid => {
  if (pid !== 0 && !pid) {
    ElMessage.error(t('data_source.the_destination_folder'))
    return false
  }
  return true
}
// 保存入口统一处理文件夹操作和数据源创建，最后把结果交给接口或父组件
const saveDataset = () => {
  datasource.value.validate(result => {
    if (result) {
      const params: Omit<DatasetOrFolder, 'nodeType'> & { nodeType: 'folder' | 'datasource' } = {
        nodeType: nodeType.value as 'folder' | 'datasource',
        name: datasetForm.name.trim()
      }
      switch (cmd.value) {
        case 'move':
          params.pid = activeAll.value ? '0' : (datasetForm.pid as string)
          params.id = id.value
          params.action = 'move'
          break
        case 'rename':
          params.pid = pid.value as string
          params.id = id.value
          params.action = 'rename'
          break
        default:
          params.pid = datasetForm.pid || pid.value || '0'
          params.action = 'create'
          break
      }
      if (cmd.value === 'rename' && oldName.value === params.name) {
        successCb()
        return
      }
      if (cmd.value === 'move' && !checkPid(params.pid)) {
        return
      }
      if (loading.value) {
        return
      }
      loading.value = true
      if (request) {
        let options = {
          confirmButtonType: 'danger',
          type: 'warning',
          autofocus: false,
          showClose: false,
          tip: ''
        }
        request.apiConfiguration = ''
        checkRepeat(request).then(res => {
          let method = request.id === '' ? save : update
          if (!request.type.startsWith('API') && request.type !== 'ExcelRemote') {
            request.syncSetting = null
          }
          if (res) {
            ElMessageBox.confirm(t('datasource.has_same_ds'), options as ElMessageBoxOptions)
              .then(() => {
                method({ ...request, name: datasetForm.name, pid: params.pid })
                  .then(res => {
                    if (res !== undefined) {
                      wsCache.set('ds-new-success', true)
                      emits('handleShowFinishPage', { ...res, pid: params.pid })
                      ElMessage.success(t('data_source.source_saved_successfully'))
                      successCb()
                    }
                  })
                  .finally(() => {
                    loading.value = false
                  })
              })
              .catch(() => {
                loading.value = false
                createDataset.value = false
              })
          } else {
            method({ ...request, name: datasetForm.name, pid: params.pid })
              .then(res => {
                if (res !== undefined) {
                  wsCache.set('ds-new-success', true)
                  emits('handleShowFinishPage', { ...res, pid: params.pid })
                  ElMessage.success(t('data_source.source_saved_successfully'))
                  successCb()
                }
              })
              .finally(() => {
                loading.value = false
              })
          }
        })
        return
      }
      emits('finish', params, successCb, finallyCb, cmd.value, dsType)
    }
  })
}

defineExpose({
  createInit,
  editeInit
})

// 向父组件声明保存完成和数据源完成页跳转事件
const emits = defineEmits(['finish', 'handleShowFinishPage'])
</script>

<template>
  <el-dialog
    v-loading="loading"
    :title="dialogTitle"
    v-model="createDataset"
    :width="cmd === 'move' ? '600px' : '420px'"
    class="create-dialog"
    :before-close="resetForm"
  >
    <el-form
      label-position="top"
      require-asterisk-position="right"
      ref="datasource"
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
          style="width: 100%"
          :render-after-expand="false"
          :props="props"
          @node-click="nodeClick"
          :filter-method="filterMethod"
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
            <span>{{ t('data_source.relevant_content_found') }}</span>
          </div>
        </div>
      </div>
    </el-form>
    <template #footer>
      <el-button secondary @click="resetForm">{{ t('dataset.cancel') }} </el-button>
      <el-button type="primary" @click="saveDataset">{{ t('dataset.confirm') }} </el-button>
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
