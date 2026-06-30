<script lang="ts" setup>
import icon_bold_outlined from '@/assets/svg/icon_bold_outlined.svg'
import icon_italic_outlined from '@/assets/svg/icon_italic_outlined.svg'
import icon_leftAlignment_outlined from '@/assets/svg/icon_left-alignment_outlined.svg'
import icon_centerAlignment_outlined from '@/assets/svg/icon_center-alignment_outlined.svg'
import icon_rightAlignment_outlined from '@/assets/svg/icon_right-alignment_outlined.svg'
import icon_customAlignment_outlined from '@/assets/svg/icon_custom-alignment_outlined.svg'
import icon_edit_outlined from '@/assets/svg/icon_edit_outlined.svg'
import { computed, onMounted, PropType, reactive, watch } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { COLOR_PANEL, DEFAULT_TABLE_HEADER } from '@/views/chart/components/editor/util/chart'
import { ElDivider, ElSpace } from 'element-plus-secondary'
import { cloneDeep, defaultsDeep, isEqual } from 'lodash-es'
import { convertToAlphaColor, isAlphaColor } from '@/views/chart/components/js/util'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import TableHeaderGroupConfig from './TableHeaderGroupConfig.vue'
import TableHeaderDescriptionConfig from './TableHeaderDescriptionConfig.vue'
import { getLeafNodes } from '@/views/chart/components/js/panel/common/common_table'
import { hasAuxiliaryDescription } from '@/views/chart/components/js/panel/common/tableAuxiliaryHeader.mjs'
import { SERIES_NUMBER_FIELD } from '@antv/s2'

const dvMainStore = dvMainStoreWithOut()
const { batchOptStatus, mobileInPc } = storeToRefs(dvMainStore)
const { t } = useI18n()

// 表头面板同时服务明细表、普通表和透视表，propertyInner 控制当前图表类型可编辑的字段集合。
const props = defineProps({
  chart: {
    type: Object as PropType<ChartObj>,
    required: true
  },
  themes: {
    type: String as PropType<EditorTheme>,
    default: 'dark'
  },
  propertyInner: {
    type: Array<string>
  }
})

// 表头配置依赖字段顺序，字段显隐或指标轴变化后需要重新计算分组、辅助说明和对齐候选项。
watch(
  [() => props.chart.customAttr.tableHeader, () => props.chart.xAxis, () => props.chart.yAxis],
  () => {
    init()
  },
  { deep: true }
)

const predefineColors = COLOR_PANEL

// 表头字号覆盖普通表头和透视表行、列、角头，区间上限需要满足大屏展示场景。
const fontSizeList = computed(() => {
  const arr = []
  for (let i = 10; i <= 40; i = i + 2) {
    arr.push({
      name: i + '',
      value: i
    })
  }
  for (let i = 50; i <= 200; i = i + 10) {
    arr.push({
      name: i + '',
      value: i
    })
  }
  return arr
})

// 表单状态保存当前编辑值，两个弹窗分别管理复杂表头分组和辅助说明配置。
const state = reactive({
  tableHeaderForm: {} as ChartTableHeaderAttr,
  showTableHeaderGroupConfig: false,
  showTableHeaderDescriptionConfig: false
})

// 父组件负责持久化表头配置并触发图表重绘，本面板只提交变更后的表单对象。
const emit = defineEmits(['onTableHeaderChange'])

// 自定义对齐需要把当前字段选择器里的临时值写回完整 alignConfig 后再上报。
const changeTableHeader = prop => {
  if (prop === 'alignConfig') {
    // 字段级对齐只更新当前选中字段，其余字段沿用原配置。
    state.tableHeaderForm.alignConfig = alignConfigOptions.map(item => ({
      id: item.id,
      align: item.id === alignConfig.id ? alignConfig.align : item.align
    }))
  }
  emit('onTableHeaderChange', state.tableHeaderForm, prop)
}

// 保存复杂表头分组后关闭弹窗，并通过同一事件链路触发表格重新渲染。
const changeHeaderGroupConfig = (headerGroupConfig: ChartTableHeaderAttr['headerGroupConfig']) => {
  state.tableHeaderForm.headerGroupConfig = headerGroupConfig
  state.showTableHeaderGroupConfig = false
  changeTableHeader('headerGroupConfig')
}

// 辅助表头描述独立于复杂分组，可在无分组表头时为叶子字段追加说明行。
const changeAuxiliaryHeaderConfig = (auxiliaryHeader: ChartTableHeaderAttr['auxiliaryHeader']) => {
  state.tableHeaderForm.auxiliaryHeader = auxiliaryHeader
  state.showTableHeaderDescriptionConfig = false
  changeTableHeader('auxiliaryHeader')
}

// 批量编辑模式不允许打开结构化配置弹窗，避免一次操作写入多张表格的字段结构。
const enableGroupConfig = computed(() => {
  return (
    !batchOptStatus.value &&
    showProperty('headerGroup') &&
    state.tableHeaderForm.headerGroup &&
    state.tableHeaderForm.showTableHeader !== false
  )
})

// 当前可见字段用于校验复杂表头和辅助说明，普通表需要同时包含维度列和指标列。
const visibleTableFields = computed(() => {
  const axis = [...(props.chart?.xAxis || [])]
  if (props.chart?.type === 'table-normal') {
    axis.push(...(props.chart?.yAxis || []))
  }
  return axis.filter(item => item.hide !== true)
})

// 辅助表头只有在表头显示且配置开关开启时可编辑，隐藏表头时保留配置但不开放入口。
const enableAuxiliaryConfig = computed(() => {
  return (
    !batchOptStatus.value &&
    showProperty('auxiliaryHeader') &&
    state.tableHeaderForm.auxiliaryHeader?.enabled &&
    state.tableHeaderForm.showTableHeader !== false
  )
})

// 描述有效性按当前可见字段校验，字段被隐藏或重命名后需要重新确认说明配置。
const auxiliaryConfigValid = computed(() => {
  return hasAuxiliaryDescription(
    state.tableHeaderForm.auxiliaryHeader,
    visibleTableFields.value.map(item => item.engineFieldName)
  )
})

// 复杂表头必须完整覆盖当前叶子字段并保持顺序一致，否则导出和渲染会出现列错位。
const groupConfigValid = computed(() => {
  const columns = props.chart?.customAttr?.tableHeader?.headerGroupConfig?.columns
  if (!columns?.length) {
    return false
  }
  const noGroup = columns.every(item => !item.children?.length)
  if (noGroup) {
    return false
  }
  // 复杂表头叶子节点必须与当前可见字段一一对应。
  const allAxis = [...props.chart?.xAxis]
  if (props.chart.type === 'table-normal') {
    allAxis.push(...props.chart?.yAxis)
  }
  const showColumns = []
  allAxis?.forEach(axis => {
    axis.hide !== true && showColumns.push({ key: axis.engineFieldName })
  })
  if (!showColumns.length) {
    return false
  }
  const showColumnFields = showColumns.map(item => item.key)
  const leafNodes = getLeafNodes(columns as Array<ColumnNode>)
  const leafKeys = leafNodes.map(item => item.key)
  return isEqual(showColumnFields, leafKeys)
})

// 当前正在编辑的字段对齐配置是表单内的临时游标，保存时会合并回 alignConfig 数组。
const alignConfig = reactive({
  id: '',
  align: 'left'
})
// 字段对齐候选项来自当前可见列，序号列开启时会作为特殊字段加入列表。
const alignConfigOptions = reactive([])
// 切换字段时同步该字段已保存的对齐方式，避免用户误以为所有字段共用一个值。
const changeAlignConfig = () => {
  const selected = state.tableHeaderForm.alignConfig.find(item => item.id === alignConfig.id)
  if (selected) {
    alignConfig.align = selected.align
  }
}
// 自定义列对齐只对普通表格有效，透视表的行列头由 S2 布局统一控制。
const showCustomAlign = computed(() => {
  return ['table-info', 'table-normal'].includes(props.chart.type)
})
// 初始化时合并默认值，并为旧版透视表补齐行头、列头、角头三套独立样式字段。
const init = () => {
  const tableHeader = props.chart?.customAttr?.tableHeader
  if (tableHeader) {
    // 旧配置只有一套表头样式，打开编辑器时映射到透视表三块区域，避免历史图表样式丢失。
    if (!tableHeader.tableHeaderColBgColor) {
      tableHeader.tableHeaderColBgColor = tableHeader.tableHeaderBgColor
      tableHeader.tableHeaderColFontColor = tableHeader.tableHeaderFontColor
      tableHeader.tableTitleColFontSize = tableHeader.tableTitleFontSize
      tableHeader.tableHeaderColAlign = tableHeader.tableHeaderAlign
      tableHeader.isColBolder = tableHeader.isBolder
      tableHeader.isColItalic = tableHeader.isItalic

      tableHeader.tableHeaderCornerBgColor = tableHeader.tableHeaderBgColor
      tableHeader.tableHeaderCornerFontColor = tableHeader.tableHeaderFontColor
      tableHeader.tableTitleCornerFontSize = tableHeader.tableTitleFontSize
      tableHeader.tableHeaderCornerAlign = tableHeader.tableHeaderAlign
      tableHeader.isCornerBolder = tableHeader.isBolder
      tableHeader.isCornerItalic = tableHeader.isItalic
    }
    state.tableHeaderForm = defaultsDeep(cloneDeep(tableHeader), cloneDeep(DEFAULT_TABLE_HEADER))
    if (!isAlphaColor(state.tableHeaderForm.tableHeaderBgColor)) {
      // 旧图表保存的是纯色值，补上透明度后才能和当前颜色选择器及导出渲染保持一致。
      const alpha = props.chart.customAttr.basicStyle.alpha
      state.tableHeaderForm.tableHeaderBgColor = convertToAlphaColor(
        state.tableHeaderForm.tableHeaderBgColor,
        alpha
      )
    }
  }
  if (['table-info', 'table-normal'].includes(props.chart.type)) {
    // 明细表和普通表的自定义对齐按字段维护，字段顺序变化后要重建候选项。
    const axis = [...props.chart?.xAxis]
    if (props.chart?.type === 'table-normal') {
      axis.push(...props.chart?.yAxis)
    }
    const alignCfg = props.chart?.customAttr?.tableHeader?.alignConfig || []
    // 将已保存的字段对齐数组转换为 Map，便于对新增字段设置默认对齐。
    const alignCfgMap = alignCfg?.reduce((p, n) => {
      p[n.id] = n.align
      return p
    }, {})
    alignConfigOptions.splice(0, alignConfigOptions.length)
    if (tableHeader?.showIndex) {
      // 序号列使用 S2 固定字段名，需要和真实字段一起参与自定义对齐。
      alignConfigOptions.push({
        id: SERIES_NUMBER_FIELD,
        label: tableHeader.indexLabel,
        align: alignCfgMap[SERIES_NUMBER_FIELD] || 'left'
      })
    }
    axis.forEach(item => {
      // 候选项按轴顺序生成，字段隐藏后的有效性由渲染层和分组校验处理。
      const align = alignCfgMap[item.engineFieldName] || 'left'
      alignConfigOptions.push({
        id: item.engineFieldName,
        label: item.chartShowName ?? item.name,
        align
      })
    })
    if (alignConfigOptions.length) {
      const exist = alignConfigOptions.findIndex(item => item.id === alignConfig.id) !== -1
      if (!exist) {
        alignConfig.id = alignConfigOptions[0].id
        alignConfig.align = alignConfigOptions[0].align
      }
    } else {
      alignConfig.id = ''
      alignConfig.align = 'left'
    }
  }
}
// 属性显示由图表类型定义，避免面板展示当前渲染器不支持的表头设置。
const showProperty = prop => props.propertyInner?.includes(prop)

onMounted(() => {
  init()
})
</script>

<template>
  <el-form
    :model="state.tableHeaderForm"
    :disabled="!state.tableHeaderForm.showTableHeader"
    ref="tableHeaderForm"
    label-position="top"
    size="small"
  >
    <!-- 普通表头区域覆盖明细表、普通表和透视表行头的基础样式。 -->
    <el-form-item
      :label="
        chart.type === 'table-pivot' ? t('chart.rowBackgroundColor') : t('chart.backgroundColor')
      "
      class="form-item"
      :class="'form-item-' + themes"
      v-if="showProperty('tableHeaderBgColor') && state.tableHeaderForm.tableHeaderBgColor"
    >
      <el-color-picker
        :effect="themes"
        v-model="state.tableHeaderForm.tableHeaderBgColor"
        is-custom
        :trigger-width="108"
        :predefine="predefineColors"
        show-alpha
        @change="changeTableHeader('tableHeaderBgColor')"
      />
    </el-form-item>

    <el-space>
      <el-form-item
        class="form-item"
        :class="'form-item-' + themes"
        v-if="showProperty('tableHeaderFontColor')"
        :label="t('chart.text')"
      >
        <el-color-picker
          :effect="themes"
          v-model="state.tableHeaderForm.tableHeaderFontColor"
          is-custom
          :predefine="predefineColors"
          @change="changeTableHeader('tableHeaderFontColor')"
        />
      </el-form-item>
      <el-form-item
        class="form-item"
        :class="'form-item-' + themes"
        v-if="showProperty('tableTitleFontSize')"
      >
        <template #label>&nbsp;</template>
        <el-select
          style="width: 58px"
          :effect="themes"
          v-model="state.tableHeaderForm.tableTitleFontSize"
          @change="changeTableHeader('tableTitleFontSize')"
        >
          <el-option
            v-for="option in fontSizeList"
            :key="option.value"
            :label="option.name"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
    </el-space>
    <el-space :class="{ 'mobile-style': mobileInPc }">
      <el-form-item class="form-item" :class="'form-item-' + themes">
        <el-checkbox
          :effect="themes"
          class="icon-checkbox"
          v-model="state.tableHeaderForm.isBolder"
          @change="changeTableHeader('isBolder')"
        >
          <el-tooltip effect="dark" placement="top">
            <template #content>
              {{ t('chart.bolder') }}
            </template>
            <div
              class="icon-btn"
              :class="{ dark: themes === 'dark', active: state.tableHeaderForm.isBolder }"
            >
              <el-icon>
                <Icon name="icon_bold_outlined"><icon_bold_outlined class="svg-icon" /></Icon>
              </el-icon>
            </div>
          </el-tooltip>
        </el-checkbox>
      </el-form-item>

      <el-form-item class="form-item" :class="'form-item-' + themes">
        <el-checkbox
          :effect="themes"
          class="icon-checkbox"
          v-model="state.tableHeaderForm.isItalic"
          @change="changeTableHeader('isItalic')"
        >
          <el-tooltip effect="dark" placement="top">
            <template #content>
              {{ t('chart.italic') }}
            </template>
            <div
              class="icon-btn"
              :class="{ dark: themes === 'dark', active: state.tableHeaderForm.isItalic }"
            >
              <el-icon>
                <Icon name="icon_italic_outlined"><icon_italic_outlined class="svg-icon" /></Icon>
              </el-icon>
            </div>
          </el-tooltip>
        </el-checkbox>
      </el-form-item>

      <div class="position-divider" :class="'position-divider--' + themes"></div>
      <el-form-item
        class="form-item"
        :class="'form-item-' + themes"
        v-if="showProperty('tableHeaderAlign')"
      >
        <el-radio-group
          class="icon-radio-group"
          v-model="state.tableHeaderForm.tableHeaderAlign"
          @change="changeTableHeader('tableHeaderAlign')"
        >
          <el-radio value="left">
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.text_pos_left') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: state.tableHeaderForm.tableHeaderAlign === 'left'
                }"
              >
                <el-icon>
                  <Icon name="icon_left-alignment_outlined"
                    ><icon_leftAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
          <el-radio value="center">
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.text_pos_center') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: state.tableHeaderForm.tableHeaderAlign === 'center'
                }"
              >
                <el-icon>
                  <Icon name="icon_center-alignment_outlined"
                    ><icon_centerAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
          <el-radio value="right">
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.text_pos_right') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: state.tableHeaderForm.tableHeaderAlign === 'right'
                }"
              >
                <el-icon>
                  <Icon name="icon_right-alignment_outlined"
                    ><icon_rightAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
          <el-radio label="custom" v-if="showCustomAlign">
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('commons.custom') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: state.tableHeaderForm.tableHeaderAlign === 'custom'
                }"
              >
                <el-icon>
                  <Icon name="icon_custom-alignment_outlined"
                    ><icon_customAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
        </el-radio-group>
      </el-form-item>
    </el-space>
    <el-row
      v-if="showProperty('tableHeaderAlign') && state.tableHeaderForm.tableHeaderAlign === 'custom'"
    >
      <!-- 自定义对齐按字段逐个维护，提交时合并回 alignConfig 数组。 -->
      <el-col :span="12">
        <el-select :effect="themes" v-model="alignConfig.id" @change="changeAlignConfig">
          <el-option
            v-for="item in alignConfigOptions"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
      </el-col>
      <el-col :offset="1" :span="11" style="display: flex; align-items: center">
        <el-radio-group
          class="icon-radio-group"
          v-model="alignConfig.align"
          @change="changeTableHeader('alignConfig')"
        >
          <el-radio label="left">
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.text_pos_left') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: alignConfig.align === 'left'
                }"
              >
                <el-icon>
                  <Icon name="icon_left-alignment_outlined"
                    ><icon_leftAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
          <el-radio label="center">
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.text_pos_center') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: alignConfig.align === 'center'
                }"
              >
                <el-icon>
                  <Icon name="icon_center-alignment_outlined"
                    ><icon_centerAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
          <el-radio label="right">
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.text_pos_right') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: alignConfig.align === 'right'
                }"
              >
                <el-icon>
                  <Icon name="icon_right-alignment_outlined"
                    ><icon_rightAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
        </el-radio-group>
      </el-col>
    </el-row>
    <template v-if="chart.type === 'table-pivot' && showProperty('tableHeaderBgColor')">
      <el-divider class="m-divider" :class="{ 'divider-dark': themes === 'dark' }" />
      <!-- 透视表列头、角头与行头独立配置，避免一套样式无法区分三类表头区域。 -->
      <el-form-item
        :label="t('chart.colBackgroundColor')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-color-picker
          :effect="themes"
          v-model="state.tableHeaderForm.tableHeaderColBgColor"
          is-custom
          :trigger-width="108"
          :predefine="predefineColors"
          show-alpha
          @change="changeTableHeader('tableHeaderColBgColor')"
        />
      </el-form-item>
      <el-space>
        <el-form-item
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('tableHeaderFontColor')"
          :label="t('chart.text')"
        >
          <el-color-picker
            :effect="themes"
            v-model="state.tableHeaderForm.tableHeaderColFontColor"
            is-custom
            :predefine="predefineColors"
            @change="changeTableHeader('tableHeaderColFontColor')"
          />
        </el-form-item>
        <el-form-item
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('tableTitleFontSize')"
        >
          <template #label>&nbsp;</template>
          <el-select
            style="width: 58px"
            :effect="themes"
            v-model="state.tableHeaderForm.tableTitleColFontSize"
            @change="changeTableHeader('tableTitleColFontSize')"
          >
            <el-option
              v-for="option in fontSizeList"
              :key="option.value"
              :label="option.name"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </el-space>
      <el-space :class="{ 'mobile-style': mobileInPc }">
        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-checkbox
            :effect="themes"
            class="icon-checkbox"
            v-model="state.tableHeaderForm.isColBolder"
            @change="changeTableHeader('isColBolder')"
          >
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.bolder') }}
              </template>
              <div
                class="icon-btn"
                :class="{ dark: themes === 'dark', active: state.tableHeaderForm.isColBolder }"
              >
                <el-icon>
                  <Icon name="icon_bold_outlined"><icon_bold_outlined class="svg-icon" /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-checkbox>
        </el-form-item>

        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-checkbox
            :effect="themes"
            class="icon-checkbox"
            v-model="state.tableHeaderForm.isColItalic"
            @change="changeTableHeader('isColItalic')"
          >
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.italic') }}
              </template>
              <div
                class="icon-btn"
                :class="{ dark: themes === 'dark', active: state.tableHeaderForm.isColItalic }"
              >
                <el-icon>
                  <Icon name="icon_italic_outlined"><icon_italic_outlined class="svg-icon" /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-checkbox>
        </el-form-item>

        <div class="position-divider" :class="'position-divider--' + themes"></div>
        <el-form-item
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('tableHeaderAlign')"
        >
          <el-radio-group
            class="icon-radio-group"
            v-model="state.tableHeaderForm.tableHeaderColAlign"
            @change="changeTableHeader('tableHeaderColAlign')"
          >
            <el-radio value="left">
              <el-tooltip effect="dark" placement="top">
                <template #content>
                  {{ t('chart.text_pos_left') }}
                </template>
                <div
                  class="icon-btn"
                  :class="{
                    dark: themes === 'dark',
                    active: state.tableHeaderForm.tableHeaderColAlign === 'left'
                  }"
                >
                  <el-icon>
                    <Icon name="icon_left-alignment_outlined"
                      ><icon_leftAlignment_outlined class="svg-icon"
                    /></Icon>
                  </el-icon>
                </div>
              </el-tooltip>
            </el-radio>
            <el-radio value="center">
              <el-tooltip effect="dark" placement="top">
                <template #content>
                  {{ t('chart.text_pos_center') }}
                </template>
                <div
                  class="icon-btn"
                  :class="{
                    dark: themes === 'dark',
                    active: state.tableHeaderForm.tableHeaderColAlign === 'center'
                  }"
                >
                  <el-icon>
                    <Icon name="icon_center-alignment_outlined"
                      ><icon_centerAlignment_outlined class="svg-icon"
                    /></Icon>
                  </el-icon>
                </div>
              </el-tooltip>
            </el-radio>
            <el-radio value="right">
              <el-tooltip effect="dark" placement="top">
                <template #content>
                  {{ t('chart.text_pos_right') }}
                </template>
                <div
                  class="icon-btn"
                  :class="{
                    dark: themes === 'dark',
                    active: state.tableHeaderForm.tableHeaderColAlign === 'right'
                  }"
                >
                  <el-icon>
                    <Icon name="icon_right-alignment_outlined"
                      ><icon_rightAlignment_outlined class="svg-icon"
                    /></Icon>
                  </el-icon>
                </div>
              </el-tooltip>
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-space>

      <el-divider class="m-divider" :class="{ 'divider-dark': themes === 'dark' }" />
      <el-form-item
        :label="t('chart.cornerBackgroundColor')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-color-picker
          :effect="themes"
          v-model="state.tableHeaderForm.tableHeaderCornerBgColor"
          is-custom
          :trigger-width="108"
          :predefine="predefineColors"
          show-alpha
          @change="changeTableHeader('tableHeaderCornerBgColor')"
        />
      </el-form-item>
      <el-space>
        <el-form-item
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('tableHeaderFontColor')"
          :label="t('chart.text')"
        >
          <el-color-picker
            :effect="themes"
            v-model="state.tableHeaderForm.tableHeaderCornerFontColor"
            is-custom
            :predefine="predefineColors"
            @change="changeTableHeader('tableHeaderCornerFontColor')"
          />
        </el-form-item>
        <el-form-item
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('tableTitleFontSize')"
        >
          <template #label>&nbsp;</template>
          <el-select
            style="width: 58px"
            :effect="themes"
            v-model="state.tableHeaderForm.tableTitleCornerFontSize"
            @change="changeTableHeader('tableTitleCornerFontSize')"
          >
            <el-option
              v-for="option in fontSizeList"
              :key="option.value"
              :label="option.name"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </el-space>
      <el-space :class="{ 'mobile-style': mobileInPc }">
        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-checkbox
            :effect="themes"
            class="icon-checkbox"
            v-model="state.tableHeaderForm.isCornerBolder"
            @change="changeTableHeader('isCornerBolder')"
          >
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.bolder') }}
              </template>
              <div
                class="icon-btn"
                :class="{ dark: themes === 'dark', active: state.tableHeaderForm.isCornerBolder }"
              >
                <el-icon>
                  <Icon name="icon_bold_outlined"><icon_bold_outlined class="svg-icon" /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-checkbox>
        </el-form-item>

        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-checkbox
            :effect="themes"
            class="icon-checkbox"
            v-model="state.tableHeaderForm.isCornerItalic"
            @change="changeTableHeader('isCornerItalic')"
          >
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.italic') }}
              </template>
              <div
                class="icon-btn"
                :class="{ dark: themes === 'dark', active: state.tableHeaderForm.isCornerItalic }"
              >
                <el-icon>
                  <Icon name="icon_italic_outlined"><icon_italic_outlined class="svg-icon" /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-checkbox>
        </el-form-item>

        <div class="position-divider" :class="'position-divider--' + themes"></div>
        <el-form-item
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('tableHeaderAlign')"
        >
          <el-radio-group
            class="icon-radio-group"
            v-model="state.tableHeaderForm.tableHeaderCornerAlign"
            @change="changeTableHeader('tableHeaderCornerAlign')"
          >
            <el-radio value="left">
              <el-tooltip effect="dark" placement="top">
                <template #content>
                  {{ t('chart.text_pos_left') }}
                </template>
                <div
                  class="icon-btn"
                  :class="{
                    dark: themes === 'dark',
                    active: state.tableHeaderForm.tableHeaderCornerAlign === 'left'
                  }"
                >
                  <el-icon>
                    <Icon name="icon_left-alignment_outlined"
                      ><icon_leftAlignment_outlined class="svg-icon"
                    /></Icon>
                  </el-icon>
                </div>
              </el-tooltip>
            </el-radio>
            <el-radio value="center">
              <el-tooltip effect="dark" placement="top">
                <template #content>
                  {{ t('chart.text_pos_center') }}
                </template>
                <div
                  class="icon-btn"
                  :class="{
                    dark: themes === 'dark',
                    active: state.tableHeaderForm.tableHeaderCornerAlign === 'center'
                  }"
                >
                  <el-icon>
                    <Icon name="icon_center-alignment_outlined"
                      ><icon_centerAlignment_outlined class="svg-icon"
                    /></Icon>
                  </el-icon>
                </div>
              </el-tooltip>
            </el-radio>
            <el-radio value="right">
              <el-tooltip effect="dark" placement="top">
                <template #content>
                  {{ t('chart.text_pos_right') }}
                </template>
                <div
                  class="icon-btn"
                  :class="{
                    dark: themes === 'dark',
                    active: state.tableHeaderForm.tableHeaderCornerAlign === 'right'
                  }"
                >
                  <el-icon>
                    <Icon name="icon_right-alignment_outlined"
                      ><icon_rightAlignment_outlined class="svg-icon"
                    /></Icon>
                  </el-icon>
                </div>
              </el-tooltip>
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-space>

      <el-divider class="m-divider" :class="{ 'divider-dark': themes === 'dark' }" />
    </template>

    <el-row :gutter="8">
      <el-col :span="12">
        <el-form-item
          :label="t('visualization.lineHeight')"
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('tableTitleHeight')"
        >
          <el-input-number
            :effect="themes"
            controls-position="right"
            v-model="state.tableHeaderForm.tableTitleHeight"
            :min="20"
            :max="1000"
            @change="changeTableHeader('tableTitleHeight')"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <el-form-item class="form-item" :class="'form-item-' + themes" v-if="showProperty('showIndex')">
      <!-- 序号列作为虚拟字段参与渲染和对齐配置，开启后会同步加入字段候选项。 -->
      <el-checkbox
        size="small"
        :effect="themes"
        v-model="state.tableHeaderForm.showIndex"
        @change="changeTableHeader('showIndex')"
      >
        {{ t('chart.table_show_index') }}
      </el-checkbox>
    </el-form-item>
    <el-form-item
      :label="t('chart.table_index_desc')"
      class="form-item"
      :class="'form-item-' + themes"
      v-if="showProperty('showIndex') && state.tableHeaderForm.showIndex"
    >
      <!-- 序号列表头文案独立保存，便于导出和预览保持同一列名。 -->
      <el-input
        :effect="themes"
        v-model="state.tableHeaderForm.indexLabel"
        @blur="changeTableHeader('indexLabel')"
      />
    </el-form-item>
    <el-form-item
      class="form-item"
      :class="'form-item-' + themes"
      v-if="showProperty('tableHeaderSort')"
    >
      <!-- 表头排序仅开放给支持列排序的表格渲染器，避免图表类型间配置串用。 -->
      <el-checkbox
        size="small"
        :effect="themes"
        v-model="state.tableHeaderForm.tableHeaderSort"
        @change="changeTableHeader('tableHeaderSort')"
      >
        {{ t('chart.table_header_sort') }}
      </el-checkbox>
    </el-form-item>
    <el-form-item
      class="form-item"
      :class="'form-item-' + themes"
      v-if="showProperty('showHorizonBorder')"
    >
      <el-checkbox
        size="small"
        :effect="themes"
        v-model="state.tableHeaderForm.showHorizonBorder"
        @change="changeTableHeader('showHorizonBorder')"
      >
        {{ t('chart.table_header_show_horizon_border') }}
      </el-checkbox>
    </el-form-item>
    <el-form-item
      class="form-item"
      :class="'form-item-' + themes"
      v-if="showProperty('showVerticalBorder')"
    >
      <el-checkbox
        size="small"
        :effect="themes"
        v-model="state.tableHeaderForm.showVerticalBorder"
        @change="changeTableHeader('showVerticalBorder')"
      >
        {{ t('chart.table_header_show_vertical_border') }}
      </el-checkbox>
    </el-form-item>
    <el-form-item
      class="form-item"
      :class="'form-item-' + themes"
      v-if="showProperty('rowHeaderFreeze')"
    >
      <!-- 透视表行头冻结由 S2 表格布局处理，配置面板只负责保存开关状态。 -->
      <el-checkbox
        size="small"
        :effect="themes"
        v-model="state.tableHeaderForm.rowHeaderFreeze"
        @change="changeTableHeader('rowHeaderFreeze')"
      >
        {{ t('chart.table_row_header_freeze') }}
      </el-checkbox>
    </el-form-item>
    <el-form-item
      v-if="!batchOptStatus && showProperty('headerGroup')"
      class="form-item"
      :class="'form-item-' + themes"
      :disabled="!state.tableHeaderForm.showTableHeader"
    >
      <!-- 复杂表头会改变列结构，批量编辑时关闭入口以避免跨图表字段错配。 -->
      <el-checkbox
        size="small"
        :effect="themes"
        v-model="state.tableHeaderForm.headerGroup"
        @change="changeTableHeader('headerGroup')"
      >
        {{ t('chart.table_header_group') }}
      </el-checkbox>
    </el-form-item>
    <el-form-item v-if="enableGroupConfig" class="form-item" :class="'form-item-' + themes">
      <!-- 复杂表头配置保存后按叶子字段顺序校验，失效时不展示已配置状态。 -->
      <div class="header-group-config">
        <span>{{ t('chart.table_header_group_config') }}</span>
        <div class="group-icon">
          <span v-if="groupConfigValid">
            {{ t('visualization.already_setting') }}
          </span>
          <div
            class="icon-btn"
            :class="{
              dark: themes === 'dark'
            }"
          >
            <el-icon @click="state.showTableHeaderGroupConfig = true">
              <Icon>
                <icon_edit_outlined class="svg-icon" />
              </Icon>
            </el-icon>
          </div>
        </div>
      </div>
    </el-form-item>
    <el-form-item
      v-if="
        !batchOptStatus && showProperty('auxiliaryHeader') && state.tableHeaderForm.auxiliaryHeader
      "
      class="form-item"
      :class="'form-item-' + themes"
      :disabled="!state.tableHeaderForm.showTableHeader"
    >
      <!-- 辅助说明开关只控制说明行渲染，说明内容在下方弹窗中按字段维护。 -->
      <el-checkbox
        size="small"
        :effect="themes"
        v-model="state.tableHeaderForm.auxiliaryHeader.enabled"
        @change="changeTableHeader('auxiliaryHeader')"
      >
        {{ t('chart.table_header_description') }}
      </el-checkbox>
    </el-form-item>
    <el-form-item v-if="enableAuxiliaryConfig" class="form-item" :class="'form-item-' + themes">
      <!-- 辅助说明只附加到叶子字段，不改变复杂表头分组结构。 -->
      <div class="header-group-config">
        <span>{{ t('chart.table_header_description_config') }}</span>
        <div class="group-icon">
          <span v-if="auxiliaryConfigValid">
            {{ t('visualization.already_setting') }}
          </span>
          <div
            class="icon-btn"
            :class="{
              dark: themes === 'dark'
            }"
          >
            <el-icon @click="state.showTableHeaderDescriptionConfig = true">
              <Icon>
                <icon_edit_outlined class="svg-icon" />
              </Icon>
            </el-icon>
          </div>
        </div>
      </div>
    </el-form-item>
  </el-form>
  <el-dialog
    v-model="state.showTableHeaderGroupConfig"
    destroy-on-close
    append-to-body
    :effect="themes"
    :show-close="false"
    :class="themes === 'dark' ? 'table-header-group-config-dialog' : ''"
  >
    <!-- 分组配置弹窗挂载到 body，避免编辑器侧栏滚动容器裁剪拖拽区域。 -->
    <template #header>
      {{ t('chart.table_header_group_config') }}
      <span style="font-size: 12px">({{ t('chart.table_header_group_config_tip') }})</span>
    </template>
    <table-header-group-config
      :chart="chart"
      :themes="themes"
      :tableHeaderForm="state.tableHeaderForm"
      @onConfigChange="changeHeaderGroupConfig"
      @onCancelConfig="() => (state.showTableHeaderGroupConfig = false)"
    />
  </el-dialog>
  <el-dialog
    v-model="state.showTableHeaderDescriptionConfig"
    destroy-on-close
    append-to-body
    :effect="themes"
    :show-close="false"
    width="760px"
    :class="themes === 'dark' ? 'table-header-group-config-dialog' : ''"
  >
    <!-- 辅助说明配置宽度固定，保证字段、说明内容和操作区在侧栏外完整展示。 -->
    <template #header>
      {{ t('chart.table_header_description_config') }}
    </template>
    <table-header-description-config
      :chart="chart"
      :themes="themes"
      :tableHeaderForm="state.tableHeaderForm"
      @onConfigChange="changeAuxiliaryHeaderConfig"
      @onCancelConfig="() => (state.showTableHeaderDescriptionConfig = false)"
    />
  </el-dialog>
</template>

<style lang="less" scoped>
.icon-btn {
  // 表头样式按钮隐藏原生控件，只保留稳定尺寸的图标态，减少配置面板抖动。
  font-size: 16px;
  line-height: 16px;
  width: 24px;
  height: 24px;
  text-align: center;
  border-radius: 6px;
  padding-top: 4px;

  color: #1f2329;

  cursor: pointer;

  &.dark {
    color: #a6a6a6;
    &.active {
      color: var(--ed-color-primary);
      background-color: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1));
    }
    &:hover {
      background-color: rgba(255, 255, 255, 0.1);
    }
  }

  &.active {
    color: var(--ed-color-primary);
    background-color: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1));
  }

  &:hover {
    background-color: rgba(31, 35, 41, 0.1);
  }
}
.icon-radio-group {
  :deep(.ed-radio) {
    margin-right: 8px;

    &:last-child {
      margin-right: 0;
    }
  }
  :deep(.ed-radio__input) {
    display: none;
  }
  :deep(.ed-radio__label) {
    padding: 0;
  }
}
.position-divider {
  // 分隔线用于区分字体样式和对齐方式，不参与表头配置数据保存。
  width: 1px;
  height: 18px;
  margin-bottom: 8px;
  background: rgba(31, 35, 41, 0.15);

  &.position-divider--dark {
    background: rgba(235, 235, 235, 0.15);
  }
}
.icon-checkbox {
  :deep(.ed-checkbox__input) {
    display: none;
  }
  :deep(.ed-checkbox__label) {
    padding: 0;
  }
}
.mobile-style {
  // PC 内移动端预览会压缩控件间距，额外留白避免第一行按钮贴边。
  margin-top: 25px;
}
.m-divider {
  // 透视表多段表头配置共用分隔线，深浅主题只切换边框透明度。
  margin: 0 0 16px;
  border-color: rgba(31, 35, 41, 0.15);

  &.divider-dark {
    border-color: rgba(255, 255, 255, 0.15);
  }
}
.header-group-config {
  display: flex;
  width: 100%;
  justify-content: space-between;
  align-items: center;
  padding-left: 22px;
  font-size: 12px;
  .group-icon {
    display: flex;
    justify-content: center;
    flex-direction: row;
    align-items: center;
  }
}
</style>
<style lang="less">
.table-header-group-config-dialog {
  background-color: #1a1a1a;
  border: 1px solid #2a2a2a;
  .ed-dialog__header,
  .ed-dialog__body {
    color: #a6a6a6;
    background-color: #1a1a1a;
    margin-right: 0;
  }
}
</style>
