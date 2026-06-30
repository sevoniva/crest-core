<script setup lang="ts">
import icon_add_outlined from '@/assets/svg/icon_add_outlined.svg'
import icon_upload_outlined from '@/assets/svg/icon_upload_outlined.svg'
import dvCopyDark from '@/assets/svg/dv-copy-dark.svg'
import dvDelete from '@/assets/svg/dv-delete.svg'
import dvMove from '@/assets/svg/dv-move.svg'
import dvCancelPublish from '@/assets/svg/icon_undo_outlined.svg'
import { treeDraggbleChart } from '@/utils/treeDraggbleChart'
import { throttle } from 'lodash-es'
import dvRename from '@/assets/svg/dv-rename.svg'
import dvDashboardSpine from '@/assets/svg/dv-dashboard-spine.svg'
import dvDashboardSpineDisabled from '@/assets/svg/dv-dashboard-spine-disabled.svg'
import dvScreenSpine from '@/assets/svg/dv-screen-spine.svg'
import dvNewFolder from '@/assets/svg/dv-new-folder.svg'
import icon_fileAdd_outlined from '@/assets/svg/icon_file-add_outlined.svg'
import icon_searchOutline_outlined from '@/assets/svg/icon_search-outline_outlined.svg'
import dvSortAsc from '@/assets/svg/dv-sort-asc.svg'
import dvSortDesc from '@/assets/svg/dv-sort-desc.svg'
import dvFolder from '@/assets/svg/dv-folder.svg'
import icon_operationAnalysis_outlined from '@/assets/svg/icon_operation-analysis_outlined.svg'
import icon_edit_outlined from '@/assets/svg/icon_edit_outlined.svg'
import { onMounted, reactive, ref, toRefs, watch, nextTick, computed } from 'vue'
import {
  copyResource,
  deleteLogic,
  decompressionLocalFile,
  queryShareBaseApi,
  ResourceOrFolder,
  updateBase
} from '@/api/visualization/dataVisualization'
import { ElIcon, ElMessage, ElMessageBox, ElScrollbar } from 'element-plus-secondary'
import { Icon } from '@/components/icon-custom'
import { useEmitt } from '@/hooks/web/useEmitt'
import { HandleMore } from '@/components/handle-more'
import CrestResourceGroupActions from '@/views/common/CrestResourceGroupActions.vue'
import { useEmbedded } from '@/store/modules/embedded'
import { BusiTreeNode, BusiTreeRequest } from '@/models/tree/TreeNode'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { useAppStoreWithOut } from '@/store/modules/app'
import { storeToRefs } from 'pinia'
import DvHandleMore from '@/components/handle-more/src/DvHandleMore.vue'
import { interactiveStoreWithOut } from '@/store/modules/interactive'
import { useShareStoreWithOut } from '@/store/modules/share'
const shareStore = useShareStoreWithOut()
const interactiveStore = interactiveStoreWithOut()
import { useI18n } from '@/hooks/web/useI18n'
import _ from 'lodash'
import { useCache } from '@/hooks/web/useCache'
import { findParentIdByChildIdRecursive, onInitReady } from '@/utils/canvasUtils'
import treeSort, { treeParentWeight } from '@/utils/treeSortUtils'
import router from '@/router'
import { cancelRequestBatch } from '@/config/axios/service'
import { isFreeFolder } from '@/utils/utils'
// 缓存用于恢复上次选中的仪表板或大屏资源
const { wsCache } = useCache()

// 大屏主状态，提供当前资源和画布上下文
const dvMainStore = dvMainStoreWithOut()
// 应用状态用于判断嵌入式运行环境
const appStore = useAppStoreWithOut()
// 嵌入式状态可携带外部传入的资源编号
const embeddedStore = useEmbedded()
// 当前大屏或仪表板详情的响应式引用
const { dvInfo } = storeToRefs(dvMainStore)
// 资源树文案的多语言翻译入口
const { t } = useI18n()

// 资源树入参决定资源类型、展示场景和数据表来源
const props = defineProps({
  curCanvasType: {
    type: String,
    required: true
  },
  showPosition: {
    required: false,
    type: String,
    default: 'preview'
  },
  resourceTable: {
    required: false,
    type: String,
    default: 'core'
  }
})
// Element 树组件的字段映射和禁用规则
const defaultProps = {
  children: 'children',
  label: 'name',
  disabled: (data: any) => data.extraFlag1 === 0
}
// 标记资源树是否完成首次挂载
const mounted = ref(false)
// 根目录管理权限标记
const rootManage = ref(false)
// 任意资源管理权限标记
const anyManage = ref(false)
// 当前画布类型和展示位置保持响应式引用
const { curCanvasType, showPosition } = toRefs(props)
// 根据资源类型生成业务侧展示名称
const resourceLabel =
  curCanvasType.value === 'dataV' ? t('work_branch.big_data_screen') : t('work_branch.dashboard')
// 当前选中的树节点编号
const selectedNodeKey = ref(null)
// 资源树搜索关键字
const filterText = ref(null)
// 已展开节点编号集合
const expandedArray = ref([])
// 资源树组件实例引用
const resourceListTree = ref()
// 批量操作组件实例引用
const resourceGroupOpt = ref()
// 导入上传组件实例引用
const importUploadRef = ref()
// 是否需要在挂载后回选缓存资源
const returnMounted = ref(false)
// 导入资源时的加载状态
const importLoading = ref(false)
// 资源树页面状态，包含排序、树数据和右键菜单配置
const state = reactive({
  pWeightMap: {},
  curSortType: 'time_desc',
  resourceTree: [] as BusiTreeNode[],
  originResourceTree: [] as BusiTreeNode[],
  folderMenuList: [
    {
      label: t('visualization.move_to'), //'移动到'
      command: 'move',
      svgName: dvMove
    },
    {
      label: t('visualization.rename'), //'重命名'
      command: 'rename',
      svgName: dvRename
    },
    {
      label: t('visualization.delete'), // 删除
      command: 'delete',
      svgName: dvDelete,
      divided: true
    }
  ] as any[],
  sortType: [
    {
      label: t('visualization.time_asc'), //'按时间升序'
      value: 'time_asc'
    },
    {
      label: t('visualization.time_desc'), //'按时间降序'
      value: 'time_desc'
    },
    {
      label: t('visualization.name_asc'), //'按名称升序'
      value: 'name_asc'
    },
    {
      label: t('visualization.name_desc'), //'按名称降序'
      value: 'name_desc'
    }
  ]
})

// 新建资源菜单图标根据画布类型切换
const dvSvgType = computed(() =>
  curCanvasType.value === 'dashboard' ? dvDashboardSpine : dvScreenSpine
)

// 嵌入式场景需要隐藏部分资源操作
const isEmbedded = computed(() => appStore.getIsCrestBi || appStore.getIsIframe)

// 新建按钮的菜单项，区分空白资源和文件夹
const resourceTypeList = computed(() => {
  const list = [
    {
      label: t('work_branch.new_empty'), //'空白新建',
      svgName: dvSvgType.value,
      command: 'newLeaf'
    },
    {
      label: t('work_branch.new_folder'), //'新建文件夹'
      divided: true,
      svgName: dvFolder,
      command: 'newFolder'
    }
  ]
  return list
})
const { handleDrop, allowDrop, handleDragStart } = treeDraggbleChart(
  state,
  'resourceTree',
  curCanvasType.value
)

// 根据父级权重决定是否允许复制等资源操作
const menuListWeight = id => {
  const pWeight = state.pWeightMap[id]
  return pWeight < 7 ? menuList : menuListWithCopy
}
// 权重允许时展示包含复制的资源操作菜单
const menuListWithCopy = [
  {
    label: t('visualization.cancel_publish'), //取消发布
    command: 'cancelPublish',
    svgName: dvCancelPublish
  },
  {
    label: t('visualization.copy'), //'复制',
    command: 'copy',
    svgName: dvCopyDark,
    divided: true
  },
  {
    label: t('visualization.move_to'), //'移动到',
    command: 'move',
    svgName: dvMove
  },
  {
    label: t('visualization.rename'), //'重命名',
    command: 'rename',
    svgName: dvRename
  },
  {
    label: t('visualization.delete'), //'删除',
    command: 'delete',
    svgName: dvDelete,
    divided: true
  }
]
// 基础资源操作菜单
const menuList = [
  {
    label: t('visualization.cancel_publish'), //取消发布
    command: 'cancelPublish',
    svgName: dvCancelPublish
  },
  {
    label: t('visualization.move_to'), //'移动到',
    command: 'move',
    svgName: dvMove,
    divided: true
  },
  {
    label: t('visualization.rename'), //'重命名',
    command: 'rename',
    svgName: dvRename
  },
  {
    label: t('visualization.delete'), //'删除',
    command: 'delete',
    svgName: dvDelete,
    divided: true
  }
]

// 从缓存中恢复最近访问的资源编号
const infoId = wsCache.get(curCanvasType.value === 'dashboard' ? 'db-info-id' : 'dv-info-id')
// 路由参数中的资源编号作为回选兜底来源
const routerDvId = router.currentRoute.value.query.dvId
// 最终用于回选的资源编号
const dvId = embeddedStore.dvId || infoId || routerDvId
wsCache.delete(curCanvasType.value === 'dashboard' ? 'db-info-id' : 'dv-info-id')
if (dvId && showPosition.value === 'preview') {
  selectedNodeKey.value = dvId
  returnMounted.value = true
}
// 记录展开节点，刷新树数据后可恢复展开状态
const nodeExpand = data => {
  if (data.id) {
    expandedArray.value.push(data.id)
  }
}

// 节点收起时从展开集合移除对应编号
const nodeCollapse = data => {
  if (data.id) {
    expandedArray.value.splice(expandedArray.value.indexOf(data.id), 1)
  }
}

// 搜索树节点，同时避免复用当前正在编辑的资源
const filterNode = (value: string, data: BusiTreeNode) => {
  if (showPosition.value === 'multiplexing' && data.id === dvInfo.value?.id) {
    return false
  }
  if (!value) return true
  return data.name?.toLocaleLowerCase().includes(value.toLocaleLowerCase())
}
// 切换资源前取消上一资源的详情、数据和联动请求
const cancelPreRequest = () => {
  cancelRequestBatch('/data-visualization/detail')
  cancelRequestBatch('/chart-data/data')
  cancelRequestBatch('/linkage/visualization-linkage-info/**')
  cancelRequestBatch('/link-jump/visualization-jump-info/**')
}

// 处理资源树节点点击，切换当前资源并同步地址参数
const nodeClick = (data: BusiTreeNode, node) => {
  dvMainStore.setCurComponent({ component: null, index: null })
  if (showPosition.value !== 'multiplexing') {
    dvMainStore.setEditMode('preview')
  }
  if (node.disabled) {
    nextTick(() => {
      // 找到当前高亮的节点，移除高亮样式
      const currentNode = resourceListTree.value.$el.querySelector('.is-current')
      if (currentNode) {
        currentNode.classList.remove('is-current')
      }
      return // 阻止后续逻辑
    })
  } else {
    cancelPreRequest()
    selectedNodeKey.value = data.id
    if (data.leaf) {
      if (!embeddedStore.baseUrl) {
        let url = window.location.href
        const paramName = 'dvId'
        const paramValue = data.id
        // 检查是否已经有查询参数（在哈希部分）
        if (url.includes('?')) {
          const regex = new RegExp(`([?&])${paramName}=[^&]*`)
          if (regex.test(url)) {
            url = url.replace(regex, `$1${paramName}=${paramValue}`)
          } else {
            url += `&${paramName}=${paramValue}`
          }
        } else {
          url += `?${paramName}=${paramValue}`
        }
        window.history.replaceState(
          {
            path: url
          },
          '',
          url
        )
      }
      emit('nodeClick', data)
    } else {
      resourceListTree.value.setCurrentKey(null)
    }
  }
}

// 拉取当前资源类型的树数据，并同步权限、排序和回选状态
const getTree = async (notOpen = false) => {
  const request = {
    busiFlag: curCanvasType.value,
    resourceTable: props.resourceTable
  } as BusiTreeRequest
  const isDashboard = curCanvasType.value == 'dashboard'
  await interactiveStore.setInteractive(request)
  const interactiveData = isDashboard ? interactiveStore.getPanel : interactiveStore.getScreen
  const nodeData = interactiveData.treeNodes
  rootManage.value = interactiveData.rootManage
  anyManage.value = interactiveData.anyManage
  if (
    dvInfo.value &&
    dvInfo.value.id &&
    !JSON.stringify(nodeData).includes(dvInfo.value.id) &&
    showPosition.value !== 'multiplexing'
  ) {
    dvMainStore.resetDvInfo()
  }
  const curSortType = getStoredSortType(`TreeSort-${curCanvasType.value}`, getDefaultSortType())
  if (nodeData.length && nodeData[0]['id'] === '0' && nodeData[0]['name'] === 'root') {
    state.originResourceTree = nodeData[0]['children'] || []
    sortTypeChange(curSortType)
    afterTreeInit(notOpen)
    return
  }
  state.originResourceTree = nodeData
  sortTypeChange(curSortType)
  afterTreeInit(notOpen)
}

// 展平资源树中的叶子资源，供空态和外部状态判断使用
const flattedTree = computed<BusiTreeNode[]>(() => {
  return _.filter(flatTree(state.resourceTree), node => node.leaf)
})

// 是否存在可选择的资源叶子节点
const hasData = computed<boolean>(() => flattedTree.value.length > 0)

// 递归展平树节点，保留目录和叶子节点顺序
function flatTree(tree: BusiTreeNode[]) {
  let result = _.cloneDeep(tree)
  _.forEach(tree, node => {
    if (node.children && node.children.length > 0) {
      result = _.union(result, flatTree(node.children))
    }
  })
  return result
}

// 树数据刷新后的收尾流程，恢复展开、选中和过滤状态
const afterTreeInit = (notOpen = false) => {
  state.pWeightMap = treeParentWeight(state.originResourceTree, rootManage.value ? 9 : 0)
  mounted.value = true
  if (selectedNodeKey.value && returnMounted.value) {
    expandedArray.value = getDefaultExpandedKeys()
    returnMounted.value = false
  }
  onInitReady({ type: curCanvasType.value }, 'resource_tree_init_ready')
  nextTick(() => {
    resourceListTree.value.setCurrentKey(selectedNodeKey.value)
    resourceListTree.value.filter(filterText.value)
    if (notOpen) return
    nextTick(() => {
      ;(document.querySelector('.is-current')?.firstChild as HTMLElement | undefined)?.click()
    })
  })
}

// 复制资源操作的加载状态
const copyLoading = ref(false)
const openType = wsCache.get('open-backend') === '1' ? '_self' : '_blank'
// 向父组件抛出资源节点点击事件
const emit = defineEmits(['nodeClick'])

// 导入前校验模板文件扩展名
const beforeImportTemplateUpload = file => {
  if (!/\.crest$/i.test(file.name || '')) {
    ElMessage.error(t('visualization.template_file_type_limit'))
    return false
  }
  return true
}

// 上传并解析本地模板包，成功后进入模板编辑流程
const importTemplateFile = uploadFile => {
  const formData = new FormData()
  formData.append('file', uploadFile.file)
  importLoading.value = true
  return decompressionLocalFile(formData)
    .then(res => {
      const payload = res?.data
      if (!payload || payload.type !== curCanvasType.value) {
        ElMessage.error(t('visualization.template_type_mismatch'))
        return
      }
      const cacheKey = `CREST-VISUALIZATION-IMPORT-${Date.now()}-${Math.random()
        .toString(36)
        .slice(2)}`
      window.sessionStorage.setItem(cacheKey, JSON.stringify(payload))
      openImportedTemplateEditor(cacheKey)
    })
    .finally(() => {
      importUploadRef.value?.clearFiles()
      importLoading.value = false
    })
}

// 根据运行环境打开导入模板的新建编辑入口
const openImportedTemplateEditor = cacheKey => {
  if (isEmbedded.value) {
    embeddedStore.clearState()
    embeddedStore.setOpt('create')
    embeddedStore.setCreateType('uploadedTemplate')
    embeddedStore.setTemplateParams(cacheKey)
    useEmitt().emitter.emit(
      'changeCurrentComponent',
      curCanvasType.value === 'dataV' ? 'VisualizationEditor' : 'DashboardEditor'
    )
    return
  }
  const query = `opt=create&createType=uploadedTemplate&templatePayloadKey=${encodeURIComponent(
    cacheKey
  )}`
  const baseUrl = curCanvasType.value === 'dataV' ? `#/dvCanvas?${query}` : `#/dashboard?${query}`
  const newWindow = window.open(baseUrl, openType)
  initOpenHandler(newWindow)
}

// 统一处理资源和目录的右键菜单命令
const operation = (cmd: string, data: BusiTreeNode, nodeType: string) => {
  if (cmd === 'delete') {
    const msg = data.leaf ? '' : t('visualization.delete_tips')
    const tips_label = data.leaf ? resourceLabel : t('visualization.folder')
    ElMessageBox.confirm(t('visualization.delete_warn', [tips_label]), {
      confirmButtonType: 'danger',
      type: 'warning',
      tip: msg,
      autofocus: false,
      showClose: false
    }).then(() => {
      deleteLogic(data.id, curCanvasType.value).then(() => {
        ElMessage.success(t('visualization.delete_success'))
        getTree(true)
      })
    })
  } else if (cmd === 'cancelPublish') {
    const params = {
      id: data.id,
      nodeType: 'leaf',
      name: data.name,
      type: curCanvasType.value,
      mobileLayout: data?.extraFlag,
      status: 0
    }
    updateBase(params).then(() => {
      data['extraFlag1'] = 0
      if (dvInfo.value.id === data.id) {
        dvMainStore.updateDvInfoCall(0)
      }
      ElMessage.warning(t('visualization.cancel_publish_tips'))
    })
  } else if (cmd === 'edit') {
    resourceEdit(data.id)
  } else if (cmd === 'copy') {
    const targetPid = findParentIdByChildIdRecursive(state.resourceTree, data.id)
    const params: ResourceOrFolder = {
      nodeType: nodeType as 'folder' | 'leaf',
      name: data.name + '-copy',
      type: curCanvasType.value,
      id: data.id,
      pid: targetPid || '0',
      mobileLayout: !!data?.extraFlag,
      status: !!data?.extraFlag1
    }

    copyLoading.value = true

    copyResource(params)
      .then(data => {
        const baseUrl =
          curCanvasType.value === 'dataV'
            ? `#/dvCanvas?opt=copy&pid=${params.pid}&dvId=${data.data}`
            : `#/dashboard?opt=copy&pid=${params.pid}&resourceId=${data.data}`
        if (isEmbedded.value) {
          embeddedStore.clearState()
          embeddedStore.setPid(params.pid as string)
          embeddedStore.setOpt('copy')
          if (curCanvasType.value === 'dataV') {
            embeddedStore.setDvId(data.data)
          } else {
            embeddedStore.setResourceId(data.data)
          }
          useEmitt().emitter.emit(
            'changeCurrentComponent',
            curCanvasType.value === 'dataV' ? 'VisualizationEditor' : 'DashboardEditor'
          )
          return
        }
        const newWindow = window.open(baseUrl, openType)
        initOpenHandler(newWindow)
      })
      .finally(() => {
        copyLoading.value = false
      })
  } else {
    resourceGroupOpt.value.optInit(nodeType, data, cmd, ['copy'].includes(cmd))
  }
}

const addOperation = (
  cmd: string,
  data?: BusiTreeNode,
  nodeType?: string,
  parentSelect?: boolean
) => {
  // 新建子节点的操作流程为先进行创建 后面选择所在目录
  if (cmd === 'newLeaf') {
    const baseUrl =
      curCanvasType.value === 'dataV' ? '#/dvCanvas?opt=create' : '#/dashboard?opt=create'
    let newWindow = null
    if (isEmbedded.value) {
      embeddedStore.clearState()
      embeddedStore.setOpt('create')
      if (data?.id) {
        embeddedStore.setPid(data?.id as string)
      }
      useEmitt().emitter.emit(
        'changeCurrentComponent',
        curCanvasType.value === 'dataV' ? 'VisualizationEditor' : 'DashboardEditor'
      )
      return
    }
    if (data?.id) {
      newWindow = window.open(baseUrl + `&pid=${data.id}`, openType)
    } else {
      newWindow = window.open(baseUrl, openType)
    }
    initOpenHandler(newWindow)
  } else {
    resourceGroupOpt.value.optInit(nodeType, data || {}, cmd, parentSelect)
  }
}

// 触发父级创建空白资源流程
function createNewObject() {
  return addOperation('newLeaf', null, 'leaf', true)
}

// 根据当前资源类型打开编辑页面
const resourceEdit = resourceId => {
  const baseUrl = curCanvasType.value === 'dataV' ? '#/dvCanvas?dvId=' : '#/dashboard?resourceId='
  if (isEmbedded.value) {
    embeddedStore.clearState()
    if (curCanvasType.value === 'dataV') {
      embeddedStore.setDvId(resourceId)
    } else {
      embeddedStore.setResourceId(resourceId)
    }
    useEmitt().emitter.emit(
      'changeCurrentComponent',
      curCanvasType.value === 'dataV' ? 'VisualizationEditor' : 'DashboardEditor'
    )
    return
  }

  const newWindow = window.open(baseUrl + resourceId, openType)
  initOpenHandler(newWindow)
}

// 资源操作完成后刷新树并保持当前打开状态
const resourceOptFinish = () => {
  getTree(true)
}

// 递归查找目标节点的父级路径
const getParentKeys = (tree, targetKey, parentKeys = []) => {
  for (const node of tree) {
    if (node.id === targetKey) {
      return parentKeys
    }
    if (node.children) {
      const newParentKeys = [...parentKeys, node.id]
      const result = getParentKeys(node.children, targetKey, newParentKeys)
      if (result) {
        return result
      }
    }
  }
  return null
}

// 计算默认展开节点，优先展开当前选中资源的父路径
const getDefaultExpandedKeys = () => {
  const parentKeys = getParentKeys(state.resourceTree, selectedNodeKey.value)
  if (parentKeys) {
    return [selectedNodeKey.value, ...parentKeys]
  } else {
    return []
  }
}

const sortList = [
  {
    name: t('visualization.time_asc'),
    value: 'time_asc'
  },
  {
    name: t('visualization.time_desc'),
    value: 'time_desc',
    divided: true
  },
  {
    name: t('visualization.name_asc'),
    value: 'name_asc'
  },
  {
    name: t('visualization.name_desc'),
    value: 'name_desc'
  }
]

// 根据资源类型返回默认排序方式
const getDefaultSortType = () => {
  const sortIndex = Number(wsCache.get('TreeSort-backend') ?? 1)
  return sortList[Number.isInteger(sortIndex) && sortList[sortIndex] ? sortIndex : 1].value
}

// 读取缓存排序方式，异常时回退到默认值
const getStoredSortType = (cacheKey: string, fallback: string) => {
  const sortType = wsCache.get(cacheKey)
  return sortList.some(ele => ele.value === sortType) ? sortType : fallback
}

// 当前排序方式的展示文案
const sortTypeTip = computed(() => {
  return sortList.find(ele => ele.value === state.curSortType)?.name ?? sortList[1].name
})

// 处理排序菜单切换并写入本地缓存
const handleSortTypeChange = sortType => {
  state.resourceTree = treeSort(state.originResourceTree, sortType)
  state.curSortType = sortType
  wsCache.set('TreeSort-' + curCanvasType.value, state.curSortType)
}

// 按排序方式重建资源树
const sortTypeChange = sortType => {
  state.resourceTree = treeSort(state.originResourceTree, sortType)
  state.curSortType = sortType
}

const proxyAllowDrop = throttle((arg1, arg2) => {
  const flagArray = ['dashboard', 'dataV', 'dataset', 'datasource']
  const flag = flagArray.findIndex(item => item === curCanvasType.value)
  if (flag < 0 || !isFreeFolder(arg2, flag + 1)) {
    return allowDrop(arg1, arg2)
  }
  ElMessage.warning(t('free.save_error'))
  return false
}, 300)

// 搜索关键字变化后过滤树节点
watch(filterText, val => {
  resourceListTree.value.filter(val)
})

// 外部窗口句柄代理，用于嵌入式宿主接管新窗口
const openHandler = ref(null)
// 初始化外部窗口句柄
const initOpenHandler = newWindow => {
  if (openHandler?.value) {
    const pm = {
      methodName: 'initOpenHandler',
      args: newWindow
    }
    openHandler.value?.invokeMethod(pm)
  }
}

// 初始化资源树排序缓存状态
const loadInit = () => {
  const historyTreeSort = wsCache.get('TreeSort-' + curCanvasType.value)
  if (historyTreeSort) {
    state.curSortType = historyTreeSort
  }
}

// 加载分享基础配置并写入共享状态
const loadShareBase = () => {
  queryShareBaseApi().then(res => {
    const param = {
      shareDisable: res.data?.disable,
      sharePeRequire: res.data?.peRequire
    }
    shareStore.setData(param)
  })
}

onMounted(() => {
  loadInit()
  getTree()
  loadShareBase()
})

defineExpose({
  rootManage,
  hasData,
  createNewObject,
  mounted
})
</script>

<template>
  <div class="resource-tree">
    <div class="tree-header">
      <div class="icon-methods" v-show="showPosition === 'preview'">
        <span class="title"> {{ resourceLabel }} </span>
        <div v-if="rootManage" class="flex-align-center">
          <el-tooltip
            :offset="14"
            :content="t('work_branch.new_folder')"
            placement="top"
            popper-class="new-folder_tip"
            effect="dark"
            :arrow-offset="10"
          >
            <el-icon
              class="custom-icon btn"
              style="margin-right: 20px"
              @click="addOperation('newFolder', null, 'folder')"
            >
              <Icon name="dv-new-folder"><dvNewFolder class="svg-icon" /></Icon>
            </el-icon>
          </el-tooltip>

          <el-dropdown placement="bottom-start" popper-class="menu-outer-dv_popper" trigger="hover">
            <el-icon class="custom-icon btn" @click="addOperation('newLeaf', null, 'leaf', true)">
              <Icon name="icon_file-add_outlined"><icon_fileAdd_outlined class="svg-icon" /></Icon>
            </el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="addOperation('newLeaf', null, 'leaf', true)">
                  <el-icon :class="`handle-icon color-${curCanvasType}`">
                    <Icon><component class="svg-icon" :is="dvSvgType"></component></Icon>
                  </el-icon>
                  {{ t('work_branch.new_empty') }}
                </el-dropdown-item>
                <el-dropdown-item @click.stop>
                  <el-upload
                    ref="importUploadRef"
                    class="resource-import-upload"
                    :show-file-list="false"
                    accept=".crest"
                    :before-upload="beforeImportTemplateUpload"
                    :http-request="importTemplateFile"
                  >
                    <div class="resource-import-entry">
                      <el-icon :class="`handle-icon color-${curCanvasType}`">
                        <Icon name="icon_upload_outlined">
                          <icon_upload_outlined class="svg-icon" />
                        </Icon>
                      </el-icon>
                      <span>{{ t('visualization.import_app_template') }}</span>
                    </div>
                  </el-upload>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <el-input
        :placeholder="t('commons.search')"
        v-model="filterText"
        clearable
        class="search-bar"
      >
        <template #prefix>
          <el-icon>
            <Icon name="icon_search-outline_outlined"
              ><icon_searchOutline_outlined class="svg-icon"
            /></Icon>
          </el-icon>
        </template>
      </el-input>
      <el-dropdown @command="handleSortTypeChange" trigger="click">
        <span class="filter-icon-trigger">
          <el-tooltip :offset="16" effect="dark" :content="sortTypeTip" placement="top">
            <el-icon class="filter-icon-span">
              <Icon v-if="state.curSortType.includes('asc')" name="dv-sort-asc" class="opt-icon"
                ><dvSortAsc class="svg-icon opt-icon"
              /></Icon>
              <Icon v-else name="dv-sort-desc" class="opt-icon"
                ><dvSortDesc class="svg-icon opt-icon"
              /></Icon>
            </el-icon>
          </el-tooltip>
        </span>
        <template #dropdown>
          <el-dropdown-menu style="width: 246px">
            <template :key="ele.value" v-for="ele in sortList">
              <el-dropdown-item
                class="ed-select-dropdown__item"
                :class="ele.value === state.curSortType && 'selected'"
                :command="ele.value"
              >
                {{ ele.name }}
              </el-dropdown-item>
              <li v-if="ele.divided" class="ed-dropdown-menu__item--divided"></li>
            </template>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <el-scrollbar class="custom-tree" v-loading="copyLoading || importLoading">
      <el-tree
        menu
        ref="resourceListTree"
        :default-expanded-keys="expandedArray"
        :data="state.resourceTree"
        :props="defaultProps"
        node-key="id"
        highlight-current
        :expand-on-click-node="true"
        :filter-node-method="filterNode"
        @node-expand="nodeExpand"
        @node-collapse="nodeCollapse"
        @node-click="nodeClick"
        @node-drag-start="handleDragStart"
        :allow-drop="proxyAllowDrop"
        @node-drop="handleDrop"
        draggable
      >
        <template #default="{ node, data }">
          <span class="custom-tree-node" :class="{ 'node-disabled-custom': data.extraFlag1 === 0 }">
            <el-icon style="font-size: 18px" v-if="!data.leaf">
              <Icon name="dv-folder"><dvFolder class="svg-icon" /></Icon>
            </el-icon>
            <el-icon style="font-size: 18px" v-else-if="curCanvasType === 'dashboard'">
              <Icon v-if="data.extraFlag1"><component :is="dvDashboardSpine"></component></Icon>
              <Icon v-if="!data.extraFlag1"
                ><component :is="dvDashboardSpineDisabled"></component
              ></Icon>
            </el-icon>
            <el-icon
              class="icon-screen-new color-dataV"
              :class="{ 'color-dataV': data.extraFlag1, 'color-dataV-disabled': !data.extraFlag1 }"
              style="font-size: 18px"
              v-else
            >
              <Icon name="icon_operation-analysis_outlined"
                ><icon_operationAnalysis_outlined class="svg-icon"
              /></Icon>
            </el-icon>
            <span :title="node.label" class="label-tooltip">
              <el-tooltip
                class="box-item"
                effect="dark"
                :content="t('visualization.publish_tips1')"
                :disabled="!!data.extraFlag1"
                placement="top-start"
              >
                {{ node.label }}
              </el-tooltip>
            </span>
            <div class="icon-more" v-if="data.weight >= 7 && showPosition === 'preview'">
              <el-icon
                v-on:click.stop
                v-if="data.leaf"
                class="hover-icon"
                @click="resourceEdit(data.id)"
              >
                <Icon><icon_edit_outlined class="svg-icon" /></Icon>
              </el-icon>
              <handle-more
                @handle-command="
                  cmd => addOperation(cmd, data, cmd === 'newFolder' ? 'folder' : 'leaf')
                "
                :menu-list="resourceTypeList"
                :icon-name="icon_add_outlined"
                placement="bottom-start"
                v-if="!data.leaf"
              ></handle-more>
              <dv-handle-more
                @handle-command="cmd => operation(cmd, data, data.leaf ? 'leaf' : 'folder')"
                :node="data"
                :any-manage="anyManage"
                :resource-type="curCanvasType"
                :menu-list="data.leaf ? menuListWeight(data.id) : state.folderMenuList"
              ></dv-handle-more>
            </div>
          </span>
        </template>
      </el-tree>
      <CrestResourceGroupActions
        :cur-canvas-type="curCanvasType"
        @finish="resourceOptFinish"
        ref="resourceGroupOpt"
      />
    </el-scrollbar>
  </div>
</template>
<style lang="less" scoped>
.filter-icon-span {
  border: 1px solid #e2e8f0;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: #334155;
  padding: 8px;
  margin-left: 8px;
  font-size: 16px;
  cursor: pointer;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);

  .opt-icon:focus {
    outline: none !important;
  }
  &:hover {
    color: var(--ed-color-primary);
    border-color: #bfdbfe;
    background: #eff6ff;
  }

  &:active {
    background: #dbeafe;
  }
}
.resource-tree {
  padding: 18px 12px 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  color: #0f172a;
  font-family: var(--crest-custom_font, var(--crest-font-sans));

  .tree-header {
    padding: 0 4px 14px;
    border-bottom: 1px solid #e2e8f0;
  }

  .icon-methods {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    font-size: 20px;
    font-weight: 700;
    color: #0f172a;
    padding-bottom: 14px;
    .title {
      margin-right: auto;
      font-size: 16px;
      font-style: normal;
      font-weight: 700;
      line-height: 24px;
    }
    .custom-icon {
      font-size: 20px;
      position: relative;
      outline: none;
      &.btn {
        color: var(--ed-color-primary);
        width: 34px;
        height: 34px;
        border: 1px solid #e2e8f0;
        border-radius: 10px;
        background: #ffffff;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
      }
      &:hover {
        color: var(--ed-color-primary);
        border-color: #bfdbfe;
        background: #eff6ff;
        cursor: pointer;
        &::after {
          content: none;
        }
      }
    }
  }
  .search-bar {
    padding-bottom: 0;
    width: calc(100% - 42px);
  }

  :deep(.ed-input__wrapper) {
    border-radius: 10px;
    border: 1px solid #e2e8f0;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
    background: #ffffff;

    &:hover {
      border-color: #bfdbfe;
      box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
    }

    &.is-focus {
      border-color: var(--ed-color-primary);
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.14);
    }
  }
}
.title-area {
  margin-left: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.title-area-outer {
  display: flex;
  flex: 1 1 0%;
  width: 0px;
}
.custom-tree-node-list {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding: 0 8px;
}
.father .child {
  visibility: hidden;
}

.father:hover .child {
  visibility: visible;
}

:deep(.ed-input__wrapper) {
  width: 100%;
}

.custom-tree {
  height: calc(100vh - 158px);
  padding: 10px 0 0;
}

.resource-import-upload {
  width: 100%;

  :deep(.ed-upload) {
    width: 100%;
  }
}

.resource-import-entry {
  width: 100%;
  display: flex;
  align-items: center;
}

.custom-tree-node {
  width: calc(100% - 30px);
  display: flex;
  align-items: center;
  box-sizing: content-box;
  padding-right: 4px;

  .label-tooltip {
    width: 100%;
    margin-left: 8.75px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    color: #334155;
    font-weight: 500;
  }
  .icon-more {
    margin-left: auto;
    display: none;
  }

  &:hover {
    .label-tooltip {
      width: calc(100% - 78px);
    }

    .icon-more {
      display: inline-flex;
    }
  }

  .icon-screen-new {
    border-radius: 6px;
    color: #fff;
    padding: 3px;
  }
}

:deep(.ed-tree) {
  --ed-tree-node-hover-bg-color: #f8fafc;
  color: #334155;
  background: transparent;
}

:deep(.ed-tree-node__content) {
  height: 36px;
  border-radius: 10px;
  margin-bottom: 4px;
  padding-right: 6px;

  &:hover {
    background: #f8fafc;
  }
}

:deep(.ed-tree--highlight-current .ed-tree-node.is-current > .ed-tree-node__content) {
  background: #eff6ff;
  color: var(--ed-color-primary);
  box-shadow: inset 3px 0 0 var(--ed-color-primary);

  .label-tooltip {
    color: var(--ed-color-primary);
  }
}
</style>

<style lang="less">
.menu-outer-dv_popper {
  --ed-border-color-light: #dee0e3;
  min-width: 140px;
  margin-top: 6px !important;
  margin-left: -4px !important;

  .ed-dropdown-menu__item:not(.is-disabled):hover {
    background-color: #1f23291a;
    color: #1f2329;
  }

  .ed-icon {
    border-radius: 6px;
  }
}

.sort-type-normal {
  i {
    display: none;
  }
}

.sort-type-checked {
  color: var(--ed-color-primary);
  i {
    display: block;
  }
}

.node-disabled-custom {
  color: rgba(187, 191, 196, 1);
  cursor: not-allowed;
}

.color-dataV-disabled {
  background: #bbbfc4 !important;
}
</style>
