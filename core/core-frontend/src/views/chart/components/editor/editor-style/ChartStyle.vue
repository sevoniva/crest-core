<script lang="ts" setup>
import { useI18n } from '@/hooks/web/useI18n'
import { PropType, toRefs, nextTick, watch, ref, computed } from 'vue'
import MiscSelector from '@/views/chart/components/editor/editor-style/components/MiscSelector.vue'
import LabelSelector from '@/views/chart/components/editor/editor-style/components/LabelSelector.vue'
import TooltipSelector from '@/views/chart/components/editor/editor-style/components/TooltipSelector.vue'
import XAxisSelector from '@/views/chart/components/editor/editor-style/components/XAxisSelector.vue'
import YAxisSelector from '@/views/chart/components/editor/editor-style/components/YAxisSelector.vue'
import DualYAxisSelector from '@/views/chart/components/editor/editor-style/components/DualYAxisSelector.vue'
import TitleSelector from '@/views/chart/components/editor/editor-style/components/TitleSelector.vue'
import LegendSelector from '@/views/chart/components/editor/editor-style/components/LegendSelector.vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import CollapseSwitchItem from '@/components/collapse-switch-item/src/CollapseSwitchItem.vue'
import { ElCollapse, ElCollapseItem } from 'element-plus-secondary'
import BasicStyleSelector from '@/views/chart/components/editor/editor-style/components/BasicStyleSelector.vue'
import SymbolicStyleSelector from '@/views/chart/components/editor/editor-style/components/SymbolicStyleSelector.vue'
import DualBasicStyleSelector from '@/views/chart/components/editor/editor-style/components/DualBasicStyleSelector.vue'
import ComponentPosition from '@/components/visualization/common/ComponentPosition.vue'
import BackgroundOverallCommon from '@/components/visualization/component-background/BackgroundOverallCommon.vue'
import TableHeaderSelector from '@/views/chart/components/editor/editor-style/components/table/TableHeaderSelector.vue'
import TableCellSelector from '@/views/chart/components/editor/editor-style/components/table/TableCellSelector.vue'
import TableTotalSelector from '@/views/chart/components/editor/editor-style/components/table/TableTotalSelector.vue'
import SummarySelector from '@/views/chart/components/editor/editor-style/components/table/SummarySelector.vue'
import MiscStyleSelector from '@/views/chart/components/editor/editor-style/components/MiscStyleSelector.vue'
import IndicatorValueSelector from '@/views/chart/components/editor/editor-style/components/IndicatorValueSelector.vue'
import IndicatorNameSelector from '@/views/chart/components/editor/editor-style/components/IndicatorNameSelector.vue'
import QuadrantSelector from '@/views/chart/components/editor/editor-style/components/QuadrantSelector.vue'
import CommonBorderSetting from '@/custom-component/common/CommonBorderSetting.vue'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import BulletTargetSelector from '@/views/chart/components/editor/editor-style/components/bullet/BulletTargetSelector.vue'
import BulletMeasureSelector from '@/views/chart/components/editor/editor-style/components/bullet/BulletMeasureSelector.vue'
import BulletRangeSelector from '@/views/chart/components/editor/editor-style/components/bullet/BulletRangeSelector.vue'

const snapshotStore = snapshotStoreWithOut()

const dvMainStore = dvMainStoreWithOut()
const { dvInfo, batchOptStatus, mobileInPc } = storeToRefs(dvMainStore)
const { t } = useI18n()

const state = {
  attrActiveNames: [],
  styleActiveNames: [],
  initReady: true
}

// 图表样式面板入参，包含当前图表、主题、字段列表和可用配置项
const props = defineProps({
  commonBackgroundPop: {
    type: Object,
    required: false
  },
  commonBorderPop: {
    type: Object,
    required: false
  },
  chart: {
    type: Object as PropType<ChartObj>,
    required: true
  },
  themes: {
    type: String as PropType<EditorTheme>,
    default: 'dark'
  },
  dimensionData: {
    type: Array,
    required: true
  },
  quotaData: {
    type: Array,
    required: true
  },
  properties: {
    type: Array as PropType<EditorProperty[]>,
    required: false,
    default: () => {
      return []
    }
  },
  propertyInnerAll: {
    type: Object as PropType<EditorPropertyInner>,
    required: false,
    default: () => {
      return {}
    }
  },
  selectorSpec: {
    type: Object as PropType<EditorSelectorSpec>,
    required: false,
    default: () => {
      return {}
    }
  },
  allFields: {
    type: Array,
    required: true
  }
})

const {
  chart,
  themes,
  properties,
  propertyInnerAll,
  commonBackgroundPop,
  commonBorderPop,
  selectorSpec
} = toRefs(props)
// 样式面板向编辑器上层转发的各类样式变更事件
const emit = defineEmits([
  'onColorChange',
  'onMiscChange',
  'onLabelChange',
  'onTooltipChange',
  'onChangeXAxisForm',
  'onChangeYAxisForm',
  'onChangeYAxisExtForm',
  'onTextChange',
  'onLegendChange',
  'onBasicStyleChange',
  'onBackgroundChange',
  'onStyleAttrChange',
  'onTableHeaderChange',
  'onTableCellChange',
  'onTableTotalChange',
  'onChangeMiscStyleForm',
  'onExtTooltipChange',
  'onIndicatorChange',
  'onIndicatorNameChange',
  'onChangeQuadrantForm'
])

// 指标值样式子组件实例，用于名称颜色变更时读取指标值配置
const indicatorValueRef = ref()
// 指标名称样式子组件实例，用于指标值颜色变更时读取名称配置
const indicatorNameRef = ref()

// 非批量操作且非仪表板时才展示组件位置设置
const positionComponentShow = computed(() => {
  return !batchOptStatus.value && dvInfo.value.type !== 'dashboard'
})

// 判断当前图表是否支持某个样式属性分组
const showProperties = (property: EditorProperty) => properties.value?.includes(property)

// 转发杂项配置变更
const onMiscChange = (val, prop) => {
  emit('onMiscChange', val, prop)
}

// 转发标签配置变更，初始化阶段不触发外部更新
const onLabelChange = (val, prop) => {
  state.initReady && emit('onLabelChange', val, prop)
}

// 转发提示框配置变更，初始化阶段不触发外部更新
const onTooltipChange = (val, prop) => {
  state.initReady && emit('onTooltipChange', val, prop)
}

// 转发 X 轴配置变更
const onChangeXAxisForm = (val, prop) => {
  state.initReady && emit('onChangeXAxisForm', val, prop)
}

// 转发 Y 轴配置变更，组合图显隐需要同步副轴显隐
const onChangeYAxisForm = (val, prop) => {
  if (prop === 'show' && chart.value.type.includes('chart-mix')) {
    chart.value.customStyle.yAxisExt.show = val.show
    onChangeYAxisExtForm(chart.value.customStyle.yAxisExt, 'show')
  }
  state.initReady && emit('onChangeYAxisForm', val, prop)
}

// 转发组合图副 Y 轴配置变更
const onChangeYAxisExtForm = (val, prop) => {
  state.initReady && emit('onChangeYAxisExtForm', val, prop)
}

// 转发文本样式配置变更
const onTextChange = (val, prop) => {
  state.initReady && emit('onTextChange', val, prop)
}

// 转发指标值样式变更，颜色变更时同时带上指标名称配置
const onIndicatorChange = (val, prop) => {
  const value = { indicatorValue: val, indicatorName: undefined }
  if (prop === 'color' || prop === 'suffixColor') {
    value.indicatorName = indicatorNameRef.value?.getFormData()
  }
  state.initReady && emit('onIndicatorChange', value, prop)
}

// 转发指标名称样式变更，颜色变更时同时带上指标值配置
const onIndicatorNameChange = (val, prop) => {
  const value = { indicatorName: val, indicatorValue: undefined }
  if (prop === 'color') {
    value.indicatorValue = indicatorValueRef.value?.getFormData()
  }
  state.initReady && emit('onIndicatorNameChange', value, prop)
}

// 转发图例配置变更
const onLegendChange = (val, prop) => {
  state.initReady && emit('onLegendChange', val, prop)
}
// 转发基础样式配置变更
const onBasicStyleChange = (val, prop) => {
  state.initReady && emit('onBasicStyleChange', val, prop)
}

// 转发组件背景配置变更，并记录样式快照
const onBackgroundChange = (val, prop) => {
  snapshotStore.recordSnapshotCache('onBackgroundChange')
  state.initReady && emit('onBackgroundChange', val, prop)
}

// 转发公共边框启用状态变更，并记录样式快照
const onActiveChange = (_val?: boolean) => {
  snapshotStore.recordSnapshotCache('onActiveChange')
  state.initReady &&
    emit('onStyleAttrChange', {
      custom: 'style',
      property: 'active',
      value: commonBorderPop.value.borderActive
    })
}

// 转发公共边框单项样式变更
const onStyleAttrChange = ({ key, value }) => {
  state.initReady && emit('onStyleAttrChange', { custom: 'style', property: key, value: value })
}

// 转发表头样式配置变更
const onTableHeaderChange = (val, prop) => {
  emit('onTableHeaderChange', val, prop)
}
// 转发表格单元格样式配置变更
const onTableCellChange = (val, prop) => {
  emit('onTableCellChange', val, prop)
}
// 转发表格汇总样式配置变更
const onTableTotalChange = (val, prop) => {
  emit('onTableTotalChange', val, prop)
}
// 转发图表杂项样式表单变更
const onChangeMiscStyleForm = (val, prop) => {
  emit('onChangeMiscStyleForm', val, prop)
}

// 转发扩展提示字段配置变更
const onExtTooltipChange = val => {
  emit('onExtTooltipChange', val)
}
// 转发象限配置变更
const onChangeQuadrantForm = val => {
  emit('onChangeQuadrantForm', val)
}
// 图表切换期间暂停事件转发，避免初始化表单误触发外部保存
watch(
  () => props.chart.id,
  () => {
    state.initReady = false
    nextTick(() => {
      state.initReady = true
    })
  },
  { deep: true }
)
</script>

<template>
  <el-row class="view-panel" :class="'style-' + themes">
    <div class="attr-style">
      <el-row class="crest-collapse-style">
        <!-- 属性区承载位置、基础样式、标题图例和表格属性，所有子组件变更统一向编辑器上层转发。 -->
        <el-collapse v-model="state.attrActiveNames" class="style-collapse">
          <el-collapse-item
            :effect="themes"
            name="position"
            :title="t('visualization.position')"
            v-if="positionComponentShow"
          >
            <component-position :themes="themes" />
          </el-collapse-item>
          <el-collapse-item
            :effect="themes"
            name="basicStyle"
            :title="t('chart.basic_style')"
            v-if="showProperties('basic-style-selector')"
          >
            <basic-style-selector
              :property-inner="propertyInnerAll['basic-style-selector']"
              :themes="themes"
              :chart="chart"
              @onBasicStyleChange="onBasicStyleChange"
              @onMiscChange="onMiscChange"
            />
          </el-collapse-item>
          <el-collapse-item
            :effect="themes"
            name="basicStyle"
            :title="t('chart.basic_style')"
            v-if="showProperties('dual-basic-style-selector')"
          >
            <DualBasicStyleSelector
              :property-inner="propertyInnerAll['dual-basic-style-selector']"
              :themes="themes"
              :chart="chart"
              @onBasicStyleChange="onBasicStyleChange"
              @onMiscChange="onMiscChange"
            />
          </el-collapse-item>
          <div v-if="showProperties('bullet-graph-selector')">
            <!-- 子弹图拆分目标值、当前值和区间背景，避免不同语义的配置混在同一面板。 -->
            <el-collapse-item :effect="themes" name="bullet" :title="t('chart.progress_target')">
              <bullet-target-selector
                :themes="themes"
                :chart="chart"
                :selector-type="'target'"
                @onBasicStyleChange="onBasicStyleChange"
                @onMiscChange="onMiscChange"
              />
            </el-collapse-item>
            <el-collapse-item :effect="themes" name="measure" :title="t('chart.progress_current')">
              <bullet-measure-selector
                :themes="themes"
                :chart="chart"
                :selector-type="'measure'"
                @onBasicStyleChange="onBasicStyleChange"
                @onMiscChange="onMiscChange"
              />
            </el-collapse-item>
            <el-collapse-item
              style="margin-bottom: 0 !important"
              :effect="themes"
              name="range"
              :title="t('chart.range_bg')"
            >
              <bullet-range-selector
                :themes="themes"
                :chart="chart"
                :selector-type="'range'"
                @onBasicStyleChange="onBasicStyleChange"
                @onMiscChange="onMiscChange"
              />
            </el-collapse-item>
          </div>
          <collapse-switch-item
            :themes="themes"
            v-model="chart.customStyle.text.show"
            v-if="showProperties('title-selector')"
            :change-model="chart.customStyle.text"
            @modelChange="val => onTextChange(val, 'show')"
            name="title"
            :title="$t('chart.title')"
          >
            <title-selector
              :property-inner="propertyInnerAll['title-selector']"
              :themes="themes"
              class="attr-selector"
              :chart="chart"
              @onTextChange="onTextChange"
            />
          </collapse-switch-item>
          <collapse-switch-item
            :themes="themes"
            v-if="showProperties('legend-selector')"
            v-model="chart.customStyle.legend.show"
            :change-model="chart.customStyle.legend"
            @modelChange="val => onLegendChange(val, 'show')"
            name="legend"
            :title="$t('chart.legend')"
          >
            <legend-selector
              class="attr-selector"
              :property-inner="propertyInnerAll['legend-selector']"
              :themes="themes"
              :chart="chart"
              @onLegendChange="onLegendChange"
              @onMiscChange="onMiscChange"
            />
          </collapse-switch-item>
          <el-collapse-item
            :effect="themes"
            name="background"
            :title="t('visualization.background')"
            v-if="showProperties('background-overall-component') && commonBackgroundPop"
          >
            <!-- 组件背景和公共边框来自大屏通用配置，样式面板只负责挂载和派发变更。 -->
            <background-overall-common
              :common-background-pop="commonBackgroundPop"
              :themes="themes"
              @onBackgroundChange="onBackgroundChange"
              component-position="component"
            />
          </el-collapse-item>
          <collapse-switch-item
            v-if="showProperties('border-style') && commonBorderPop && !batchOptStatus"
            v-model="commonBorderPop.borderActive"
            @modelChange="val => onActiveChange(val)"
            :themes="themes"
            :title="t('visualization.board')"
            name="borderSetting"
            class="common-style-area"
          >
            <common-border-setting
              :style-info="commonBorderPop"
              :themes="themes"
              @onStyleAttrChange="onStyleAttrChange"
            ></common-border-setting>
          </collapse-switch-item>

          <el-collapse-item
            :effect="themes"
            name="symbolicStyle"
            :title="t('chart.symbolic')"
            v-if="showProperties('symbolic-style-selector')"
          >
            <SymbolicStyleSelector
              :property-inner="propertyInnerAll['symbolic-style-selector']"
              :themes="themes"
              :chart="chart"
              @onBasicStyleChange="onBasicStyleChange"
              @onMiscChange="onMiscChange"
            />
          </el-collapse-item>
          <el-collapse-item
            :effect="themes"
            v-if="showProperties('indicator-value-selector')"
            name="indicator-value"
            :title="t('chart.indicator_value')"
          >
            <indicator-value-selector
              ref="indicatorValueRef"
              :property-inner="propertyInnerAll['indicator-value-selector']"
              :themes="themes"
              class="attr-selector"
              :chart="chart"
              :quota-fields="props.quotaData"
              @onIndicatorChange="onIndicatorChange"
            />
          </el-collapse-item>
          <collapse-switch-item
            :themes="themes"
            v-model="chart.customAttr.indicatorName.show"
            v-if="showProperties('indicator-name-selector')"
            :change-model="chart.customAttr.indicatorName"
            @modelChange="val => onIndicatorNameChange(val, 'show')"
            :title="t('visualization.indicator_name')"
            name="indicator-name"
          >
            <indicator-name-selector
              ref="indicatorNameRef"
              :property-inner="propertyInnerAll['indicator-name-selector']"
              :themes="themes"
              class="attr-selector"
              :chart="chart"
              :quota-fields="props.quotaData"
              @onIndicatorNameChange="onIndicatorNameChange"
            />
          </collapse-switch-item>
          <el-collapse-item
            :effect="themes"
            v-if="showProperties('misc-selector') && !chart.type.includes('mix')"
            name="size"
            :title="t('visualization.component_size')"
          >
            <misc-selector
              :property-inner="propertyInnerAll['misc-selector']"
              :themes="themes"
              class="attr-selector"
              :chart="chart"
              :quota-fields="props.quotaData"
              :mobile-in-pc="mobileInPc"
              @onMiscChange="onMiscChange"
            />
          </el-collapse-item>
          <el-collapse-item
            :effect="themes"
            v-if="showProperties('misc-style-selector')"
            name="size"
            :title="selectorSpec['misc-style-selector']?.title || t('chart.tooltip_axis')"
          >
            <misc-style-selector
              :property-inner="propertyInnerAll['misc-style-selector']"
              :themes="themes"
              class="attr-selector"
              :chart="chart"
              :quota-fields="props.quotaData"
              @onChangeMiscStyleForm="onChangeMiscStyleForm"
            />
          </el-collapse-item>
          <collapse-switch-item
            :themes="themes"
            v-if="showProperties('label-selector')"
            v-model="chart.customAttr.label.show"
            :change-model="chart.customAttr.label"
            @modelChange="val => onLabelChange({ data: val }, 'show')"
            :title="t('chart.label')"
            name="label"
          >
            <label-selector
              :property-inner="propertyInnerAll['label-selector']"
              :themes="themes"
              class="attr-selector"
              :chart="chart"
              :all-fields="props.allFields"
              @onLabelChange="onLabelChange"
            />
          </collapse-switch-item>
          <collapse-switch-item
            v-if="showProperties('tooltip-selector')"
            v-model="chart.customAttr.tooltip.show"
            :themes="themes"
            :change-model="chart.customAttr.tooltip"
            :title="t('chart.tooltip')"
            :show-switch="propertyInnerAll['tooltip-selector'].includes('show')"
            name="tooltip"
            @modelChange="val => onTooltipChange({ data: val }, 'show')"
          >
            <tooltip-selector
              class="attr-selector"
              :property-inner="propertyInnerAll['tooltip-selector']"
              :themes="themes"
              :chart="chart"
              :all-fields="props.allFields"
              @onTooltipChange="onTooltipChange"
              @onExtTooltipChange="onExtTooltipChange"
            />
          </collapse-switch-item>
          <collapse-switch-item
            v-if="showProperties('table-header-selector')"
            v-model="chart.customAttr.tableHeader.showTableHeader"
            :change-model="chart.customAttr.tableHeader"
            :effect="themes"
            :title="t('chart.table_header')"
            :show-switch="propertyInnerAll['table-header-selector'].includes('showTableHeader')"
            name="tableHeader"
            @modelChange="val => onTableHeaderChange(val, 'showTableHeader')"
          >
            <table-header-selector
              :property-inner="propertyInnerAll['table-header-selector']"
              :themes="themes"
              :chart="chart"
              @onTableHeaderChange="onTableHeaderChange"
            />
          </collapse-switch-item>
          <el-collapse-item
            :effect="themes"
            name="tableCell"
            :title="t('chart.table_cell')"
            v-if="showProperties('table-cell-selector')"
          >
            <table-cell-selector
              :property-inner="propertyInnerAll['table-cell-selector']"
              :themes="themes"
              :chart="chart"
              @onTableCellChange="onTableCellChange"
            />
          </el-collapse-item>
          <el-collapse-item
            :effect="themes"
            name="tableTotal"
            :title="t('chart.table_total')"
            v-if="showProperties('table-total-selector')"
          >
            <table-total-selector
              :property-inner="propertyInnerAll['table-total-selector']"
              :themes="themes"
              :chart="chart"
              @onTableTotalChange="onTableTotalChange"
            />
          </el-collapse-item>
          <el-collapse-item
            :effect="themes"
            name="quadrant"
            :title="t('chart.quadrant')"
            v-if="showProperties('quadrant-selector')"
          >
            <quadrant-selector
              class="attr-selector"
              :property-inner="propertyInnerAll['quadrant-selector']"
              :themes="themes"
              :chart="chart"
              @onChangeQuadrantForm="onChangeQuadrantForm"
            />
          </el-collapse-item>
        </el-collapse>

        <!-- 样式区集中处理坐标轴、双轴和汇总行等图形渲染相关配置。 -->
        <el-collapse v-model="state.styleActiveNames" class="style-collapse">
          <collapse-switch-item
            :themes="themes"
            v-if="showProperties('x-axis-selector')"
            v-model="chart.customStyle.xAxis.show"
            :change-model="chart.customStyle.xAxis"
            @modelChange="val => onChangeXAxisForm(val, 'show')"
            name="xAxis"
            :title="selectorSpec['x-axis-selector']?.title || t('chart.xAxis')"
          >
            <x-axis-selector
              class="attr-selector"
              :property-inner="propertyInnerAll['x-axis-selector']"
              :themes="themes"
              :chart="chart"
              @onChangeXAxisForm="onChangeXAxisForm"
            />
          </collapse-switch-item>
          <collapse-switch-item
            :themes="themes"
            v-if="showProperties('y-axis-selector')"
            v-model="chart.customStyle.yAxis.show"
            :change-model="chart.customStyle.yAxis"
            @modelChange="val => onChangeYAxisForm(val, 'show')"
            name="yAxis"
            :title="t('chart.yAxis')"
          >
            <y-axis-selector
              class="attr-selector"
              :property-inner="propertyInnerAll['y-axis-selector']"
              :themes="themes"
              :chart="chart"
              @onChangeYAxisForm="onChangeYAxisForm"
            />
          </collapse-switch-item>

          <collapse-switch-item
            :themes="themes"
            v-if="showProperties('dual-y-axis-selector')"
            v-model="chart.customStyle.yAxis.show"
            :change-model="chart.customStyle.yAxis"
            @modelChange="val => onChangeYAxisForm(val, 'show')"
            name="yAxis"
            :title="selectorSpec['dual-y-axis-selector']?.title ?? t('chart.yAxis')"
          >
            <!-- 双轴选择器同时维护主轴和副轴，组合图显隐由上层保持联动。 -->
            <dual-y-axis-selector
              class="attr-selector"
              :property-inner="propertyInnerAll['dual-y-axis-selector']"
              :themes="themes"
              :chart="chart"
              @onChangeYAxisForm="onChangeYAxisForm"
              @onChangeYAxisExtForm="onChangeYAxisExtForm"
            />
          </collapse-switch-item>

          <collapse-switch-item
            :themes="themes"
            v-if="showProperties('summary-selector')"
            v-model="chart.customAttr.basicStyle.showSummary"
            :change-model="chart.customAttr.basicStyle"
            @modelChange="val => onBasicStyleChange({ data: val }, 'showSummary')"
            :title="t('chart.table_summary')"
            name="summary"
          >
            <summary-selector
              :property-inner="propertyInnerAll['summary-selector']"
              :themes="themes"
              :chart="chart"
              @onBasicStyleChange="onBasicStyleChange"
            />
          </collapse-switch-item>
        </el-collapse>
      </el-row>
    </div>
  </el-row>
</template>

<style lang="less" scoped>
.ed-row {
  display: block;
}

.prop {
  border-bottom: 1px solid @side-outline-border-color;
}
.prop-top {
  border-top: 1px solid @side-outline-border-color;
}

span {
  font-size: 14px;
}

.view-panel {
  display: flex;
  height: 100%;
  width: 100%;
}

.attr-style {
  overflow-y: auto;
  height: 100%;
  width: 100%;
}

.crest-collapse-style {
  :deep(.ed-form-item) {
    display: block;
    margin-bottom: 8px;
  }
  :deep(.ed-form-item__label) {
    justify-content: flex-start;
  }
}
:deep(.ed-collapse-item) {
  &:first-child {
    .ed-collapse-item__header {
      border-top: none;
    }
  }
}
.style-collapse:empty {
  border-bottom: none;
}

:deep(.ed-collapse-item__content) {
  padding: 16px 8px 8px 8px !important;
}
</style>
