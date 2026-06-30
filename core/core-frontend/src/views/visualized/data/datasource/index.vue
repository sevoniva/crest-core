<script lang="tsx" setup>
import icon_down_outlined1 from '@/assets/svg/icon_down_outlined-1.svg'
import icon_down_outlined from '@/assets/svg/icon_down_outlined.svg'
import icon_copy_filled from '@/assets/svg/icon_copy_filled.svg'
import icon_dataset from '@/assets/svg/icon_dataset.svg'
import icon_deleteTrash_outlined from '@/assets/svg/icon_delete-trash_outlined.svg'
import icon_intoItem_outlined from '@/assets/svg/icon_into-item_outlined.svg'
import { throttle } from 'lodash-es'
import icon_rename_outlined from '@/assets/svg/icon_rename_outlined.svg'
import icon_warning_colorful_red from '@/assets/svg/icon_warning_colorful_red.svg'
import dvFolder from '@/assets/svg/dv-folder.svg'
import dvNewFolder from '@/assets/svg/dv-new-folder.svg'
import icon_fileAdd_outlined from '@/assets/svg/icon_file-add_outlined.svg'
import icon_searchOutline_outlined from '@/assets/svg/icon_search-outline_outlined.svg'
import dvSortAsc from '@/assets/svg/dv-sort-asc.svg'
import dvSortDesc from '@/assets/svg/dv-sort-desc.svg'
import icon_add_outlined from '@/assets/svg/icon_add_outlined.svg'
import icon_info_outlined from '@/assets/svg/icon_info_outlined.svg'
import icon_dataset_outlined from '@/assets/svg/icon_dataset_outlined.svg'
import icon_newItem_outlined from '@/assets/svg/icon_new-item_outlined.svg'
import icon_describe_outlined from '@/assets/svg/icon_describe_outlined.svg'
import icon_edit_outlined from '@/assets/svg/icon_edit_outlined.svg'
import icon_succeed_filled from '@/assets/svg/icon_succeed_filled.svg'
import icon_close_filled from '@/assets/svg/icon_close_filled.svg'
import icon_replace_outlined from '@/assets/svg/icon_replace_outlined.svg'
import iconMaybe_outlined from '@/assets/svg/icon-maybe_outlined.svg'
import icon_refresh_outlined from '@/assets/svg/icon_refresh_outlined.svg'
import icon_left_outlined from '@/assets/svg/icon_left_outlined.svg'
import { computed, h, unref, reactive, ref, shallowRef, nextTick, watch, onMounted } from 'vue'
import { dsTypes } from '@/views/visualized/data/datasource/form/option'
import type { TabPaneName, ElMessageBoxOptions } from 'element-plus-secondary'
import {
  ElIcon,
  ElButton,
  ElMessageBox,
  ElMessage,
  ElScrollbar,
  ElAside
} from 'element-plus-secondary'
import { treeDraggble } from '@/utils/treeDraggble'
import GridTable from '@/components/grid-table/src/GridTable.vue'
import ArrowSide from '@/views/common/CrestResourceArrow.vue'
import relationChart from '@/components/relation-chart/index.vue'
import { HandleMore } from '@/components/handle-more'
import { Icon } from '@/components/icon-custom'
import { fieldType } from '@/utils/attr'
import { useEmitt } from '@/hooks/web/useEmitt'
import {
  getHidePwById,
  listSyncRecord,
  uploadFile,
  perDeleteDatasource,
  getSimpleDs,
  supportSetKey,
  tableStatus,
  excelDataPage,
  saveExcelData
} from '@/api/datasource'
import CreatDsGroup from './form/CreatDsGroup.vue'
import type { Tree } from '../dataset/form/CreatDsGroup.vue'
import { previewData, getById } from '@/api/datasource'
import { useI18n } from '@/hooks/web/useI18n'
import { useRoute, useRouter } from 'vue-router_2'
import DatasetDetail from '@/views/visualized/data/dataset/DatasetDetail.vue'
import { timestampFormatDate } from '@/views/visualized/data/dataset/form/util'
import EmptyBackground from '@/components/empty-background/src/EmptyBackground.vue'
import dayjs from 'dayjs'
import { useAppStoreWithOut } from '@/store/modules/app'
import {
  getTableField,
  listDatasourceTables,
  deleteById,
  move,
  reName,
  createFolder,
  validateById,
  syncApiDs,
  syncApiTable
} from '@/api/datasource'
import type { SyncSetting, Node } from './form/option'
import EditorDatasource from './form/index.vue'
import ExcelInfoBase from './ExcelInfoBase.vue'
import SheetTabs from './SheetTabs.vue'
import BaseInfoItem from './BaseInfoItem.vue'
import BaseInfoContent from './BaseInfoContent.vue'
import type { BusiTreeNode, BusiTreeRequest } from '@/models/tree/TreeNode'
import { useMoveLine } from '@/hooks/web/useMoveLine'
import { cloneDeep } from 'lodash-es'
import { interactiveStoreWithOut } from '@/store/modules/interactive'
import treeSort from '@/utils/treeSortUtils'
import { useCache } from '@/hooks/web/useCache'
import { useEmbedded } from '@/store/modules/embedded'
import { iconFieldMap } from '@/components/icon-group/field-list'
import { iconDatasourceMap } from '@/components/icon-group/datasource-list'
import { querySymmetricKey } from '@/api/login'
import { symmetricDecrypt } from '@/utils/encryption'
import { isFreeFolder } from '@/utils/utils'
const route = useRoute()
const interactiveStore = interactiveStoreWithOut()
interface Field {
  fieldShortName: string
  name: string
  engineFieldName: string
  originName: string
  fieldType: number
}
const { wsCache } = useCache()
const { t } = useI18n()
const router = useRouter()
const appStore = useAppStoreWithOut()
// 数据源管理页的核心状态，集中维护树列表、表格预览分页、排序和过滤结果
const state = reactive({
  datasourceTree: [] as BusiTreeNode[],
  dsTableData: [],
  paginationConfig: {
    currentPage: 1,
    pageSize: 10,
    total: 0
  },
  curSortType: 'time_desc',
  filterTable: []
})

// 同步记录弹窗的分页状态，独立于表格预览分页，避免两个列表互相覆盖页码
const recordState = reactive({
  paginationConfig: {
    currentPage: 1,
    pageSize: 10,
    total: 0
  }
})
// 当前页面是否运行在嵌入式数据集编辑模式，用于决定创建数据集时走路由还是事件切换
const isCrestBi = computed(() => appStore.getIsCrestBi)
// 当前页面是否处于 iframe 宿主中，模板据此调整部分交互入口
const isIframe = computed(() => appStore.getIsIframe)
const embedded = useEmbedded()
// 根据当前数据源和可选表名进入数据集创建流程，并兼容嵌入式与普通路由两种入口
const createDataset = (tableName?: string) => {
  if (isCrestBi.value) {
    embedded.clearState()
    embedded.setDatasourceId(nodeInfo.id as string)
    embedded.setTableName(tableName)
    useEmitt().emitter.emit('changeCurrentComponent', 'DatasetEditor')
    return
  }
  wsCache.set('ds-info-id', nodeInfo.id)
  router.push({
    path: '/dataset-form',
    query: {
      datasourceId: nodeInfo.id,
      tableName
    }
  })
}

const { width, node } = useMoveLine('DATASOURCE')

// 当前选中表的基础信息，驱动右侧字段明细抽屉的标题和查询参数
const dsTableDetail = reactive({
  tableName: '',
  name: ''
})
// 根目录管理权限标记，用于控制顶层新增与拖拽等管理动作是否可用
const rootManage = ref(false)
// 表格搜索关键字，作用于当前数据源下的表清单
const nickName = ref('')
// 数据源树搜索关键字，作用于左侧资源树过滤
const dsName = ref('')
// 表字段明细抽屉开关，打开时展示当前表的字段列表
const userDrawer = ref(false)
// 原始数据源树缓存，排序和过滤前保留一份完整树结构
const rawDatasourceList = ref([])
// 数据源优先级设置入口开关，由编辑表单按数据源类型决定是否展示
const showPriority = ref(false)
// SSH 配置入口开关，由插件或数据源类型决定是否展示
const showSSH = ref(true)
// 数据源编辑表单组件引用，负责新增、编辑、复制和导入后的初始化
const datasourceEditor = ref()
// Excel 数据源当前选中的工作表页签
const activeTab = ref('')
const menuList = [
  {
    label: t('chart.move_to'),
    svgName: icon_intoItem_outlined,
    command: 'move'
  },
  {
    label: t('data_set.rename'),
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

const typeMap = dsTypes.reduce((pre, next) => {
  pre[next.type] = next.name
  return pre
}, {})

// 新建菜单项，根据页面文案和图标配置生成数据源与文件夹入口
const datasetTypeList = computed(() => {
  return [
    {
      label: t('datasource.create'),
      svgName: icon_dataset,
      command: 'datasource'
    },
    {
      label: t('datasetUi.new_folder'),
      divided: true,
      svgName: dvFolder,
      command: 'folder'
    }
  ]
})

// 当前表字段加载状态，避免字段抽屉在异步请求期间显示过期数据
const dsTableDataLoading = ref(false)
// 选择数据表后加载字段信息，并打开字段明细抽屉
const selectDataset = row => {
  Object.assign(dsTableDetail, row)
  userDrawer.value = true
  dsTableDataLoading.value = true
  getTableField({ tableName: row.tableName, datasourceId: nodeInfo.id, isCross: false })
    .then(res => {
      state.dsTableData = res || []
    })
    .finally(() => {
      dsTableDataLoading.value = false
    })
}

// 数据源树的排序基准数据，排序操作始终从该副本重新计算
const originResourceTree = shallowRef([])

// 用户显式切换排序方式时，更新树、当前排序状态和本地缓存
const handleSortTypeChange = sortType => {
  state.datasourceTree = treeSort(originResourceTree.value, sortType)
  state.curSortType = sortType
  wsCache.set('TreeSort-datasource', state.curSortType)
}

// 按指定排序方式重排树数据，不写入缓存，供初始化和刷新流程复用
const sortTypeChange = sortType => {
  state.datasourceTree = treeSort(originResourceTree.value, sortType)
  state.curSortType = sortType
}
// 表清单页容量变更时重置到第一页，防止当前页越界
const handleSizeChange = pageSize => {
  state.paginationConfig.currentPage = 1
  state.paginationConfig.pageSize = pageSize
}
// 表清单页码变更入口，只维护分页状态，数据来自已加载的过滤结果
const handleCurrentChange = currentPage => {
  state.paginationConfig.currentPage = currentPage
}

// 同步记录页容量变更后重新拉取记录，保证弹窗数据与分页一致
const handleRecordSizeChange = pageSize => {
  recordState.paginationConfig.currentPage = 1
  recordState.paginationConfig.pageSize = pageSize
  getRecord()
}
// 同步记录页码变更后重新拉取当前页数据
const handleRecordCurrentChange = currentPage => {
  recordState.paginationConfig.currentPage = currentPage
  getRecord()
}

let listScrollTop = 0
// 记录左侧树滚动位置，刷新树组件后恢复用户所在位置
const handleScroll = val => {
  listScrollTop = val.scrollTop
}

// 左侧树滚动容器引用，用于树重建后恢复滚动位置
const scrollbarRef = ref()

// 将后端字段元数据转换为预览表格列配置，并渲染字段类型图标
const generateColumns = (arr: Field[]) =>
  arr.map(ele => ({
    key: ele.originName,
    fieldType: ele.fieldType,
    dataKey: ele.originName,
    title: ele.name,
    width: 150,
    headerCellRenderer: ({ column }) => (
      <div class="flex-align-center">
        <ElIcon style={{ marginRight: '6px' }}>
          <Icon className={`field-icon-${fieldType[column.fieldType]}`}>
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

// 数据预览加载状态，控制 Excel 表格预览区域的 loading 展示
const dataPreviewLoading = ref(false)
// 数据预览表格列配置，由字段元数据动态生成
const columns = ref([])
// 加载指定 Excel 工作表的字段与数据预览，并同步页签预览内容
const handleLoadExcel = data => {
  dataPreviewLoading.value = true
  previewData(data)
    .then(res => {
      columns.value = generateColumns((res?.data?.fields as Field[]) || [])
      tabData.value = (res?.data?.data as Array<{}>) || []
    })
    .finally(() => {
      dataPreviewLoading.value = false
    })
}

// 校验当前数据源连通性，并把 API 子表状态同步回树节点状态
const validateDS = () => {
  let nodeTmpInfo = reactive<Node>(cloneDeep(defaultInfo))
  Object.assign(nodeTmpInfo, cloneDeep(nodeInfo))
  validateById(nodeTmpInfo.id)
    .then(res => {
      if (res.data.type.startsWith('API')) {
        let error = 0
        const dsStatus = JSON.parse(res.data.status) as Array<{ name: string; status: string }>
        const apiConfiguration = nodeInfo.apiConfiguration || []
        for (const statusInfo of dsStatus) {
          if (statusInfo.status === 'Error') {
            error++
          }
          const apiInfo = apiConfiguration.find(api => api.name === statusInfo.name)
          if (apiInfo) {
            apiInfo.status = statusInfo.status
          }
        }
        if (error === 0) {
          changeDsStatus(state.datasourceTree, nodeTmpInfo.id, Math.abs(nodeTmpInfo.extraFlag))
          ElMessage.success(t('data_source.verification_successful'))
        } else {
          changeDsStatus(state.datasourceTree, nodeTmpInfo.id, -Math.abs(nodeTmpInfo.extraFlag))
          ElMessage.error(t('data_source.verification_failed'))
        }
      } else {
        changeDsStatus(state.datasourceTree, nodeTmpInfo.id, Math.abs(nodeTmpInfo.extraFlag))
        ElMessage.success(t('data_source.verification_successful'))
      }
    })
    .catch(() => {
      changeDsStatus(state.datasourceTree, nodeTmpInfo.id, -Math.abs(nodeTmpInfo.extraFlag))
    })
}

// API 表状态错误详情弹窗开关
const dialogErrorInfo = ref(false)
// API 表状态错误详情文本
const dialogMsg = ref('')

// 将同步配置格式化为详情页展示文案，兼容立即执行、Cron 和简单周期三种模式
const formatSimpleCron = (info?: SyncSetting) => {
  const { syncRate, simpleCronValue, simpleCronType, startTime, endTime, cron } = info
  let start = '-'
  let end = '-'
  if (startTime) {
    start = dayjs(new Date(startTime)).format('YYYY-MM-DD HH:mm:ss')
  }
  if (endTime) {
    end = dayjs(new Date(endTime)).format('YYYY-MM-DD HH:mm:ss')
  }
  let strArr = []
  switch (syncRate) {
    case 'RIGHTNOW':
      strArr.push(t('dataset.execute_once'))
      break
    case 'CRON':
      strArr.push(`${t('dataset.cron_config')}: ${cron}`)
      strArr.push(`${t('dataset.start_time')}: ${start}`)
      strArr.push(`${t('dataset.end_time')}: ${end}`)
      break
    case 'SIMPLE_CRON':
      const type = t(`common.${simpleCronType}`)
      strArr.push(
        `${t('dataset.simple_cron')}: ${t('common.every')}${simpleCronValue}${type}${t(
          'data_source.update_once'
        )}`
      )
      strArr.push(`${t('dataset.start_time')}: ${start}`)
      strArr.push(`${t('dataset.end_time')}: ${end}`)
      break
    default:
      break
  }

  return strArr
}

// 打开 API 表错误详情弹窗，并写入当前行的错误信息
const showErrorInfo = info => {
  dialogMsg.value = info
  dialogErrorInfo.value = true
}

// 已加载的数据源插件定义，补充内置类型以外的名称、图标和静态配置
const pluginDs = ref([])
// 接收插件数据源定义，并写入类型名称映射供详情页展示
const loadDsPlugin = data => {
  pluginDs.value = data
  pluginDs.value.forEach(ele => {
    typeMap[ele.type] = ele.name
  })
}
// 按树节点类型获取插件图标，仅叶子数据源节点需要展示
const getDsIcon = data => {
  if (pluginDs?.value.length === 0) return null
  if (!data.leaf) return null

  const arr = pluginDs.value.filter(ele => {
    return ele.type === data.type
  })
  return arr && arr.length > 0 ? arr[0].icon : null
}
// 按数据源类型获取插件图标，供详情面板或菜单中无完整节点数据的场景使用
const getDsIconType = type => {
  const arr = pluginDs.value.filter(ele => {
    return ele.type === type
  })
  return arr && arr.length > 0 ? arr[0].icon : null
}

// 获取树节点图标，文件夹使用固定图标，数据源按内置类型映射
const getDsIconName = data => {
  if (!data.leaf) return dvFolder
  return iconDatasourceMap[data.type]
}

// 切换 Excel 工作表页签后加载对应工作表的预览数据
const handleTabClick = tab => {
  activeTab.value = tab.value
  handleLoadExcel({ table: tab.value, id: nodeInfo.id })
}

// Excel 数据源的工作表页签列表，由后端表清单转换而来
const tabList = shallowRef([])

// 根据表名关键字过滤当前数据源表清单，并重置表格分页到第一页
const initSearch = () => {
  handleCurrentChange(1)
  state.filterTable = tableData.value.filter(ele =>
    ele.tableName.toLowerCase().includes(nickName.value.toLowerCase())
  )
  state.paginationConfig.total = state.filterTable.length
}

// 当前页展示的表清单数据，从过滤结果中按分页状态切片
const pagingTable = computed(() => {
  const { currentPage, pageSize } = state.paginationConfig
  return state.filterTable.slice((currentPage - 1) * pageSize, currentPage * pageSize)
})

// 数据源详情默认结构，重置节点时保持字段完整，避免旧详情残留
const defaultInfo = {
  name: '',
  createBy: '',
  creator: '',
  createTime: '',
  description: '',
  id: 0,
  size: 0,
  nodeType: '',
  type: '',
  fileName: '',
  configuration: null,
  syncSetting: null,
  apiConfiguration: [],
  weight: 0,
  enableDataFill: false,
  extraFlag: 0
}
// 当前选中的数据源节点详情，供右侧详情、表清单和编辑动作共享
const nodeInfo = reactive<Node>(cloneDeep(defaultInfo))
// 详情面板的基础展示项，统一格式化创建时间
const infoList = computed(() => {
  return {
    creator: nodeInfo.creator,
    createTime: timestampFormatDate(nodeInfo.createTime)
  }
})
// 保存文件夹新增、移动或重命名操作，并在成功后刷新资源树
const saveDsFolder = (params, successCb, finallyCb, cmd) => {
  let method = move
  let message = t('data_set.moved_successfully')

  switch (cmd) {
    case 'move':
      method = move
      message = t('data_set.moved_successfully')

      break
    case 'rename':
      method = reName
      message = t('data_set.rename_successful')
      break
    default:
      method = createFolder
      message = t('data_source.create_successfully')
      break
  }
  method(params)
    .then(res => {
      if (res !== undefined) {
        successCb()
        ElMessage.success(message)
        listDs()
      }
    })
    .finally(() => {
      finallyCb()
    })
}

// 数据源树加载状态，覆盖树刷新和初始化阶段
const dsLoading = ref(false)
// 页面是否完成首次数据源树加载，用于控制空态和加载态切换
const mounted = ref(false)
// 当前环境是否支持前端设置加密密钥，由后端能力接口决定
const isSupportSetKey = ref(false)
// 当前会话使用的对称密钥，用于解密后端返回的敏感配置
const symmetricKey = ref('')

// 拉取数据源树，处理根节点权限、排序、选中节点恢复和树过滤恢复
const listDs = () => {
  rawDatasourceList.value = []
  dsLoading.value = true
  const curSortType = getStoredSortType('TreeSort-datasource', getDefaultSortType())
  const request = { busiFlag: 'datasource' } as BusiTreeRequest
  interactiveStore
    .setInteractive(request)
    .then(res => {
      const nodeData = (res as unknown as BusiTreeNode[]) || []
      if (nodeData.length && nodeData[0]['id'] === '0' && nodeData[0]['name'] === 'root') {
        rootManage.value = nodeData[0]['weight'] >= 7
        state.datasourceTree = nodeData[0]['children'] || []
        originResourceTree.value = cloneDeep(unref(state.datasourceTree))
        sortTypeChange(curSortType)
        return
      }
      originResourceTree.value = cloneDeep(unref(state.datasourceTree))
      state.datasourceTree = nodeData
      sortTypeChange(curSortType)
    })
    .finally(() => {
      mounted.value = true
      dsLoading.value = false
      updateTreeExpand()
      const id = nodeInfo.id
      if (!!id) {
        Object.assign(nodeInfo, cloneDeep(defaultInfo))
        dfsDatasourceTree(state.datasourceTree, id)
        setTimeout(() => {
          if (dsName.value) {
            dsListTree.value.filter(dsName.value)
          }
          dsListTree.value.setCurrentKey(nodeInfo.id, true)
        }, 100)
      }
    })
}

// 查询当前部署是否允许设置密钥，失败时只记录警告，不阻断页面加载
const setSupportSetKey = () => {
  supportSetKey()
    .then(response => {
      isSupportSetKey.value = response.data
    })
    .catch(error => {
      console.warn(error?.message)
    })
}
// 在树结构中更新指定数据源的连通性状态，保留其它节点引用不变
const changeDsStatus = (ds, id, extraFlag) => {
  ds.some(ele => {
    if (ele.id === id) {
      ele.extraFlag = extraFlag
      return true
    }
    if (!!ele.children?.length) {
      changeDsStatus(ele.children, id, extraFlag)
    }
    return false
  })
}

// 深度查找数据源树中的目标节点，并触发与用户点击一致的详情加载流程
const dfsDatasourceTree = (ds, id) => {
  ds.some(ele => {
    if (ele.id === id) {
      handleNodeClick(ele)
      return true
    }
    if (!!ele.children?.length) {
      dfsDatasourceTree(ele.children, id)
    }
    return false
  })
}

// 文件夹创建和移动弹窗组件引用
const creatDsFolder = ref()
// 数据源树排序选项，展示文案与排序值保持一一对应
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

// 当前排序方式的展示文案，用于排序入口提示
const sortTypeTip = computed(() => {
  return sortList.find(ele => ele.value === state.curSortType)?.name ?? sortList[1].name
})
// 当前数据源的完整表清单，搜索和分页都从该数据集派生
const tableData = shallowRef([])
// 当前 Excel 工作表的数据预览行
const tabData = shallowRef([])
const EXCEL_EDIT_ROW_ID = '_rowId'
// Excel 编辑器最近一次成功加载的工作表名，用于切表前校验未保存修改
const excelEditLoadedTableName = ref('')
// Excel 在线编辑器状态，集中保存分页、字段、行、选中项和脏数据标记
const excelEditState = reactive({
  visible: false,
  loading: false,
  saving: false,
  tableName: '',
  page: 1,
  pageSize: 100,
  total: 0,
  fields: [] as Field[],
  rows: [] as Array<Record<string, any>>,
  selectedRows: [] as Array<Record<string, any>>,
  deletedIds: [] as string[],
  dirty: false
})

// 获取 Excel 字段在行数据中的稳定键名，优先使用标准字段名
const excelEditFieldKey = (field: Field) => field.name || field.originName

// 根据字段名长度计算编辑器列宽，并限制在可读范围内
const excelEditColumnWidth = (field: Field) => {
  const title = excelEditFieldKey(field)
  return Math.min(Math.max(title.length * 16 + 72, 160), 320)
}

// 将字段类型编号转换为编辑器使用的输入类型，未知类型按文本处理
const excelEditFieldType = (field: Field) => fieldType[field.fieldType] || 'text'

// 清理 Excel 编辑器的脏数据、删除列表和选中行，通常在重新加载数据后调用
const resetExcelEditChanges = () => {
  excelEditState.dirty = false
  excelEditState.deletedIds = []
  excelEditState.selectedRows = []
}

// 判断 Excel 编辑器是否存在新增、修改或删除，供切表、刷新和关闭前确认
const hasExcelEditChanges = () => {
  return (
    excelEditState.deletedIds.length > 0 ||
    excelEditState.rows.some(row => row.__dirty || row.__isNew)
  )
}

// 在离开 Excel 编辑器当前数据集前确认是否丢弃未保存修改
const confirmDiscardExcelEditChanges = async () => {
  if (!hasExcelEditChanges()) {
    return true
  }
  try {
    await ElMessageBox.confirm(t('data_source.unsaved_edit_confirm'), {
      confirmButtonText: t('common.sure'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
      autofocus: false,
      showClose: false
    } as ElMessageBoxOptions)
    return true
  } catch (e) {
    return false
  }
}

// 按当前工作表和分页加载 Excel 可编辑数据，并重置本地修改状态
const loadExcelEditData = () => {
  if (!excelEditState.tableName) {
    return
  }
  excelEditState.loading = true
  return excelDataPage({
    datasourceId: nodeInfo.id,
    tableName: excelEditState.tableName,
    page: excelEditState.page,
    pageSize: excelEditState.pageSize
  })
    .then(res => {
      excelEditState.fields = res?.fields || []
      excelEditState.rows = (res?.rows || []).map(row => ({
        ...row,
        __dirty: false,
        __isNew: false
      }))
      excelEditState.total = res?.total || 0
      excelEditLoadedTableName.value = excelEditState.tableName
      resetExcelEditChanges()
    })
    .finally(() => {
      excelEditState.loading = false
    })
}

// 刷新 Excel 编辑器数据，刷新前先确认未保存修改是否可以丢弃
const refreshExcelEditData = async () => {
  const canRefresh = await confirmDiscardExcelEditChanges()
  if (!canRefresh) {
    return
  }
  await loadExcelEditData()
}

// 确保 Excel 工作表列表已加载，编辑器打开前复用已有表清单减少重复请求
const ensureExcelEditTables = async () => {
  if (tabList.value.length) {
    return
  }
  const res = await listDatasourceTables({ datasourceId: nodeInfo.id })
  tabList.value = res.data.map(ele => {
    const { name, tableName } = ele
    return {
      value: name,
      label: tableName
    }
  })
  tableData.value = res.data
  if (!!tabList.value.length && !activeTab.value) {
    activeTab.value = tabList.value[0].value
  }
}

// 打开 Excel 在线编辑器，默认选中当前页签或第一张工作表
const openExcelEditor = async () => {
  await ensureExcelEditTables()
  if (!tabList.value.length) {
    ElMessage.warning(t('datasource.no_data_table'))
    return
  }
  excelEditState.tableName = activeTab.value || tabList.value[0].value
  excelEditState.page = 1
  excelEditState.visible = true
  await loadExcelEditData()
}

// 切换 Excel 编辑器工作表，切换前校验当前工作表是否存在未保存修改
const handleExcelEditTableChange = async tableName => {
  if (tableName === excelEditLoadedTableName.value) {
    return
  }
  const canLeave = await confirmDiscardExcelEditChanges()
  if (!canLeave) {
    excelEditState.tableName = excelEditLoadedTableName.value
    return
  }
  excelEditState.page = 1
  await loadExcelEditData()
}

// 切换 Excel 编辑器页码，离开当前页前确认是否丢弃本地修改
const handleExcelEditPageChange = async page => {
  const canLeave = await confirmDiscardExcelEditChanges()
  if (!canLeave) {
    return
  }
  excelEditState.page = page
  await loadExcelEditData()
}

// 切换 Excel 编辑器页容量，并从第一页重新加载数据
const handleExcelEditSizeChange = async pageSize => {
  const canLeave = await confirmDiscardExcelEditChanges()
  if (!canLeave) {
    return
  }
  excelEditState.page = 1
  excelEditState.pageSize = pageSize
  await loadExcelEditData()
}

// 标记 Excel 行已被编辑，新增行保持新增标记，已有行追加修改标记
const markExcelEditDirty = row => {
  if (!row.__isNew) {
    row.__dirty = true
  }
  excelEditState.dirty = true
}

// 在 Excel 编辑器顶部插入一行新数据，并为所有字段初始化空值
const addExcelEditRow = () => {
  if (excelEditState.loading) {
    return
  }
  const row = {
    [EXCEL_EDIT_ROW_ID]: `new_${Date.now()}_${Math.random().toString(16).slice(2)}`,
    __dirty: true,
    __isNew: true
  }
  excelEditState.fields.forEach(field => {
    row[excelEditFieldKey(field)] = ''
  })
  excelEditState.rows.unshift(row)
  excelEditState.dirty = true
}

// 同步 Excel 编辑器表格选中行，供批量删除动作读取
const handleExcelEditSelectionChange = rows => {
  excelEditState.selectedRows = rows
}

// 删除 Excel 编辑器中选中的行，已有行记录行号，新建行只从本地列表移除
const deleteExcelEditRows = () => {
  if (excelEditState.loading) {
    return
  }
  if (!excelEditState.selectedRows.length) {
    ElMessage.warning(t('data_source.select_delete_rows'))
    return
  }
  const selectedRowIds = new Set(excelEditState.selectedRows.map(row => row[EXCEL_EDIT_ROW_ID]))
  excelEditState.selectedRows.forEach(row => {
    if (!row.__isNew && row[EXCEL_EDIT_ROW_ID]) {
      excelEditState.deletedIds.push(row[EXCEL_EDIT_ROW_ID])
    }
  })
  excelEditState.rows = excelEditState.rows.filter(
    row => !selectedRowIds.has(row[EXCEL_EDIT_ROW_ID])
  )
  excelEditState.selectedRows = []
  excelEditState.dirty = true
}

// 将编辑器行数据转换为后端保存载荷，只提交业务字段和已有行标识
const excelEditPayload = row => {
  const payload = {}
  excelEditState.fields.forEach(field => {
    payload[excelEditFieldKey(field)] = row[excelEditFieldKey(field)]
  })
  if (!row.__isNew) {
    payload[EXCEL_EDIT_ROW_ID] = row[EXCEL_EDIT_ROW_ID]
  }
  return payload
}

// 保存 Excel 在线编辑结果，按新增、修改和删除三类操作提交到后端
const saveExcelEditor = () => {
  if (excelEditState.loading) {
    return
  }
  const updates = excelEditState.rows
    .filter(row => row.__dirty && !row.__isNew)
    .map(excelEditPayload)
  const inserts = excelEditState.rows.filter(row => row.__isNew).map(excelEditPayload)
  const deletes = [...new Set(excelEditState.deletedIds)]
  if (!updates.length && !inserts.length && !deletes.length) {
    ElMessage.info(t('data_source.no_edit_changes'))
    return
  }
  excelEditState.saving = true
  saveExcelData({
    datasourceId: nodeInfo.id,
    tableName: excelEditState.tableName,
    updates,
    inserts,
    deletes
  })
    .then(() => {
      ElMessage.success(t('common.save_success'))
      if (activeTab.value === excelEditState.tableName) {
        handleLoadExcel({ table: activeTab.value, id: nodeInfo.id })
      }
      loadExcelEditData()
    })
    .finally(() => {
      excelEditState.saving = false
    })
}

// 关闭 Excel 编辑器前确认未保存修改，并兼容抽屉回调与直接状态关闭两种入口
const closeExcelEditor = async (done?: () => void) => {
  const canClose = await confirmDiscardExcelEditChanges()
  if (canClose) {
    if (done) {
      done()
    } else {
      excelEditState.visible = false
    }
  }
}

// 点击数据源树叶子节点后加载详情，解密敏感配置并刷新右侧关联视图
const handleNodeClick = data => {
  if (!data.leaf) {
    dsListTree.value.setCurrentKey(null)
    return
  }
  let method = getHidePwById
  if (data.weight < 7) {
    method = getSimpleDs
  }
  return method(data.id).then(res => {
    let {
      name,
      createBy,
      id,
      createTime,
      creator,
      type,
      pid,
      configuration,
      syncSetting,
      apiConfigurationStr,
      paramsStr,
      fileName,
      size,
      description,
      lastSyncTime,
      enableDataFill
    } = res.data
    if (configuration) {
      configuration = JSON.parse(symmetricDecrypt(configuration, symmetricKey.value))
    }
    if (paramsStr) {
      paramsStr = JSON.parse(symmetricDecrypt(paramsStr, symmetricKey.value))
    }
    if (apiConfigurationStr) {
      apiConfigurationStr = JSON.parse(symmetricDecrypt(apiConfigurationStr, symmetricKey.value))
    }
    Object.assign(nodeInfo, {
      name,
      pid,
      description,
      fileName,
      size,
      createTime,
      creator,
      createBy,
      id,
      type,
      configuration,
      syncSetting,
      apiConfiguration: apiConfigurationStr,
      paramsConfiguration: paramsStr,
      weight: data.weight,
      lastSyncTime,
      enableDataFill,
      extraFlag: data.extraFlag
    })
    activeTab.value = ''
    activeName.value = 'config'
    nickName.value = ''
    handleCurrentChange(1)
    handleClick(activeName.value)
  })
}
// 打开新建数据源表单，并传入可选父文件夹和当前密钥能力状态
const createDatasource = (data?: Tree) => {
  datasourceEditor.value.init(null, data?.id, null, isSupportSetKey.value)
}
// 同步记录弹窗显示状态
const showRecord = ref(false)
// 数据源树组件引用，用于过滤、选中和恢复当前节点
const dsListTree = ref()
// 当前展开的树节点键集合，用于折叠和展开事件维护 UI 状态
const expandedKey = ref([])
// 控制数据源树组件重建，配合滚动位置恢复解决刷新后的状态丢失
const dsListTreeShow = ref(true)
// 数据源树搜索关键字变化时，调用树组件过滤方法刷新可见节点
watch(dsName, (val: string) => {
  dsListTree.value.filter(val)
})
// 短暂重建数据源树组件，并在下一轮渲染后恢复滚动位置
const updateTreeExpand = () => {
  dsListTreeShow.value = false
  nextTick(() => {
    dsListTreeShow.value = true
    nextTick(() => {
      scrollbarRef.value?.setScrollTop(listScrollTop)
    })
  })
}

// 当前数据源同步记录列表
const recordData = ref([])

// 打开同步记录弹窗并按分页参数加载当前数据源的同步记录
const getRecord = () => {
  showRecord.value = true
  listSyncRecord(
    recordState.paginationConfig.currentPage,
    recordState.paginationConfig.pageSize,
    nodeInfo.id
  ).then(res => {
    recordData.value = res.data.records
    recordState.paginationConfig.total = res.data.total
  })
}

// 同步单个 API 表，并在请求完成后提示用户
const updateApiTable = api => {
  syncApiTable({ name: api.name, tableName: api.displayTableName, datasourceId: nodeInfo.id }).then(
    () => {
      ElMessage.success(t('datasource.req_completed'))
    }
  )
}

// 同步当前 API 数据源下的全部接口表
const updateApiDs = () => {
  syncApiDs({ datasourceId: nodeInfo.id }).then(() => {
    ElMessage.success(t('datasource.req_completed'))
  })
}

// 记录用户展开的树节点，刷新树时用于保留展开状态
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

// 数据源树节点过滤规则，按节点名称做不区分大小写的包含匹配
const filterNode = (value: string, data: BusiTreeNode) => {
  if (!value) return true
  return data.name?.toLowerCase().includes(value.toLowerCase())
}

// 打开当前数据源编辑表单，加载完整详情并解密配置后传入编辑器
const editDatasource = (editType?: number) => {
  if (nodeInfo.type.startsWith('Excel')) {
    nodeInfo.editType = editType
  }
  return getById(nodeInfo.id).then(res => {
    let arr = pluginDs.value.filter(ele => {
      return ele.type == res.data.type
    })
    let {
      name,
      createBy,
      id,
      createTime,
      creator,
      type,
      pid,
      configuration,
      syncSetting,
      apiConfigurationStr,
      paramsStr,
      fileName,
      size,
      description,
      lastSyncTime,
      enableDataFill
    } = res.data
    if (configuration) {
      configuration = JSON.parse(symmetricDecrypt(configuration, symmetricKey.value))
    }
    if (paramsStr) {
      paramsStr = JSON.parse(symmetricDecrypt(paramsStr, symmetricKey.value))
    }
    if (apiConfigurationStr) {
      apiConfigurationStr = JSON.parse(symmetricDecrypt(apiConfigurationStr, symmetricKey.value))
    }
    let datasource = reactive<Node>(cloneDeep(defaultInfo))
    Object.assign(datasource, {
      name,
      pid,
      description,
      fileName,
      size,
      createTime,
      creator,
      createBy,
      id,
      type,
      configuration,
      syncSetting,
      apiConfiguration: apiConfigurationStr,
      paramsConfiguration: paramsStr,
      lastSyncTime,
      enableDataFill,
      isPlugin: arr && arr.length > 0,
      staticMap: arr[0]?.staticMap
    })
    datasourceEditor.value.init(datasource, null, null, isSupportSetKey.value)
  })
}

// 从树节点快捷编辑入口加载详情，再进入与详情页一致的编辑流程
const handleEdit = async data => {
  await handleNodeClick(data)
  editDatasource()
}

// 数据源树拖拽处理器，封装拖拽开始、放置校验和移动保存逻辑
const { handleDrop, allowDrop, handleDragStart } = treeDraggble(
  state,
  'datasourceTree',
  move,
  'datasource',
  originResourceTree
)

// 复制数据源时加载完整配置，清理主键和 API 表名后打开编辑器
const handleCopy = async data => {
  getById(data.id).then(res => {
    let {
      name,
      createBy,
      id,
      createTime,
      creator,
      type,
      pid,
      configuration,
      syncSetting,
      apiConfigurationStr,
      paramsStr,
      fileName,
      size,
      description,
      lastSyncTime,
      enableDataFill
    } = res.data
    let arr = pluginDs.value.filter(ele => {
      return ele.type == res.data.type
    })
    if (configuration) {
      configuration = JSON.parse(symmetricDecrypt(configuration, symmetricKey.value))
    }
    if (paramsStr) {
      paramsStr = JSON.parse(symmetricDecrypt(paramsStr, symmetricKey.value))
    }
    if (apiConfigurationStr) {
      apiConfigurationStr = JSON.parse(symmetricDecrypt(apiConfigurationStr, symmetricKey.value))
    }
    let datasource = reactive<Node>(cloneDeep(defaultInfo))
    Object.assign(datasource, {
      name,
      pid,
      description,
      fileName,
      size,
      createTime,
      creator,
      createBy,
      id,
      type,
      configuration,
      syncSetting,
      apiConfiguration: apiConfigurationStr,
      paramsConfiguration: paramsStr,
      lastSyncTime,
      enableDataFill,
      isPlugin: arr && arr.length > 0,
      staticMap: arr[0]?.staticMap
    })
    datasource.id = ''
    datasource.copy = true
    datasource.name = t('datasource.copy')
    if (datasource.type.startsWith('API')) {
      for (let i = 0; i < datasource.apiConfiguration.length; i++) {
        datasource.apiConfiguration[i].displayTableName = ''
      }
    }
    datasourceEditor.value.init(datasource, null, null, isSupportSetKey.value)
  })
}

// 处理数据源树的新建命令，在数据源和文件夹入口之间分发
const handleDatasourceTree = (cmd: string, data?: Tree) => {
  if (cmd === 'datasource') {
    createDatasource(data)
  }
  if (cmd === 'folder') {
    creatDsFolder.value.createInit(cmd, data || {})
  }
}
// 血缘关系图组件引用，用于删除数据源前展示影响范围
const relationChartRef = ref()
// 处理树节点更多菜单命令，覆盖复制、删除、移动和重命名等操作
const operation = (cmd: string, data: Tree, nodeType: string) => {
  if (cmd === 'copy') {
    handleCopy(data)
    return
  }
  if (cmd === 'delete') {
    let options = {
      confirmButtonText: t('common.sure'),
      cancelButtonText: t('common.cancel'),
      confirmButtonType: 'danger',
      type: 'warning',
      tip: '',
      autofocus: false,
      showClose: false
    }
    if (!!data.children?.length) {
      options.tip = t('data_source.operate_with_caution')
    } else {
      delete options.tip
    }

    if (nodeType !== 'folder') {
      perDeleteDatasource(data.id).then(res => {
        if (res === true) {
          // 删除确认弹窗中的血缘查看动作，延迟到用户主动点击时再查询图数据
          const onClick = () => {
            relationChartRef.value.getChartData({
              queryType: 'datasource',
              num: data.id,
              label: data.name
            })
          }

          ElMessageBox.confirm('', {
            confirmButtonType: 'danger',
            type: 'warning',
            autofocus: false,
            confirmButtonText: t('common.sure'),
            showClose: false,
            dangerouslyUseHTMLString: true,
            message: h('div', null, [
              h('p', { style: 'margin-bottom: 8px;' }, t('datasource.this_data_source')),
              h('p', { class: 'tip' }, t('data_source.confirm_to_delete')),
              h(
                ElButton,
                { text: true, onClick: onClick, style: 'margin-left: -4px;' },
                t('data_source.view_blood_relationship')
              )
            ])
          }).then(() => {
            deleteById(data.id as number).then(() => {
              if (data.id === nodeInfo.id) {
                Object.assign(nodeInfo, cloneDeep(defaultInfo))
              }
              listDs()
              ElMessage.success(t('dataset.delete_success'))
            })
          })
        } else {
          ElMessageBox.confirm(
            t('datasource.this_data_source'),
            options as ElMessageBoxOptions
          ).then(() => {
            deleteById(data.id as number).then(() => {
              if (data.id === nodeInfo.id) {
                Object.assign(nodeInfo, cloneDeep(defaultInfo))
              }
              listDs()
              ElMessage.success(t('dataset.delete_success'))
            })
          })
        }
      })
    } else {
      ElMessageBox.confirm(t('data_set.delete_this_folder'), options as ElMessageBoxOptions).then(
        () => {
          deleteById(data.id as number).then(() => {
            if (data.id === nodeInfo.id) {
              Object.assign(nodeInfo, cloneDeep(defaultInfo))
            }
            listDs()
            ElMessage.success(t('dataset.delete_success'))
          })
        }
      )
    }
  } else {
    creatDsFolder.value.createInit(nodeType, data, cmd)
  }
}

// 切换详情页签时加载配置页或表清单，并为 API 与远程 Excel 补充表状态
const handleClick = (tabName: TabPaneName) => {
  switch (tabName) {
    case 'config':
      tableData.value = []
      if (nodeInfo.type.startsWith('Excel')) {
        listDatasourceTables({ datasourceId: nodeInfo.id }).then(res => {
          tabList.value = res.data.map(ele => {
            const { name, tableName } = ele
            return {
              value: name,
              label: tableName
            }
          })
          if (!!tabList.value.length && !activeTab.value) {
            activeTab.value = tabList.value[0].value
            handleTabClick(activeTab)
          }
          tableData.value = res.data
        })
      }
      break
    case 'table':
      tableData.value = []
      listDatasourceTables({ datasourceId: nodeInfo.id }).then(res => {
        tableData.value = res.data
        initSearch()
        if (nodeInfo.type.startsWith('API') || nodeInfo.type === 'ExcelRemote') {
          tableStatus({ datasourceId: nodeInfo.id }).then(res => {
            for (let i = 0; i < state.filterTable.length; i++) {
              for (let j = 0; j < res.data.length; j++) {
                if (state.filterTable[i].tableName === res.data[j].tableName) {
                  state.filterTable[i].lastUpdateTime = res.data[j].lastUpdateTime
                  state.filterTable[i].status = res.data[j].status
                }
              }
            }
            for (let i = 0; i < tableData.value.length; i++) {
              for (let j = 0; j < res.data.length; j++) {
                if (tableData.value[i].tableName === res.data[j].tableName) {
                  tableData.value[i].lastUpdateTime = res.data[j].lastUpdateTime
                  tableData.value[i].status = res.data[j].status
                }
              }
            }
          })
        }
      })
      break
    default:
      break
  }
}
// 外部刷新入口，统一走数据源树重新加载流程
const refresh = () => {
  listDs()
}

let fileList = null
// 记录用户选择的 Excel 文件，供替换或追加导入时构造 FormData
const onChange = file => {
  fileList = file
}

// Excel 替换导入按钮加载状态
const replaceLoading = ref(false)
// Excel 追加导入按钮加载状态
const addLoading = ref(false)

// 上传 Excel 文件并进入导入编辑流程，按 editType 区分替换和追加
const uploadExcel = editType => {
  const formData = new FormData()
  formData.append('file', fileList.raw)
  formData.append('type', '')
  formData.append('editType', editType)
  formData.append('id', (nodeInfo.id || 0) as string)
  replaceLoading.value = editType === 0
  addLoading.value = editType === 1
  return uploadFile(formData)
    .then(res => {
      if (res?.code !== 0) {
        return
      }
      nodeInfo.editType = editType
      datasourceEditor.value.init(nodeInfo, nodeInfo.id, res, isSupportSetKey.value)
    })
    .finally(() => {
      replaceLoading.value = false
      addLoading.value = false
    })
}
// 当前详情页签名称，默认展示数据表页签
const activeName = ref('table')
// 数据源树组件字段映射，约定子节点和展示名称字段
const defaultProps = {
  children: 'children',
  label: 'name'
}

// 页面初始化时恢复缓存中的数据源树排序方式
const loadInit = () => {
  state.curSortType = getStoredSortType('TreeSort-datasource', state.curSortType)
}

// 拖拽放置代理，在免费文件夹限制生效时阻止跨类型保存
const proxyAllowDrop = throttle((arg1, arg2) => {
  const flagArray = ['dashboard', 'dataV', 'dataset', 'datasource']
  const flag = flagArray.findIndex(item => item === 'datasource')
  if (flag < 0 || !isFreeFolder(arg2, flag + 1)) {
    return allowDrop(arg1, arg2)
  }
  ElMessage.warning(t('free.save_error'))
  return false
}, 300)
// 组件挂载后恢复路由选中节点、加载数据源树、密钥能力和对称密钥
onMounted(() => {
  const dsId = wsCache.get('ds-info-id') || route.params.id
  nodeInfo.id = (dsId as string) || (route.query.id as string) || ''
  wsCache.delete('ds-info-id')
  loadInit()
  listDs()
  setSupportSetKey()
  const { opt } = router?.currentRoute?.value?.query || {}
  if (opt && opt === 'create') {
    datasourceEditor.value.init(null, null, null, isSupportSetKey.value)
  }
  querySymmetricKey().then(res => {
    symmetricKey.value = res.data
  })
})

// 左侧数据源树展开状态，控制详情区占位和侧栏显示
const sideTreeStatus = ref(true)
// 接收侧栏展开收起组件的状态变化
const changeSideTreeStatus = val => {
  sideTreeStatus.value = val
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
</script>

<template>
  <div class="datasource-manage" v-loading="dsLoading">
    <!-- 左侧数据源树支持拖拽宽度调整，收起后通过 ArrowSide 保留恢复入口。 -->
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
        <!-- 树头部聚合创建、搜索和排序能力，根目录权限不足时隐藏管理入口。 -->
        <div class="tree-header">
          <div class="icon-methods">
            <span class="title"> {{ t('datasource.datasource') }} </span>
            <div v-if="rootManage" class="flex-align-center">
              <el-tooltip
                arrow-offset="10"
                offset="14"
                effect="dark"
                popper-class="new-folder_tip"
                :content="t('datasetUi.new_folder')"
                placement="top"
              >
                <el-icon
                  class="custom-icon btn"
                  :style="{ marginRight: '20px' }"
                  @click="handleDatasourceTree('folder')"
                >
                  <Icon name="dv-new-folder"><dvNewFolder class="svg-icon" /></Icon>
                </el-icon>
              </el-tooltip>
              <el-tooltip
                arrow-offset="10"
                offset="14"
                popper-class="new-folder_tip"
                effect="dark"
                :content="t('datasource.create')"
                placement="top"
              >
                <el-icon class="custom-icon btn" @click="createDatasource">
                  <Icon name="icon_file-add_outlined"
                    ><icon_fileAdd_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </el-tooltip>
            </div>
          </div>

          <el-input
            :placeholder="t('commons.search')"
            v-model="dsName"
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
                  ><dvSortDesc v-if="state.curSortType.includes('desc')" class="svg-icon opt-icon"
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
        <!-- 树节点同时承载文件夹和数据源，叶子节点点击后加载右侧详情。 -->
        <el-scrollbar @scroll="handleScroll" ref="scrollbarRef" class="custom-tree">
          <el-tree
            menu
            v-if="dsListTreeShow"
            ref="dsListTree"
            node-key="id"
            @node-expand="nodeExpand"
            @node-collapse="nodeCollapse"
            :filter-node-method="filterNode"
            :default-expanded-keys="expandedKey"
            :data="state.datasourceTree"
            :props="defaultProps"
            @node-drag-start="handleDragStart"
            :allow-drop="proxyAllowDrop"
            @node-drop="handleDrop"
            draggable
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <span class="custom-tree-node" style="position: relative">
                <el-icon :class="data.leaf && 'icon-border'" style="font-size: 18px">
                  <Icon :static-content="getDsIcon(data)"
                    ><component class="svg-icon" :is="getDsIconName(data)"></component
                  ></Icon>
                </el-icon>
                <el-icon
                  style="position: absolute; top: 10px; left: 10px; font-size: 12px"
                  v-if="data.extraFlag <= -1"
                >
                  <Icon><icon_warning_colorful_red class="svg-icon" /></Icon>
                </el-icon>
                <span
                  :title="node.label"
                  class="label-tooltip ellipsis"
                  :class="data.type === 'Excel' && 'excel'"
                  v-if="data.extraFlag > -1"
                  >{{ node.label }}</span
                >
                <el-tooltip
                  effect="dark"
                  v-else
                  :content="`${t('data_set.invalid_data_source')}: ${node.label}`"
                  placement="top"
                >
                  <span
                    :title="node.label"
                    class="label-tooltip ellipsis"
                    :class="data.type === 'Excel' && 'excel'"
                    >{{ node.label }}</span
                  >
                </el-tooltip>
                <div class="icon-more" v-if="data.weight >= 7">
                  <handle-more
                    icon-size="24px"
                    @handle-command="cmd => handleDatasourceTree(cmd, data)"
                    :menu-list="datasetTypeList"
                    :icon-name="icon_add_outlined"
                    placement="bottom-start"
                    v-if="!data.leaf"
                  ></handle-more>
                  <el-icon
                    class="hover-icon"
                    @click.stop="handleEdit(data)"
                    v-else-if="data.type !== 'Excel'"
                  >
                    <icon name="icon_edit_outlined"><icon_edit_outlined class="svg-icon" /></icon>
                  </el-icon>
                  <handle-more
                    @handle-command="
                      cmd => operation(cmd, data, data.leaf ? 'datasource' : 'folder')
                    "
                    :menu-list="getMenuList(!['Excel'].includes(data.type) && data.leaf)"
                  ></handle-more>
                </div>
              </span>
            </template>
          </el-tree>
        </el-scrollbar>
      </div>
    </el-aside>

    <div
      class="datasource-content"
      :class="{
        auto: isIframe || isCrestBi,
        h100: isCrestBi || isIframe
      }"
    >
      <!-- 未选中数据源时展示空态；选中后根据节点类型展示配置、表清单和编辑入口。 -->
      <template v-if="!state.datasourceTree.length && mounted">
        <empty-background :description="t('data_source.no_data_source')" img-type="none">
          <el-button v-if="rootManage" @click="() => createDatasource()" type="primary">
            <template #icon>
              <Icon name="icon_add_outlined"><icon_add_outlined class="svg-icon" /></Icon>
            </template>
            {{ t('datasource.create') }}</el-button
          >
        </empty-background>
      </template>
      <template v-else-if="!!nodeInfo.id">
        <div class="datasource-info">
          <!-- 详情头部展示当前数据源身份、连通状态和可执行操作。 -->
          <div class="info-method">
            <el-icon class="icon-border">
              <Icon :static-content="getDsIconType(nodeInfo.type)"
                ><component class="svg-icon" :is="iconDatasourceMap[nodeInfo.type]"></component
              ></Icon>
            </el-icon>
            <span :title="nodeInfo.name" class="name ellipsis">
              {{ nodeInfo.name }}
            </span>
            <el-divider style="margin: 0 12px" direction="vertical" />
            <span class="create-user">
              {{ t('visualization.create_by') }}:{{ nodeInfo.creator }}
            </span>
            <el-popover :offset="8" show-arrow placement="bottom" width="290" trigger="hover">
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
            <div class="right-btn flex-align-center">
              <el-button secondary @click="createDataset(null)" v-permission="['dataset']">
                <template #icon>
                  <Icon name="icon_dataset_outlined"
                    ><icon_dataset_outlined class="svg-icon"
                  /></Icon>
                </template>
                {{ t('data_set.a_new_dataset') }}
              </el-button>
              <el-button
                v-if="nodeInfo.type !== 'Excel' && nodeInfo.weight >= 7"
                secondary
                @click="validateDS"
              >
                {{ t('datasource.validate') }}</el-button
              >

              <template v-if="nodeInfo.type === 'Excel'">
                <el-button
                  v-if="nodeInfo.weight >= 7"
                  type="primary"
                  @click="openExcelEditor"
                  class="edit-excel-data"
                >
                  <template #icon>
                    <Icon name="icon_edit_outlined"><icon_edit_outlined class="svg-icon" /></Icon>
                  </template>
                  {{ t('data_source.edit_data') }}
                </el-button>
                <el-upload
                  v-if="nodeInfo.weight >= 7"
                  action=""
                  :multiple="false"
                  ref="uploadAgain"
                  :show-file-list="false"
                  accept=".xls,.xlsx,.csv"
                  :on-change="onChange"
                  :http-request="() => uploadExcel(0)"
                  name="file"
                >
                  <template #trigger>
                    <el-button v-loading="replaceLoading" class="replace-excel" type="primary">
                      <template #icon>
                        <Icon name="icon_edit_outlined"
                          ><icon_edit_outlined class="svg-icon"
                        /></Icon>
                      </template>
                      {{ t('data_source.replace_data') }}
                    </el-button>
                  </template>
                </el-upload>

                <el-upload
                  v-if="nodeInfo.weight >= 7"
                  action=""
                  :multiple="false"
                  ref="uploadAgain"
                  :show-file-list="false"
                  accept=".xls,.xlsx,.csv"
                  :on-change="onChange"
                  :http-request="() => uploadExcel(1)"
                  name="file"
                >
                  <template #trigger>
                    <el-button v-loading="addLoading" type="primary">
                      <template #icon>
                        <Icon name="icon_new-item_outlined"
                          ><icon_newItem_outlined class="svg-icon"
                        /></Icon>
                      </template>
                      {{ t('data_source.append_data') }}
                    </el-button>
                  </template>
                </el-upload>
              </template>
              <el-button v-else-if="nodeInfo.weight >= 7" @click="editDatasource()" type="primary">
                <template #icon>
                  <Icon name="icon_edit_outlined"><icon_edit_outlined class="svg-icon" /></Icon>
                </template>
                {{ t('chart.edit') }}
              </el-button>
            </div>
          </div>
          <div class="tab-border">
            <el-tabs v-model="activeName" @tab-change="handleClick">
              <el-tab-pane :label="t('datasource.config')" name="config"></el-tab-pane>
              <el-tab-pane :label="t('datasource.table')" name="table"></el-tab-pane>
            </el-tabs>
          </div>
        </div>
        <div v-if="activeName === 'table'" class="datasource-table">
          <div class="search-operate">
            <el-input
              ref="search"
              v-model="nickName"
              :placeholder="t('commons.search')"
              clearable
              @input="initSearch"
              style="width: 240px"
            >
              <template #prefix>
                <el-icon>
                  <Icon name="icon_search-outline_outlined"
                    ><icon_searchOutline_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </template>
            </el-input>
          </div>
          <div class="info-table">
            <grid-table
              :pagination="state.paginationConfig"
              :table-data="pagingTable"
              :is-search="!!nickName.trim()"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
            >
              <el-table-column
                key="tableName"
                prop="tableName"
                :label="t('datasource.table_name')"
              />
              <el-table-column
                key="status"
                prop="status"
                v-if="nodeInfo.type.startsWith('API')"
                :label="t('data_source.latest_update_status')"
              >
                <template #default="scope">
                  <div class="flex-align-center">
                    <template v-if="scope.row.status === 'Completed'">
                      <el-icon style="margin-right: 8px">
                        <icon name="icon_succeed_filled"
                          ><icon_succeed_filled class="svg-icon"
                        /></icon>
                      </el-icon>
                      {{ t('dataset.completed') }}
                    </template>
                    <template v-if="scope.row.status === 'UnderExecution'">
                      {{ t('dataset.underway') }}
                    </template>
                    <template v-if="scope.row.status === 'Error' || scope.row.status === 'Warning'">
                      <el-icon style="margin-right: 8px">
                        <icon class="field-icon-red" name="icon_close_filled"
                          ><icon_close_filled class="svg-icon field-icon-red"
                        /></icon>
                      </el-icon>
                      {{ t('dataset.error') }}
                    </template>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                key="lastUpdateTime"
                prop="lastUpdateTime"
                v-if="
                  ['excel', 'api'].includes(nodeInfo.type.toLowerCase()) ||
                  nodeInfo.type.startsWith('API')
                "
                :label="t('data_source.latest_update_time')"
              >
                <template v-slot:default="scope">
                  <span>{{ timestampFormatDate(scope.row.lastUpdateTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                key="__operation"
                :label="t('commons.operating')"
                fixed="right"
                width="108"
              >
                <template #default="scope">
                  <el-tooltip effect="dark" :content="t('data_set.a_new_dataset')" placement="top">
                    <el-button
                      @click.stop="createDataset(scope.row.tableName)"
                      text
                      v-permission="['dataset']"
                    >
                      <template #icon>
                        <Icon name="icon_dataset_outlined"
                          ><icon_dataset_outlined class="svg-icon"
                        /></Icon>
                      </template>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip effect="dark" :content="t('visualization.details')" placement="top">
                    <el-button @click.stop="selectDataset(scope.row)" text>
                      <template #icon>
                        <Icon name="icon_describe_outlined"
                          ><icon_describe_outlined class="svg-icon"
                        /></Icon>
                      </template>
                    </el-button>
                  </el-tooltip>
                </template>
              </el-table-column>
            </grid-table>
          </div>
        </div>
        <template v-else>
          <BaseInfoContent v-slot="slotProps" :name="t('datasource.base_info')">
            <template v-if="slotProps.active">
              <el-row :gutter="24">
                <el-col :span="12">
                  <BaseInfoItem :label="t('data_source.data_source_name')">{{
                    nodeInfo.name
                  }}</BaseInfoItem>
                </el-col>
                <el-col :span="12">
                  <BaseInfoItem :label="t('datasource.type')">{{
                    typeMap[nodeInfo.type]
                  }}</BaseInfoItem>
                </el-col>
              </el-row>
              <el-row :gutter="24">
                <el-col v-if="nodeInfo.type === 'Excel'" :span="12">
                  <BaseInfoItem :label="t('data_source.document')">
                    <ExcelInfoBase :name="nodeInfo.fileName" :size="nodeInfo.size"></ExcelInfoBase>
                  </BaseInfoItem>
                </el-col>
                <el-col v-if="nodeInfo.type === 'ExcelRemote'" :span="12">
                  <BaseInfoItem :label="t('datasource.remote_excel_url')">
                    {{ nodeInfo.configuration.url }}
                  </BaseInfoItem>
                </el-col>
                <el-col v-if="nodeInfo.type === 'ExcelRemote'" :span="12">
                  <BaseInfoItem :label="t('data_source.document')">
                    <ExcelInfoBase :name="nodeInfo.fileName" :size="nodeInfo.size"></ExcelInfoBase>
                  </BaseInfoItem>
                </el-col>
                <el-col v-if="!nodeInfo.type.startsWith('Excel')" :span="24">
                  <BaseInfoItem :label="t('common.description')">{{
                    nodeInfo.description
                  }}</BaseInfoItem>
                </el-col>
              </el-row>
              <template
                v-if="
                  !['Excel', 'es'].includes(nodeInfo.type) &&
                  !nodeInfo.type.startsWith('API') &&
                  !nodeInfo.type.startsWith('Excel') &&
                  nodeInfo.weight >= 7
                "
              >
                <el-row :gutter="24" v-show="nodeInfo.configuration.urlType !== 'jdbcUrl'">
                  <el-col :span="12">
                    <BaseInfoItem :label="t('datasource.host')">{{
                      nodeInfo.configuration.host
                    }}</BaseInfoItem>
                  </el-col>
                </el-row>
                <el-row :gutter="24" v-show="nodeInfo.configuration.urlType !== 'jdbcUrl'">
                  <el-col :span="12">
                    <BaseInfoItem :label="t('datasource.port')">{{
                      nodeInfo.configuration.port
                    }}</BaseInfoItem>
                  </el-col>
                  <el-col :span="12">
                    <BaseInfoItem :label="t('datasource.data_base')">{{
                      nodeInfo.configuration.dataBase
                    }}</BaseInfoItem>
                  </el-col>
                </el-row>
                <el-row :gutter="24" v-show="nodeInfo.configuration.urlType !== 'jdbcUrl'">
                  <el-col :span="12">
                    <BaseInfoItem :label="t('datasource.user_name')">{{
                      nodeInfo.configuration.username
                    }}</BaseInfoItem>
                  </el-col>
                  <el-col :span="12">
                    <BaseInfoItem :label="t('datasource.extra_params')">{{
                      nodeInfo.configuration.extraParams
                    }}</BaseInfoItem>
                  </el-col>
                </el-row>
                <el-row :gutter="24" v-show="nodeInfo.configuration.urlType === 'jdbcUrl'">
                  <el-col :span="12">
                    <BaseInfoItem :label="t('datasource.jdbcUrl')">{{
                      nodeInfo.configuration.jdbcUrl
                    }}</BaseInfoItem>
                  </el-col>
                </el-row>
                <el-row :gutter="24" v-show="nodeInfo.configuration.urlType === 'jdbcUrl'">
                  <el-col :span="12">
                    <BaseInfoItem :label="t('datasource.user_name')">{{
                      nodeInfo.configuration.username
                    }}</BaseInfoItem>
                  </el-col>
                </el-row>
                <el-row :gutter="24">
                  <span
                    v-if="
                      !['es', 'api'].includes(nodeInfo.type.toLowerCase()) &&
                      nodeInfo.configuration.urlType !== 'jdbcUrl'
                    "
                    class="crest-expand"
                    @click="showSSH = !showSSH"
                    >{{ t('data_source.ssh_settings') }}
                    <el-icon>
                      <Icon
                        ><component
                          :is="showSSH ? icon_down_outlined : icon_down_outlined1"
                        ></component
                      ></Icon>
                    </el-icon>
                  </span>
                </el-row>
                <template v-if="showSSH">
                  <el-row :gutter="24" v-if="nodeInfo.configuration.useSSH">
                    <el-col :span="12">
                      <BaseInfoItem :label="t('data_source.host')">{{
                        nodeInfo.configuration.sshHost
                      }}</BaseInfoItem>
                    </el-col>
                    <el-col :span="12">
                      <BaseInfoItem :label="t('data_source.port')">{{
                        nodeInfo.configuration.sshPort
                      }}</BaseInfoItem>
                    </el-col>
                  </el-row>
                  <el-row :gutter="24" v-if="nodeInfo.configuration.useSSH">
                    <el-col :span="12">
                      <BaseInfoItem :label="t('datasource.user_name')">{{
                        nodeInfo.configuration.sshUserName
                      }}</BaseInfoItem>
                    </el-col>
                  </el-row>
                </template>
                <el-row :gutter="24">
                  <span
                    v-if="!['es', 'api'].includes(nodeInfo.type.toLowerCase())"
                    class="crest-expand"
                    @click="showPriority = !showPriority"
                    >{{ t('datasource.priority') }}
                    <el-icon>
                      <Icon
                        ><component
                          :is="showPriority ? icon_down_outlined : icon_down_outlined1"
                        ></component
                      ></Icon>
                    </el-icon>
                  </span>
                </el-row>

                <template v-if="showPriority">
                  <el-row :gutter="24">
                    <el-col :span="12">
                      <BaseInfoItem :label="t('datasource.initial_pool_size')">{{
                        nodeInfo.configuration.initialPoolSize || 5
                      }}</BaseInfoItem>
                    </el-col>
                    <el-col :span="12">
                      <BaseInfoItem :label="t('datasource.min_pool_size')">{{
                        nodeInfo.configuration.minPoolSize || 5
                      }}</BaseInfoItem>
                    </el-col>
                  </el-row>
                  <el-row :gutter="24">
                    <el-col :span="12">
                      <BaseInfoItem :label="t('datasource.max_pool_size')">{{
                        nodeInfo.configuration.maxPoolSize || 5
                      }}</BaseInfoItem>
                    </el-col>
                    <el-col :span="12">
                      <BaseInfoItem
                        :value="nodeInfo.configuration.queryTimeout"
                        :label="t('datasource.query_timeout')"
                        >{{ nodeInfo.configuration.queryTimeout || '30'
                        }}{{ t('common.second') }}</BaseInfoItem
                      >
                    </el-col>
                  </el-row>
                </template>

                <!--    数据填报      -->
              </template>
              <template v-if="['es'].includes(nodeInfo.type) && nodeInfo.weight >= 7">
                <el-row :gutter="24">
                  <el-col :span="12">
                    <BaseInfoItem :label="t('datasource.datasource_url')">{{
                      nodeInfo.configuration.url
                    }}</BaseInfoItem>
                  </el-col>
                </el-row>
              </template>
            </template>
          </BaseInfoContent>
          <BaseInfoContent
            v-if="nodeInfo.type.startsWith('API') && nodeInfo.weight >= 7"
            v-slot="slotProps"
            :name="t('datasource.data_table')"
          >
            <div class="api-card-content" v-if="slotProps.active">
              <div v-for="api in nodeInfo.apiConfiguration" :key="api.id" class="api-card">
                <el-row>
                  <el-col :span="19">
                    <span class="name">
                      <span class="ellipsis" :title="api.name">{{ api.name }}</span>
                    </span>
                    <span v-if="api.status === 'Error'" class="crest-tag error-color">{{
                      t('datasource.invalid')
                    }}</span>
                    <span v-if="api.status === 'Success'" class="crest-tag success-color">{{
                      t('datasource.valid')
                    }}</span>
                  </el-col>
                  <el-col style="text-align: right" :span="5">
                    <el-button @click="updateApiTable(api)" text>
                      <template #icon>
                        <icon name="icon_replace_outlined"
                          ><icon_replace_outlined class="svg-icon"
                        /></icon>
                      </template>
                    </el-button>
                  </el-col>
                </el-row>
                <div>
                  {{ t('data_source.data_time') }} {{ timestampFormatDate(api['updateTime']) }}
                </div>

                <div class="req-title">
                  <span>{{ t('datasource.method') }}</span>
                  <span>{{ t('datasource.url') }}</span>
                </div>
                <div class="req-value">
                  <span>{{ api.method }}</span>
                  <el-tooltip effect="dark" :content="api.url" placement="top">
                    <span>{{ api.url }}</span>
                  </el-tooltip>
                </div>
              </div>
            </div>
            <el-button @click="updateApiDs" class="update-records" text>
              <template #icon>
                <icon name="icon_replace_outlined"><icon_replace_outlined class="svg-icon" /></icon>
              </template>
              {{ t('data_source.update_all') }}
            </el-button>
          </BaseInfoContent>
          <BaseInfoContent
            v-if="nodeInfo.type.startsWith('Excel')"
            v-slot="slotProps"
            :name="t('dataset.data_preview')"
            :time="Number(nodeInfo.lastSyncTime) || undefined"
            :showTime="nodeInfo.type === 'ExcelRemote'"
          >
            <template v-if="slotProps.active">
              <div class="excel-table">
                <SheetTabs
                  :active-tab="activeTab"
                  @tab-click="handleTabClick"
                  :tab-list="tabList"
                ></SheetTabs>
                <div class="sheet-table-content">
                  <el-auto-resizer>
                    <template #default="{ height, width }">
                      <el-table-v2
                        class="crest-data-table-v2"
                        :columns="columns"
                        v-loading="dataPreviewLoading"
                        header-class="excel-header-cell"
                        :data="tabData"
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
                </div>
              </div>
            </template>
          </BaseInfoContent>
          <BaseInfoContent
            v-if="
              (nodeInfo.type.startsWith('API') || nodeInfo.type === 'ExcelRemote') &&
              nodeInfo.weight >= 7
            "
            v-slot="slotProps"
            :name="t('dataset.update_setting')"
            :time="Number(nodeInfo.lastSyncTime) || undefined"
          >
            <template v-if="slotProps.active">
              <el-row :gutter="24">
                <el-col :span="12">
                  <BaseInfoItem :label="t('dataset.update_type')">{{
                    t(`dataset.${nodeInfo.syncSetting.updateType}`)
                  }}</BaseInfoItem>
                </el-col>
                <el-col :span="12">
                  <BaseInfoItem :label="t('dataset.execute_rate')">
                    <p
                      class="value"
                      :key="ele"
                      v-for="ele in formatSimpleCron(nodeInfo.syncSetting)"
                    >
                      {{ ele }}
                    </p>
                  </BaseInfoItem>
                </el-col>
              </el-row>
            </template>
            <el-button @click="getRecord" class="update-records" text>
              <template #icon>
                <icon name="icon_describe_outlined"
                  ><icon_describe_outlined class="svg-icon"
                /></icon>
              </template>
              {{ t('dataset.update_records') }}
            </el-button>
          </BaseInfoContent>
        </template>
      </template>
      <template v-else-if="mounted">
        <empty-background :description="t('data_source.on_the_left')" img-type="select" />
      </template>
    </div>
    <EditorDatasource @refresh="refresh" ref="datasourceEditor"></EditorDatasource>
    <el-dialog
      :title="t('common.detail')"
      v-model="userDrawer"
      class="ds-table-drawer"
      width="840px"
      top="60px"
    >
      <div style="overflow: hidden">
        <el-row :gutter="24">
          <el-col :span="12">
            <p class="table-name">
              {{ t('datasource.table_name') }}
            </p>
            <p class="table-value">
              {{ dsTableDetail.tableName }}
            </p>
          </el-col>
          <el-col :span="12">
            <p class="table-name">
              {{ t('datasource.table_description') }}
            </p>
            <p class="table-value">
              {{ dsTableDetail.name || '-' }}
            </p>
          </el-col>
        </el-row>
      </div>
      <el-scrollbar>
        <el-table
          v-loading="dsTableDataLoading"
          class="crest-data-table"
          header-cell-class-name="header-cell"
          :data="state.dsTableData"
          stripe
          style="width: 100%"
        >
          <el-table-column prop="originName" :label="t('datasource.column_name')" />
          <el-table-column prop="type" :label="t('datasource.field_type')">
            <template #default="scope">
              <div class="flex-align-center icon">
                <el-icon>
                  <icon :class="`field-icon-${fieldType[scope.row.fieldType]}`"
                    ><component
                      class="svg-icon"
                      :class="`field-icon-${fieldType[scope.row.fieldType]}`"
                      :is="iconFieldMap[fieldType[scope.row.fieldType]]"
                    ></component
                  ></icon>
                </el-icon>
                {{
                  t(`dataset.${fieldType[scope.row.fieldType]}`) +
                  `${scope.row.fieldType === 3 ? '(' + t('dataset.float') + ')' : ''}`
                }}
              </div>
            </template>
          </el-table-column>
          <el-table-column
            prop="name"
            show-overflow-tooltip
            :label="t('datasource.field_description')"
          />
        </el-table>
      </el-scrollbar>
    </el-dialog>
    <el-dialog
      v-model="excelEditState.visible"
      :title="t('data_source.edit_data')"
      class="excel-edit-dialog"
      fullscreen
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :before-close="closeExcelEditor"
    >
      <div class="excel-edit-layout">
        <div class="excel-edit-toolbar">
          <div class="excel-edit-toolbar-left">
            <el-select
              v-model="excelEditState.tableName"
              filterable
              :placeholder="t('data_source.select_edit_table')"
              style="width: 280px"
              @change="handleExcelEditTableChange"
            >
              <el-option
                v-for="tab in tabList"
                :key="tab.value"
                :label="tab.label"
                :value="tab.value"
              />
            </el-select>
            <span class="excel-edit-summary">
              {{ excelEditState.total }}
            </span>
          </div>
          <div class="excel-edit-toolbar-right">
            <el-button
              secondary
              @click="refreshExcelEditData"
              :disabled="excelEditState.loading || excelEditState.saving"
            >
              <template #icon>
                <icon name="icon_refresh_outlined"><icon_refresh_outlined class="svg-icon" /></icon>
              </template>
              {{ t('data_source.refresh_data') }}
            </el-button>
            <el-button secondary @click="addExcelEditRow" :disabled="excelEditState.loading">
              <template #icon>
                <Icon name="icon_new-item_outlined"
                  ><icon_newItem_outlined class="svg-icon"
                /></Icon>
              </template>
              {{ t('data_source.add_row') }}
            </el-button>
            <el-button secondary @click="deleteExcelEditRows" :disabled="excelEditState.loading">
              <template #icon>
                <Icon name="icon_delete-trash_outlined"
                  ><icon_deleteTrash_outlined class="svg-icon"
                /></Icon>
              </template>
              {{ t('data_source.delete_row') }}
            </el-button>
          </div>
        </div>
        <el-table
          v-loading="excelEditState.loading"
          :data="excelEditState.rows"
          border
          stripe
          height="100%"
          class="excel-edit-table crest-data-table"
          @selection-change="handleExcelEditSelectionChange"
        >
          <el-table-column type="selection" width="44" fixed />
          <el-table-column type="index" width="64" fixed />
          <el-table-column
            v-for="field in excelEditState.fields"
            :key="excelEditFieldKey(field)"
            :prop="excelEditFieldKey(field)"
            :min-width="excelEditColumnWidth(field)"
            show-overflow-tooltip
          >
            <template #header>
              <div class="excel-edit-header-cell">
                <el-icon>
                  <icon :class="`field-icon-${excelEditFieldType(field)}`">
                    <component
                      class="svg-icon"
                      :class="`field-icon-${excelEditFieldType(field)}`"
                      :is="iconFieldMap[excelEditFieldType(field)]"
                    ></component>
                  </icon>
                </el-icon>
                <span class="ellipsis" :title="field.name">{{ field.name }}</span>
              </div>
            </template>
            <template #default="scope">
              <el-input
                v-model="scope.row[excelEditFieldKey(field)]"
                size="small"
                clearable
                @input="markExcelEditDirty(scope.row)"
              />
            </template>
          </el-table-column>
          <template #empty>
            <empty-background :description="t('data_set.no_data')" img-type="noneWhite" />
          </template>
        </el-table>
        <div class="excel-edit-footer">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :page-sizes="[50, 100, 200, 500]"
            :total="excelEditState.total"
            :page-size="excelEditState.pageSize"
            :current-page="excelEditState.page"
            @size-change="handleExcelEditSizeChange"
            @current-change="handleExcelEditPageChange"
          />
          <div class="excel-edit-footer-actions">
            <el-button secondary @click="() => closeExcelEditor()">
              <template #icon>
                <Icon name="icon_left_outlined"><icon_left_outlined class="svg-icon" /></Icon>
              </template>
              {{ t('data_source.edit_data_back') }}
            </el-button>
            <el-button
              type="primary"
              :loading="excelEditState.saving"
              :disabled="excelEditState.loading"
              @click="saveExcelEditor"
            >
              {{ t('common.save') }}
            </el-button>
          </div>
        </div>
      </div>
    </el-dialog>
    <creat-ds-group @finish="saveDsFolder" ref="creatDsFolder"></creat-ds-group>
    <el-drawer
      v-model="showRecord"
      :title="t('dataset.update_records')"
      :close-on-press-escape="false"
      :close-on-click-modal="false"
      modal-class="record-drawer"
      direction="rtl"
      size="840px"
    >
      <grid-table
        :pagination="recordState.paginationConfig"
        :table-data="recordData"
        @size-change="handleRecordSizeChange"
        @current-change="handleRecordCurrentChange"
      >
        <el-table-column prop="name" :label="t('datasource.data_table')"></el-table-column>
        <el-table-column prop="triggerType" :label="t('datasource.sync_rate')">
          <template #default="scope">
            <div class="flex-align-center">
              <template v-if="scope.row.triggerType === 'CRON'">
                {{ t('datasource.cron_config') }}
              </template>
              <template v-if="scope.row.triggerType === 'RIGHTNOW'">
                {{ t('datasource.execute_once') }}
              </template>
              <template v-if="scope.row.triggerType === 'SIMPLE_CRON'">
                {{ t('datasource.simple_cron') }}
              </template>
              <template v-if="scope.row.triggerType === 'MANUAL'">
                {{ t('datasource.manual') }}
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" :label="t('datasource.start_time')">
          <template v-slot:default="scope">
            <span>{{ timestampFormatDate(scope.row.startTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="endTime" :label="t('datasource.end_time')">
          <template v-slot:default="scope">
            <span>{{ timestampFormatDate(scope.row.endTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="taskStatus" :label="t('data_source.update_result')">
          <template #default="scope">
            <div class="flex-align-center">
              <template v-if="scope.row.taskStatus === 'Completed'">
                <el-icon style="margin-right: 8px">
                  <icon name="icon_succeed_filled"><icon_succeed_filled class="svg-icon" /></icon>
                </el-icon>
                {{ t('dataset.completed') }}
              </template>
              <template v-if="scope.row.taskStatus === 'UnderExecution'">
                {{ t('dataset.underway') }}
              </template>

              <template
                v-if="scope.row.taskStatus === 'Error' || scope.row.taskStatus === 'Warning'"
              >
                <el-icon style="margin-right: 8px">
                  <icon class="field-icon-red" name="icon_close_filled"
                    ><icon_close_filled class="svg-icon field-icon-red"
                  /></icon>
                </el-icon>
                {{ t('dataset.error') }}
                <el-icon @click="showErrorInfo(scope.row.info)" class="error-info">
                  <icon name="icon-maybe_outlined"><iconMaybe_outlined class="svg-icon" /></icon>
                </el-icon>
              </template>
            </div>
          </template>
        </el-table-column>
      </grid-table>
    </el-drawer>
    <el-dialog
      v-model="dialogErrorInfo"
      :close-on-press-escape="false"
      :close-on-click-modal="false"
      :title="t('data_source.failure_details')"
      width="600px"
    >
      <span>{{ dialogMsg }}</span>
      <template #footer>
        <span class="dialog-footer">
          <el-button secondary @click="dialogErrorInfo = false">
            {{ t('chart.close') }}
          </el-button>
        </span>
      </template>
    </el-dialog>
    <relationChart ref="relationChartRef"></relationChart>
  </div>
</template>

<style lang="less" scoped>
@import '@/style/mixin.less';

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
.datasource-manage {
  display: flex;
  width: 100%;
  height: 100%;
  background: #fff;
  position: relative;

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

  .update-records {
    position: absolute;
    top: 19px;
    right: 12px;
  }

  .update-info {
    display: inline-flex;
    height: 24px;
    padding: 1px 6px;
    align-items: center;
    border-radius: 2px;

    &.to-be-updated {
      background: rgba(31, 35, 41, 0.1);
      color: #646a73;
    }
    &.updating {
      color: var(--ed-color-primary-dark-2, #2b5fd9);
      background: var(--ed-color-primary-33, rgba(59, 130, 246, 0.2));
    }
    &.pause {
      background: rgba(31, 35, 41, 0.1);
      color: #646a73;
    }
    &.updated {
      color: #2ca91f;
      background: rgba(52, 199, 36, 0.2);
    }
  }

  .icon-border {
    font-size: 18px;
  }

  .excel-table {
    margin-top: 16px;

    .sheet-table-content {
      height: 400px;
    }
  }

  .api-card-content {
    display: flex;
    flex-wrap: wrap;
    margin-top: 16px;
    margin-left: -16px;
  }

  .api-card {
    width: calc(50% - 16px);
    height: 140px;
    border-radius: 6px;
    border: 1px solid var(--crestCardStrokeColor, #dee0e3);
    border-radius: 6px;
    margin: 0 0 16px 16px;
    padding: 16px;
    font-family: var(--crest-custom_font, 'PingFang');
    .name {
      font-size: 16px;
      font-weight: 500;
      margin-right: 8px;
      max-width: 70%;
      display: inline-flex;
    }
    .req-title,
    .req-value {
      display: flex;
      font-size: 14px;
      font-weight: 400;
      :nth-child(1) {
        width: 110px;
      }

      :nth-child(2) {
        margin-left: 24px;
        max-width: calc(100% - 124px);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
    .req-title {
      color: var(--crestTextSecondary, #646a73);
      margin: 16px 0 4px 0;
    }
    .req-value {
      color: var(--crestTextPrimary, #1f2329);
    }
    .copy-icon {
      cursor: pointer;
      margin-right: 20px;
      color: var(--crestTextSecondary, #646a73);
    }
    .delete-icon:not(.not-allow) {
      cursor: pointer;
      &:hover {
        color: var(--crestDanger, #f54a45);
      }
    }
    .crest-tag {
      display: inline-flex;
      justify-content: center;
      align-items: center;
      border-radius: 2px;
      padding: 1px 6px;
      height: 24px;
      font-size: 14px;
    }

    .error-color {
      color: #646a73;
      background-color: rgba(31, 35, 41, 10%);
    }
    .success-color {
      color: green;
      background-color: rgba(52, 199, 36, 20%);
    }
  }

  .crest-expand {
    font-family: var(--crest-custom_font, 'PingFang');
    font-size: 14px;
    font-weight: 400;
    line-height: 22px;
    color: var(--ed-color-primary);
    cursor: pointer;
    margin-top: 16px;
    display: inline-flex;
    align-items: center;

    .ed-icon {
      margin-left: 4px;
    }
  }

  .datasource-height,
  .datasource-content {
    height: calc(100vh - 56px);
    overflow: auto;
    position: relative;
    &.h100 {
      .datasource-table {
        height: calc(100% - 140px);
      }
    }
  }

  .datasource-list {
    width: 279px;
    padding: 16px 8px;
  }

  .datasource-content {
    background: #f5f6f7;
    overflow-y: auto;
    &.auto {
      height: auto;
    }
  }

  .m24 {
    margin: 24px 0;
  }

  .w100 {
    width: 100%;
  }

  .datasource-content {
    flex: 1;
    position: relative;

    .datasource-info {
      background: #fff;
      padding: 0 24px;
      padding-top: 12px;
      height: 90px;
      position: sticky;
      top: 0;
      z-index: 6;
      .info-method {
        width: 100%;
        display: flex;
        align-items: center;
        font-family: var(--crest-custom_font, 'PingFang');
        font-size: 16px;
        font-weight: 500;

        .ed-icon {
          font-size: 24px;
        }

        .name {
          margin-left: 8px;
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
          .edit-excel-data {
            margin-left: 12px;
          }
          .replace-excel {
            margin: 0 12px;
          }
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

    .datasource-table {
      padding: 24px;
      margin: 24px;
      background: #fff;
      height: calc(100vh - 200px);

      .search-operate {
        width: 280px;
        margin-bottom: 16px;
      }
    }

    .info-table {
      height: calc(100% - 49px);
    }
  }
}

.custom-tree {
  height: calc(100vh - 148px);
  padding: 0 8px;
}

.custom-tree-node {
  width: calc(100% - 34px);
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

      &.excel {
        width: calc(100% - 54px);
      }
    }

    .icon-more {
      display: inline-flex;
    }
  }
}

.datasource-manage {
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

  .datasource-content {
    background: #f8fafc;

    .datasource-info {
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

        .name {
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

    .datasource-table {
      margin: 16px 24px 24px;
      padding: 18px;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      background: #ffffff;
      box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
      height: calc(100vh - 204px);
    }
  }

  .api-card {
    border-color: #e2e8f0;
    border-radius: 14px;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
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

:deep(.ed-table) {
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

:deep(.ed-pagination.is-background .ed-pager li.is-active) {
  background: #0f172a;
  color: #ffffff;
}
</style>
<style lang="less">
.record-drawer {
  .ed-drawer__body {
    padding: 24px;
  }

  .flex-align-center {
    .ed-icon {
      margin: 0 4px;
    }

    .error-info {
      cursor: pointer;
    }
  }
}
.ds-table-drawer {
  max-height: calc(100% - 120px);
  display: flex;
  flex-direction: column;

  .ed-dialog__body {
    overflow-y: auto;
  }

  .table-value,
  .table-name {
    font-family: var(--crest-custom_font, 'PingFang');
    font-size: 14px;
    font-weight: 400;
    margin: 0;
  }

  .table-name {
    color: var(--crestTextSecondary, #646a73);
  }

  .table-value {
    margin: 4px 0 24px 0;
    color: var(--crestTextPrimary, #1f2329);
  }
}

.excel-edit-dialog {
  .ed-dialog__body {
    height: calc(100vh - 54px);
    padding: 16px 24px 20px;
    box-sizing: border-box;
  }

  .excel-edit-layout {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 0;
  }

  .excel-edit-toolbar,
  .excel-edit-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex: 0 0 auto;
  }

  .excel-edit-toolbar {
    margin-bottom: 12px;
  }

  .excel-edit-toolbar-left,
  .excel-edit-toolbar-right,
  .excel-edit-footer-actions,
  .excel-edit-header-cell {
    display: flex;
    align-items: center;
  }

  .excel-edit-toolbar-right,
  .excel-edit-footer-actions {
    gap: 8px;
  }

  .excel-edit-summary {
    margin-left: 12px;
    color: var(--crestTextSecondary, #646a73);
    font-size: 14px;
  }

  .excel-edit-table {
    flex: 1 1 auto;
    min-height: 0;
  }

  .excel-edit-header-cell {
    min-width: 0;

    .ed-icon {
      margin-right: 6px;
    }
  }

  .excel-edit-footer {
    padding-top: 12px;
  }
}
</style>
