<script lang="tsx" setup>
import icon_expandRight_filled from '@/assets/svg/icon_expand-right_filled.svg'
import { nextTick, reactive, ref, shallowRef, provide } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import type { FormInstance, FormRules } from 'element-plus-secondary'
import { ElIcon, ElMessage } from 'element-plus-secondary'
import type { ApiRequest } from './ApiHttpRequestForm.vue'
import ApiHttpRequestForm from './ApiHttpRequestForm.vue'
import { Icon } from '@/components/icon-custom'
import { Base64 } from 'js-base64'
import EmptyBackground from '@/components/empty-background/src/EmptyBackground.vue'
import { checkApiItem } from '@/api/datasource'
import { cloneDeep } from 'lodash-es'
import { fieldType } from '@/utils/attr'
import type { ApiConfiguration } from '@/views/visualized/data/datasource/form/option'
import { cancelMap } from '@/config/axios/service'
import { iconFieldMap } from '@/components/icon-group/field-list'

export interface Field {
  name: string
  length: number
  value: Array<{}>
  checked: boolean
  primaryKey: boolean
  children?: Array<{}>
  extractedFieldType?: number
}

export interface ApiItem {
  status: string
  name: string
  type: string
  appToken: string
  tableId: string
  viewId: string
  displayTableName?: string
  url: string
  copy: boolean
  method: string
  request: ApiRequest
  fields: Field[]
  jsonFields: JsonField[]
  useJsonPath: boolean
  apiQueryTimeout: number
  showApiStructure: boolean
  jsonPath: string
  serialNumber: number
}

export interface JsonField {
  fieldType: number
  size: number
  children: null
  name: string
  checked: false
  primaryKey: false
  length: string
  extField: number
  jsonPath: string
  type: string
  originName: string
  extractedFieldType: number
}
const { t } = useI18n()

// 保存接口结构校验返回的原始字段信息，供结构预览区展示
const originFieldItem = reactive({
  jsonFields: [],
  fields: []
})

// 当前数据源下已有的接口表配置列表
let apiItemList = reactive<ApiConfiguration[]>([])
// 当前数据源下已有的接口参数配置列表
let paramsList = reactive<ApiConfiguration[]>([])
// 编辑前的字段列表快照，用于判断主键变更
let fields = reactive<Field[]>([])

// 当前正在新增或编辑的接口配置主体
let apiItem = reactive<ApiItem>({
  status: '',
  name: '',
  type: 'table',
  appToken: '',
  tableId: '',
  viewId: '',
  url: '',
  copy: false,
  method: 'GET',
  request: {
    changeId: '',
    rest: [],
    headers: [],
    arguments: [],
    body: {
      typeChange: '',
      raw: '',
      kvs: []
    },
    authManager: {
      verification: '',
      username: '',
      password: ''
    },
    page: {
      pageType: 'empty',
      requestData: [],
      responseData: []
    }
  },
  fields: [],
  jsonFields: [],
  useJsonPath: false,
  apiQueryTimeout: 10,
  showApiStructure: false,
  jsonPath: '',
  serialNumber: -1
})
// 字段递归提取时收集到的重复字段名
let errMsg = []
// 接口请求参数表单组件引用
const apiItemForm = ref()
// 数据预览为空时的占位展示状态
const showEmpty = ref(false)
// 接口配置抽屉显示状态
const edit_api_item = ref(false)
// 当前步骤条所在步骤
const active = ref(1)
// 接口结构加载状态
const loading = ref(false)
// 表单提交和校验加载状态
const formLoading = ref(false)
// 数据预览表格列配置
const columns = shallowRef([])
// 参数配置中可引用的字段值列表
const valueList = shallowRef([])
// 数据预览表格行数据
const tableData = shallowRef([])
// 内置基础信息表单引用
const apiItemBasicInfo = ref<FormInstance>()
// 插件数据源基础信息表单引用
const pluginApiItemBasicInfo = ref<any>()
// 当前数据源是否支持设置主键
const isSupportSetKey = ref(false)
// 校验接口请求超时时间必须为正整数
const isNumber = (rule, value, callback) => {
  if (!value) {
    callback(new Error(t('datasource.please_input_query_timeout')))
    return
  }
  let isNumber = false
  var reg = /^\d+$/
  isNumber = reg.test(value)
  if (!isNumber) {
    callback(new Error(t('datasource.please_input_query_timeout')))
    return
  }
  if (value <= 0) {
    callback(new Error(t('datasource.please_input_query_timeout')))
    return
  }
  callback()
}
// 接口基础信息表单校验规则
const rule = reactive<FormRules>({
  name: [
    {
      required: true,
      message: t('datasource.input_name'),
      trigger: 'blur'
    },
    {
      min: 2,
      max: 64,
      message: t('datasource.input_limit_2_25', [2, 64]),
      trigger: 'blur'
    }
  ],
  apiQueryTimeout: [
    {
      required: true,
      validator: isNumber,
      trigger: ['blur', 'change']
    }
  ],
  url: [
    {
      required: true,
      message: t('data_source.the_request_address'),
      trigger: 'blur'
    }
  ],
  desc: [
    {
      required: true,
      trigger: 'blur'
    }
  ]
})
// 当前抽屉编辑的是接口表还是接口参数
const activeName = ref('table')
// 标记当前配置是否处于编辑已有项模式
const editItem = ref(false)
// 标记当前接口配置是否来自复制操作
const copyItem = ref(false)
// 当前数据源类型，作为后端校验接口的类型参数
const dsType = ref('API')
// 插件数据源的静态入口标识
const jsName = ref('')
// 当前数据源是否走插件渲染表单
const isPlugin = ref(false)
// 可用插件数据源列表
const pluginDs = ref([])
// 当前插件数据源索引
const pluginIndex = ref('')
// 当前数据源是否来自复制操作
const copyDs = ref(false)
provide('api-active-name', activeName)
// 初始化接口配置抽屉，并根据数据源类型准备内置或插件表单
const initApiItem = (
  val: ApiItem,
  from,
  name,
  edit,
  supportSetKey,
  pluginDsList,
  indexPlugin,
  isPluginDs
) => {
  pluginDs.value = pluginDsList
  pluginIndex.value = indexPlugin
  if (!isPluginDs) {
    const arr = pluginDs.value.filter(ele => {
      return ele.type === from.type
    })
    if (arr && arr.length > 0) {
      isPlugin.value = true
    }
  } else {
    isPlugin.value = isPluginDs
  }

  copyItem.value = val.copy
  copyDs.value = from.copy
  dsType.value = from.type
  isSupportSetKey.value = supportSetKey
  activeName.value = name
  editItem.value = edit
  apiItemList = from.apiConfiguration
  fields = val.fields
  if (from.paramsConfiguration) {
    paramsList = from.paramsConfiguration
  }
  if (isPlugin.value) {
    jsName.value = getPluginStatic()
  }
  valueList.value = []
  if (paramsList) {
    for (let i = 0; i < paramsList.length; i++) {
      valueList.value = valueList.value.concat(paramsList[i].fields)
    }
  }
  Object.assign(apiItem, val)
  edit_api_item.value = true
  active.value = 0
  nextTick(() => {
    if (isPlugin.value) {
      pluginApiItemBasicInfo?.value?.invokeMethod({
        methodName: 'clearForm',
        args: []
      })
      pluginApiItemBasicInfo?.value?.invokeMethod({
        methodName: 'initForm',
        args: []
      })
    } else {
      apiItemBasicInfo.value.clearValidate()
    }
  })
}

// 拉取接口结构信息，用于展示原始响应字段树
const showApiData = () => {
  apiItemBasicInfo.value.validate(valid => {
    if (valid) {
      const data = Base64.encode(JSON.stringify(apiItem))
      const params = Base64.encode(JSON.stringify(paramsList))
      loading.value = true
      cancelMap['/datasource/api-data-source-check']?.()
      checkApiItem({ dsType: dsType.value, data: data, type: 'apiStructure', paramsList: params })
        .then(response => {
          originFieldItem.jsonFields = response.data.jsonFields
        })
        .catch(error => {
          console.warn(error?.message)
        })
      loading.value = false
    }
  })
}

// 接口请求方法选项
const reqOptions = [
  { id: 'GET', label: 'GET' },
  { id: 'POST', label: 'POST' }
]

// 是否使用 JSONPath 解析接口响应
const isUseJsonPath = [
  { id: true, label: t('common.yes') },
  { id: false, label: t('common.no') }
]

// 字段类型选项，供接口字段类型调整使用
const fieldOptions = [
  { label: t('dataset.text'), value: 0 },
  { label: t('dataset.value'), value: 2 },
  {
    label: t('dataset.value') + '(' + t('dataset.float') + ')',
    value: 3
  }
]
// 下一步按钮禁用状态，防止接口校验请求重复提交
const disabledNext = ref(false)
// 保存当前接口配置，校验字段、参数和主键信息后回传父组件
const saveItem = () => {
  if (apiItem.type !== 'params' && apiItem.fields.length === 0) {
    ElMessage.error(t('datasource.api_field_not_empty'))
    return
  }
  if (apiItem.type === 'params') {
    for (let i = 0; i < apiItem.fields.length; i++) {
      for (let j = 0; j < paramsList.length; j++) {
        for (let k = 0; k < paramsList[j].fields.length; k++) {
          if (
            apiItem.fields[i].name === paramsList[j].fields[k].name &&
            apiItem.serialNumber !== paramsList[j].serialNumber
          ) {
            ElMessage.error(t('data_source.name_already_exists') + apiItem.fields[i].name)
            return
          }
        }
      }
    }
  }

  for (let i = 0; i < apiItem.fields.length - 1; i++) {
    for (let j = i + 1; j < apiItem.fields.length; j++) {
      if (apiItem.fields[i].name === apiItem.fields[j].name) {
        ElMessage.error(apiItem.fields[i].name + ', ' + t('datasource.has_repeat_field_name'))
        return
      }
    }
  }
  if (editItem.value) {
    let msg = ''
    for (let i = 0; i < apiItem.fields.length; i++) {
      if (apiItem.fields[i].primaryKey) {
        let find = false
        for (let j = 0; j < fields.length; j++) {
          if (fields[j].name === apiItem.fields[i].name && fields[j].primaryKey) {
            find = true
          }
        }
        if (!find) {
          msg = msg + ' ' + apiItem.fields[i].name
        }
      }
    }
    for (let i = 0; i < fields.length; i++) {
      if (fields[i].primaryKey) {
        let find = false
        for (let j = 0; j < apiItem.fields.length; j++) {
          if (fields[i].name === apiItem.fields[j].name && apiItem.fields[j].primaryKey) {
            find = true
          }
        }
        if (!find) {
          msg = msg + ' ' + fields[i].name
        }
      }
    }
    if (msg !== '' && !(copyDs.value || copyItem.value)) {
      ElMessage.error(t('datasource.primary_key_change') + msg)
      return
    }
    for (let i = 0; i < apiItem.fields.length; i++) {
      if (
        apiItem.fields[i].primaryKey &&
        !apiItem.fields[i].length &&
        apiItem.fields[i].extractedFieldType === 0
      ) {
        ElMessage.error(t('datasource.primary_key_length') + apiItem.fields[i].name)
        return
      }
    }
  } else {
    for (let i = 0; i < apiItem.fields.length; i++) {
      if (
        apiItem.fields[i].primaryKey &&
        !apiItem.fields[i].length &&
        apiItem.fields[i].extractedFieldType === 0
      ) {
        ElMessage.error(t('datasource.primary_key_length') + apiItem.fields[i].name)
        return
      }
    }
  }
  returnAPIItem('returnItem', cloneDeep(apiItem))
  if (isPlugin.value) {
    pluginApiItemBasicInfo?.value?.invokeMethod({
      methodName: 'resetForm',
      args: []
    })
  }
  edit_api_item.value = false
}
// 返回上一步
const before = () => {
  active.value -= 1
}

// 下一步入口，插件表单和内置表单分别触发表单校验
const next = () => {
  if (isPlugin.value) {
    pluginApiItemBasicInfo?.value?.invokeMethod({
      methodName: 'submitForm',
      args: [{ eventName: 'stepNext', args: apiItem }]
    })
  } else {
    apiItemBasicInfo.value.validate(val => {
      if (val) {
        stepNext()
      }
    })
  }
}

// 完成基础信息校验后请求后端解析接口字段，并进入字段配置步骤
const stepNext = () => {
  if (apiItem.useJsonPath && !apiItem.jsonPath) {
    ElMessage.error(t('datasource.please_input_dataPath'))
    return
  }
  if (apiItem.type === 'params') {
    for (let i = 0; i < paramsList.length; i++) {
      if (
        paramsList[i].name === apiItem.name &&
        apiItem.serialNumber !== paramsList[i].serialNumber
      ) {
        ElMessage.error(t('data_source.parameter_table_name_exists'))
        return
      }
    }
  } else {
    for (let i = 0; i < apiItemList.length; i++) {
      if (
        apiItemList[i].name === apiItem.name &&
        apiItem.serialNumber !== apiItemList[i].serialNumber
      ) {
        ElMessage.error(t('datasource.has_repeat_name'))
        return
      }
    }
  }
  cancelMap['/datasource/api-data-source-check']?.()
  const params = Base64.encode(JSON.stringify(paramsList))
  disabledNext.value = true
  formLoading.value = true
  checkApiItem({
    dsType: dsType.value,
    data: Base64.encode(JSON.stringify(apiItem)),
    paramsList: params
  })
    .then(response => {
      disabledNext.value = false
      formLoading.value = false
      apiItem.jsonFields = response.data.jsonFields
      apiItem.fields = []
      apiItem.name = response.data.name
      handleFiledChange(apiItem)
      previewData()
      active.value += 1
    })
    .catch(error => {
      disabledNext.value = false
      formLoading.value = false
      console.warn(error?.message)
    })
}
// 校验当前接口配置，插件表单和内置表单使用不同提交流程
const validate = () => {
  if (isPlugin.value) {
    pluginApiItemBasicInfo?.value?.invokeMethod({
      methodName: 'submitForm',
      args: [{ eventName: 'validateItem', args: apiItem }]
    })
  } else {
    apiItemBasicInfo.value.validate(val => {
      if (!val) {
        return
      } else {
        validateItem()
      }
    })
  }
}

// 调用后端接口校验当前配置，并刷新字段树和数据预览
const validateItem = () => {
  if (apiItem.useJsonPath && !apiItem.jsonPath) {
    ElMessage.error(t('datasource.please_input_dataPath'))
    return
  }
  cancelMap['/datasource/api-data-source-check']?.()
  const params = Base64.encode(JSON.stringify(paramsList))
  formLoading.value = true
  checkApiItem({
    dsType: dsType.value,
    data: Base64.encode(JSON.stringify(apiItem)),
    paramsList: params
  })
    .then(response => {
      formLoading.value = false
      apiItem.jsonFields = response.data.jsonFields
      apiItem.fields = []
      apiItem.name = response.data.name
      handleFiledChange(apiItem)
      previewData()
      ElMessage.success(t('datasource.validate_success'))
    })
    .catch(() => {
      formLoading.value = false
      ElMessage.error(t('data_source.verification_failed'))
    })
}

// 接收插件表单提交结果，并分发到校验或下一步流程
const handleSubmit = param => {
  const validateFrom = param.validate
  validateFrom(val => {
    if (val) {
      if (param.eventName === 'validateItem') {
        validateItem()
      } else {
        stepNext()
      }
    }
  })
}

// 关闭抽屉时取消未完成的接口校验请求，并重置插件表单
const closeEditItem = () => {
  cancelMap['/datasource/api-data-source-check']?.()
  if (isPlugin.value) {
    pluginApiItemBasicInfo?.value?.invokeMethod({
      methodName: 'resetForm',
      args: []
    })
  }
  edit_api_item.value = false
}

// 存在子字段的节点不允许作为叶子字段直接操作
const disabledByChildren = item => {
  if (item.hasOwnProperty('children') && item.children.length > 0) {
    return true
  } else {
    return false
  }
}

// 字段长度仅允许文本类叶子字段编辑
const disabledFieldLength = item => {
  if (item.hasOwnProperty('children') && item.children.length > 0) {
    return true
  } else {
    return item.extractedFieldType !== 0
  }
}

// 根据复制、编辑、勾选状态判断主键开关是否禁用
const disabledSetKey = item => {
  if (item.hasOwnProperty('children') && item.children.length > 0) {
    return true
  }
  if (copyItem.value || copyDs.value) {
    return false
  }
  if (editItem.value) {
    return true
  }
  if (!item.checked) {
    return true
  }
  return false
}

// 参数接口或非叶子节点不允许切换提取字段类型
const disabledChangeFieldByChildren = item => {
  if (apiItem.type == 'params') {
    return true
  }
  if (item.hasOwnProperty('children') && item.children.length > 0) {
    return true
  } else {
    return false
  }
}

// 字段类型切换为非文本时清空长度配置
const extractedFieldTypeChange = item => {
  if (item.extractedFieldType !== 0) {
    item.length = ''
  }
}
// 根据已选字段组装数据预览表格的列和行
const previewData = () => {
  showEmpty.value = false
  const data = []
  const columnTmp = []
  let maxPreviewNum = 0
  for (let j = 0; j < apiItem.fields.length; j++) {
    if (apiItem.fields[j].value && apiItem.fields[j].value.length > maxPreviewNum) {
      maxPreviewNum = apiItem.fields[j].value.length
    }
  }
  for (let i = 0; i < maxPreviewNum; i++) {
    data.push({})
  }
  for (let i = 0; i < apiItem.fields.length; i++) {
    for (let j = 0; j < apiItem.fields[i].value.length; j++) {
      data[j][apiItem.fields[i].name] = apiItem.fields[i].value[j]
    }

    columnTmp.push({
      key: apiItem.fields[i].name,
      dataKey: apiItem.fields[i].name,
      title: apiItem.fields[i].name,
      width: 150
    })
  }
  tableData.value = data
  columns.value = columnTmp
  showEmpty.value = apiItem.fields.length === 0
}

// 勾选父节点时递归同步所有子节点的勾选状态
const handleCheckChange = (apiItem, node) => {
  if (node.children?.length) {
    node.children.forEach(item => {
      item.checked = node.checked
      handleCheckChange(apiItem, item)
    })
  }
}

// 从接口响应字段树中提取所有已勾选的叶子字段
const handleFiledChange = apiItem => {
  for (var i = 0; i < apiItem.jsonFields.length; i++) {
    if (apiItem.jsonFields[i].checked && apiItem.jsonFields[i].children === undefined) {
      apiItem.fields.push(apiItem.jsonFields[i])
    }
    if (apiItem.jsonFields[i].children !== undefined) {
      handleFiledChange2(apiItem, apiItem.jsonFields[i].children)
    }
  }
}
// 递归处理子字段，遇到重名字段时取消勾选并记录提示信息
const handleFiledChange2 = (apiItem, jsonFields) => {
  for (var i = 0; i < jsonFields.length; i++) {
    if (jsonFields[i].checked && jsonFields[i].children === undefined) {
      for (var j = 0; j < apiItem.fields.length; j++) {
        if (apiItem.fields[j].name === jsonFields[i].name) {
          jsonFields[i].checked = false
          nextTick(() => {
            jsonFields[i].checked = false
          })
          errMsg.push(jsonFields[i].name)
        }
      }
      apiItem.fields.push(jsonFields[i])
    }
    if (jsonFields[i].children?.length) {
      handleFiledChange2(apiItem, jsonFields[i].children)
    }
  }
}

// 处理字段树节点勾选变化，并刷新字段列表和数据预览
const handleCheckAllChange = row => {
  errMsg = []
  handleCheckChange(apiItem, row)
  apiItem.fields = []
  handleFiledChange(apiItem)
  previewData()

  if (errMsg.length) {
    ElMessage.error([...new Set(errMsg)].join(',') + ', ' + t('datasource.has_repeat_field_name'))
  }
}
// 切换请求体类型时同步到接口请求配置
const changeId = (val: string) => {
  apiItem.request.body.typeChange = val
}

// 字段配置面板展开状态
const activeColumnInfo = ref(true)
// 数据预览面板展开状态
const activeDataPreview = ref(true)

// 获取当前插件数据源的静态入口索引
const getPluginStatic = () => {
  const arr = pluginDs.value.filter(ele => {
    return ele.type === dsType.value
  })
  return pluginIndex.value
    ? pluginIndex.value
    : arr && arr.length > 0
    ? arr[0].staticMap?.index
    : null
}

// 向父组件回传接口配置
const returnAPIItem = defineEmits(['returnItem'])

// 暴露初始化方法，供父级抽屉控制入口调用
defineExpose({
  initApiItem
})
</script>

<template>
  <el-drawer
    :title="
      activeName === 'table' ? t('datasource.data_table') : t('data_source.interface_parameters')
    "
    v-model="edit_api_item"
    modal-class="api-datasource-drawer"
    size="1000px"
    :before-close="closeEditItem"
    direction="rtl"
  >
    <div style="display: flex; width: 100%; justify-content: center">
      <el-steps custom style="max-width: 400px; flex: 1" :active="active" align-center>
        <el-step>
          <template #title>
            {{ t('datasource.api_step_1') }}
          </template>
        </el-step>
        <el-step>
          <template #title>
            {{
              activeName === 'table'
                ? t('datasource.api_step_2')
                : t('data_source.extract_parameters')
            }}
          </template>
        </el-step>
      </el-steps>
    </div>

    <el-row v-show="active === 0 && dsType === 'API'">
      <el-form
        ref="apiItemBasicInfo"
        :model="apiItem"
        label-position="top"
        label-width="100px"
        require-asterisk-position="right"
        :rules="rule"
        v-loading="formLoading"
      >
        <div class="title-form_primary base-info">
          <span>{{ t('datasource.base_info') }}</span>
        </div>
        <el-form-item :label="t('common.name')" prop="name">
          <el-input
            v-model="apiItem.name"
            :placeholder="t('common.input_name')"
            autocomplete="off"
          />
        </el-form-item>

        <el-form-item :label="t('datasource.request')" prop="url">
          <el-input
            v-model="apiItem.url"
            :placeholder="t('datasource.path_all_info')"
            class="input-with-select"
          >
            <template #prepend>
              <el-select v-model="apiItem.method">
                <el-option
                  v-for="item in reqOptions"
                  :key="item.id"
                  :label="item.label"
                  :value="item.id"
                />
              </el-select>
            </template>
          </el-input>
        </el-form-item>

        <div v-loading="loading">
          <div class="title-form_primary request-info">
            <span>{{ t('datasource.req_param') }}</span>
          </div>
          <!-- HTTP 请求参数 -->
          <el-form-item class="line-height_18">
            <api-http-request-form
              v-if="edit_api_item"
              :request="apiItem.request"
              :value-list="valueList"
              @changeId="changeId"
            />
          </el-form-item>
        </div>
        <el-form-item :label="$t('datasource.query_timeout')" prop="apiQueryTimeout">
          <el-input v-model="apiItem.apiQueryTimeout" autocomplete="off" type="number" :min="0">
            <template v-slot:append>{{ $t('chart.second') }}</template>
          </el-input>
        </el-form-item>
        <div class="title-form_primary request-info">
          <span>{{ t('datasource.isUseJsonPath') }}</span>
        </div>
        <el-form-item>
          <el-input
            v-model="apiItem.jsonPath"
            :placeholder="t('datasource.jsonpath_info')"
            class="input-with-select"
          >
            <template #prepend>
              <el-select v-model="apiItem.useJsonPath" style="width: 100px">
                <el-option
                  v-for="item in isUseJsonPath"
                  :key="item.label"
                  :label="item.label"
                  :value="item.id"
                />
              </el-select>
            </template>
            <template #append>
              <el-button @click="showApiData"
                >{{ t('data_source.view_data_structure') }}
              </el-button>
            </template>
          </el-input>
        </el-form-item>

        <div class="title-form_primary request-info" v-show="apiItem.useJsonPath">
          <span>{{ t('datasource.column_info') }}</span>
        </div>
        <div class="table-container crest-svg-in-table" v-show="apiItem.useJsonPath">
          <el-table
            class="crest-data-table"
            :data="originFieldItem.jsonFields"
            style="width: 100%"
            row-key="jsonPath"
          >
            <el-table-column
              class-name="checkbox-table"
              prop="originName"
              :label="t('datasource.parse_filed')"
              show-overflow-tooltip
            >
              <template #default="scope">
                {{ scope.row.originName }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-form>
    </el-row>
    <el-row v-show="active === 0 && dsType !== 'API'"> </el-row>
    <el-row v-show="active === 1">
      <el-form
        style="width: 100%"
        ref="apiItemForm"
        :model="apiItem"
        label-position="top"
        label-width="100px"
        :rules="rule"
      >
        <p
          :class="[activeColumnInfo ? 'active' : 'deactivate', 'column-info-title']"
          @click="activeColumnInfo = !activeColumnInfo"
        >
          <el-icon style="font-size: 10px">
            <Icon name="icon_expand-right_filled"
              ><icon_expandRight_filled class="svg-icon"
            /></Icon>
          </el-icon>
          <span class="name">{{ t('datasource.column_info') }}</span>
        </p>
        <div v-show="activeColumnInfo" class="crest-svg-in-table">
          <el-table
            class="crest-data-table"
            header-cell-class-name="header-cell"
            :data="apiItem.jsonFields"
            style="width: 100%"
            row-key="jsonPath"
          >
            <el-table-column
              class-name="checkbox-table"
              prop="originName"
              :label="t('datasource.parse_filed')"
              width="200"
            >
              <template #default="scope">
                <el-checkbox
                  style="display: inline-block; max-width: 80px; white-space: nowrap"
                  :key="scope.row.jsonPath"
                  v-model="scope.row.checked"
                  @change="handleCheckAllChange(scope.row)"
                >
                  <span
                    :title="scope.row.originName"
                    class="ellipsis"
                    style="display: inline-block; max-width: 80px; line-height: 16px"
                    >{{ scope.row.originName }}</span
                  >
                </el-checkbox>
              </template>
            </el-table-column>
            <el-table-column prop="name" :label="t('datasource.field_rename')">
              <template #default="scope">
                <el-input
                  v-model="scope.row.name"
                  :disabled="disabledByChildren(scope.row)"
                  text
                  @change="previewData()"
                />
              </template>
            </el-table-column>

            <el-table-column
              prop="extractedFieldType"
              :label="t('datasource.field_type')"
              :disabled="apiItem.type === 'params'"
            >
              <template #default="scope">
                <el-select
                  v-model="scope.row.extractedFieldType"
                  :disabled="disabledChangeFieldByChildren(scope.row)"
                  class="select-type"
                  style="display: inline-block; width: 120px"
                  @change="extractedFieldTypeChange(scope.row)"
                >
                  <template #prefix>
                    <el-icon>
                      <Icon :className="`field-icon-${fieldType[scope.row.extractedFieldType]}`"
                        ><component
                          class="svg-icon"
                          :class="`field-icon-${fieldType[scope.row.extractedFieldType]}`"
                          :is="iconFieldMap[fieldType[scope.row.extractedFieldType]]"
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
                        <Icon :className="`field-icon-${fieldType[item.value]}`"
                          ><component
                            class="svg-icon"
                            :class="`field-icon-${fieldType[item.value]}`"
                            :is="iconFieldMap[fieldType[item.value]]"
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
            <el-table-column
              prop="length"
              :label="t('datasource.length')"
              v-if="apiItem.type !== 'params'"
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
                />
              </template>
            </el-table-column>
            <el-table-column
              prop="primaryKey"
              class-name="checkbox-table"
              :label="t('datasource.set_key')"
              v-if="apiItem.type !== 'params' && isSupportSetKey"
              width="100"
            >
              <template #default="scope">
                <el-checkbox
                  :key="scope.row.jsonPath"
                  v-model="scope.row.primaryKey"
                  :disabled="disabledSetKey(scope.row)"
                >
                </el-checkbox>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <p
          :class="[activeDataPreview ? 'active' : 'deactivate', 'column-info-title']"
          @click="activeDataPreview = !activeDataPreview"
        >
          <el-icon style="font-size: 10px">
            <Icon name="icon_expand-right_filled"
              ><icon_expandRight_filled class="svg-icon"
            /></Icon>
          </el-icon>
          <span class="name">{{ t('datasource.data_preview') }}</span>
        </p>
        <div
          v-show="activeDataPreview"
          class="info-table"
          :style="{ height: Math.min(tableData.length, 20) * 40 + 'px' }"
        >
          <empty-background
            v-if="showEmpty"
            :description="t('data_source.the_data_structure')"
            img-type="select"
          />
          <el-auto-resizer v-else>
            <template #default="{ height, width }">
              <el-table-v2
                class="crest-data-table-v2"
                :columns="columns"
                header-class="header-cell"
                :data="tableData"
                :width="width"
                :height="height"
                fixed
              />
            </template>
          </el-auto-resizer>
        </div>
      </el-form>
    </el-row>
    <template #footer>
      <el-button secondary @click="closeEditItem">{{ t('common.cancel') }}</el-button>
      <el-button v-show="active === 0" :disabled="formLoading" secondary @click="validate"
        >{{ t('commons.validate') }}
      </el-button>
      <el-button type="primary" v-show="active === 0" :disabled="disabledNext" @click="next"
        >{{ t('common.next') }}
      </el-button>
      <el-button v-show="active === 1" secondary @click="before">{{ t('common.prev') }} </el-button>
      <el-button v-show="active === 1" type="primary" @click="saveItem"
        >{{ t('commons.save') }}
      </el-button>
    </template>
  </el-drawer>
</template>

<style lang="less">
.api-datasource-drawer {
  .select-type {
    .ed-select__prefix {
      font-size: 16px;
      &::after {
        display: none;
      }
    }
  }
  .ed-drawer__body {
    padding: 24px 24px 80px 24px !important;
  }

  .ed-form {
    width: 100%;

    .ed-form-item:not(.is-error) {
      margin-bottom: 16px;
    }
  }

  .base-info {
    margin: 24px 0 16px 0;
  }

  .line-height_18 {
    .ed-form-item__content {
      line-height: 18px;
    }
  }

  .request-info {
    margin: 32px 0 16px 0;
  }

  .input-with-select {
    .ed-input-group__prepend {
      background-color: #fff;
      padding: 0 20px;
      .ed-select {
        width: 84px !important;
      }
    }

    .ed-input-group__append {
      background-color: #fff;
    }
  }
  .table-container {
    padding: 20px;
    border: 1px solid #dee0e3;
  }

  .info-table {
    min-height: 300px;
    .ed-table-v2__header-cell {
      background-color: #f5f6f7;
    }
  }

  .column-info-title {
    margin: 24px 0 16px 0;
    display: flex;
    align-items: center;
    cursor: pointer;
    .name {
      color: #1f2329;
      font-family: var(--crest-custom_font, 'PingFang');
      font-size: 16px;
      font-style: normal;
      font-weight: 500;
      line-height: 24px;
      margin-left: 8px;
    }

    &.active {
      .ed-icon {
        transform: rotate(90deg);
        color: var(--ed-color-primary);
      }
    }

    &.deactivate {
      .ed-icon {
        transform: rotate(0);
        color: var(--ed-color-primary);
      }
    }
  }
}
</style>
