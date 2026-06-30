<script setup lang="ts">
import { onMounted, PropType, reactive, watch, ref } from 'vue'
import { DEFAULT_BASIC_STYLE, DEFAULT_MISC } from '@/views/chart/components/editor/util/chart'
import { useI18n } from '@/hooks/web/useI18n'
import CustomColorStyleSelect from '@/views/chart/components/editor/editor-style/components/CustomColorStyleSelect.vue'
import { cloneDeep, defaultsDeep } from 'lodash-es'
import {
  CHART_MIX_DEFAULT_BASIC_STYLE,
  MixChartBasicStyle
} from '@/views/chart/components/js/panel/charts/others/chart-mix-common'

const { t } = useI18n()
// 双轴基础样式面板分别维护左轴柱/线样式和右轴线样式。
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

// 面板项由图表类型传入的 propertyInner 控制，避免展示不支持的样式能力。
const showProperty = prop => props.propertyInner?.includes(prop)
// 状态同时承载基础样式、杂项样式、当前配色选择和表格列宽临时值。
const state = reactive({
  basicStyleForm: JSON.parse(JSON.stringify(CHART_MIX_DEFAULT_BASIC_STYLE)) as MixChartBasicStyle,
  miscForm: JSON.parse(JSON.stringify(DEFAULT_MISC)) as ChartMiscAttr,
  customColor: null,
  colorIndex: 0,
  fieldColumnWidth: {
    fieldId: '',
    width: 0
  }
})
// 字段和样式配置变化后重新初始化表单，确保左右轴设置与当前图表保持同步。
watch(
  [
    () => props.chart.customAttr.basicStyle,
    () => props.chart.customAttr.misc,
    () => props.chart.customAttr.tableHeader,
    () => props.chart.xAxis,
    () => props.chart.yAxis
  ],
  () => {
    init()
  },
  { deep: true }
)
// 父级负责持久化样式并触发图表重绘，这里只派发变更数据。
const emit = defineEmits(['onBasicStyleChange', 'onMiscChange'])
// requestData 表示该样式变更是否需要重新查询数据，而不只是重绘图形。
const changeBasicStyle = (prop?: string, requestData = false) => {
  emit('onBasicStyleChange', { data: state.basicStyleForm, requestData }, prop)
}
// 左轴不透明度限制在 0 到 100，非法输入回退到已有配置值。
const onAlphaChange = v => {
  const _v = parseInt(v)
  if (_v >= 0 && _v <= 100) {
    state.basicStyleForm.alpha = _v
  } else if (_v < 0) {
    state.basicStyleForm.alpha = 0
  } else if (_v > 100) {
    state.basicStyleForm.alpha = 100
  } else {
    const basicStyle = cloneDeep(props.chart.customAttr.basicStyle)
    const oldForm = defaultsDeep(
      basicStyle,
      cloneDeep(CHART_MIX_DEFAULT_BASIC_STYLE)
    ) as ChartBasicStyle
    state.basicStyleForm.alpha = oldForm.alpha
  }
  changeBasicStyle('alpha')
}

// 柱宽比例限制在 1 到 100，避免极端输入导致柱形不可见或溢出。
const onColumnWidthRatioChange = v => {
  const _v = parseInt(v)
  if (_v >= 1 && _v <= 100) {
    state.basicStyleForm.columnWidthRatio = _v
  } else if (_v < 1) {
    state.basicStyleForm.columnWidthRatio = 1
  } else if (_v > 100) {
    state.basicStyleForm.columnWidthRatio = 100
  } else {
    const basicStyle = cloneDeep(props.chart.customAttr.basicStyle)
    // 非数字输入恢复为图表当前配置，避免把 NaN 写入样式表单。
    const oldForm = defaultsDeep(basicStyle, cloneDeep(DEFAULT_BASIC_STYLE)) as ChartBasicStyle
    state.basicStyleForm.columnWidthRatio = oldForm.columnWidthRatio
  }
  changeBasicStyle('columnWidthRatio')
}

// 右轴不透明度使用独立字段，校验规则与左轴保持一致。
const onSubAlphaChange = v => {
  const _v = parseInt(v)
  if (_v >= 0 && _v <= 100) {
    state.basicStyleForm.subAlpha = _v
  } else if (_v < 0) {
    state.basicStyleForm.subAlpha = 0
  } else if (_v > 100) {
    state.basicStyleForm.subAlpha = 100
  } else {
    const basicStyle = cloneDeep(props.chart.customAttr.basicStyle)
    // 右轴透明度输入非法时，同样回退到历史配置中的 subAlpha。
    const oldForm = defaultsDeep(
      basicStyle,
      cloneDeep(CHART_MIX_DEFAULT_BASIC_STYLE)
    ) as MixChartBasicStyle
    state.basicStyleForm.subAlpha = oldForm.subAlpha
  }
  changeBasicStyle('subAlpha')
}

// 初始化时合并默认值和历史配置，保证旧图表也能进入新版样式面板。
const init = () => {
  const basicStyle = cloneDeep(props.chart.customAttr.basicStyle)
  const miscStyle = cloneDeep(props.chart.customAttr.misc)
  configCompat(basicStyle)
  // 双轴图使用混合图默认样式补齐左右轴独立字段。
  state.basicStyleForm = defaultsDeep(
    basicStyle,
    cloneDeep(CHART_MIX_DEFAULT_BASIC_STYLE)
  ) as MixChartBasicStyle
  state.miscForm = defaultsDeep(miscStyle, cloneDeep(DEFAULT_MISC)) as ChartMiscAttr
  if (!state.customColor) {
    // 首次进入面板默认选中第一组配色，后续切换时保留用户当前位置。
    state.customColor = state.basicStyleForm.colors[0]
    state.colorIndex = 0
  }
  if (
    props.chart.type.includes('-stack') &&
    state.basicStyleForm.radiusColumnBar === 'topRoundAngle'
  ) {
    // 堆叠图不支持顶部圆角，历史配置降级为普通圆角。
    state.basicStyleForm.radiusColumnBar = 'roundAngle'
  }
}
// 兼容旧版本 suspension 字段到 showZoom，避免旧图表丢失缩放按钮状态。
const configCompat = (basicStyle: ChartBasicStyle) => {
  if (basicStyle.suspension === false && basicStyle.showZoom === undefined) {
    basicStyle.showZoom = false
  }
}
// 折线点形状选项与 G2Plot 支持的 marker 类型保持一致。
const symbolOptions = [
  { name: t('chart.line_symbol_circle'), value: 'circle' },
  { name: t('chart.line_symbol_rect'), value: 'square' },
  { name: t('chart.line_symbol_triangle'), value: 'triangle' },
  { name: t('chart.line_symbol_diamond'), value: 'diamond' }
]

// 保存当前激活的左右轴样式页签，避免切换配置时重置用户正在编辑的位置。
const activeName = ref<'left' | 'right'>('left')

onMounted(() => {
  init()
})
</script>
<template>
  <el-form size="small" style="width: 100%">
    <el-tabs v-model="activeName" id="axis-tabs" stretch>
      <el-tab-pane :label="t('chart.yAxisLeft')" name="left">
        <!-- 左轴承载柱形或左侧折线样式，具体控件由图表类型和 propertyInner 共同决定。 -->
        <template v-if="showProperty('colors')">
          <custom-color-style-select
            :model-value="state"
            @update:model-value="value => Object.assign(state, value)"
            :chart="chart"
            :themes="themes"
            :property-inner="propertyInner"
            @change-basic-style="prop => changeBasicStyle(prop)"
          />
        </template>

        <template v-if="chart.type !== 'chart-mix-dual-line'">
          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            v-if="showProperty('gradient')"
          >
            <el-checkbox
              size="small"
              :effect="themes"
              v-model="state.basicStyleForm.gradient"
              @change="changeBasicStyle('gradient')"
            >
              {{ $t('chart.gradient') }}{{ $t('chart.color') }}
            </el-checkbox>
          </el-form-item>
        </template>

        <div class="alpha-setting" v-if="showProperty('alpha')">
          <label class="alpha-label" :class="{ dark: 'dark' === themes }">
            {{ t('chart.not_alpha') }}
          </label>
          <el-row style="flex: 1" :gutter="8">
            <el-col :span="13">
              <el-form-item class="form-item alpha-slider" :class="'form-item-' + themes">
                <el-slider
                  :effect="themes"
                  v-model="state.basicStyleForm.alpha"
                  @change="changeBasicStyle('alpha')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="11" style="padding-top: 2px">
              <el-form-item class="form-item" :class="'form-item-' + themes">
                <el-input
                  type="number"
                  :effect="themes"
                  v-model="state.basicStyleForm.alpha"
                  :min="0"
                  :max="100"
                  class="basic-input-number"
                  :controls="false"
                  @change="onAlphaChange"
                >
                  <template #suffix> % </template>
                </el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <template v-if="chart.type !== 'chart-mix-dual-line'">
          <el-form-item
            class="form-item"
            v-if="showProperty('radiusColumnBar')"
            :label="t('chart.radiusColumnBar')"
            :class="'form-item-' + themes"
          >
            <el-radio-group
              size="small"
              :effect="themes"
              v-model="state.basicStyleForm.radiusColumnBar"
              @change="changeBasicStyle('radiusColumnBar')"
              class="radius-class"
            >
              <el-radio value="rightAngle" :effect="themes">{{ t('chart.rightAngle') }}</el-radio>
              <el-radio value="roundAngle" :effect="themes">{{ t('chart.roundAngle') }}</el-radio>
              <el-radio
                v-if="!props.chart.type.includes('-stack')"
                label="topRoundAngle"
                :effect="themes"
              >
                {{ t('chart.topRoundAngle') }}</el-radio
              >
            </el-radio-group>
          </el-form-item>
          <div class="alpha-setting" v-if="showProperty('columnWidthRatio')">
            <label class="alpha-label" :class="{ dark: 'dark' === themes }">
              {{ t('chart.column_width_ratio') }}
            </label>
            <el-row style="flex: 1" :gutter="8">
              <el-col :span="13">
                <el-form-item class="form-item alpha-slider" :class="'form-item-' + themes">
                  <el-slider
                    :effect="themes"
                    :min="1"
                    :max="100"
                    v-model="state.basicStyleForm.columnWidthRatio"
                    @change="changeBasicStyle('columnWidthRatio')"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="11" style="padding-top: 2px">
                <el-form-item class="form-item" :class="'form-item-' + themes">
                  <el-input
                    type="number"
                    :effect="themes"
                    v-model="state.basicStyleForm.columnWidthRatio"
                    :min="1"
                    :max="100"
                    class="basic-input-number"
                    :controls="false"
                    @change="onColumnWidthRatioChange"
                  >
                    <template #suffix> % </template>
                  </el-input>
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </template>
        <template v-else>
          <el-row :gutter="8">
            <el-col :span="12">
              <el-form-item
                :label="t('chart.line_width')"
                class="form-item form-item-slider"
                :class="'form-item-' + themes"
                v-if="showProperty('lineWidth')"
              >
                <el-input-number
                  :effect="themes"
                  v-model="state.basicStyleForm.leftLineWidth"
                  :min="0"
                  :max="10"
                  controls-position="right"
                  @change="changeBasicStyle('leftLineWidth')"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="8">
            <el-col :span="12">
              <el-form-item
                :label="t('chart.line_symbol')"
                class="form-item"
                :class="'form-item-' + themes"
                v-if="showProperty('lineSymbol')"
              >
                <el-select
                  :effect="themes"
                  v-model="state.basicStyleForm.leftLineSymbol"
                  :placeholder="t('chart.line_symbol')"
                  @change="changeBasicStyle('leftLineSymbol')"
                >
                  <el-option
                    v-for="item in symbolOptions"
                    :key="item.value"
                    :label="item.name"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                :label="t('chart.line_symbol_size')"
                class="form-item form-item-slider"
                :class="'form-item-' + themes"
                v-if="showProperty('lineSymbolSize')"
              >
                <el-input-number
                  :effect="themes"
                  v-model="state.basicStyleForm.leftLineSymbolSize"
                  :min="0"
                  :max="20"
                  controls-position="right"
                  @change="changeBasicStyle('leftLineSymbolSize')"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            v-if="showProperty('lineSmooth')"
          >
            <el-checkbox
              size="small"
              :effect="themes"
              v-model="state.basicStyleForm.leftLineSmooth"
              @change="changeBasicStyle('leftLineSmooth')"
            >
              {{ t('chart.line_smooth') }}
            </el-checkbox>
          </el-form-item>
        </template>
      </el-tab-pane>
      <el-tab-pane :label="t('chart.yAxisRight')" name="right">
        <!-- 右轴只维护右侧折线样式，字段命名使用 sub/line 前缀和左轴区分。 -->
        <template v-if="showProperty('colors')">
          <custom-color-style-select
            sub
            :model-value="state"
            @update:model-value="value => Object.assign(state, value)"
            :chart="chart"
            :themes="themes"
            :property-inner="propertyInner"
            @change-basic-style="prop => changeBasicStyle(prop)"
          />
        </template>

        <div class="alpha-setting" v-if="showProperty('alpha')">
          <label class="alpha-label" :class="{ dark: 'dark' === themes }">
            {{ t('chart.not_alpha') }}
          </label>
          <el-row style="flex: 1" :gutter="8">
            <el-col :span="13">
              <el-form-item class="form-item alpha-slider" :class="'form-item-' + themes">
                <el-slider
                  :effect="themes"
                  v-model="state.basicStyleForm.subAlpha"
                  @change="changeBasicStyle('subAlpha')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="11" style="padding-top: 2px">
              <el-form-item class="form-item" :class="'form-item-' + themes">
                <el-input
                  type="number"
                  :effect="themes"
                  v-model="state.basicStyleForm.subAlpha"
                  :min="0"
                  :max="100"
                  class="basic-input-number"
                  :controls="false"
                  @change="onSubAlphaChange"
                >
                  <template #suffix> % </template>
                </el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <el-row :gutter="8">
          <el-col :span="12">
            <el-form-item
              :label="t('chart.line_width')"
              class="form-item form-item-slider"
              :class="'form-item-' + themes"
              v-if="showProperty('lineWidth')"
            >
              <el-input-number
                :effect="themes"
                v-model="state.basicStyleForm.lineWidth"
                :min="0"
                :max="10"
                controls-position="right"
                @change="changeBasicStyle('lineWidth')"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="8">
          <el-col :span="12">
            <el-form-item
              :label="t('chart.line_symbol')"
              class="form-item"
              :class="'form-item-' + themes"
              v-if="showProperty('lineSymbol')"
            >
              <el-select
                :effect="themes"
                v-model="state.basicStyleForm.lineSymbol"
                :placeholder="t('chart.line_symbol')"
                @change="changeBasicStyle('lineSymbol')"
              >
                <el-option
                  v-for="item in symbolOptions"
                  :key="item.value"
                  :label="item.name"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              :label="t('chart.line_symbol_size')"
              class="form-item form-item-slider"
              :class="'form-item-' + themes"
              v-if="showProperty('lineSymbolSize')"
            >
              <el-input-number
                :effect="themes"
                v-model="state.basicStyleForm.lineSymbolSize"
                :min="0"
                :max="20"
                controls-position="right"
                @change="changeBasicStyle('lineSymbolSize')"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('lineSmooth')"
        >
          <el-checkbox
            size="small"
            :effect="themes"
            v-model="state.basicStyleForm.lineSmooth"
            @change="changeBasicStyle('lineSmooth')"
          >
            {{ t('chart.line_smooth') }}
          </el-checkbox>
        </el-form-item>
      </el-tab-pane>
    </el-tabs>
  </el-form>
</template>
<style scoped lang="less">
.form-item {
}

.color-picker-style {
  /* 颜色浮层需要高于右侧属性面板的折叠内容。 */
  cursor: pointer;
  z-index: 1003;
}

.alpha-setting {
  /* 透明度和柱宽比例共用横向滑块布局。 */
  display: flex;
  width: 100%;

  .alpha-slider {
    padding: 0 8px;
    :deep(.ed-slider__button-wrapper) {
      --ed-slider-button-wrapper-size: 36px;
      --ed-slider-button-size: 16px;
    }
  }

  .alpha-label {
    padding-right: 8px;
    font-size: 12px;
    font-style: normal;
    font-weight: 400;
    height: 32px;
    line-height: 32px;
    display: inline-flex;
    align-items: flex-start;

    min-width: 56px;

    &.dark {
      color: #a6a6a6;
    }
  }
}
.table-field-width-config {
  .ed-select {
    width: 100px !important;
    :deep(.ed-input__wrapper) {
      border-radius: 6px 0 0 4px !important;
    }
  }
  .ed-input-group {
    width: 120px;
    :deep(.ed-input__wrapper) {
      border-radius: 0 !important;
    }
    :deep(.ed-input-group__append) {
      padding: 0 8px;
    }
  }
}
.table-column-mode {
  :deep(.ed-radio) {
    margin-right: 10px !important;
  }
}
.basic-input-number {
  :deep(input) {
    /* 百分比输入框隐藏浏览器数字步进按钮，保持和滑块组合控件一致。 */
    -webkit-appearance: none;
    -moz-appearance: textfield;

    &::-webkit-inner-spin-button,
    &::-webkit-outer-spin-button {
      -webkit-appearance: none;
    }
  }
}
.top-n-setting {
  .ed-input-number {
    width: 80px !important;
    margin: 0 2px;
  }
  :deep(span) {
    font-size: 12px;
  }
}
#axis-tabs {
  /* 页签上移贴合编辑器分组标题，减少双轴面板的垂直占用。 */
  margin-top: -16px;
  --ed-tabs-header-height: 34px;

  :deep(.ed-tabs__header) {
    border-top: none !important;
  }
}
.radius-class {
  :deep(.ed-radio) {
    margin-right: 30px !important;
  }
  .ed-radio:last-child {
    margin-right: 0px !important;
  }
}
</style>
