<script lang="ts" setup>
import { onMounted, PropType, reactive, watch, ref, inject, nextTick, computed } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import {
  DEFAULT_BASIC_STYLE,
  DEFAULT_TABLE_TOTAL
} from '@/views/chart/components/editor/util/chart'
import { cloneDeep, defaultsDeep, find, includes } from 'lodash-es'
import CustomAggrEdit from './CustomAggrEdit.vue'

const { t } = useI18n()

// 表格合计配置面板同时维护行总计、行小计、列总计和列小计。
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
// 合计配置、指标字段和基础布局变化后重新初始化，保持表单与图表字段一致。
watch(
  [
    () => props.chart.customAttr.tableTotal,
    () => props.chart.xAxis,
    () => props.chart.yAxis,
    () => props.chart.customAttr.basicStyle
  ],
  () => {
    init()
  },
  { deep: true }
)

// 合计聚合方式列表，CUSTOM 会打开自定义聚合字段编辑器。
const aggregations = [
  { name: t('chart.none'), value: 'NONE' },
  { name: t('chart.sum'), value: 'SUM' },
  { name: t('chart.avg'), value: 'AVG' },
  { name: t('chart.max'), value: 'MAX' },
  { name: t('chart.min'), value: 'MIN' },
  { name: t('commons.custom'), value: 'CUSTOM' }
]
// 面板状态缓存当前编辑项，避免行/列、总计/小计之间切换时互相覆盖。
const state = reactive({
  tableTotalForm: cloneDeep(DEFAULT_TABLE_TOTAL) as ChartTableTotalAttr,
  // 四类合计编辑项分别缓存当前字段和聚合方式，避免共用状态造成互相污染。
  rowSubTotalItem: {} as DeepPartial<CalcTotalCfg>,
  rowTotalItem: {} as DeepPartial<CalcTotalCfg>,
  colSubTotalItem: {} as DeepPartial<CalcTotalCfg>,
  colTotalItem: {} as DeepPartial<CalcTotalCfg>,
  totalCfg: [] as CalcTotalCfg[],
  totalCfgAttr: '',
  totalItem: {} as DeepPartial<CalcTotalCfg>,
  selectedSubTotalDimensionName: '',
  selectedSubTotalDimension: undefined as { name: string; checked: boolean },
  subTotalDimensionList: [],
  basicStyleForm: JSON.parse(JSON.stringify(DEFAULT_BASIC_STYLE)) as ChartBasicStyle
})

// 多指标按列展开时才需要独立的列字段合计标签。
const showColFieldTotalLabel = computed(() => {
  // 多指标列展开时，字段合计标签需要单独展示，避免和总计标签混淆。
  const chart = props.chart
  return (
    chart.customAttr.basicStyle.quotaPosition !== 'row' &&
    chart.xAxisExt.length &&
    chart.yAxis.length > 1
  )
})

// 多指标按行展开且非树形布局时才需要独立的行字段合计标签。
const showRowFieldTotalLabel = computed(() => {
  // 多指标行展开时，行字段合计标签只在非树表布局下展示。
  const chart = props.chart
  return (
    chart.customAttr.basicStyle.quotaPosition === 'row' &&
    chart.customAttr.basicStyle.tableLayoutMode !== 'tree' &&
    chart.xAxis.length &&
    chart.xAxisExt.length &&
    chart.yAxis.length > 1
  )
})

// 切换当前正在编辑的小计维度，后续勾选状态直接写回该维度项。
function onSelectedSubTotalDimensionNameChange(name) {
  // 小计维度下拉切换时只改变当前编辑项，实际启用状态由 checkbox 保存。
  state.selectedSubTotalDimension = find(state.subTotalDimensionList, d => d.name === name)
}

// 汇总已勾选的小计维度并保存到行小计配置。
function changeRowSubTableTotal() {
  // 小计维度列表只保存字段名，渲染层再按当前维度轴顺序恢复。
  const list = []
  for (let i = 0; i < state.subTotalDimensionList.length; i++) {
    if (state.subTotalDimensionList[i].checked) {
      list.push(state.subTotalDimensionList[i].name)
    }
  }
  state.tableTotalForm.row.subTotalsDimensions = list
  changeTableTotal('row')
}

// 初始化可显示小计的维度列表，最后一个明细维度不参与小计。
const initSubTotalDimensionList = () => {
  const list = []
  if (props.chart.xAxis.length >= 2) {
    for (let i = 0; i < props.chart.xAxis.length - 1; i++) {
      // 排除最后一个明细维度，避免小计与最细粒度重复。
      const old = includes(
        state.tableTotalForm.row.subTotalsDimensions,
        props.chart.xAxis[i].engineFieldName
      )
      list.push({
        displayName: props.chart.xAxis[i].name,
        name: props.chart.xAxis[i].engineFieldName,
        checked: !!state.tableTotalForm.row.subTotalsDimensionsNew ? old : true
      })
    }
  }
  state.subTotalDimensionList = list

  const existItem = find(
    state.subTotalDimensionList,
    s => s.name === state.selectedSubTotalDimensionName
  )
  if (existItem) {
    state.selectedSubTotalDimension = existItem
  } else {
    state.selectedSubTotalDimensionName = list[0]?.name
    state.selectedSubTotalDimension = list[0]
  }
  if (!state.tableTotalForm.row.subTotalsDimensionsNew) {
    // 旧配置没有显式维度列表时默认启用所有可小计维度，并标记为新版结构。
    state.tableTotalForm.row.subTotalsDimensionsNew = true
    changeRowSubTableTotal()
  }
}

// 向父级同步合计配置变更，由父级持久化并触发表格重绘。
const emit = defineEmits(['onTableTotalChange'])

// prop 用于标识变更范围，减少父级判断成本。
const changeTableTotal = prop => {
  emit('onTableTotalChange', state.tableTotalForm, prop)
}

// 初始化合计配置表单和默认聚合字段，并兼容旧版本缺省字段。
const init = () => {
  const tableTotal = props.chart?.customAttr?.tableTotal
  if (tableTotal) {
    if (tableTotal.row) {
      // subTotalsDimensionsNew 标记是否已经迁移到显式小计维度配置。
      tableTotal.row.subTotalsDimensionsNew = !!tableTotal.row?.subTotalsDimensionsNew
    }
    state.tableTotalForm = defaultsDeep(cloneDeep(tableTotal), cloneDeep(DEFAULT_TABLE_TOTAL))
  }
  const yAxis = props.chart.yAxis
  if (yAxis?.length > 0) {
    // 排序字段必须来自当前指标轴，字段被移除后回退到第一个指标。
    const axisArr = yAxis.map(i => i.engineFieldName)
    if (axisArr.indexOf(state.tableTotalForm.row.totalSortField) === -1) {
      state.tableTotalForm.row.totalSortField = yAxis[0].engineFieldName
    }
    if (axisArr.indexOf(state.tableTotalForm.col.totalSortField) === -1) {
      state.tableTotalForm.col.totalSortField = yAxis[0].engineFieldName
    }
  } else {
    state.tableTotalForm.row.totalSortField = ''
    state.tableTotalForm.col.totalSortField = ''
  }
  const totals = [
    { ...state.tableTotalForm.row.calcTotals },
    { ...state.tableTotalForm.row.calcSubTotals },
    { ...state.tableTotalForm.col.calcTotals },
    { ...state.tableTotalForm.col.calcSubTotals }
  ]
  totals.forEach(total => {
    // 每组总计配置都需要与当前指标轴保持同构。
    setupTotalCfg(total.cfg, yAxis)
  })
  const totalTupleArr: [DeepPartial<CalcTotalCfg>, CalcTotalCfg[]][] = [
    [state.rowTotalItem, state.tableTotalForm.row.calcTotals.cfg],
    [state.rowSubTotalItem, state.tableTotalForm.row.calcSubTotals.cfg],
    [state.colTotalItem, state.tableTotalForm.col.calcTotals.cfg],
    [state.colSubTotalItem, state.tableTotalForm.col.calcSubTotals.cfg]
  ]
  totalTupleArr.forEach(tuple => {
    const [total, totalCfg] = tuple
    if (!totalCfg.length) {
      // 没有可聚合指标时清空当前编辑项，避免面板显示失效字段。
      total.engineFieldName = ''
      total.aggregation = ''
      total.originName = ''
      return
    }
    const totalIndex = totalCfg.findIndex(i => i.engineFieldName === total.engineFieldName)
    if (totalIndex !== -1) {
      total.aggregation = totalCfg[totalIndex].aggregation
    } else {
      // 当前编辑字段已不存在时回退到第一项配置，保证下拉框有稳定值。
      total.engineFieldName = totalCfg[0].engineFieldName
      total.aggregation = totalCfg[0].aggregation
      total.originName = totalCfg[0].originName
      total.label = totalCfg[0].label
    }
  })

  const basicStyle = cloneDeep(props.chart.customAttr.basicStyle)
  state.basicStyleForm = defaultsDeep(basicStyle, cloneDeep(DEFAULT_BASIC_STYLE)) as ChartBasicStyle

  initSubTotalDimensionList()
}
// 判断指定合计配置项是否可编辑，由图表类型传入的 propertyInner 控制。
const showProperty = prop => props.propertyInner?.includes(prop)
// 根据字段选择恢复当前聚合配置，避免切换字段后沿用旧字段的聚合方式。
const changeTotal = (totalItem, totals) => {
  for (let i = 0; i < totals.length; i++) {
    const item = totals[i]
    if (item.engineFieldName === totalItem.engineFieldName) {
      // 字段切换后恢复该字段自己的聚合方式和自定义表达式。
      totalItem.aggregation = item.aggregation
      totalItem.originName = item.originName
      totalItem.label = item.label
      return
    }
  }
}
// 更新聚合方式并触发合计配置变更，自定义聚合未配置字段时暂不提交。
const changeTotalAggr = (totalItem, totals, colOrNum) => {
  // 聚合方式按字段粒度保存，同一行列方向下不同指标可以使用不同聚合。
  for (let i = 0; i < totals.length; i++) {
    const item = totals[i]
    if (item.engineFieldName === totalItem.engineFieldName) {
      item.aggregation = totalItem.aggregation
      item.label = totalItem.label
      break
    }
  }
  if (totalItem.aggregation == 'CUSTOM' && !totalItem.originName) {
    // 自定义聚合必须先配置表达式，避免提交空计算字段。
    return
  }
  changeTableTotal(colOrNum)
}
// 根据当前指标轴补齐或清理合计字段配置，保留同名字段已有聚合方式。
const setupTotalCfg = (totalCfg, axis) => {
  if (!totalCfg.length) {
    // 首次启用合计时为每个指标生成默认 SUM 聚合项。
    axis.forEach(i => {
      totalCfg.push({
        engineFieldName: i.engineFieldName,
        aggregation: 'SUM',
        label: i.chartShowName ?? i.name
      })
    })
    return
  }
  if (!axis.length) {
    // 指标轴为空时清空合计配置，避免提交无效字段。
    totalCfg.splice(0, totalCfg.length)
    return
  }
  const cfgMap = totalCfg.reduce((p, n) => {
    p[n.engineFieldName] = n
    return p
  }, {})
  totalCfg.splice(0, totalCfg.length)
  axis.forEach(i => {
    totalCfg.push({
      engineFieldName: i.engineFieldName,
      aggregation: cfgMap[i.engineFieldName] ? cfgMap[i.engineFieldName].aggregation : 'SUM',
      originName: cfgMap[i.engineFieldName] ? cfgMap[i.engineFieldName].originName : '',
      label: cfgMap[i.engineFieldName]?.label
        ? cfgMap[i.engineFieldName].label
        : i.chartShowName ?? i.name
    })
  })
}
const quota = inject('quota', () => [])
const dimension = inject('dimension', () => [])
// 自定义聚合编辑器实例用于读取和写回编辑器内部字段表单。
const calcEdit = ref()
// 控制自定义聚合编辑弹窗显隐。
const editCalcField = ref(false)
// 打开自定义聚合字段编辑器，并过滤占位指标后初始化可选字段。
const editField = (totalItem, totalCfg, attr) => {
  // 自定义聚合编辑器复用指标字段上下文，只编辑当前选中合计项。
  editCalcField.value = true
  state.totalCfg = totalCfg
  state.totalCfgAttr = attr
  state.totalItem = totalItem
  nextTick(() => {
    calcEdit.value.initEdit(
      totalItem,
      quota().filter(ele => ele.id !== '-1')
    )
  })
}
// 关闭自定义聚合字段编辑器。
const closeEditCalc = () => {
  editCalcField.value = false
}
// 保存自定义聚合字段并同步到当前合计配置项。
const confirmEditCalc = () => {
  calcEdit.value.setFieldForm()
  const obj = cloneDeep(calcEdit.value.fieldForm)
  // 只更新当前选中指标对应的自定义聚合表达式。
  state.totalCfg?.forEach(item => {
    if (item.engineFieldName === obj.engineFieldName) {
      item.originName = obj.originName
      setFieldDefaultValue(item)
      state.totalItem.originName = item.originName
    }
  })
  closeEditCalc()
  changeTableTotal(state.totalCfgAttr)
}
// 补齐自定义合计字段的图表上下文默认值，供后续计算和字段定位使用。
const setFieldDefaultValue = field => {
  // 自定义合计字段按计算字段保存，需要补齐图表和数据集上下文。
  field.extField = 2
  field.chartId = props.chart.id
  field.datasetGroupId = props.chart.tableId
  field.lastSyncTime = null
  field.columnIndex = dimension().length + quota().length
  field.extractedFieldType = field.fieldType
}
onMounted(() => {
  init()
})
</script>

<template>
  <el-form size="small" ref="tableTotalForm" :model="state.tableTotalForm" label-position="top">
    <el-divider
      v-if="showProperty('row')"
      content-position="center"
      :class="'divider-style-' + themes"
    >
      {{ t('chart.row_cfg') }}
    </el-divider>
    <el-form-item
      v-show="showProperty('row')"
      :label="t('chart.total_show')"
      class="form-item"
      :class="'form-item-' + themes"
    >
      <el-checkbox
        :effect="themes"
        v-model="state.tableTotalForm.row.showGrandTotals"
        @change="changeTableTotal('row.showGrandTotals')"
      >
        {{ t('chart.show') }}
      </el-checkbox>
    </el-form-item>
    <div v-show="showProperty('row') && state.tableTotalForm.row.showGrandTotals">
      <el-form-item
        :label="t('chart.total_position')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-radio-group
          :effect="themes"
          v-model="state.tableTotalForm.row.reverseLayout"
          @change="changeTableTotal('row.reverseLayout')"
        >
          <el-radio :effect="themes" :label="true">{{ t('chart.total_pos_top') }}</el-radio>
          <el-radio :effect="themes" :label="false">{{ t('chart.total_pos_bottom') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        :label="t('chart.table_grand_total_label')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-input
          :effect="themes"
          :placeholder="t('chart.table_grand_total_label')"
          size="small"
          maxlength="20"
          v-model="state.tableTotalForm.row.label"
          clearable
          @change="changeTableTotal('row.label')"
        />
      </el-form-item>
      <el-form-item
        :label="t('chart.aggregation')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-col :span="11">
          <el-select
            :effect="themes"
            v-model="state.rowTotalItem.engineFieldName"
            :placeholder="t('chart.aggregation')"
            @change="changeTotal(state.rowTotalItem, state.tableTotalForm.row.calcTotals.cfg)"
          >
            <el-option
              v-for="option in chart.yAxis"
              :key="option.engineFieldName"
              :label="option.name"
              :value="option.engineFieldName"
            />
          </el-select>
        </el-col>
        <el-col :span="state.rowTotalItem.aggregation === 'CUSTOM' ? 8 : 11" :offset="2">
          <el-select
            :effect="themes"
            v-model="state.rowTotalItem.aggregation"
            :placeholder="t('chart.aggregation')"
            @change="
              changeTotalAggr(
                state.rowTotalItem,
                state.tableTotalForm.row.calcTotals.cfg,
                'row.calcTotals.cfg'
              )
            "
          >
            <el-option
              v-for="option in aggregations"
              :key="option.value"
              :label="option.name"
              :value="option.value"
            />
          </el-select>
        </el-col>
        <el-col v-if="state.rowTotalItem.aggregation === 'CUSTOM'" :span="2" :offset="1">
          <el-icon>
            <Setting
              @click="
                editField(
                  state.rowTotalItem,
                  state.tableTotalForm.row.calcTotals.cfg,
                  'row.calcTotals.cfg'
                )
              "
            />
          </el-icon>
        </el-col>
      </el-form-item>
      <el-form-item
        v-if="showRowFieldTotalLabel"
        class="form-item"
        :label="t('chart.table_field_total_label')"
        :class="'form-item-' + themes"
      >
        <el-input
          :effect="themes"
          :placeholder="t('chart.table_field_total_label')"
          size="small"
          maxlength="20"
          v-model="state.rowTotalItem.label"
          clearable
          @change="
            changeTotalAggr(
              state.rowTotalItem,
              state.tableTotalForm.row.calcTotals.cfg,
              'row.calcTotals.cfg'
            )
          "
        />
      </el-form-item>
      <el-form-item
        v-if="chart.type === 'table-pivot'"
        :label="t('chart.total_sort')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-radio-group
          :effect="themes"
          v-model="state.tableTotalForm.row.totalSort"
          @change="changeTableTotal('row.totalSort')"
        >
          <el-radio :effect="themes" label="none">{{ t('chart.total_sort_none') }}</el-radio>
          <el-radio :effect="themes" label="asc">{{ t('chart.total_sort_asc') }}</el-radio>
          <el-radio :effect="themes" label="desc">{{ t('chart.total_sort_desc') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        v-if="chart.type === 'table-pivot' && state.tableTotalForm.row.totalSort !== 'none'"
        :label="t('chart.total_sort_field')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-select
          :effect="themes"
          v-model="state.tableTotalForm.row.totalSortField"
          class="form-item-select"
          :placeholder="t('chart.total_sort_field')"
          @change="changeTableTotal('row.totalSortField')"
        >
          <el-option
            v-for="option in chart.yAxis"
            :key="option.engineFieldName"
            :label="option.name"
            :value="option.engineFieldName"
          />
        </el-select>
      </el-form-item>
    </div>

    <el-form-item
      v-show="showProperty('row')"
      :label="t('chart.sub_total_show')"
      class="form-item"
      :class="'form-item-' + themes"
    >
      <el-checkbox
        :effect="themes"
        v-model="state.tableTotalForm.row.showSubTotals"
        :disabled="chart.xAxis.length < 2"
        @change="changeTableTotal('row')"
      >
        {{ t('chart.show') }}
      </el-checkbox>
    </el-form-item>
    <div v-if="showProperty('row') && state.tableTotalForm.row.showSubTotals">
      <div style="display: flex">
        <div style="width: 22px; flex-direction: row"></div>
        <div style="flex: 1">
          <el-form-item class="form-item" :class="'form-item-' + themes">
            <el-select
              :effect="themes"
              v-model="state.selectedSubTotalDimensionName"
              :disabled="chart.xAxis.length < 2 || state.basicStyleForm.tableLayoutMode === 'tree'"
              @change="onSelectedSubTotalDimensionNameChange"
            >
              <el-option
                v-for="option in state.subTotalDimensionList"
                :key="option.name"
                :label="option.displayName"
                :value="option.name"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            v-if="state.selectedSubTotalDimension"
            class="form-item"
            :class="'form-item-' + themes"
          >
            <el-checkbox
              :effect="themes"
              v-model="state.selectedSubTotalDimension.checked"
              :disabled="chart.xAxis.length < 2 || state.basicStyleForm.tableLayoutMode === 'tree'"
              @change="changeRowSubTableTotal"
            >
              {{ t('chart.show') }}
            </el-checkbox>
          </el-form-item>
        </div>
      </div>

      <el-form-item
        :label="t('chart.total_position')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-radio-group
          :effect="themes"
          v-model="state.tableTotalForm.row.reverseSubLayout"
          :disabled="chart.xAxis.length < 2 || state.basicStyleForm.tableLayoutMode === 'tree'"
          @change="changeTableTotal('row')"
        >
          <el-radio :effect="themes" :label="true">{{ t('chart.total_pos_top') }}</el-radio>
          <el-radio :effect="themes" :label="false">{{ t('chart.total_pos_bottom') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        :label="t('chart.total_label')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-input
          :effect="themes"
          :disabled="chart.xAxis.length < 2"
          :placeholder="t('chart.total_label')"
          v-model="state.tableTotalForm.row.subLabel"
          size="small"
          maxlength="20"
          clearable
          @change="changeTableTotal('row.subLabel')"
        />
      </el-form-item>
      <el-form-item
        :label="t('chart.aggregation')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-col :span="11">
          <el-select
            :effect="themes"
            v-model="state.rowSubTotalItem.engineFieldName"
            :disabled="chart.xAxis.length < 2"
            :placeholder="t('chart.aggregation')"
            @change="changeTotal(state.rowSubTotalItem, state.tableTotalForm.row.calcSubTotals.cfg)"
          >
            <el-option
              v-for="option in chart.yAxis"
              :key="option.engineFieldName"
              :label="option.name"
              :value="option.engineFieldName"
            />
          </el-select>
        </el-col>
        <el-col :span="state.rowSubTotalItem.aggregation === 'CUSTOM' ? 8 : 11" :offset="2">
          <el-select
            :effect="themes"
            v-model="state.rowSubTotalItem.aggregation"
            :disabled="chart.xAxis.length < 2"
            :placeholder="t('chart.aggregation')"
            @change="
              changeTotalAggr(
                state.rowSubTotalItem,
                state.tableTotalForm.row.calcSubTotals.cfg,
                'row'
              )
            "
          >
            <el-option
              v-for="option in aggregations"
              :key="option.value"
              :label="option.name"
              :value="option.value"
            />
          </el-select>
        </el-col>
        <el-col v-if="state.rowSubTotalItem.aggregation === 'CUSTOM'" :span="2" :offset="1">
          <el-icon>
            <Setting
              @click="
                editField(
                  state.rowSubTotalItem,
                  state.tableTotalForm.row.calcSubTotals.cfg,
                  'row.calcSubTotals.cfg'
                )
              "
            />
          </el-icon>
        </el-col>
      </el-form-item>
    </div>

    <el-divider
      v-if="showProperty('col')"
      content-position="center"
      :class="'divider-style-' + themes"
    >
      {{ t('chart.col_cfg') }}
    </el-divider>
    <el-form-item
      v-show="showProperty('col')"
      :label="t('chart.total_show')"
      class="form-item"
      :class="'form-item-' + themes"
    >
      <el-checkbox
        :effect="themes"
        v-model="state.tableTotalForm.col.showGrandTotals"
        @change="changeTableTotal('col')"
        >{{ t('chart.show') }}</el-checkbox
      >
    </el-form-item>
    <div v-show="showProperty('col') && state.tableTotalForm.col.showGrandTotals">
      <el-form-item
        :label="t('chart.total_position')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-radio-group
          :effect="themes"
          v-model="state.tableTotalForm.col.reverseLayout"
          @change="changeTableTotal('col')"
        >
          <el-radio :effect="themes" :label="true">{{ t('chart.total_pos_left') }}</el-radio>
          <el-radio :effect="themes" :label="false">{{ t('chart.total_pos_right') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        :label="t('chart.table_grand_total_label')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-input
          :effect="themes"
          :placeholder="t('chart.table_grand_total_label')"
          size="small"
          maxlength="20"
          v-model="state.tableTotalForm.col.label"
          clearable
          @blur="changeTableTotal('col.label')"
        />
      </el-form-item>
      <el-form-item
        :label="t('chart.aggregation')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-col :span="11">
          <el-select
            :effect="themes"
            v-model="state.colTotalItem.engineFieldName"
            :placeholder="t('chart.aggregation')"
            @change="changeTotal(state.colTotalItem, state.tableTotalForm.col.calcTotals.cfg)"
          >
            <el-option
              v-for="option in chart.yAxis"
              :key="option.engineFieldName"
              :label="option.name"
              :value="option.engineFieldName"
            />
          </el-select>
        </el-col>
        <el-col :span="state.colTotalItem.aggregation === 'CUSTOM' ? 8 : 11" :offset="2">
          <el-select
            :effect="themes"
            v-model="state.colTotalItem.aggregation"
            :placeholder="t('chart.aggregation')"
            @change="
              changeTotalAggr(
                state.colTotalItem,
                state.tableTotalForm.col.calcTotals.cfg,
                'col.calcTotals.cfg'
              )
            "
          >
            <el-option
              v-for="option in aggregations"
              :key="option.value"
              :label="option.name"
              :value="option.value"
            />
          </el-select>
        </el-col>
        <el-col v-if="state.colTotalItem.aggregation === 'CUSTOM'" :span="2" :offset="1">
          <el-icon>
            <Setting
              @click="
                editField(
                  state.colTotalItem,
                  state.tableTotalForm.col.calcTotals.cfg,
                  'col.calcTotals.cfg'
                )
              "
            />
          </el-icon>
        </el-col>
      </el-form-item>
      <el-form-item
        v-if="showColFieldTotalLabel"
        class="form-item"
        :label="t('chart.table_field_total_label')"
        :class="'form-item-' + themes"
      >
        <el-input
          :effect="themes"
          :placeholder="t('chart.table_field_total_label')"
          size="small"
          maxlength="20"
          v-model="state.colTotalItem.label"
          clearable
          @change="
            changeTotalAggr(
              state.colTotalItem,
              state.tableTotalForm.col.calcTotals.cfg,
              'col.calcTotals.cfg'
            )
          "
        />
      </el-form-item>
      <el-form-item
        v-if="chart.type === 'table-pivot'"
        :label="t('chart.total_sort')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-radio-group
          :effect="themes"
          v-model="state.tableTotalForm.col.totalSort"
          @change="changeTableTotal('col')"
        >
          <el-radio :effect="themes" label="none">{{ t('chart.total_sort_none') }}</el-radio>
          <el-radio :effect="themes" label="asc">{{ t('chart.total_sort_asc') }}</el-radio>
          <el-radio :effect="themes" label="desc">{{ t('chart.total_sort_desc') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        v-show="chart.type === 'table-pivot' && state.tableTotalForm.col?.totalSort !== 'none'"
        :label="t('chart.total_sort_field')"
        class="form-item"
      >
        <el-select
          :effect="themes"
          v-model="state.tableTotalForm.col.totalSortField"
          class="form-item-select"
          :placeholder="t('chart.total_sort_field')"
          @change="changeTableTotal('col')"
        >
          <el-option
            v-for="option in chart.yAxis"
            :key="option.engineFieldName"
            :label="option.name"
            :value="option.engineFieldName"
          />
        </el-select>
      </el-form-item>
    </div>

    <el-form-item
      v-show="showProperty('col')"
      :label="t('chart.sub_total_show')"
      class="form-item"
      :class="'form-item-' + themes"
    >
      <el-checkbox
        :effect="themes"
        v-model="state.tableTotalForm.col.showSubTotals"
        :disabled="chart.xAxisExt.length < 2"
        @change="changeTableTotal('col')"
        >{{ t('chart.show') }}</el-checkbox
      >
    </el-form-item>
    <div v-show="showProperty('col') && state.tableTotalForm.col.showSubTotals">
      <el-form-item
        :label="t('chart.total_position')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-radio-group
          :effect="themes"
          v-model="state.tableTotalForm.col.reverseSubLayout"
          :disabled="chart.xAxisExt?.length < 2"
          @change="changeTableTotal('col')"
        >
          <el-radio :effect="themes" :label="true">{{ t('chart.total_pos_left') }}</el-radio>
          <el-radio :effect="themes" :label="false">{{ t('chart.total_pos_right') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        :label="t('chart.total_label')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-input
          :effect="themes"
          :disabled="chart.xAxisExt?.length < 2"
          :placeholder="t('chart.total_label')"
          v-model="state.tableTotalForm.col.subLabel"
          size="small"
          maxlength="20"
          clearable
          @change="changeTableTotal('col.subLabel')"
        />
      </el-form-item>
      <el-form-item
        :label="t('chart.aggregation')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-col :span="11">
          <el-select
            :effect="themes"
            v-model="state.colSubTotalItem.engineFieldName"
            :disabled="chart.xAxisExt?.length < 2"
            :placeholder="t('chart.aggregation')"
            @change="changeTotal(state.colSubTotalItem, state.tableTotalForm.col.calcSubTotals.cfg)"
          >
            <el-option
              v-for="option in chart.yAxis"
              :key="option.engineFieldName"
              :label="option.name"
              :value="option.engineFieldName"
            />
          </el-select>
        </el-col>
        <el-col :span="state.colSubTotalItem.aggregation === 'CUSTOM' ? 8 : 11" :offset="2">
          <el-select
            :effect="themes"
            v-model="state.colSubTotalItem.aggregation"
            :disabled="chart.xAxisExt?.length < 2"
            :placeholder="t('chart.aggregation')"
            @change="
              changeTotalAggr(
                state.colSubTotalItem,
                state.tableTotalForm.col.calcSubTotals.cfg,
                'col.calcSubTotals.cfg'
              )
            "
          >
            <el-option
              v-for="option in aggregations"
              :key="option.value"
              :label="option.name"
              :value="option.value"
            />
          </el-select>
        </el-col>
        <el-col v-if="state.colSubTotalItem.aggregation === 'CUSTOM'" :span="2" :offset="1">
          <el-icon>
            <Setting
              @click="
                editField(
                  state.colSubTotalItem,
                  state.tableTotalForm.col.calcSubTotals.cfg,
                  'col.calcSubTotals.cfg'
                )
              "
            />
          </el-icon>
        </el-col>
      </el-form-item>
    </div>
  </el-form>
  <!--图表计算字段-->
  <el-dialog
    v-model="editCalcField"
    width="1000px"
    title="自定义聚合公式"
    :close-on-click-modal="false"
  >
    <custom-aggr-edit ref="calcEdit" />
    <template #footer>
      <el-button secondary @click="closeEditCalc()">{{ t('dataset.cancel') }} </el-button>
      <el-button type="primary" @click="confirmEditCalc()">{{ t('dataset.confirm') }} </el-button>
    </template>
  </el-dialog>
</template>

<style lang="less" scoped>
.divider-style-dark {
  ::v-deep(.ed-divider__text) {
    color: #fff;
    background: @side-content-background;
  }
}
</style>
