<script lang="ts" setup>
import dvFolder from '@/assets/svg/dv-folder.svg'
import icon_searchOutline_outlined from '@/assets/svg/icon_search-outline_outlined.svg'
import { ref, reactive, computed, watch, toRefs, nextTick } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { useCache } from '@/hooks/web/useCache'
import nothingTree from '@/assets/img/nothing-tree.png'
import { BusiTreeNode } from '@/models/tree/TreeNode'
import {
  copyResource,
  dvNameCheck,
  moveResource,
  queryTreeApi,
  ResourceOrFolder,
  updateBase,
  saveCanvas
} from '@/api/visualization/dataVisualization'
import { ElMessage } from 'element-plus-secondary'
import { cutTargetTree, filterFreeFolder, nameTrim } from '@/utils/utils'
// 当前组件服务仪表板和大屏两类资源，父级传入类型决定接口参数和展示文案
const props = defineProps({
  curCanvasType: {
    type: String,
    required: true
  }
})

const { curCanvasType } = toRefs(props)
const { wsCache } = useCache('localStorage')
const { t } = useI18n()

// 弹窗状态集合，保存目录树、当前目标资源和外部附加参数
const state = reactive({
  tData: [],
  nameList: [],
  targetInfo: null,
  attachParams: null
})

// 是否展示父目录选择，由保存场景和父组件参数共同控制
const showParentSelected = ref(false)
// 保存中状态，防止重复提交和展示按钮加载
const loading = ref(false)
// 当前操作节点类型，folder 表示目录，leaf 表示资源
const nodeType = ref()
// 当前父目录编号，创建和重命名时复用
const pid = ref()
// 当前资源或目录编号，移动、复制、重命名时使用
const id = ref()
// 当前操作命令，驱动标题、字段显隐和后端接口选择
const cmd = ref('')
// 移动目录树实例，用于关键字过滤和空态读取
const treeRef = ref()
// 移动目标目录搜索关键字
const filterText = ref('')
// 名称字段标签，随资源类型和操作类型切换
const resourceFormNameLabel = ref('')
// 弹窗表单模型，保存目标父级、父级名称和资源名称
const resourceForm = reactive({
  pid: '',
  pName: null,
  name: '新建'
})
// 当前画布类型对应的资源名称，用于标题、按钮和复制文案
const sourceLabel = computed(() =>
  curCanvasType.value === 'dataV' ? t('work_branch.big_data_screen') : t('work_branch.dashboard')
)

// 后端操作方法映射，未命中的命令会走基础信息更新接口
const methodMap = {
  move: moveResource,
  copy: copyResource,
  newFolder: saveCanvas
}
// 搜索后目录树是否为空，用于展示自定义空态
const searchEmpty = ref(false)

// 目录树过滤函数，同时同步树组件的空态
const filterNode = (value: string, data: BusiTreeNode) => {
  nextTick(() => {
    searchEmpty.value = treeRef.value.isEmpty
  })
  if (!value) return true
  return data.name.includes(value)
}

// 搜索关键字变化时刷新移动目录树过滤结果
watch(filterText, val => {
  treeRef.value.filter(val)
})

// 判断同级名称是否重复，名称列表由当前弹窗初始化阶段准备
const nameRepeat = value => {
  if (!nameList || nameList.length === 0) {
    return false
  }
  return nameList.some(name => name === value)
}
// 表单名称校验器，向 Element Plus 回传重复名称错误
const nameValidator = (_, value, callback) => {
  if (nameRepeat(value)) {
    callback(new Error(t('visualization.name_repeat')))
  } else {
    callback()
  }
}

// 只有创建、复制和保存后归档资源时才需要展示父目录选择
const showPid = computed(() => {
  return ['newLeaf', 'copy', 'newLeafAfter'].includes(cmd.value) && showParentSelected.value
})

// 移动和保存后归档不编辑名称，其余操作展示名称输入框
const showName = computed(() => {
  return !['newLeafAfter', 'move'].includes(cmd.value)
})

// 当前目录下已有名称列表，用于本地重复校验
let nameList = []
// 表单校验规则在初始化时按操作动态生成
const resourceFormRules = ref()

// 表单实例引用，用于校验和清理校验提示
const resource = ref()
// 弹窗显隐状态
const resourceDialogShow = ref(false)
// 弹窗标题，由操作类型和资源类型共同决定
const dialogTitle = ref('')
// 原始目录树缓存，用于搜索过滤时恢复全量目录
let tData = []
// tree-select 搜索过滤，保留当前关键字命中的目录
const filterMethod = value => {
  state.tData = [...tData].filter(item => item.name.includes(value))
}
// 关闭弹窗时恢复基础表单状态，下一次初始化重新灌入上下文
const resetForm = () => {
  dialogTitle.value = null
  resourceFormNameLabel.value = ''
  resourceForm.name = t('visualization.new')
  resourceForm.pid = ''
  resourceDialogShow.value = false
}

// 为目录树补齐 value 字段，并递归处理所有子目录
const dfs = (arr: BusiTreeNode[]) => {
  arr.forEach(ele => {
    ele['value'] = ele.id
    if (ele.children?.length) {
      dfs(ele.children)
    }
  })
}

// 根据命令类型返回弹窗标题，资源类型差异在这里集中处理
const getDialogTitle = exec => {
  return {
    newFolder: t('visualization.new_folder'),
    newLeaf:
      props.curCanvasType === 'dataV'
        ? t('visualization.new_screen')
        : t('visualization.new_dashboard'),
    move: t('visualization.move_to'),
    copy: t('visualization.copy') + sourceLabel.value,
    rename: t('visualization.rename'),
    newLeafAfter: t('visualization.belong_folder')
  }[exec]
}
// 名称输入占位文案，按目录、大屏或仪表板切换
const placeholder = ref('')

// 初始化资源操作弹窗，加载可选目录并准备表单、标题和校验规则
const optInit = (type, data: BusiTreeNode, exec, parentSelect = false, attachParams?) => {
  showParentSelected.value = parentSelect
  state.targetInfo = data
  state.attachParams = attachParams
  nodeType.value = type
  const optSource = data.leaf || type === 'leaf' ? sourceLabel.value : t('visualization.folder')
  const placeholderLabel =
    data.leaf || type === 'leaf'
      ? props.curCanvasType === 'dataV'
        ? t('work_branch.big_data_screen')
        : t('work_branch.dashboard')
      : t('visualization.folder')
  placeholder.value = t('visualization.input_name_tips', [placeholderLabel])
  filterText.value = ''
  dialogTitle.value = getDialogTitle(exec) + ('rename' === exec ? optSource : '')
  resourceFormNameLabel.value = (exec === 'move' ? '' : optSource) + t('visualization.name')
  const request = { busiFlag: curCanvasType.value, leaf: false, resourceTable: 'core', weight: 7 }
  if (['newFolder'].includes(exec)) {
    resourceForm.name = ''
  } else if ('copy' === exec) {
    resourceForm.name = data.name + '_copy'
  } else {
    resourceForm.name = data.name
  }
  queryTreeApi(request).then(res => {
    filterFreeFolder(res, curCanvasType.value)
    const resultTree = res || []
    dfs(resultTree as unknown as BusiTreeNode[])
    state.tData = (resultTree as unknown as BusiTreeNode[]) || []
    if (state.tData.length && state.tData[0].name === 'root' && state.tData[0].id === '0') {
      state.tData[0].name =
        curCanvasType.value === 'dataV'
          ? t('work_branch.big_data_screen')
          : t('work_branch.dashboard')
    }
    tData = [...state.tData]
    if ('move' === exec) {
      cutTargetTree(state.tData, data.id)
    }
    if (['newLeaf', 'newFolder'].includes(exec)) {
      resourceForm.pid = data.id as string
      pid.value = data.id
    } else {
      id.value = data.id
    }
  })
  cmd.value = exec
  resourceDialogShow.value = true
  resourceFormRules.value = {
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
        message: t('commons.char_1_64'),
        trigger: 'change'
      },
      { required: true, trigger: 'blur', validator: nameValidator }
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
    resource.value.clearValidate()
  }, 50)
}

// 编辑入口只回填当前节点和父级，具体操作由 optInit 的命令驱动
const editeInit = (param: BusiTreeNode) => {
  pid.value = param['pid']
  id.value = param.id
}

// 目录树字段映射，el-tree 和 tree-select 共用同一套结构
const propsTree = {
  label: 'name',
  children: 'children',
  isLeaf: node => !node.children?.length
}

// 点击目录节点后写回目标父级和父级名称，后续校验会使用名称判断搜索命中
const nodeClick = (data: BusiTreeNode) => {
  resourceForm.pid = data.id as string
  resourceForm.pName = data.name as string
}

// 校验移动或复制目标目录，防止空目标、搜索后未重新选择以及选择自身
const checkParent = params => {
  if (params.pid !== 0 && !params.pid) {
    ElMessage.error(t('visualization.select_target_folder'))
    return false
  }
  // 搜索后必须重新点击命中目录，避免旧父级在过滤结果之外仍被提交
  if (filterText.value && !resourceForm.pName.includes(filterText.value)) {
    ElMessage.error(t('visualization.select_target_folder'))
    return false
  }
  // 移动时不能把资源放到自身下面
  if (params.pid === params.id) {
    ElMessage.warning(t('visualization.select_target_tips'))
    return
  }
  return true
}

// 保存入口统一处理新建、移动、复制、重命名和保存后归档
const saveResource = () => {
  resource.value.validate(async result => {
    if (result) {
      const params: ResourceOrFolder = {
        nodeType: nodeType.value as 'folder' | 'leaf',
        name: resourceForm.name,
        type: curCanvasType.value,
        mobileLayout: state.targetInfo?.extraFlag,
        status: state.targetInfo?.extraFlag1
      }

      switch (cmd.value) {
        case 'move':
          params.pid = resourceForm.pid as string
          params.id = id.value
          break
        case 'copy':
          params.id = id.value
          params.pid = resourceForm.pid || pid.value || '0'
          break
        case 'rename':
          params.pid = pid.value as string
          params.id = id.value
          break
        default:
          params.pid = resourceForm.pid || pid.value || '0'
          break
      }
      nameTrim(params, t('components.length_1_64_characters'))
      if (cmd.value === 'move' && !checkParent(params)) {
        return
      }
      if (['newLeaf', 'newLeafAfter', 'newFolder', 'rename', 'move', 'copy'].includes(cmd.value)) {
        await dvNameCheck({ opt: cmd.value, ...params })
      }
      if (cmd.value === 'newLeaf') {
        resourceDialogShow.value = false
        emits('finish', { opt: 'newLeaf', ...params, ...state.attachParams })
      } else {
        loading.value = true
        const method = methodMap[cmd.value] ? methodMap[cmd.value] : updateBase
        method(params)
          .then(data => {
            loading.value = false
            resourceDialogShow.value = false
            emits('finish')
            ElMessage.success(t('visualization.save_success'))
            if (cmd.value === 'copy') {
              const openType = wsCache.get('open-backend') === '1' ? '_self' : '_blank'
              const baseUrl =
                curCanvasType.value === 'dataV'
                  ? '#/dvCanvas?opt=copy&dvId='
                  : '#/dashboard?opt=copy&resourceId='
              window.open(baseUrl + data.data, openType)
            }
          })
          .finally(() => {
            loading.value = false
          })
      }
    }
  })
}

defineExpose({
  optInit,
  editeInit
})

// 向父组件声明操作完成事件，父级负责刷新资源树或继续保存画布
const emits = defineEmits(['finish'])
</script>

<template>
  <el-dialog
    class="create-dialog"
    :title="dialogTitle"
    v-model="resourceDialogShow"
    :width="cmd === 'move' ? '600px' : '420px'"
    :before-close="resetForm"
    @submit.prevent
  >
    <el-form
      v-loading="loading"
      label-position="top"
      require-asterisk-position="right"
      ref="resource"
      :model="resourceForm"
      :rules="resourceFormRules"
    >
      <el-form-item v-if="showName" :label="resourceFormNameLabel" prop="name">
        <el-input
          @keydown.stop
          @keyup.stop
          :placeholder="placeholder"
          v-model="resourceForm.name"
        />
      </el-form-item>
      <el-form-item v-if="showPid" :label="t('visualization.belong_folder')" prop="pid">
        <el-tree-select
          style="width: 100%"
          @keydown.stop
          @keyup.stop
          v-model="resourceForm.pid"
          :data="state.tData"
          :props="propsTree"
          @node-click="nodeClick"
          :filter-method="filterMethod"
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
            v-model="resourceForm.pid"
            empty-text=""
            menu
            :data="state.tData"
            :props="propsTree"
            @node-click="nodeClick"
          >
            <template #default="{ data }">
              <span class="custom-tree-node">
                <el-icon style="font-size: 18px">
                  <Icon name="dv-folder"><dvFolder class="svg-icon" /></Icon>
                </el-icon>
                <span :title="data.name">{{ data.name }}</span>
              </span>
            </template>
          </el-tree>
          <div v-if="searchEmpty" class="empty-search">
            <img :src="nothingTree" />
            <span>{{ t('visualization.no_content') }}</span>
          </div>
        </div>
      </div>
    </el-form>
    <template #footer>
      <el-button secondary @click="resetForm()">{{ t('visualization.cancel') }} </el-button>
      <el-button type="primary" @click="saveResource()"
        >{{ t('visualization.confirm') }}
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
</style>
