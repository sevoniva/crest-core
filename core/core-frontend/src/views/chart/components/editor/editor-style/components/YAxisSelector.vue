<script lang="tsx" setup>
import icon_info_outlined from '@/assets/svg/icon_info_outlined.svg'
import { computed, onMounted, PropType, reactive, watch } from 'vue'
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

// Y 轴选择器同时服务普通图表、混合图和子弹图，propertyInner 控制当前图表开放的轴配置项。
const props = defineProps({
  themes: {
    type: String as PropType<EditorTheme>,
    default: 'dark'
  },
  chart: {
    type: Object,
    required: true
  },
  propertyInner: {
    type: Array<string>
  }
})

const predefineColors = COLOR_PANEL
// 数值格式化类型来自统一格式化配置，保证轴标签、tooltip 和导出展示口径一致。
const typeList = formatterType
// 提示浮层使用反向主题，避免暗色配置面板内的说明文字对比度不足。
const toolTip = computed(() => {
  return props.themes === 'dark' ? 'light' : 'dark'
})
// 表单以 DEFAULT_YAXIS_STYLE 为基础，初始化时会被图表已保存的 customStyle.yAxis 覆盖。
const state = reactive({
  // Y 轴表单包含轴标题、刻度、网格线和标签格式化，初始化时按图表配置整体覆盖。
  axisForm: JSON.parse(JSON.stringify(DEFAULT_YAXIS_STYLE))
})

// 父级负责把轴样式写回图表配置并触发重绘，本组件只提交当前表单和变更字段。
const emit = defineEmits(['onChangeYAxisForm'])

// 撤销、重做或切换图表时会替换 yAxis 对象，需要重新解析并刷新格式化单位。
watch(
  () => props.chart.customStyle.yAxis,
  () => {
    init()
  },
  { deep: true }
)

// 轴标题和轴标签共用字号候选，大屏放大展示需要覆盖到较大字号。
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

// 轴线和网格线共用线型枚举，value 需要和 G2Plot lineStyle.style 保持一致。
const splitLineStyle = [
  { label: t('chart.line_type_solid'), value: 'solid' },
  { label: t('chart.line_type_dashed'), value: 'dashed' },
  { label: t('chart.line_type_dotted'), value: 'dotted' }
]

// 固定刻度数量直接影响坐标轴渲染，提交前限制范围，避免异常配置拖慢图表绘制。
const changeAxisStyle = prop => {
  if (
    state.axisForm.axisValue.splitCount &&
    (state.axisForm.axisValue.splitCount > 100 || state.axisForm.axisValue.splitCount < 0)
  ) {
    // splitCount 过大时图表库会生成密集刻度，直接在配置层拦截。
    ElMessage.error(t('chart.splitCount_less_100'))
    return
  }
  emit('onChangeYAxisForm', state.axisForm, prop)
}

// 切换单位语言时同步修正单位枚举，再通过普通轴样式事件通知父组件保存。
function changeUnitLanguage(cfg: BaseFormatter, lang, prop: string) {
  onChangeFormatCfgUnitLanguage(cfg, lang)
  changeAxisStyle(prop)
}

// customStyle 可能是对象或字符串，初始化时统一转为对象并补齐格式化单位默认值。
const init = () => {
  const chart = JSON.parse(JSON.stringify(props.chart))
  if (chart.customStyle) {
    // 旧图表可能把 customStyle 持久化为字符串，这里统一反序列化后再读取 yAxis。
    let customStyle = null
    if (Object.prototype.toString.call(chart.customStyle) === '[object Object]') {
      customStyle = JSON.parse(JSON.stringify(chart.customStyle))
    } else {
      customStyle = JSON.parse(chart.customStyle)
    }
    if (customStyle.yAxis) {
      state.axisForm = customStyle.yAxis
      initFormatCfgUnit(state.axisForm.axisLabelFormatter)
    }
  }
}

// 属性显示由图表类型配置决定，隐藏项不应被用户编辑或提交。
const showProperty = prop => props.propertyInner?.includes(prop)
// 子弹图的轴位置文案和普通直角坐标图不同，需要单独处理。
const isBulletGraph = computed(() => {
  return ['bullet-graph'].includes(props.chart.type)
})

// 横向子弹图会把上下位置映射到左右轴值，保持界面文案和视觉方向一致。
const isHorizontalLayout = computed(() => {
  // 子弹图横向布局下，Y 轴视觉方向与普通直角坐标图相反。
  return props.chart.customAttr.basicStyle.layout === 'horizontal'
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
    <!-- 轴位置对不同图表类型有不同语义，子弹图会在脚本中映射为业务文案。 -->
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
        <div v-if="isBulletGraph">
          <!-- 子弹图轴位置表达目标值方向，和普通 Y 轴左右位置不同。 -->
          <div v-if="isHorizontalLayout">
            <el-radio :effect="props.themes" value="right">{{ t('chart.text_pos_top') }}</el-radio>
            <el-radio :effect="props.themes" value="left">{{
              t('chart.text_pos_bottom')
            }}</el-radio>
          </div>
          <div v-else>
            <el-radio :effect="props.themes" value="left">{{ t('chart.text_pos_left') }}</el-radio>
            <el-radio :effect="props.themes" value="right">{{
              t('chart.text_pos_right')
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
      <!-- 轴标题关闭时保留标题文本和样式值，重新开启后可直接恢复。 -->
      <el-form-item
        class="form-item"
        :class="'form-item-' + themes"
        :label="t('chart.name')"
        v-if="showProperty('name')"
      >
        <el-input
          :effect="props.themes"
          v-model="state.axisForm.name"
          :disabled="!state.axisForm.nameShow"
          size="small"
          maxlength="50"
          @blur="changeAxisStyle('name')"
        />
      </el-form-item>

      <div style="display: flex">
        <el-form-item
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('color')"
          style="padding-right: 4px"
          :label="t('chart.chart_style')"
        >
          <el-color-picker
            v-model="state.axisForm.color"
            :disabled="!state.axisForm.nameShow"
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
          <template #label>&nbsp;</template>
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

      <!-- 固定刻度区只在关闭自动刻度后生效，避免和 G2Plot 自动计算策略冲突。 -->
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
        <!-- 固定最大值、最小值和刻度数量作为一组提交，避免半自动刻度配置混用。 -->
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
    <el-divider
      v-if="showProperty('splitLine') || showProperty('axisLine')"
      class="m-divider"
      :class="'m-divider--' + themes"
    />
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
    <!-- 轴线和网格线共用颜色、线型、宽度控件，但分别写入不同的 G2Plot 配置节点。 -->
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
    <el-divider
      v-if="showProperty('axisLabel')"
      class="m-divider"
      :class="'m-divider--' + themes"
    />
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
        <!-- 轴标签样式独立于轴标题，关闭标签后仍保留格式化配置用于恢复。 -->
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
        <el-form-item
          class="form-item"
          :class="'form-item-' + themes"
          :label="t('chart.length_limit')"
          v-if="showProperty('showLengthLimit')"
        >
          <el-input-number
            :disabled="!state.axisForm.axisLabel.show"
            style="width: 100%"
            :effect="props.themes"
            v-model="state.axisForm.axisLabel.lengthLimit"
            :min="1"
            size="small"
            controls-position="right"
            @change="changeAxisStyle('axisLabel.lengthLimit')"
          />
        </el-form-item>

        <template v-if="showProperty('axisLabelFormatter')">
          <!-- 标签格式化沿用统一 formatter 配置，保证坐标轴与 tooltip 数值口径一致。 -->
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
            <!-- 百分比格式不展示单位配置，其余格式可继续叠加单位和后缀。 -->
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
  // 复合标题行承载说明图标，使用自定义 label 保持和 Element 表单标题对齐。
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
  // 深浅主题共用分隔线结构，仅切换边框透明度以适配右侧配置面板。
  border-color: rgba(31, 35, 41, 0.15);
  margin: 0 0 16px;

  &.m-divider--dark {
    border-color: rgba(235, 235, 235, 0.15);
  }
}
</style>
