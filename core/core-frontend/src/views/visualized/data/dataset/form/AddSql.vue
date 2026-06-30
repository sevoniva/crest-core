<script lang="tsx" setup>
import referencePlay from '@/assets/svg/reference-play.svg'
import referenceSetting1 from '@/assets/svg/reference-setting.svg'
import icon_preferences_outlined from '@/assets/svg/icon_preferences_outlined.svg'
import icon_close_outlined from '@/assets/svg/icon_close_outlined.svg'
import icon_right_outlined from '@/assets/svg/icon_right_outlined.svg'
import icon_left_outlined from '@/assets/svg/icon_left_outlined.svg'
import referenceTable from '@/assets/svg/reference-table.svg'
import icon_searchOutline_outlined from '@/assets/svg/icon_search-outline_outlined.svg'
import icon_form_outlined from '@/assets/svg/icon_form_outlined.svg'
import icon_copy_outlined from '@/assets/svg/icon_copy_outlined.svg'
import icon_info_outlined from '@/assets/svg/icon_info_outlined.svg'
import icon_textBox_outlined from '@/assets/svg/icon_text-box_outlined.svg'
import icon_info_colorful from '@/assets/svg/icon_info_colorful.svg'
import icon_playRound_outlined from '@/assets/svg/icon_play-round_outlined.svg'
import { searchVariableApi } from '@/api/variable'
import {
  ref,
  reactive,
  onMounted,
  PropType,
  toRefs,
  watch,
  onBeforeUnmount,
  shallowRef,
  computed,
  inject,
  h,
  Ref
} from 'vue'
import { debounce } from 'lodash-es'
import { useI18n } from '@/hooks/web/useI18n'
import { Base64 } from 'js-base64'
import FixedSizeList from 'element-plus-secondary/es/components/virtual-list/src/components/fixed-size-list.mjs'
import { useWindowSize } from '@vueuse/core'
import useClipboard from 'vue-clipboard3'
import { ElMessage, ElMessageBox, ElIcon } from 'element-plus-secondary'
import { Icon } from '@/components/icon-custom'
import { getTableField } from '@/api/dataset'
import CodeMirror from './SqlCodeEditor.vue'
import type { Field, DataSource } from './util'
import { datasourceList, tables, getPreviewSql } from '@/api/dataset'
import GridTable from '@/components/grid-table/src/GridTable.vue'
import { EmptyBackground } from '@/components/empty-background'
import { timestampFormatDate, defaultValueScopeList, fieldOptions } from './util'
import { fieldType } from '@/utils/attr'
import { iconFieldMap } from '@/components/icon-group/field-list'
import { isDesktop } from '@/utils/ModelUtil'
import field_text from '@/assets/svg/field_text.svg'
import field_value from '@/assets/svg/field_value.svg'
import field_time from '@/assets/svg/field_time.svg'
export interface SqlNode {
  sql: string
  tableName: string
  datasourceId: string
  id: string
  changeFlag?: boolean
  variables?: Array<{
    variableName: string
    defaultValue: string
    defaultValueScope: string
  }>
  sqlVariableDetails?: string
}

// 当前编辑的 SQL 节点，由父级数据集表单负责传入和保存
const props = defineProps({
  sqlNode: {
    type: Object as PropType<SqlNode>,
    default: () => ({})
  }
})

const { sqlNode } = toRefs(props)
const { toClipboard } = useClipboard()
const { t } = useI18n()
// 当前左侧数据表列表中选中的表名
const activeName = ref('')
// SQL 编辑器组件实例，用于初始化 CodeMirror
const myCm = ref()
// CodeMirror 编辑器运行时实例，用于插入文本和读取 SQL
const codeCom = ref()
const dialogTitle = t('sql_variable.variable_mgm') + ' '
// 下方结果区域的当前标签页
const tabActive = ref('result')
// 左侧数据表搜索关键字
const searchTable = ref('')
// SQL 参数管理抽屉的显示状态
const showVariableMgm = ref(false)
// 左侧数据源表加载状态
const dsLoading = ref(false)
// 预留的执行日志加载状态，保持与模板表格绑定一致
const loading = ref(false)
// 左侧数据表面板宽度，拖拽时在安全范围内更新
const LeftWidth = ref(240)
// 左侧数据表面板是否展开
const showLeft = ref(true)
// 表名输入框引用，供后续聚焦或校验扩展使用
const editorName = ref()
// SQL 编辑器主状态，集中保存预览数据、变量、字段和数据源列表
const state = reactive({
  plxTableData: [],
  variables: [],
  fields: [],
  sqlData: [],
  variablesTmp: [],
  dataSourceList: [],
  table: {
    name: '',
    id: ''
  },
  param: {
    tableId: 0
  }
})

// 虚拟列表的数据源表数据，搜索时会替换为过滤结果
const datasourceTableData = shallowRef([])
// 当前数据源表名列表，用于 SQL 编辑器表名联想
const tableCompletionNames = ref<string[]>([])
const isCross = inject<Ref>('isCross')
// 执行日志表格分页配置
const paginationConfig = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0
})

// 切换左侧表选中状态，只允许可检查的表进入激活态
const setActiveName = ({ name, enableCheck }) => {
  if (!enableCheck) return
  activeName.value = name
}
const { height: windowHeight } = useWindowSize()

// 根据后端字段元数据生成预览表格列，并保留字段类型图标
const generateColumns = (arr: Field[]) =>
  arr.map(ele => ({
    key: ele.originName,
    fieldType: ele.fieldType,
    dataKey: ele.originName,
    title: ele.originName,
    width: 170,
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

// 打开 SQL 变量设置抽屉，并先同步编辑器中最新变量
const referenceSetting = () => {
  showVariableMgm.value = true
  parseVariable()
}
// 自定义系统变量列表，桌面端和移动端右侧变量面板共用
const fieldFormList = ref([])

// 内置系统变量列表，写入 SQL 时使用系统占位符表示
const builtInList = ref([
  {
    id: 'sysParams.userId',
    name: t('common.account')
  },
  {
    id: 'sysParams.userName',
    name: t('datasource.user_name')
  },
  {
    id: 'sysParams.userEmail',
    name: t('commons.email')
  },
  {
    id: 'sysParams.userPhone',
    name: t('auth.sysParams_type.user_phone')
  }
])

// 根据变量类型选择变量面板中的图标资源
const iconName = type => {
  if (type === 'text') {
    return field_text
  }
  if (type === 'num') {
    return field_value
  }
  if (type === 'time') {
    return field_time
  }
}

// 根据变量类型选择变量面板中的图标样式
const iconClassName = type => {
  if (type === 'text') {
    return 'field-icon-text'
  }
  if (type === 'num') {
    return 'field-icon-value'
  }
  if (type === 'time') {
    return 'field-icon-time'
  }
}

// 按搜索关键字过滤自定义变量，保持内置变量不参与该过滤
const fieldFormListComputed = computed(() => {
  return fieldFormList.value.filter(ele =>
    ele.name.toLowerCase().includes(searchField.value.toLowerCase())
  )
})
const desktop = isDesktop()
// 移动端系统参数侧栏显示状态
const showSysParams = ref(false)
// 系统参数侧栏中的自定义变量搜索关键字
const searchField = ref('')
// 打开移动端系统参数面板，并懒加载自定义变量
const sysParams = () => {
  showSysParams.value = true
  handleSearchVariableApi()
}

// 兼容不同 CodeMirror 版本的光标位置读取
const getCursor = view => {
  return (
    view?.state?.selection?.main?.from ??
    view?.viewState?.state?.selection?.ranges?.[0]?.from ??
    view?.state?.doc?.length ??
    0
  )
}

// 在当前光标位置插入系统变量或自定义变量占位符
const insertFieldToCodeMirror = (value: string) => {
  if (!codeCom.value) return
  const from = getCursor(codeCom.value)
  codeCom.value.dispatch({
    changes: { from, insert: value },
    selection: { anchor: from + value.length }
  })
}

// 在保存和展示之间转换系统变量 ID 与名称，编辑器展示名称，持久化保存稳定 ID。
const setNameIdTrans = (from, to, originName, name2Auto?: string[]) => {
  let name2Id = originName
  const ids = [...builtInList.value, ...fieldFormList.value].map(item => item.id)
  const names = [...builtInList.value, ...fieldFormList.value].map(item => item.name)
  const nameIdMap = [...builtInList.value, ...fieldFormList.value].reduce((pre, next) => {
    pre[next[from]] = next[to]
    return pre
  }, {})
  const on = originName.match(/\$crest\[(.+?)\]/g)
  if (on) {
    on.forEach(itm => {
      const ele = itm.slice(7, -1)
      if (name2Auto) {
        // 调用方可收集自动识别到的变量 ID，用于后续参数详情同步。
        name2Auto.push(nameIdMap[ele])
      }
      if (from === 'id' && ids.includes(ele)) {
        name2Id = name2Id.replace(`$crest[${ele}]`, `$crest[${nameIdMap[ele]}]`)
      }
      if (from === 'name' && names.includes(ele)) {
        name2Id = name2Id.replace(`$crest[${ele}]`, `$crest[${nameIdMap[ele]}]`)
      }
    })
  }
  return name2Id
}
// 拉取可引用的自定义系统变量列表
const handleSearchVariableApi = async () => {
  return searchVariableApi({}).then(res => {
    fieldFormList.value = res?.data || []
  })
}
// 移动端系统参数入口是否可见，接口失败时隐藏入口
const showSystemParams = ref(true)
// 初始化数据源表、移动端变量列表和 CodeMirror 实例
onMounted(async () => {
  // 数据源表列表先按当前 SQL 节点加载，避免编辑器打开后左侧列表为空。
  await loadDatasourceTables(sqlNode.value.datasourceId)
  if (!desktop) {
    try {
      await handleSearchVariableApi()
    } catch (e) {
      if (e) {
        showSystemParams.value = false
      }
    }
  }
  codeCom.value = myCm.value.codeComInit(
    setNameIdTrans('id', 'name', Base64.decode(sqlNode.value.sql)),
    true,
    tableCompletionNames.value
  )
})

// 组件卸载时销毁 CodeMirror 实例，避免残留编辑器监听器
onBeforeUnmount(() => {
  codeCom.value.destroy?.()
})

// 当前表字段弹窗的字段列表
const gridData = ref([])
// 当前表字段弹窗的加载状态
const gridDataLoading = ref(false)

// 加载左侧表信息弹窗中的字段元数据
const getNodeField = ({ datasourceId, tableName }) => {
  gridDataLoading.value = true
  let info = {
    table: tableName,
    sql: ''
  }
  getTableField({
    datasourceId,
    info: JSON.stringify(info),
    tableName,
    type: 'db',
    isCross: isCross.value
  })
    .then(res => {
      gridData.value = res as unknown as Field[]
    })
    .finally(() => {
      gridDataLoading.value = false
    })
}

// 加载数据源树，并兼容后端返回的虚拟根节点结构
const datasource = () => {
  datasourceList().then(res => {
    const _list = (res as unknown as DataSource[]) || []
    if (_list && _list.length > 0 && _list[0].id === '0') {
      state.dataSourceList = _list[0].children
    } else {
      state.dataSourceList = _list
    }
  })
}
// SQL 编辑器上半区高度，拖拽时在安全范围内更新
const dragHeight = ref(260)

// 开始纵向拖拽，监听编辑器容器内的鼠标移动
const mousedownDragH = () => {
  document.querySelector('.sql-eidtor').addEventListener('mousemove', calculateHeight)
}

// 根据鼠标纵坐标计算 SQL 编辑器高度，并限制最小和最大高度
const calculateHeight = (e: MouseEvent) => {
  if (e.pageY - 164 < 64) {
    dragHeight.value = 64
    return
  }
  if (e.pageY - 164 > document.documentElement.clientHeight - 200) {
    dragHeight.value = document.documentElement.clientHeight - 200
    return
  }
  dragHeight.value = e.pageY - 164
}

// 根据鼠标横坐标计算左侧数据表面板宽度
const calculateWidth = (e: MouseEvent) => {
  // 左侧面板宽度限制在 240 到 400 像素，兼顾表名可读性和 SQL 编辑区空间。
  if (e.pageX < 240) {
    LeftWidth.value = 240
    return
  }
  if (e.pageX > 400) {
    LeftWidth.value = 400
    return
  }
  LeftWidth.value = e.pageX
}

// 用指定 SQL 全量替换编辑器内容，常用于切换节点或关闭重置
const insertParamToCodeMirror = (value: string) => {
  if (!codeCom.value) return
  const to = codeCom.value.state?.doc?.length ?? codeCom.value.state?.doc?.toString().length ?? 0
  codeCom.value.dispatch({
    changes: { from: 0, to, insert: value },
    selection: { anchor: value.length }
  })
}

// 切换数据源时刷新左侧表列表
watch(
  () => sqlNode.value.datasourceId,
  val => {
    dsChange(val)
  }
)

// 切换 SQL 节点时同步变量配置，并恢复编辑器展示用变量名称
watch(
  () => sqlNode.value.id,
  () => {
    state.variables = sqlNode.value.variables
    if (codeCom.value) {
      insertParamToCodeMirror(setNameIdTrans('id', 'name', Base64.decode(sqlNode.value.sql)))
    }
  },
  {
    immediate: true
  }
)

const treeProps = {
  children: 'children',
  label: 'name'
}

datasource()

// 关闭和保存事件由父级数据集表单统一处理
const emits = defineEmits(['close', 'save'])

let changeFlag = false
// SQL 或表名是否有未保存变更，用于控制保存按钮和关闭确认
const changeFlagCode = ref(false)
// 标记当前编辑内容已发生变化
const setFlag = () => {
  changeFlag = true
  changeFlagCode.value = true
}
let sql = ''

// 保存 SQL 节点，提交前把展示名称占位符转换回变量 ID 并序列化参数配置
const save = () => {
  if (!sqlNode.value.tableName.trim()) {
    ElMessage.error(t('data_set.cannot_be_empty'))
    return
  }

  parseVariable()
  sql = setNameIdTrans('name', 'id', codeCom.value.state.doc.toString())
  sqlNode.value.changeFlag = true
  if (!sql.trim()) {
    ElMessage.error(t('data_set.sql_content_required'))
    return
  }
  sqlNode.value.sql = Base64.encode(sql)
  emits(
    'save',
    {
      ...sqlNode.value,
      sql: Base64.encode(sql),
      sqlVariableDetails: JSON.stringify(state.variables)
    },
    () => {
      // 保存成功提示由父级回调触发，确保后端持久化完成后再反馈用户。
      ElMessage.success(t('common.save_success'))
    }
  )
  changeFlag = false
}

// 关闭编辑器并恢复局部预览状态
const close = () => {
  searchTable.value = ''
  state.plxTableData = []
  state.fields = []
  if (codeCom.value) {
    insertParamToCodeMirror(setNameIdTrans('id', 'name', Base64.decode(sqlNode.value.sql)))
  }
  emits('close')
}

// 关闭前检查未保存变更，必要时弹出二次确认
const handleClose = () => {
  let sqlNew = setNameIdTrans('name', 'id', codeCom.value.state.doc.toString())

  if (changeFlag || sql !== sqlNew || !sqlNew.trim()) {
    ElMessageBox.confirm(t('chart.tips'), {
      confirmButtonType: 'primary',
      tip: t('data_set.sure_to_exit'),
      type: 'warning',
      autofocus: false,
      showClose: false
    }).then(() => {
      close()
      changeFlag = false
      changeFlagCode.value = false
    })
  } else {
    close()
    changeFlagCode.value = false
  }
}

// SQL 预览按钮加载状态
const dataPreviewLoading = ref(false)
// 执行当前 SQL 预览，并把后端字段和数据转成表格展示结构
const getSQLPreview = () => {
  parseVariable()
  dataPreviewLoading.value = true
  getPreviewSql({
    isCross: isCross.value,
    sql: Base64.encode(setNameIdTrans('name', 'id', codeCom.value.state.doc.toString())),
    datasourceId: sqlNode.value.datasourceId,
    sqlVariableDetails: JSON.stringify(state.variables)
  })
    .then(res => {
      state.plxTableData = res.data.data
      state.fields = generateColumns(res.data.fields)
    })
    .finally(() => {
      dataPreviewLoading.value = false
    })
}

let tableList = []
// 左侧表搜索只过滤本地已加载表列表，避免频繁请求后端
watch(searchTable, val => {
  datasourceTableData.value = tableList.filter(ele =>
    ele.tableName.toLowerCase().includes(val.toLowerCase())
  )
})

// 根据字段类型映射通用字段图标类型
const getIconName = (type: string) => {
  if (
    ['DATETIME-YEAR', 'DATETIME-YEAR-MONTH', 'DATETIME', 'DATETIME-YEAR-MONTH-DAY'].includes(type)
  ) {
    return 'time'
  }

  if (type === 'TEXT') {
    return 'text'
  }

  if (['LONG', 'DOUBLE'].includes(type)) {
    return 'value'
  }
}

// 执行日志耗时列格式化，空值以占位符展示
const formatter = (_, __, cellValue) => {
  return cellValue ? `${cellValue} ${t(`commons.millisecond`)}` : '-'
}

// 展开或收起左侧数据表面板，并同步宽度
const handleShowLeft = () => {
  showLeft.value = !showLeft.value
  LeftWidth.value = showLeft.value ? 240 : 0
}

// 加载当前数据源下的数据表列表，并同步 SQL 编辑器补全所需表名
const loadDatasourceTables = async (val: string) => {
  if (!val) {
    tableList = []
    datasourceTableData.value = []
    tableCompletionNames.value = []
    return
  }
  dsLoading.value = true
  try {
    const res = await tables({ datasourceId: val })
    tableList = res || []
    datasourceTableData.value = [...tableList]
    tableCompletionNames.value = tableList.map(ele => ele.tableName).filter(Boolean)
  } catch {
    tableList = []
    datasourceTableData.value = []
    tableCompletionNames.value = []
  } finally {
    dsLoading.value = false
  }
}

// 数据源切换后重建编辑器语言扩展，保留当前 SQL 文本并刷新表名补全
const refreshSqlEditorTables = async (val: string) => {
  await loadDatasourceTables(val)
  if (!codeCom.value || !myCm.value) return
  const currentSql = codeCom.value.state.doc.toString()
  codeCom.value.destroy?.()
  codeCom.value = myCm.value.codeComInit(currentSql, true, tableCompletionNames.value)
}

// 防抖加载当前数据源下的数据表列表
const dsChange = debounce((val: string) => {
  refreshSqlEditorTables(val)
}, 300)

// 数据源选择变化时标记未保存，并刷新数据表列表
const handleDsChange = () => {
  setFlag()
  dsChange(sqlNode.value.datasourceId)
}

// 复制表名、字段名或 SQL 内容，并根据浏览器能力提示结果
const copyInfo = async (value: string) => {
  try {
    await toClipboard(value)
    ElMessage.success(t('data_set.copied_successfully'))
  } catch (e) {
    ElMessage.warning(t('data_set.not_support_copying'), e)
  }
}

// 结束横向或纵向拖拽，清理临时鼠标移动监听
const mouseupDrag = () => {
  const dom = document.querySelector('.sql-eidtor')
  dom.removeEventListener('mousemove', calculateWidth)
  dom.removeEventListener('mousemove', calculateHeight)
}

// 从编辑器 SQL 中解析参数占位符，并保留已配置的参数默认值
const parseVariable = () => {
  state.variablesTmp = []
  const variableReg = new RegExp('\\$CREST_PARAM{(.*?)}', 'gim')
  const variableMatch = codeCom.value.state.doc.toString().match(variableReg)
  if (variableMatch !== null) {
    // 新版参数语法允许一个参数块内声明多个变量，逐个提取并去重
    const names = []
    const reg = new RegExp('\\$\\[[^\\]]+\\]', 'gim')
    for (let index = 0; index < variableMatch.length; index++) {
      let sqlItem = variableMatch[index].substring(10, variableMatch[index].length - 1)
      const match = sqlItem.match(reg)
      if (match !== null) {
        for (let matchIndex = 0; matchIndex < match.length; matchIndex++) {
          let name = match[matchIndex].substring(2, match[matchIndex].length - 1)
          if (names.indexOf(name) < 0) {
            // 同一 SQL 中重复出现的参数只保留一份配置，避免参数抽屉出现重复行。
            names.push(name)
            let obj = undefined
            for (let i = 0; i < state.variables?.length; i++) {
              if (state.variables[i].variableName === name) {
                obj = state.variables[i]
                if (!obj.hasOwnProperty('defaultValueScope')) {
                  obj.defaultValueScope = 'EDIT'
                }
              }
            }
            if (obj === undefined) {
              // 首次出现的参数默认按文本类型处理，后续可在参数抽屉中调整
              obj = {
                variableName: name,
                alias: '',
                type: [],
                required: false,
                defaultValue: '',
                details: '',
                defaultValueScope: 'EDIT'
              }
              obj.type.push('TEXT')
            }
            state.variablesTmp.push(obj)
          }
        }
      }
    }
  } else {
    // 兼容旧版 ${name} 参数写法，避免历史 SQL 打开后丢失参数配置
    const reg = new RegExp('\\${(.*?)}', 'gim')
    const match = codeCom.value.state.doc.toString().match(reg)
    const names = []
    if (match !== null) {
      for (let index = 0; index < match.length; index++) {
        let name = match[index].substring(2, match[index].length - 1)
        if (names.indexOf(name) < 0) {
          // 旧语法参数同样按变量名去重，保持与新版参数块一致的编辑体验。
          names.push(name)
          let obj = undefined
          for (let i = 0; i < state.variables?.length; i++) {
            if (state.variables[i].variableName === name) {
              obj = state.variables[i]
              if (!obj.hasOwnProperty('defaultValueScope')) {
                obj.defaultValueScope = 'EDIT'
              }
            }
          }
          if (obj === undefined) {
            // 历史参数没有保存类型时按文本参数补齐默认结构
            obj = {
              variableName: name,
              alias: '',
              type: [],
              required: false,
              defaultValue: '',
              details: '',
              defaultValueScope: 'EDIT'
            }
            obj.type.push('TEXT')
          }
          state.variablesTmp.push(obj)
        }
      }
    }
  }
  // 使用深拷贝断开临时表格和已保存变量配置的引用
  state.variables = JSON.parse(JSON.stringify(state.variablesTmp))
}

// 保存参数管理抽屉中的临时变量配置
const saveVariable = () => {
  state.variables = JSON.parse(JSON.stringify(state.variablesTmp))
  showVariableMgm.value = false
  changeFlagCode.value = true
  ElMessage.success(t('data_set.parameters_set_successfully'))
}
// 开始横向拖拽，监听编辑器容器内的鼠标移动
const mousedownDrag = () => {
  document.querySelector('.sql-eidtor').addEventListener('mousemove', calculateWidth)
}
</script>

<template>
  <div class="add-sql-name">
    <!-- 顶部操作区承载节点命名、运行预览、参数设置和保存状态。 -->
    <el-input class="name" ref="editorName" v-model="sqlNode.tableName" @change="setFlag" />
    <div class="save-or-cancel flex-align-center">
      <el-button @click="getSQLPreview" text style="color: #1f2329">
        <template #icon>
          <el-icon>
            <Icon name="reference-play"><referencePlay class="svg-icon" /></Icon>
          </el-icon>
        </template>
        {{ t('data_set.run') }}
      </el-button>
      <el-button @click="referenceSetting()" style="color: #1f2329" text>
        <template #icon>
          <el-icon>
            <Icon name="reference-setting"><referenceSetting1 class="svg-icon" /></Icon>
          </el-icon>
        </template>
        {{ t('data_set.parameter_settings') }}
      </el-button>
      <el-button v-if="!desktop && showSystemParams" @click="sysParams" class="system-text_bg" text>
        <template #icon>
          <el-icon>
            <Icon><icon_preferences_outlined class="svg-icon" /></Icon>
          </el-icon>
        </template>
        {{ t('auth.sysParams') }}
      </el-button>
      <el-button :disabled="!changeFlagCode" @click="save" type="primary">
        {{ t('data_set.save') }}</el-button
      >
      <el-divider direction="vertical" />
      <el-icon class="hover-icon" @click="handleClose">
        <Icon name="icon_close_outlined"><icon_close_outlined class="svg-icon" /></Icon>
      </el-icon>
    </div>
  </div>

  <div class="sql-eidtor" @mouseup="mouseupDrag">
    <p v-show="!showLeft" class="arrow-right" @click="handleShowLeft">
      <el-icon>
        <Icon name="icon_right_outlined"><icon_right_outlined class="svg-icon" /></Icon>
      </el-icon>
    </p>
    <div
      v-show="showLeft"
      :style="{ left: LeftWidth + 'px' }"
      class="drag-left"
      @mousedown="mousedownDrag"
    />
    <div
      v-loading="dsLoading"
      v-show="showLeft"
      class="table-list"
      :style="{ width: LeftWidth + 'px' }"
    >
      <!-- 左侧数据源面板支持数据源切换、表搜索、字段查看和快捷复制。 -->
      <div class="table-list-top">
        <p class="select-ds">
          {{ t('data_set.current_data_source') }}
          <span class="left-outlined">
            <el-icon style="color: #1f2329" @click="showLeft = false">
              <Icon name="icon_left_outlined"><icon_left_outlined class="svg-icon" /></Icon>
            </el-icon>
          </span>
        </p>
        <el-tree-select
          :check-strictly="false"
          @change="handleDsChange"
          :placeholder="t('dataset.pls_slc_data_source')"
          class="ds-list"
          popper-class="tree-select-ds_popper"
          v-model="sqlNode.datasourceId"
          node-key="id"
          :props="treeProps"
          :data="state.dataSourceList"
          :render-after-expand="false"
        />
        <p class="select-ds table-num">
          {{ t('datasource.data_table')
          }}<span class="num">
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
        <!-- 数据表数量可能较多，使用固定高度虚拟列表降低左侧面板渲染成本 -->
        <FixedSizeList
          :itemSize="40"
          :data="datasourceTableData"
          :total="datasourceTableData.length"
          :width="LeftWidth - 17"
          :height="windowHeight - 350"
          :scrollbarAlwaysOn="false"
          class-name="el-select-dropdown__list"
          layout="vertical"
        >
          <template #default="{ index, style }">
            <div
              :class="[{ active: activeName === datasourceTableData[index].tableName }]"
              class="list-item_primary"
              :style="style"
              :title="datasourceTableData[index].tableName"
              @click="setActiveName(datasourceTableData[index])"
            >
              <el-icon class="icon-color">
                <Icon name="icon_form_outlined"><icon_form_outlined class="svg-icon" /></Icon>
              </el-icon>
              <span class="label">{{ datasourceTableData[index].tableName }}</span>
              <span class="name-copy">
                <el-tooltip effect="dark" :content="t('common.copy')" placement="top">
                  <el-icon
                    class="hover-icon"
                    @click="copyInfo(datasourceTableData[index].tableName)"
                  >
                    <Icon name="icon_copy_outlined"><icon_copy_outlined class="svg-icon" /></Icon>
                  </el-icon>
                </el-tooltip>

                <el-popover
                  popper-class="sql-table-info"
                  placement="right"
                  :width="502"
                  :persistent="false"
                  @show="getNodeField(datasourceTableData[index])"
                  trigger="click"
                >
                  <template #reference>
                    <el-icon class="hover-icon">
                      <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
                    </el-icon>
                  </template>
                  <div class="table-filed" v-loading="gridDataLoading">
                    <div class="top flex-align-center">
                      <div class="title ellipsis">
                        {{
                          datasourceTableData[index].name || datasourceTableData[index].tableName
                        }}
                      </div>
                      <el-icon
                        class="hover-icon crest-hover-icon-primary"
                        @click.stop="
                          copyInfo(
                            datasourceTableData[index].name || datasourceTableData[index].tableName
                          )
                        "
                      >
                        <Icon name="icon_copy_outlined"
                          ><icon_copy_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                      <div class="num flex-align-center">
                        <el-icon>
                          <Icon name="icon_text-box_outlined"
                            ><icon_textBox_outlined class="svg-icon"
                          /></Icon>
                        </el-icon>
                        {{ gridData.length }}
                      </div>
                    </div>
                    <div class="table-grid">
                      <el-table
                        class="crest-data-table"
                        height="405"
                        style="width: 100%"
                        header-cell-class-name="header-cell"
                        :data="gridData"
                      >
                        <el-table-column :label="t('data_set.physical_field_name')">
                          <template #default="scope">
                            <div class="flex-align-center icon">
                              <el-icon>
                                <Icon
                                  ><component
                                    class="svg-icon"
                                    :class="`field-icon-${fieldType[scope.row.fieldType]}`"
                                    :is="iconFieldMap[fieldType[scope.row.fieldType]]"
                                  ></component
                                ></Icon>
                              </el-icon>
                              {{ scope.row.originName }}
                            </div>
                          </template>
                        </el-table-column>
                        <el-table-column :label="t('common.label')">
                          <template #default="scope">
                            {{ scope.row.description || '-' }}
                          </template>
                        </el-table-column>
                        <el-table-column :label="t('common.operate')">
                          <template #default="scope">
                            <el-icon
                              class="hover-icon crest-hover-icon-primary"
                              @click.stop="copyInfo(scope.row.originName)"
                            >
                              <Icon name="icon_copy_outlined"
                                ><icon_copy_outlined class="svg-icon"
                              /></Icon>
                            </el-icon>
                          </template>
                        </el-table-column>
                      </el-table>
                    </div>
                  </div>
                </el-popover>
              </span>
            </div>
          </template>
        </FixedSizeList>
      </div>
    </div>
    <div
      class="sql-code-right"
      :class="showSysParams && 'p280'"
      :style="{ width: `calc(100% - ${showLeft ? LeftWidth : 0}px)` }"
    >
      <!-- SQL 编辑器接收变量名映射，用于高亮区分指标型和维度型系统变量。 -->
      <code-mirror
        @change="changeFlagCode = true"
        :height="`${dragHeight}px`"
        dom-id="sql-editor"
        :regexp="/\$crest\[(.*?)\]/g"
        ref="myCm"
        :quotaMap="fieldFormList.filter(ele => ['num'].includes(ele.type)).map(ele => ele.name)"
        :tableNames="tableCompletionNames"
        :dimensionMap="
          builtInList
            .concat(fieldFormList.filter(ele => !['num'].includes(ele.type)))
            .map(ele => ele.name)
        "
      ></code-mirror>
      <div class="sql-result" :style="{ height: `calc(100% - ${dragHeight}px)` }">
        <div class="sql-title">
          <span class="drag" @mousedown="mousedownDragH" />
        </div>
        <div class="padding-24">
          <el-tabs v-model="tabActive">
            <el-tab-pane :label="t('datasetUi.running_results')" name="result" />
          </el-tabs>
        </div>
        <div v-show="tabActive === 'result'" class="table-sql">
          <div class="table-scroll" v-if="state.fields.length">
            <el-auto-resizer>
              <template #default="{ height, width }">
                <el-table-v2
                  class="crest-data-table-v2"
                  :columns="state.fields"
                  v-loading="dataPreviewLoading"
                  header-class="header-cell"
                  :data="state.plxTableData"
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
          <template v-else>
            <empty-background description=" " img-type="noneWhite">
              <div class="sql-tips flex-align-center">
                {{ t('data_set.click_above') }}
                <el-icon>
                  <icon name="icon_play-round_outlined"
                    ><icon_playRound_outlined class="svg-icon"
                  /></icon>
                </el-icon>
                {{ t('data_set.see_the_results') }}
              </div>
            </empty-background>
          </template>
        </div>
        <div v-show="tabActive === 'execLog'" class="table-container">
          <grid-table
            v-loading="loading"
            :table-data="state.sqlData"
            :show-pagination="!!state.param.tableId"
            :columns="[]"
            :pagination="paginationConfig"
          >
            <el-table-column
              key="startTimeTable"
              min-width="100px"
              prop="startTime"
              :label="t('dataset.start_time')"
            >
              <template #default="scope">
                <span>{{ timestampFormatDate(scope.row.startTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column key="sql" prop="sql" show-overflow-tooltip :label="t('dataset.sql')" />
            <el-table-column
              key="spend"
              prop="spend"
              :formatter="formatter"
              :label="t('dataset.spend_time')"
            />
            <el-table-column key="status" prop="status" :label="t('dataset.sql_result')">
              <template #default="scope">
                <span
                  v-if="scope.row.status"
                  :class="[`crest-${scope.row.status}-pre`, 'crest-status']"
                  >{{ t(`dataset.${scope.row.status.toLocaleLowerCase()}`) }}
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>

            <el-table-column
              key="__operation"
              :label="t('commons.operating')"
              fixed="right"
              width="100"
            >
              <template #default="scope">
                <el-button text @click="copyInfo(scope.row.sql)">
                  {{ t('common.copy') }}
                </el-button>
              </template>
            </el-table-column>
          </grid-table>
        </div>
      </div>
      <div v-if="showSysParams" class="handle-system">
        <div class="handle-system_title">
          {{ t('auth.sysParams') }}
          <el-icon class="hover-icon" @click="showSysParams = false">
            <Icon name="icon_close_outlined"><icon_close_outlined class="svg-icon" /></Icon>
          </el-icon>
        </div>
        <div class="handle-system_list">
          <el-input
            style="width: 100%; margin-bottom: 16px"
            v-model="searchField"
            :placeholder="t('dataset.edit_search')"
            clearable
          >
            <template #prefix>
              <el-icon>
                <Icon><icon_searchOutline_outlined class="svg-icon" /></Icon>
              </el-icon>
            </template>
          </el-input>
          <div class="system-list">
            <!-- 移动端侧栏将内置变量和自定义变量分组展示，点击后写入当前光标位置 -->
            <div class="built-in">
              {{ t('system.system_built_in_variable') }}
            </div>
            <div
              class="variable-item flex-align-center"
              @click="insertFieldToCodeMirror(`$crest[${fieldForm.name}]`)"
              v-for="fieldForm in builtInList"
              :key="fieldForm.id"
            >
              {{ fieldForm.name }}
            </div>
            <div class="built-in" style="margin-top: 16px">
              {{ t('system.custom_variable') }}
            </div>
            <div
              class="variable-item flex-align-center"
              v-for="fieldForm in fieldFormListComputed"
              :key="fieldForm.id"
              @click="insertFieldToCodeMirror(`$crest[${fieldForm.name}]`)"
              :class="['num'].includes(fieldForm.type) && 'with-type'"
            >
              <el-icon>
                <Icon
                  ><component
                    class="svg-icon"
                    :class="iconClassName(fieldForm.type)"
                    :is="iconName(fieldForm.type)"
                  ></component
                ></Icon>
              </el-icon>
              <span :title="fieldForm.name" class="ellipsis">{{ fieldForm.name }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  <el-drawer
    :title="dialogTitle"
    v-model="showVariableMgm"
    modal-class="sql-dataset-drawer"
    size="870px"
    direction="rtl"
  >
    <div class="content">
      <el-icon style="font-size: 16px">
        <Icon name="icon_info_colorful"><icon_info_colorful class="svg-icon" /></Icon>
      </el-icon>
      {{ t('dataset.sql_variable_limit_1') }}<br />
      {{ t('dataset.sql_variable_limit_2') }}<br />
    </div>
    <el-table
      class="crest-data-table"
      header-cell-class-name="header-cell"
      :data="state.variablesTmp"
    >
      <el-table-column width="220" prop="variableName" :label="t('visualization.param_name')" />
      <el-table-column width="200" :label="t('datasetUi.parameter_type')">
        <template #default="scope">
          <el-cascader
            class="select-type"
            popper-class="cascader-panel no-scroll_bar"
            v-model="scope.row.type"
            :options="fieldOptions"
            @change="scope.row.defaultValue = ''"
          >
            <template v-slot="{ data }">
              <el-icon>
                <Icon
                  ><component
                    class="svg-icon"
                    :class="`field-icon-${getIconName(data.value)}`"
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
                  :class="`field-icon-${getIconName(scope.row.type[0])}`"
                  :is="iconFieldMap[getIconName(scope.row.type[0])]"
                ></component
              ></Icon>
            </el-icon>
          </span>
        </template>
      </el-table-column>
      <el-table-column min-width="350" prop="defaultValue" :label="t('commons.params_value')">
        <template #header>
          {{ t('commons.params_value') }}
        </template>
        <template #default="scope">
          <el-input
            v-if="getIconName(scope.row.type[0]) === 'text'"
            v-model="scope.row.defaultValue"
            type="text"
            :placeholder="t('common.please_input')"
          >
            <template #prepend>
              <el-select v-model="scope.row.defaultValueScope" style="width: calc(100% + 22px)">
                <el-option
                  v-for="item in defaultValueScopeList"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </template>
          </el-input>
          <el-input
            v-if="getIconName(scope.row.type[0]) === 'value'"
            v-model="scope.row.defaultValue"
            :placeholder="t('common.please_input')"
            type="number"
          >
            <template #prepend>
              <el-select v-model="scope.row.defaultValueScope" style="width: calc(100% + 22px)">
                <el-option
                  v-for="item in defaultValueScopeList"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </template>
          </el-input>
          <div
            v-if="getIconName(scope.row.type[0]) === 'time'"
            class="ed-input ed-input--light ed-input-group ed-input-group--prepend crest-group__prepend"
          >
            <div class="ed-input-group__prepend">
              <el-select v-model="scope.row.defaultValueScope" style="width: calc(100% + 22px)">
                <el-option
                  v-for="item in defaultValueScopeList"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </div>
            <el-date-picker
              v-if="scope.row.type[0] === 'DATETIME-YEAR'"
              v-model="scope.row.defaultValue"
              type="year"
              format="YYYY"
              value-format="YYYY"
              :placeholder="t('dataset.select_year')"
            />

            <el-date-picker
              v-if="scope.row.type[0] === 'DATETIME-YEAR-MONTH'"
              v-model="scope.row.defaultValue"
              type="month"
              :format="scope.row.type[1]"
              :value-format="scope.row.type[1]"
              :placeholder="t('dataset.select_month')"
            />

            <el-date-picker
              v-if="scope.row.type[0] === 'DATETIME-YEAR-MONTH-DAY'"
              v-model="scope.row.defaultValue"
              type="date"
              :format="scope.row.type[1]"
              :value-format="scope.row.type[1]"
              :placeholder="t('dataset.select_date')"
            />

            <el-date-picker
              v-if="scope.row.type[0] === 'DATETIME'"
              v-model="scope.row.defaultValue"
              type="datetime"
              :format="scope.row.type[1]"
              :value-format="scope.row.type[1]"
              :placeholder="t('dataset.select_time')"
            />
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <empty-background :description="t('data_set.no_data')" img-type="noneWhite" />
      </template>
    </el-table>
    <template #footer>
      <el-button secondary @click="showVariableMgm = false">{{ t('dataset.cancel') }} </el-button>
      <el-button type="primary" @click="saveVariable()">{{ t('dataset.confirm') }} </el-button>
    </template>
  </el-drawer>
</template>

<style lang="less" scoped>
@import '@/style/mixin.less';
.sql-eidtor {
  /* SQL 编辑器占满剩余视口高度，左右分栏和底部结果区都在该容器内计算 */
  width: 100%;
  height: calc(100vh - 156px);
  position: relative;
  .drag-left {
    /* 左侧数据表面板拖拽手柄，仅负责捕获横向拖动事件 */
    position: absolute;
    height: calc(100vh - 156px);
    width: 2px;
    top: 0;
    z-index: 5;
    cursor: col-resize;
  }

  .arrow-right {
    /* 左侧面板收起后的展开入口，贴边展示避免遮挡编辑器内容 */
    position: absolute;
    top: 15px;
    z-index: 5;
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
    font-size: 12px;
    background: #fff;
    &:hover {
      padding-left: 4px;
      width: 24px;
      .ed-icon {
        color: var(--ed-color-primary, #3b82f6);
      }
    }
  }

  .table-list {
    height: 100%;
    width: 240px;
    float: left;
    font-family: var(--crest-custom_font, 'PingFang');
    border-right: 1px solid rgba(31, 35, 41, 0.15);

    .list-item_primary {
      padding: 8px;
    }
    .table-list-top {
      padding: 16px;
      padding-bottom: 0;
    }

    .select-ds {
      font-size: 14px;
      font-weight: 500;
      display: flex;
      justify-content: space-between;
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

    .table-checkbox-list {
      height: calc(100% - 190px);
      overflow-y: auto;
      padding: 0 8px;

      .list-item_primary {
        padding-right: 4px;
        .label {
          width: calc(100% - 4px);
        }
        &:hover {
          .label {
            width: calc(100% - 74px);
          }
        }
      }

      .not-allow {
        cursor: not-allowed;
        color: var(--crestTextDisable, #bbbfc4);
      }

      .name-copy {
        display: none;
        line-height: 24px;
        margin-left: 4px;
      }

      .list-item_primary:hover {
        .name-copy {
          display: inline;
        }
      }
    }
  }

  .sql-code-right {
    float: right;
    height: calc(100vh - 156px);
    position: relative;
    .sql-result {
      font-family: var(--crest-custom_font, 'PingFang');
      font-size: 14px;
      overflow-y: auto;
      box-sizing: border-box;
      width: 100%;

      .sql-title {
        user-select: none;
        display: flex;
        align-items: center;
        position: relative;
        z-index: 5;
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
        width: calc(100% - 48px);
        .border-bottom-tab(24px);
      }

      .table-sql {
        margin-top: 1px;
        height: calc(100% - 46px);
        width: 100%;
        overflow: auto;
        box-sizing: border-box;

        .table-scroll {
          .ed-table-v2 {
            --ed-table-header-bg-color: #f5f6f7;
            :deep(.header-cell) {
              border-top: none;
            }
          }
          width: 100%;
          height: 100%;
        }
      }

      .crest-status {
        position: relative;
        margin-left: 15px;

        &::before {
          content: '';
          position: absolute;
          top: 50%;
          left: -13px;
          transform: translateY(-50%);
          width: 5px;
          height: 5px;
          border-radius: 50%;
        }
      }

      .crest-Pending-result,
      .crest-Underway-result {
        &::before {
          background: var(--crestTextPlaceholder, #8f959e);
        }
      }

      .crest-Exec-result,
      .crest-Underway-pre {
        &::before {
          background: var(--ed-color-primary, #3b82f6);
        }
      }

      .crest-Stopped-result,
      .crest-Completed-pre {
        &::before {
          background: var(--crestSuccess, #34c724);
        }
      }

      .crest-Error-pre {
        &::before {
          background: var(--crestDanger, #f54a45);
        }

        .ed-icon-s-order {
          color: var(--ed-color-primary, #3b82f6);
          cursor: pointer;
        }
      }
    }

    &.p280 {
      padding-right: 280px;
    }

    .handle-system {
      height: 100%;
      width: 280px;
      border-left: 1px solid #1f232926;
      position: absolute;
      right: 0;
      top: 0;
      overflow-y: auto;
      .handle-system_title {
        padding: 16px;
        font-size: 14px;
        font-weight: 500;
        line-height: 22px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        height: 54px;
      }

      .handle-system_list {
        padding: 16px;
        border-top: 1px solid #1f232926;

        .system-list {
          height: calc(100vh - 300px);
          overflow-y: auto;

          .built-in {
            font-size: 14px;
            font-weight: 400;
            line-height: 22px;
          }

          .variable-item {
            cursor: pointer;
            padding: 1px 8px;
            border: solid 1px #dee0e3;
            margin-bottom: 8px;
            background-color: white;
            color: #1f2329;
            font-size: 14px;

            .ed-icon {
              font-size: 16px;
              margin-right: 4px;
            }
            height: 28px;
            margin-top: 4px;
            word-break: break-all;
            border-radius: 6px;

            .icon-right {
              display: none;
              margin-left: auto;
              align-items: center;
              .ed-icon {
                margin: 0 0 0 6px;
              }
            }
            &:hover {
              border-color: var(--ed-color-primary, #3b82f6);
              background: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1));
            }
          }

          .with-type:hover {
            background: rgba(4, 180, 156, 0.1);
            border-color: #04b49c;
          }
        }
      }
    }

    .table-container {
      height: calc(100% - 46px);
      padding: 16px 24px;
    }
  }
}

.add-sql-name {
  height: 56px;
  width: 100%;
  padding: 0 16px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid rgba(31, 35, 41, 0.15);
  .name {
    width: 240px;
    margin: 8px;
  }

  .save-or-cancel {
    margin-left: auto;

    .ed-button--primary:focus {
      color: var(--ed-button-hover-text-color);
      border-color: var(--ed-color-primary);
      background-color: var(--ed-color-primary);
    }

    .ed-button--primary:hover {
      color: var(--ed-button-hover-text-color);
      border-color: var(--ed-button-hover-border-color);
      background-color: var(--ed-button-hover-bg-color);
    }

    .ed-button--primary:active {
      color: var(--ed-button-active-text-color);
      border-color: var(--ed-button-active-border-color);
      background-color: var(--ed-button-active-bg-color);
    }
    .ed-divider--vertical {
      margin: 0 10px 0 16px;
    }

    .is-text:not(.system-text_bg):hover {
      background: rgba(31, 35, 41, 0.1);
    }

    .system-text_bg {
      color: #1f2329;
      &:hover {
        background: #1f23291a;
      }

      &:active {
        background: #1f232933;
      }

      &:focus {
        background: var(--ed-color-primary-1a, #3b82f61a);
        color: var(--ed-color-primary, #3b82f6);
      }

      &:focus:hover {
        color: var(--ed-color-primary, #3b82f6);
        background: var(--ed-color-primary-33, #3b82f633);
      }
    }
  }
}
.icon-color {
  color: #646a73;
}
</style>
<style lang="less">
.no-scroll_bar {
  .ed-cascader-menu__wrap.ed-scrollbar__wrap {
    height: 240px;
  }
}
.crest-hover-icon-primary {
  color: var(--ed-color-primary) !important;
}
.sql-tips {
  color: #646a73;
  text-align: center;
  font-family: var(--crest-custom_font, 'PingFang');
  font-size: 14px;
  font-style: normal;
  font-weight: 400;
  line-height: 22px;
  margin-top: -35px;
  .ed-icon {
    margin: 0 4px;
  }
}
.sql-table-info {
  padding: 0 !important;
  height: 480px;
  .table-filed {
    height: 480px;
    .top {
      padding: 16px;
      border-bottom: 1px solid rgba(31, 35, 41, 0.15);
      .title {
        max-width: 50%;
      }
      .num {
        margin-left: auto;
        color: #646a73;
        font-family: var(--crest-custom_font, 'PingFang');
        font-size: 14px;
        font-style: normal;
        font-weight: 400;
        line-height: 22px;
        .ed-icon {
          margin-right: 4px;
          font-size: 16px;
        }
      }
    }

    .table-grid {
      padding: 16px;
      height: 423px;
      padding-bottom: 0;
      overflow-y: auto;
    }
  }
}
.tree-select-ds_popper {
  .ed-tree-node.is-current > .ed-tree-node__content:not(.is-menu):after {
    display: none !important;
  }
}
.sql-eidtor {
  .cm-scroller {
    height: 250px;
    width: 100%;
    overflow-y: auto;
  }

  .cm-focused {
    outline: none;
  }
}
.sql-dataset-drawer {
  .ed-empty__description {
    margin-top: 8px;
    p {
      line-height: 22px;
    }
  }
  .ed-input-group__prepend {
    padding: 0 11px;
    width: 163px;
  }
  .crest-group__prepend {
    .ed-date-editor {
      flex: 1;
      .ed-input__wrapper {
        border-top-left-radius: 0;
        border-bottom-left-radius: 0;
        width: 100%;
      }
    }
  }

  .ed-date-editor {
    width: 100%;
  }

  .select-type {
    .ed-input__wrapper {
      padding-left: 32px !important;
    }
  }

  .select-svg-icon {
    position: absolute;
    left: 24px;
    top: 19px;
  }

  .content {
    height: 80px;
    width: 822px;
    border-radius: 6px;
    background: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1));
    position: relative;
    line-height: 22px;
    padding: 9px 0 9px 40px;
    font-family: var(--crest-custom_font, 'PingFang');
    font-size: 14px;
    font-weight: 400;

    .ed-icon {
      position: absolute;
      top: 10.6px;
      left: 16px;
      font-size: 14px;
      color: var(--ed-color-primary, #3b82f6);
    }

    margin-bottom: 16px;
  }
}
.cascader-panel {
  .ed-cascader-node__label {
    display: flex;
    align-items: center;
    .ed-icon {
      margin-right: 5px;
    }
  }
}
</style>
