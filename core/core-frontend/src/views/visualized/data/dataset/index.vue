<script lang="tsx" setup>
import icon_copy_filled from '@/assets/svg/icon_copy_filled.svg'
import icon_dataset from '@/assets/svg/icon_dataset.svg'
import icon_deleteTrash_outlined from '@/assets/svg/icon_delete-trash_outlined.svg'
import icon_intoItem_outlined from '@/assets/svg/icon_into-item_outlined.svg'
import { throttle } from 'lodash-es'
import icon_rename_outlined from '@/assets/svg/icon_rename_outlined.svg'
import dvNewFolder from '@/assets/svg/dv-new-folder.svg'
import icon_fileAdd_outlined from '@/assets/svg/icon_file-add_outlined.svg'
import { moveDatasetTree } from '@/api/dataset'
import icon_searchOutline_outlined from '@/assets/svg/icon_search-outline_outlined.svg'
import dvSortAsc from '@/assets/svg/dv-sort-asc.svg'
import dvSortDesc from '@/assets/svg/dv-sort-desc.svg'
import dvFolder from '@/assets/svg/dv-folder.svg'
import { treeDraggble } from '@/utils/treeDraggble'
import icon_add_outlined from '@/assets/svg/icon_add_outlined.svg'
import icon_info_outlined from '@/assets/svg/icon_info_outlined.svg'
import icon_dashboard_outlined from '@/assets/svg/icon_dashboard_outlined.svg'
import icon_operationAnalysis_outlined from '@/assets/svg/icon_operation-analysis_outlined.svg'
import icon_download_outlined from '@/assets/svg/icon_download_outlined.svg'
import icon_edit_outlined from '@/assets/svg/icon_edit_outlined.svg'
import { useI18n } from '@/hooks/web/useI18n'
import {
  ref,
  reactive,
  shallowRef,
  computed,
  watch,
  onBeforeMount,
  nextTick,
  unref,
  h,
  provide
} from 'vue'
import ArrowSide from '@/views/common/CrestResourceArrow.vue'
import { useEmbedded } from '@/store/modules/embedded'
import { useEmitt } from '@/hooks/web/useEmitt'
import relationChart from '@/components/relation-chart/index.vue'
import {
  ElIcon,
  ElButton,
  ElMessageBox,
  ElMessage,
  type ElMessageBoxOptions,
  ElAside,
  ElScrollbar
} from 'element-plus-secondary'
import { HandleMore } from '@/components/handle-more'
import { Icon } from '@/components/icon-custom'
import { useMoveLine } from '@/hooks/web/useMoveLine'
import { useRouter, useRoute } from 'vue-router_2'
import CreatDsGroup from './form/CreatDsGroup.vue'
import type { BusiTreeNode, BusiTreeRequest } from '@/models/tree/TreeNode'
import {
  delDatasetTree,
  datasetPreview,
  barInfoApi,
  perDelete,
  exportDatasetData,
  exportLimit,
  datasetTotal
} from '@/api/dataset'
import EmptyBackground from '@/components/empty-background/src/EmptyBackground.vue'
import CrestResourceGroupActions from '@/views/common/CrestResourceGroupActions.vue'
import DatasetDetail from './DatasetDetail.vue'
import { guid } from '@/views/visualized/data/dataset/form/util'
import { save as saveVisualization } from '@/api/visualization/dataVisualization'
import { cloneDeep } from 'lodash-es'
import { fieldType } from '@/utils/attr'
import { useAppStoreWithOut } from '@/store/modules/app'
import treeSort from '@/utils/treeSortUtils'
import RowAuth from '@/views/chart/components/editor/filter/auth-tree/RowAuth.vue'

import {
  DEFAULT_CANVAS_STYLE_DATA_LIGHT,
  DEFAULT_CANVAS_STYLE_DATA_SCREEN_DARK
} from '@/views/chart/components/editor/util/dataVisualization'
import type { TabPaneName } from 'element-plus-secondary'
import { timestampFormatDate } from './form/util'
import { interactiveStoreWithOut } from '@/store/modules/interactive'
import { useCache } from '@/hooks/web/useCache'
import { RefreshLeft } from '@element-plus/icons-vue'
import { iconFieldMap } from '@/components/icon-group/field-list'
import { exportPermission, isFreeFolder } from '@/utils/utils'
import { EXPORT_DATASET_RESULT, resolveExportDatasetResult } from './exportDatasetResult.mjs'
const { t } = useI18n()
const interactiveStore = interactiveStoreWithOut()
const { wsCache } = useCache()
interface Field {
  fieldShortName: string
  name: string
  engineFieldName: string
  originName: string
  fieldType: number
}

interface Node {
  name: string
  createBy: string
  creator: string
  id: string
  nodeType: string
  createTime: number
  weight: number
  ext?: number
}
const appStore = useAppStoreWithOut()
// 根目录管理权限标记，用于控制顶层新增、移动和删除等管理动作
const rootManage = ref(false)
// 数据集导出弹窗显示状态
const showExport = ref(false)
// 行权限配置组件引用，导出前用于收集过滤表达式
const rowAuth = ref()
// 数据集导出请求加载状态，防止重复提交
const exportDatasetLoading = ref(false)
// 数据集导出行数上限展示值，由后端配置接口返回
const limit = ref<any>(t('data_set.ten_wan'))
// 数据集导出表单数据，包含文件名和行权限表达式
const exportForm = ref<any>({})
// 导出请求载荷缓存，保存数据集、文件名、行数和表达式树
const table = ref<any>({})
// 导出表单引用，用于提交前触发表单校验
const exportFormRef = ref()
const exportFormRules = {
  name: [
    {
      required: true,
      message: t('commons.input_content'),
      trigger: 'change'
    },
    {
      max: 50,
      message: t('commons.char_can_not_more_50'),
      trigger: 'change'
    }
  ]
}
// 数据集字段清单，提供给行权限组件作为可选字段来源
const datasetTableFiled = ref([])
provide('filedList', datasetTableFiled)

// 数据集树搜索关键字
const nickName = ref('')
const router = useRouter()
const route = useRoute()
// 数据集管理页核心状态，维护资源树和当前排序方式
const state = reactive({
  datasetTree: [] as BusiTreeNode[],
  curSortType: 'time_desc'
})

// 当前要新建的可视化资源类型，创建仪表板或大屏时由资源组件写入
const curCanvasType = ref('')
// 页面是否完成首次资源树加载，用于区分加载态和空态
const mounted = ref(false)
const openType = wsCache.get('open-backend') === '1' ? '_self' : '_blank'
// 当前是否处于嵌入式数据集编辑模式，决定跳转方式
const isCrestBi = computed(() => appStore.getIsCrestBi)
// 当前页面是否运行在 iframe 宿主中，影响高度和嵌入式导出处理
const isIframe = computed(() => appStore.getIsIframe)
// 当前数据集的导出权限，结合节点权重和扩展权限位计算
const exportPermissions = computed(() => exportPermission(nodeInfo.weight, nodeInfo.ext))
// 根据当前数据集上下文打开新建可视化面板，并缓存来源数据集 id
const createPanel = path => {
  const baseUrl = `#/${path}?opt=create&id=${nodeInfo.id}`
  wsCache.set('dataset-info-id', nodeInfo.id)
  window.open(baseUrl, openType)
}

// 资源分组组件完成新建叶子资源后，创建对应的可视化资源记录
const resourceOptFinish = param => {
  if (param && param.opt === 'newLeaf') {
    resourceCreate(param.pid, param.name)
  }
}

// 数据集树排序前的原始副本，排序操作始终基于该副本重新计算
const originResourceTree = shallowRef([])

// 用户切换排序方式时重排资源树，并写入本地缓存
const handleSortTypeChange = sortType => {
  state.datasetTree = treeSort(originResourceTree.value, sortType)
  state.curSortType = sortType
  wsCache.set('TreeSort-dataset', state.curSortType)
}

// 按指定排序方式重排资源树，不写缓存，供初始化和刷新流程复用
const sortTypeChange = sortType => {
  state.datasetTree = treeSort(originResourceTree.value, sortType)
  state.curSortType = sortType
}

// 新建仪表板或大屏资源，并以当前数据集作为初始数据来源
const resourceCreate = (pid, name) => {
  // 可视化资源基础信息与画布初始配置必须在同一次保存中提交
  const newResourceId = guid()
  const bashResourceInfo = {
    dataState: 'ready',
    id: newResourceId,
    name: name,
    pid: pid,
    type: curCanvasType.value,
    status: 1,
    selfWatermarkStatus: true
  }
  const canvasStyleDataNew =
    curCanvasType.value === 'dashboard'
      ? DEFAULT_CANVAS_STYLE_DATA_LIGHT
      : DEFAULT_CANVAS_STYLE_DATA_SCREEN_DARK
  const canvasInfo = {
    canvasStyleData: JSON.stringify(canvasStyleDataNew),
    componentData: JSON.stringify([]),
    canvasViewInfo: {},
    ...bashResourceInfo
  }
  saveVisualization(canvasInfo as any).then(() => {
    const baseUrl = curCanvasType.value === 'dataV' ? '#/dvCanvas?dvId=' : '#/dashboard?resourceId='
    window.open(baseUrl + newResourceId, openType)
  })
}

// 数据集文件夹创建和移动弹窗组件引用
const creatDsFolder = ref()
const defaultNode = {
  name: '',
  createBy: '',
  creator: '',
  id: '',
  nodeType: '',
  createTime: 0,
  weight: 0
}

// 当前选中的数据集节点详情，驱动右侧预览、导出和编辑入口
const nodeInfo = reactive<Node>(cloneDeep(defaultNode))

let allFields = []
let columnsPreview = []
let dataPreview = []

const allFieldsColumns = [
  {
    key: 'name',
    dataKey: 'name',
    title: t('data_set.field_name'),
    width: 250
  },
  {
    key: 'fieldType',
    dataKey: 'fieldType',
    title: t('data_set.field_type'),
    width: 250,
    cellRenderer: ({ cellData: fieldTypeCode }) => (
      <div style={{ width: '100%', display: 'flex', alignItems: 'center' }}>
        <ElIcon style={{ marginRight: '6px' }}>
          <Icon>
            {h(iconFieldMap[fieldType[fieldTypeCode]], {
              class: `svg-icon field-icon-${fieldType[fieldTypeCode]}`
            })}
          </Icon>
        </ElIcon>
        {t(`dataset.${fieldType[fieldTypeCode]}`) +
          `${fieldTypeCode === 3 ? '(' + t('dataset.float') + ')' : ''}`}
      </div>
    )
  },
  {
    key: 'description',
    dataKey: 'description',
    title: t('data_set.field_notes'),
    width: 250
  }
]

// 数据预览加载状态，覆盖数据预览页签的异步请求阶段
const dataPreviewLoading = ref(false)
const { width, node } = useMoveLine('DATASOURCE')

// 详情面板基础信息，统一格式化创建时间
const infoList = computed(() => {
  return {
    creator: nodeInfo.creator,
    createTime: nodeInfo.createTime && timestampFormatDate(nodeInfo.createTime)
  }
})

const { handleDrop, allowDrop, handleDragStart } = treeDraggble(
  state,
  'datasetTree',
  moveDatasetTree,
  'dataset',
  originResourceTree
)

// 将数据集字段元数据转换为预览表格列，并渲染字段类型图标
const generateColumns = (arr: Field[]) =>
  arr.map(ele => ({
    key: ele.engineFieldName,
    fieldType: ele.fieldType,
    dataKey: ele.engineFieldName,
    title: ele.name,
    width: 150,
    headerCellRenderer: ({ column }) => (
      <div class="flex-align-center">
        <ElIcon style={{ marginRight: '6px' }}>
          <Icon>
            {h(iconFieldMap[fieldType[column.fieldType]], {
              class: `svg-icon field-icon-${fieldType[column.fieldType]}`
            })}
          </Icon>
        </ElIcon>
        <span class="ellipsis" title={column.title} style={{ width: '120px' }}>
          {column.title}
        </span>
      </div>
    )
  }))

// 数据集树加载状态，覆盖首次加载、刷新和排序恢复阶段
const dtLoading = ref(false)
// 首次恢复选中节点的标记，避免刷新树时重复设置当前节点
const isCreated = ref(false)
// 加载数据集资源树，处理根目录权限、排序、搜索过滤和选中节点恢复
const getData = () => {
  dtLoading.value = true
  const curSortType = getStoredSortType('TreeSort-dataset', getDefaultSortType())
  const request = { busiFlag: 'dataset' } as BusiTreeRequest
  interactiveStore
    .setInteractive(request)
    .then(res => {
      const nodeData = (res as unknown as BusiTreeNode[]) || []
      if (nodeData.length && nodeData[0]['id'] === '0' && nodeData[0]['name'] === 'root') {
        rootManage.value = nodeData[0]['weight'] >= 7
        state.datasetTree = nodeData[0]['children'] || []
        originResourceTree.value = cloneDeep(unref(state.datasetTree))
        sortTypeChange(curSortType)
        return
      }
      state.datasetTree = nodeData
      originResourceTree.value = cloneDeep(unref(state.datasetTree))
      sortTypeChange(curSortType)
    })
    .finally(() => {
      dtLoading.value = false
      mounted.value = true
      nextTick(() => {
        if (!!nickName.value.length) {
          datasetListTree.value.filter(nickName.value)
        }
      })
      const id = nodeInfo.id
      if (!!id) {
        Object.assign(nodeInfo, cloneDeep(defaultNode))
        dfsDatasetTree(state.datasetTree, id)
        nextTick(() => {
          if (isCreated.value) return
          isCreated.value = true
          datasetListTree.value.setCurrentKey(id, true)
        })
      }
    })
}

// 深度查找数据集树中的目标节点，并触发与用户点击一致的详情加载流程
const dfsDatasetTree = (ds, id) => {
  ds.some(ele => {
    if (ele.id === id) {
      handleNodeClick(ele)
      return true
    }
    if (!!ele.children?.length) {
      dfsDatasetTree(ele.children, id)
    }
    return false
  })
}

onBeforeMount(() => {
  const paramId = wsCache.get('dataset-info-id') || route.params.id
  nodeInfo.id = (paramId as string) || (route.query.id as string) || ''
  wsCache.delete('dataset-info-id')
  wsCache.delete('db-info-id')
  wsCache.delete('dv-info-id')
  loadInit()
  getData()
  getLimit()
})

// 数据预览或结构预览的表格列配置
const columns = shallowRef([])
// 数据预览或结构预览的表格行数据
const tableData = shallowRef([])
// 当前数据集总行数，预览页签异步补充展示
const total = ref(null)

// 点击数据集树叶子节点后加载详情，并重置预览缓存
const handleNodeClick = (data: BusiTreeNode) => {
  if (!data.leaf) {
    datasetListTree.value.setCurrentKey(null)
    return
  }
  barInfoApi(data.id).then(res => {
    const nodeData = res as any
    Object.assign(nodeInfo, nodeData)
    nodeInfo.weight = data.weight
    nodeInfo.ext = data.ext || 0
    columnsPreview = []
    dataPreview = []
    activeName.value = 'dataPreview'
    handleClick(activeName.value)
  })
}

// 打开导出弹窗，并初始化行权限表达式组件
const exportDataset = () => {
  showExport.value = true
  exportForm.value.name = nodeInfo.name
  exportForm.value.expressionTree = ''
  nextTick(() => {
    rowAuth.value.init({})
    rowAuth.value.relationList = []
    rowAuth.value.logic = 'or'
  })
}

// 关闭导出弹窗
const closeExport = () => {
  showExport.value = false
}

// 接收行权限组件提交结果，组装导出载荷并处理下载或导出中心提示
const save = ({ logic, items, errorMessage }: any) => {
  if (exportDatasetLoading.value) {
    return
  }
  table.value.id = nodeInfo.id
  table.value.row = 100000
  table.value.filename = exportForm.value.name
  table.value.embeddedExport = isCrestBi.value || appStore.getIsIframe
  if (errorMessage) {
    ElMessage.error(errorMessage)
    return
  }
  table.value.expressionTree = JSON.stringify({ items, logic })
  let exportSucceeded = false
  exportDatasetLoading.value = true
  exportDatasetData(table.value)
    .then(async res => {
      const result = await resolveExportDatasetResult(res)
      if (result.status === EXPORT_DATASET_RESULT.CANCELED) {
        ElMessage.warning(
          result.message && result.message !== 'request canceled'
            ? result.message
            : '导出请求已取消，请重试'
        )
        return
      }
      if (result.status === EXPORT_DATASET_RESULT.FAILED) {
        ElMessage.error(result.message || '数据集导出请求失败，请稍后再试')
        return
      }
      exportSucceeded = true
      if (isCrestBi.value || appStore.getIsIframe) {
        const blob = new Blob([res.data], { type: 'application/vnd.ms-excel' })
        const link = document.createElement('a')
        link.style.display = 'none'
        link.href = URL.createObjectURL(blob)
        link.download = table.value.filename + '.xlsx' // 下载的文件名
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
      } else {
        openMessageLoading(exportData)
      }
    })
    .catch(error => {
      const message = typeof error === 'string' ? error : error?.message
      ElMessage.error(message || '数据集导出请求失败，请稍后再试')
    })
    .finally(() => {
      exportDatasetLoading.value = false
      if (exportSucceeded) {
        showExport.value = false
      }
    })
}

// 提交导出表单，表单校验通过后交由行权限组件生成表达式树
const exportDatasetRequest = () => {
  if (exportDatasetLoading.value) {
    return
  }
  exportFormRef.value.validate(valid => {
    if (valid) {
      rowAuth.value.submit()
    } else {
      return false
    }
  })
}

// 通知导出中心切换到进行中列表，方便用户查看异步导出进度
const exportData = () => {
  useEmitt().emitter.emit('data-export-center', { activeName: 'IN_PROGRESS' })
}

// 展开或收起字段说明单元格，解决长文本在表格内的临时查看需求
const rowClick = (_, __, event) => {
  const element = event.target.parentNode.parentNode
  if ([...element.classList].includes('no-hide')) {
    element.classList.remove('no-hide')
    return
  }
  element.classList.add('no-hide')
}

// 展示导出进度提示，并提供跳转导出中心的快捷动作
const openMessageLoading = cb => {
  const iconClass = `el-icon-loading`
  const customClass = `crest-message-loading crest-message-export`
  ElMessage({
    message: h('p', null, [
      t('data_set.can_go_to'),
      h(
        ElButton,
        {
          text: true,
          size: 'small',
          class: 'btn-text',
          onClick: () => {
            cb()
          }
        },
        t('data_export.export_center')
      ),
      t('data_set.progress_and_download')
    ]),
    iconClass,
    icon: h(RefreshLeft),
    showClose: true,
    customClass
  } as any)
}

// 从详情页编辑当前数据集
const editorDataset = () => {
  handleEdit(nodeInfo.id)
}
const embedded = useEmbedded()

// 打开数据集编辑页，兼容嵌入式模式和普通路由模式
const handleEdit = id => {
  if (isCrestBi.value) {
    embedded.clearState()
    embedded.setDatasetId(id as string)
    useEmitt().emitter.emit('changeCurrentComponent', 'DatasetEditor')
    return
  }
  router.push({
    path: '/dataset-form',
    query: {
      id
    }
  })
}

// 打开新建数据集流程，并携带可选父文件夹
const createDataset = (data?: BusiTreeNode) => {
  if (isCrestBi.value) {
    embedded.clearState()
    embedded.setdatasetPid(data?.id as string)
    useEmitt().emitter.emit('changeCurrentComponent', 'DatasetEditor')
    return
  }
  const query = data?.id ? { pid: data.id } : {}
  router.push({
    path: '/dataset-form',
    query
  })
}

// 切换详情页签，按需加载数据预览、结构预览和统计信息
const handleClick = (tabName: TabPaneName) => {
  switch (tabName) {
    case 'dataPreview':
      if (columnsPreview.length) {
        columns.value = columnsPreview
        tableData.value = dataPreview
        break
      }
      dataPreviewLoading.value = true
      total.value = null
      datasetPreview(nodeInfo.id)
        .then(res => {
          allFields = (res?.allFields as unknown as Field[]) || []
          datasetTableFiled.value = allFields
          columnsPreview = generateColumns((res?.data?.fields as Field[]) || [])
          dataPreview = (res?.data?.data as Array<{}>) || []
          columns.value = columnsPreview
          tableData.value = dataPreview
        })
        .finally(() => {
          dataPreviewLoading.value = false
        })
      datasetTotal(nodeInfo.id).then(res => {
        total.value = res
      })
      break
    case 'structPreview':
      columns.value = allFieldsColumns
      tableData.value = allFields
      break
    case 'row':
      break
    case 'column':
      break
    default:
      break
  }
}
// 血缘关系图组件引用，用于删除数据集前展示影响范围
const relationChartRef = ref()
// 处理数据集树更多菜单命令，覆盖复制、删除、移动和重命名等操作
const operation = (cmd: string, data: BusiTreeNode, nodeType: string) => {
  if (cmd === 'copy') {
    if (isCrestBi.value) {
      embedded.clearState()
      embedded.setDatasetCopyId(data.id as string)
      useEmitt().emitter.emit('changeCurrentComponent', 'DatasetEditor')
      return
    }
    router.push({
      name: embedded.getToken && appStore.getIsIframe ? 'dataset-embedded-form' : 'dataset-form',
      query: { copyId: data.id }
    })
    return
  }
  if (cmd === 'delete') {
    let options = {
      confirmButtonType: 'danger',
      type: 'warning',
      autofocus: false,
      showClose: false,
      tip: ''
    }

    if (!!data.children?.length) {
      options.tip = t('data_set.operate_with_caution')
    } else {
      delete options.tip
    }

    if (nodeType !== 'folder') {
      perDelete(data.id).then(res => {
        if (res === true) {
          // 删除确认弹窗中的血缘查看动作，延迟到用户主动点击时再查询图数据
          const onClick = () => {
            relationChartRef.value.getChartData({
              queryType: 'dataset',
              num: data.id,
              label: data.name
            })
          }

          ElMessageBox.confirm('', {
            confirmButtonType: 'danger',
            type: 'warning',
            autofocus: false,
            confirmButtonText: t('userimport.sure'),
            showClose: false,
            dangerouslyUseHTMLString: true,
            message: h('div', null, [
              h('p', { style: 'margin-bottom: 8px;' }, t('data_set.this_data_set')),
              h('p', { class: 'tip' }, t('data_set.to_delete_them')),
              h(
                ElButton,
                { text: true, onClick: onClick, style: 'margin-left: -4px;' },
                t('data_set.check_blood_relationship')
              )
            ])
          }).then(() => {
            delDatasetTree(data.id).then(() => {
              getData()
              ElMessage.success(t('dataset.delete_success'))
            })
          })
        } else {
          ElMessageBox.confirm(
            t('datasource.delete_this_dataset'),
            options as ElMessageBoxOptions
          ).then(() => {
            delDatasetTree(data.id).then(() => {
              getData()
              ElMessage.success(t('dataset.delete_success'))
            })
          })
        }
      })
    } else {
      ElMessageBox.confirm(t('data_set.delete_this_folder'), options as ElMessageBoxOptions).then(
        () => {
          delDatasetTree(data.id).then(() => {
            getData()
            ElMessage.success(t('dataset.delete_success'))
          })
        }
      )
    }
  } else {
    creatDsFolder.value.createInit(nodeType, data, cmd)
  }
}

// 处理数据集树的新建命令，在数据集和文件夹入口之间分发
const handleDatasetTree = (cmd: string, data?: BusiTreeNode) => {
  if (cmd === 'dataset') {
    createDataset(data)
  }
  if (cmd === 'folder') {
    creatDsFolder.value.createInit(cmd, data || {})
  }
}

// 当前详情页签，默认展示数据预览
const activeName = ref('dataPreview')

const menuList = [
  {
    label: t('visualization.move_to'),
    svgName: icon_intoItem_outlined,
    command: 'move'
  },
  {
    label: t('visualization.rename'),
    svgName: icon_rename_outlined,
    command: 'rename'
  },
  {
    label: t('common.delete'),
    divided: true,
    svgName: icon_deleteTrash_outlined,
    command: 'delete'
  }
]
// 当前展开的数据集树节点键集合
const expandedKey = ref([])

// 记录用户展开的树节点，刷新后用于保留展开状态
const nodeExpand = data => {
  if (data.id) {
    expandedKey.value.push(data.id)
  }
}

// 移除用户折叠的树节点键，保持展开状态集合与界面一致
const nodeCollapse = data => {
  if (data.id) {
    expandedKey.value.splice(expandedKey.value.indexOf(data.id), 1)
  }
}

// 新建菜单项，提供数据集和文件夹两个入口
const datasetTypeList = computed(() => {
  return [
    {
      label: t('data_set.a_new_dataset'),
      svgName: icon_dataset,
      command: 'dataset'
    },
    {
      label: t('datasetUi.new_folder'),
      divided: true,
      svgName: dvFolder,
      command: 'folder'
    }
  ]
})

// 数据集树组件字段映射，约定子节点和展示名称字段
const defaultProps = {
  children: 'children',
  label: 'name'
}

const defaultTab = [
  {
    title: t('chart.data_preview'),
    name: 'dataPreview'
  },
  {
    title: t('data_set.structure_preview'),
    name: 'structPreview'
  }
]

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

// 读取旧版排序缓存并映射为当前排序值，不可用时回落到默认时间倒序
const getDefaultSortType = () => {
  const sortIndex = Number(wsCache.get('TreeSort-backend') ?? 1)
  return sortList[Number.isInteger(sortIndex) && sortList[sortIndex] ? sortIndex : 1].value
}

// 从指定缓存键读取排序值，并校验其仍存在于当前排序选项中
const getStoredSortType = (cacheKey: string, fallback: string) => {
  const sortType = wsCache.get(cacheKey)
  return sortList.some(ele => ele.value === sortType) ? sortType : fallback
}

// 页面初始化时恢复缓存中的数据集树排序方式
const loadInit = () => {
  state.curSortType = getStoredSortType('TreeSort-dataset', state.curSortType)
}

// 读取数据集导出行数上限，用于导出弹窗提示
const getLimit = () => {
  exportLimit().then(res => {
    limit.value = res
  })
}

// 当前排序方式的展示文案，用于排序入口提示
const sortTypeTip = computed(() => {
  return sortList.find(ele => ele.value === state.curSortType)?.name ?? sortList[1].name
})

// 由详情扩展组件动态注入的页签列表
const tablePanes = ref([])
// 当前可展示的详情页签，低权限节点只展示默认页签
const tablePaneList = computed(() => {
  return nodeInfo.weight >= 7 ? [...defaultTab, ...tablePanes.value] : [...defaultTab]
})
// 接收详情扩展组件加载出的页签配置
const panelLoad = paneInfo => {
  tablePanes.value = paneInfo
}
// 数据集树组件引用，用于过滤和设置当前节点
const datasetListTree = ref()

// 数据集树搜索关键字变化时，调用树组件过滤方法刷新可见节点
watch(nickName, (val: string) => {
  datasetListTree.value.filter(val)
})
// 左侧数据集树展开状态，控制侧栏显示和箭头位置
const sideTreeStatus = ref(true)
// 接收侧栏展开收起组件的状态变化
const changeSideTreeStatus = val => {
  sideTreeStatus.value = val
}

// 数据集树节点过滤规则，按节点名称做不区分大小写的包含匹配
const filterNode = (value: string, data: BusiTreeNode) => {
  if (!value) return true
  return data.name?.toLowerCase().includes(value.toLowerCase())
}
// 根据节点类型返回更多菜单，文件夹不展示复制动作
const getMenuList = (val: boolean) => {
  return !val
    ? menuList
    : [
        {
          label: t('common.copy'),
          svgName: icon_copy_filled,
          command: 'copy'
        }
      ].concat(menuList)
}

// 拖拽放置代理，在免费文件夹限制生效时阻止跨类型保存
const proxyAllowDrop = throttle((arg1, arg2) => {
  const flagArray = ['dashboard', 'dataV', 'dataset', 'datasource']
  const flag = flagArray.findIndex(item => item === 'dataset')
  if (flag < 0 || !isFreeFolder(arg2, flag + 1)) {
    return allowDrop(arg1, arg2)
  }
  ElMessage.warning(t('free.save_error'))
  return false
}, 300)
</script>

<template>
  <div class="dataset-manage" :class="isIframe && 'crest-100vh'" v-loading="dtLoading">
    <ArrowSide
      :style="{ left: (sideTreeStatus ? width - 12 : 0) + 'px' }"
      @change-side-tree-status="changeSideTreeStatus"
      :isInside="!sideTreeStatus"
    ></ArrowSide>
    <el-aside
      class="resource-area"
      :class="{ retract: !sideTreeStatus }"
      ref="node"
      :style="{ width: width + 'px' }"
    >
      <div class="resource-tree">
        <div class="tree-header">
          <div class="icon-methods">
            <span class="title"> {{ t('auth.dataset') }} </span>
            <div v-if="rootManage" class="flex-align-center">
              <el-tooltip
                class="box-item"
                effect="dark"
                offset="14"
                popper-class="new-folder_tip"
                :content="t('datasetUi.new_folder')"
                arrow-offset="10"
                placement="top"
              >
                <el-icon
                  class="custom-icon btn"
                  style="margin-right: 20px"
                  @click="handleDatasetTree('folder')"
                >
                  <Icon name="dv-new-folder"><dvNewFolder class="svg-icon" /></Icon>
                </el-icon>
              </el-tooltip>
              <el-tooltip
                class="box-item"
                effect="dark"
                popper-class="new-folder_tip"
                offset="14"
                arrow-offset="10"
                :content="t('data_set.a_new_dataset')"
                placement="top"
              >
                <el-icon class="custom-icon btn" @click="createDataset">
                  <Icon name="icon_file-add_outlined"
                    ><icon_fileAdd_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </el-tooltip>
            </div>
          </div>
          <el-input
            :placeholder="t('commons.search')"
            v-model="nickName"
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
            <el-icon class="filter-icon-span">
              <el-tooltip :offset="16" effect="dark" :content="sortTypeTip" placement="top">
                <Icon name="dv-sort-asc" class="opt-icon"
                  ><dvSortAsc v-if="state.curSortType.includes('asc')" class="svg-icon opt-icon"
                /></Icon>
              </el-tooltip>
              <el-tooltip :offset="16" effect="dark" :content="sortTypeTip" placement="top">
                <Icon name="dv-sort-desc" class="opt-icon"
                  ><dvSortDesc v-if="state.curSortType.includes('desc')" class="svg-icon"
                /></Icon>
              </el-tooltip>
            </el-icon>
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

        <el-scrollbar class="custom-tree">
          <el-tree
            menu
            ref="datasetListTree"
            node-key="id"
            :data="state.datasetTree"
            :filter-node-method="filterNode"
            expand-on-click-node
            highlight-current
            @node-drag-start="handleDragStart"
            :allow-drop="proxyAllowDrop"
            @node-drop="handleDrop"
            draggable
            @node-expand="nodeExpand"
            @node-collapse="nodeCollapse"
            :default-expanded-keys="expandedKey"
            :props="defaultProps"
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <span class="custom-tree-node">
                <el-icon v-if="!data.leaf" style="font-size: 18px">
                  <Icon name="dv-folder"><dvFolder class="svg-icon" /></Icon>
                </el-icon>
                <el-icon v-if="data.leaf" style="font-size: 18px">
                  <Icon name="icon_dataset"><icon_dataset class="svg-icon" /></Icon>
                </el-icon>
                <span :title="node.label" class="label-tooltip ellipsis">{{ node.label }}</span>
                <div class="icon-more" v-if="data.weight >= 7">
                  <handle-more
                    icon-size="24px"
                    @handle-command="cmd => handleDatasetTree(cmd, data)"
                    :menu-list="datasetTypeList"
                    :icon-name="icon_add_outlined"
                    placement="bottom-start"
                    v-if="!data.leaf"
                  ></handle-more>
                  <el-icon v-else class="hover-icon" @click.stop="handleEdit(data.id)">
                    <icon name="icon_edit_outlined"><icon_edit_outlined class="svg-icon" /></icon>
                  </el-icon>
                  <handle-more
                    @handle-command="cmd => operation(cmd, data, data.leaf ? 'dataset' : 'folder')"
                    :menu-list="getMenuList(data.leaf)"
                  ></handle-more>
                </div>
              </span>
            </template>
          </el-tree>
        </el-scrollbar>
      </div>
    </el-aside>

    <div
      class="dataset-content"
      :class="{
        auto: isIframe || isCrestBi
      }"
    >
      <template v-if="!state.datasetTree.length && mounted">
        <empty-background :description="t('data_set.data_set_yet')" img-type="none">
          <el-button v-if="rootManage" @click="() => createDataset()" type="primary">
            <template #icon>
              <Icon name="icon_add_outlined"><icon_add_outlined class="svg-icon" /></Icon>
            </template>
            {{ t('datasetUi.create') + t('auth.dataset') }}</el-button
          >
        </empty-background>
      </template>
      <template v-else-if="!!nodeInfo.id">
        <div class="dataset-info">
          <div class="info-method">
            <span :title="nodeInfo.name" class="dataset-name ellipsis">{{ nodeInfo.name }}</span>
            <el-divider style="margin: 0 12px" direction="vertical" />
            <span class="create-user">
              {{ t('visualization.create_by') }}:{{ nodeInfo.creator }}
            </span>

            <el-popover show-arrow :offset="8" placement="bottom" width="290" trigger="hover">
              <template #reference>
                <el-icon size="16px" class="create-user">
                  <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
                </el-icon>
              </template>
              <dataset-detail
                :create-time="infoList.createTime"
                :creator="infoList.creator"
              ></dataset-detail>
            </el-popover>
            <div class="right-btn">
              <el-button secondary @click="createPanel('dashboard')" v-permission="['panel']">
                <template #icon>
                  <Icon name="icon_dashboard_outlined"
                    ><icon_dashboard_outlined class="svg-icon"
                  /></Icon>
                </template>
                {{ t('visualization.panelAdd') }}
              </el-button>
              <el-button secondary @click="createPanel('dvCanvas')" v-permission="['screen']">
                <template #icon>
                  <Icon name="icon_operation-analysis_outlined"
                    ><icon_operationAnalysis_outlined class="svg-icon"
                  /></Icon> </template
                >{{ t('data_set.new_data_screen') }}
              </el-button>
              <el-button v-if="exportPermissions[0]" secondary @click="exportDataset">
                <template #icon>
                  <Icon name="icon_download_outlined"
                    ><icon_download_outlined class="svg-icon"
                  /></Icon>
                </template>
                {{ t('data_set.dataset_export') }}
              </el-button>
              <el-button type="primary" @click="editorDataset" v-if="nodeInfo.weight >= 7">
                <template #icon>
                  <Icon name="icon_edit_outlined"><icon_edit_outlined class="svg-icon" /></Icon>
                </template>
                {{ t('visualization.edit') }}
              </el-button>
            </div>
          </div>
          <div class="tab-border">
            <el-tabs v-model="activeName" @tab-change="handleClick">
              <el-tab-pane
                v-for="ele in tablePaneList"
                :key="ele.name"
                :label="ele.title"
                :name="ele.name"
              ></el-tab-pane>
            </el-tabs>
          </div>
        </div>
        <div class="dataset-table-info">
          <div v-if="activeName === 'dataPreview'" class="preview-num">
            {{ t('data_set.pieces_in_total', { msg: total }) }}
          </div>
          <template v-if="['dataPreview', 'structPreview'].includes(activeName)">
            <div class="info-table" :class="[{ 'struct-preview': activeName === 'structPreview' }]">
              <el-auto-resizer v-if="activeName === 'structPreview'">
                <template #default="{ height, width }">
                  <el-table-v2
                    class="crest-data-table-v2"
                    key="structPreview"
                    :columns="columns"
                    v-loading="dataPreviewLoading"
                    :data="tableData"
                    header-class="excel-header-cell"
                    :width="width"
                    :height="height"
                    fixed
                    ><template #empty>
                      <empty-background
                        :description="t('data_set.no_data')"
                        img-type="noneWhite"
                      /> </template
                  ></el-table-v2>
                </template>
              </el-auto-resizer>
              <template v-if="activeName === 'dataPreview'">
                <el-table
                  v-loading="dataPreviewLoading"
                  class="dataset-preview_table crest-data-table"
                  :data="tableData"
                  @row-click="rowClick"
                  key="dataPreview"
                  style="width: 100%; height: 100%"
                >
                  <el-table-column
                    :key="column.dataKey"
                    v-for="(column, index) in columns"
                    :prop="column.dataKey"
                    :label="column.title"
                    :min-width="150"
                    :fixed="columns.length - 1 === index ? 'right' : false"
                  >
                    <template #header>
                      <div class="flex-align-center">
                        <ElIcon style="margin-right: 6px">
                          <Icon :className="`field-icon-${fieldType[column.fieldType]}`"
                            ><component
                              class="svg-icon"
                              :class="`field-icon-${fieldType[column.fieldType]}`"
                              :is="iconFieldMap[fieldType[column.fieldType]]"
                            ></component
                          ></Icon>
                        </ElIcon>
                        <span class="ellipsis" :title="column.title" style="width: 120px">
                          {{ column.title }}
                        </span>
                      </div>
                    </template>
                  </el-table-column>
                  <template #empty>
                    <empty-background :description="t('data_set.no_data')" img-type="noneWhite" />
                  </template>
                </el-table>
              </template>
            </div>
          </template>
          <template v-if="['row', 'column'].includes(activeName)">
            <div class="table-row-column"></div>
          </template>
        </div>
      </template>
      <template v-else-if="mounted">
        <empty-background :description="t('datasetUi.on_the_left')" img-type="select" />
      </template>
    </div>
    <relationChart ref="relationChartRef"></relationChart>
    <CrestResourceGroupActions
      :cur-canvas-type="curCanvasType"
      @finish="resourceOptFinish"
      ref="resourceGroupOpt"
    ></CrestResourceGroupActions>
    <creat-ds-group @finish="getData()" ref="creatDsFolder"></creat-ds-group>
  </div>
  <!--导出数据集弹框-->
  <el-dialog
    v-if="showExport"
    v-model="showExport"
    width="800px"
    class="crest-dialog-form form-tree-cont"
    :title="$t('dataset.export_dataset')"
    append-to-body
  >
    <el-form
      ref="exportFormRef"
      class="crest-form-item"
      @submit.prevent
      :model="exportForm"
      :rules="exportFormRules"
      :before-close="closeExport"
    >
      <el-form-item :label="$t('dataset.filename')" prop="name">
        <el-input v-model.trim="exportForm.name" :placeholder="$t('dataset.pls_input_filename')" />
      </el-form-item>
      <el-form-item :label="$t('dataset.export_filter')" prop="expressionTree">
        <div class="tree-cont">
          <div class="content">
            <RowAuth @save="save" ref="rowAuth" />
          </div>
        </div>
      </el-form-item>
    </el-form>
    <span class="tip">{{ t('data_set.pieces_of_data', { limit: limit }) }}</span>
    <template v-slot:footer>
      <div class="dialog-footer">
        <el-button secondary @click="closeExport">{{ $t('dataset.cancel') }} </el-button>
        <el-button
          v-loading="exportDatasetLoading"
          type="primary"
          :disabled="exportDatasetLoading"
          @click="exportDatasetRequest"
          >{{ $t('dataset.confirm') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="less" scoped>
@import '@/style/mixin.less';

:deep(.dataset-preview_table) {
  .ed-table__body {
    .ed-table__row:not(.no-hide) {
      .cell {
        white-space: nowrap;
      }
    }
  }
}

.ed-table {
  --ed-table-header-bg-color: #f5f6f7;
}
.form-tree-cont {
  .tree-cont {
    height: 200px;
    width: 100%;
    padding: 16px;
    border-radius: 6px;
    border: 1px solid var(--crestBorderBase, #dcdfe6);
    overflow: auto;

    .content {
      height: 100%;
      width: 100%;
    }
  }
}
.filter-icon-span {
  border: 1px solid #d9dcdf;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  color: #1f2329;
  padding: 8px;
  margin-left: 8px;
  font-size: 16px;
  cursor: pointer;

  .opt-icon:focus {
    outline: none !important;
  }
  &:hover {
    background: #f5f6f7;
  }

  &:active {
    background: #eff0f1;
  }
}
.custom-tree {
  height: calc(100vh - 172px);
  padding: 0 8px;
}
.dataset-manage {
  display: flex;
  width: 100%;
  height: 100%;
  background: #fff;
  position: relative;

  &.crest-100vh {
    height: 100vh;
    .custom-tree {
      height: calc(100vh - 122px);
    }
  }

  .resource-area {
    position: relative;
    height: 100%;
    width: 279px;
    padding: 0;
    border-right: 1px solid #d7d7d7;
    overflow: visible;
    &.retract {
      display: none;
    }

    .resource-tree {
      padding: 16px 0 0;
      width: 100%;
      height: 100%;
      display: flex;
      flex-direction: column;

      .tree-header {
        padding: 0 16px;
      }

      .icon-methods {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        font-size: 20px;
        font-weight: 500;
        color: var(--TextPrimary, #1f2329);
        padding-bottom: 16px;

        .title {
          margin-right: auto;
          font-size: 16px;
          font-style: normal;
          font-weight: 500;
          line-height: 24px;
        }

        .custom-icon {
          &.btn {
            color: var(--ed-color-primary);
          }

          &:hover {
            cursor: pointer;
            &::after {
              content: '';
              background-color: var(--ed-color-primary-1a, #3b82f61a);
              width: 28px;
              height: 28px;
              position: absolute;
              top: 50%;
              left: 50%;
              border-radius: 6px;
              transform: translate(-50%, -50%);
            }
          }
        }
      }

      .search-bar {
        padding-bottom: 10px;
        width: calc(100% - 40px);
      }
    }
  }

  .dataset-height,
  .dataset-content {
    height: calc(100vh - 56px);
    overflow: auto;
    position: relative;
  }

  .dataset-content {
    background: #f5f6f7;
    &.auto {
      height: auto;
    }

    :deep(.ed-table-v2__header-cell) {
      background-color: #f5f6f7 !important;
    }
  }

  .dataset-list {
    width: 279px;
    padding: 16px 8px;
  }

  .dataset-content {
    flex: 1;
    position: relative;

    .dataset-info {
      background: #fff;
      padding: 0 24px;
      padding-top: 12px;
      height: 90px;
      .info-method {
        height: 32px;
        width: 100%;
        display: flex;
        align-items: center;
        font-family: var(--crest-custom_font, 'PingFang');
        font-size: 16px;
        font-weight: 500;

        .dataset-name {
          max-width: 200px;
        }

        .create-user {
          font-size: 14px;
          font-weight: 400;
          line-height: 22px;
          color: #646a73;
          margin-right: 4px;
        }

        .mr8 {
          margin-left: 8px;
        }

        .right-btn {
          margin-left: auto;
        }
      }
      .tab-border {
        .border-bottom-tab(24px);
        :deep(.ed-tabs__item) {
          font-size: 14px;
        }
        :deep(.ed-tabs__nav-wrap::after) {
          border-color: rgba(31, 35, 41, 0.15);
        }
        margin-left: 0;
      }
    }

    .dataset-table-info {
      padding: 24px;
      margin: 24px;
      background: #fff;
      border-radius: 6px;
      height: calc(100% - 138px);
    }

    .preview-num {
      color: var(--crestTextSecondary, #606266);
      font-family: var(--crest-custom_font, 'PingFang');
      font-size: 14px;
      font-weight: 400;
      line-height: 22px;
      margin-bottom: 16px;
    }

    .info-table {
      height: calc(100% - 37px);
    }

    .struct-preview {
      height: 100%;
    }

    .table-row-column {
      height: calc(100% - 50px);
      :deep(.add-row-column) {
        margin-bottom: 16px;
      }
    }
  }
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
}

.dataset-manage {
  background: #f8fafc;
  color: #0f172a;
  font-family: var(--crest-font-sans);

  .resource-area {
    border-right: 1px solid #e2e8f0;
    background: #ffffff;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
    z-index: 4;

    .resource-tree {
      padding: 18px 12px 0;
      background: #ffffff;

      .tree-header {
        padding: 0 4px 14px;
        border-bottom: 1px solid #e2e8f0;
      }

      .icon-methods {
        color: #0f172a;
        font-weight: 700;
        padding-bottom: 14px;

        .title {
          font-weight: 700;
        }

        .custom-icon.btn {
          width: 34px;
          height: 34px;
          border: 1px solid #e2e8f0;
          border-radius: 10px;
          background: #ffffff;
          color: #3b82f6;
          display: inline-flex;
          align-items: center;
          justify-content: center;
          box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);

          &:hover {
            border-color: #bfdbfe;
            background: #eff6ff;

            &::after {
              content: none;
            }
          }
        }
      }

      .search-bar {
        width: calc(100% - 42px);
        padding-bottom: 0;
      }
    }
  }

  .dataset-content {
    background: #f8fafc;

    .dataset-info {
      height: 88px;
      margin: 22px 24px 0;
      padding: 12px 18px 0;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      background: #ffffff;
      box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);

      .info-method {
        color: #0f172a;
        font-family: var(--crest-font-sans);
        font-weight: 700;

        .dataset-name {
          font-size: 16px;
          font-weight: 700;
        }

        .create-user {
          color: #64748b;
          font-family: var(--crest-font-sans);
        }
      }

      .tab-border {
        :deep(.ed-tabs__item) {
          color: #64748b;
          font-weight: 600;
        }

        :deep(.ed-tabs__item.is-active) {
          color: #3b82f6;
        }

        :deep(.ed-tabs__active-bar) {
          background: #3b82f6;
        }

        :deep(.ed-tabs__header::before),
        :deep(.ed-tabs__header::after),
        :deep(.ed-tabs__nav-wrap::after) {
          display: none;
        }
      }
    }

    .dataset-table-info {
      margin: 16px 24px 24px;
      padding: 18px;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      background: #ffffff;
      box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
      height: calc(100% - 126px);
    }
  }
}

.filter-icon-span {
  border: 1px solid #e2e8f0;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: #334155;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);

  &:hover {
    color: #3b82f6;
    border-color: #bfdbfe;
    background: #eff6ff;
  }
}

.custom-tree {
  height: calc(100vh - 158px);
  padding: 10px 0 0;
}

.custom-tree-node {
  .label-tooltip {
    color: #334155;
    font-weight: 500;
  }
}

:deep(.ed-input__wrapper) {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  background: #ffffff;

  &:hover {
    border-color: #bfdbfe;
  }

  &.is-focus {
    border-color: #3b82f6;
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.14);
  }
}

:deep(.ed-tree) {
  --ed-tree-node-hover-bg-color: #f8fafc;
  color: #334155;
  background: transparent;
}

:deep(.ed-tree-node__content) {
  height: 36px;
  margin-bottom: 4px;
  border-radius: 10px;

  &:hover {
    background: #f8fafc;
  }
}

:deep(.ed-tree--highlight-current .ed-tree-node.is-current > .ed-tree-node__content) {
  background: #eff6ff;
  color: #3b82f6;
  box-shadow: inset 3px 0 0 #3b82f6;

  .label-tooltip {
    color: #3b82f6;
  }
}

:deep(.ed-button) {
  border-radius: 10px;
  font-family: var(--crest-font-sans);
  font-weight: 600;
}

:deep(.ed-button:not(.ed-button--primary)) {
  border-color: #e2e8f0;
  background: #ffffff;
  color: #334155;

  &:hover,
  &:focus {
    border-color: #bfdbfe;
    background: #eff6ff;
    color: #3b82f6;
  }
}

:deep(.ed-table),
:deep(.ed-table-v2) {
  color: #334155;
  font-family: var(--crest-font-sans);
}

:deep(.ed-table__inner-wrapper::before),
:deep(.ed-table__inner-wrapper::after) {
  display: none;
}

:deep(.ed-table th.ed-table__cell),
:deep(.ed-table-v2__header-cell) {
  background: #ffffff !important;
  color: #94a3b8;
  font-family: var(--crest-font-mono);
  font-size: 12px;
  font-weight: 600;
  border-bottom: 1px solid #f1f5f9;
}

:deep(.ed-table td.ed-table__cell) {
  border-bottom: 1px solid #f1f5f9;
}

:deep(.ed-table__row:hover > td.ed-table__cell) {
  background: #fafbfc;
}
</style>
