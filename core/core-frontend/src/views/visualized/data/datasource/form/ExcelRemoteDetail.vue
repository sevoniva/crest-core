<script lang="tsx" setup>
import icon_calendar_outlined from '@/assets/svg/icon_calendar_outlined.svg'
import {
  ref,
  reactive,
  h,
  toRefs,
  computed,
  nextTick,
  watch,
  shallowRef,
  onMounted,
  onBeforeUnmount
} from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { type Action, ElIcon, FormInstance, FormRules } from 'element-plus-secondary'
import { cloneDeep, debounce } from 'lodash-es'
import { iconFieldMap } from '@/components/icon-group/field-list'
import { Icon } from '@/components/icon-custom'
import { ElForm, ElMessage, ElMessageBox } from 'element-plus-secondary'
import Cron from '@/components/cron/src/Cron.vue'
import SheetTabs from '@/views/visualized/data/datasource/SheetTabs.vue'
import { loadRemoteFile, save, update } from '@/api/datasource'
import { Base64 } from 'js-base64'
import { useEmitt } from '@/hooks/web/useEmitt'
import { CustomPassword } from '@/components/custom-password'
import { fieldType as fieldTypeLowercase } from '@/utils/attr'
const { t } = useI18n()
export interface Param {
  editType: number
  pid?: string
  type?: string
  id?: string
  name?: string
  creator?: string
  isPlugin?: boolean
  staticMap?: any
}

export interface Field {
  accuracy: number
  originName: string
  fieldSize: number
  fieldType: string
  name: string
  extractedFieldType: number
  checked: boolean
  primaryKey: boolean
  length: number
}

// 远程 Excel 数据源表单的外部输入属性
const props = defineProps({
  form: {
    required: false,
    default() {
      return reactive<{
        id: string
        name: string
        desc: string
        type: string
        editType: number
      }>({
        id: '0',
        name: '',
        desc: '',
        type: 'Excel',
        editType: 0
      })
    },
    type: Object
  },
  isSupportSetKey: {
    type: Boolean,
    required: true
  },
  activeStep: {
    required: false,
    default: 1,
    type: Number
  }
})

const { form, isSupportSetKey, activeStep } = toRefs(props)
// 单个工作表的默认状态，用于切换和清空预览
const defaultSheetObj = {
  tableName: ' ',
  sheetExcelId: '',
  fields: [],
  jsonArray: [],
  sheet: true,
  inspectionStatus: 'NORMAL',
  inspectionMessage: '',
  nameExist: false,
  empty: '',
  overLength: false
}
// 当前选中工作表的字段、预览数据和检测状态
const sheetObj = reactive(cloneDeep(defaultSheetObj))
// 远程文件加载后的整体 Excel 状态
const state = reactive({
  excelData: [],
  defaultExpandedKeys: [],
  defaultCheckedKeys: [],
  fileList: null,
  sheets: []
})

// 当前工作表区域展示模式：预览或字段选择
const currentMode = ref('preview')
// 远程 Excel 基础配置表单引用
const remoteExcelForm = ref<FormInstance>()
// 远程文件加载过程状态
const uploading = ref(false)
// 当前工作表已选中的字段列
const multipleSelection = shallowRef([])
// 表格自适应刷新开关，用于窗口尺寸变化后重建表格
const isResize = ref(true)
// 标记字段表格正在初始化勾选状态，避免触发选择回写
const initMultipleTable = ref(false)
// 字段选择表格引用
const multipleTable = ref()
const { emitter } = useEmitt()
// 当前工作表的字段列配置
const columns = shallowRef([])
// 工作表页签列表
const tabList = shallowRef([])
// 保存或加载远程 Excel 的加载状态
const loading = ref(false)
// Cron 表达式编辑状态
const cronEdit = ref(true)
// 远程 Excel 基础信息表单默认校验规则
const defaultRule = {
  name: [
    {
      required: true,
      message: t('sync_datasource.input_ds_name'),
      trigger: 'blur'
    },
    {
      min: 1,
      max: 64,
      message: t('datasource.input_limit_1_64', [1, 64]),
      trigger: 'blur'
    }
  ],
  'configuration.url': [
    {
      required: true,
      message: t('datasource.remote_excel_url_empty'),
      trigger: 'blur'
    }
  ]
}

// 当前表单校验规则，部分数据源类型切换时会重置
const rule = ref<FormRules>(cloneDeep(defaultRule))
// 当前选中的工作表页签
const activeTab = ref('')
// 当前工作表是否被检测为不可导入
const currentSheetSkipped = computed(() => sheetObj.sheet === false)
// 当前工作表不可导入时的提示文案
const sheetInspectionMessage = computed(
  () => sheetObj.inspectionMessage || '该工作表未检测到可导入的数据表。'
)
let time
// 初始化远程 Excel 表单配置和默认同步策略
const initForm = type => {
  form.value.configuration = {
    url: '',
    userName: '',
    passwd: ''
  }
  form.value.syncSetting = {
    updateType: 'all_scope',
    syncRate: 'SIMPLE_CRON',
    simpleCronValue: '1',
    simpleCronType: 'minute',
    startTime: '',
    endTime: '',
    endLimit: '0',
    cron: '0 0/1 * * * ? *'
  }
  form.value.type = type

  time = setTimeout(() => {
    clearTimeout(time)
    remoteExcelForm.value.clearValidate()
  }, 0)
}

// 窗口尺寸变化后延迟重建表格，避免虚拟表格尺寸异常
const handleResize = debounce(() => {
  isResize.value = false
  nextTick(() => {
    isResize.value = true
  })
}, 500)

// 监听页面尺寸变化，驱动预览表格重新计算
onMounted(() => {
  window.addEventListener('resize', handleResize)
})

// 组件卸载时移除窗口尺寸监听
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})

// 数据源类型变化时恢复远程 Excel 的基础校验规则
watch(
  () => form.value.type,
  val => {
    if (!val.startsWith('API')) {
      rule.value = cloneDeep(defaultRule)
    }
  },
  {
    immediate: true
  }
)

// 步骤变化时同步 Cron 编辑区显示状态
watch(
  () => activeStep.value,
  () => {
    showCron.value = form.value.syncSetting?.syncRate === 'CRON'
  }
)

// 暴露基础配置表单校验函数给父组件
const submitForm = () => {
  remoteExcelForm.value.clearValidate()
  return remoteExcelForm.value.validate
}

// 暴露同步配置表单校验函数给父组件
const submitSyncSettingForm = () => {
  dsApiForm.value.clearValidate()
  return dsApiForm.value.validate
}

// 校验至少存在一个可导入工作表，并检查字段和主键长度配置
const validateExcel = () => {
  let selectedSheet = []
  let sheetFileMd5 = []
  let selectNode = state.excelData[0]?.sheets
  if (selectNode === undefined) {
    ElMessage({
      message: t('datasource.file_not_empty'),
      type: 'error'
    })
    return false
  }
  for (let i = 0; i < selectNode.length; i++) {
    if (selectNode[i].sheet) {
      if (selectNode[i].fields.filter(field => field.checked).length == 0) {
        ElMessage({
          message: selectNode[i].excelLabel + t('datasource.api_field_not_empty'),
          type: 'error'
        })
        return false
      }
      for (let j = 0; j < selectNode[i].fields.length; j++) {
        if (
          selectNode[i].fields[j].checked &&
          selectNode[i].fields[j].primaryKey &&
          !selectNode[i].fields[j].length &&
          selectNode[i].fields[j].extractedFieldType === 0
        ) {
          ElMessage({
            message:
              t('datasource.primary_key_length') +
              selectNode[i].excelLabel +
              ': ' +
              selectNode[i].fields[j].name,
            type: 'error'
          })
          return false
        }
      }
      selectedSheet.push(selectNode[i])
      sheetFileMd5.push(selectNode[i].fieldsMd5)
    }
  }
  if (!selectedSheet.length) {
    ElMessage({
      message: '请选择至少一个可导入工作表',
      type: 'error'
    })
    return false
  }
  return true
}

// 清空远程文件解析结果并重置当前工作表状态
const clearForm = () => {
  state.excelData = []
  activeTab.value = ''
  tabList.value = []
  Object.assign(sheetObj, cloneDeep(defaultSheetObj))
  return remoteExcelForm.value.clearValidate()
}

// 重置基础配置表单字段
const resetForm = () => {
  remoteExcelForm.value.resetFields()
}

// Cron 表达式编辑器显示状态
const showCron = ref(false)

// 切换同步频率时重置相关时间和 Cron 配置
const onRateChange = () => {
  if (form.value.syncSetting.syncRate === 'SIMPLE') {
    form.value.syncSetting.endLimit = 0
    form.value.syncSetting.endTime = 0
    form.value.syncSetting.cron = ''
  }
  if (form.value.syncSetting.syncRate === 'SIMPLE_CRON') {
    form.value.syncSetting.cron = '0 0/1 * * * ? *'
    form.value.syncSetting.simpleCronType = 'minute'
  }
  if (form.value.syncSetting.syncRate === 'CRON') {
    form.value.syncSetting.cron = '00 00 * ? * * *'
  }
  nextTick(() => {
    showCron.value = form.value.syncSetting.syncRate === 'CRON'
  })
}

// 根据简单周期类型生成对应 Cron 表达式，并限制取值范围
const onSimpleCronChange = () => {
  if (form.value.syncSetting.simpleCronType === 'minute') {
    if (form.value.syncSetting.simpleCronValue < 1 || form.value.syncSetting.simpleCronValue > 59) {
      ElMessage.warning(t('cron.minute_limit'))
      form.value.syncSetting.simpleCronValue = 59
    }
    form.value.syncSetting.cron = '0 0/' + form.value.syncSetting.simpleCronValue + ' * * * ? *'
    return
  }
  if (form.value.syncSetting.simpleCronType === 'hour') {
    if (form.value.syncSetting.simpleCronValue < 1 || form.value.syncSetting.simpleCronValue > 23) {
      ElMessage.warning(t('cron.hour_limit'))
      form.value.syncSetting.simpleCronValue = 23
    }
    form.value.syncSetting.cron = '0 0 0/' + form.value.syncSetting.simpleCronValue + ' * * ? *'
    return
  }
  if (form.value.syncSetting.simpleCronType === 'day') {
    if (form.value.syncSetting.simpleCronValue < 1 || form.value.syncSetting.simpleCronValue > 31) {
      ElMessage.warning(t('cron.day_limit'))
      form.value.syncSetting.simpleCronValue = 31
    }
    form.value.syncSetting.cron = '0 0 0 1/' + form.value.syncSetting.simpleCronValue + ' * ? *'
    return
  }
}

// 同步设置表单引用
const dsApiForm = ref()
// 同步设置表单校验规则
const apiRule = {
  'syncSetting.updateType': [
    {
      required: true,
      message: t('datasource.update_type'),
      trigger: 'change'
    }
  ],
  'syncSetting.syncRate': [
    {
      required: true,
      message: t('datasource.sync_rate'),
      trigger: 'change'
    }
  ],
  'syncSetting.simpleCronValue': [
    {
      required: true,
      message: t('auth.set_rules'),
      trigger: 'change'
    }
  ],
  'syncSetting.cron': [
    {
      required: true,
      message: t('common.cron_exp'),
      trigger: 'change'
    }
  ],
  'syncSetting.startTime': [
    {
      required: true,
      message: t('datasource.start_time'),
      trigger: 'change'
    }
  ]
}

// 切换工作表页签时刷新当前工作表字段和预览
const handleTabClick = tab => {
  activeTab.value = tab.value
  const sheet = state.excelData[0]?.sheets.find(ele => ele.sheetId === tab.value)
  handleNodeClick(sheet)
}

// 选中工作表后生成预览列和字段选择状态
const handleNodeClick = data => {
  Object.assign(sheetObj, data || cloneDeep(defaultSheetObj))
  if (!data?.sheet) {
    // 不可导入工作表只展示检测信息，不进入字段选择流程。
    columns.value = []
    multipleSelection.value = []
    currentMode.value = 'preview'
    return
  }
  columns.value = generateColumns(data.fields)
  multipleSelection.value = columns.value.filter(item => item.checked)
  currentMode.value = 'preview'
}
// 后端字段类型到前端图标类型的映射
const fieldType = {
  TEXT: 'text',
  DATETIME: 'time',
  LONG: 'value',
  DOUBLE: 'value'
}

// 根据远程 Excel 字段生成虚拟表格列配置
const generateColumns = (arr: Field[]) =>
  arr.map(ele => ({
    key: ele.originName,
    fieldType: ele.fieldType,
    extractedFieldType: ele.extractedFieldType,
    dataKey: ele.originName,
    title: ele.name,
    checked: ele.checked,
    primaryKey: ele.primaryKey,
    length: ele.length,
    width: 150,
    headerCellRenderer: ({ column }) => (
      <div class="flex-align-center icon">
        <ElIcon>
          <Icon>
            {h(iconFieldMap[fieldType[column.fieldType]], {
              class: `svg-icon field-icon-${fieldType[column.fieldType]}`
            })}
          </Icon>
        </ElIcon>
        <span class="ellipsis" title={column.title} style={{ width: '100px' }}>
          {column.title}
        </span>
      </div>
    )
  }))

// 校验基础配置后加载远程 Excel 文件并解析工作表结构
const loadData = () => {
  remoteExcelForm.value.validate(val => {
    if (val) {
      const request = JSON.parse(JSON.stringify(form.value.configuration))
      // 远程文件凭证只在加载接口中使用，提交前编码避免明文出现在请求体日志中。
      request.userName = Base64.encode(request.userName)
      request.passwd = Base64.encode(request.passwd)
      request.datasourceId = form.value.id || 0
      request.editType = form.value.editType
      loading.value = true
      return loadRemoteFile(request)
        .then(res => {
          loading.value = false
          uploadSuccess(res)
          loading.value = false
        })
        .catch(error => {
          state.excelData = []
          activeTab.value = ''
          tabList.value = []
          Object.assign(sheetObj, cloneDeep(defaultSheetObj))
          if (error.code === 'ECONNABORTED') {
            ElMessage({
              type: 'error',
              message: error.message,
              showClose: true
            })
          }
          loading.value = false
        })
    }
  })
}

// 处理远程 Excel 解析结果，初始化工作表页签和当前工作表
const uploadSuccess = response => {
  if (!response) {
    // 解析失败时必须清空旧工作表，避免用户误保存上一次加载结果。
    state.excelData = []
    activeTab.value = ''
    tabList.value = []
    Object.assign(sheetObj, cloneDeep(defaultSheetObj))
    ElMessage.warning(response.msg)
    return
  }
  if (response?.code !== 0) {
    state.excelData = []
    activeTab.value = ''
    tabList.value = []
    Object.assign(sheetObj, cloneDeep(defaultSheetObj))
    ElMessage.warning(response.msg)
    return
  }
  columns.value = []
  Object.assign(sheetObj, cloneDeep(defaultSheetObj))
  multipleSelection.value = []
  uploading.value = false
  if (!form.value.name) {
    form.value.name = response.data.excelLabel
  }
  tabList.value = response.data.sheets.map(ele => {
    const { sheetId, tableName, newSheet, sheet, inspectionStatus, inspectionMessage } = ele
    return {
      value: sheetId,
      label: tableName,
      newSheet: newSheet,
      sheet,
      inspectionStatus,
      inspectionMessage
    }
  })
  state.excelData = [response.data]
  const [sheet] = tabList.value
  sheet && handleTabClick(sheet)
}

// 切换工作表区域的预览模式和字段选择模式
const changeCurrentMode = val => {
  currentMode.value = val
  if (val === 'select') {
    nextTick(() => {
      // 进入字段选择模式时恢复后端检测结果中的默认勾选状态。
      initMultipleTable.value = true
      for (let i = 0; i < columns.value.length; i++) {
        if (columns.value[i].checked) {
          multipleTable?.value?.toggleRowSelection(columns.value[i], true)
        }
      }
      initMultipleTable.value = false
    })
  } else {
    const sheet = state.excelData[0]?.sheets.find(ele => ele.sheetId === activeTab.value)
    handleNodeClick(sheet)
  }
}

// 字段选择变化时同步列配置和当前工作表字段勾选状态
const handleSelectionChange = val => {
  if (!initMultipleTable.value) {
    multipleSelection.value = val
    multipleSelection.value.forEach(row => {
      row.checked = true
    })
    // 先同步虚拟表格列，再回写原始 sheet 字段，保证预览和保存使用同一份勾选结果。
    columns.value.forEach(row => {
      let item
      for (let i = 0; i < multipleSelection.value.length; i++) {
        if (row.dataKey === multipleSelection.value[i].dataKey) {
          item = multipleSelection.value[i]
        }
      }
      if (item) {
        row.checked = item.checked
      } else {
        row.checked = false
      }
    })

    const sheet = state.excelData[0]?.sheets.find(ele => ele.sheetId === activeTab.value)
    sheet.fields.forEach(row => {
      let item
      for (let i = 0; i < multipleSelection.value.length; i++) {
        if (row.originName === multipleSelection.value[i].dataKey) {
          item = multipleSelection.value[i]
        }
      }
      if (item) {
        row.checked = item.checked
      } else {
        row.checked = false
      }
    })
  }
}

// 更新当前工作表中指定字段的文本长度
const lengthChange = val => {
  const sheet = state.excelData[0]?.sheets.find(ele => ele.sheetId === activeTab.value)
  sheet.fields.forEach(row => {
    if (row.originName === val.dataKey) {
      row.length = val.length
    }
  })
}

// 更新当前工作表中指定字段的主键标记
const primaryKeyChange = val => {
  const sheet = state.excelData[0]?.sheets.find(ele => ele.sheetId === activeTab.value)
  sheet.fields.forEach(row => {
    if (row.originName === val.dataKey) {
      row.primaryKey = val.primaryKey
    }
  })
}

// 判断字段长度输入框是否禁用
const disabledFieldLength = item => {
  if (!item.checked) {
    return true
  }
  if (item.extractedFieldType !== 0) {
    return true
  }
}

// 保存远程 Excel 数据源前组装工作表和同步配置
const saveExcelDs = (params, successCb, finallyCb) => {
  let validate = true
  let selectedSheet = []
  let sheetFileMd5 = []
  let effectExtField = false
  let changeFiled = false
  let selectNode = state.excelData[0]?.sheets
  for (let i = 0; i < selectNode.length; i++) {
    if (selectNode[i].sheet) {
      if (selectNode[i].effectExtField) {
        effectExtField = true
      }
      if (selectNode[i].changeFiled) {
        changeFiled = true
      }
      if (selectNode[i].fields.filter(field => field.checked).length == 0) {
        ElMessage({
          message: selectNode[i].excelLabel + t('datasource.api_field_not_empty'),
          type: 'error'
        })
        finallyCb?.()
        return
      }
      for (let j = 0; j < selectNode[i].fields.length; j++) {
        if (
          selectNode[i].fields[j].checked &&
          selectNode[i].fields[j].primaryKey &&
          !selectNode[i].fields[j].length &&
          selectNode[i].fields[j].extractedFieldType === 0
        ) {
          ElMessage({
            message:
              t('datasource.primary_key_length') +
              selectNode[i].excelLabel +
              ': ' +
              selectNode[i].fields[j].name,
            type: 'error'
          })
          finallyCb?.()
          return
        }
      }
      selectedSheet.push(selectNode[i])
      sheetFileMd5.push(selectNode[i].fieldsMd5)
    }
  }
  if (!selectedSheet.length) {
    ElMessage({
      message: '请选择至少一个可导入工作表',
      type: 'error'
    })
    finallyCb?.()
    return
  }
  if (!validate) {
    finallyCb?.()
    return
  }

  let table: Record<string, any> = {}
  if (params) {
    form.value.name = params.name
  }
  form.value.configuration.sheets = selectedSheet
  if (!props.form.id) {
    table = {
      id: props.form.id,
      name: props.form.name,
      type: 'ExcelRemote',
      configuration: JSON.parse(JSON.stringify(form.value.configuration)),
      editType: 0
    }
  } else {
    table = {
      id: props.form.id,
      name: props.form.name,
      type: 'ExcelRemote',
      configuration: JSON.parse(JSON.stringify(form.value.configuration)),
      editType: props.form.editType ? props.form.editType : 0
    }
  }
  table.syncSetting = form.value.syncSetting
  if (props.form.editType === 0 && props.form.id && (effectExtField || changeFiled)) {
    // 已被数据集引用的字段发生变化时，需要用户确认是否替换下游数据。
    ElMessageBox.confirm(t('datasetUi.replace_the_data'), {
      confirmButtonText: t('dataset.confirm'),
      tip: t('data_source.to_replace_it'),
      cancelButtonText: 'Cancel',
      confirmButtonType: 'primary',
      type: 'warning',
      autofocus: false,
      showClose: false,
      callback: (action: Action) => {
        if (action === 'confirm') {
          saveExcelData(sheetFileMd5, table, params, successCb, finallyCb)
        }
      }
    })
  } else {
    saveExcelData(sheetFileMd5, table, params, successCb, finallyCb)
  }
}

// 字段类型下拉选项
const fieldOptions = [
  { label: t('dataset.text'), value: 0 },
  { label: t('dataset.value'), value: 2 },
  {
    label: t('dataset.value') + '(' + t('dataset.float') + ')',
    value: 3
  }
]

// 切换字段提取类型时同步列配置和工作表字段类型
const extractedFieldTypeChange = item => {
  item.fieldType = item.extractedFieldType
  const sheet = state.excelData[0]?.sheets.find(ele => ele.sheetId === activeTab.value)
  sheet.fields.forEach(row => {
    if (row.originName === item.dataKey) {
      row.extractedFieldType = item.extractedFieldType
      row.fieldType = item.extractedFieldType
      row.fieldType = fieldTypeToStr[item.extractedFieldType]
    }
  })
}

// 前端字段类型值到后端字段类型编码的映射
const fieldTypeToStr = {
  0: 'TEXT',
  2: 'LONG',
  3: 'DOUBLE'
}
// 提交远程 Excel 数据源保存或更新请求
const saveExcelData = (sheetFileMd5, table, params, successCb, finallyCb) => {
  for (let i = 0; i < table.configuration.sheets.length; i++) {
    table.configuration.sheets[i].data = []
    table.configuration.sheets[i].jsonArray = []
  }
  table.configuration = Base64.encode(JSON.stringify(table.configuration))
  table.syncSetting.startTime = new Date(table.syncSetting.startTime).getTime()
  table.syncSetting.endTime = new Date(table.syncSetting.endTime).getTime()
  let method = save
  if (!table.id || table.id === '0') {
    delete table.id
    table.pid = params.pid
  } else {
    method = update
  }
  if (loading.value) return
  loading.value = true
  method(table)
    .then(res => {
      emitter.emit('showFinishPage', res)
      successCb?.()
      ElMessage({
        message: t('commons.save_success'),
        type: 'success'
      })
    })
    .finally(() => {
      finallyCb?.()
      loading.value = false
    })
}

// 暴露给父组件的远程 Excel 表单操作入口
defineExpose({
  submitForm,
  resetForm,
  initForm,
  clearForm,
  submitSyncSettingForm,
  validateExcel,
  saveExcelDs
})
</script>

<template>
  <div class="editor-detail">
    <div class="detail-inner create-dialog">
      <div class="info-update">
        <div :class="activeStep === 1 && 'active'" class="info-text">
          {{ t('data_source.source_configuration_information') }}
        </div>
        <div class="update-info-line"></div>
        <div :class="activeStep === 2 && 'active'" class="update-text">
          {{ t('data_source.data_update_settings') }}
        </div>
      </div>
      <div class="title-form_primary base-info" v-show="activeStep !== 2">
        {{ t('datasource.basic_info') }}
      </div>
      <!-- 基础配置区负责远程文件地址和凭据，加载成功后才进入工作表预览与字段选择。 -->
      <el-form
        ref="remoteExcelForm"
        @submit.prevent
        :model="form"
        :rules="rule"
        label-width="180px"
        label-position="top"
        require-asterisk-position="right"
        v-loading="loading"
        v-show="activeStep !== 2"
      >
        <el-form-item
          :label="t('data_source.data_source_name')"
          prop="name"
          v-show="activeStep !== 2"
        >
          <el-input
            v-model="form.name"
            autocomplete="off"
            :placeholder="t('data_source.data_source_name_placeholder')"
          />
        </el-form-item>
        <el-form-item
          :label="t('datasource.remote_excel_url')"
          prop="configuration.url"
          v-show="activeStep !== 2"
        >
          <el-input
            v-model="form.configuration.url"
            autocomplete="off"
            :placeholder="t('datasource.remote_excel_url_placeholder')"
          />
        </el-form-item>
        <el-form-item :label="t('datasource.username')" v-show="activeStep !== 2">
          <el-input
            v-model="form.configuration.userName"
            autocomplete="off"
            :placeholder="t('datasource.please_input_user_name')"
          />
        </el-form-item>
        <el-form-item :label="t('datasource.password')" v-show="activeStep !== 2">
          <CustomPassword
            :placeholder="t('common.inputText') + t('common.empty') + t('datasource.password')"
            show-password
            type="password"
            v-model="form.configuration.passwd"
          />
        </el-form-item>
        <el-form-item v-show="activeStep !== 2">
          <el-button type="primary" @click="loadData()">
            {{ t('datasource.load_data') }}
          </el-button>
        </el-form-item>
      </el-form>
      <template v-if="activeTab">
        <!-- 工作表预览区展示解析结果，并允许在新建数据源时调整导入字段、主键和字段类型。 -->
        <div class="title-form_primary" v-show="activeStep !== 2">
          {{ t('chart.data_preview') }}
        </div>
        <SheetTabs
          v-show="activeStep !== 2"
          :activeTab="activeTab"
          @tab-click="handleTabClick"
          :tab-list="tabList"
        ></SheetTabs>
        <div class="sheet-inspection-tip" v-if="currentSheetSkipped" v-show="activeStep !== 2">
          {{ sheetInspectionMessage }}
        </div>

        <div
          class="table-select_mode"
          v-if="form.editType === 0 && !currentSheetSkipped"
          v-show="activeStep !== 2"
        >
          <div class="btn-select">
            <el-button
              @click="changeCurrentMode('preview')"
              :class="[currentMode === 'preview' && 'is-active']"
              text
            >
              {{ t('chart.data_preview') }}
            </el-button>
            <el-button
              @click="changeCurrentMode('select')"
              :class="[currentMode === 'select' && 'is-active']"
              text
            >
              {{ t('data_set.field_selection') }}
            </el-button>
          </div>
        </div>
        <div
          class="info-table"
          :class="form.editType === 0 && 'info-table_height'"
          v-if="isResize && !currentSheetSkipped"
          v-show="activeStep !== 2"
        >
          <el-auto-resizer v-if="currentMode === 'preview'">
            <template #default="{ height, width }">
              <el-table-v2
                class="crest-data-table-v2"
                :columns="multipleSelection"
                header-class="excel-header-cell"
                :data="sheetObj.jsonArray"
                :width="width"
                :height="height"
                fixed
              />
            </template>
          </el-auto-resizer>
          <el-table
            class="crest-data-table"
            header-class="header-cell"
            v-else
            ref="multipleTable"
            :data="columns"
            style="width: 100%"
            @selection-change="handleSelectionChange"
          >
            <el-table-column type="selection" width="55" />
            <el-table-column :label="t('data_set.field_name')">
              <template #default="scope">{{ scope.row.title }}</template>
            </el-table-column>

            <el-table-column prop="extractedFieldType" :label="t('datasource.field_type')">
              <template #default="scope">
                <el-select
                  v-model="scope.row.extractedFieldType"
                  class="select-type"
                  style="display: inline-block; width: 120px"
                  @change="extractedFieldTypeChange(scope.row)"
                >
                  <template #prefix>
                    <el-icon>
                      <Icon
                        :className="`field-icon-${
                          fieldTypeLowercase[scope.row.extractedFieldType]
                        }`"
                        ><component
                          class="svg-icon"
                          :class="`field-icon-${fieldTypeLowercase[scope.row.extractedFieldType]}`"
                          :is="iconFieldMap[fieldTypeLowercase[scope.row.extractedFieldType]]"
                        ></component
                      ></Icon>
                    </el-icon>
                  </template>
                  <el-option
                    v-for="item in fieldOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  >
                    <span style="float: left">
                      <el-icon>
                        <Icon :className="`field-icon-${fieldTypeLowercase[item.value]}`"
                          ><component
                            class="svg-icon"
                            :class="`field-icon-${fieldTypeLowercase[item.value]}`"
                            :is="iconFieldMap[fieldTypeLowercase[item.value]]"
                          ></component
                        ></Icon>
                      </el-icon>
                    </span>
                    <span style="float: left; font-size: 12px; color: #8492a6">{{
                      item.label
                    }}</span>
                  </el-option>
                </el-select>
              </template>
            </el-table-column>

            <el-table-column :label="t('data_set.field_type')">
              <template #default="scope">
                <div class="flex-align-center">
                  <el-icon>
                    <Icon>
                      <component
                        :class="`svg-icon field-icon-${fieldType[scope.row.fieldType]}`"
                        :is="iconFieldMap[fieldType[scope.row.fieldType]]"
                      ></component>
                    </Icon>
                  </el-icon>

                  {{ t(`dataset.${fieldType[scope.row.fieldType]}`) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column
              prop="length"
              :label="t('datasource.length')"
              v-if="form.editType === 0"
            >
              <template #default="scope">
                <el-input-number
                  :disabled="disabledFieldLength(scope.row)"
                  v-model="scope.row.length"
                  autocomplete="off"
                  step-strictly
                  class="text-left edit-all-line"
                  :min="1"
                  :max="512"
                  :placeholder="t('common.inputText')"
                  controls-position="right"
                  type="number"
                  @change="lengthChange(scope.row)"
                />
              </template>
            </el-table-column>
            <el-table-column
              prop="primaryKey"
              class-name="checkbox-table"
              :label="t('datasource.set_key')"
              width="100"
              v-if="form.editType === 0 && isSupportSetKey"
            >
              <template #default="scope">
                <el-checkbox
                  :key="scope.row.dataKey"
                  v-model="scope.row.primaryKey"
                  :disabled="!scope.row.checked"
                  @change="primaryKeyChange(scope.row)"
                >
                </el-checkbox>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>
      <!-- 同步设置区在第二步展示，负责增量策略、执行频率和任务有效期配置。 -->
      <el-form
        ref="dsApiForm"
        :model="form"
        style="margin-top: 24px"
        :rules="apiRule"
        label-width="180px"
        label-position="top"
        require-asterisk-position="right"
      >
        <el-form-item
          :label="t('datasource.update_type')"
          prop="syncSetting.updateType"
          v-if="activeStep === 2"
        >
          <el-radio-group v-model="form.syncSetting.updateType">
            <el-radio label="add_scope"> {{ t('data_source.append_data') }}</el-radio>
            <el-radio label="all_scope">{{ t('data_source.replace_data') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          :label="t('datasource.sync_rate')"
          prop="syncSetting.syncRate"
          v-if="activeStep === 2"
        >
          <el-radio-group v-model="form.syncSetting.syncRate" @change="onRateChange">
            <el-radio label="RIGHTNOW">{{ t('data_source.update_now') }}</el-radio>
            <el-radio label="CRON">{{ t('datasource.cron_config') }}</el-radio>
            <el-radio label="SIMPLE_CRON">{{ t('datasource.simple_cron') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <div
          v-if="activeStep === 2 && form.syncSetting.syncRate !== 'RIGHTNOW'"
          class="execute-rate-cont"
        >
          <el-form-item
            :label="t('auth.set_rules')"
            v-if="form.syncSetting.syncRate === 'SIMPLE_CRON'"
            prop="syncSetting.simpleCronValue"
          >
            <div class="simple-cron">
              {{ t('common.every') }}
              <el-input-number
                v-model="form.syncSetting.simpleCronValue"
                controls-position="right"
                :min="1"
                @change="onSimpleCronChange()"
              />
              <el-select
                v-model="form.syncSetting.simpleCronType"
                filterable
                @change="onSimpleCronChange()"
              >
                <el-option :label="t('common.minute')" value="minute" />
                <el-option :label="t('common.hour')" value="hour" />
                <el-option :label="t('common.day')" value="day" />
              </el-select>
              {{ t('data_source.update_once') }}
            </div>
          </el-form-item>
          <el-form-item v-if="form.syncSetting.syncRate === 'CRON'" prop="syncSetting.cron">
            <el-popover :width="834" v-model="cronEdit" trigger="click">
              <template #default>
                <div style="width: 814px; height: 450px; overflow-y: auto">
                  <cron
                    v-if="showCron"
                    v-model="form.syncSetting.cron"
                    :is-rate="form.syncRate === 'CRON'"
                    @close="cronEdit = false"
                  />
                </div>
              </template>
              <template #reference>
                <el-input v-model="form.syncSetting.cron" @click="cronEdit = true" />
              </template>
            </el-popover>
          </el-form-item>
          <el-form-item
            v-if="form.syncSetting.syncRate !== 'RIGHTNOW'"
            :label="t('datasource.start_time')"
            prop="syncSetting.startTime"
          >
            <el-date-picker
              v-model="form.syncSetting.startTime"
              class="crest-date-picker"
              :prefix-icon="icon_calendar_outlined"
              type="datetime"
              :placeholder="t('datasource.start_time')"
            />
          </el-form-item>
          <el-form-item
            v-if="form.syncSetting.syncRate !== 'RIGHTNOW'"
            :label="t('datasource.end_time')"
            prop="syncSetting.endLimit"
          >
            <div style="width: 100%">
              <el-date-picker
                v-model="form.syncSetting.endTime"
                class="crest-date-picker"
                :prefix-icon="icon_calendar_outlined"
                type="datetime"
                :placeholder="t('datasource.end_time')"
              />
            </div>
          </el-form-item>
        </div>
      </el-form>
    </div>
  </div>
</template>

<style lang="less" scoped>
.editor-detail {
  width: 100%;
  display: flex;
  justify-content: center;
  .ed-radio {
    height: 22px;
  }

  .mb16 {
    :deep(.ed-form-item) {
      margin-bottom: 16px;
    }
  }

  .execute-rate-cont {
    border-radius: 6px;
    margin-top: -8px;
  }

  .crest-select {
    width: 100%;
  }
  .ed-input-number {
    width: 100%;
  }

  :deep(.is-controls-right > span) {
    background: #fff;
  }

  .crest-expand {
    font-family: var(--crest-custom_font, 'PingFang');
    font-size: 14px;
    font-weight: 400;
    line-height: 22px;
    color: var(--ed-color-primary);
    cursor: pointer;
    display: inline-flex;
    align-items: center;

    .ed-icon {
      margin-left: 4px;
    }
  }

  :deep(.ed-date-editor.ed-input) {
    .ed-input__wrapper {
      width: 100%;
    }
    width: 100%;
  }
  .simple-cron {
    height: 32px;
    .ed-select,
    .ed-input-number {
      width: 140px;
      margin: 0 8px;
    }
  }
  .detail-inner {
    height: calc(100vh - 280px);
    min-height: 700px;

    .dropdown-icon {
      .down-outlined {
        transform: rotate(180deg);
      }
      &[aria-expanded='true'] {
        .down-outlined {
          transform: rotate(0);
        }
      }
      cursor: pointer;
    }

    .error-status {
      margin-top: 32px;
    }

    .upload-tip {
      color: #8f959e;
      font-family: var(--crest-custom_font, 'PingFang');
      font-size: 14px;
      font-style: normal;
      font-weight: 400;
      line-height: 22px;
    }

    .title-form_primary {
      margin: 16px 0;
      margin-top: 32px;
    }

    .sheet-inspection-tip {
      min-height: 40px;
      display: flex;
      align-items: center;
      padding: 0 16px;
      color: #646a73;
      background: #f5f6f7;
      border: 1px solid rgba(31, 35, 41, 0.12);
      border-top: none;
      font-size: 13px;
    }

    .info-table {
      width: 100%;
      height: calc(100% - 200px);
      &.info-table_height {
        height: calc(100% - 379px);
      }
    }
    width: 800px;
    padding-top: 8px;

    .description-text {
      :deep(.ed-textarea__inner) {
        height: 92px;
      }
    }

    .base-info {
      margin: 24px 0 16px 0;
    }

    .left-api_params {
      border-top-left-radius: 6px;
      border-bottom-left-radius: 6px;
      border: 1px solid #d9dcdf;
      width: 300px;
      padding: 16px;
      .name-copy {
        display: none;
        line-height: 24px;
        margin-left: 4px;
      }

      .list-item_primary:hover {
        .name-copy {
          display: inline;
        }

        .label {
          width: 74% !important;
        }
      }
    }

    .right-api_params {
      border-top-right-radius: 6px;
      border-bottom-right-radius: 6px;
      border: 1px solid #d9dcdf;
      border-left: none;
      width: calc(100% - 200px);
    }

    .table-info-mr {
      margin: 28px 0 12px 0;
      .api-tabs {
        :deep(.ed-tabs__nav-wrap::after) {
          display: none;
        }
      }
    }

    .info-update {
      height: 22px;
      width: 100%;
      display: flex;
      align-items: center;
      font-family: var(--crest-custom_font, 'PingFang');
      font-size: 14px;
      font-style: normal;
      font-weight: 400;
      line-height: 22px;
      justify-content: center;

      .update-info-line {
        width: 208px;
        height: 1px;
        background: #bcbdbf;
        margin: 0 8px;
      }

      .info-text,
      .update-text {
        padding-left: 16px;
        position: relative;
        color: #1f2329;
        font-weight: 400;
        font-family: var(--crest-custom_font, 'PingFang');
        font-size: 14px;
        font-style: normal;
        line-height: 22px;
        &::before {
          width: 8px;
          height: 8px;
          content: '';
          position: absolute;
          left: 0;
          top: 50%;
          transform: translateY(-50%);
          border: 1px solid var(--ed-color-primary);
          border-radius: 50%;
        }

        &.active {
          font-weight: 500;
        }

        &.active::before {
          border: none;
          background: var(--ed-color-primary);
        }
      }
    }

    .detail-operate {
      text-align: right;
      padding: 8px 0;
    }

    .flex-space {
      display: flex;
      align-items: center;
    }
  }
  .table-select_mode {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: #f5f6f7;
    padding: 16px;
    .btn-select {
      min-width: 164px;
      padding: 0 6px;
      height: 32px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #ffffff;
      border: 1px solid #d9dcdf;
      border-radius: 6px;

      .is-active {
        background: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1));
      }

      .ed-button:not(.is-active) {
        color: #1f2329;
      }
      .ed-button.is-text {
        height: 24px;
        min-width: 74px;
        line-height: 24px;
      }
      .ed-button + .ed-button {
        margin-left: 4px;
      }
    }
  }
  .detail-operate {
    height: 56px;
    padding: 16px 24px;
    font-size: 16px;
    font-weight: 500;
    width: 100%;
    border-bottom: 1px solid rgba(31, 35, 41, 0.15);
  }
}

.api-card-content {
  display: flex;
  flex-wrap: wrap;
  margin-left: -16px;
}
.api-card {
  height: 120px;
  width: 392px;
  border-radius: 6px;
  border: 1px solid var(--crestCardStrokeColor, #dee0e3);
  border-radius: 6px;
  margin: 0 0 16px 16px;
  padding: 16px;
  font-family: var(--crest-custom_font, 'PingFang');
  cursor: pointer;

  &:hover {
    border-color: var(--ed-color-primary);
  }
  .name {
    font-size: 16px;
    font-weight: 500;
    margin-right: 8px;
    max-width: 70%;
  }
  .req-title,
  .req-value {
    display: flex;
    font-size: 14px;
    font-weight: 400;
    :nth-child(1) {
      width: 120px;
    }
    :nth-child(2) {
      margin-left: 24px;
      max-width: 230px;
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
    margin-right: 16px;
    color: var(--crestTextSecondary, #646a73);
  }
  .delete-icon {
    cursor: pointer;
  }
  .crest-tag {
    display: inline-flex;
    justify-content: center;
    align-items: center;
    border-radius: 2px;
    padding: 1px 6px;
    height: 24px;
    font-size: 14px;

    &.invalid {
      color: #646a73;
      background: rgba(31, 35, 41, 0.1);
    }

    &.valid {
      color: green;
      background: rgba(52, 199, 36, 0.2);
    }
  }
}
</style>

<style lang="less">
.api-table-delete {
  padding: 20px 24px !important;
  display: flex;
  flex-wrap: wrap;
  .small {
    height: 28px;
    min-width: 48px !important;
  }

  .icon-warning {
    transform: translateY(3px);
  }

  .tips {
    font-family: var(--crest-custom_font, 'PingFang');
    font-size: 14px;
    font-weight: 500;
    line-height: 22px;
    margin-left: 8.67px;
    color: var(--crestTextPrimary, #1f2329);
  }
  i {
    font-size: 14.666666030883789px;
    color: var(--crestWarning, #ff8800);
    line-height: 22px;
  }
  .foot {
    text-align: right;
    width: 100%;
    margin-top: 16px;
  }
}

.schema-label {
  .ed-form-item__label {
    display: flex !important;
    justify-content: space-between;
    padding-right: 0;
    &::after {
      display: none;
    }
    .name {
      .required::after {
        content: '*';
        color: #f54a45;
        margin-left: 2px;
      }
    }
  }
}
</style>
