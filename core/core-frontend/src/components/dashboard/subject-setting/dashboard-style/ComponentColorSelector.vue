<template>
  <div style="width: 100%" ref="containerRef">
    <!-- 组件配色面板按系列色、透明度、标签、提示框和表格样式分区维护。 -->
    <el-form
      ref="colorFormRef"
      :model="colorForm"
      size="small"
      style="width: 100%; padding-bottom: 8px"
    >
      <div style="width: 100%; padding: 16px 8px 0">
        <!-- 系列色选择器维护画布级默认配色，变更后会影响所有使用默认色板的图表。 -->
        <custom-color-style-select
          v-if="colorAreaInit"
          class="custom-color-pick"
          :class="{ 'custom-dark': themes === 'dark' }"
          :model-value="state"
          @update:model-value="value => Object.assign(state, value)"
          :themes="themes"
          @change-basic-style="changeColorOption('value')"
        />

        <!-- 渐变和透明度属于基础图表色板配置，直接写回 basicStyle。 -->
        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-checkbox
            size="small"
            :effect="themes"
            v-model="colorForm['basicStyle']['gradient']"
            @change="changeColorCase('gradient')"
          >
            {{ $t('chart.gradient') }}{{ $t('chart.color') }}
          </el-checkbox>
        </el-form-item>

        <div class="alpha-setting">
          <label class="alpha-label" :class="{ dark: 'dark' === themes }">
            {{ t('chart.not_alpha') }}
          </label>
          <el-row style="flex: 1" :gutter="8">
            <el-col :span="13">
              <el-form-item class="form-item alpha-slider" :class="'form-item-' + themes">
                <el-slider
                  :effect="themes"
                  v-model="colorForm['basicStyle']['alpha']"
                  @change="changeColorCase('alpha')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="11">
              <el-form-item
                style="padding-top: 4px"
                class="form-item"
                :class="'form-item-' + themes"
              >
                <el-input
                  type="number"
                  :effect="themes"
                  v-model="colorForm['basicStyle']['alpha']"
                  :min="0"
                  :max="100"
                  class="alpha-input-number"
                  :controls="false"
                  @change="changeColorCase('alpha')"
                >
                  <template #suffix> % </template>
                </el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </div>
        <el-divider class="m-divider" :class="'m-divider-' + themes"></el-divider>
      </div>

      <!-- 标签样式是全局同步项，修改后会批量刷新每个图表的标签配置。 -->
      <el-collapse-item
        :title="t('visualization.chart_label')"
        name="chart_label"
        class="inner-collapse"
        :effect="themes"
        :class="`inner-collapse_${themes}`"
      >
        <div style="padding: 0 8px 8px">
          <el-row :gutter="8">
            <el-col :span="12">
              <el-form-item :label="t('chart.text_color')" class="form-item">
                <el-color-picker
                  :trigger-width="colorPickerWidth"
                  v-model="colorForm.label.color"
                  size="small"
                  :predefine="predefineColors"
                  is-custom
                  :effect="themes"
                  @change="changeLabelCase"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('chart.text_fontsize')" class="form-item">
                <el-select
                  style="width: 100%"
                  v-model="colorForm.label.fontSize"
                  size="small"
                  :effect="themes"
                  @change="changeLabelCase"
                >
                  <el-option
                    v-for="option in fontSizeList"
                    :key="option.value"
                    :label="option.name"
                    :value="option.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </div>
      </el-collapse-item>

      <!-- tooltip 样式同样按全局同步处理，包括系列级 tooltip formatter。 -->
      <el-collapse-item
        :title="t('visualization.chart_tooltip')"
        name="chart_tooltip"
        class="inner-collapse"
        :effect="themes"
        :class="`inner-collapse_${themes}`"
      >
        <div style="padding: 0 8px 8px">
          <el-row :gutter="8">
            <el-col :span="12">
              <el-form-item :label="t('visualization.chart_tooltip_bg_color')" class="form-item">
                <el-color-picker
                  :trigger-width="colorPickerWidth"
                  v-model="colorForm.tooltip.backgroundColor"
                  size="small"
                  :predefine="predefineColors"
                  is-custom
                  :effect="themes"
                  @change="changeTooltipCase"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('chart.text_color')" class="form-item">
                <el-color-picker
                  :trigger-width="colorPickerWidth"
                  v-model="colorForm.tooltip.color"
                  size="small"
                  :predefine="predefineColors"
                  is-custom
                  :effect="themes"
                  @change="changeTooltipCase"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('chart.text_fontsize')" class="form-item">
                <el-select
                  style="width: 100%"
                  v-model="colorForm.tooltip.fontSize"
                  size="small"
                  :effect="themes"
                  @change="changeTooltipCase"
                >
                  <el-option
                    v-for="option in fontSizeList"
                    :key="option.value"
                    :label="option.name"
                    :value="option.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </div>
      </el-collapse-item>

      <!-- 表格配色覆盖表头、明细行、条纹、分页器和滚动条等表格专属样式。 -->
      <el-collapse-item
        :title="t('visualization.table_color_matching')"
        name="table_color_matching"
        class="inner-collapse"
        :effect="themes"
        :class="`inner-collapse_${themes}`"
      >
        <div style="padding: 0 8px 8px">
          <el-row :gutter="8">
            <el-col :span="12">
              <el-form-item :label="t('chart.table_header_row_bg')" class="form-item">
                <el-color-picker
                  :trigger-width="colorPickerWidth"
                  v-model="colorForm['tableHeader']['tableHeaderBgColor']"
                  size="small"
                  :predefine="predefineColors"
                  is-custom
                  show-alpha
                  :effect="themes"
                  @change="changeColorCase('tableHeaderBgColor')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('chart.table_item_bg')" class="form-item">
                <el-color-picker
                  :trigger-width="colorPickerWidth"
                  v-model="colorForm['tableCell']['tableItemBgColor']"
                  size="small"
                  :predefine="predefineColors"
                  :effect="themes"
                  show-alpha
                  is-custom
                  @change="changeColorCase('tableItemBgColor')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('chart.stripe')" class="form-item">
                <el-color-picker
                  :trigger-width="colorPickerWidth"
                  v-model="colorForm['tableCell']['tableItemSubBgColor']"
                  size="small"
                  :predefine="predefineColors"
                  is-custom
                  show-alpha
                  :effect="themes"
                  @change="changeColorCase('tableItemSubBgColor')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('chart.table_header_font_color')" class="form-item">
                <el-color-picker
                  :trigger-width="colorPickerWidth"
                  v-model="colorForm['tableHeader']['tableHeaderFontColor']"
                  :effect="themes"
                  size="small"
                  :predefine="predefineColors"
                  is-custom
                  @change="changeColorCase('tableHeaderFontColor')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('chart.colBackgroundColor')" class="form-item">
                <el-color-picker
                  :trigger-width="colorPickerWidth"
                  v-model="colorForm['tableHeader']['tableHeaderColBgColor']"
                  size="small"
                  :predefine="predefineColors"
                  color-format="rgb"
                  :effect="themes"
                  show-alpha
                  is-custom
                  @change="changeColorCase('tableHeaderColBgColor')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('chart.cornerBackgroundColor')" class="form-item">
                <el-color-picker
                  :trigger-width="colorPickerWidth"
                  v-model="colorForm['tableHeader']['tableHeaderCornerBgColor']"
                  size="small"
                  :predefine="predefineColors"
                  color-format="rgb"
                  :effect="themes"
                  show-alpha
                  is-custom
                  @change="changeColorCase('tableHeaderCornerBgColor')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('chart.table_item_font_color')" class="form-item">
                <el-color-picker
                  :trigger-width="colorPickerWidth"
                  v-model="colorForm['tableCell']['tableFontColor']"
                  size="small"
                  :predefine="predefineColors"
                  :effect="themes"
                  is-custom
                  @change="changeColorCase('tableFontColor')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('chart.table_border_color')" class="form-item">
                <el-color-picker
                  :trigger-width="colorPickerWidth"
                  v-model="colorForm.basicStyle['tableBorderColor']"
                  size="small"
                  :predefine="predefineColors"
                  :effect="themes"
                  is-custom
                  show-alpha
                  @change="changeColorCase('tableBorderColor')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('chart.table_scroll_bar_color')" class="form-item">
                <el-color-picker
                  :trigger-width="colorPickerWidth"
                  v-model="colorForm.basicStyle['tableScrollBarColor']"
                  size="small"
                  :predefine="predefineColors"
                  color-format="rgb"
                  :effect="themes"
                  show-alpha
                  is-custom
                  @change="changeColorCase('tableScrollBarColor')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('components.pager_color')" class="form-item">
                <div style="display: flex; width: 100%; gap: 8px">
                  <el-color-picker
                    :trigger-width="60"
                    v-model="seniorForm.pagerColor"
                    size="small"
                    :predefine="predefineColors"
                    color-format="rgb"
                    :effect="themes"
                    show-alpha
                    is-custom
                    @change="changePagerColorChange"
                  />
                  <el-select
                    style="flex: 1; width: auto"
                    :title="t('chart.text_fontsize')"
                    v-model="seniorForm.pagerSize"
                    size="small"
                    :effect="themes"
                    @change="changePagerColorChange"
                  >
                    <el-option
                      v-for="option in fontSizeList"
                      :key="option.value"
                      :label="option.name"
                      :value="option.value"
                    />
                  </el-select>
                </div>
              </el-form-item>
            </el-col>
          </el-row>
        </div>
      </el-collapse-item>
    </el-form>
  </div>
</template>

<script lang="ts" setup>
import { computed, nextTick, onMounted, reactive, ref, toRefs, PropType } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { COLOR_PANEL, DEFAULT_BASIC_STYLE } from '@/views/chart/components/editor/util/chart'
import { useI18n } from '@/hooks/web/useI18n'
import eventBus from '@/utils/eventBus'
import { storeToRefs } from 'pinia'
import CustomColorStyleSelect from '@/views/chart/components/editor/editor-style/components/CustomColorStyleSelect.vue'
import elementResizeDetectorMaker from 'element-resize-detector'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { useEmitt } from '@/hooks/web/useEmitt'
const { t } = useI18n()
const snapshotStore = snapshotStoreWithOut()

// 组件配色面板维护画布级图表、表格、标签、提示框和分页样式。
const props = defineProps({
  themes: {
    type: String as PropType<EditorTheme>,
    default: 'light'
  }
})

let colorAreaInit = ref(false)

const { themes } = toRefs(props)
// 配色变更由父级统一应用到画布配置和组件重渲染。
const emits = defineEmits(['onColorChange'])
// 颜色配置表单实例预留给后续校验和滚动定位。
const colorFormRef = ref(null)

// 字号选项覆盖 6 到 40 的偶数字号，适配标签和 tooltip 的常用范围。
const fontSizeList = computed(() => {
  const arr = []
  for (let i = 6; i <= 40; i = i + 2) {
    arr.push({
      name: i + '',
      value: i
    })
  }
  return arr
})

// 当前画布主题颜色配置，所有图表默认配色都从这里读取。
const colorForm = computed(
  () => canvasStyleData.value.component.chartColor as DeepPartial<ChartAttr>
)

// 当前画布高级样式配置承载分页器等非基础图表样式。
const seniorForm = computed(() => canvasStyleData.value.component.seniorStyleSetting)

const predefineColors = COLOR_PANEL

// 维护颜色面板表单和当前选中的自定义颜色序号。
const state = reactive({
  basicStyleForm: JSON.parse(JSON.stringify(DEFAULT_BASIC_STYLE)) as ChartBasicStyle,
  customColor: null,
  colorIndex: 0
})
const dvMainStore = dvMainStoreWithOut()
const { canvasStyleData, canvasViewInfo } = storeToRefs(dvMainStore)
// 初始化时同步默认系列色，并补齐历史表头字体色缺省值。
const initForm = () => {
  state.customColor = colorForm.value.basicStyle.colors[0]
  setTimeout(() => {
    // 自定义颜色选择器依赖默认基础样式，延迟到画布样式完成回填后再渲染。
    state.basicStyleForm = canvasStyleData.value.component.chartColor.basicStyle
    colorAreaInit.value = true
  }, 1000)
  const tableHeader = colorForm.value.tableHeader
  const tableCell = colorForm.value.tableCell
  // 老配置可能没有独立表头字体色，默认继承单元格字体色。
  tableHeader.tableHeaderFontColor = tableHeader.tableHeaderFontColor ?? tableCell.tableFontColor
}
// 自定义颜色选择器变更后先回写基础样式，再触发统一颜色变更流程。
const changeColorOption = (modifyName = 'value') => {
  colorForm.value.basicStyle = state.basicStyleForm
  changeColorCase(modifyName)
}

// 分页颜色属于高级样式，单独记录快照即可由画布渲染时读取。
const changePagerColorChange = () => {
  snapshotStore.recordSnapshotCache('changePagerColorChange')
}

// modifyName 标记本次变更来源，父级可按需执行局部或全量刷新。
const changeColorCase = modifyName => {
  colorForm.value['modifyName'] = modifyName
  emits('onColorChange', colorForm.value)
}

let canvasAttrInit = false

// 批量同步标签样式到所有图表，包括系列标签格式化中的独立颜色和字号。
const changeLabelCase = () => {
  if (canvasAttrInit) {
    const val = colorForm.value.label
    Object.keys(canvasViewInfo.value).forEach(function (viewId) {
      const viewInfo = canvasViewInfo.value[viewId]
      try {
        // 图表自身标签配置和系列标签格式化配置都需要同步画布级标签样式。
        const label = viewInfo.customAttr?.label
        if (label) {
          label.color = val.color
          label.fontSize = val.fontSize
        }
        const labelFormatter = viewInfo.customAttr?.label?.seriesLabelFormatter
        if (labelFormatter && Array.isArray(labelFormatter)) {
          // 系列标签配置会覆盖默认标签样式，需要同步更新每一项。
          labelFormatter.forEach(item => {
            item.color = val.color
            item.fontSize = val.fontSize
          })
        }
        useEmitt().emitter.emit('renderChart-' + viewId, viewInfo)
      } catch (e) {
        console.warn('changeLabelCase-error')
      }
    })
    snapshotStore.recordSnapshotCache('renderChart')
  }
}

// 批量同步提示框样式到所有图表，包括系列 tooltip 格式化配置。
const changeTooltipCase = () => {
  if (canvasAttrInit) {
    const val = colorForm.value.tooltip
    Object.keys(canvasViewInfo.value).forEach(function (viewId) {
      const viewInfo = canvasViewInfo.value[viewId]
      try {
        // tooltip 的基础样式和系列格式化样式分开存储，需要同时更新。
        const tooltip = viewInfo.customAttr?.tooltip
        if (tooltip) {
          tooltip.color = val.color
          tooltip.fontSize = val.fontSize
          tooltip.backgroundColor = val.backgroundColor
        }
        const tooltipFormatter = viewInfo.customAttr?.tooltip?.seriesTooltipFormatter
        if (tooltipFormatter && Array.isArray(tooltipFormatter)) {
          // 系列 tooltip 配置同样需要同步，否则会继续使用旧颜色和字号。
          tooltipFormatter.forEach(item => {
            item.color = val.color
            item.fontSize = val.fontSize
            item.backgroundColor = val.backgroundColor
          })
        }
        useEmitt().emitter.emit('renderChart-' + viewId, viewInfo)
      } catch (e) {
        console.warn('changeTooltipCase-error')
      }
    })
    snapshotStore.recordSnapshotCache('renderChart')
  }
}

// 颜色面板容器实例用于监听宽度变化，动态调整颜色选择器宽度。
const containerRef = ref()
// 容器宽度由 resize detector 更新，避免折叠面板宽度变化后控件溢出。
const containerWidth = ref()

// 根据容器宽度计算颜色选择器宽度，小面板使用紧凑布局。
const colorPickerWidth = computed(() => {
  if (containerWidth.value <= 240) {
    // 折叠面板较窄时使用紧凑宽度，避免颜色选择器挤压右侧表单项。
    return 108
  } else {
    return 197
  }
})

onMounted(() => {
  // 初始化、主题切换和容器缩放都可能改变配色面板展示状态。
  initForm()
  eventBus.on('onThemeColorChange', initForm)

  const erd = elementResizeDetectorMaker()
  containerWidth.value = containerRef.value?.offsetWidth
  erd.listenTo(containerRef.value, () => {
    nextTick(() => {
      // 宽度更新延迟到 DOM 完成布局后读取，避免折叠动画期间读到旧尺寸。
      containerWidth.value = containerRef.value?.offsetWidth
    })
  })

  nextTick(() => {
    // 首次挂载完成后才允许批量同步图表，防止初始化过程触发无效快照。
    canvasAttrInit = true
  })
})
</script>

<style scoped lang="less">
.form-item-slider :deep(.ed-form-item__label) {
  /* 滑块标签与控件垂直居中，保持和编辑器其它数值项一致。 */
  font-size: 12px;
  line-height: 38px;
  justify-content: flex-start;
}

.form-item :deep(.ed-form-item__label) {
  font-size: 12px;
  justify-content: flex-start;
}

.color-picker-style {
  cursor: pointer;
  z-index: 1003;
}
.fill-width {
  width: 100%;
}

.color-label {
  display: inline-block;
  width: 60px;
}

.color-type :deep(.ed-radio__input) {
  /* 颜色类型切换使用自定义色块样式，隐藏原生单选圆点。 */
  display: none;
}

.ed-radio {
  margin: 0 2px 0 0 !important;
  border: 1px solid transparent;
}

.ed-radio :deep(.ed-radio__label) {
  padding-left: 0;
  padding-top: 3px;
}

.ed-radio.is-checked {
  border: 1px solid #0a7be0;
}

.custom-color-style {
  height: 300px;
  overflow-y: auto;
  padding: 4px 12px;
  border: 1px solid #e6e6e6;
}

.shape-item {
  padding: 6px;
  border: none;
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.el-select-dropdown__item {
  padding: 0 20px;
}
span {
  font-size: 12px;
}

.color-type :deep(.el-radio__input) {
  display: none;
}
.ed-radio {
  margin: 0 2px 0 0 !important;
  border: 1px solid transparent;
}
.el-radio :deep(.el-radio__label) {
  padding-left: 0;
}

.ed-radio.is-checked {
  border: 1px solid #0a7be0;
}

.span-label {
  width: 300px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: inline-block;
  padding: 0 8px;
}

.custom-color-style {
  height: 300px;
  overflow-y: auto;
  padding: 4px 12px;
  border: 1px solid #e6e6e6;
}

.ed-divider__text {
  font-size: 8px;
  font-weight: 400;
  color: rgb(144, 147, 153);
}

.ed-form-item {
  flex-direction: column;
  margin-bottom: 8px;
}

:deep(.ed-form-item__label) {
  margin-bottom: 8px;
  color: #646a73;
}

.color-type-text {
  font-weight: 500;
  font-size: 12px;
  margin-bottom: 8px;
}
.ed-divider--horizontal {
  margin: 16px 0;
}
.m-divider {
  border-color: rgba(31, 35, 41, 0.15);
  margin: 0 0 8px;
}

.m-divider-dark {
  border-color: rgba(233, 236, 241, 0.15) !important;
}
.inner-collapse {
  :deep(.ed-collapse-item__header) {
    background-color: transparent !important;
  }
  :deep(.ed-collapse-item__header) {
    border: none !important;
  }
  :deep(.ed-collapse-item__wrap) {
    border: none;
  }

  &.inner-collapse_dark {
    :deep(.ed-form-item__label) {
      color: #a6a6a6;
    }
  }
}

.custom-color-pick {
  :deep(.ed-form-item__label) {
    justify-content: flex-start;
  }
  :deep(.ed-input__prefix) {
    max-width: 192px;
  }
  :deep(.custom-color-setting-btn) {
    margin-top: 28px;
  }
}

.custom-dark {
  :deep(.ed-input__wrapper) {
    padding: 0 16px;
  }
}

.alpha-setting {
  display: flex;
  max-width: 230px;

  .alpha-slider {
    padding: 4px 8px 0 8px;
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
  .alpha-input-number {
    :deep(input) {
      -webkit-appearance: none;
      -moz-appearance: textfield;

      &::-webkit-inner-spin-button,
      &::-webkit-outer-spin-button {
        -webkit-appearance: none;
      }
    }
  }
}
</style>
