<script lang="tsx" setup>
import dvFolder from '@/assets/svg/dv-folder.svg'
import icon_left_outlined from '@/assets/svg/icon_left_outlined.svg'
import icon_right_outlined from '@/assets/svg/icon_right_outlined.svg'
import referenceTable from '@/assets/svg/reference-table.svg'
import icon_organization_outlined from '@/assets/svg/icon_organization_outlined.svg'
import icon_searchOutline_outlined from '@/assets/svg/icon_search-outline_outlined.svg'
import icon_sql_outlined_1 from '@/assets/svg/icon_sql_outlined_1.svg'
import icon_warning_colorful from '@/assets/svg/icon_warning_colorful.svg'
import icon_add_outlined from '@/assets/svg/icon_add_outlined.svg'
import icon_refresh_outlined from '@/assets/svg/icon_refresh_outlined.svg'
import icon_expandRight_filled from '@/assets/svg/icon_expand-right_filled.svg'
import icon_switch_outlined from '@/assets/svg/icon_switch_outlined.svg'
import icon_copy_outlined from '@/assets/svg/icon_copy_outlined.svg'
import icon_deleteTrash_outlined from '@/assets/svg/icon_delete-trash_outlined.svg'
import icon_edit_outlined from '@/assets/svg/icon_edit_outlined.svg'
import icon_info_outlined from '@/assets/svg/icon_info_outlined.svg'
import dayjs from 'dayjs'
import {
  iconFieldCalculatedMap,
  iconFieldCalculatedQMap
} from '@/components/icon-group/field-calculated-list'
import { enumValueDs } from '@/api/dataset'
import {
  ref,
  toRaw,
  unref,
  nextTick,
  reactive,
  shallowRef,
  computed,
  watch,
  provide,
  h,
  onMounted,
  onBeforeUnmount
} from 'vue'
import { useCache } from '@/hooks/web/useCache'
import { useI18n } from '@/hooks/web/useI18n'
import { useEmitt } from '@/hooks/web/useEmitt'
import { ElIcon, ElMessageBox, ElMessage } from 'element-plus-secondary'
import FixedSizeList from 'element-plus-secondary/es/components/virtual-list/src/components/fixed-size-list.mjs'
import type { Action } from 'element-plus-secondary'
import FieldMore from './FieldMore.vue'
import EmptyBackground from '@/components/empty-background/src/EmptyBackground.vue'
import { Icon } from '@/components/icon-custom'
import { useWindowSize } from '@vueuse/core'
import CalcFieldEdit from './CalcFieldEdit.vue'
import { useRoute, useRouter } from 'vue-router_2'
import UnionEdit from './UnionEdit.vue'
import type { FormInstance } from 'element-plus-secondary'
import type { BusiTreeNode } from '@/models/tree/TreeNode'
import CreatDsGroup from './CreatDsGroup.vue'
import { guid, getFieldName, timeTypes, num, type DataSource } from './util'
import { fieldType } from '@/utils/attr'
import { cancelMap } from '@/config/axios/service'
import { useEmbedded } from '@/store/modules/embedded'
import { useAppStoreWithOut } from '@/store/modules/app'
import {
  datasourceList,
  tables,
  getPreviewData,
  datasetDetails,
  saveDatasetTree,
  barInfoApi,
  datasetSyncTask,
  saveDatasetSyncTask,
  executeDatasetSync,
  stopDatasetSync,
  datasetSyncLogs
} from '@/api/dataset'
import type { DatasetSyncLog, DatasetSyncTask, Table } from '@/api/dataset'
import DatasetUnion from './DatasetUnion.vue'
import { cloneDeep, debounce } from 'lodash-es'
import { iconFieldMap } from '@/components/icon-group/field-list'
import { iconDatasourceMap } from '@/components/icon-group/datasource-list'
import {
  applyDatasetSyncTaskPatch,
  buildDatasetSyncTaskPayload,
  executeDatasetSyncNowFlow,
  formatDatasetSyncRowCount,
  getDatasetSyncNowDisabledReason,
  getDatasetSyncStatusText,
  getIncrementalDisabledReason,
  isDatasetIncrementalField,
  isDatasetSyncField,
  normalizeDatasetSyncInfo
} from './datasetSyncWorkflow.mjs'
interface DragEvent extends MouseEvent {
  dataTransfer: DataTransfer
}

interface Field {
  fieldShortName: string
  name: string
  engineFieldName: string
  originName: string
  fieldType: number
}
const { wsCache } = useCache()
const appStore = useAppStoreWithOut()
const embeddedStore = useEmbedded()
const { t } = useI18n()
const route = useRoute()
const { push } = useRouter() || {
  push: val => {
    if (embeddedStore.getToken) return
    window.location.href = val as string
  }
}
// 指标表格区域的初始高度，随下方面板拖拽动态调整
const quotaTableHeight = ref(238)
// 数据集目录创建弹窗实例
const creatDsFolder = ref()
// 计算字段编辑弹窗显示状态
const editCalcField = ref(false)
// 计算字段编辑组件实例
const calcEdit = ref()
// 联合关系编辑弹窗显示状态
const editUnion = ref(false)
// 联合树画布组件实例
const datasetDrag = ref()
// 当前数据集名称，未命名时使用默认展示名
const datasetName = ref(t('data_set.unnamed_dataset'))
// 预览和批量管理页签当前值
const tabActive = ref('preview')
// 左侧数据表列表当前激活表名
const activeName = ref('')
// 当前选择的数据源 ID
const dataSource = ref('')
// 左侧数据表搜索关键字
const searchTable = ref('')
// 数据集名称是否处于输入编辑态
const showInput = ref(false)
// 左侧数据表加载状态
const dsLoading = ref(false)
// 左侧数据源面板宽度
const LeftWidth = ref(240)
// 拖拽数据表时记录鼠标横向偏移
const offsetX = ref(0)
// 拖拽数据表时记录鼠标纵向偏移
const offsetY = ref(0)
// 左侧数据源面板是否展开
const showLeft = ref(true)
// 联合画布拖拽遮罩显示状态
const maskShow = ref(false)
// 保存和初始化加载状态
const loading = ref(false)
// 自定义时间格式弹窗显示状态
const updateCustomTime = ref(false)
// 数据集名称输入框实例
const editorName = ref()
// 数据表 ID 到展示名称的缓存映射
const nameMap = ref({})
// 当前正在编辑时间格式或批量时间格式的字段上下文
const currentField = ref({
  dateFormat: '',
  id: '',
  dateFormatType: '',
  name: '',
  idArr: []
})
// 当前数据集是否跨源
const isCross = ref(false)
// 数据集读取模式，0 为直连，1 为缓存
const syncMode = ref(0)
// 同步支持状态刷新触发器，用于强制重新计算 SQL 参数依赖
const syncSupportRefreshKey = ref(0)
let isUpdate = false
let pendingSyncSave: Promise<void> | null = null

// 创建同步任务默认配置，供新建数据集和无任务数据集初始化
const defaultSyncTask = (): DatasetSyncTask => ({
  updateType: 'all_scope',
  syncRate: 'RIGHTNOW',
  simpleCronValue: 30,
  simpleCronType: 'minute',
  cron: '0 0/30 * * * ? *',
  fullSyncIntervalHours: 24,
  verifyEnabled: 1,
  cacheExpireHours: 26,
  taskTimeoutMinutes: 360,
  failureWarnThreshold: 1
})
// 当前数据集同步任务配置
const syncTask = reactive<DatasetSyncTask>(defaultSyncTask())
// 当前数据集同步日志列表
const syncLogs = ref<DatasetSyncLog[]>([])
// 手动同步按钮提交状态
const syncSubmitting = ref(false)
// 同步高级设置面板展开状态
const syncAdvancedVisible = ref(false)
// 用户是否正在编辑同步配置，避免后台轮询覆盖未保存的表单值
const syncConfigDirty = ref(false)
let syncPollTimer: number | null = null

// 标记同步配置被用户修改
const markSyncConfigDirty = () => {
  syncConfigDirty.value = true
}

// 应用后端同步任务状态。轮询刷新时可保留用户正在编辑的配置字段
const applyServerSyncTask = (task, options = { preserveConfig: false }) => {
  applyDatasetSyncTaskPatch(syncTask, task, {
    preserveConfig: !!options.preserveConfig && syncConfigDirty.value
  })
}

// 将字段类型编码转换为界面展示文案
const fieldTypes = index => {
  return [
    t('dataset.text'),
    t('dataset.time'),
    t('dataset.value'),
    t('dataset.value') + '(' + t('dataset.float') + ')',
    t('dataset.value'),
    t('dataset.location')
  ][index]
}

// 标记数据集配置已修改，并触发同步支持状态重算
const changeUpdate = () => {
  isUpdate = true
  syncSupportRefreshKey.value += 1
}

const fieldOptions = [
  { label: t('dataset.text'), value: 0 },
  {
    label: t('dataset.time'),
    value: 1,
    children: [
      {
        value: 'yyyy-MM-dd',
        label: 'yyyy-MM-dd'
      },
      {
        value: 'yyyy/MM/dd',
        label: 'yyyy/MM/dd'
      },
      {
        value: 'yyyy-MM-dd HH:mm:ss',
        label: 'yyyy-MM-dd HH:mm:ss'
      },
      {
        value: 'yyyy/MM/dd HH:mm:ss',
        label: 'yyyy/MM/dd HH:mm:ss'
      },
      {
        value: 'custom',
        label: t('visualization.custom')
      }
    ]
  },
  { label: t('dataset.location'), value: 5 },
  { label: t('dataset.value'), value: 2 },
  {
    label: t('dataset.value') + '(' + t('dataset.float') + ')',
    value: 3
  },
  { label: 'URL', value: 7 }
]

const fieldOptionsText = [
  { label: t('dataset.text'), value: 0 },
  {
    label: t('dataset.time'),
    value: 1
  },
  { label: t('dataset.location'), value: 5 },
  { label: t('dataset.value'), value: 2 },
  {
    label: t('dataset.value') + '(' + t('dataset.float') + ')',
    value: 3
  },
  { label: 'URL', value: 7 }
]

// 自定义时间格式表单实例
const ruleFormRef = ref<FormInstance>()
// 普通字段重命名表单实例
const ruleFormFieldRef = ref<FormInstance>()

const rules = {
  name: [{ required: true, message: t('data_set.cannot_be_empty_time'), trigger: 'blur' }]
}

const fieldRules = {
  name: [{ required: true, message: t('dataset.input_edit_name'), trigger: 'blur' }]
}

// 新建自定义 SQL 节点的临时表配置
const sqlNode = reactive<Table>({
  datasourceId: '',
  name: '',
  tableName: t('data_set.custom_sql'),
  type: 'sql'
})

let nodeInfo = {
  id: '',
  pid: '',
  name: ''
}
// 当前已保存数据集 ID，新建前为空
const currentDatasetId = ref('')

const defaultProps = {
  children: 'children',
  label: 'label'
}
// 联合画布高度，拖拽时同步影响预览区域高度
const dragHeight = ref(260)

let tableList = []

// 在数据源树中按 ID 递归查找数据源名称
const dfsName = (arr, id) => {
  let name = ''
  arr.some(ele => {
    if (ele.id === id) {
      name = ele.name
      return true
    }

    if (!!ele.children?.length) {
      name = dfsName(ele.children, id) || name
    }
    return false
  })

  return name
}

const { height } = useWindowSize()

// 裁剪数据源树，只保留目录或可选叶子节点
const dfsChild = arr => {
  return arr.filter(ele => {
    if (ele.leaf) {
      return true
    }
    if (!!ele.children?.length) {
      ele.children = dfsChild(ele.children || [])
    }
    return !!ele.children?.length
  })
}

// 根据数据源 ID 获取数据源展示名
const getDsName = (id: string) => {
  return dfsName(state.dataSourceList, id)
}

// 返回数据集列表页，并兼容嵌入式模式和浏览器历史栈
const pushDataset = () => {
  wsCache.set(`dataset-info-id`, nodeInfo.id)
  if (appStore.isCrestBi) {
    embeddedStore.clearState()
    useEmitt().emitter.emit('changeCurrentComponent', 'Dataset')
    return
  }
  const routeName = embeddedStore.getToken && appStore.getIsIframe ? 'dataset-embedded' : 'dataset'
  if (!!history.state.back && !appStore.getIsIframe) {
    history.back()
  } else {
    push({
      name: routeName,
      params: {
        id: nodeInfo.id
      }
    })
  }
}

// 返回主列表前检查未保存变更，必要时弹出确认
const backToMain = () => {
  if (isUpdate) {
    ElMessageBox.confirm(t('data_set.want_to_exit'), {
      confirmButtonText: t('dataset.confirm'),
      cancelButtonText: t('common.cancel'),
      showCancelButton: true,
      confirmButtonType: 'primary',
      type: 'warning',
      autofocus: false,
      showClose: false
    }).then(() => {
      pushDataset()
    })
  } else {
    pushDataset()
  }
}

// 取消自定义时间格式编辑，并恢复字段原始类型选择
const closeCustomTime = () => {
  if (!!currentField.value.idArr.length) {
    const { idArr } = currentField.value
    allfields.value.forEach(ele => {
      if (idArr.includes(ele.id)) {
        Object.assign(ele, { fieldTypeSelectionValue: [...oldArrValue] })
      }
    })
    delete currentField.value.name
    recoverSelection()
  } else {
    dimensions.value.concat(quota.value).some(ele => {
      if (ele.id === currentField.value.id) {
        delete currentField.value.name
        Object.assign(ele, { fieldTypeSelectionValue: [...oldArrValue] })
        return true
      }
      return false
    })
  }
  currentField.value.idArr = []
  currentField.value.id = ''
  updateCustomTime.value = false
}

// 确认自定义时间格式，支持单字段和批量字段两种入口
const confirmCustomTime = () => {
  if (!!currentField.value.idArr.length) {
    const { name, idArr } = currentField.value
    allfields.value.forEach(ele => {
      if (idArr.includes(ele.id)) {
        Object.assign(ele, {
          fieldType: 1,
          dateFormatType: 'custom',
          dateFormat: name,
          fieldTypeSelectionValue: [1, 'custom']
        })
      }
    })
    delete currentField.value.name
    recoverSelection()
    updateCustomTime.value = false
  } else {
    ruleFormRef.value.validate(valid => {
      if (valid) {
        dimensions.value.concat(quota.value).some(ele => {
          if (ele.id === currentField.value.id) {
            ele.dateFormat = currentField.value.name
            ele.fieldType = 1
            ele.dateFormatType = 'custom'
            return true
          }
          return false
        })
        updateCustomTime.value = false
      }
    })
  }
}

// 按本地已加载表列表过滤左侧数据表
watch(searchTable, val => {
  datasourceTableData.value = tableList.filter(ele =>
    ele.tableName.toLowerCase().includes(val.toLowerCase())
  )
})
// 编辑态保存数据集，并在需要时保存同步配置和返回列表
const editeSave = () => {
  if (!validateSyncSetting()) {
    return
  }
  loading.value = true
  saveCurrentDataset()
    .then(() => persistDatasetSync(nodeInfo.id))
    .then(() => {
      isUpdate = false
      ElMessage.success(t('data_set.saved_successfully'))
      if (willBack) {
        pushDataset()
      }
    })
    .finally(() => {
      loading.value = false
    })
}

// 处理字段更多菜单中的类型转换、复制、删除、编辑和重命名命令
const handleFieldMore = (ele, type) => {
  changeUpdate()
  if (tabActive.value === 'manage') {
    dimensionsSelection.value = dimensionsTable.value.getSelectionRows().map(ele => ele.id)
    quotaSelection.value = quotaTable.value.getSelectionRows().map(ele => ele.id)
  }
  const arr = ['text', 'time', 'value', 'float', 'value', 'location', 'binary', 'url']
  if (arr.includes(type as string)) {
    ele.fieldType = arr.indexOf(type)
    ele.dateFormat = ''
    return
  }
  if (timeTypes.includes(type as string)) {
    currentField.value.dateFormat = ele.dateFormat
    currentField.value.dateFormatType = ele.dateFormatType

    ele.fieldType = 1
    ele.dateFormatType = type
    ele.dateFormat = type
  }
  switch (type) {
    case 'copy':
      if (ele.extField === 3) {
        copyGroupField(ele)
      } else {
        copyField(ele)
      }
      break
    case 'delete':
      deleteField(ele)
      break
    case 'translate':
      dqTrans(ele.id)
      break
    case 'editor':
      if (ele.extField === 3) {
        initGroupField(ele)
      } else {
        editField(ele)
      }
      break
    case 'rename':
      renameField(ele)
      break
    case 'custom':
      currentField.value.id = ele.id
      updateCustomTime.value = true
      break
    default:
      break
  }

  if (tabActive.value === 'manage') {
    recoverSelection()
  }
}

// 在维度和指标之间切换字段分组类型
const dqTrans = id => {
  const obj = allfields.value.find(ele => ele.id === id)
  obj.groupType = obj.groupType === 'd' ? 'q' : 'd'
}

// 批量设置已选字段的维度或指标分组类型
const dqTransArr = groupType => {
  const idArr = fieldSelection.value.map(ele => ele.id)
  allfields.value.forEach(ele => {
    if (idArr.includes(ele.id)) {
      ele.groupType = groupType
    }
  })
  recoverSelection()
}

// 复制普通字段或计算字段，复制后作为计算字段参与后续编辑
const copyField = item => {
  const param = cloneDeep(item)
  param.id = guid()
  param.extField = 2
  param.originName = item.extField === 2 ? item.originName : '[' + item.id + ']'
  param.name = getFieldName(dimensions.value.concat(quota.value), item.name)
  param.engineFieldName = null
  param.lastSyncTime = null
  const index = allfields.value.findIndex(ele => ele.id === item.id)
  allfields.value.splice(index + 1, 0, param)
}

// 批量选择时禁止选中分组字段
const selectable = row => ![3].includes(row.extField)

// 复制分组字段，并生成新的前端临时 ID
const copyGroupField = item => {
  const param = cloneDeep(item)
  param.id = guid()
  param.name = getFieldName(dimensions.value.concat(quota.value), item.name)
  const index = allfields.value.findIndex(ele => ele.id === item.id)
  allfields.value.splice(index + 1, 0, param)
}

// 按字段 ID 删除字段，并递归删除依赖缺失的计算字段和分组字段
const delFieldById = arr => {
  const delId = [...arr]
  while (delId.length) {
    const [targetId] = delId
    delId.shift()
    allfields.value = allfields.value.filter(ele => ele.id !== targetId)
    const paramsId = allfields.value.reduce((pre, next) => {
      if (next.extField === 2) {
        pre = [...pre, ...(next.params || []).map(element => element.id)]
      }
      return pre
    }, [])
    const allfieldsId = allfields.value.map(ele => ele.id).concat(paramsId)
    allfields.value = allfields.value.filter(ele => {
      if (![2, 3].includes(ele.extField)) return true
      const idMap = ele.originName.match(/\[(.+?)\]/g) || []
      if (!idMap) return true
      const result = idMap.every(itm => {
        const id = itm.slice(1, -1)
        return allfieldsId.includes(id)
      })
      if (result) return true
      delId.push(ele.id)
      return false
    })
  }

  delGroupIds()
}

// 模拟删除字段，返回会受影响的计算字段或分组字段 ID
const delFieldByIdFake = (arr, fakeAllfields) => {
  const delId = [...arr]
  let idList = []
  while (delId.length) {
    const [targetId] = delId
    delId.shift()
    fakeAllfields = fakeAllfields.filter(ele => ele.id !== targetId)
    const allfieldsId = fakeAllfields.map(ele => ele.id)
    fakeAllfields = fakeAllfields.filter(ele => {
      if (![2, 3].includes(ele.extField)) return true
      const idMap = ele.originName.match(/\[(.+?)\]/g) || []
      if (
        !idMap ||
        idMap.every(itx => ele.params?.map(element => element.id).includes(itx.slice(1, -1)))
      )
        return true
      const result = idMap.every(itm => {
        const id = itm.slice(1, -1)
        return allfieldsId.includes(id)
      })
      if (result) return true
      delId.push(ele.id)
      idList.push(ele.id)
      return false
    })
  }

  return idList
}

// 删除字段前确认依赖影响，并同步联合树中的字段选择
const deleteField = item => {
  let tip = ''
  const idArr = allfields.value.reduce((pre, next) => {
    if (![2, 3].includes(next.extField)) return pre
    let idMap = next.originName.match(/\[(.+?)\]/g) || []
    idMap = idMap.filter(itx => !next.params?.map(element => element.id).includes(itx.slice(1, -1)))
    const result = idMap.map(itm => {
      return itm.slice(1, -1)
    })
    pre = [...result, ...pre]
    return pre
  }, [])
  tip = idArr.includes(item.id) ? t('data_set.deleted_confirm_deletion') : ''
  ElMessageBox.confirm(t('data_set.delete_field_a', { a: item.name }), {
    confirmButtonText: t('dataset.confirm'),
    tip,
    cancelButtonText: t('common.cancel'),
    showCancelButton: true,
    confirmButtonType: 'danger',
    type: 'warning',
    autofocus: false,
    showClose: false,
    callback: (action: Action) => {
      if (action === 'confirm') {
        delFieldById([item.id])
        datasetDrag.value.dfsNodeFieldBackReal(item)
        ElMessage({
          message: t('chart.delete_success'),
          type: 'success'
        })
      }
    }
  })
}

// 新增计算字段，并把当前维度和指标字段作为可引用上下文传入编辑器
const addCalcField = groupType => {
  editCalcField.value = true
  calcTitle.value = t('dataset.add_calc_field')
  nextTick(() => {
    calcEdit.value.initEdit(
      { groupType, id: guid() },
      dimensions.value.filter(ele => ele.extField !== 3),
      quota.value.filter(ele => ele.extField !== 3)
    )
  })
}

// 普通字段重命名弹窗显示状态
const editNormalField = ref(false)
// 当前正在重命名的普通字段
const currentNormalField = ref({
  id: '',
  name: ''
})

// 打开普通字段重命名弹窗
const renameField = item => {
  const { id, name } = item
  currentNormalField.value = {
    id,
    name
  }
  editNormalField.value = true
}

// 计算字段弹窗标题
const calcTitle = ref('')

// 打开计算字段编辑弹窗，并加载待编辑字段
const editField = item => {
  editCalcField.value = true
  nextTick(() => {
    calcTitle.value = t('dataset.edit_calc_field')
    calcEdit.value.initEdit(
      item,
      dimensions.value.filter(ele => ele.extField !== 3),
      quota.value.filter(ele => ele.extField !== 3)
    )
  })
}

// 关闭普通字段重命名弹窗并清空临时状态
const closeNormalField = () => {
  currentNormalField.value.id = ''
  currentNormalField.value.name = ''
  editNormalField.value = false
}

// 确认普通字段重命名，并写回全量字段列表
const confirmNormalField = () => {
  ruleFormFieldRef.value.validate(val => {
    if (val) {
      allfields.value.some(ele => {
        if (ele.id === currentNormalField.value.id) {
          ele.name = currentNormalField.value.name
          return true
        }
        return false
      })
      closeNormalField()
    }
  })
}

// 关闭计算字段编辑弹窗
const closeEditCalc = () => {
  editCalcField.value = false
}

// 确认计算字段编辑，规范化时间字段格式后写入全量字段列表
const confirmEditCalc = () => {
  calcEdit.value.formField.validate(val => {
    if (val) {
      calcEdit.value.setFieldForm()
      if (!calcEdit.value.fieldForm.originName.trim()) {
        ElMessage.error(t('data_set.expression_required'))
        return
      }
      const obj = cloneDeep(calcEdit.value.fieldForm)
      const { fieldType, dateFormat, extractedFieldType } = obj
      obj.dateFormat = fieldType === 1 ? dateFormat : ''
      obj.dateFormatType = fieldType === 1 ? dateFormat : ''
      obj.fieldTypeSelectionValue =
        fieldType === 1 && extractedFieldType === 0 ? [fieldType, dateFormat] : [fieldType]
      const result = allfields.value.findIndex(ele => obj.id === ele.id)
      if (result !== -1) {
        allfields.value.splice(result, 1, obj)
      } else {
        allfields.value.push(obj)
      }
      editCalcField.value = false
    }
  })
}

// 根据预览字段生成虚拟表格列配置
const generateColumns = (arr: Field[]) =>
  arr.map(ele => ({
    key: ele.engineFieldName,
    fieldType: ele.fieldType,
    dataKey: ele.engineFieldName,
    title: ele.name,
    width: 150,
    headerCellRenderer: ({ column }) => (
      <div class="flex-align-center">
        <ElIcon>
          <Icon>
            {h(iconFieldMap[fieldType[column.fieldType]], {
              class: `svg-icon field-icon-${fieldType[column.fieldType]}`
            })}
          </Icon>
        </ElIcon>
        <span class="ellipsis" title={column.title} style={{ width: '120px', marginLeft: '4px' }}>
          {column.title}
        </span>
      </div>
    )
  }))

// 加载指定数据源下的数据表，并同步自定义 SQL 节点的数据源 ID
const dsChange = (val: string) => {
  dsLoading.value = true
  sqlNode.datasourceId = dataSource.value
  return tables({ datasourceId: val })
    .then(res => {
      tableList = res || []
      datasourceTableData.value = [...tableList]
    })
    .finally(() => {
      dsLoading.value = false
    })
}

// 初始化指定数据源和表名，常用于从路由参数进入创建页
const getTableName = async (datasourceId, tableName) => {
  await dsChange(datasourceId)
  if (!!tableName) {
    searchTable.value = tableName
  }
}
// 当前页面是否处于编辑或复制已有数据集状态
const isEdit = ref(false)
// 外部数据源树检查组件实例
const datasetCheckRef = ref()
// 初始化编辑或复制数据集，恢复联合树、字段、同步配置和左侧数据源
const initEdite = async () => {
  let { id, datasourceId, tableName, copyId } = route.query
  const { id: legacyCopyId } = route.params
  copyId = copyId || legacyCopyId
  if (appStore.getIsCrestBi) {
    id = embeddedStore.datasetId
    datasourceId = embeddedStore.datasourceId
    tableName = embeddedStore.tableName
    copyId = embeddedStore.datasetCopyId || copyId
  }
  isEdit.value = false
  if (copyId || id) {
    const barRes = await barInfoApi(copyId || id)
    if (!barRes || !barRes['id']) {
      return
    }
    isEdit.value = true
  }
  if (datasourceId) {
    dataSource.value = datasourceId as string
    getTableName(datasourceId as string, tableName)
  }
  if (!id && !copyId) return

  loading.value = true
  try {
    const res = await datasetDetails(copyId || id)
    loading.value = false
    let arr = []
    const { pid, name } = res || {}
    nodeInfo = {
      id: res?.id || null,
      pid,
      name: copyId ? t('data_set.copy_a_dataset') : name
    }
    if (copyId) {
      nodeInfo.id = ''
    }
    currentDatasetId.value = nodeInfo.id || ''
    datasetName.value = nodeInfo.name
    allfields.value = res.allFields || []
    isCross.value = res.isCross || false
    syncMode.value = copyId ? 0 : res.mode || 0
    if (!copyId && res.id) {
      await loadDatasetSyncTask(res.id)
    } else {
      Object.assign(syncTask, defaultSyncTask())
      syncLogs.value = []
    }
    dfsUnion(arr, res.union || [])
    const [fir] = res.union as { currentDs: { datasourceId: string } }[]
    dataSource.value = fir?.currentDs?.datasourceId
    dsChange(dataSource.value)
    datasetDrag.value.initState(arr)
  } catch (error) {
    console.error(error)
    loading.value = false
  }
}

// 打开关联条件编辑器，并把当前父子节点传给关联编辑组件
const joinEditor = (arr: []) => {
  state.editArr = cloneDeep(arr)
  editUnion.value = true
  nextTick(() => {
    fieldUnion.value.initState()
  })
}

// 数据预览表格列配置
const columns = shallowRef([])
// 数据预览表格数据
const tableData = shallowRef([])
// 指标字段列表，由全量字段按 groupType 派生
const quota = computed(() => {
  return allfields.value.filter(ele => ele.groupType === 'q')
})

// 维度字段列表，由全量字段按 groupType 派生
const dimensions = computed(() => {
  return allfields.value.filter(ele => ele.groupType === 'd')
})

// 深度优先生成数据表 ID 到表名的映射
const dfsGetName = (list, name) => {
  list.forEach(ele => {
    name[ele.id] = ele.tableName
    if (ele.children?.length) {
      dfsGetName(ele.children, name)
    }
  })
}

// 切换到批量管理页签时，补齐字段类型选择值并刷新表名缓存
const tabChange = val => {
  if (val === 'preview') return
  reGetName()
  allfields.value.forEach(ele => {
    if (!Array.isArray(ele.fieldTypeSelectionValue)) {
      ele.fieldTypeSelectionValue =
        ele.fieldType === 1 && ele.extractedFieldType === 0
          ? [ele.fieldType, ele.dateFormatType]
          : [ele.fieldType]
    } else {
      const [type] = ele.fieldTypeSelectionValue
      if (ele.fieldTypeSelectionValue.length && type !== ele.fieldType) {
        ele.fieldTypeSelectionValue.splice(0, 1, ele.fieldType)
      }
    }
  })
}

// 联合树结构变化后的统一回调，刷新字段名称、取消预览请求并标记已更新
const addComplete = () => {
  state.nodeNameList = [...datasetDrag.value.nodeNameList]
  changeUpdate()
  if (!state.nodeNameList?.length) {
    columns.value = []
    tableData.value = []
  }
  cancelMap['/dataset-data/preview-data']?.()
  datasetPreviewLoading.value = false
  reGetName()
}

// 重新从联合树生成表名映射
const reGetName = () => {
  dfsGetName(datasetDrag.value.getNodeList(), nameMap.value)
}

// 数据集表单主状态，保存联合编辑、数据源树和字段折叠状态
const state = reactive({
  nodeNameList: [],
  editArr: [],
  dataSourceList: [],
  fieldCollapse: ['dimension', 'quota']
})

// 左侧当前数据源的数据表列表
const datasourceTableData = shallowRef([])
// 根据字段类型编码选择字段图标类型
const getIconName = (type: number) => {
  if (type === 1) {
    return 'time'
  }

  if (type === 0) {
    return 'text'
  }

  if ([2, 3, 4].includes(type)) {
    return 'value'
  }
  if (type === 5) {
    return 'location'
  }
  if (type === 7) {
    return 'url'
  }
}

// 当前数据集的全量字段列表，包含物理字段、计算字段和分组字段
const allfields = ref([])

provide('allfields', allfields)
provide('isCross', isCross)

// 在数据源树中查找指定 ID 的数据源节点
const findDatasource = (list, id) => {
  for (const item of list || []) {
    if (`${item.id}` === `${id}`) {
      return item
    }
    const child = findDatasource(item.children, id)
    if (child) {
      return child
    }
  }
}

// 判断 SQL 参数配置是否非空，兼容字符串、数组和对象三种来源
const hasSqlVariableDetails = value => {
  if (!value) {
    return false
  }
  if (Array.isArray(value)) {
    return value.length > 0
  }
  if (typeof value === 'string') {
    const text = value.trim()
    if (!text || text === '[]') {
      return false
    }
    try {
      const parsed = JSON.parse(text)
      if (Array.isArray(parsed)) {
        return parsed.length > 0
      }
      return !!parsed && Object.keys(parsed).length > 0
    } catch {
      return true
    }
  }
  if (typeof value === 'object') {
    return Object.keys(value).length > 0
  }
  return true
}

// 判断联合树节点或其子节点是否包含 SQL 参数
const nodeHasSqlVariables = node => {
  if (!node) {
    return false
  }
  if (
    hasSqlVariableDetails(node.sqlVariableDetails) ||
    hasSqlVariableDetails(node.currentDs?.sqlVariableDetails)
  ) {
    return true
  }
  let children = []
  if (Array.isArray(node.children)) {
    children = node.children
  } else if (Array.isArray(node.childrenDs)) {
    children = node.childrenDs
  }
  return children.some(child => nodeHasSqlVariables(child))
}

// 当前左侧选中的数据源节点
const currentDatasource = computed(() => findDatasource(state.dataSourceList, dataSource.value))
const syncSupportedDatasourceTypes = ['obOracle']
// 当前数据源类型是否具备缓存同步基础能力
const syncBaseSupportedDataset = computed(
  () => !!dataSource.value && syncSupportedDatasourceTypes.includes(currentDatasource.value?.type)
)
// 当前联合树中是否存在带 SQL 参数的自定义 SQL 节点
const hasDatasetSqlVariables = computed(() => {
  const refreshVersion = syncSupportRefreshKey.value
  const nodes = datasetDrag.value?.getNodeList?.() || []
  return refreshVersion >= 0 && nodes.some(node => nodeHasSqlVariables(node))
})
// 当前数据集不能使用缓存同步的具体原因
const syncUnsupportedReason = computed(() => {
  if (!syncBaseSupportedDataset.value) {
    return ''
  }
  if (isCross.value) {
    return '跨源数据集暂不支持缓存'
  }
  if (hasDatasetSqlVariables.value) {
    return '带 SQL 参数的数据集暂不支持缓存'
  }
  return ''
})
// 是否显示缓存同步配置区域
const showSyncSetting = computed(() => syncBaseSupportedDataset.value)
// 当前数据集是否真正支持开启缓存模式
const syncSupportedDataset = computed(
  () => syncBaseSupportedDataset.value && !syncUnsupportedReason.value
)
// 最终保存到后端的数据集读取模式
const datasetMode = computed(() => (syncSupportedDataset.value && syncMode.value === 1 ? 1 : 0))
// 可作为增量字段的普通选中字段
const syncFieldOptions = computed(() => allfields.value.filter(isDatasetSyncField))
// 可作为增量水位的字段
const incrementalFieldOptions = computed(() =>
  syncFieldOptions.value.filter(isDatasetIncrementalField)
)
// 时间类型增量候选字段
const timeIncrementalFields = computed(() =>
  incrementalFieldOptions.value.filter(
    field => Number(field.extractedFieldType ?? field.fieldType) === 1
  )
)
// 数值类型增量候选字段
const numberIncrementalFields = computed(() =>
  incrementalFieldOptions.value.filter(field =>
    [2, 3].includes(Number(field.extractedFieldType ?? field.fieldType))
  )
)
// 自动推荐增量字段，优先选择更新时间类字段，再回退到 ID 类数值字段
const autoIncrementalField = computed(() => {
  const keyword = ['更新', '修改', '时间', 'update', 'modify', 'time', 'date']
  return (
    timeIncrementalFields.value.find(field => {
      const name = `${field.name || ''} ${field.originName || ''}`.toLowerCase()
      return keyword.some(item => name.includes(item))
    }) ||
    timeIncrementalFields.value[0] ||
    numberIncrementalFields.value.find(field => {
      const name = `${field.name || ''} ${field.originName || ''}`.toLowerCase()
      return name.includes('id') || name.includes('主键')
    }) ||
    numberIncrementalFields.value[0]
  )
})
// 当前同步任务选择的增量字段
const currentIncrementalField = computed(() =>
  incrementalFieldOptions.value.find(field => `${field.id}` === `${syncTask.incrementalFieldId}`)
)
// 当前表单是否已经拥有可用于同步的后端数据集 ID
const hasCurrentDatasetId = computed(() => !!currentDatasetId.value)
// 增量同步不可用的原因
const incrementalDisabledReason = computed(() =>
  getIncrementalDisabledReason(incrementalFieldOptions.value)
)
// 同步方式摘要文案
const syncMethodText = computed(() => {
  if (syncTask.updateType === 'add_scope') {
    return currentIncrementalField.value?.name
      ? `增量：${currentIncrementalField.value.name}`
      : '增量'
  }
  return '全量'
})
// 同步任务是否正在执行
const syncRunning = computed(() => syncTask.taskStatus === 'UnderExecution')
// 同步状态展示文案
const syncStatusText = computed(() =>
  getDatasetSyncStatusText({
    ...toRaw(syncTask),
    syncMode: datasetMode.value,
    hasDatasetId: hasCurrentDatasetId.value
  })
)
// 立即同步按钮禁用原因
const syncNowDisabledReason = computed(() =>
  getDatasetSyncNowDisabledReason({
    hasDatasetId: hasCurrentDatasetId.value,
    syncRunning: syncRunning.value
  })
)
// 立即同步按钮是否禁用
const syncNowDisabled = computed(() => !!syncNowDisabledReason.value)
// 最新一条同步日志
const latestSyncLog = computed(() => syncLogs.value[0])
// 最新同步日志中的标准化详情
const latestSyncInfo = computed(() =>
  normalizeDatasetSyncInfo(latestSyncLog.value?.info || syncTask.lastVerifyMessage || '')
)
// 最新同步日志中的行数摘要
const latestSyncRowCountText = computed(() => formatDatasetSyncRowCount(latestSyncLog.value))
// 同步详情入口或最终状态文案
const syncDetailText = computed(() => {
  const info = String(latestSyncInfo.value || '')
  if (!info) return ''
  if (syncTask.lastVerifyStatus === 'WARNING') return '查看校验详情'
  if (syncTask.lastExecStatus === 'Error' || /^error\b/i.test(info)) {
    return '查看错误详情'
  }
  if (info === '同步完成') return '更新完成'
  return info
})

// 根据同步方式补齐或清空增量字段默认值
const applyIncrementalDefaults = () => {
  if (syncTask.updateType !== 'add_scope') {
    syncTask.incrementalFieldId = null
    return
  }
  if (!currentIncrementalField.value) {
    syncTask.incrementalFieldId = autoIncrementalField.value?.id || null
  }
}

// 切换直连和缓存模式时校验支持状态，并自动推荐增量字段
const syncModeChange = () => {
  markSyncConfigDirty()
  if (syncMode.value !== 1) {
    return
  }
  if (syncUnsupportedReason.value) {
    syncMode.value = 0
    ElMessage.warning(syncUnsupportedReason.value)
    return
  }
  if (syncTask.updateType === 'all_scope' && autoIncrementalField.value) {
    syncTask.updateType = 'add_scope'
  }
  applyIncrementalDefaults()
}

// 切换全量或增量更新方式时校验字段条件
const syncUpdateTypeChange = () => {
  markSyncConfigDirty()
  if (syncTask.updateType === 'add_scope' && incrementalDisabledReason.value) {
    syncTask.updateType = 'all_scope'
    ElMessage.warning(incrementalDisabledReason.value)
    return
  }
  applyIncrementalDefaults()
}

// 增量字段候选变化或缓存模式变化时，自动刷新增量默认字段
watch([autoIncrementalField, () => syncMode.value], () => {
  if (datasetMode.value === 1) {
    applyIncrementalDefaults()
  }
})

// 缓存模式不再支持时自动回退到直连
watch(syncUnsupportedReason, reason => {
  if (reason && syncMode.value === 1) {
    syncMode.value = 0
  }
})

// 根据简单定时配置生成后端 cron 表达式
const refreshSimpleCron = () => {
  markSyncConfigDirty()
  const value = Number(syncTask.simpleCronValue || 30)
  if (syncTask.simpleCronType === 'hour') {
    syncTask.simpleCronValue = Math.min(Math.max(value, 1), 23)
    syncTask.cron = `0 0 0/${syncTask.simpleCronValue} * * ? *`
    return
  }
  if (syncTask.simpleCronType === 'day') {
    syncTask.simpleCronValue = Math.min(Math.max(value, 1), 31)
    syncTask.cron = `0 0 0 1/${syncTask.simpleCronValue} * ? *`
    return
  }
  syncTask.simpleCronValue = Math.min(Math.max(value, 1), 59)
  syncTask.cron = `0 0/${syncTask.simpleCronValue} * * * ? *`
}

// 切换同步频率时刷新 cron 或清空手动同步 cron
const syncRateChange = () => {
  markSyncConfigDirty()
  if (syncTask.syncRate === 'SIMPLE_CRON') {
    refreshSimpleCron()
  }
  if (syncTask.syncRate === 'RIGHTNOW') {
    syncTask.cron = ''
  }
}

// 保存或执行同步前校验同步配置，并修正不再支持的模式
const validateSyncSetting = () => {
  if (syncMode.value === 1 && syncUnsupportedReason.value) {
    syncMode.value = 0
    ElMessage.warning(syncUnsupportedReason.value)
  }
  if (datasetMode.value !== 1) {
    return true
  }
  if (syncTask.updateType === 'add_scope' && incrementalDisabledReason.value) {
    ElMessage.warning(incrementalDisabledReason.value)
    return false
  }
  applyIncrementalDefaults()
  if (syncTask.updateType === 'add_scope' && !syncTask.incrementalFieldId) {
    ElMessage.warning('请选择增量字段')
    return false
  }
  return true
}

// 加载数据集同步任务和日志，并根据运行状态启动轮询
const loadDatasetSyncTask = async id => {
  Object.assign(syncTask, defaultSyncTask())
  const task = await datasetSyncTask(id)
  if (task) {
    applyServerSyncTask(task)
  }
  syncConfigDirty.value = false
  applyIncrementalDefaults()
  syncLogs.value = await datasetSyncLogs(id)
  if (syncRunning.value) {
    startSyncPolling()
  } else {
    stopSyncPolling()
  }
}

// 刷新同步任务和日志状态，任务结束后停止轮询
const refreshDatasetSyncState = async () => {
  if (!currentDatasetId.value) {
    return
  }
  const task = await datasetSyncTask(currentDatasetId.value)
  if (task) {
    applyServerSyncTask(task, { preserveConfig: true })
  }
  if (!syncConfigDirty.value) {
    applyIncrementalDefaults()
  }
  syncLogs.value = await datasetSyncLogs(currentDatasetId.value)
  if (!syncRunning.value) {
    stopSyncPolling()
  }
}

// 启动同步任务轮询，避免重复创建定时器
const startSyncPolling = () => {
  if (syncPollTimer) {
    return
  }
  syncPollTimer = window.setInterval(() => {
    refreshDatasetSyncState().catch(() => stopSyncPolling())
  }, 3000)
}

// 停止同步任务轮询并清空定时器引用
const stopSyncPolling = () => {
  if (!syncPollTimer) {
    return
  }
  window.clearInterval(syncPollTimer)
  syncPollTimer = null
}

// 保存数据集同步配置，直连模式下会停止已有同步任务
const persistDatasetSync = async id => {
  if (!id) {
    return
  }
  if (datasetMode.value !== 1) {
    await stopDatasetSync(id)
    stopSyncPolling()
    syncConfigDirty.value = false
    return
  }
  if (!validateSyncSetting()) {
    throw new Error('invalid sync setting')
  }
  applyIncrementalDefaults()
  if (syncTask.syncRate === 'SIMPLE_CRON') {
    refreshSimpleCron()
  }
  const savedTask = await saveDatasetSyncTask(
    buildDatasetSyncTaskPayload({
      syncTask: toRaw(syncTask),
      datasetGroupId: id,
      datasetName: datasetName.value
    })
  )
  if (savedTask) {
    applyServerSyncTask(savedTask)
  }
  syncConfigDirty.value = false
}

// 保存当前数据集定义，并回写后端返回的 ID、名称和字段列表
const saveCurrentDataset = async () => {
  const union = []
  dfsNodeList(union, datasetDrag.value.getNodeList())
  const saved = await saveDatasetTree({
    ...nodeInfo,
    name: datasetName.value,
    union,
    isCross: isCross.value,
    mode: datasetMode.value,
    allFields: allfields.value,
    nodeType: 'dataset'
  })
  if (saved) {
    nodeInfo = {
      id: saved.id || nodeInfo.id,
      pid: saved.pid || nodeInfo.pid,
      name: saved.name || datasetName.value
    }
    currentDatasetId.value = nodeInfo.id || currentDatasetId.value
    datasetName.value = nodeInfo.name
    allfields.value = saved.allFields || allfields.value
  }
  return saved
}

// 立即执行一次数据集同步，必要时先保存数据集和同步配置
const executeDatasetSyncNow = async () => {
  if (!validateSyncSetting()) {
    return
  }
  syncSubmitting.value = true
  try {
    await executeDatasetSyncNowFlow({
      currentDatasetId: currentDatasetId.value,
      syncRunning: syncRunning.value,
      warn: ElMessage.warning,
      saveCurrentDataset,
      persistDatasetSync,
      executeDatasetSync,
      loadDatasetSyncLogs: datasetSyncLogs,
      applyTask: task => applyServerSyncTask(task),
      applyLogs: logs => {
        syncLogs.value = logs
      },
      startSyncPolling,
      success: ElMessage.success
    })
    isUpdate = false
  } finally {
    syncSubmitting.value = false
  }
}

// 维度区域是否展开
const expandedD = ref(true)
// 指标区域是否展开
const expandedQ = ref(true)
// 为联合节点新增字段补齐 ID、数据表 ID 和数据源 ID
const setGuid = (arr, id, datasourceId, oldArr) => {
  arr.forEach(ele => {
    if (!ele.id) {
      ele.id = oldArr.find(itx => itx.originName === ele.originName)?.id || `${++num.value}`
      ele.datasetTableId = id
      ele.datasourceId = datasourceId
    }
  })
}

// 从联合树中收集所有节点的当前选中字段
const dfsFields = (arr, list) => {
  list.forEach(ele => {
    if (ele.children?.length) {
      dfsFields(arr, ele.children)
    }
    const { currentDsFields } = ele
    arr.push(...cloneDeep(currentDsFields))
  })
}

// 计算新字段集合中已删除的旧字段和扩展字段
const getDelIdArr = (newArr, oldArr) => {
  const idMapNew = newArr.map(ele => ele.id)
  return [
    ...oldArr
      .filter(ele => ![2, 3].includes(ele.extField))
      .filter(ele => !idMapNew.includes(ele.id)),
    ...oldArr.filter(ele => [2, 3].includes(ele.extField))
  ]
}

// 合并新旧字段列表，保留计算字段和分组字段，再追加新增普通字段
const diffArr = (newArr, oldArr) => {
  const idMapNew = newArr.map(ele => ele.id)
  const idMapOld = oldArr.map(ele => ele.id)
  const arr = newArr.filter(ele => !idMapOld.includes(ele.id))
  return cloneDeep([
    ...oldArr.filter(ele => [2, 3].includes(ele.extField)),
    ...arr,
    ...oldArr.filter(ele => idMapNew.includes(ele.id))
  ])
}

// 关闭关联编辑器并撤销未确认的联合画布占位状态
const closeEditUnion = () => {
  notConfirmEditUnion()
  fieldUnion.value.clearState()
  editUnion.value = false
}
// 关联条件编辑组件实例
const fieldUnion = ref()

// 根据联合树字段选择重建全量字段列表，并清理失效分组字段
const setFieldAll = () => {
  const arr = []
  dfsFields(arr, datasetDrag.value.getNodeList())
  const delIdArr = getDelIdArr(arr, allfields.value)
  allfields.value = diffArr(arr, allfields.value)
  delFieldById(delIdArr)
  tabChange('manage')
  fieldUnion.value?.clearState()
}

// 清理原始字段不存在后失效的分组字段
const delGroupIds = () => {
  const delIds = []
  const fields = allfields.value.map(ele => ele.id)
  const groupIds = allfields.value.filter(ele => ele.extField === 3)
  groupIds.forEach(ele => {
    if (!fields.includes(ele.originName)) {
      delIds.push(ele.id)
    }
  })
  allfields.value = allfields.value.filter(ele => !delIds.includes(ele.id))
}

// 在联合树中查找指定节点的当前字段列表
const dfsNode = (arr, id) => {
  return arr.reduce((pre, next) => {
    if (next.id === id) {
      pre = [...next.currentDsFields]
    } else if (next.children?.length) {
      pre = dfsNode(next.children, id)
    }
    return pre
  }, [])
}

// 收集除指定节点外的其他节点字段，用于关联编辑时计算删除影响
const dfsFieldsTips = (arr, list, idArr) => {
  list.forEach(ele => {
    if (ele.children?.length) {
      dfsFieldsTips(arr, ele.children, idArr)
    }
    if (!idArr.includes(ele.id)) {
      const { currentDsFields } = ele
      arr.push(...cloneDeep(currentDsFields))
    }
  })
}
// 确认关联条件编辑，并在删除字段会影响计算字段时弹出确认
const confirmEditUnion = () => {
  const { node, parent } = fieldUnion.value
  const to = node.id
  const from = parent.id
  let unionFieldsLost = node.unionFields.some(ele => {
    const { currentField, parentField } = ele
    return !currentField || !parentField
  })

  if (unionFieldsLost) {
    ElMessage.error(t('data_set.related_field_required'))
    return
  }

  const nodeOldCurrentDsFields = dfsNode(datasetDrag.value.getNodeList(), to)
  const parentOldCurrentDsFields = dfsNode(datasetDrag.value.getNodeList(), from)

  setGuid(node.currentDsFields, node.id, node.datasourceId, nodeOldCurrentDsFields)
  setGuid(parent.currentDsFields, parent.id, parent.datasourceId, parentOldCurrentDsFields)
  const top = cloneDeep(node)
  const bottom = cloneDeep(parent)
  let arr = []
  dfsFieldsTips(arr, datasetDrag.value.getNodeList(), [node.id, parent.id])
  arr = [...arr, ...node.currentDsFields, ...parent.currentDsFields]
  const delIdArr = getDelIdArr(arr, allfields.value)
  let fakeAllfields = diffArr(arr, allfields.value)
  const idList = delFieldByIdFake(delIdArr, fakeAllfields)
  if (!!idList.length) {
    const idArr = allfields.value.reduce((pre, next) => {
      if (idList.includes(next.id)) {
        const idMap = next.originName.match(/\[(.+?)\]/g) || []
        const result = idMap.map(itm => {
          return itm.slice(1, -1)
        })
        pre = [...result, ...pre]
      }
      return pre
    }, [])

    ElMessageBox.confirm(t('data_set.field_selection'), {
      confirmButtonText: t('dataset.confirm'),
      cancelButtonText: t('common.cancel'),
      showCancelButton: true,
      tip: `${t('data_set.field')}: ${allfields.value
        .filter(ele => [...new Set(idArr)].includes(ele.id) && ![2, 3].includes(ele.extField))
        .map(ele => ele.name)
        .join(',')}, ${t('data_set.confirm_the_deletion')}`,
      confirmButtonType: 'danger',
      type: 'warning',
      autofocus: false,
      showClose: false,
      callback: (action: Action) => {
        if (action === 'confirm') {
          datasetDrag.value.setStateBack(top, bottom)
          setFieldAll()
          editUnion.value = false
          addComplete()
          datasetDrag.value.setChangeStatus(to, from)
        }
      }
    })
    return
  }

  datasetDrag.value.setStateBack(top, bottom)
  setFieldAll()
  editUnion.value = false
  addComplete()
  datasetDrag.value.setChangeStatus(to, from)
}

// 联合树子组件通知字段变更时，重新生成全量字段
const updateAllfields = () => {
  setFieldAll()
}

const equalMin = [
  {
    value: 'lt',
    label: '<'
  },
  {
    value: 'le',
    label: '<='
  }
]

// 分组字段校验器，确保每个分组项都有取值条件
const validatePass = (_: any, value: any, callback: any) => {
  if (!value || !value.length) {
    callback(new Error(t('chart.value_can_not_empty')))
  } else {
    callback()
  }
}
// 分组字段子表单实例列表，用于逐个校验分组规则
const refsForm = ref([])

const fieldGroupRules = {
  name: [{ required: true, message: t('dataset.input_edit_name'), trigger: 'blur' }]
}

const defaultObj = {
  name: '',
  id: +new Date(),
  datasourceId: '',
  datasetTableId: '',
  datasetGroupId: '',
  originName: '',
  otherGroup: '',
  groupType: 'd',
  fieldType: 0,
  type: 'ANY',
  extractedFieldType: 0,
  extField: 3,
  originalFieldType: null,
  groupList: [
    {
      name: '',
      text: [],
      time: '',
      minTerm: 'lt',
      maxTerm: 'lt',
      min: null,
      max: null
    }
  ]
}
// 当前分组字段编辑表单数据
const currentGroupField = reactive(cloneDeep(defaultObj))
// 分组字段主表单实例
const ruleGroupFieldRef = ref()
// 分组字段编辑弹窗显示状态
const editGroupField = ref(false)
// 枚举值加载状态
const enumValueLoading = ref(false)
// 可作为分组来源的字段列表
const groupFields = shallowRef([])
// 文本分组字段的枚举值候选
const enumValue = shallowRef([])
// 新建分组字段，并初始化默认分组规则
const addGroupField = () => {
  groupFields.value = allfields.value.filter(ele => ![2, 3].includes(ele.extField))
  Object.assign(currentGroupField, cloneDeep(defaultObj))
  currentGroupField.id = guid()
  titleForGroup.value = t('dataset.create_grouping_field')
  editGroupField.value = true
}
// 切换分组来源字段时重置分组规则，并按文本字段加载枚举值
const handleFieldschange = val => {
  const field = groupFields.value.find(ele => ele.id === val)
  const { fieldType } = field
  if (fieldType !== currentGroupField.extractedFieldType || fieldType === 0) {
    refsForm.value = []
    currentGroupField.groupList = [
      {
        name: '',
        text: [],
        time: '',
        minTerm: 'lt',
        maxTerm: 'lt',
        min: null,
        max: null
      }
    ]
  }
  currentGroupField.originalFieldType = fieldType
  if (fieldType !== 0) return
  enumValueLoading.value = true
  const arr = []
  const allfieldsCopy = cloneDeep(unref(allfields))
  dfsNodeList(arr, datasetDrag.value.getNodeList())
  enumValueDs({ dataset: { union: arr, allFields: allfieldsCopy, isCross: isCross.value }, field })
    .then(res => {
      enumValue.value = res || []
    })
    .finally(() => {
      enumValueLoading.value = false
    })
}
// 关闭分组字段编辑弹窗
const closeGroupField = () => {
  editGroupField.value = false
}

// 已被当前分组规则占用的枚举值
const disabledEnumArr = computed(() => {
  return currentGroupField.groupList?.map(ele => ele.text).flat()
})

// 判断枚举值是否已被其他分组占用
const disabledEnum = (item, arr) => {
  return disabledEnumArr.value.includes(item) && !arr.includes(item)
}

// 分组字段弹窗标题
const titleForGroup = ref(t('dataset.create_grouping_field'))

// 打开分组字段编辑弹窗，并把后端结构转换为表单结构
const initGroupField = val => {
  groupFields.value = allfields.value.filter(ele => ![2, 3].includes(ele.extField))
  Object.assign(currentGroupField, val)
  const groupList = []
  val.groupList.forEach(ele => {
    const { name, text = [], startTime, endTime, min, max, minTerm, maxTerm } = ele
    const obj = {
      name,
      text,
      min,
      max,
      minTerm,
      maxTerm,
      time: []
    }
    if (startTime && endTime) {
      obj.time = [startTime, endTime]
    }
    groupList.push(obj)
  })

  handleFieldschange(currentGroupField.originName)

  currentGroupField.groupList = groupList
  titleForGroup.value = t('dataset.editing_grouping_field')
  editGroupField.value = true
}

// 确认分组字段编辑，校验所有分组规则后写回全量字段列表
const confirmGroupField = () => {
  ruleGroupFieldRef.value.validate(val => {
    let count = 0
    let time
    refsForm.value.forEach(ele => {
      ele?.validate(val => {
        if (val) {
          count++
        }
      })
    })
    time = setTimeout(() => {
      clearTimeout(time)
      time = null
      if (val && count === currentGroupField.groupList.length) {
        const groupList = []
        currentGroupField.groupList.forEach(ele => {
          const { name, text = [], time, min, max, minTerm, maxTerm } = ele
          const obj = {
            name,
            text,
            min,
            max,
            minTerm,
            maxTerm,
            startTime: '',
            endTime: ''
          }
          if (currentGroupField.originalFieldType === 1) {
            const [startTime, endTime] = time
            obj.startTime = dayjs(startTime).format('YYYY-MM-DD HH:mm:ss')
            obj.endTime = dayjs(endTime).format('YYYY-MM-DD HH:mm:ss')
          }
          groupList.push(obj)
        })
        const index = allfields.value.findIndex(ele => ele.id === currentGroupField.id)
        if (index !== -1) {
          allfields.value.splice(index, 1, { ...currentGroupField, groupList })
        } else {
          allfields.value.push({ ...currentGroupField, groupList })
        }
        editGroupField.value = false
      }
    }, 1000)
  })
}

// 撤销关联编辑器中的未确认占位节点
const notConfirmEditUnion = () => {
  datasetDrag.value.notConfirm()
}

// 新增一个分组字段规则行
const addGroupFields = () => {
  currentGroupField.groupList.push({
    name: '',
    text: [],
    time: '',
    minTerm: 'lt',
    maxTerm: 'lt',
    min: null,
    max: null
  })
}

// 删除一个分组字段规则行，并同步移除对应表单引用
const removeGroupFields = index => {
  currentGroupField.groupList.splice(index, 1)
  refsForm.value.splice(index, 1)
}

// 开始从左侧拖拽数据表或自定义 SQL 节点
const dragstart = (e: DragEvent, ele) => {
  offsetX.value = e.offsetX
  offsetY.value = e.offsetY
  e.dataTransfer.setData('text/plain', JSON.stringify(ele))
  maskShow.value = true
}
// 设置左侧表列表的当前激活表
const setActiveName = (data: Table) => {
  if (data.unableCheck) return
  activeName.value = data.tableName
}

// 校验计算字段表达式能否生成预览 SQL
const verify = () => {
  if (datasetPreviewLoading.value) return
  calcEdit.value.formField.validate(val => {
    if (val) {
      calcEdit.value.setFieldForm()
      if (!calcEdit.value.fieldForm.originName.trim()) {
        ElMessage.error(t('data_set.expression_required'))
        return
      }
      const obj = cloneDeep(calcEdit.value.fieldForm)
      const { fieldType, dateFormat, extractedFieldType } = obj
      obj.dateFormat = fieldType === 1 ? dateFormat : ''
      obj.dateFormatType = fieldType === 1 ? dateFormat : ''
      obj.fieldTypeSelectionValue =
        fieldType === 1 && extractedFieldType === 0 ? [fieldType, dateFormat] : [fieldType]
      const result = allfields.value.findIndex(ele => obj.id === ele.id)
      const allfieldsCopy = cloneDeep(unref(allfields))
      if (result !== -1) {
        allfieldsCopy.splice(result, 1, obj)
      } else {
        allfieldsCopy.push(obj)
      }
      const arr = []
      dfsNodeList(arr, datasetDrag.value.getNodeList())
      datasetPreviewLoading.value = true
      getPreviewData({ union: arr, allFields: allfieldsCopy, isCross: isCross.value })
        .then(() => {
          ElMessage.success(t('data_set.validation_succeeded'))
        })
        .finally(() => {
          datasetPreviewLoading.value = false
        })
    }
  })
}

// 左侧面板是否正在横向拖拽
const isDragging = ref(false)

// 开始拖拽左侧面板宽度，并临时禁用页面文本选择
const mousedownDrag = () => {
  isDragging.value = true
  document.querySelector('body').style.userSelect = 'none'
  document.querySelector('.dataset-db').addEventListener('mousemove', calculateWidth)
}
// 结束横向或纵向拖拽，清理鼠标移动监听
const mouseupDrag = () => {
  isDragging.value = false
  document.querySelector('body').style.userSelect = 'auto'
  const dom = document.querySelector('.dataset-db')
  dom.removeEventListener('mousemove', calculateWidth)
  dom.removeEventListener('mousemove', calculateHeight)
}

// 联合树中是否存在跨数据源关联
const crossDatasources = computed(() => {
  return datasetDrag.value?.crossDatasources
})
// 根据鼠标横坐标调整左侧面板宽度
const calculateWidth = (e: MouseEvent) => {
  if (e.pageX < 240) {
    LeftWidth.value = 240
    return
  }
  if (e.pageX > 500) {
    LeftWidth.value = 500
    return
  }
  LeftWidth.value = e.pageX
}

// 开始拖拽联合画布高度
const mousedownDragH = () => {
  document.querySelector('.dataset-db').addEventListener('mousemove', calculateHeight)
}
// 根据鼠标纵坐标调整联合画布和 SQL 结果区域高度
const calculateHeight = (e: MouseEvent) => {
  const clientHeight = document.documentElement.clientHeight
  if (e.pageY - 56 < 64) {
    dragHeight.value = 64
    sqlResultHeight.value = clientHeight - dragHeight.value - 56
    return
  }
  if (e.pageY > clientHeight - 57) {
    dragHeight.value = clientHeight - 113
    sqlResultHeight.value = clientHeight - dragHeight.value - 56
    return
  }
  dragHeight.value = e.pageY - 56
  sqlResultHeight.value = clientHeight - dragHeight.value - 56
  quotaTableHeight.value = sqlResultHeight.value - 242
}

// SQL 结果区域高度，窗口变化和拖拽时共同维护
const sqlResultHeight = ref(0)
const handleResize = debounce(() => {
  const clientHeight = document.documentElement.clientHeight
  if (clientHeight - sqlResultHeight.value - 56 < 64) {
    dragHeight.value = 64
    sqlResultHeight.value = clientHeight - dragHeight.value - 56
    return
  }
  dragHeight.value = clientHeight - sqlResultHeight.value - 56
}, 60)
let willBack = false
// 保存完成且存在待处理同步配置后，再返回数据集列表
const saveAndBack = async () => {
  if (!willBack) return
  if (pendingSyncSave) {
    await pendingSyncSave
    pendingSyncSave = null
  }
  pushDataset()
}

onMounted(async () => {
  isEdit.value = false
  await nextTick()
  await initEdite()
  datasource(isEdit.value ? 0 : 2)
  window.addEventListener('resize', handleResize)
  await nextTick()
  getSqlResultHeight()
  quotaTableHeight.value = sqlResultHeight.value - 242
})

onBeforeUnmount(() => {
  stopSyncPolling()
  window.removeEventListener('resize', handleResize)
})
// 读取 SQL 结果区域实际高度，用于初始化拖拽布局
const getSqlResultHeight = () => {
  const sqlResult = document.querySelector('.sql-result') as HTMLElement | null
  sqlResultHeight.value = sqlResult?.offsetHeight || 0
}
// 加载数据源树，并触发外部数据源检查组件刷新
const datasource = (weight?: number) => {
  datasourceList(weight).then(res => {
    const _list = (res as unknown as DataSource[]) || []
    if (_list && _list.length > 0 && _list[0].id === '0' && _list[0].children?.length) {
      state.dataSourceList = dfsChild(_list[0].children)
    } else {
      state.dataSourceList = dfsChild(_list)
    }
    nextTick(() => {
      const param = {
        methodName: 'execute',
        args: null
      }
      datasetCheckRef.value?.invokeMethod(param)
    })
  })
}

// 复制或新建数据集时，递归重置联合树中的数据表 ID
const resetDfsFields = (arr, idMap) => {
  for (let i in arr) {
    const id = guid()
    idMap[arr[i].currentDs.id] = id
    arr[i].currentDs.id = id
    if (!!arr[i].childrenDs?.length) {
      resetDfsFields(arr[i].childrenDs, idMap)
    }
  }
}

// 复制数据集时重置字段 ID，并记录旧 ID 到新 ID 的映射
const resetAllfieldsId = arr => {
  const idMap = {}
  for (let i in allfields.value) {
    const id = guid()
    idMap[allfields.value[i].id] = id
    allfields.value[i].id = id
    allfields.value[i].datasetGroupId = ''
  }
  resetDfsFields(arr, idMap)
  return idMap
}

// 用新的字段和节点 ID 重写联合树、画布状态和字段列表
const resetAllfieldsUnionId = (arr, idMap) => {
  let strUnion = JSON.stringify(arr) as string
  let strNodeList = JSON.stringify(toRaw(datasetDrag.value.getNodeList())) as string
  let strAllfields = JSON.stringify(unref(allfields.value)) as string
  Object.entries(idMap).forEach(([key, value]) => {
    strUnion = strUnion.replaceAll(key, value as string)
    strAllfields = strAllfields.replaceAll(key, value as string)
    strNodeList = strNodeList.replaceAll(key, value as string)
  })
  allfields.value = JSON.parse(strAllfields)
  datasetDrag.value.initState(JSON.parse(strNodeList))
  return JSON.parse(strUnion)
}

// 保存数据集；新建时先打开目录选择，编辑时直接保存
const datasetSave = () => {
  if (!validateSyncSetting()) {
    return
  }
  if (nodeInfo.id) {
    editeSave()
    return
  }
  let union = []
  dfsNodeList(union, datasetDrag.value.getNodeList())
  const pid = appStore.getIsCrestBi ? embeddedStore.datasetPid : route.query.pid || nodeInfo.pid
  if (!union.length) {
    ElMessage.error(t('data_set.dataset_cannot_be'))
    return
  }
  if (nodeInfo.pid && !nodeInfo.id) {
    union = resetAllfieldsUnionId(union, resetAllfieldsId(union))
  }

  creatDsFolder.value.createInit(
    'dataset',
    {
      id: pid || '0',
      union,
      allfields: allfields.value,
      isCross: isCross.value,
      mode: datasetMode.value
    },
    '',
    datasetName.value
  )
}
// 保存数据集并在保存完成后返回列表页
const datasetSaveAndBack = () => {
  willBack = true
  datasetSave()
}

// 数据集预览加载状态
const datasetPreviewLoading = ref(false)

// 按当前联合树和字段配置请求数据预览
const datasetPreview = () => {
  if (datasetPreviewLoading.value) return
  const arr = []
  dfsNodeList(arr, datasetDrag.value.getNodeList())
  datasetPreviewLoading.value = true
  getPreviewData({ union: arr, allFields: allfields.value, isCross: isCross.value })
    .then(res => {
      columns.value = generateColumns((res.data.fields as Field[]) || [])
      tableData.value = (res.data.data as Array<{}>) || []
    })
    .finally(() => {
      datasetPreviewLoading.value = false
    })
}

// 将前端联合树转换为后端保存接口需要的 union 结构
const dfsNodeList = (arr, list) => {
  list.forEach(ele => {
    const childrenDs = []
    if (ele.children?.length) {
      dfsNodeList(childrenDs, ele.children)
    }
    const {
      tableName,
      type,
      datasourceId,
      id,
      info,
      unionType,
      unionFields,
      currentDsFields,
      sqlVariableDetails
    } = ele
    arr.push({
      currentDs: {
        sqlVariableDetails,
        tableName,
        type,
        datasourceId,
        id,
        info
      },
      currentDsFields,
      childrenDs,
      unionToParent: {
        unionType,
        unionFields
      }
    })
  })
}

// 指标批量管理表格实例
const quotaTable = ref()
// 维度批量管理表格实例
const dimensionsTable = ref()

// 当前维度表格选中字段 ID
const dimensionsSelection = ref([])
// 当前指标表格选中字段 ID
const quotaSelection = ref([])

// 当前批量选中字段的原始类型集合
const fieldTypeSelection = ref([])
// 当前批量选中的字段对象集合
const fieldSelection = ref([])

// 批量类型选择器是否可展示，只有选中字段原始类型一致时展示
const showCascaderBatch = computed(() => {
  return (
    !!fieldTypeSelection.value.length && Array.from(new Set(fieldTypeSelection.value)).length === 1
  )
})

// 清空维度和指标表格的选中状态
const clearSelection = () => {
  dimensionsTable.value.clearSelection()
  quotaTable.value.clearSelection()
}
// 批量字段类型选择器当前值
const fieldTypeSelectionValue = ref([])

// 汇总维度和指标表格选中字段，并计算批量类型选择器状态
const setFieldTypeSelection = () => {
  fieldSelection.value = [
    ...dimensionsTable.value.getSelectionRows(),
    ...quotaTable.value.getSelectionRows()
  ]
  fieldTypeSelection.value = fieldSelection.value.map(ele => ele.extractedFieldType)
  let fieldTypes = fieldSelection.value.map(ele => ele.fieldType)
  const [obj] = fieldSelection.value
  nextTick(() => {
    dimensionsSelection.value = dimensionsTable.value.getSelectionRows().map(ele => ele.id)
    quotaSelection.value = quotaTable.value.getSelectionRows().map(ele => ele.id)
  })
  if (Array.from(new Set(fieldTypes)).length !== 1) {
    fieldTypeSelectionValue.value = []
    return
  }
  fieldTypeSelectionValue.value =
    obj.fieldType === 1 && obj.extractedFieldType === 0 ? [1, obj.dateFormatType] : [obj.fieldType]
}

// 切换批量管理表格行的操作按钮显示状态
const rowClick = (_, __, event) => {
  const element = event.target.parentNode.parentNode
  if ([...element.classList].includes('no-hide')) {
    element.classList.remove('no-hide')
    return
  }
  element.classList.add('no-hide')
}

let oldArrValue = []

// 批量修改字段类型，遇到自定义时间格式时转入弹窗编辑
const cascaderChangeArr = val => {
  const [fieldType, dateFormat] = val
  dimensionsSelection.value = dimensionsTable.value.getSelectionRows().map(ele => ele.id)
  quotaSelection.value = quotaTable.value.getSelectionRows().map(ele => ele.id)

  const arr = [...quotaSelection.value, ...dimensionsSelection.value]
  if (dateFormat === 'custom') {
    const [obj] = allfields.value.filter(ele => arr.includes(ele.id))
    oldArrValue = obj.fieldType === 1 ? [1, obj.dateFormatType] : [obj.fieldType]
    currentField.value.id = ''
    currentField.value.idArr = [...arr]
    currentField.value.dateFormat = ''
    currentField.value.dateFormatType = dateFormat
    updateCustomTime.value = true
    recoverSelection()
    return
  }
  allfields.value.forEach(ele => {
    if (arr.includes(ele.id)) {
      ele.fieldType = fieldType
      ele.dateFormat = fieldType === 1 ? dateFormat : ''
      ele.dateFormatType = fieldType === 1 ? dateFormat : ''
      ele.fieldTypeSelectionValue =
        fieldType === 1 && ele.extractedFieldType === 0 ? [fieldType, dateFormat] : [fieldType]
    }
  })
  recoverSelection()
}

// 数据源树过滤函数
const filterNode = (value: string, data: BusiTreeNode) => {
  if (!value) return true
  return data.name?.toLowerCase().includes(value.toLowerCase())
}
// 批量操作后恢复维度和指标表格的选中状态
const recoverSelection = () => {
  nextTick(() => {
    quota.value.forEach(ele => {
      if (quotaSelection.value.includes(ele.id)) {
        quotaTable.value.toggleRowSelection(ele, true)
      }
    })
    dimensions.value.forEach(ele => {
      if (dimensionsSelection.value.includes(ele.id)) {
        dimensionsTable.value.toggleRowSelection(ele, true)
      }
    })
  })
}

// 左侧拖拽结束后关闭联合画布遮罩
const dragEnd = () => {
  maskShow.value = false
}

// 单字段修改字段类型，遇到自定义时间格式时转入弹窗编辑
const cascaderChange = (row, val) => {
  const [fieldType, dateFormat] = val
  if (dateFormat === 'custom') {
    oldArrValue = row.fieldType === 1 ? [1, row.dateFormatType] : [row.fieldType]
    currentField.value.id = row.id
    updateCustomTime.value = true
    return
  }
  row.fieldType = fieldType
  row.dateFormat = fieldType === 1 ? dateFormat : ''
  row.dateFormatType = fieldType === 1 ? dateFormat : ''
}

// 将后端 union 结构转换为前端联合树结构
const dfsUnion = (arr, list) => {
  list.forEach(ele => {
    const children = []
    if (ele.childrenDs?.length) {
      dfsUnion(children, ele.childrenDs)
    }
    const { unionToParent, currentDsFields, currentDs } = ele
    const { tableName, type, datasourceId, id, info, sqlVariableDetails } = currentDs || {}
    const { unionType, unionFields } = unionToParent || {}
    arr.push({
      sqlVariableDetails,
      tableName,
      type,
      datasourceId,
      id,
      info,
      currentDsFields,
      children,
      unionType,
      unionFields
    })
  })
}
// 进入数据集名称编辑状态并聚焦输入框
const handleClick = () => {
  showInput.value = true
  nextTick(() => {
    editorName.value.focus()
  })
}

// 切换为单源时，如当前联合树已经跨源则提示确认
const sourceChange = val => {
  if (val) return
  if (crossDatasources.value) {
    isCross.value = !val
    ElMessageBox.confirm(t('common.source_tips'), {
      confirmButtonText: t('dataset.confirm'),
      cancelButtonText: t('common.cancel'),
      showCancelButton: true,
      confirmButtonType: 'primary',
      type: 'warning',
      autofocus: false,
      showClose: false
    }).then(() => {
      isCross.value = val
    })
  }
}

// 目录选择弹窗完成后回写数据集基本信息，并异步保存同步配置
const finish = res => {
  const { id, pid, name } = res
  isUpdate = false
  datasetName.value = name
  nodeInfo = {
    id,
    pid,
    name
  }
  currentDatasetId.value = id
  allfields.value = res.allFields || []
  pendingSyncSave = persistDatasetSync(id).catch(e => {
    ElMessage.error(e?.message || '同步设置保存失败')
  })
}

// 数据集名称校验错误文案
const errorTips = ref('')

// 校验数据集名称并控制名称输入框是否保持编辑态
const handleDatasetName = () => {
  errorTips.value = ''
  if (!datasetName.value.trim()) {
    errorTips.value = t('commons.input_content')
  }

  if (datasetName.value.trim().length < 1) {
    errorTips.value = t('datasource.input_limit_1_64', [1, 64])
  }
  showInput.value = !!errorTips.value
}

const treeProps = {
  children: 'children',
  label: 'name',
  disabled: data => {
    return (!data.children?.length && !data.leaf) || (data.extraFlag < 0 && data.type !== 'API')
  }
}

// 插件数据源元数据列表，用于显示自定义图标
const pluginDs = ref([])
// 接收数据源插件列表
const loadDsPlugin = data => {
  pluginDs.value = data
}
// 获取插件数据源图标静态内容
const getDsIcon = data => {
  if (pluginDs?.value.length === 0) return null
  if (!data.leaf) return null

  const arr = pluginDs.value.filter(ele => {
    return ele.type === data.type
  })
  return arr && arr.length > 0 ? arr[0].icon : null
}

// 获取内置数据源图标组件
const getDsIconName = data => {
  if (!data.leaf) return dvFolder
  return iconDatasourceMap[data.type]
}

// 根据字段类型、扩展字段类型和维度指标方向选择字段图标
const getIconNameCalc = (fieldTypeCode, extField, dimension = false) => {
  if (extField === 2) {
    const iconFieldCalculated = dimension ? iconFieldCalculatedMap : iconFieldCalculatedQMap
    return iconFieldCalculated[fieldTypeCode]
  }
  return iconFieldMap[fieldType[fieldTypeCode]]
}
</script>

<template>
  <div class="crest-dataset-form" v-loading="loading">
    <div class="top">
      <span class="name">
        <el-icon @click="backToMain">
          <Icon name="icon_left_outlined"><icon_left_outlined class="svg-icon" /></Icon>
        </el-icon>
        <template v-if="showInput">
          <el-input
            maxlength="64"
            ref="editorName"
            v-model="datasetName"
            @blur="handleDatasetName"
          />
          <div class="ed-form-item__error" v-if="errorTips">{{ errorTips }}</div>
        </template>
        <template v-else>
          <span @click="handleClick" class="dataset-name ellipsis" style="margin-left: 12px">{{
            datasetName
          }}</span>
        </template>
      </span>
      <span class="operate">
        <el-button :disabled="showInput" type="primary" @click="datasetSaveAndBack">{{
          t('data_set.save_and_return')
        }}</el-button>
        <el-button :disabled="showInput" type="primary" @click="datasetSave">{{
          t('data_set.save')
        }}</el-button>
      </span>
    </div>
    <div class="container dataset-db" @mouseup="mouseupDrag">
      <p v-show="!showLeft" class="arrow-right" @click="showLeft = true">
        <el-icon>
          <Icon><icon_right_outlined class="svg-icon" /></Icon>
        </el-icon>
      </p>
      <div
        v-show="showLeft"
        :style="{ left: LeftWidth + 'px' }"
        class="drag-left"
        :class="isDragging && 'is-dragging'"
        @mousedown="mousedownDrag"
      />
      <div
        v-loading="dsLoading"
        v-show="showLeft"
        class="table-list"
        :style="{ width: LeftWidth + 'px' }"
      >
        <div class="table-list-top">
          <el-switch
            style="margin-bottom: 8px"
            v-model="isCross"
            @change="sourceChange"
            :active-text="$t('common.cross_source')"
            :inactive-text="$t('common.single_source')"
          />

          <p class="select-ds">
            {{ t('data_set.select_data_source') }}
            <span class="left-outlined">
              <el-icon style="color: #1f2329" @click="showLeft = false">
                <Icon name="icon_left_outlined"><icon_left_outlined class="svg-icon" /></Icon>
              </el-icon>
            </span>
          </p>
          <el-tree-select
            :check-strictly="false"
            @change="dsChange"
            :placeholder="t('dataset.pls_slc_data_source')"
            class="ds-list"
            :filter-node-method="filterNode"
            filterable
            popper-class="tree-select-ds_popper"
            v-model="dataSource"
            node-key="id"
            :props="treeProps"
            :data="state.dataSourceList"
            :render-after-expand="false"
          >
            <template #default="{ data: { name, leaf, type, extraFlag } }">
              <div class="flex-align-center icon">
                <el-icon>
                  <icon :static-content="getDsIcon({ leaf, type })"
                    ><component class="svg-icon" :is="getDsIconName({ leaf, type })"></component
                  ></icon>
                </el-icon>
                <span v-if="!leaf || extraFlag > -1">{{ name }}</span>
                <el-tooltip
                  effect="dark"
                  v-else
                  :content="`${t('data_set.invalid_data_source')}:${name}`"
                  placement="top"
                >
                  <span>{{ name }}</span>
                </el-tooltip>
              </div>
            </template>
          </el-tree-select>
          <div v-if="showSyncSetting" class="dataset-sync-setting">
            <div class="sync-row">
              <span>读取方式</span>
              <el-radio-group v-model="syncMode" @change="syncModeChange">
                <el-radio :value="0">直连</el-radio>
                <el-radio :value="1" :disabled="!!syncUnsupportedReason">缓存</el-radio>
              </el-radio-group>
            </div>
            <div v-if="syncUnsupportedReason" class="sync-tip">{{ syncUnsupportedReason }}</div>
            <template v-if="syncMode === 1 && syncSupportedDataset">
              <div class="sync-row">
                <span>更新方式</span>
                <el-radio-group v-model="syncTask.updateType" @change="syncUpdateTypeChange">
                  <el-radio value="add_scope" :disabled="!!incrementalDisabledReason"
                    >增量</el-radio
                  >
                  <el-radio value="all_scope">全量</el-radio>
                </el-radio-group>
              </div>
              <div v-if="incrementalDisabledReason" class="sync-tip">
                {{ incrementalDisabledReason }}
              </div>
              <div class="sync-row">
                <span>更新频率</span>
                <el-radio-group v-model="syncTask.syncRate" @change="syncRateChange">
                  <el-radio value="RIGHTNOW">手动</el-radio>
                  <el-radio value="SIMPLE_CRON">定时</el-radio>
                  <el-radio v-if="syncTask.syncRate === 'CRON'" value="CRON">表达式</el-radio>
                </el-radio-group>
              </div>
              <div v-if="syncTask.syncRate === 'SIMPLE_CRON'" class="sync-cron">
                <el-input-number
                  v-model="syncTask.simpleCronValue"
                  :min="1"
                  controls-position="right"
                  @change="refreshSimpleCron"
                />
                <el-select
                  v-model="syncTask.simpleCronType"
                  class="sync-unit"
                  @change="refreshSimpleCron"
                >
                  <el-option label="分钟" value="minute" />
                  <el-option label="小时" value="hour" />
                  <el-option label="天" value="day" />
                </el-select>
              </div>
              <div class="sync-summary">
                <span>{{ syncMethodText }}</span>
                <span>{{ syncStatusText }}</span>
              </div>
              <el-tooltip
                :disabled="!syncNowDisabledReason"
                :content="syncNowDisabledReason"
                placement="top"
              >
                <span class="sync-now-wrapper">
                  <el-button
                    class="sync-now"
                    secondary
                    :loading="syncSubmitting"
                    :disabled="syncNowDisabled"
                    @click="executeDatasetSyncNow"
                  >
                    {{ syncRunning ? '更新中' : '立即更新' }}
                  </el-button>
                </span>
              </el-tooltip>
              <div class="sync-status" v-if="syncTask.lastExecTime || latestSyncLog">
                <span v-if="latestSyncRowCountText">{{ latestSyncRowCountText }}</span>
                <span v-if="syncTask.lastExecTime">{{
                  dayjs(syncTask.lastExecTime).format('YYYY-MM-DD HH:mm:ss')
                }}</span>
              </div>
              <el-tooltip v-if="latestSyncInfo" :content="latestSyncInfo" placement="top">
                <div class="sync-log">{{ syncDetailText }}</div>
              </el-tooltip>
              <el-button
                class="sync-advanced-toggle"
                text
                @click="syncAdvancedVisible = !syncAdvancedVisible"
              >
                {{ syncAdvancedVisible ? '收起' : '更多设置' }}
              </el-button>
              <div v-if="syncAdvancedVisible" class="sync-advanced">
                <el-input
                  v-if="syncTask.syncRate === 'CRON'"
                  v-model="syncTask.cron"
                  class="sync-control"
                  placeholder="Cron 表达式"
                  @change="markSyncConfigDirty"
                />
                <el-select
                  v-if="syncTask.updateType === 'add_scope'"
                  v-model="syncTask.incrementalFieldId"
                  class="sync-control"
                  placeholder="自动识别增量字段"
                  @change="markSyncConfigDirty"
                >
                  <el-option
                    v-for="field in incrementalFieldOptions"
                    :key="field.id"
                    :label="field.name"
                    :value="field.id"
                  />
                </el-select>
                <div class="sync-options">
                  <div v-if="syncTask.updateType === 'add_scope'" class="sync-option">
                    <span>全量刷新间隔(小时)</span>
                    <el-input-number
                      v-model="syncTask.fullSyncIntervalHours"
                      :min="0"
                      :max="720"
                      controls-position="right"
                      @change="markSyncConfigDirty"
                    />
                  </div>
                  <div class="sync-option">
                    <span>超时(分钟)</span>
                    <el-input-number
                      v-model="syncTask.taskTimeoutMinutes"
                      :min="0"
                      :max="1440"
                      controls-position="right"
                      @change="markSyncConfigDirty"
                    />
                  </div>
                </div>
                <el-checkbox
                  v-model="syncTask.verifyEnabled"
                  class="sync-check"
                  :true-value="1"
                  :false-value="0"
                  @change="markSyncConfigDirty"
                >
                  更新后校验数据一致性
                </el-checkbox>
              </div>
            </template>
          </div>
          <p class="select-ds table-num">
            {{ t('datasource.data_table') }}
            <span class="num">
              <el-icon class="icon-color">
                <Icon name="reference-table"><referenceTable class="svg-icon" /></Icon>
              </el-icon>
              {{ datasourceTableData.length }}
            </span>
          </p>
          <el-input
            v-model="searchTable"
            class="search"
            :placeholder="t('datasetUi.by_table_name')"
            clearable
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
        <div v-if="!datasourceTableData.length && searchTable !== ''" class="el-empty">
          <div
            class="el-empty__description"
            style="margin-top: 80px; color: #5e6d82; text-align: center"
          >
            {{ t('data_set.relevant_content_found') }}
          </div>
        </div>
        <div v-else class="table-checkbox-list">
          <div
            class="list-item_primary"
            v-if="dataSource"
            @dragstart="$event => dragstart($event, sqlNode)"
            @dragend="dragEnd"
            :draggable="true"
            @click="setActiveName(sqlNode)"
          >
            <el-icon class="icon-color">
              <Icon name="icon_sql_outlined_1"><icon_sql_outlined_1 class="svg-icon" /></Icon>
            </el-icon>
            <span class="label">{{ t('data_set.custom_sql') }}</span>
          </div>
          <FixedSizeList
            :itemSize="40"
            :data="datasourceTableData"
            :total="datasourceTableData.length"
            :width="LeftWidth - 17"
            :height="height - 305"
            :scrollbarAlwaysOn="false"
            class-name="el-select-dropdown__list"
            layout="vertical"
          >
            <template #default="{ index, style }">
              <div
                class="list-item_primary"
                :style="style"
                :title="datasourceTableData[index].tableName"
                @dragstart="$event => dragstart($event, datasourceTableData[index])"
                @dragend="maskShow = false"
                :draggable="true"
                @click="setActiveName(datasourceTableData[index])"
              >
                <el-icon class="icon-color">
                  <Icon name="reference-table"><referenceTable class="svg-icon" /></Icon>
                </el-icon>
                <span class="label">{{ datasourceTableData[index].tableName }}</span>
              </div>
            </template>
          </FixedSizeList>
        </div>
      </div>
      <div class="drag-right" :style="{ width: `calc(100vw - ${showLeft ? LeftWidth : 0}px)` }">
        <div v-if="crossDatasources" class="different-datasource">
          <el-icon>
            <Icon name="icon_warning_colorful"><icon_warning_colorful class="svg-icon" /></Icon>
          </el-icon>
          {{ t('data_set.be_reported_incorrectly') }}
        </div>
        <dataset-union
          @reGetName="reGetName"
          @join-editor="joinEditor"
          @changeUpdate="changeUpdate"
          :maskShow="maskShow"
          :dragHeight="dragHeight"
          :getDsName="getDsName"
          :offsetX="offsetX"
          :offsetY="offsetY"
          ref="datasetDrag"
          @updateAllfields="updateAllfields"
          @addComplete="addComplete"
        ></dataset-union>
        <div
          class="sql-result"
          :style="{
            height: sqlResultHeight
              ? `${crossDatasources ? sqlResultHeight - 40 : sqlResultHeight}px`
              : `calc(100% - ${crossDatasources ? dragHeight + 40 : dragHeight}px)`
          }"
        >
          <div class="sql-title">
            <span class="drag" @mousedown="mousedownDragH" />
            <div class="field-data">
              <el-button :disabled="!allfields.length" @click="addCalcField('q')" secondary>
                <template #icon>
                  <el-icon>
                    <Icon name="icon_add_outlined"><icon_add_outlined class="svg-icon" /></Icon>
                  </el-icon>
                </template>
                {{ t('dataset.add_calc_field') }}
              </el-button>
              <el-button :disabled="!allfields.length" @click="addGroupField" secondary>
                <template #icon>
                  <el-icon>
                    <Icon><icon_organization_outlined class="svg-icon" /></Icon>
                  </el-icon>
                </template>
                {{ t('dataset.create_grouping_field') }}
              </el-button>
              <el-button
                style="min-width: 70px"
                :disabled="!allfields.length"
                v-loading="datasetPreviewLoading"
                @click="datasetPreview"
                secondary
              >
                <template #icon>
                  <el-icon>
                    <Icon name="icon_refresh_outlined"
                      ><icon_refresh_outlined class="svg-icon"
                    /></Icon>
                  </el-icon>
                </template>
                {{ t('data_set.refresh_data') }}
              </el-button>
            </div>
          </div>
          <el-tabs class="padding-24" v-model="tabActive" @tab-change="tabChange">
            <el-tab-pane :label="t('chart.data_preview')" name="preview" />
            <el-tab-pane :label="t('dataset.batch_manage')" name="manage" />
          </el-tabs>
          <div v-show="tabActive === 'preview' && !!allfields.length" class="table-preview">
            <div class="preview-field">
              <div :class="['field-d', { open: expandedD }]">
                <div :class="['title', { expanded: expandedD }]" @click="expandedD = !expandedD">
                  <ElIcon class="expand">
                    <Icon name="icon_expand-right_filled"
                      ><icon_expandRight_filled class="svg-icon"
                    /></Icon>
                  </ElIcon>
                  &nbsp;{{ t('chart.dimension') }}
                </div>
                <el-tree v-if="expandedD" :data="dimensions" :props="defaultProps">
                  <template #default="{ data }">
                    <span class="custom-tree-node father">
                      <el-icon>
                        <Icon
                          ><component
                            class="svg-icon"
                            :class="`field-icon-${
                              fieldType[[2, 3].includes(data.fieldType) ? 2 : 0]
                            }`"
                            :is="getIconNameCalc(data.fieldType, data.extField)"
                          ></component
                        ></Icon>
                      </el-icon>
                      <span :title="data.name" class="label-tooltip">{{ data.name }}</span>
                      <div class="operate child">
                        <field-more
                          :extField="data.extField"
                          :trans-type="t('data_set.convert_to_indicator')"
                          :show-time="data.extractedFieldType === 0"
                          @handle-command="type => handleFieldMore(data, type)"
                        ></field-more>
                      </div>
                    </span>
                  </template>
                </el-tree>
              </div>
              <div :class="['field-q', { open: expandedQ }]">
                <div :class="['title', { expanded: expandedQ }]" @click="expandedQ = !expandedQ">
                  <ElIcon class="expand">
                    <Icon name="icon_expand-right_filled"
                      ><icon_expandRight_filled class="svg-icon"
                    /></Icon>
                  </ElIcon>
                  &nbsp;{{ t('chart.quota') }}
                </div>
                <el-tree v-if="expandedQ" :data="quota" :props="defaultProps">
                  <template #default="{ data }">
                    <span class="custom-tree-node father">
                      <el-icon>
                        <Icon
                          ><component
                            class="svg-icon"
                            :class="`field-icon-${
                              fieldType[[2, 3].includes(data.fieldType) ? 2 : 0]
                            }`"
                            :is="getIconNameCalc(data.fieldType, data.extField, true)"
                          ></component
                        ></Icon>
                      </el-icon>
                      <span :title="data.name" class="label-tooltip">{{ data.name }}</span>
                      <div class="operate child">
                        <field-more
                          :trans-type="t('data_set.convert_to_dimension')"
                          typeColor="green-color"
                          :show-time="data.extractedFieldType === 0"
                          :extField="data.extField"
                          @handle-command="type => handleFieldMore(data, type)"
                        ></field-more>
                      </div>
                    </span>
                  </template>
                </el-tree>
              </div>
            </div>
            <div class="preview-data">
              <el-table
                class="dataset-preview_table crest-data-table"
                @row-click="rowClick"
                v-loading="datasetPreviewLoading"
                header-class="header-cell"
                :data="tableData"
                border
                style="width: 100%; height: 100%"
              >
                <el-table-column
                  :key="column.dataKey + column.fieldType"
                  v-for="(column, index) in columns"
                  :prop="column.dataKey"
                  :label="column.title"
                  :width="columns.length - 1 === index ? 150 : 'auto'"
                  :fixed="columns.length - 1 === index ? 'right' : false"
                >
                  <template #header>
                    <div class="flex-align-center">
                      <ElIcon style="margin-right: 6px">
                        <Icon
                          ><component
                            class="svg-icon"
                            :class="`field-icon-${
                              fieldType[[2, 3].includes(column.fieldType) ? 2 : 0]
                            }`"
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
            </div>
          </div>
          <div v-show="tabActive !== 'preview' && !!allfields.length" class="batch-area">
            <div class="manage-container">
              <el-collapse v-model="state.fieldCollapse" class="style-collapse">
                <el-collapse-item
                  name="dimension"
                  :title="t('chart.dimension')"
                  class="dimension-manage-header manage-header"
                >
                  <el-table
                    class="crest-data-table"
                    @selection-change="setFieldTypeSelection"
                    ref="dimensionsTable"
                    :data="dimensions"
                    :height="quotaTableHeight"
                    style="width: 100%"
                  >
                    <el-table-column :selectable="selectable" type="selection" width="40" />
                    <el-table-column prop="name" :label="t('dataset.field_name')" width="264">
                      <template #default="scope">
                        <div class="column-style">
                          <el-input
                            v-model="scope.row.name"
                            maxlength="100"
                            :placeholder="t('commons.input_content')"
                          />
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column
                      prop="originName"
                      :label="t('dataset.origin_name')"
                      width="240"
                    >
                      <template #default="scope">
                        <div class="column-style">
                          <span style="color: #8d9199" v-if="scope.row.extField === 2">{{
                            t('dataset.calc_field')
                          }}</span>
                          <span style="color: #8d9199" v-else-if="scope.row.extField === 3">{{
                            t('dataset.grouping_field')
                          }}</span>
                          <span v-else>{{ scope.row.originName }}</span>
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column
                      prop="description"
                      :label="t('datasetUi.description')"
                      width="240"
                    >
                      <template #default="scope">
                        <div class="column-style">
                          <span v-if="scope.row.extField === 0">{{ scope.row.description }}</span>
                          <span style="color: #8d9199" v-else>&nbsp;</span>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column
                      prop="datasetTableId"
                      :label="t('data_set.table_name_label')"
                      width="240"
                    >
                      <template #default="scope">
                        {{ scope.row.extField === 0 ? nameMap[scope.row.datasetTableId] : '' }}
                      </template>
                    </el-table-column>
                    <el-table-column prop="fieldType" :label="t('dataset.field_type')" width="200">
                      <template #default="scope">
                        <el-cascader
                          :class="
                            !!scope.row.fieldTypeSelectionValue &&
                            !!scope.row.fieldTypeSelectionValue.length &&
                            'select-type'
                          "
                          v-if="scope.row.extField !== 3"
                          popper-class="cascader-panel"
                          v-model="scope.row.fieldTypeSelectionValue"
                          @change="val => cascaderChange(scope.row, val)"
                          :options="
                            scope.row.extractedFieldType === 0 ? fieldOptions : fieldOptionsText
                          "
                        >
                          <template v-slot="{ data }">
                            <el-icon>
                              <Icon
                                ><component
                                  class="svg-icon"
                                  :class="`field-icon-${
                                    fieldType[[2, 3].includes(data.value) ? 2 : 0]
                                  }`"
                                  :is="iconFieldMap[getIconName(data.value)]"
                                ></component
                              ></Icon>
                            </el-icon>
                            <span>{{ data.label }}</span>
                          </template>
                        </el-cascader>
                        <div style="padding-left: 30px" v-else>{{ $t('data_set.text') }}</div>
                        <span class="select-svg-icon">
                          <el-icon>
                            <Icon
                              ><component
                                class="svg-icon"
                                :class="`field-icon-${
                                  fieldType[[2, 3].includes(scope.row.fieldType) ? 2 : 0]
                                }`"
                                :is="iconFieldMap[getIconName(scope.row.fieldType)]"
                              ></component
                            ></Icon>
                          </el-icon>
                        </span>
                      </template>
                    </el-table-column>

                    <el-table-column
                      prop="originType"
                      :label="t('dataset.origin_type')"
                      width="168"
                    >
                      <template #default="scope">
                        <div class="column-style">
                          <span style="color: #8d9199" v-if="scope.row.extField === 2">{{
                            t('dataset.calc_field')
                          }}</span>
                          <span style="color: #8d9199" v-else-if="scope.row.extField === 3">{{
                            t('dataset.grouping_field')
                          }}</span>
                          <span class="flex-align-center icon" v-else-if="scope.row.extField === 0">
                            <el-icon>
                              <Icon className="primary-color"
                                ><component
                                  class="svg-icon primary-color"
                                  :is="iconFieldMap[getIconName(scope.row.extractedFieldType)]"
                                ></component
                              ></Icon>
                            </el-icon>
                            {{ fieldTypes(scope.row.extractedFieldType) }}
                          </span>
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column :label="t('chart.total_sort_field')" align="center" width="90">
                      <template #default="scope">
                        <el-checkbox v-model="scope.row.orderChecked" />
                      </template>
                    </el-table-column>

                    <el-table-column fixed="right" :label="t('chart.dimension')">
                      <template #default="scope">
                        <el-tooltip
                          effect="dark"
                          :content="t('data_set.convert_to_indicator')"
                          placement="top"
                        >
                          <template #default>
                            <el-button
                              v-if="![3].includes(scope.row.extField)"
                              text
                              @click="handleFieldMore(scope.row, 'translate')"
                            >
                              <template #icon>
                                <Icon name="icon_switch_outlined"
                                  ><icon_switch_outlined class="svg-icon"
                                /></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>
                      </template>
                    </el-table-column>

                    <el-table-column fixed="right" width="168" :label="t('dataset.operator')">
                      <template #default="scope">
                        <el-tooltip effect="dark" :content="t('dataset.copy')" placement="top">
                          <template #default>
                            <el-button text @click="handleFieldMore(scope.row, 'copy')">
                              <template #icon>
                                <Icon name="icon_copy_outlined"
                                  ><icon_copy_outlined class="svg-icon"
                                /></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>

                        <el-tooltip effect="dark" :content="t('dataset.delete')" placement="top">
                          <template #default>
                            <el-button text @click="handleFieldMore(scope.row, 'delete')">
                              <template #icon>
                                <Icon name="icon_delete-trash_outlined"
                                  ><icon_deleteTrash_outlined class="svg-icon"
                                /></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>

                        <el-tooltip effect="dark" :content="t('dataset.edit')" placement="top">
                          <template #default>
                            <el-button
                              v-if="[2, 3].includes(scope.row.extField)"
                              text
                              @click="handleFieldMore(scope.row, 'editor')"
                            >
                              <template #icon>
                                <Icon name="icon_edit_outlined"
                                  ><icon_edit_outlined class="svg-icon"
                                /></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>
                      </template>
                    </el-table-column>
                  </el-table>
                </el-collapse-item>
                <el-collapse-item
                  name="quota"
                  :title="t('chart.quota')"
                  class="quota-manage-header manage-header"
                >
                  <el-table
                    class="crest-data-table"
                    @selection-change="setFieldTypeSelection"
                    ref="quotaTable"
                    :height="quotaTableHeight"
                    :data="quota"
                    style="width: 100%"
                  >
                    <el-table-column type="selection" width="40" />
                    <el-table-column prop="name" :label="t('dataset.field_name')" width="264">
                      <template #default="scope">
                        <div class="column-style">
                          <el-input
                            v-model="scope.row.name"
                            :placeholder="t('commons.input_content')"
                          />
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column
                      prop="originName"
                      :label="t('dataset.origin_name')"
                      width="240"
                    >
                      <template #default="scope">
                        <div class="column-style">
                          <span v-if="scope.row.extField === 0">{{ scope.row.originName }}</span>
                          <span v-else style="color: #8d9199">{{ t('dataset.calc_field') }}</span>
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column
                      prop="description"
                      :label="t('datasetUi.description')"
                      width="240"
                    >
                      <template #default="scope">
                        <div class="column-style">
                          <span v-if="scope.row.extField === 0">{{ scope.row.description }}</span>
                          <span style="color: #8d9199" v-else>&nbsp;</span>
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column
                      prop="datasetTableId"
                      :label="t('data_set.table_name_label')"
                      width="240"
                    >
                      <template #default="scope">
                        {{ scope.row.extField === 0 ? nameMap[scope.row.datasetTableId] : '' }}
                      </template>
                    </el-table-column>

                    <el-table-column prop="fieldType" :label="t('dataset.field_type')" width="200">
                      <template #default="scope">
                        <el-cascader
                          :class="
                            !!scope.row.fieldTypeSelectionValue &&
                            !!scope.row.fieldTypeSelectionValue.length &&
                            'select-type'
                          "
                          popper-class="cascader-panel"
                          v-model="scope.row.fieldTypeSelectionValue"
                          @change="val => cascaderChange(scope.row, val)"
                          :options="
                            scope.row.extractedFieldType === 0 ? fieldOptions : fieldOptionsText
                          "
                        >
                          <template v-slot="{ data }">
                            <el-icon>
                              <Icon
                                ><component
                                  class="svg-icon"
                                  :class="`field-icon-${
                                    fieldType[[2, 3].includes(data.value) ? 2 : 0]
                                  }`"
                                  :is="iconFieldMap[getIconName(data.value)]"
                                ></component
                              ></Icon>
                            </el-icon>
                            <span>{{ data.label }}</span>
                          </template>
                        </el-cascader>
                        <span class="select-svg-icon">
                          <el-icon>
                            <Icon
                              ><component
                                class="svg-icon"
                                :class="`field-icon-${
                                  fieldType[[2, 3].includes(scope.row.fieldType) ? 2 : 0]
                                }`"
                                :is="iconFieldMap[getIconName(scope.row.fieldType)]"
                              ></component
                            ></Icon>
                          </el-icon>
                        </span>
                      </template>
                    </el-table-column>

                    <el-table-column
                      prop="originType"
                      :label="t('dataset.origin_type')"
                      width="168"
                    >
                      <template #default="scope">
                        <div class="column-style">
                          <span style="color: #8d9199" v-if="scope.row.extField === 2">{{
                            t('dataset.calc_field')
                          }}</span>
                          <span style="color: #8d9199" v-else-if="scope.row.extField === 3">{{
                            t('dataset.grouping_field')
                          }}</span>
                          <span class="flex-align-center icon" v-else-if="scope.row.extField === 0">
                            <el-icon>
                              <Icon className="green-color"
                                ><component
                                  class="svg-icon green-color"
                                  :is="iconFieldMap[getIconName(scope.row.extractedFieldType)]"
                                ></component
                              ></Icon>
                            </el-icon>
                            {{ fieldTypes(scope.row.extractedFieldType) }}
                          </span>
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column :label="t('chart.total_sort_field')" align="center" width="90">
                      <template #default="scope">
                        <el-checkbox v-model="scope.row.orderChecked" />
                      </template>
                    </el-table-column>

                    <el-table-column fixed="right" :label="t('chart.quota')">
                      <template #default="scope">
                        <el-tooltip
                          effect="dark"
                          :content="t('data_set.convert_to_dimension')"
                          placement="top"
                        >
                          <template #default>
                            <el-button text @click="handleFieldMore(scope.row, 'translate')">
                              <template #icon>
                                <Icon name="icon_switch_outlined"
                                  ><icon_switch_outlined class="svg-icon"
                                /></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>
                      </template>
                    </el-table-column>

                    <el-table-column fixed="right" width="168" :label="t('dataset.operator')">
                      <template #default="scope">
                        <el-tooltip effect="dark" :content="t('dataset.copy')" placement="top">
                          <template #default>
                            <el-button text @click="handleFieldMore(scope.row, 'copy')">
                              <template #icon>
                                <Icon name="icon_copy_outlined"
                                  ><icon_copy_outlined class="svg-icon"
                                /></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>

                        <el-tooltip effect="dark" :content="t('dataset.delete')" placement="top">
                          <template #default>
                            <el-button text @click="handleFieldMore(scope.row, 'delete')">
                              <template #icon>
                                <Icon name="icon_delete-trash_outlined"
                                  ><icon_deleteTrash_outlined class="svg-icon"
                                /></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>

                        <el-tooltip effect="dark" :content="t('dataset.edit')" placement="top">
                          <template #default>
                            <el-button
                              v-if="scope.row.extField === 2"
                              text
                              @click="handleFieldMore(scope.row, 'editor')"
                            >
                              <template #icon>
                                <Icon name="icon_edit_outlined"
                                  ><icon_edit_outlined class="svg-icon"
                                /></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>
                      </template>
                    </el-table-column>
                  </el-table>
                </el-collapse-item>
              </el-collapse>
            </div>
            <div class="batch-operate flex-align-center" v-if="!!fieldTypeSelection.length">
              <div class="flex-align-center">
                {{ t('data_set.selected') }}
                <span class="num">{{ fieldTypeSelection.length }}</span>
                {{ t('data_set.bar') }}
                <el-button @click="clearSelection" text style="margin-left: 16px">{{
                  t('commons.clear')
                }}</el-button>
              </div>
              <div class="cascader-batch">
                <el-cascader
                  :disabled="!showCascaderBatch"
                  :class="!!fieldTypeSelectionValue.length && 'select-type'"
                  v-model="fieldTypeSelectionValue"
                  @change="cascaderChangeArr"
                  popper-class="cascader-panel"
                  :options="
                    fieldTypeSelection.every(ele => ele === 0) ? fieldOptions : fieldOptionsText
                  "
                >
                  <template v-slot="{ data }">
                    <el-icon>
                      <Icon
                        ><component
                          class="svg-icon"
                          :class="`field-icon-${fieldType[[2, 3].includes(data.value) ? 2 : 0]}`"
                          :is="iconFieldMap[getIconName(data.value)]"
                        ></component
                      ></Icon>
                    </el-icon>
                    <span>{{ data.label }}</span>
                  </template>
                </el-cascader>
                <span class="select-svg-icon">
                  <el-icon>
                    <Icon :className="`field-icon-${getIconName(fieldTypeSelectionValue[0])}`"
                      ><component
                        class="svg-icon"
                        :class="`field-icon-${getIconName(fieldTypeSelectionValue[0])}`"
                        :is="iconFieldMap[getIconName(fieldTypeSelectionValue[0])]"
                      ></component
                    ></Icon>
                  </el-icon>
                </span>
                <el-tooltip class="item" effect="dark" placement="top">
                  <template #content>
                    <div>{{ t('dataset.field_diff') }}</div>
                  </template>
                  <el-icon size="16px" style="margin-left: 6px" v-show="!showCascaderBatch">
                    <Icon name="icon_info_outlined"
                      ><icon_info_outlined class="svg-icon tip-icon"
                    /></Icon>
                  </el-icon>
                </el-tooltip>
              </div>
              <el-button
                @click="dqTransArr('q')"
                v-if="fieldSelection.every(ele => ele.groupType === 'd')"
                plain
                style="margin-left: 200px"
              >
                {{ t('data_set.convert_to_indicator') }}
              </el-button>
              <el-button
                @click="dqTransArr('d')"
                v-else-if="fieldSelection.every(ele => ele.groupType === 'q')"
                plain
                style="margin-left: 200px"
              >
                {{ t('data_set.convert_to_dimension') }}
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
    <el-drawer
      :title="t('dataset.edit_union_relation')"
      v-model="editUnion"
      modal-class="union-dataset-drawer"
      size="840px"
      :before-close="closeEditUnion"
      direction="rtl"
    >
      <union-edit ref="fieldUnion" :editArr="state.editArr" />
      <template #footer>
        <el-button secondary @click="closeEditUnion">{{ t('dataset.cancel') }} </el-button>
        <el-button type="primary" @click="confirmEditUnion">{{ t('dataset.confirm') }} </el-button>
      </template>
    </el-drawer>
  </div>
  <creat-ds-group
    @finish="finish"
    @onDatasetSave="saveAndBack"
    ref="creatDsFolder"
  ></creat-ds-group>
  <el-dialog
    modal-class="calc-field-edit-dialog"
    v-model="editCalcField"
    width="1000px"
    :title="calcTitle"
  >
    <calc-field-edit ref="calcEdit" :crossDs="crossDatasources" />
    <template #footer>
      <el-button secondary @click="closeEditCalc()">{{ t('dataset.cancel') }} </el-button>
      <el-button secondary @click="verify">{{ t('datasource.validate') }} </el-button>
      <el-button type="primary" @click="confirmEditCalc()">{{ t('dataset.confirm') }} </el-button>
    </template>
  </el-dialog>
  <el-dialog
    class="create-dialog"
    :title="t('data_set.format_edit')"
    v-model="updateCustomTime"
    width="1000px"
  >
    <el-form ref="ruleFormRef" :rules="rules" :model="currentField" label-width="120px">
      <el-form-item prop="name" :label="t('data_set.custom_time_format')">
        <el-input v-model="currentField.name" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button secondary @click="closeCustomTime()">{{ t('dataset.cancel') }} </el-button>
      <el-button type="primary" @click="confirmCustomTime()">{{ t('dataset.confirm') }} </el-button>
    </template>
  </el-dialog>
  <el-dialog
    class="create-dialog"
    :title="t('datasource.field_rename')"
    v-model="editNormalField"
    width="420px"
  >
    <el-form
      ref="ruleFormFieldRef"
      :rules="fieldRules"
      :model="currentNormalField"
      require-asterisk-position="right"
      label-position="top"
      label-width="120px"
    >
      <el-form-item prop="name" :label="t('dataset.field_name')">
        <el-input maxlength="100" v-model="currentNormalField.name" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button secondary @click="closeNormalField()">{{ t('dataset.cancel') }} </el-button>
      <el-button type="primary" @click="confirmNormalField()"
        >{{ t('dataset.confirm') }}
      </el-button>
    </template>
  </el-dialog>
  <el-dialog
    class="create-dialog group-fields_dialog"
    :title="titleForGroup"
    v-model="editGroupField"
    width="1000px"
  >
    <el-form
      ref="ruleGroupFieldRef"
      :rules="fieldGroupRules"
      :model="currentGroupField"
      require-asterisk-position="right"
      label-position="top"
      label-width="120px"
      v-loading="enumValueLoading"
    >
      <el-row :gutter="24">
        <el-col :span="12">
          <el-form-item prop="name" :label="t('dataset.field_name')">
            <el-input
              :placeholder="t('dataset.input_edit_name')"
              v-model="currentGroupField.name"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="originName" :label="t('dataset.grouping_field')">
            <el-select
              @change="handleFieldschange"
              v-model="currentGroupField.originName"
              style="width: 100%"
            >
              <el-option
                v-for="item in groupFields"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item style="margin-top: 16px">
        <template v-slot:label>
          <div class="grouping_settings_label">
            {{ t('dataset.grouping_settings') }}
          </div>
        </template>
        <div style="width: 100%">
          <el-scrollbar max-height="393px"
            ><div
              class="group-fields_item"
              v-for="(domain, index) in currentGroupField.groupList"
              :key="index"
            >
              <el-form
                :ref="el => (refsForm[index] = el)"
                :model="domain"
                inline
                :key="index"
                label-width="auto"
                class="form-dynamic"
              >
                <el-form-item
                  :key="index + 'name'"
                  prop="name"
                  :rules="{
                    required: true,
                    message: t('chart.value_can_not_empty'),
                    trigger: 'blur'
                  }"
                  ><el-input
                    style="width: 278px"
                    v-model="domain.name"
                    :placeholder="t('common.inputText')"
                /></el-form-item>
                <el-form-item
                  :key="index + 'text'"
                  v-if="[0, null].includes(currentGroupField.originalFieldType)"
                  prop="text"
                  style="width: 100%; margin-left: 24px"
                  :rules="{
                    validator: validatePass,
                    required: true,
                    trigger: 'change'
                  }"
                  ><el-select
                    style="width: 100%"
                    multiple
                    collapse-tags
                    filterable
                    collapse-tags-tooltip
                    :max-collapse-tags="2"
                    v-model="domain.text"
                  >
                    <el-option
                      v-for="item in enumValue"
                      :key="item"
                      :label="item"
                      :disabled="disabledEnum(item, domain.text)"
                      :value="item"
                    /> </el-select
                ></el-form-item>

                <div
                  class="group-fields_num"
                  v-else-if="[2, 3, 4].includes(currentGroupField.originalFieldType)"
                >
                  <el-form-item
                    :key="index + 'min'"
                    prop="min"
                    :rules="{
                      required: true,
                      message: t('chart.value_can_not_empty'),
                      trigger: 'blur'
                    }"
                    ><el-input-number
                      :placeholder="t('dataset.please_enter_number')"
                      v-model="domain.min"
                      controls-position="right"
                  /></el-form-item>
                  <el-form-item :key="index + 'minTerm'"
                    ><el-select v-model="domain.minTerm">
                      <el-option
                        v-for="item in equalMin"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      /> </el-select
                  ></el-form-item>
                  <div class="name">
                    {{ t('dataset.field_value') }}
                  </div>
                  <el-form-item :key="index + 'maxTerm'"
                    ><el-select v-model="domain.maxTerm">
                      <el-option
                        v-for="item in equalMin"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      /> </el-select
                  ></el-form-item>
                  <el-form-item
                    :key="index + 'max'"
                    prop="max"
                    :rules="{
                      required: true,
                      message: t('chart.value_can_not_empty'),
                      trigger: 'blur'
                    }"
                    ><el-input-number
                      :placeholder="t('dataset.please_enter_number')"
                      v-model="domain.max"
                      :validate-even="false"
                      controls-position="right"
                  /></el-form-item>
                </div>
                <el-form-item
                  :key="index + 'time'"
                  prop="time"
                  class="group-fields_num"
                  v-else-if="[1].includes(currentGroupField.originalFieldType)"
                  :rules="{
                    required: true,
                    validator: validatePass,
                    trigger: 'change'
                  }"
                  ><el-date-picker
                    :end-placeholder="t('commons.date.end_date')"
                    :start-placeholder="t('commons.date.start_date')"
                    v-model="domain.time"
                    type="daterange" /></el-form-item
              ></el-form>

              <el-button
                class="variable_del"
                text
                v-if="currentGroupField.groupList.length !== 1"
                @click="removeGroupFields(index)"
              >
                <template #icon>
                  <Icon><icon_deleteTrash_outlined class="svg-icon" /></Icon>
                </template>
              </el-button></div
          ></el-scrollbar>
        </div>
      </el-form-item>
      <el-button style="margin-top: -20px" @click="addGroupFields" text>
        <template #icon>
          <icon><icon_add_outlined class="svg-icon" /></icon>
        </template>
        {{ t('auth.add_condition') }}
      </el-button>
      <div class="line"></div>
      <div class="group-fields_item" style="align-items: center">
        <el-input
          :placeholder="t('common.inputText')"
          style="width: 278px; margin-right: 24px"
          v-model="currentGroupField.otherGroup"
        />
        {{ t('dataset.ungrouped_value') }}
      </div>
    </el-form>
    <template #footer>
      <el-button secondary @click="closeGroupField">{{ t('dataset.cancel') }} </el-button>
      <el-button type="primary" @click="confirmGroupField">{{ t('dataset.confirm') }} </el-button>
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

.crest-dataset-form {
  color: #1f2329;

  :deep(.ed-table__border-left-patch),
  :deep(.ed-table--border .ed-table__inner-wrapper::after) {
    display: none !important;
  }

  --ed-border-color-lighter: #1f232926 !important;
  .top {
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 24px;
    background: #050e21;
    box-shadow: 0px 2px 4px 0px rgba(31, 35, 41, 0.12);

    .name {
      color: #fff;
      font-family: var(--crest-custom_font, 'PingFang');
      font-size: 16px;
      font-weight: 400;
      display: flex;
      align-items: center;
      width: 50%;
      position: relative;

      .ed-form-item__error {
        top: 19px !important;
        left: 16px !important;
      }
      .dataset-name {
        cursor: pointer;
        width: 294px;
      }

      .ed-input {
        width: 302px;
        line-height: 24px;
        height: 24px;
        :deep(.ed-input__wrapper) {
          background-color: #050e21;
          box-shadow: 0 0 0 1px var(--ed-color-primary);
          padding: 0 4px;
        }
        :deep(.ed-input__inner) {
          color: #fff;
          font-size: 16px;
        }
      }
      i {
        cursor: pointer;
      }
    }
  }

  .container {
    width: 100%;
    height: calc(100vh - 56px);
    position: relative;
    .drag-left {
      position: absolute;
      height: calc(100vh - 56px);
      width: 4px;
      top: 0;
      z-index: 2;
      cursor: col-resize;

      &.is-dragging::after,
      &:hover::after {
        width: 1px;
        height: 100%;
        content: '';
        position: absolute;
        left: -1px;
        top: 0;
        background: var(--ed-color-primary);
      }
    }

    .arrow-right {
      position: absolute;
      top: 15px;
      z-index: 2;
      cursor: pointer;
      margin: 0;
      display: flex;
      align-items: center;
      left: 0;
      height: 24px;
      width: 20px;
      box-shadow: 0px 4px 8px rgba(0, 0, 0, 0.1);
      border: 1px solid var(--crestCardStrokeColor, #dee0e3);
      display: flex;
      align-items: center;
      padding-left: 2px;
      border-top-right-radius: 12px;
      border-bottom-right-radius: 12px;
      background: #fff;
      font-size: 12px;

      &:hover {
        padding-left: 4px;
        width: 24px;
        .ed-icon {
          color: var(--ed-color-primary, #3b82f6);
        }
      }
    }

    .table-list {
      display: flex;
      flex-direction: column;

      .list-item_primary {
        padding: 8px;
      }
      .table-list-top {
        padding: 16px;
        padding-bottom: 0;
        flex: 0 0 auto;
      }

      height: 100%;
      width: 240px;
      padding-bottom: 16px;

      font-family: var(--crest-custom_font, 'PingFang');
      border-right: 1px solid rgba(31, 35, 41, 0.15);

      .select-ds {
        font-size: 14px;
        font-weight: 500;
        display: flex;
        justify-content: space-between;
        align-items: center;
        color: var(--crestTextPrimary, #1f2329);
        position: relative;

        i {
          cursor: pointer;
          font-size: 12px;
          color: var(--crestTextPlaceholder, rgba(31, 35, 41, 0.15));
        }

        .left-outlined {
          position: absolute;
          font-size: 12px;
          right: -30px;
          top: -5px;
          height: 24px;
          border: 1px solid #dee0e3;
          width: 24px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          background: #fff;
          box-shadow: 0px 5px 10px 0px #1f23291a;
          z-index: 10;
          &:hover {
            .ed-icon {
              color: var(--ed-color-primary, #3b82f6) !important;
            }
          }
        }
      }

      .table-num {
        .num {
          display: flex;
          align-items: center;
          font-weight: 400;
          font-size: 14px;
          color: #646a73;
          .ed-icon {
            margin-right: 5.33px;
          }
        }

        i {
          cursor: auto;
          font-size: 16px;
          color: var(--crestTextPlaceholder, #646a73);
        }
      }

      .search {
        margin: 12px 0;
      }

      .ds-list {
        margin: 12px 0 24px 0;
        width: 100%;
      }

      .dataset-sync-setting {
        padding: 10px 0 14px;
        margin-bottom: 12px;
        border-top: 1px solid #dee0e3;
        border-bottom: 1px solid #dee0e3;

        .sync-row {
          min-height: 28px;
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 8px;
          font-size: 13px;
          color: #1f2329;

          span {
            flex: 0 0 auto;
            color: #646a73;
          }
        }

        .sync-control,
        .sync-now {
          width: 100%;
          margin-top: 8px;
        }

        .sync-now-wrapper {
          display: block;
        }

        .sync-tip {
          margin-top: 6px;
          font-size: 12px;
          line-height: 18px;
          color: #8f959e;
        }

        .sync-summary {
          min-height: 28px;
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 8px;
          margin-top: 8px;
          padding: 0 2px;
          font-size: 12px;
          color: #646a73;

          span:first-child {
            min-width: 0;
            overflow: hidden;
            white-space: nowrap;
            text-overflow: ellipsis;
            color: #1f2329;
          }

          span:last-child {
            flex: 0 0 auto;
          }
        }

        .sync-options {
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 8px;
          margin-top: 8px;
        }

        .sync-option {
          min-width: 0;

          > span {
            display: block;
            margin-bottom: 4px;
            font-size: 12px;
            color: #646a73;
          }

          .ed-input-number {
            width: 100%;
          }
        }

        .sync-check {
          margin-top: 8px;
        }

        .sync-status,
        .sync-log {
          display: flex;
          gap: 8px;
          margin-top: 8px;
          font-size: 12px;
          line-height: 18px;
          color: #646a73;
        }

        .sync-status,
        .sync-log {
          justify-content: space-between;
        }

        .sync-log {
          overflow: hidden;
          white-space: nowrap;
          text-overflow: ellipsis;
          color: #8f959e;
        }

        .sync-cron {
          display: flex;
          gap: 8px;
          margin-top: 8px;

          .ed-input-number {
            width: 92px;
          }

          .sync-unit {
            flex: 1;
          }
        }

        .sync-advanced-toggle {
          height: 24px;
          margin-top: 6px;
          padding: 0;
          font-size: 12px;
        }

        .sync-advanced {
          margin-top: 4px;
          padding-top: 8px;
          border-top: 1px dashed #dee0e3;
        }
      }

      .table-checkbox-list {
        flex: 1;
        min-height: 0;
        overflow-y: auto;
        padding: 0 8px;

        .not-allow {
          cursor: not-allowed;
          color: var(--crestTextDisable, #bbbfc4);
        }
      }
    }
  }

  .dataset-db {
    display: flex;
    .drag-right {
      height: calc(100vh - 56px);
      .different-datasource {
        height: 40px;
        width: 100%;
        background: #ffe7cc;
        color: #1f2329;
        font-size: 14px;
        font-weight: 400;
        line-height: 22px;
        display: flex;
        align-items: center;
        padding: 0 16px;

        .ed-icon {
          font-size: 16px;
          margin-right: 8px;
        }
      }
      .sql-result {
        font-family: var(--crest-custom_font, 'PingFang');
        font-size: 14px;
        overflow-y: auto;
        box-sizing: border-box;
        :deep(.ed-tabs) {
          position: relative;
          z-index: 4;
        }

        .sql-title {
          user-select: none;
          height: 10px;
          position: relative;
          z-index: 5;
          color: var(--crestTextPrimary, #1f2329);

          .field-data {
            position: absolute;
            right: 24px;
            top: 13px;
            width: 50%;
            z-index: 2;
            text-align: right;
          }

          .drag {
            position: absolute;
            top: 4px;
            left: 0;
            height: 7px;
            width: 100%;
            cursor: row-resize;
            &::after {
              content: '';
              height: 7px;
              width: 100px;
              border-radius: 3.5px;
              position: absolute;
              left: 50%;
              top: 0;
              transform: translateX(-50%);
              background: rgba(31, 35, 41, 0.1);
            }
          }
        }

        .padding-24 {
          .border-bottom-tab(24px);
          :deep(.ed-tabs__header::after) {
            display: none;
          }
        }

        .table-preview {
          height: calc(100% - 56px);
          box-sizing: border-box;

          .preview-data {
            float: right;
            height: 100%;
            width: calc(100% - 260px);

            :deep(.ed-table-v2__header-cell) {
              background-color: #f5f6f7 !important;
            }

            :deep(.header-cell) {
              border-top: none;
            }
          }

          .preview-field {
            float: left;
            width: 260px;
            height: 100%;
            position: relative;

            :deep(.ed-tree-node__content) {
              border-radius: 6px;
              &:hover {
                background: rgba(31, 35, 41, 0.1);
              }
            }

            :deep(.ed-tree-node.is-current > .ed-tree-node__content:not(.is-menu):after) {
              display: none;
            }

            .custom-tree-node {
              width: calc(100% - 32px);
              display: flex;
              align-items: center;
              padding-right: 8px;
              box-sizing: content-box;

              .label-tooltip {
                margin-left: 5.33px;
                width: 70%;
                overflow: hidden;
                white-space: nowrap;
                text-overflow: ellipsis;
              }

              .operate {
                margin-left: auto;
                position: relative;
                z-index: 5;
              }
            }

            .field-d,
            .field-q {
              padding: 0 8px;
              position: relative;
              height: 49px;

              &.open {
                height: 50%;
              }
              .title {
                cursor: pointer;
                position: sticky;
                margin: 1px;
                top: 1px;
                height: 49px;
                font-family: var(--crest-custom_font, 'PingFang');
                font-style: normal;
                font-weight: 500;
                font-size: 14px;
                line-height: 22px;
                color: #1f2329;
                display: flex;
                align-items: center;
                z-index: 10;
                background: #fff;

                .add {
                  margin-left: auto;
                }
                i {
                  color: #646a73;
                }

                .expand {
                  font-size: 10px;
                }

                &.expanded {
                  .expand {
                    transform: rotate(90deg);
                  }
                }
              }
              overflow-y: auto;
            }

            .field-d {
              max-height: calc(100% - 50px);
              border-bottom: 1px solid rgba(31, 35, 41, 0.15);
            }
          }
        }
      }
    }
  }
}
.icon-color {
  color: #646a73;
}

.ed-button.is-secondary.is-disabled {
  color: #bbbfc4 !important;
  border-color: #bbbfc4 !important;
}

.father .child {
  visibility: hidden;
}

.father:hover .child {
  visibility: visible;
}

.manage-container {
  padding: 12px 24px 0;
  flex: 1;
  overflow: auto;
}

.style-collapse {
  :deep(.ed-collapse-item__header),
  :deep(.ed-collapse-item__wrap) {
    border-bottom: none !important;
  }
  :deep(.ed-collapse-item__content) {
    padding: 0 !important;
  }

  &.data-tab-collapse {
    border-bottom: none;
    border-top: 1px solid var(--ed-collapse-border-color);

    :deep(.ed-collapse-item.ed-collapse--dark .ed-collapse-item__wrap) {
      background-color: #1a1a1a;
    }

    :deep(.ed-collapse-item__wrap) {
      border-top: none !important;
    }
    :deep(.ed-collapse-item__content) {
      padding: 0 !important;
      border-top: none !important;
    }
    :deep(.ed-collapse-item__header) {
      background-color: transparent;
      border-bottom: none !important;
    }
  }
}

.column-style {
  display: flex;
  align-items: center;
}

.select-svg-icon {
  position: absolute;
  left: 24px;
  top: 50%;
  height: 14px;
  transform: translateY(-50%);
  line-height: 14px;
}

.batch-operate {
  width: 100%;
  height: 64px;
  padding: 0 24px;
  z-index: 2;
  box-shadow: 0px -2px 4px rgba(31, 35, 41, 0.08);

  .select-svg-icon {
    left: 11px;
  }

  .flex-align-center {
    white-space: nowrap;
    .num {
      margin: 0 4px;
    }
    .is-text {
      margin-left: 16px;
    }
  }

  .cascader-batch {
    position: relative;
    margin-left: 30%;
    width: 176px;
    display: flex;
    align-items: center;
  }

  .tip-icon {
    color: #f54a45;
  }
}

.batch-area {
  display: flex;
  flex-direction: column;
  height: calc(100% - 55px);
}

.dimension-manage-header {
  :deep(.ed-collapse-item__header) {
    background: #ebf1ff;
  }
}
.quota-manage-header {
  :deep(.ed-collapse-item__header) {
    background: #e6f7f5;
  }
}
.manage-header {
  :deep(.ed-collapse-item__header) {
    height: 30px;
  }
  :deep(.ed-table th.ed-table__cell) {
    background: #f5f6f7;
  }
}
</style>

<style lang="less">
.cascader-panel {
  .ed-scrollbar__wrap {
    height: 210px !important;
  }
  .ed-cascader-node__label {
    display: flex;
    align-items: center;
    .ed-icon {
      margin-right: 5px;
    }
  }
}
.select-type {
  .ed-input__wrapper {
    padding-left: 32px;
  }
}
.green-color {
  color: #04b49c;
}
.ed-select-dropdown__item {
  display: flex;
  align-items: center;
  .ed-icon {
    font-size: 14px;
    margin-right: 5.25px;
  }
}
.tree-select-ds_popper {
  .ed-tree-node.is-current > .ed-tree-node__content:not(.is-menu):after {
    display: none !important;
  }

  .flex-align-center {
    padding-right: 15px;
  }
}
.calc-field-edit-dialog {
  .ed-dialog__footer {
    padding-top: 24px;
    border: 1px solid rgba(31, 35, 41, 0.15);
  }
}
.group-fields_dialog {
  .group-fields_item {
    padding: 16px;
    background: #f5f6f7;
    border-radius: 6px;
    display: flex;

    & + .group-fields_item {
      margin-top: 8px;
    }

    .form-dynamic {
      display: flex;
      width: 100%;
      align-items: center;
      .ed-form-item {
        margin: 0;
      }
      &:has(.is-error) {
        .ed-form-item {
          margin-bottom: 24px;
        }
      }
    }

    .variable_del {
      color: #646a73;
      margin-left: 4px;
      margin-right: -4px;

      .ed-icon {
        font-size: 16px;
      }

      &:hover {
        background: rgba(31, 35, 41, 0.1) !important;
      }
      &:focus {
        background: rgba(31, 35, 41, 0.1) !important;
      }
      &:active {
        background: rgba(31, 35, 41, 0.2) !important;
      }
    }

    .group-fields_num {
      flex: 1;
      display: flex;
      gap: 8px;
      margin-left: 24px;
      .name {
        white-space: nowrap;
      }
    }
  }

  .line {
    background: #1f232926;
    height: 1px;
    width: 100%;
    margin-bottom: 16px;
    margin-top: 8px;
  }

  .grouping_settings_label:after {
    content: '*';
    color: var(--ed-color-danger);
    margin-left: 2px;
    font-family: var(--crest-custom_font, 'PingFang');
    font-size: 14px;
    font-style: normal;
    font-weight: 400;
  }
}
</style>
