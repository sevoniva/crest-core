<script lang="ts" setup>
import icon_letterSpacing_outlined from '@/assets/svg/icon_letter-spacing_outlined.svg'
import icon_bold_outlined from '@/assets/svg/icon_bold_outlined.svg'
import icon_italic_outlined from '@/assets/svg/icon_italic_outlined.svg'
import icon_leftAlignment_outlined from '@/assets/svg/icon_left-alignment_outlined.svg'
import icon_centerAlignment_outlined from '@/assets/svg/icon_center-alignment_outlined.svg'
import icon_rightAlignment_outlined from '@/assets/svg/icon_right-alignment_outlined.svg'
import icon_topAlign_outlined from '@/assets/svg/icon_top-align_outlined.svg'
import icon_verticalAlign_outlined from '@/assets/svg/icon_vertical-align_outlined.svg'
import icon_bottomAlign_outlined from '@/assets/svg/icon_bottom-align_outlined.svg'
import { PropType, computed, onMounted, reactive, watch, nextTick } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import {
  COLOR_PANEL,
  CHART_FONT_LETTER_SPACE,
  DEFAULT_INDICATOR_STYLE,
  DEFAULT_BASIC_STYLE,
  CHART_FONT_FAMILY_ORIGIN
} from '@/views/chart/components/editor/util/chart'
import { cloneDeep, defaultsDeep } from 'lodash-es'
import { ElIcon, ElInput } from 'element-plus-secondary'
import Icon from '@/components/icon-custom/src/Icon.vue'
import { useAppearanceStoreWithOut } from '@/store/modules/appearance'
const appearanceStore = useAppearanceStoreWithOut()

const { t } = useI18n()

// 指标值样式面板只编辑 customAttr.indicator 和部分基础样式，不直接触发图表数据重新查询。
const props = defineProps({
  chart: {
    type: Object,
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

// 父组件负责合并样式变更并记录快照，这里只上报变更字段和当前表单值。
const emit = defineEmits(['onIndicatorChange', 'onBasicStyleChange'])
// 提示框主题与面板主题反向搭配，保证暗色面板上的提示内容可读。
const toolTip = computed(() => {
  return props.themes === 'dark' ? 'light' : 'dark'
})
const predefineColors = COLOR_PANEL
// 系统字体和用户上传字体统一进入下拉列表，value 保持字体名称，便于渲染层直接应用。
const fontFamily = CHART_FONT_FAMILY_ORIGIN.concat(
  appearanceStore.fontList.map(ele => ({
    name: ele.name,
    value: ele.name
  }))
)
const fontLetterSpace = CHART_FONT_LETTER_SPACE

// 表单状态拆分为指标值样式和基础图表样式，避免后缀设置误写到 basicStyle。
const state = reactive({
  // 指标主值样式包含字体、对齐、阴影和后缀配置，是指标卡最核心的展示配置。
  indicatorValueForm: JSON.parse(JSON.stringify(DEFAULT_INDICATOR_STYLE)),
  // basicStyle 仅承载指标值周边的图表基础设置，避免和 indicator 对象互相污染。
  basicStyleForm: {} as ChartBasicStyle
})
// 指标卡片字号跨度较大，小字号用细粒度步进，大字号用 10px 步进控制列表长度。
const fontSizeList = computed(() => {
  const arr = []
  for (let i = 10; i <= 60; i = i + 2) {
    arr.push({
      name: i + '',
      value: i
    })
  }
  for (let i = 70; i <= 210; i += 10) {
    arr.push({
      name: i + '',
      value: i
    })
  }
  return arr
})

// 每次字段变更都带上字段名，父组件可按字段决定是否立即重绘或仅更新配置。
const changeLabelTitleStyleStyle = prop => {
  // 指标值样式变更不经过通用轴样式通道，避免父级误判为坐标系更新。
  emit('onIndicatorChange', state.indicatorValueForm, prop)
}

// 初始化时以图表已保存配置为准，再补齐新增字段默认值，兼容旧版本指标卡配置。
const init = () => {
  const TEMP_DEFAULT_BASIC_STYLE = cloneDeep(DEFAULT_BASIC_STYLE)
  delete TEMP_DEFAULT_BASIC_STYLE.alpha

  state.basicStyleForm = defaultsDeep(
    cloneDeep(props.chart?.customAttr?.basicStyle),
    cloneDeep(TEMP_DEFAULT_BASIC_STYLE)
  )

  const customText = defaultsDeep(
    cloneDeep(props.chart?.customAttr?.indicator),
    cloneDeep(DEFAULT_INDICATOR_STYLE)
  )

  // 使用深拷贝隔离表单编辑态，避免直接改写 chart.customAttr.indicator。
  state.indicatorValueForm = cloneDeep(customText)

  // 颜色选择器首次挂载时可能晚于表单赋值，下一帧再次同步可保证主值和后缀颜色正确回显。
  nextTick(() => {
    state.indicatorValueForm.color = customText.color
    state.indicatorValueForm.suffixColor = customText.suffixColor
  })
}

// 组件挂载后读取当前图表配置，避免编辑面板打开时展示默认值覆盖已有配置。
onMounted(() => {
  init()
})

// 外部撤销、重做或主题切换会替换 indicator 对象，需要重新合并默认值并刷新表单。
watch(
  () => props.chart?.customAttr?.indicator,
  () => {
    init()
  },
  { deep: true }
)

// 暴露给父组件的读取入口，用于保存前收集面板内尚未派发的最新表单状态。
function getFormData() {
  return state.indicatorValueForm
}

defineExpose({ getFormData })
</script>

<template>
  <div>
    <!-- 主值与后缀共用同一表单，关闭指标显示时整组配置进入只读态。 -->
    <el-form
      ref="indicatorValueForm"
      :disabled="!state.indicatorValueForm.show"
      :model="state.indicatorValueForm"
      label-position="top"
      size="small"
    >
      <el-form-item
        class="form-item"
        :class="'form-item-' + themes"
        :effect="themes"
        :label="t('chart.text')"
      >
        <el-select
          :effect="themes"
          v-model="state.indicatorValueForm.fontFamily"
          :placeholder="t('chart.font_family')"
          @change="changeLabelTitleStyleStyle('fontFamily')"
        >
          <el-option
            v-for="option in fontFamily"
            :key="option.value"
            :label="option.name"
            :value="option.value"
          />
        </el-select>
      </el-form-item>

      <div style="display: flex">
        <!-- 主值颜色、字号和字间距在同一行展示，减少指标卡常用样式调整路径。 -->
        <el-form-item class="form-item" :class="'form-item-' + themes" style="padding-right: 4px">
          <el-color-picker
            :effect="themes"
            v-model="state.indicatorValueForm.color"
            class="color-picker-style"
            :predefine="predefineColors"
            @change="changeLabelTitleStyleStyle('color')"
            show-alpha
            is-custom
          />
        </el-form-item>
        <el-form-item class="form-item" :class="'form-item-' + themes" style="padding: 0 4px">
          <el-tooltip :content="t('chart.font_size')" :effect="toolTip" placement="top">
            <el-select
              style="width: 56px"
              :effect="themes"
              v-model="state.indicatorValueForm.fontSize"
              :placeholder="t('chart.text_fontsize')"
              size="small"
              @change="changeLabelTitleStyleStyle('fontSize')"
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

        <el-form-item
          class="form-item"
          :class="'form-item-' + themes"
          style="width: 106px; padding-left: 4px"
        >
          <el-select
            :effect="themes"
            v-model="state.indicatorValueForm.letterSpace"
            :placeholder="t('chart.quota_letter_space')"
            @change="changeLabelTitleStyleStyle('letterSpace')"
          >
            <template #prefix>
              <el-icon>
                <Icon name="icon_letter-spacing_outlined"
                  ><icon_letterSpacing_outlined class="svg-icon"
                /></Icon>
              </el-icon>
            </template>
            <el-option
              v-for="option in fontLetterSpace"
              :key="option.value"
              :label="option.name"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </div>

      <el-space>
        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-checkbox
            :effect="themes"
            class="icon-checkbox"
            v-model="state.indicatorValueForm.isBolder"
            @change="changeLabelTitleStyleStyle('isBolder')"
          >
            <el-tooltip :effect="toolTip" placement="top">
              <template #content>
                {{ t('chart.bolder') }}
              </template>
              <div
                class="icon-btn"
                :class="{ dark: themes === 'dark', active: state.indicatorValueForm.isBolder }"
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
            v-model="state.indicatorValueForm.isItalic"
            @change="changeLabelTitleStyleStyle('isItalic')"
          >
            <el-tooltip :effect="toolTip" placement="top">
              <template #content>
                {{ t('chart.italic') }}
              </template>
              <div
                class="icon-btn"
                :class="{ dark: themes === 'dark', active: state.indicatorValueForm.isItalic }"
              >
                <el-icon>
                  <Icon name="icon_italic_outlined"><icon_italic_outlined class="svg-icon" /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-checkbox>
        </el-form-item>

        <div class="position-divider" :class="'position-divider--' + themes"></div>

        <el-form-item class="form-item" :class="'form-item-' + themes">
          <!-- 主值水平对齐只影响指标值文本块，不改变指标名称或后缀的相对布局。 -->
          <el-radio-group
            :effect="themes"
            class="icon-radio-group"
            v-model="state.indicatorValueForm.hPosition"
            @change="changeLabelTitleStyleStyle('hPosition')"
          >
            <el-radio :effect="themes" value="left">
              <el-tooltip :effect="toolTip" placement="top">
                <template #content>
                  {{ t('chart.text_pos_left') }}
                </template>
                <div
                  class="icon-btn"
                  :class="{
                    dark: themes === 'dark',
                    active: state.indicatorValueForm.hPosition === 'left'
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
            <el-radio :effect="themes" label="center">
              <el-tooltip :effect="toolTip" placement="top">
                <template #content>
                  {{ t('chart.text_pos_center') }}
                </template>
                <div
                  class="icon-btn"
                  :class="{
                    dark: themes === 'dark',
                    active: state.indicatorValueForm.hPosition === 'center'
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
            <el-radio :effect="themes" label="right">
              <el-tooltip :effect="toolTip" placement="top">
                <template #content>
                  {{ t('chart.text_pos_right') }}
                </template>
                <div
                  class="icon-btn"
                  :class="{
                    dark: themes === 'dark',
                    active: state.indicatorValueForm.hPosition === 'right'
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

      <el-form-item class="form-item" :class="'form-item-' + themes">
        <!-- 垂直对齐用于指标值在容器内定位，和画布组件自身位置无关。 -->
        <el-radio-group
          :effect="themes"
          class="icon-radio-group"
          v-model="state.indicatorValueForm.vPosition"
          @change="changeLabelTitleStyleStyle('vPosition')"
        >
          <el-radio value="top">
            <el-tooltip :effect="toolTip" placement="top">
              <template #content>
                {{ t('chart.text_pos_top') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: state.indicatorValueForm.vPosition === 'top'
                }"
              >
                <el-icon>
                  <Icon name="icon_top-align_outlined"
                    ><icon_topAlign_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
          <el-radio value="center">
            <el-tooltip :effect="toolTip" placement="top">
              <template #content>
                {{ t('chart.text_pos_center') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: state.indicatorValueForm.vPosition === 'center'
                }"
              >
                <el-icon>
                  <Icon name="icon_vertical-align_outlined"
                    ><icon_verticalAlign_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
          <el-radio value="bottom">
            <el-tooltip :effect="toolTip" placement="top">
              <template #content>
                {{ t('chart.text_pos_bottom') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: state.indicatorValueForm.vPosition === 'bottom'
                }"
              >
                <el-icon>
                  <Icon name="icon_bottom-align_outlined"
                    ><icon_bottomAlign_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item class="form-item" :class="'form-item-' + themes">
        <el-checkbox
          size="small"
          :effect="themes"
          v-model="state.indicatorValueForm.fontShadow"
          @change="changeLabelTitleStyleStyle('fontShadow')"
        >
          {{ t('chart.font_shadow') }}
        </el-checkbox>
      </el-form-item>

      <el-divider class="m-divider" :class="{ 'divider-dark': themes === 'dark' }" />

      <!-- 后缀样式独立于主值，便于单位、百分号等短文本使用更轻的字号和颜色。 -->
      <el-form-item class="form-item" :class="'form-item-' + themes">
        <el-checkbox
          size="small"
          :effect="themes"
          v-model="state.indicatorValueForm.suffixEnable"
          @change="changeLabelTitleStyleStyle('suffixEnable')"
        >
          {{ t('chart.indicator_suffix') }}
        </el-checkbox>
      </el-form-item>

      <div style="padding-left: 22px">
        <!-- 后缀配置跟随 suffixEnable 禁用，保留已配置值便于再次启用。 -->
        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-input
            v-model="state.indicatorValueForm.suffix"
            maxlength="10"
            :effect="themes"
            :disabled="!state.indicatorValueForm.suffixEnable"
            :placeholder="t('chart.indicator_suffix_placeholder')"
            @change="changeLabelTitleStyleStyle('suffix')"
          />
        </el-form-item>

        <el-form-item class="form-item" :class="'form-item-' + themes" :effect="themes">
          <el-select
            :disabled="!state.indicatorValueForm.suffixEnable"
            :effect="themes"
            v-model="state.indicatorValueForm.suffixFontFamily"
            :placeholder="t('chart.font_family')"
            @change="changeLabelTitleStyleStyle('suffixFontFamily')"
          >
            <el-option
              v-for="option in fontFamily"
              :key="option.value"
              :label="option.name"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <div style="display: flex">
          <!-- 后缀颜色、字号和字间距独立于主值，适配单位文本弱化展示的常见需求。 -->
          <el-form-item class="form-item" :class="'form-item-' + themes" style="padding-right: 4px">
            <el-color-picker
              :disabled="!state.indicatorValueForm.suffixEnable"
              :effect="themes"
              v-model="state.indicatorValueForm.suffixColor"
              class="color-picker-style"
              :predefine="predefineColors"
              @change="changeLabelTitleStyleStyle('suffixColor')"
              is-custom
              show-alpha
            />
          </el-form-item>
          <el-form-item class="form-item" :class="'form-item-' + themes" style="padding: 0 4px">
            <el-tooltip :content="t('chart.font_size')" :effect="toolTip" placement="top">
              <el-select
                :disabled="!state.indicatorValueForm.suffixEnable"
                style="width: 56px"
                :effect="themes"
                v-model="state.indicatorValueForm.suffixFontSize"
                :placeholder="t('chart.text_fontsize')"
                size="small"
                @change="changeLabelTitleStyleStyle('suffixFontSize')"
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

          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            style="width: 106px; padding-left: 4px"
          >
            <el-select
              size="small"
              :disabled="!state.indicatorValueForm.suffixEnable"
              :effect="themes"
              v-model="state.indicatorValueForm.suffixLetterSpace"
              :placeholder="t('chart.quota_letter_space')"
              @change="changeLabelTitleStyleStyle('suffixLetterSpace')"
            >
              <template #prefix>
                <el-icon>
                  <Icon name="icon_letter-spacing_outlined"
                    ><icon_letterSpacing_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </template>
              <el-option
                v-for="option in fontLetterSpace"
                :key="option.value"
                :label="option.name"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-space>
          <!-- 后缀加粗和斜体沿用图标控件，交互上与主值样式保持一致。 -->
          <el-form-item class="form-item" :class="'form-item-' + themes">
            <el-checkbox
              :disabled="!state.indicatorValueForm.suffixEnable"
              :effect="themes"
              class="icon-checkbox"
              v-model="state.indicatorValueForm.suffixIsBolder"
              @change="changeLabelTitleStyleStyle('suffixIsBolder')"
            >
              <el-tooltip :effect="toolTip" placement="top">
                <template #content>
                  {{ t('chart.bolder') }}
                </template>
                <div
                  class="icon-btn"
                  :class="{
                    dark: themes === 'dark',
                    active: state.indicatorValueForm.suffixIsBolder
                  }"
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
              :disabled="!state.indicatorValueForm.suffixEnable"
              :effect="themes"
              class="icon-checkbox"
              v-model="state.indicatorValueForm.suffixIsItalic"
              @change="changeLabelTitleStyleStyle('suffixIsItalic')"
            >
              <el-tooltip :effect="toolTip" placement="top">
                <template #content>
                  {{ t('chart.italic') }}
                </template>
                <div
                  class="icon-btn"
                  :class="{
                    dark: themes === 'dark',
                    active: state.indicatorValueForm.suffixIsItalic
                  }"
                >
                  <el-icon>
                    <Icon name="icon_italic_outlined"
                      ><icon_italic_outlined class="svg-icon"
                    /></Icon>
                  </el-icon>
                </div>
              </el-tooltip>
            </el-checkbox>
          </el-form-item>
        </el-space>

        <el-form-item class="form-item" :class="'form-item-' + themes">
          <!-- 后缀阴影单独控制，避免单位文本跟随主值阴影后影响小字号可读性。 -->
          <el-checkbox
            :disabled="!state.indicatorValueForm.suffixEnable"
            size="small"
            :effect="themes"
            v-model="state.indicatorValueForm.suffixFontShadow"
            @change="changeLabelTitleStyleStyle('suffixFontShadow')"
          >
            {{ t('chart.font_shadow') }}
          </el-checkbox>
        </el-form-item>
      </div>
    </el-form>
  </div>
</template>

<style lang="less" scoped>
:deep(.ed-input .ed-select__prefix--light) {
  padding-right: 6px;
}
.icon-btn {
  // 图标按钮通过隐藏原生勾选控件实现，尺寸需要稳定以避免工具栏跳动。
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

.is-disabled {
  // 禁用态保留 active 视觉反馈，方便用户理解当前保存值但不能编辑。
  .icon-btn {
    color: #8f959e;
    cursor: not-allowed;

    &:hover {
      background-color: inherit;
    }

    &.active {
      background-color: #f5f7fa;
      &:hover {
        background-color: #f5f7fa;
      }
    }
    &.dark {
      color: #5f5f5f;
      &.active {
        background-color: #373737;
        &:hover {
          background-color: #373737;
        }
      }
    }
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

.icon-radio-group {
  // 对齐类选项用图标表达，原生 radio 仅保留可访问状态和 v-model 绑定。
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
  // 粗细和高度保持轻量，只用于区分字体样式按钮与对齐按钮。
  width: 1px;
  height: 18px;
  margin-bottom: 16px;
  background: rgba(31, 35, 41, 0.15);

  &.position-divider--dark {
    background: rgba(235, 235, 235, 0.15);
  }
}
.remark-label {
  color: var(--N600, #646a73);
  font-family: var(--crest-custom_font, 'PingFang');
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
  line-height: 20px;

  &.remark-label--dark {
    color: var(--N600-Dark, #a6a6a6);
  }
}
.m-divider {
  // 主值和后缀配置之间保留轻量分隔，避免视觉上混为同一组字体项。
  margin: 0 0 16px;
  border-color: rgba(31, 35, 41, 0.15);

  &.divider-dark {
    border-color: rgba(255, 255, 255, 0.15);
  }
}
</style>
