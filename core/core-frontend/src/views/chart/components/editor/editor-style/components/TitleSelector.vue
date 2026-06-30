<script lang="ts" setup>
import icon_letterSpacing_outlined from '@/assets/svg/icon_letter-spacing_outlined.svg'
import icon_bold_outlined from '@/assets/svg/icon_bold_outlined.svg'
import icon_italic_outlined from '@/assets/svg/icon_italic_outlined.svg'
import icon_leftAlignment_outlined from '@/assets/svg/icon_left-alignment_outlined.svg'
import icon_centerAlignment_outlined from '@/assets/svg/icon_center-alignment_outlined.svg'
import icon_rightAlignment_outlined from '@/assets/svg/icon_right-alignment_outlined.svg'
import icon_edit_outlined from '@/assets/svg/icon_edit_outlined.svg'
import { PropType, computed, onMounted, reactive, toRefs, watch, nextTick, ref } from 'vue'
import { useAppearanceStoreWithOut } from '@/store/modules/appearance'
import { useI18n } from '@/hooks/web/useI18n'
import {
  COLOR_PANEL,
  CHART_FONT_FAMILY,
  CHART_FONT_LETTER_SPACE,
  DEFAULT_TITLE_STYLE
} from '@/views/chart/components/editor/util/chart'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { cloneDeep, defaultsDeep } from 'lodash-es'
import { ElButton, ElIcon } from 'element-plus-secondary'
import Icon from '@/components/icon-custom/src/Icon.vue'
const dvMainStore = dvMainStoreWithOut()
const { batchOptStatus, mobileInPc } = storeToRefs(dvMainStore)

const { t } = useI18n()

// 标题样式面板维护图表标题文本、字体、对齐、阴影和备注说明。
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
const appearanceStore = useAppearanceStoreWithOut()
// 标题样式变更由父级写回图表配置并触发重绘。
const emit = defineEmits(['onTextChange'])
// 提示框使用反向主题，保证深浅编辑面板下都具备可读对比度。
const toolTip = computed(() => {
  return props.themes === 'dark' ? 'light' : 'dark'
})
const predefineColors = COLOR_PANEL
// 字体列表合并系统内置字体和用户上传字体，供标题配置统一选择。
const fontFamily = CHART_FONT_FAMILY.concat(
  appearanceStore.fontList.map(ele => ({
    name: ele.name,
    value: ele.name
  }))
)
const fontLetterSpace = CHART_FONT_LETTER_SPACE

// 标题样式表单以默认值初始化，避免旧图表缺字段时面板渲染异常。
const state = reactive({
  titleForm: JSON.parse(JSON.stringify(DEFAULT_TITLE_STYLE))
})

const { chart } = toRefs(props)

// 标题字号覆盖普通标题和大屏标题两个区间，避免用户手工输入非法值。
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

// 通知父级标题样式变化，prop 用于父级判断局部刷新范围。
const changeTitleStyle = prop => {
  emit('onTextChange', state.titleForm, prop)
}

// 初始化标题样式表单，并用默认样式补齐历史图表缺失字段。
const init = () => {
  const customText = defaultsDeep(
    cloneDeep(props.chart?.customStyle?.text),
    cloneDeep(DEFAULT_TITLE_STYLE)
  )

  state.titleForm = cloneDeep(customText)

  // 颜色选择器挂载首帧可能丢失值，延后一帧再同步一次。
  nextTick(() => {
    state.titleForm.color = customText.color
  })
}

// 控制备注编辑弹窗显隐。
const showEditRemark = ref<boolean>(false)
// 临时备注内容用于取消编辑时回退，不直接污染标题配置。
const tempRemark = ref<string>('')

// 打开备注编辑弹窗前复制当前备注，避免编辑中途影响图表配置。
const openEditRemark = () => {
  tempRemark.value = cloneDeep(state.titleForm.remark)
  showEditRemark.value = true
}

// 关闭备注编辑弹窗并清理临时内容。
const closeEditRemark = () => {
  showEditRemark.value = false
  tempRemark.value = ''
}

// 保存备注编辑内容后再派发变更，保持备注更新可撤销。
const saveEditRemark = () => {
  showEditRemark.value = false
  state.titleForm.remark = tempRemark.value
  changeTitleStyle('remark')
}

onMounted(() => {
  init()
})

// 外部标题样式变化时重新初始化，保证批量操作和单图面板状态一致。
watch(
  () => props.chart?.customStyle?.text,
  () => {
    init()
  },
  { deep: true }
)
</script>

<template>
  <div>
    <el-form
      ref="titleForm"
      :disabled="!state.titleForm.show"
      :model="state.titleForm"
      label-position="top"
      size="small"
    >
      <!-- 标题文本本身只在单图非移动端编辑，批量和移动端模式只调整样式。 -->
      <el-form-item
        :label="t('chart.title')"
        class="form-item"
        :class="'form-item-' + themes"
        v-if="!batchOptStatus && !mobileInPc"
      >
        <el-input
          :effect="themes"
          v-model="chart.title"
          size="small"
          maxlength="100"
          :placeholder="t('chart.title')"
          clearable
          @blur="changeTitleStyle('title')"
        />
      </el-form-item>

      <el-form-item
        class="form-item"
        :class="'form-item-' + themes"
        :effect="themes"
        :label="t('chart.text')"
      >
        <el-select
          :effect="themes"
          v-model="state.titleForm.fontFamily"
          :placeholder="t('chart.font_family')"
          @change="changeTitleStyle('fontFamily')"
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
        <!-- 标题颜色、字号和字间距是最高频样式项，保持一行布局便于快速调整。 -->
        <el-form-item class="form-item" :class="'form-item-' + themes" style="padding-right: 4px">
          <el-color-picker
            :effect="themes"
            v-model="state.titleForm.color"
            class="color-picker-style"
            :predefine="predefineColors"
            @change="changeTitleStyle('color')"
            is-custom
          />
        </el-form-item>
        <el-form-item class="form-item" :class="'form-item-' + themes" style="padding: 0 4px">
          <el-tooltip :content="t('chart.font_size')" :effect="toolTip" placement="top">
            <el-select
              style="width: 56px"
              :effect="themes"
              v-model="state.titleForm.fontSize"
              :placeholder="t('chart.text_fontsize')"
              size="small"
              @change="changeTitleStyle('fontSize')"
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
            v-model="state.titleForm.letterSpace"
            :placeholder="t('chart.quota_letter_space')"
            @change="changeTitleStyle('letterSpace')"
          >
            <template #prefix>
              <el-icon size="16">
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
            v-model="state.titleForm.isBolder"
            @change="changeTitleStyle('isBolder')"
          >
            <el-tooltip :effect="toolTip" placement="top">
              <template #content>
                {{ t('chart.bolder') }}
              </template>
              <div
                class="icon-btn"
                :class="{ dark: themes === 'dark', active: state.titleForm.isBolder }"
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
            v-model="state.titleForm.isItalic"
            @change="changeTitleStyle('isItalic')"
          >
            <el-tooltip :effect="toolTip" placement="top">
              <template #content>
                {{ t('chart.italic') }}
              </template>
              <div
                class="icon-btn"
                :class="{ dark: themes === 'dark', active: state.titleForm.isItalic }"
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
          <!-- 标题水平对齐只影响图表标题块内部排布，不改变组件在画布中的位置。 -->
          <el-radio-group
            :effect="themes"
            class="icon-radio-group"
            v-model="state.titleForm.hPosition"
            @change="changeTitleStyle('hPosition')"
          >
            <el-radio :effect="themes" label="left">
              <el-tooltip :effect="toolTip" placement="top">
                <template #content>
                  {{ t('chart.text_pos_left') }}
                </template>
                <div
                  class="icon-btn"
                  :class="{ dark: themes === 'dark', active: state.titleForm.hPosition === 'left' }"
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
                    active: state.titleForm.hPosition === 'center'
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
                    active: state.titleForm.hPosition === 'right'
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
        <el-checkbox
          size="small"
          :effect="themes"
          v-model="state.titleForm.fontShadow"
          @change="changeTitleStyle('fontShadow')"
        >
          {{ t('chart.font_shadow') }}
        </el-checkbox>
      </el-form-item>

      <!-- 备注说明使用弹窗编辑，避免长文本占用右侧样式面板空间。 -->
      <el-form-item class="form-item" :class="'form-item-' + themes">
        <el-checkbox
          size="small"
          :effect="themes"
          v-model="state.titleForm.remarkShow"
          @change="changeTitleStyle('remarkShow')"
        >
          {{ t('chart.remark_show') }}
        </el-checkbox>
      </el-form-item>
      <el-form-item class="form-item" :class="'form-item-' + themes" style="margin-left: 22px">
        <!-- 备注编辑入口不受 remarkShow 限制，方便先编辑内容再决定是否展示。 -->
        <label class="remark-label" :class="{ 'remark-label--dark': themes === 'dark' }">
          {{ t('chart.remark_edit') }}
        </label>
        <el-button text @click="openEditRemark" :effect="themes">
          <el-icon size="14px">
            <Icon name="icon_edit_outlined"><icon_edit_outlined class="svg-icon" /></Icon>
          </el-icon>
        </el-button>
      </el-form-item>
    </el-form>

    <el-dialog
      :title="t('chart.remark_edit')"
      :visible="showEditRemark"
      v-model="showEditRemark"
      width="420px"
      :close-on-click-modal="false"
    >
      <!-- 备注弹窗阻止键盘事件冒泡，避免触发画布层快捷键。 -->
      <div @keydown.stop @keyup.stop>
        <el-form-item :label="t('chart.remark')" class="form-item" prop="chartShowName">
          <el-input
            type="textarea"
            autosize
            v-model="tempRemark"
            :maxlength="512"
            clearable
            :placeholder="t('chart.remark_placeholder')"
          />
        </el-form-item>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="closeEditRemark">{{ t('chart.cancel') }}</el-button>
          <el-button type="primary" @click="saveEditRemark()">{{ t('chart.confirm') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="less" scoped>
:deep(.ed-input .ed-select__prefix--light) {
  padding-right: 6px;
}
.icon-btn {
  // 标题样式图标按钮隐藏原生控件，保证加粗、斜体和对齐按钮尺寸一致。
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
  // 禁用态保留当前 active 状态，让用户能看到已保存样式但不能编辑。
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
  // 分隔字体样式与对齐方式，仅作为视觉分组，不写入标题配置。
  width: 1px;
  height: 18px;
  margin-bottom: 16px;
  background: rgba(31, 35, 41, 0.15);

  &.position-divider--dark {
    background: rgba(235, 235, 235, 0.15);
  }
}
.remark-label {
  // 备注编辑入口使用轻量标签样式，避免和普通表单 label 争夺层级。
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
</style>
