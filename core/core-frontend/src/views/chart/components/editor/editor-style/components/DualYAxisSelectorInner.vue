<script lang="tsx" setup>
import icon_info_outlined from '@/assets/svg/icon_info_outlined.svg'
import { computed, onMounted, reactive, watch } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { COLOR_PANEL, DEFAULT_YAXIS_STYLE } from '@/views/chart/components/editor/util/chart'
import {
  isEnLocal,
  formatterType,
  getUnitTypeList,
  initFormatCfgUnit,
  onChangeFormatCfgUnitLanguage
} from '@/views/chart/components/js/formatter'
import { ElFormItem, ElMessage } from 'element-plus-secondary'

const { t } = useI18n()

// 双 Y 轴内层表单由外层轴配置面板复用，负责单侧轴的展示、刻度和标签格式。
const props = withDefaults(
  defineProps<{
    themes?: EditorTheme
    form: any
    propertyInner?: Array<string>
    type?: 'left' | 'right'
    chartType?: string
    layout?: string
  }>(),
  {
    themes: 'dark',
    type: 'left'
  }
)

const predefineColors = COLOR_PANEL
// 数值格式化类型与普通 X/Y 轴面板保持一致，避免双向柱图独立维护格式口径。
const typeList = formatterType
// 提示浮层使用反向主题，保证暗色面板中的说明文字有足够对比度。
const toolTip = computed(() => {
  return props.themes === 'dark' ? 'light' : 'dark'
})
// 内部表单复制外部传入值，避免用户编辑中途直接改写父组件状态。
const state = reactive({
  axisForm: JSON.parse(JSON.stringify(DEFAULT_YAXIS_STYLE))
})

// 父组件负责把单侧轴配置合并回完整双轴配置并触发图表重绘。
const emit = defineEmits(['onChangeYAxisForm'])

// 轴线和网格线共用线型枚举，value 需要和 G2Plot lineStyle.style 对齐。
const splitLineStyle = [
  { label: t('chart.line_type_solid'), value: 'solid' },
  { label: t('chart.line_type_dashed'), value: 'dashed' },
  { label: t('chart.line_type_dotted'), value: 'dotted' }
]

// 外部切换左右轴、撤销或重做时会替换 form，需要重新复制并初始化单位配置。
watch(
  () => props.form,
  () => {
    init()
  },
  { deep: true }
)

// 轴标题和轴标签共用字号候选，大屏场景允许使用更大字号。
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

// 固定刻度数量会直接影响坐标轴渲染，提交前限制范围，避免异常配置进入图表实例。
const changeAxisStyle = prop => {
  if (
    state.axisForm.axisValue.splitCount &&
    (state.axisForm.axisValue.splitCount > 100 || state.axisForm.axisValue.splitCount < 0)
  ) {
    // splitCount 过大会造成刻度过密和渲染性能下降，保存前统一拦截。
    ElMessage.error(t('chart.splitCount_less_100'))
    return
  }
  emit('onChangeYAxisForm', state.axisForm, prop)
}

// 切换单位语言时同步修正单位枚举，再通过普通轴样式变更事件保存。
function changeUnitLanguage(cfg: BaseFormatter, lang, prop: string) {
  onChangeFormatCfgUnitLanguage(cfg, lang)
  changeAxisStyle(prop)
}

// 初始化时深拷贝外部表单，随后补齐格式化单位默认值。
const init = () => {
  // 双轴外层会传入左轴或右轴对象，内层只维护当前侧的编辑副本。
  state.axisForm = JSON.parse(JSON.stringify(props.form))
  initFormatCfgUnit(state.axisForm.axisLabelFormatter)
}

// 属性显示由外层面板传入，隐藏项不提交，避免双向柱图收到不支持的轴字段。
const showProperty = prop => props.propertyInner?.includes(prop)

// 双向柱图需要根据方向重写轴位置文案，与普通直角坐标系不同。
const isBidirectionalBar = computed(() => {
  return props.chartType === 'bidirectional-bar'
})

// 纵向双向柱图保留左右轴语义，横向布局则把上下文案映射到左右轴值。
const isVerticalLayout = computed(() => {
  // 横向双向柱图会交换视觉方向，轴位置文案需要按布局重新解释。
  return props.layout === 'vertical'
})

onMounted(() => {
  init()
})
</script>

<template>
  <el-form
    ref="axisForm"
    :disabled="!state.axisForm.show"
    :model="state.axisForm"
    size="small"
    label-position="top"
  >
    <!-- 轴位置在双向柱图中会随布局方向映射为上下或左右文案。 -->
    <el-form-item
      class="form-item"
      :class="'form-item-' + themes"
      :label="t('chart.position')"
      v-if="showProperty('position')"
    >
      <el-radio-group
        v-model="state.axisForm.position"
        size="small"
        @change="changeAxisStyle('position')"
      >
        <div v-if="isBidirectionalBar">
          <!-- 双向柱图的左右轴语义随布局变化，不能直接复用普通 Y 轴文案。 -->
          <div v-if="isVerticalLayout">
            <el-radio :effect="props.themes" value="left">{{ t('chart.text_pos_left') }}</el-radio>
            <el-radio :effect="props.themes" value="right">{{
              t('chart.text_pos_right')
            }}</el-radio>
          </div>
          <div v-else>
            <el-radio :effect="props.themes" value="right">{{ t('chart.text_pos_top') }}</el-radio>
            <el-radio :effect="props.themes" value="left">{{
              t('chart.text_pos_bottom')
            }}</el-radio>
          </div>
        </div>
        <div v-else>
          <el-radio :effect="props.themes" value="left">{{ t('chart.text_pos_left') }}</el-radio>
          <el-radio :effect="props.themes" value="right">{{ t('chart.text_pos_right') }}</el-radio>
        </div>
      </el-radio-group>
    </el-form-item>
    <el-form-item class="form-item" :class="'form-item-' + themes">
      <el-checkbox
        size="small"
        :effect="props.themes"
        v-model="state.axisForm.nameShow"
        @change="changeAxisStyle('nameShow')"
      >
        {{ t('chart.axis_nameShow') }}
      </el-checkbox>
    </el-form-item>
    <div style="margin-left: 22px">
      <!-- 轴标题关闭后仍保留标题文本和样式，便于用户再次开启时恢复配置。 -->
      <el-form-item
        class="form-item"
        :class="'form-item-' + themes"
        :label="t('chart.name')"
        v-if="showProperty('name')"
      >
        <el-input
          :disabled="!state.axisForm.nameShow"
          :effect="props.themes"
          v-model="state.axisForm.name"
          size="small"
          maxlength="50"
          @blur="changeAxisStyle('name')"
        />
      </el-form-item>

      <label class="custom-form-item-label" :class="'custom-form-item-label--' + themes"
        >{{ t('chart.name') }}{{ t('chart.text') }}</label
      >
      <div style="display: flex">
        <el-form-item
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('color')"
          style="padding-right: 4px"
        >
          <el-color-picker
            :disabled="!state.axisForm.nameShow"
            v-model="state.axisForm.color"
            class="color-picker-style"
            :predefine="predefineColors"
            @change="changeAxisStyle('color')"
            :effect="themes"
            is-custom
          />
        </el-form-item>
        <el-form-item
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('fontSize')"
          style="padding-left: 4px"
        >
          <el-tooltip :content="t('chart.font_size')" :effect="toolTip" placement="top">
            <el-select
              :disabled="!state.axisForm.nameShow"
              style="width: 108px"
              :effect="props.themes"
              v-model="state.axisForm.fontSize"
              :placeholder="t('chart.axis_name_fontsize')"
              @change="changeAxisStyle('fontSize')"
            >
              <el-option
                v-for="option in fontSizeList"
                :key="option.value"
                :label="option.name"
                :value="option.value"
              />
            </el-select>
          </el-tooltip>
        </el-form-item>
      </div>
    </div>
    <template v-if="showProperty('axisValue')">
      <el-divider class="m-divider" :class="'m-divider--' + themes" />

      <!-- 关闭自动刻度后才允许编辑最大值、最小值和分割数，避免覆盖图表自适应刻度。 -->
      <div style="display: flex; flex-direction: row; justify-content: space-between">
        <label class="custom-form-item-label" :class="'custom-form-item-label--' + themes">
          {{ t('chart.axis_value') }}
          <el-tooltip class="item" :effect="toolTip" placement="top">
            <template #content
              ><span>{{ t('chart.axis_tip') }}</span></template
            >
            <span style="vertical-align: middle">
              <el-icon style="cursor: pointer">
                <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
              </el-icon>
            </span>
          </el-tooltip>
        </label>

        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-checkbox
            size="small"
            :effect="props.themes"
            v-model="state.axisForm.axisValue.auto"
            @change="changeAxisStyle('axisValue.auto')"
          >
            {{ t('chart.axis_auto') }}
          </el-checkbox>
        </el-form-item>
      </div>

      <template v-if="showProperty('axisValue') && !state.axisForm.axisValue.auto">
        <!-- 固定刻度三项必须成组保存，防止单侧轴出现半自动刻度。 -->
        <el-row :gutter="8">
          <el-col :span="12">
            <el-form-item
              class="form-item"
              :class="'form-item-' + themes"
              :label="t('chart.axis_value_max')"
            >
              <el-input-number
                controls-position="right"
                :effect="props.themes"
                v-model.number="state.axisForm.axisValue.max"
                @change="changeAxisStyle('axisValue.max')"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              class="form-item"
              :class="'form-item-' + themes"
              :label="t('chart.axis_value_min')"
            >
              <el-input-number
                :effect="props.themes"
                controls-position="right"
                v-model.number="state.axisForm.axisValue.min"
                @change="changeAxisStyle('axisValue.min')"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <label class="custom-form-item-label" :class="'custom-form-item-label--' + themes">
          {{ t('chart.axis_value_split_count') }}
          <el-tooltip class="item" :effect="toolTip" placement="top">
            <template #content>{{ t('chart.number_of_scales_tip') }}</template>
            <span style="vertical-align: middle">
              <el-icon style="cursor: pointer">
                <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
              </el-icon>
            </span>
          </el-tooltip>
        </label>

        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-input-number
            style="width: 100%"
            :effect="props.themes"
            controls-position="right"
            v-model.number="state.axisForm.axisValue.splitCount"
            @change="changeAxisStyle('axisValue.splitCount')"
          />
        </el-form-item>
      </template>
    </template>
    <el-divider class="m-divider" :class="'m-divider--' + themes" />
    <el-form-item class="form-item" :class="'form-item-' + themes" v-if="showProperty('axisLine')">
      <el-checkbox
        size="small"
        :effect="props.themes"
        v-model="state.axisForm.axisLine.show"
        @change="changeAxisStyle('axisLine.show')"
      >
        {{ t('chart.axis_show') }}
      </el-checkbox>
    </el-form-item>
    <!-- 轴线和网格线写入不同配置分支，但共享颜色、线型、线宽三类控件。 -->
    <div style="padding-left: 22px" v-if="showProperty('axisLine')">
      <div style="flex: 1; display: flex">
        <el-form-item class="form-item" :class="'form-item-' + themes" style="padding-right: 4px">
          <el-color-picker
            :disabled="!state.axisForm.axisLine.show"
            v-model="state.axisForm.axisLine.lineStyle.color"
            :predefine="predefineColors"
            :effect="themes"
            @change="changeAxisStyle('axisLine.lineStyle.color')"
            is-custom
          />
        </el-form-item>
        <el-form-item class="form-item" :class="'form-item-' + themes" style="padding: 0 4px">
          <el-select
            :disabled="!state.axisForm.axisLine.show"
            style="width: 62px"
            :effect="props.themes"
            v-model="state.axisForm.axisLine.lineStyle.style"
            @change="changeAxisStyle('axisLine.lineStyle.style')"
          >
            <el-option
              v-for="option in splitLineStyle"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item class="form-item" :class="'form-item-' + themes" style="padding-left: 4px">
          <el-input-number
            :disabled="!state.axisForm.axisLine.show"
            style="width: 70px"
            :effect="props.themes"
            v-model="state.axisForm.axisLine.lineStyle.width"
            :min="1"
            :max="10"
            size="small"
            controls-position="right"
            @change="changeAxisStyle('axisLine.lineStyle.width')"
          />
        </el-form-item>
      </div>
    </div>
    <el-form-item
      class="form-item form-item-checkbox"
      :class="{
        'form-item-dark': themes === 'dark'
      }"
      v-if="showProperty('splitLine')"
    >
      <el-checkbox
        size="small"
        :effect="props.themes"
        v-model="state.axisForm.splitLine.show"
        @change="changeAxisStyle('splitLine.show')"
      >
        {{ t('chart.grid_show') }}
      </el-checkbox>
    </el-form-item>
    <div style="padding-left: 22px" v-if="showProperty('splitLine')">
      <!-- 网格线配置只影响当前侧轴，另一侧轴由外层独立保存。 -->
      <div style="flex: 1; display: flex">
        <el-form-item class="form-item" :class="'form-item-' + themes" style="padding-right: 4px">
          <el-color-picker
            :disabled="!state.axisForm.splitLine.show"
            v-model="state.axisForm.splitLine.lineStyle.color"
            :predefine="predefineColors"
            @change="changeAxisStyle('splitLine.lineStyle.color')"
            :effect="themes"
            is-custom
          />
        </el-form-item>
        <el-form-item class="form-item" :class="'form-item-' + themes" style="padding: 0 4px">
          <el-select
            :disabled="!state.axisForm.splitLine.show"
            style="width: 62px"
            :effect="props.themes"
            v-model="state.axisForm.splitLine.lineStyle.style"
            @change="changeAxisStyle('splitLine.lineStyle.style')"
          >
            <el-option
              v-for="option in splitLineStyle"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item class="form-item" :class="'form-item-' + themes" style="padding-left: 4px">
          <el-input-number
            :disabled="!state.axisForm.splitLine.show"
            style="width: 70px"
            :effect="props.themes"
            v-model="state.axisForm.splitLine.lineStyle.width"
            :min="1"
            :max="10"
            size="small"
            controls-position="right"
            @change="changeAxisStyle('splitLine.lineStyle.width')"
          />
        </el-form-item>
      </div>
    </div>
    <el-divider class="m-divider" :class="'m-divider--' + themes" />
    <el-form-item
      class="form-item form-item-checkbox"
      :class="{
        'form-item-dark': themes === 'dark'
      }"
      v-if="showProperty('axisLabel')"
    >
      <el-checkbox
        size="small"
        :effect="props.themes"
        v-model="state.axisForm.axisLabel.show"
        @change="changeAxisStyle('axisLabel.show')"
      >
        {{ t('chart.axis_label_show') }}
      </el-checkbox>
    </el-form-item>
    <div style="padding-left: 22px" v-if="showProperty('axisLabel')">
      <div style="flex: 1">
        <!-- 轴标签颜色和字号独立于轴标题，关闭标签时保留格式化规则。 -->
        <div style="display: flex">
          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            style="padding-right: 4px"
            :label="t('chart.text')"
          >
            <el-color-picker
              :disabled="!state.axisForm.axisLabel.show"
              v-model="state.axisForm.axisLabel.color"
              :predefine="predefineColors"
              @change="changeAxisStyle('axisLabel.color')"
              :effect="themes"
              is-custom
            />
          </el-form-item>
          <el-form-item class="form-item" :class="'form-item-' + themes" style="padding-left: 4px">
            <template #label>&nbsp;</template>
            <el-tooltip :content="t('chart.font_size')" :effect="toolTip" placement="top">
              <el-select
                :disabled="!state.axisForm.axisLabel.show"
                style="width: 108px"
                :effect="props.themes"
                v-model="state.axisForm.axisLabel.fontSize"
                :placeholder="t('chart.axis_label_fontsize')"
                @change="changeAxisStyle('axisLabel.fontSize')"
              >
                <el-option
                  v-for="option in fontSizeList"
                  :key="option.value"
                  :label="option.name"
                  :value="option.value"
                />
              </el-select>
            </el-tooltip>
          </el-form-item>
        </div>

        <el-form-item class="form-item" :class="'form-item-' + themes" :label="t('chart.rotate')">
          <el-input-number
            :disabled="!state.axisForm.axisLabel.show"
            style="width: 100%"
            :effect="props.themes"
            v-model="state.axisForm.axisLabel.rotate"
            :min="-90"
            :max="90"
            size="small"
            controls-position="right"
            @change="changeAxisStyle('axisLabel.rotate')"
          />
        </el-form-item>

        <template v-if="showProperty('axisLabelFormatter')">
          <!-- 双轴标签格式化仍走统一 formatter，确保左右轴单位和小数规则可独立保存。 -->
          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            :label="t('chart.value_formatter_type')"
          >
            <el-select
              :disabled="!state.axisForm.axisLabel.show"
              style="width: 100%"
              :effect="props.themes"
              v-model="state.axisForm.axisLabelFormatter.type"
              @change="changeAxisStyle('axisLabelFormatter.type')"
            >
              <el-option
                v-for="type in typeList"
                :key="type.value"
                :label="t('chart.' + type.name)"
                :value="type.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            v-if="state.axisForm.axisLabelFormatter.type !== 'auto'"
            :label="t('chart.value_formatter_decimal_count')"
            class="form-item"
            :class="'form-item-' + themes"
          >
            <el-input-number
              :disabled="!state.axisForm.axisLabel.show"
              style="width: 100%"
              :effect="props.themes"
              v-model="state.axisForm.axisLabelFormatter.decimalCount"
              :precision="0"
              :min="0"
              :max="10"
              size="small"
              controls-position="right"
              @change="changeAxisStyle('axisLabelFormatter.decimalCount')"
            />
          </el-form-item>

          <template
            v-if="
              state.axisForm.axisLabel.show && state.axisForm.axisLabelFormatter.type !== 'percent'
            "
          >
            <!-- 百分比格式不展示单位配置，其余数值格式可继续叠加单位和后缀。 -->
            <el-row :gutter="8">
              <el-col :span="12" v-if="!isEnLocal">
                <el-form-item
                  :label="t('chart.value_formatter_unit_language')"
                  class="form-item"
                  :class="'form-item-' + themes"
                >
                  <el-select
                    size="small"
                    :effect="themes"
                    v-model="state.axisForm.axisLabelFormatter.unitLanguage"
                    :placeholder="t('chart.pls_select_field')"
                    @change="
                      v =>
                        changeUnitLanguage(
                          state.axisForm.axisLabelFormatter,
                          v,
                          'axisLabelFormatter'
                        )
                    "
                  >
                    <el-option :label="t('chart.value_formatter_unit_language_ch')" value="ch" />
                    <el-option :label="t('chart.value_formatter_unit_language_en')" value="en" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="isEnLocal ? 24 : 12">
                <el-form-item
                  class="form-item"
                  :class="'form-item-' + themes"
                  :label="t('chart.value_formatter_unit')"
                >
                  <el-select
                    :effect="props.themes"
                    v-model="state.axisForm.axisLabelFormatter.unit"
                    :placeholder="t('chart.pls_select_field')"
                    size="small"
                    @change="changeAxisStyle('axisLabelFormatter')"
                  >
                    <el-option
                      v-for="item in getUnitTypeList(
                        state.axisForm.axisLabelFormatter.unitLanguage
                      )"
                      :key="item.value"
                      :label="item.name"
                      :value="item.value"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="8">
              <el-col :span="24">
                <el-form-item
                  class="form-item"
                  :class="'form-item-' + themes"
                  :label="t('chart.value_formatter_suffix')"
                >
                  <el-input
                    :disabled="!state.axisForm.axisLabel.show"
                    :effect="props.themes"
                    v-model="state.axisForm.axisLabelFormatter.suffix"
                    size="small"
                    clearable
                    :placeholder="t('commons.input_content')"
                    @change="changeAxisStyle('axisLabelFormatter.suffix')"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </template>

          <el-form-item class="form-item" :class="'form-item-' + themes">
            <el-checkbox
              :disabled="!state.axisForm.axisLabel.show"
              size="small"
              :effect="props.themes"
              v-model="state.axisForm.axisLabelFormatter.thousandSeparator"
              @change="changeAxisStyle('axisLabelFormatter.thousandSeparator')"
              :label="t('chart.value_formatter_thousand_separator')"
            />
          </el-form-item>
        </template>
      </div>
    </div>
  </el-form>
</template>

<style lang="less" scoped>
.custom-form-item-label {
  // 自定义标题用于容纳说明图标，并与 Element 表单 label 的视觉节奏保持一致。
  margin-bottom: 4px;
  line-height: 20px;
  color: #646a73;
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
  padding: 2px 12px 0 0;

  &.custom-form-item-label--dark {
    color: #a6a6a6;
  }
}
.form-item-checkbox {
  margin-bottom: 10px !important;
}
.m-divider {
  // 分隔线在深浅主题中共用布局，仅通过透明边框区分层级。
  border-color: rgba(31, 35, 41, 0.15);
  margin: 0 0 16px;

  &.m-divider--dark {
    border-color: rgba(235, 235, 235, 0.15);
  }
}
</style>
