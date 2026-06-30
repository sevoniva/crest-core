<template>
  <el-row class="custom-row">
    <el-row class="custom-row-inner">
      <el-space wrap>
        <!-- 前置选择项通常是字体族，优先展示可以减少用户在常用文本设置中的查找成本。 -->
        <template v-for="styleOptionKey in styleOptionKeyArrayPre">
          <el-tooltip
            :key="styleOptionKey.value"
            v-if="styleForm[styleOptionKey.value] !== undefined"
            effect="dark"
            placement="bottom"
          >
            <template #content> {{ styleOptionKey.label }} </template>
            <el-form-item class="form-item no-margin-bottom" :class="'form-item-' + themes">
              <el-select
                :style="{ width: styleOptionKey.width }"
                :effect="themes"
                v-model="styleForm[styleOptionKey.value]"
                size="small"
                @change="
                  changeStyle({ key: styleOptionKey.value, value: styleForm[styleOptionKey.value] })
                "
              >
                <template #prefix>
                  <el-icon :class="{ 'dark-icon': themes === 'dark' }">
                    <Icon><component :is="styleOptionKey.icon"></component></Icon>
                  </el-icon>
                </template>
                <el-option
                  class="custom-style-option"
                  v-for="option in styleOptionKey.customOption"
                  :key="option.value"
                  :label="option.name"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
          </el-tooltip>
        </template>

        <template v-for="styleColorKey in styleColorKeyArray">
          <!-- 颜色项按当前组件 style 中存在的字段动态出现，避免工具条展示无效控件。 -->
          <el-tooltip
            :key="styleColorKey.value"
            v-if="styleForm[styleColorKey.value] !== undefined"
            effect="dark"
            placement="bottom"
          >
            <template #content> {{ styleColorKey.label }} </template>
            <el-form-item
              :effect="themes"
              class="form-item no-margin-bottom"
              :class="'form-item-' + themes"
            >
              <el-color-picker
                :title="t('chart.text_color')"
                v-model="styleForm[styleColorKey.value]"
                class="color-picker-style"
                :prefix-icon="styleColorKey.icon"
                :triggerWidth="styleColorKey.width"
                is-custom
                show-alpha
                :predefine="state.predefineColors"
                @change="
                  changeStyle({ key: styleColorKey.value, value: styleForm[styleColorKey.value] })
                "
              >
              </el-color-picker>
            </el-form-item>
          </el-tooltip>
        </template>

        <template v-for="styleOptionMountedKey in styleOptionMountedKeyArray">
          <!-- 尺寸类控件展示反算后的画布值，写回时再按缩放比例转换为真实像素。 -->
          <el-tooltip
            :key="styleOptionMountedKey.value"
            v-if="styleForm[styleOptionMountedKey.value] !== undefined"
            effect="dark"
            placement="bottom"
          >
            <template #content> {{ styleOptionMountedKey.label }} </template>
            <el-form-item
              :effect="themes"
              class="form-item no-margin-bottom"
              :class="'form-item-' + themes"
            >
              <el-select
                :style="{ width: styleOptionMountedKey.width }"
                :effect="themes"
                v-model="styleMounted[styleOptionMountedKey.value]"
                size="small"
                @change="sizeChange(styleOptionMountedKey.value)"
              >
                <template #prefix>
                  <el-icon :class="{ 'dark-icon': themes === 'dark' }">
                    <Icon><component :is="styleOptionMountedKey.icon"></component></Icon>
                  </el-icon>
                </template>
                <el-option
                  class="custom-style-option"
                  v-for="option in styleOptionMountedKey.customOption"
                  :key="option.value"
                  :label="option.name"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
          </el-tooltip>
        </template>

        <template v-for="styleOptionKey in styleOptionKeyArray">
          <!-- 普通选项直接写入组件 style，不需要根据画布缩放做像素换算。 -->
          <el-tooltip
            :key="styleOptionKey.value"
            v-if="styleForm[styleOptionKey.value] !== undefined"
            effect="dark"
            placement="bottom"
          >
            <template #content> {{ styleOptionKey.label }} </template>
            <el-form-item class="form-item no-margin-bottom" :class="'form-item-' + themes">
              <el-select
                :style="{ width: styleOptionKey.width }"
                :effect="themes"
                v-model="styleForm[styleOptionKey.value]"
                size="small"
                @change="changeStylePre(styleOptionKey.value)"
              >
                <template #prefix>
                  <el-icon :class="{ 'dark-icon': themes === 'dark' }">
                    <Icon><component :is="styleOptionKey.icon"></component></Icon>
                  </el-icon>
                </template>
                <el-option
                  class="custom-style-option"
                  v-for="option in styleOptionKey.customOption"
                  :key="option.value"
                  :label="option.name"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
          </el-tooltip>
        </template>
        <!-- 字重、斜体和下划线使用图标按钮表达二态切换，减少文本工具条宽度。 -->
        <el-tooltip v-if="styleForm.fontWeight !== undefined" effect="dark" placement="bottom">
          <template #content>
            {{ t('chart.bolder') }}
          </template>
          <div
            class="icon-btn"
            :class="{ dark: themes === 'dark', active: styleForm.fontWeight === 'bold' }"
            @click="checkBold"
          >
            <el-icon>
              <Icon name="icon_bold_outlined"><icon_bold_outlined class="svg-icon" /></Icon>
            </el-icon>
          </div>
        </el-tooltip>

        <el-tooltip v-if="styleForm.fontStyle !== undefined" effect="dark" placement="bottom">
          <template #content>
            {{ t('chart.italic') }}
          </template>
          <div
            class="icon-btn"
            :class="{ dark: themes === 'dark', active: styleForm.fontStyle === 'italic' }"
            @click="checkItalic"
          >
            <el-icon>
              <Icon name="icon_italic_outlined"><icon_italic_outlined class="svg-icon" /></Icon>
            </el-icon>
          </div>
        </el-tooltip>
        <el-tooltip v-if="styleForm.textDecoration !== undefined" effect="dark" placement="bottom">
          <template #content>
            {{ t('visualization.text_decoration') }}
          </template>
          <div
            class="icon-btn"
            :class="{ dark: themes === 'dark', active: styleForm.textDecoration === 'underline' }"
            @click="checkTextDecoration"
          >
            <el-icon>
              <Icon name="style-underline"><styleUnderline class="svg-icon" /></Icon>
            </el-icon>
          </div>
        </el-tooltip>
        <template v-if="styleForm.textAlign">
          <!-- 横向对齐按钮直接写入 CSS text-align，适用于文本、指标和部分标题组件。 -->
          <div class="m-divider" :class="'custom-divider-' + themes"></div>
          <div style="display: flex">
            <el-tooltip effect="dark" placement="bottom">
              <template #content>
                {{ t('chart.text_pos_left') }}
              </template>
              <div
                class="icon-btn"
                :class="{ dark: themes === 'dark', active: styleForm.textAlign === 'left' }"
                @click="setPosition('textAlign', 'left')"
              >
                <el-icon>
                  <Icon name="icon_left-alignment_outlined"
                    ><icon_leftAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
            <el-tooltip effect="dark" placement="bottom">
              <template #content>
                {{ t('chart.text_pos_center') }}
              </template>
              <div
                class="icon-btn"
                :class="{ dark: themes === 'dark', active: styleForm.textAlign === 'center' }"
                @click="setPosition('textAlign', 'center')"
              >
                <el-icon>
                  <Icon name="icon_center-alignment_outlined"
                    ><icon_centerAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
            <el-tooltip effect="dark" placement="bottom">
              <template #content>
                {{ t('chart.text_pos_right') }}
              </template>
              <div
                class="icon-btn"
                :class="{ dark: themes === 'dark', active: styleForm.textAlign === 'right' }"
                @click="setPosition('textAlign', 'right')"
              >
                <el-icon>
                  <Icon name="icon_right-alignment_outlined"
                    ><icon_rightAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </div>
        </template>
        <template v-if="styleForm.headHorizontalPosition">
          <!-- 表头横向位置使用独立字段，避免覆盖组件主体文本的 textAlign。 -->
          <div class="m-divider"></div>
          <el-tooltip effect="dark" placement="bottom">
            <template #content>
              {{ t('chart.text_pos_left') }}
            </template>
            <div
              class="icon-btn"
              :class="{
                dark: themes === 'dark',
                active: styleForm.headHorizontalPosition === 'left'
              }"
              @click="setPosition('headHorizontalPosition', 'left')"
            >
              <el-icon>
                <Icon name="icon_left-alignment_outlined"
                  ><icon_leftAlignment_outlined class="svg-icon"
                /></Icon>
              </el-icon>
            </div>
          </el-tooltip>
          <el-tooltip effect="dark" placement="bottom">
            <template #content>
              {{ t('chart.text_pos_center') }}
            </template>
            <div
              class="icon-btn"
              :class="{
                dark: themes === 'dark',
                active: styleForm.headHorizontalPosition === 'center'
              }"
              @click="setPosition('headHorizontalPosition', 'center')"
            >
              <el-icon>
                <Icon name="icon_center-alignment_outlined"
                  ><icon_centerAlignment_outlined class="svg-icon"
                /></Icon>
              </el-icon>
            </div>
          </el-tooltip>
          <el-tooltip effect="dark" placement="bottom">
            <template #content>
              {{ t('chart.text_pos_right') }}
            </template>
            <div
              class="icon-btn"
              :class="{
                dark: themes === 'dark',
                active: styleForm.headHorizontalPosition === 'right'
              }"
              @click="setPosition('headHorizontalPosition', 'right')"
            >
              <el-icon>
                <Icon name="icon_right-alignment_outlined"
                  ><icon_rightAlignment_outlined class="svg-icon"
                /></Icon>
              </el-icon>
            </div>
          </el-tooltip>
        </template>
      </el-space>
    </el-row>
  </el-row>
</template>

<script lang="tsx" setup>
import dvStyleBackgroundColor from '@/assets/svg/dv-style-backgroundColor.svg'
import dvStyleColor from '@/assets/svg/dv-style-color.svg'
import dvStyleHeadFontActiveColor from '@/assets/svg/dv-style-headFontActiveColor.svg'
import dvStyleHeadFontColor from '@/assets/svg/dv-style-headFontColor.svg'
import dvStyleScrollSpeed from '@/assets/svg/dv-style-scroll-speed.svg'
import dvStyleOpacity from '@/assets/svg/dv-style-opacity.svg'
import dvStyleBlur from '@/assets/svg/dv-style-blur.svg'
import dvStyleFontSize from '@/assets/svg/dv-style-fontSize.svg'
import dvStyleLetterSpacing from '@/assets/svg/dv-style-letterSpacing.svg'
import dvStyleActiveFont from '@/assets/svg/dv-style-activeFont.svg'
import dvStyleFontFamily from '@/assets/svg/dv-style-fontFamily.svg'
import icon_bold_outlined from '@/assets/svg/icon_bold_outlined.svg'
import icon_italic_outlined from '@/assets/svg/icon_italic_outlined.svg'
import styleUnderline from '@/assets/svg/style-underline.svg'
import icon_leftAlignment_outlined from '@/assets/svg/icon_left-alignment_outlined.svg'
import icon_centerAlignment_outlined from '@/assets/svg/icon_center-alignment_outlined.svg'
import icon_rightAlignment_outlined from '@/assets/svg/icon_right-alignment_outlined.svg'
import { computed, reactive, ref, toRefs, watch } from 'vue'
import { COLOR_PANEL } from '@/views/chart/components/editor/util/chart'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { useI18n } from '@/hooks/web/useI18n'
import Icon from '@/components/icon-custom/src/Icon.vue'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { storeToRefs } from 'pinia'
import { ElIcon } from 'element-plus-secondary'
const { t } = useI18n()
const dvMainStore = dvMainStoreWithOut()
const snapshotStore = snapshotStoreWithOut()

const { canvasStyleData } = storeToRefs(dvMainStore)

// 通用样式工具条根据传入组件的 style 字段动态展示可编辑项。
const props = withDefaults(
  defineProps<{
    themes?: EditorTheme
    element: any
  }>(),
  {
    themes: 'dark'
  }
)
const { themes, element } = toRefs(props)
// 样式变更由父级统一落盘和刷新组件，这里只派发变更字段。
const emits = defineEmits(['onStyleAttrChange'])
// 保存按画布缩放反算后的显示值，避免面板中直接展示缩放后的实际像素。
const styleMounted = ref({
  opacity: 1,
  fontSize: 22,
  activeFontSize: 22,
  letterSpacing: 0,
  scrollSpeed: 0,
  fontWeight: 'normal',
  fontStyle: 'normal',
  textAlign: 'center',
  color: '#000000'
})

const fontFamilyList = [
  { name: '微软雅黑', value: 'Microsoft YaHei' },
  { name: '宋体', value: 'SimSun, "Songti SC", STSong' },
  { name: '黑体', value: 'SimHei, Helvetica' },
  { name: '楷体', value: 'KaiTi, "Kaiti SC", STKaiti' }
]

// 滚动速度枚举直接写入组件样式，0 表示停止滚动。
const scrollSpeedList = [
  { name: '停止', value: 0 },
  { name: '10', value: 10 },
  { name: '20', value: 20 },
  { name: '30', value: 30 },
  { name: '40', value: 40 },
  { name: '50', value: 50 },
  { name: '60', value: 60 },
  { name: '70', value: 70 },
  { name: '80', value: 80 },
  { name: '90', value: 90 },
  { name: '100', value: 100 },
  { name: '150', value: 150 },
  { name: '200', value: 200 }
]

// 透明度使用 0.1 步进，和颜色面板中的 alpha 调整粒度保持接近。
const opacitySizeList = [
  { name: '0', value: 0 },
  { name: '0.1', value: 0.1 },
  { name: '0.2', value: 0.2 },
  { name: '0.3', value: 0.3 },
  { name: '0.4', value: 0.4 },
  { name: '0.5', value: 0.5 },
  { name: '0.6', value: 0.6 },
  { name: '0.7', value: 0.7 },
  { name: '0.8', value: 0.8 },
  { name: '0.9', value: 0.9 },
  { name: '1', value: 1 }
]
// 背景模糊保存为 CSS filter 字符串，渲染层可直接应用。
const backdropBlurList = [
  { name: '0', value: 'blur(0px)' },
  { name: '1', value: 'blur(1px)' },
  { name: '2', value: 'blur(2px)' },
  { name: '3', value: 'blur(3px)' },
  { name: '4', value: 'blur(4px)' },
  { name: '5', value: 'blur(5px)' },
  { name: '6', value: 'blur(6px)' },
  { name: '7', value: 'blur(7px)' },
  { name: '8', value: 'blur(8px)' },
  { name: '9', value: 'blur(9px)' },
  { name: '10', value: 'blur(10px)' },
  { name: '11', value: 'blur(11px)' },
  { name: '12', value: 'blur(12px)' },
  { name: '13', value: 'blur(13px)' },
  { name: '14', value: 'blur(14px)' },
  { name: '15', value: 'blur(15px)' },
  { name: '16', value: 'blur(16px)' },
  { name: '17', value: 'blur(17px)' },
  { name: '18', value: 'blur(18px)' },
  { name: '19', value: 'blur(19px)' },
  { name: '20', value: 'blur(20px)' },
  { name: '21', value: 'blur(21px)' },
  { name: '22', value: 'blur(22px)' },
  { name: '23', value: 'blur(23px)' },
  { name: '24', value: 'blur(24px)' },
  { name: '25', value: 'blur(25px)' },
  { name: '26', value: 'blur(26px)' },
  { name: '27', value: 'blur(27px)' },
  { name: '28', value: 'blur(28px)' },
  { name: '29', value: 'blur(29px)' },
  { name: '30', value: 'blur(30px)' }
]

// 当前组件样式表单直接指向组件 style，面板修改会即时反映到画布元素。
const styleForm = computed<any>(() => element.value.style)
// 维护颜色预设和面板内部状态，避免每次渲染重复创建颜色列表。
const state = reactive({
  fontSize: [],
  isSetting: false,
  predefineColors: COLOR_PANEL
})

const styleColorKeyArray = [
  { value: 'color', label: t('visualization.color'), width: 90, icon: dvStyleColor },
  {
    value: 'headFontColor',
    label: t('visualization.head_font_color'),
    width: 90,
    icon: dvStyleHeadFontColor
  },
  {
    value: 'headFontActiveColor',
    label: t('visualization.head_font_active_color'),
    width: 90,
    icon: dvStyleHeadFontActiveColor
  },
  {
    value: 'backgroundColor',
    label: t('visualization.background_color'),
    width: 90,
    icon: dvStyleBackgroundColor
  }
]

// 字间距固定提供 0 到 60，覆盖普通文本和数字卡片的常见间距范围。
const letterSpacingList = computed(() => {
  const arr = []
  for (let i = 0; i <= 60; i = i + 1) {
    arr.push({
      name: i + '',
      value: i
    })
  }
  return arr
})

// 字号先按 1px 精度覆盖常用区间，再按 10px 覆盖大屏标题区间。
const fontSizeList = computed(() => {
  const arr = []
  for (let i = 10; i <= 60; i = i + 1) {
    arr.push({
      name: i + '',
      value: i
    })
  }
  for (let i = 70; i <= 300; i = i + 10) {
    arr.push({
      name: i + '',
      value: i
    })
  }
  return arr
})
const styleOptionKeyArrayPre = [
  {
    value: 'fontFamily',
    label: t('visualization.font_family'),
    customOption: fontFamilyList,
    width: '188px',
    icon: dvStyleFontFamily
  }
]

// 这些尺寸属性会随画布缩放，需要通过 styleMounted 做显示值和真实值转换。
const styleOptionMountedKeyArray = [
  {
    value: 'letterSpacing',
    label: t('visualization.letter_spacing'),
    customOption: letterSpacingList.value,
    width: '90px',
    icon: dvStyleLetterSpacing
  },
  {
    value: 'fontSize',
    label: t('visualization.font_size'),
    customOption: fontSizeList.value,
    width: '90px',
    icon: dvStyleFontSize
  },
  {
    value: 'activeFontSize',
    label: t('visualization.active_font_size'),
    customOption: fontSizeList.value,
    width: '90px',
    icon: dvStyleActiveFont
  }
]

// 这些样式值不随画布缩放，直接写入组件 style 即可。
const styleOptionKeyArray = [
  {
    value: 'scrollSpeed',
    label: t('visualization.scroll_speed'),
    customOption: scrollSpeedList,
    width: '90px',
    icon: dvStyleScrollSpeed
  },
  {
    value: 'opacity',
    label: t('visualization.opacity'),
    customOption: opacitySizeList,
    width: '90px',
    icon: dvStyleOpacity
  },
  {
    value: 'backdropFilter',
    label: t('visualization.background_opacity'),
    customOption: backdropBlurList,
    width: '90px',
    icon: dvStyleBlur
  }
]

// 初始化时将真实样式按当前画布缩放反算，保证面板值符合用户认知。
const styleInit = () => {
  if (element.value) {
    Object.keys(styleMounted.value).forEach(key => {
      // 面板展示值按 100% 画布缩放反算，避免用户看到被缩放后的像素值。
      styleMounted.value[key] = Math.round(
        (element.value.style[key] * 100) / canvasStyleData.value.scale
      )
    })
  }
}

// 无需缩放的样式直接透传当前 style 值，保持事件结构一致。
const changeStylePre = key => {
  changeStyle({ key: key, value: element.value.style[key] })
}

// 尺寸类属性按画布缩放换算回真实像素，再通知父级记录和刷新。
const sizeChange = key => {
  element.value.style[key] = Math.round(
    (styleMounted.value[key] * canvasStyleData.value.scale) / 100
  )
  changeStyle({ key: key, value: element.value.style[key] })
}

// 每次样式变更都记录快照，保证撤销/重做能覆盖工具条操作。
const changeStyle = params => {
  snapshotStore.recordSnapshotCache('changeStyle')
  emits('onStyleAttrChange', params)
}

// 加粗按钮在 normal/bold 之间切换，避免写入浏览器兼容性不稳定的数值字重。
const checkBold = () => {
  if (styleForm.value.fontWeight === 'normal') {
    styleForm.value.fontWeight = 'bold'
  } else {
    styleForm.value.fontWeight = 'normal'
  }
  changeStyle({ key: 'fontWeight', value: styleForm.value.fontWeight })
}

// 斜体按钮在 normal/italic 之间切换，保持与 CSS font-style 取值一致。
const checkItalic = () => {
  if (styleForm.value.fontStyle === 'normal') {
    styleForm.value.fontStyle = 'italic'
  } else {
    styleForm.value.fontStyle = 'normal'
  }
  changeStyle({ key: 'fontStyle', value: styleForm.value.fontStyle })
}

// 下划线按钮只控制 underline/none，避免影响删除线等未暴露的装饰样式。
const checkTextDecoration = () => {
  if (styleForm.value.textDecoration === 'none') {
    styleForm.value.textDecoration = 'underline'
  } else {
    styleForm.value.textDecoration = 'none'
  }
  changeStyle({ key: 'textDecoration', value: styleForm.value.textDecoration })
}

// 文本对齐类按钮统一走 setPosition，便于横向位置和表头位置复用。
function setPosition(key, p: 'left' | 'center' | 'right') {
  styleForm.value[key] = p
  changeStyle({ key: key, value: p })
}

// 组件切换或外部样式变化后重新计算面板显示值，避免缩放值滞留。
watch(
  () => element.value,
  () => {
    styleInit()
  },
  {
    deep: true,
    immediate: true
  }
)
</script>

<style lang="less">
.custom-style-option::after {
  // 下拉项不展示默认选中伪元素，避免与自定义图标前缀形成重复视觉。
  display: none;
}
</style>

<style scoped lang="less">
.custom-item-text {
  font-size: 12px !important;
  font-weight: 400 !important;
  line-height: 20px;
  color: #646a73 !important;
}

:deep(.ed-radio) {
  margin-right: 0;
}
:deep(.ed-radio-group) {
  padding-top: 2px;
}

:deep(.ed-checkbox__input) {
  // 工具条使用图标作为可见交互控件，隐藏原生勾选框。
  display: none;
}

:deep(.ed-checkbox.is-checked) {
  .ed-checkbox__label {
    .bash-icon {
      background: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1));
      border-radius: 6px;
      color: var(--ed-color-primary);
    }
  }
}

:deep(.ed-radio.is-checked) {
  .ed-radio__label {
    .bash-icon {
      background: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1));
      border-radius: 6px;
    }
  }
}

:deep(.ed-radio__input) {
  // 对齐按钮同样隐藏原生 radio，只保留图标态。
  display: none;
}

:deep(.ed-radio__input.is-checked) {
  .ed-radio__inner {
    padding: 4px;
    background-color: green;
    background-clip: content-box;
  }
}

.bash-icon {
  width: 24px;
  height: 24px;
}

.custom-divider {
  margin: 5px 0 0 8px;
  height: 20px;
  width: 1px;
  background-color: rgba(31, 35, 41, 0.15);
}

.icon-btn {
  // 工具条按钮固定 24px 尺寸，保证不同图标切换 active 时不改变布局。
  font-size: 16px;
  width: 24px;
  height: 24px;
  text-align: center;
  border-radius: 6px;
  padding-top: 1px;

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
.m-divider {
  // 工具条内分隔线只做功能分组，不参与组件样式配置。
  width: 1px;
  height: 18px;
  background: rgba(31, 35, 41, 0.15);
}

.custom-divider-light {
  background-color: rgba(31, 35, 41, 0.15);
}

.custom-divider-dark {
  background-color: #757575;
}
.form-item {
  &.no-margin-bottom {
    margin-bottom: 0 !important;
  }
}
.custom-row-inner {
  margin: 0 0 16px;
}

.dark-icon {
  color: #ffffff;
}

.icon-checkbox {
  :deep(.ed-checkbox__input) {
    display: none;
  }
  :deep(.ed-checkbox__label) {
    padding: 0;
  }
}

.icon-btn {
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

.form-item-dark {
  :deep(.ed-color-picker__trigger) {
    border-color: #5f5f5f;
  }
  :deep(.ed-color-picker__custom-icon::after) {
    background-color: #5f5f5f;
  }
}
</style>
