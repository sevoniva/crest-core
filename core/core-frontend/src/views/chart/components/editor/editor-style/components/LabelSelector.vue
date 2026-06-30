<script lang="ts" setup>
import icon_info_outlined from '@/assets/svg/icon_info_outlined.svg'
import { computed, onMounted, PropType, reactive, ref, watch } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { COLOR_PANEL, DEFAULT_LABEL } from '@/views/chart/components/editor/util/chart'
import { ElFormItem, ElIcon, ElInput, ElSpace } from 'element-plus-secondary'
import {
  isEnLocal,
  formatterType,
  getUnitTypeList,
  initFormatCfgUnit,
  onChangeFormatCfgUnitLanguage
} from '@/views/chart/components/js/formatter'
import { defaultsDeep, cloneDeep, intersection, union, defaultTo, map, isEmpty } from 'lodash-es'
import { includesAny } from '../../util/StringUtils'
import { fieldType } from '@/utils/attr'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import Icon from '../../../../../../components/icon-custom/src/Icon.vue'
import { iconFieldMap } from '@/components/icon-group/field-list'

// 标签面板覆盖普通标签、系列标签、总计标签和自定义内容，文案统一从国际化资源读取。
const { t } = useI18n()

// 标签样式面板的可见项由当前图表类型的 propertyInner 决定，避免展示渲染器不支持的配置。
const props = defineProps({
  chart: {
    type: Object as PropType<ChartObj>,
    required: true
  },
  dimensionData: {
    type: Array<any>,
    required: false
  },
  quotaData: {
    type: Array<any>,
    required: false
  },
  themes: {
    type: String as PropType<EditorTheme>,
    default: 'dark'
  },
  allFields: {
    type: Array<any>,
    required: false
  },
  propertyInner: {
    type: Array<string>
  }
})
// 画布状态用于判断批量编辑模式和当前主题色，系列标签默认颜色会随主题初始化。
const dvMainStore = dvMainStoreWithOut()
/** 根据当前主题选择 Tooltip 的反色主题 */
const toolTip = computed(() => {
  return props.themes === 'dark' ? 'light' : 'dark'
})
/** 当前是否处于批量设置状态 */
const { batchOptStatus } = storeToRefs(dvMainStore)
/** 标签配置变化时重新初始化面板状态 */
watch(
  [() => props.chart.customAttr.label, () => props.chart.customAttr.label.show],
  () => {
    init()
  },
  { deep: false }
)
/** 当前图表参与标签配置的指标系列 */
const yAxis = computed(() => {
  if (props.chart.type.includes('chart-mix') || props.chart.type.includes('bidirectional-bar')) {
    return union(
      defaultTo(
        map(props.chart.yAxis, y => {
          return { ...y, axisType: 'yAxis', seriesId: y.id + '-yAxis' }
        }),
        []
      ),
      defaultTo(
        map(props.chart.yAxisExt, y => {
          return { ...y, axisType: 'yAxisExt', seriesId: y.id + '-yAxisExt' }
        }),
        []
      )
    )
  } else {
    return defaultTo(
      map(props.chart.yAxis, y => {
        return { ...y, axisType: 'yAxis', seriesId: y.id + '-yAxis' }
      }),
      []
    )
  }
})

/** 当前指标系列的稳定标识集合 */
const yAxisIds = computed(() => {
  return map(yAxis.value, y => y.seriesId)
})

/** 指标系列或图表类型变化时同步系列标签配置 */
watch(
  [() => yAxisIds.value, () => props.chart.type],
  () => {
    initSeriesLabel()
  },
  { deep: true }
)

/** 混合图使用 seriesId 作为系列键，普通图使用字段 id */
const computedIdKey = computed(() => {
  if (props.chart.type.includes('chart-mix')) {
    return 'seriesId'
  }
  return 'id'
})

/** 当前正在编辑的系列标签格式配置 */
const curSeriesFormatter = ref<Partial<SeriesFormatter>>({})
/** 当前是否允许编辑系列标签格式 */
const formatterEditable = computed(() => {
  return showProperty('seriesLabelFormatter') && yAxis.value?.length
})
/** 系列标签格式选择器引用 */
const formatterSelector = ref()
/** 初始化并补齐每个指标系列的标签配置 */
const initSeriesLabel = () => {
  // 批量设置阶段不展示系列级配置，避免把某张图的指标列表写入多张图。
  if (!showProperty('seriesLabelFormatter') || batchOptStatus.value) {
    return
  }
  const formatter = state.labelForm.seriesLabelFormatter

  const seriesAxisMap = formatter.reduce((pre, next) => {
    const id = next.seriesId ?? next.id
    // 混合图左右轴可能引用同一指标，seriesId 用于区分轴侧并保持配置稳定。
    pre[next[computedIdKey.value]] = { ...next, seriesId: id }
    return pre
  }, {})
  formatter.splice(0, formatter.length)
  if (!yAxis.value.length) {
    curSeriesFormatter.value = {}
    return
  }
  let initFlag = false
  const themeColor = dvMainStore.canvasStyleData.dashboard.themeColor
  const axisMap = yAxis.value.reduce((pre, next) => {
    const optionLabel: string = `${next.chartShowName ?? next.name}${
      next.summary !== '' ? '(' + t('chart.' + next.summary) + ')' : ''
    }${
      props.chart.type.includes('chart-mix')
        ? next.axisType === 'yAxis'
          ? `(${t('chart.left_axis')})`
          : `(${t('chart.right_axis')})`
        : ''
    }` as string
    const optionShowName: string = `${next.chartShowName ?? next.name}${
      next.summary !== '' ? '(' + t('chart.' + next.summary) + ')' : ''
    }${
      props.chart.type.includes('chart-mix')
        ? next.axisType === 'yAxis'
          ? `(${t('chart.left_axis')})`
          : `(${t('chart.right_axis')})`
        : ''
    }` as string
    let tmp = {
      ...next,
      optionLabel: optionLabel,
      optionShowName: optionShowName,
      show: true,
      color: themeColor === 'dark' ? '#fff' : '#000',
      fontSize: COMPUTED_DEFAULT_LABEL.value.fontSize,
      showExtremum: false,
      position: 'top'
    } as SeriesFormatter
    if (seriesAxisMap[next[computedIdKey.value]]) {
      initFormatCfgUnit(seriesAxisMap[next[computedIdKey.value]].formatterCfg)
      tmp = {
        ...tmp,
        formatterCfg: seriesAxisMap[next[computedIdKey.value]].formatterCfg,
        show: seriesAxisMap[next[computedIdKey.value]].show,
        color: seriesAxisMap[next[computedIdKey.value]].color,
        fontSize: seriesAxisMap[next[computedIdKey.value]].fontSize,
        showExtremum: seriesAxisMap[next[computedIdKey.value]].showExtremum,
        position: seriesAxisMap[next[computedIdKey.value]].position
      }
    } else {
      initFlag = true
    }
    formatter.push(tmp)
    next.seriesId = next.seriesId ?? next.id
    pre[next[computedIdKey.value]] = tmp
    return pre
  }, {})
  // 新增指标系列时立即同步默认配置，保证主题切换和首次渲染使用同一套颜色。
  if (initFlag) {
    changeLabelAttr('seriesLabelFormatter', false)
  }
  if (!curSeriesFormatter.value || !axisMap[curSeriesFormatter.value[computedIdKey.value]]) {
    curSeriesFormatter.value = axisMap[formatter[0][computedIdKey.value]]
    return
  }
  curSeriesFormatter.value = axisMap[curSeriesFormatter.value[computedIdKey.value]]
}

/** 径向图表标签位置选项 */
const labelPositionR = [
  { name: t('chart.inside'), value: 'inner' },
  { name: t('chart.outside'), value: 'outer' }
]
/** 水平标签位置选项 */
const labelPositionH = [
  { name: t('chart.text_pos_left'), value: 'left' },
  { name: t('chart.center'), value: 'middle' },
  { name: t('chart.text_pos_right'), value: 'right' }
]
/** 垂直标签位置选项 */
const labelPositionVList = [
  { name: t('chart.text_pos_top'), value: 'top' },
  { name: t('chart.center'), value: 'middle' },
  { name: t('chart.text_pos_bottom'), value: 'bottom' }
]

/** 当前图表可用的垂直标签位置选项 */
const labelPositionV = computed(() => {
  if (['line', 'area-stack', 'area'].includes(chartType.value)) {
    return labelPositionVList.filter(item => item.value !== 'middle')
  }
  return labelPositionVList
})

/** 当前图表类型 */
const chartType = computed(() => {
  const chart = JSON.parse(JSON.stringify(props.chart))
  return chart?.type
})

/** 标签字号候选项 */
const fontSizeList = computed(() => {
  const arr = []
  for (let i = 10; i <= 40; i = i + 2) {
    if (i === 10 && chartType.value === 'liquid') {
      continue
    }
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

/** 根据图表类型修正后的默认标签配置 */
const COMPUTED_DEFAULT_LABEL = computed(() => {
  if (chartType.value === 'liquid') {
    return {
      ...DEFAULT_LABEL,
      fontSize: fontSizeList.value[0].value
    }
  }
  return DEFAULT_LABEL
})

/** 标签表单的可编辑状态 */
const state = reactive<{ labelForm: DeepPartial<ChartLabelAttr> }>({
  labelForm: {
    quotaLabelFormatter: DEFAULT_LABEL.quotaLabelFormatter,
    seriesLabelFormatter: [],
    labelFormatter: DEFAULT_LABEL.labelFormatter,
    conversionTag: DEFAULT_LABEL.conversionTag,
    totalFormatter: DEFAULT_LABEL.totalFormatter,
    proportionSeriesFormatter: DEFAULT_LABEL.proportionSeriesFormatter
  }
})

/** 标签配置变更事件 */
const emit = defineEmits(['onLabelChange'])
/** 将单个标签属性变更同步给父级编辑器 */
const changeLabelAttr = (prop: string, render = true) => {
  emit('onLabelChange', { data: state.labelForm, render }, prop)
}

/** 切换格式化单位语言并同步对应标签属性 */
function changeLabelUnitLanguage(cfg: BaseFormatter, lang, prop: string, render = true) {
  onChangeFormatCfgUnitLanguage(cfg, lang)
  changeLabelAttr(prop, render)
}

/** 用当前图表配置初始化标签表单 */
const init = () => {
  const chart = JSON.parse(JSON.stringify(props.chart))
  if (chart.customAttr) {
    const customAttr = chart.customAttr
    if (customAttr.label) {
      configCompat(customAttr.label)
      // 深度合并默认值可以补齐新增字段，同时保留旧图表已经保存的标签样式。
      state.labelForm = defaultsDeep(customAttr.label, cloneDeep(COMPUTED_DEFAULT_LABEL.value))
      // 初始化格式化单位语言
      initFormatCfgUnit(state.labelForm.labelFormatter)
      initFormatCfgUnit(state.labelForm.quotaLabelFormatter)
      initFormatCfgUnit(state.labelForm.totalFormatter)
      if (chartType.value === 'liquid' && state.labelForm.fontSize < fontSizeList.value[0].value) {
        state.labelForm.fontSize = fontSizeList.value[0].value
      }
      initSeriesLabel()
      formatterSelector.value?.blur()
    }
    // 初始化标签位置
    initPosition()
  }
}
/** 兼容旧版本标签配置中的堆叠指标显示字段 */
const configCompat = (labelAttr: DeepPartial<ChartLabelAttr>) => {
  if (labelAttr.showStackQuota === undefined) {
    labelAttr.showStackQuota = labelAttr.show
  }
}
/** 判断标签内容选项是否是当前唯一启用项，避免全部内容被关闭 */
const checkLabelContent = contentProp => {
  if (['funnel', 'liquid'].includes(chartType.value)) {
    return false
  }
  const propIntersection = intersection(props.propertyInner, [
    'showDimension',
    'showQuota',
    'showProportion'
  ])
  if (!propIntersection?.includes(contentProp)) {
    return false
  }
  let trueCount = 0
  propIntersection?.forEach(prop => {
    state.labelForm?.[prop] && trueCount++
  })
  return trueCount === 1 && state.labelForm?.[contentProp]
}
/** 判断当前属性是否在面板可配置范围内 */
const showProperty = prop => {
  return props.propertyInner?.includes(prop)
}

/** 当前是否展示空配置提示 */
const showEmpty = computed(() => {
  return (
    props.propertyInner.length === 0 ||
    (batchOptStatus.value && showProperty('seriesLabelFormatter'))
  )
})
/** 当前是否展示系列标签格式配置 */
const showSeriesLabelFormatter = computed(() => {
  return !batchOptStatus.value && showProperty('seriesLabelFormatter')
})
/** 当前是否展示标签内容分隔区域 */
const showDivider = computed(() => {
  const DIVIDER_PROPS = ['labelFormatter', 'showDimension', 'showQuota', 'showProportion']
  return (
    includesAny(props.propertyInner, ...DIVIDER_PROPS) &&
    !isBarRangeTime.value &&
    !isGroupBar.value &&
    !isGauge.value
  )
})

/** 当前图表是否为时间范围条形图 */
const isBarRangeTime = computed<boolean>(() => {
  if (props.chart.type === 'bar-range') {
    const tempYAxis = props.chart.yAxis[0]
    const tempYAxisExt = props.chart.yAxisExt[0]
    if (
      (tempYAxis && tempYAxis.groupType === 'd') ||
      (tempYAxisExt && tempYAxisExt.groupType === 'd')
    ) {
      return true
    }
  }
  return false
})
/** 当前是否展示水平位置配置 */
const showPositionH = computed(() => {
  if (showProperty('hPosition')) {
    if (props.chart.type !== 'bidirectional-bar') {
      return true
    }
    return props.chart.customAttr.basicStyle.layout === 'horizontal'
  }
  return false
})
/** 当前是否展示垂直位置配置 */
const showPositionV = computed(() => {
  if (showProperty('vPosition')) {
    if (props.chart.type !== 'bidirectional-bar' && props.chart.type !== 'bar-group') {
      return true
    }
    return props.chart.customAttr.basicStyle.layout === 'vertical'
  }
  return false
})
/** 根据双向柱状图布局修正标签位置 */
function initBidirectionalBarPosition() {
  if (chartType.value === 'bidirectional-bar') {
    const layout = props.chart.customAttr.basicStyle.layout
    const oldPosition = state?.labelForm?.position
    if (state?.labelForm?.position === 'inner' || state?.labelForm?.position === 'outer') {
      state.labelForm.position = 'middle'
    }
    if (layout === 'horizontal') {
      if (state?.labelForm?.position === 'top') {
        state.labelForm.position = 'right'
      }
      if (state?.labelForm?.position === 'bottom') {
        state.labelForm.position = 'left'
      }
    }
    if (layout === 'vertical') {
      if (state?.labelForm?.position === 'left') {
        state.labelForm.position = 'bottom'
      }
      if (state?.labelForm?.position === 'right') {
        state.labelForm.position = 'top'
      }
    }
    if (oldPosition !== state.labelForm.position) {
      changeLabelAttr('position')
    }
  }
}

/** 根据图表类型和可配置属性修正标签位置 */
function initPosition() {
  if (chartType.value === 'bidirectional-bar') {
    initBidirectionalBarPosition()
  } else {
    const oldPosition = state?.labelForm?.position
    // 不同图表族支持的标签位置枚举不同，打开面板时要把历史值迁移到当前可用范围。
    if (showProperty('rPosition')) {
      if (state?.labelForm?.position !== 'inner') {
        state.labelForm.position = 'outer'
      }
    } else if (showProperty('hPosition')) {
      if (state?.labelForm?.position === 'top') {
        state.labelForm.position = 'right'
      } else if (state?.labelForm?.position === 'bottom') {
        state.labelForm.position = 'left'
      } else if (state?.labelForm?.position === 'inner' || state?.labelForm?.position === 'outer') {
        state.labelForm.position = 'middle'
      }
    } else if (showProperty('vPosition')) {
      if (state?.labelForm?.position === 'left') {
        state.labelForm.position = 'bottom'
      } else if (state?.labelForm?.position === 'right') {
        state.labelForm.position = 'top'
      } else if (state?.labelForm?.position === 'inner' || state?.labelForm?.position === 'outer') {
        state.labelForm.position = 'middle'
      }
    }
    if (oldPosition !== state.labelForm.position) {
      changeLabelAttr('position')
    }
  }
}

/** 双向柱状图布局变化时同步标签位置 */
watch(
  () => props.chart.customAttr.basicStyle.layout,
  () => {
    initBidirectionalBarPosition()
  },
  { deep: true }
)

/** 图表类型变化时同步标签位置 */
watch(chartType, () => {
  initPosition()
})

/** 可用于自定义标签内容的字段选项 */
const allFields = computed(() => {
  return defaultTo(props.allFields, []).map(item => ({
    key: item.engineFieldName,
    name: item.name,
    value: `${item.engineFieldName}@${item.name}`,
    disabled: false
  }))
})

/** 自定义标签内容输入框的默认占位文本 */
const defaultPlaceholder = computed(() => {
  if (state.labelForm.showFields && state.labelForm.showFields.length > 0) {
    return state.labelForm.showFields
      .filter(field => !isEmpty(field))
      ?.map(field => {
        return '${' + field.split('@')[1] + '}'
      })
      .join(',')
  }
  return ''
})
/** 字段列表变化时清理已失效的自定义标签字段 */
watch(
  () => allFields.value,
  () => {
    if (!showProperty('showFields')) {
      return
    }
    let result = []
    state.labelForm.showFields?.forEach(field => {
      if (allFields.value?.map(i => i.value).includes(field)) {
        result.push(field)
      }
    })
    state.labelForm.showFields = result
    if (allFields.value.length > 0) {
      changeLabelAttr('showFields')
    }
  }
)
/** 组件挂载后初始化标签配置 */
onMounted(() => {
  init()
})
/** 当前图表是否为分组柱状图 */
const isGroupBar = computed(() => {
  return props.chart.type === 'bar-group'
})
/** 转化率精度选项 */
const conversionPrecision = [
  { name: t('chart.reserve_zero'), value: 0 },
  { name: t('chart.reserve_one'), value: 1 },
  { name: t('chart.reserve_two'), value: 2 }
]
/** 当前图表是否允许完整显示标签 */
const noFullDisplay = computed(() => {
  return !['liquid', 'gauge', 'indicator'].includes(props.chart.type)
})
/** 当前图表是否为仪表盘 */
const isGauge = computed(() => {
  return props.chart.type === 'gauge'
})
/** 当前图表是否为进度条 */
const isProgressBar = computed(() => {
  return props.chart.type === 'progress-bar'
})
</script>

<template>
  <el-form
    ref="labelForm"
    :disabled="!state.labelForm.show"
    :model="state.labelForm"
    label-position="top"
    size="small"
  >
    <!-- 空态提示覆盖无可配置属性和批量设置禁用系列标签两种场景。 -->
    <el-row v-show="showEmpty" style="margin-bottom: 12px">
      {{ t('chart.no_other_configurable_properties') }}</el-row
    >
    <div>
      <!-- 顶部开关类配置只控制标签内容范围，不影响下面的字体和格式化配置。 -->
      <el-form-item v-if="noFullDisplay" class="form-item" :class="'form-item-' + themes">
        <el-checkbox
          size="small"
          :effect="themes"
          v-model="state.labelForm.fullDisplay"
          @change="changeLabelAttr('fullDisplay')"
          :label="t('chart.full_display')"
        />
      </el-form-item>
      <el-form-item
        v-if="showProperty('showStackQuota')"
        class="form-item"
        :class="'form-item-' + themes"
        style="display: inline-block; margin-right: 8px"
      >
        <el-checkbox
          size="small"
          :effect="themes"
          v-model="state.labelForm.showStackQuota"
          @change="changeLabelAttr('showStackQuota')"
          :label="t('chart.quota')"
        />
      </el-form-item>
      <el-form-item
        v-if="showProperty('showTotal')"
        class="form-item"
        :class="'form-item-' + themes"
        style="display: inline-block"
      >
        <el-checkbox
          size="small"
          :effect="themes"
          v-model="state.labelForm.showTotal"
          @change="changeLabelAttr('showTotal')"
          :label="t('chart.total_show')"
        />
      </el-form-item>
    </div>
    <div v-if="!isGroupBar && !isGauge">
      <!-- 普通图表的基础标签样式直接写入 labelForm，分组柱和仪表盘在各自分支单独维护。 -->
      <el-space>
        <el-form-item
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('color')"
          :label="t('chart.text')"
        >
          <el-color-picker
            :effect="themes"
            v-model="state.labelForm.color"
            class="color-picker-style"
            :predefine="COLOR_PANEL"
            @change="changeLabelAttr('color')"
            is-custom
            show-alpha
          />
        </el-form-item>
        <el-form-item
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('fontSize')"
        >
          <template #label>&nbsp;</template>
          <el-tooltip :content="t('chart.font_size')" :effect="toolTip" placement="top">
            <el-select
              size="small"
              style="width: 108px"
              :effect="themes"
              v-model.number="state.labelForm.fontSize"
              :placeholder="t('chart.text_fontsize')"
              @change="changeLabelAttr('fontSize')"
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
      </el-space>
    </div>
    <div v-if="showProperty('showFields') && !batchOptStatus">
      <!-- 自定义标签字段来自当前数据集字段，批量编辑时不开放以避免字段不一致。 -->
      <el-form-item :label="t('chart.label')" class="form-item" :class="'form-item-' + themes">
        <el-select
          size="small"
          :effect="themes"
          filterable
          multiple
          collapse-tags
          collapse-tags-tooltip
          v-model="state.labelForm.showFields"
          @change="changeLabelAttr('showFields')"
        >
          <el-option
            v-for="option in allFields"
            :key="option.key"
            :label="option.name"
            :value="option.value"
            :disabled="option.disabled"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="showProperty('customContent')" :class="'form-item-' + themes">
        <!-- 自定义内容使用字段占位符拼接，保存的是表达式模板而不是最终显示文本。 -->
        <template #label>
          <span class="data-area-label">
            <span style="margin-right: 4px">
              {{ t('chart.content_formatter') }}
            </span>
            <el-tooltip class="item" :effect="toolTip" placement="bottom">
              <template #content>
                <div>{{ t('chart.custom_label_content_tip') }}</div>
              </template>
              <el-icon class="hint-icon" :class="{ 'hint-icon--dark': themes === 'dark' }">
                <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
              </el-icon>
            </el-tooltip>
          </span>
        </template>
        <el-input
          style="font-size: smaller; font-weight: normal"
          v-model="state.labelForm.customContent"
          :effect="themes"
          type="textarea"
          :autosize="{ minRows: 4, maxRows: 4 }"
          :placeholder="defaultPlaceholder"
          @blur="changeLabelAttr('customContent')"
        />
      </el-form-item>
    </div>
    <el-form-item
      v-if="showProperty('rPosition')"
      :label="t('chart.label')"
      class="form-item"
      :class="'form-item-' + themes"
    >
      <!-- 径向、水平和垂直位置枚举互斥，初始化阶段会把历史值迁移到当前枚举内。 -->
      <el-select
        size="small"
        :effect="themes"
        v-model="state.labelForm.position"
        :placeholder="t('chart.label_position')"
        @change="changeLabelAttr('position')"
      >
        <el-option
          v-for="option in labelPositionR"
          :key="option.value"
          :label="option.name"
          :value="option.value"
        />
      </el-select>
    </el-form-item>
    <el-form-item
      v-if="showPositionH"
      :label="t('chart.label_position')"
      class="form-item"
      :class="'form-item-' + themes"
    >
      <el-select
        size="small"
        :effect="themes"
        v-model="state.labelForm.position"
        :placeholder="t('chart.label_position')"
        @change="changeLabelAttr('position')"
      >
        <el-option
          v-for="option in labelPositionH"
          :key="option.value"
          :label="option.name"
          :value="option.value"
        />
      </el-select>
    </el-form-item>
    <el-form-item v-if="showPositionV" class="form-item" :class="'form-item-' + themes">
      <template #label>
        {{ t('chart.label_position') }}
        <el-tooltip
          class="item"
          :effect="toolTip"
          placement="top"
          v-if="chart.type.includes('chart-mix')"
        >
          <template #content>
            <span>{{ t('chart.chart_mix_label_only_left') }}</span>
          </template>
          <span style="vertical-align: middle">
            <el-icon style="cursor: pointer">
              <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
            </el-icon>
          </span>
        </el-tooltip>
      </template>
      <el-select
        size="small"
        :effect="themes"
        v-model="state.labelForm.position"
        :placeholder="t('chart.label_position')"
        @change="changeLabelAttr('position')"
      >
        <el-option
          v-for="option in labelPositionV"
          :key="option.value"
          :label="option.name"
          :value="option.value"
        />
      </el-select>
    </el-form-item>
    <el-divider
      class="m-divider"
      :class="{ 'divider-dark': themes === 'dark' }"
      v-if="showDivider"
    />
    <template v-if="showProperty('labelFormatter') && !isBarRangeTime && !isGroupBar && !isGauge">
      <!-- 标签值格式化复用统一 formatter，时间范围、分组柱和仪表盘走各自渲染逻辑。 -->
      <el-form-item
        :label="$t('chart.value_formatter_type')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-select
          size="small"
          :effect="themes"
          v-model="state.labelForm.labelFormatter.type"
          @change="changeLabelAttr('labelFormatter.type')"
        >
          <el-option
            v-for="type in formatterType"
            :key="type.value"
            :label="$t('chart.' + type.name)"
            :value="type.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="state.labelForm.labelFormatter && state.labelForm.labelFormatter.type !== 'auto'"
        :label="$t('chart.value_formatter_decimal_count')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-input-number
          controls-position="right"
          :effect="themes"
          v-model="state.labelForm.labelFormatter.decimalCount"
          :precision="0"
          :min="0"
          :max="10"
          @change="changeLabelAttr('labelFormatter.decimalCount')"
        />
      </el-form-item>

      <template
        v-if="state.labelForm.labelFormatter && state.labelForm.labelFormatter.type !== 'percent'"
      >
        <el-row :gutter="8">
          <el-col :span="12" v-if="!isEnLocal">
            <el-form-item
              :label="$t('chart.value_formatter_unit_language')"
              class="form-item"
              :class="'form-item-' + themes"
            >
              <el-select
                size="small"
                :effect="themes"
                v-model="state.labelForm.labelFormatter.unitLanguage"
                :placeholder="$t('chart.pls_select_field')"
                @change="
                  v => changeLabelUnitLanguage(state.labelForm.labelFormatter, v, 'labelFormatter')
                "
              >
                <el-option :label="$t('chart.value_formatter_unit_language_ch')" value="ch" />
                <el-option :label="$t('chart.value_formatter_unit_language_en')" value="en" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="isEnLocal ? 24 : 12">
            <el-form-item
              :label="$t('chart.value_formatter_unit')"
              class="form-item"
              :class="'form-item-' + themes"
            >
              <el-select
                size="small"
                :effect="themes"
                v-model="state.labelForm.labelFormatter.unit"
                :placeholder="$t('chart.pls_select_field')"
                @change="changeLabelAttr('labelFormatter')"
              >
                <el-option
                  v-for="item in getUnitTypeList(state.labelForm.labelFormatter.unitLanguage)"
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
              :label="$t('chart.value_formatter_suffix')"
              class="form-item"
              :class="'form-item-' + themes"
            >
              <el-input
                :effect="themes"
                v-model="state.labelForm.labelFormatter.suffix"
                clearable
                :placeholder="$t('commons.input_content')"
                @change="changeLabelAttr('labelFormatter.suffix')"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <el-form-item class="form-item" :class="'form-item-' + themes">
        <el-checkbox
          size="small"
          :effect="themes"
          v-model="state.labelForm.labelFormatter.thousandSeparator"
          @change="changeLabelAttr('labelFormatter.thousandSeparator')"
          :label="t('chart.value_formatter_thousand_separator')"
        />
      </el-form-item>
    </template>
    <template v-if="false && showProperty('totalFormatter')">
      <el-divider class="m-divider" :class="{ 'divider-dark': themes === 'dark' }" />
      <div v-show="state.labelForm.showTotal">
        <el-space>
          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            v-if="showProperty('totalColor')"
            :label="t('chart.text')"
          >
            <el-color-picker
              :effect="themes"
              v-model="state.labelForm.totalColor"
              class="color-picker-style"
              :predefine="COLOR_PANEL"
              @change="changeLabelAttr('totalColor')"
              is-custom
            />
          </el-form-item>
          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            v-if="showProperty('totalFontSize')"
          >
            <template #label>&nbsp;</template>
            <el-tooltip :content="t('chart.font_size')" :effect="toolTip" placement="top">
              <el-select
                size="small"
                style="width: 108px"
                :effect="themes"
                v-model.number="state.labelForm.totalFontSize"
                :placeholder="t('chart.text_fontsize')"
                @change="changeLabelAttr('totalFontSize')"
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
        </el-space>
        <el-form-item
          :label="$t('chart.value_formatter_type')"
          class="form-item"
          :class="'form-item-' + themes"
        >
          <el-select
            size="small"
            :effect="themes"
            v-model="state.labelForm.totalFormatter.type"
            @change="changeLabelAttr('totalFormatter.type')"
          >
            <el-option
              v-for="type in formatterType"
              :key="type.value"
              :label="$t('chart.' + type.name)"
              :value="type.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="state.labelForm.totalFormatter && state.labelForm.totalFormatter.type !== 'auto'"
          :label="$t('chart.value_formatter_decimal_count')"
          class="form-item"
          :class="'form-item-' + themes"
        >
          <el-input-number
            controls-position="right"
            :effect="themes"
            v-model="state.labelForm.totalFormatter.decimalCount"
            :precision="0"
            :min="0"
            :max="10"
            @change="changeLabelAttr('totalFormatter.decimalCount')"
          />
        </el-form-item>

        <template
          v-if="state.labelForm.totalFormatter && state.labelForm.totalFormatter.type !== 'percent'"
        >
          <el-row :gutter="8">
            <el-col :span="12" v-if="!isEnLocal">
              <el-form-item
                :label="$t('chart.value_formatter_unit_language')"
                class="form-item"
                :class="'form-item-' + themes"
              >
                <el-select
                  size="small"
                  :effect="themes"
                  v-model="state.labelForm.totalFormatter.unitLanguage"
                  :placeholder="$t('chart.pls_select_field')"
                  @change="
                    v =>
                      changeLabelUnitLanguage(state.labelForm.totalFormatter, v, 'totalFormatter')
                  "
                >
                  <el-option :label="$t('chart.value_formatter_unit_language_ch')" value="ch" />
                  <el-option :label="$t('chart.value_formatter_unit_language_en')" value="en" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="isEnLocal ? 24 : 12">
              <el-form-item
                :label="$t('chart.value_formatter_unit')"
                class="form-item"
                :class="'form-item-' + themes"
              >
                <el-select
                  size="small"
                  :effect="themes"
                  v-model="state.labelForm.totalFormatter.unit"
                  :placeholder="$t('chart.pls_select_field')"
                  @change="changeLabelAttr('totalFormatter')"
                >
                  <el-option
                    v-for="item in getUnitTypeList(state.labelForm.totalFormatter.unitLanguage)"
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
                :label="$t('chart.value_formatter_suffix')"
                class="form-item"
                :class="'form-item-' + themes"
              >
                <el-input
                  :effect="themes"
                  v-model="state.labelForm.totalFormatter.suffix"
                  clearable
                  :placeholder="$t('commons.input_content')"
                  @change="changeLabelAttr('totalFormatter.suffix')"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-checkbox
            size="small"
            :effect="themes"
            v-model="state.labelForm.totalFormatter.thousandSeparator"
            @change="changeLabelAttr('totalFormatter.thousandSeparator')"
            :label="t('chart.value_formatter_thousand_separator')"
          />
        </el-form-item>
      </div>
    </template>

    <el-form-item
      class="form-item"
      :class="'form-item-' + themes"
      v-if="showProperty('showDimension')"
    >
      <el-checkbox
        v-model="state.labelForm.showDimension"
        :effect="themes"
        :disabled="checkLabelContent('showDimension')"
        size="small"
        label="dimension"
        @change="changeLabelAttr('showDimension')"
      >
        {{ t('chart.dimension') }}
      </el-checkbox>
    </el-form-item>
    <template v-if="showProperty('showQuota')">
      <el-form-item class="form-item form-item-checkbox" :class="'form-item-' + themes">
        <el-checkbox
          v-model="state.labelForm.showQuota"
          :effect="themes"
          :disabled="isProgressBar ? false : checkLabelContent('showQuota')"
          size="small"
          label="quota"
          @change="changeLabelAttr('showQuota')"
        >
          {{ t('chart.quota') }}
        </el-checkbox>
      </el-form-item>

      <div style="padding-left: 22px">
        <!-- 指标标签格式只在指标内容启用时生效，但配置值会保留以支持再次开启。 -->
        <el-form-item
          :label="t('chart.value_formatter_type')"
          class="form-item"
          :class="'form-item-' + themes"
        >
          <el-select
            size="small"
            :disabled="!state.labelForm.showQuota"
            style="width: 100%"
            :effect="themes"
            v-model="state.labelForm.quotaLabelFormatter.type"
            @change="changeLabelAttr('quotaLabelFormatter.type')"
          >
            <el-option
              v-for="type in formatterType"
              :key="type.value"
              :label="t('chart.' + type.name)"
              :value="type.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="
            state.labelForm.quotaLabelFormatter &&
            state.labelForm.quotaLabelFormatter.type !== 'auto'
          "
          :label="t('chart.value_formatter_decimal_count')"
          class="form-item"
          :class="'form-item-' + themes"
        >
          <el-input-number
            controls-position="right"
            :disabled="!state.labelForm.showQuota"
            style="width: 100%"
            :effect="themes"
            v-model="state.labelForm.quotaLabelFormatter.decimalCount"
            :precision="0"
            :min="0"
            :max="10"
            size="small"
            @change="changeLabelAttr('quotaLabelFormatter.decimalCount')"
          />
        </el-form-item>

        <template
          v-if="
            state.labelForm.quotaLabelFormatter &&
            state.labelForm.quotaLabelFormatter.type !== 'percent'
          "
        >
          <el-row :gutter="8">
            <el-col :span="12" v-if="!isEnLocal">
              <el-form-item
                :label="$t('chart.value_formatter_unit_language')"
                class="form-item"
                :class="'form-item-' + themes"
              >
                <el-select
                  :disabled="!state.labelForm.showQuota"
                  size="small"
                  :effect="themes"
                  v-model="state.labelForm.quotaLabelFormatter.unitLanguage"
                  :placeholder="$t('chart.pls_select_field')"
                  @change="
                    v =>
                      changeLabelUnitLanguage(
                        state.labelForm.quotaLabelFormatter,
                        v,
                        'quotaLabelFormatter'
                      )
                  "
                >
                  <el-option :label="$t('chart.value_formatter_unit_language_ch')" value="ch" />
                  <el-option :label="$t('chart.value_formatter_unit_language_en')" value="en" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="isEnLocal ? 24 : 12">
              <el-form-item
                :label="t('chart.value_formatter_unit')"
                class="form-item"
                :class="'form-item-' + themes"
              >
                <el-select
                  :disabled="!state.labelForm.showQuota"
                  :effect="themes"
                  v-model="state.labelForm.quotaLabelFormatter.unit"
                  :placeholder="t('chart.pls_select_field')"
                  size="small"
                  @change="changeLabelAttr('quotaLabelFormatter')"
                >
                  <el-option
                    v-for="item in getUnitTypeList(
                      state.labelForm.quotaLabelFormatter.unitLanguage
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
                :label="t('chart.value_formatter_suffix')"
                class="form-item"
                :class="'form-item-' + themes"
              >
                <el-input
                  :disabled="!state.labelForm.showQuota"
                  :effect="themes"
                  v-model="state.labelForm.quotaLabelFormatter.suffix"
                  size="small"
                  clearable
                  :placeholder="t('commons.input_content')"
                  @change="changeLabelAttr('quotaLabelFormatter.suffix')"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-checkbox
            :disabled="!state.labelForm.showQuota"
            size="small"
            :effect="themes"
            v-model="state.labelForm.quotaLabelFormatter.thousandSeparator"
            @change="changeLabelAttr('quotaLabelFormatter.thousandSeparator')"
            :label="t('chart.value_formatter_thousand_separator')"
          />
        </el-form-item>
      </div>
    </template>
    <template v-if="showProperty('showProportion')">
      <el-form-item class="form-item form-item-checkbox" :class="'form-item-' + themes">
        <el-checkbox
          v-model="state.labelForm.showProportion"
          :effect="themes"
          :disabled="isProgressBar ? false : checkLabelContent('showProportion')"
          size="small"
          label="proportion"
          @change="changeLabelAttr('showProportion')"
        >
          {{ isProgressBar ? t('chart.value_formatter_percent') : t('chart.proportion') }}
        </el-checkbox>
      </el-form-item>
      <div style="padding-left: 22px">
        <!-- 占比标签只暴露精度设置，具体百分号和计算口径由图表渲染层统一处理。 -->
        <el-form-item
          :label="t('chart.label_reserve_decimal_count')"
          class="form-item"
          :class="'form-item-' + themes"
        >
          <el-select
            size="small"
            :effect="themes"
            :disabled="!state.labelForm.showProportion"
            v-model="state.labelForm.reserveDecimalCount"
            @change="changeLabelAttr('reserveDecimalCount')"
          >
            <el-option :label="t('chart.reserve_zero')" :value="0" />
            <el-option :label="t('chart.reserve_one')" :value="1" />
            <el-option :label="t('chart.reserve_two')" :value="2" />
          </el-select>
        </el-form-item>
      </div>
    </template>
    <el-form-item
      v-if="showProperty('reserveDecimalCount')"
      :label="t('chart.label_reserve_decimal_count')"
      class="form-item"
      :class="'form-item-' + themes"
    >
      <el-select
        size="small"
        :effect="themes"
        v-model="state.labelForm.reserveDecimalCount"
        @change="changeLabelAttr('reserveDecimalCount')"
      >
        <el-option :label="t('chart.reserve_zero')" :value="0" />
        <el-option :label="t('chart.reserve_one')" :value="1" />
        <el-option :label="t('chart.reserve_two')" :value="2" />
      </el-select>
    </el-form-item>
    <div v-if="showSeriesLabelFormatter">
      <!-- 系列标签按指标逐项配置，混合图通过稳定 seriesId 区分左右轴同名指标。 -->
      <el-form-item class="form-item" :class="'form-item-' + themes">
        <el-select
          v-model="curSeriesFormatter"
          :effect="themes"
          :teleported="false"
          :disabled="!formatterEditable"
          ref="formatterSelector"
          :value-key="computedIdKey"
          class="series-select"
          size="small"
        >
          <!-- 选中项前缀展示字段类型图标，帮助区分同名指标在不同轴侧的标签配置。 -->
          <template #prefix>
            <el-icon v-if="curSeriesFormatter[computedIdKey]" style="font-size: 14px">
              <Icon :className="`field-icon-${fieldType[curSeriesFormatter.fieldType]}`"
                ><component
                  :class="`field-icon-${fieldType[curSeriesFormatter.fieldType]}`"
                  class="svg-icon"
                  :is="iconFieldMap[fieldType[curSeriesFormatter.fieldType]]"
                ></component
              ></Icon>
            </el-icon>
          </template>
          <template v-for="item in state.labelForm.seriesLabelFormatter" :key="item[computedIdKey]">
            <el-option class="series-select-option" :value="item" :label="item.optionLabel">
              <el-icon style="margin-right: 8px">
                <Icon :className="`field-icon-${fieldType[item.fieldType]}`"
                  ><component
                    :class="`field-icon-${fieldType[item.fieldType]}`"
                    class="svg-icon"
                    :is="iconFieldMap[fieldType[item.fieldType]]"
                  ></component
                ></Icon>
              </el-icon>
              {{ item.optionShowName }}
            </el-option>
          </template>
        </el-select>
      </el-form-item>
      <template v-if="curSeriesFormatter?.id">
        <el-form-item class="form-item form-item-checkbox" :class="'form-item-' + themes">
          <el-checkbox
            :effect="themes"
            size="small"
            @change="changeLabelAttr('seriesLabelFormatter')"
            v-model="curSeriesFormatter.show"
            label="quota"
          >
            {{ t('chart.show_label') }}
          </el-checkbox>
        </el-form-item>

        <div style="padding-left: 22px">
          <!-- 当前系列的样式和格式化写回 seriesLabelFormatter 数组，依赖 value-key 保持选中项稳定。 -->
          <el-form-item
            v-if="showProperty('seriesLabelVPosition')"
            class="form-item"
            :class="'form-item-' + themes"
            :label="t('chart.position')"
          >
            <el-select
              :disabled="!curSeriesFormatter.show"
              size="small"
              :effect="themes"
              v-model="curSeriesFormatter.position"
              :placeholder="t('chart.label_position')"
              @change="changeLabelAttr('seriesLabelFormatter')"
            >
              <el-option
                v-for="option in labelPositionV"
                :key="option.value"
                :label="option.name"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-space>
            <el-form-item class="form-item" :class="'form-item-' + themes" :label="t('chart.text')">
              <el-color-picker
                :disabled="!curSeriesFormatter.show"
                style="width: 100%"
                :effect="themes"
                v-model="curSeriesFormatter.color"
                class="color-picker-style"
                :predefine="COLOR_PANEL"
                @change="changeLabelAttr('seriesLabelFormatter')"
                is-custom
              />
            </el-form-item>
            <el-form-item class="form-item" :class="'form-item-' + themes">
              <template #label>&nbsp;</template>
              <el-tooltip :content="t('chart.font_size')" :effect="toolTip" placement="top">
                <el-select
                  size="small"
                  :disabled="!curSeriesFormatter.show"
                  style="width: 108px"
                  :effect="themes"
                  v-model.number="curSeriesFormatter.fontSize"
                  :placeholder="t('chart.text_fontsize')"
                  @change="changeLabelAttr('seriesLabelFormatter')"
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
          </el-space>

          <el-form-item
            :label="t('chart.value_formatter_type')"
            class="form-item"
            :class="'form-item-' + themes"
            v-if="curSeriesFormatter.formatterCfg"
          >
            <el-select
              size="small"
              :disabled="!curSeriesFormatter.show"
              style="width: 100%"
              :effect="props.themes"
              v-model="curSeriesFormatter.formatterCfg.type"
              @change="changeLabelAttr('seriesLabelFormatter')"
            >
              <el-option
                v-for="type in formatterType"
                :key="type.value"
                :label="t('chart.' + type.name)"
                :value="type.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            v-if="
              curSeriesFormatter.formatterCfg && curSeriesFormatter.formatterCfg.type !== 'auto'
            "
            :label="t('chart.value_formatter_decimal_count')"
            class="form-item"
            :class="'form-item-' + themes"
          >
            <el-input-number
              controls-position="right"
              :disabled="!curSeriesFormatter.show"
              style="width: 100%"
              :effect="props.themes"
              v-model="curSeriesFormatter.formatterCfg.decimalCount"
              :precision="0"
              :min="0"
              :max="10"
              size="small"
              @change="changeLabelAttr('seriesLabelFormatter')"
            />
          </el-form-item>

          <template
            v-if="
              curSeriesFormatter.show &&
              curSeriesFormatter.formatterCfg &&
              curSeriesFormatter.formatterCfg.type !== 'percent'
            "
          >
            <el-row :gutter="8">
              <el-col :span="12" v-if="!isEnLocal">
                <el-form-item
                  :label="$t('chart.value_formatter_unit_language')"
                  class="form-item"
                  :class="'form-item-' + themes"
                >
                  <el-select
                    :disabled="!curSeriesFormatter.show"
                    size="small"
                    :effect="themes"
                    v-model="curSeriesFormatter.formatterCfg.unitLanguage"
                    :placeholder="$t('chart.pls_select_field')"
                    @change="
                      v =>
                        changeLabelUnitLanguage(
                          curSeriesFormatter.formatterCfg,
                          v,
                          'seriesLabelFormatter'
                        )
                    "
                  >
                    <el-option :label="$t('chart.value_formatter_unit_language_ch')" value="ch" />
                    <el-option :label="$t('chart.value_formatter_unit_language_en')" value="en" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="isEnLocal ? 24 : 12">
                <el-form-item
                  :label="t('chart.value_formatter_unit')"
                  class="form-item"
                  :class="'form-item-' + themes"
                >
                  <el-select
                    :disabled="!curSeriesFormatter.show"
                    :effect="props.themes"
                    v-model="curSeriesFormatter.formatterCfg.unit"
                    :placeholder="t('chart.pls_select_field')"
                    size="small"
                    @change="changeLabelAttr('seriesLabelFormatter')"
                  >
                    <el-option
                      v-for="item in getUnitTypeList(curSeriesFormatter.formatterCfg.unitLanguage)"
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
                  :label="t('chart.value_formatter_suffix')"
                  class="form-item"
                  :class="'form-item-' + themes"
                >
                  <el-input
                    :disabled="!curSeriesFormatter.show"
                    :effect="props.themes"
                    v-model="curSeriesFormatter.formatterCfg.suffix"
                    size="small"
                    maxlength="30"
                    clearable
                    :placeholder="t('commons.input_content')"
                    @change="changeLabelAttr('seriesLabelFormatter')"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </template>

          <el-form-item class="form-item" :class="'form-item-' + themes">
            <el-checkbox
              :disabled="!curSeriesFormatter.show"
              size="small"
              :effect="props.themes"
              v-model="curSeriesFormatter.formatterCfg.thousandSeparator"
              @change="changeLabelAttr('seriesLabelFormatter')"
              :label="t('chart.value_formatter_thousand_separator')"
            />
          </el-form-item>
        </div>
        <el-form-item
          class="form-item form-item-checkbox"
          :class="'form-item-' + themes"
          v-if="showProperty('showExtremum')"
        >
          <el-checkbox
            :effect="themes"
            size="small"
            @change="changeLabelAttr('seriesLabelFormatter')"
            v-model="curSeriesFormatter.showExtremum"
            label="quota"
          >
            <!-- 系列极值跟随当前指标保存，避免多指标图表只能统一开关。 -->
            {{ t('chart.show_extremum') }}
          </el-checkbox>
        </el-form-item>
      </template>
    </div>
    <template v-if="isGroupBar">
      <!-- 分组柱状图的子项标签使用独立开关，避免影响普通柱状图标签配置。 -->
      <el-form-item class="form-item form-item-checkbox" :class="'form-item-' + themes">
        <el-checkbox
          :effect="themes"
          size="small"
          @change="changeLabelAttr('childrenShow')"
          v-model="state.labelForm.childrenShow"
          label="quota"
        >
          {{ t('chart.show_label') }}
        </el-checkbox>
      </el-form-item>
      <div style="padding-left: 22px">
        <el-space>
          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            v-if="showProperty('color')"
            :label="t('chart.text')"
          >
            <el-color-picker
              :disabled="!state.labelForm.childrenShow"
              :effect="themes"
              v-model="state.labelForm.color"
              class="color-picker-style"
              :predefine="COLOR_PANEL"
              @change="changeLabelAttr('color')"
              is-custom
            />
          </el-form-item>
          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            v-if="showProperty('fontSize')"
          >
            <template #label>&nbsp;</template>
            <el-tooltip :content="t('chart.font_size')" :effect="toolTip" placement="top">
              <el-select
                :disabled="!state.labelForm.childrenShow"
                size="small"
                style="width: 108px"
                :effect="themes"
                v-model.number="state.labelForm.fontSize"
                :placeholder="t('chart.text_fontsize')"
                @change="changeLabelAttr('fontSize')"
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
        </el-space>
        <el-form-item
          v-if="showProperty('vPosition')"
          class="form-item"
          :class="'form-item-' + themes"
        >
          <template #label>
            {{ t('chart.label_position') }}
            <el-tooltip
              class="item"
              :effect="toolTip"
              placement="top"
              v-if="chart.type.includes('chart-mix')"
            >
              <template #content>
                <span>{{ t('chart.chart_mix_label_only_left') }}</span>
              </template>
              <span style="vertical-align: middle">
                <el-icon style="cursor: pointer">
                  <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
                </el-icon>
              </span>
            </el-tooltip>
          </template>
          <el-select
            :disabled="!state.labelForm.childrenShow"
            size="small"
            :effect="themes"
            v-model="state.labelForm.position"
            :placeholder="t('chart.label_position')"
            @change="changeLabelAttr('position')"
          >
            <el-option
              v-for="option in labelPositionV"
              :key="option.value"
              :label="option.name"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          :label="$t('chart.value_formatter_type')"
          class="form-item"
          :class="'form-item-' + themes"
        >
          <!-- 分组柱子项标签复用普通标签格式化结构，但受 childrenShow 独立开关控制。 -->
          <el-select
            :disabled="!state.labelForm.childrenShow"
            size="small"
            :effect="themes"
            v-model="state.labelForm.labelFormatter.type"
            @change="changeLabelAttr('labelFormatter.type')"
          >
            <el-option
              v-for="type in formatterType"
              :key="type.value"
              :label="$t('chart.' + type.name)"
              :value="type.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="state.labelForm.labelFormatter && state.labelForm.labelFormatter.type !== 'auto'"
          :label="$t('chart.value_formatter_decimal_count')"
          class="form-item"
          :class="'form-item-' + themes"
        >
          <el-input-number
            :disabled="!state.labelForm.childrenShow"
            controls-position="right"
            :effect="themes"
            v-model="state.labelForm.labelFormatter.decimalCount"
            :precision="0"
            :min="0"
            :max="10"
            @change="changeLabelAttr('labelFormatter.decimalCount')"
          />
        </el-form-item>

        <template
          v-if="state.labelForm.labelFormatter && state.labelForm.labelFormatter.type !== 'percent'"
        >
          <el-row :gutter="8">
            <el-col :span="12" v-if="!isEnLocal">
              <el-form-item
                :label="$t('chart.value_formatter_unit_language')"
                class="form-item"
                :class="'form-item-' + themes"
              >
                <el-select
                  :disabled="!state.labelForm.childrenShow"
                  size="small"
                  :effect="themes"
                  v-model="state.labelForm.labelFormatter.unitLanguage"
                  :placeholder="$t('chart.pls_select_field')"
                  @change="
                    v =>
                      changeLabelUnitLanguage(state.labelForm.labelFormatter, v, 'labelFormatter')
                  "
                >
                  <el-option :label="$t('chart.value_formatter_unit_language_ch')" value="ch" />
                  <el-option :label="$t('chart.value_formatter_unit_language_en')" value="en" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="isEnLocal ? 24 : 12">
              <el-form-item
                :label="$t('chart.value_formatter_unit')"
                class="form-item"
                :class="'form-item-' + themes"
              >
                <el-select
                  :disabled="!state.labelForm.childrenShow"
                  size="small"
                  :effect="themes"
                  v-model="state.labelForm.labelFormatter.unit"
                  :placeholder="$t('chart.pls_select_field')"
                  @change="changeLabelAttr('labelFormatter')"
                >
                  <el-option
                    v-for="item in getUnitTypeList(state.labelForm.labelFormatter.unitLanguage)"
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
                :label="$t('chart.value_formatter_suffix')"
                class="form-item"
                :class="'form-item-' + themes"
              >
                <el-input
                  :disabled="!state.labelForm.childrenShow"
                  :effect="themes"
                  v-model="state.labelForm.labelFormatter.suffix"
                  clearable
                  :placeholder="$t('commons.input_content')"
                  @change="changeLabelAttr('labelFormatter.suffix')"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-checkbox
            size="small"
            :effect="themes"
            v-model="state.labelForm.labelFormatter.thousandSeparator"
            @change="changeLabelAttr('labelFormatter.thousandSeparator')"
            :label="t('chart.value_formatter_thousand_separator')"
            :disabled="!state.labelForm.childrenShow"
          />
        </el-form-item>
      </div>
    </template>
    <el-form-item
      class="form-item form-item-checkbox"
      :class="'form-item-' + themes"
      v-if="showProperty('showExtremum') && !showSeriesLabelFormatter"
    >
      <!-- 无系列级配置的图表使用全局极值开关，避免同一能力出现两套保存入口。 -->
      <el-checkbox
        :effect="themes"
        size="small"
        @change="changeLabelAttr('showExtremum')"
        v-model="state.labelForm.showExtremum"
        label="quota"
      >
        {{ t('chart.show_extremum') }}
      </el-checkbox>
    </el-form-item>
    <el-form-item class="form-item" :class="'form-item-' + themes" v-show="showProperty('showGap')">
      <!-- 间隔值标签只在支持差值展示的图表类型中渲染，配置项按属性列表显隐。 -->
      <el-checkbox
        :effect="themes"
        size="small"
        @change="changeLabelAttr('showGap')"
        v-model="state.labelForm.showGap"
      >
        {{ t('chart.show_gap') }}
      </el-checkbox>
    </el-form-item>
    <el-form-item
      class="form-item"
      :class="'form-item-' + themes"
      v-if="showProperty('conversionTag')"
    >
      <el-checkbox
        :effect="themes"
        size="small"
        @change="changeLabelAttr('conversionTag')"
        v-model="state.labelForm.conversionTag.show"
      >
        {{ t('chart.conversion_rate') }}
      </el-checkbox>
    </el-form-item>
    <div style="padding-left: 22px" v-if="showProperty('conversionTag')">
      <!-- 转化率标签与普通占比标签独立保存，名称和精度需要随漏斗类指标口径调整。 -->
      <el-row :gutter="8">
        <el-col :span="12">
          <el-form-item
            :label="t('chart.label_reserve_decimal_count')"
            class="form-item"
            :class="'form-item-' + themes"
          >
            <el-select
              size="small"
              style="width: 108px"
              :effect="themes"
              :disabled="!state.labelForm.conversionTag.show"
              v-model.number="state.labelForm.conversionTag.precision"
              @change="changeLabelAttr('conversionTag')"
            >
              <el-option
                v-for="option in conversionPrecision"
                :key="option.value"
                :label="option.name"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item
            :label="t('chart.conversion_rate') + t('chart.name')"
            class="form-item"
            :class="'form-item-' + themes"
          >
            <el-input
              :effect="themes"
              v-model="state.labelForm.conversionTag.text"
              size="small"
              maxlength="100"
              :disabled="!state.labelForm.conversionTag.show"
              @blur="changeLabelAttr('conversionTag')"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </div>
    <template v-if="isGauge">
      <!-- 仪表盘同时包含指标值和占比值，两类标签分别维护格式与样式。 -->
      <el-form-item class="form-item form-item-checkbox" :class="'form-item-' + themes">
        <el-checkbox
          :effect="themes"
          size="small"
          @change="changeLabelAttr('childrenShow')"
          v-model="state.labelForm.childrenShow"
          label="quota"
        >
          {{ t('chart.quota') }}
        </el-checkbox>
      </el-form-item>
      <div style="padding-left: 22px">
        <el-space>
          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            v-if="showProperty('color')"
            :label="t('chart.text')"
          >
            <el-color-picker
              :disabled="!state.labelForm.childrenShow"
              :effect="themes"
              v-model="state.labelForm.color"
              class="color-picker-style"
              :predefine="COLOR_PANEL"
              @change="changeLabelAttr('color')"
              is-custom
            />
          </el-form-item>
          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            v-if="showProperty('fontSize')"
          >
            <template #label>&nbsp;</template>
            <el-tooltip :content="t('chart.font_size')" :effect="toolTip" placement="top">
              <el-select
                :disabled="!state.labelForm.childrenShow"
                size="small"
                style="width: 108px"
                :effect="themes"
                v-model.number="state.labelForm.fontSize"
                :placeholder="t('chart.text_fontsize')"
                @change="changeLabelAttr('fontSize')"
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
        </el-space>
        <el-form-item
          :label="$t('chart.value_formatter_type')"
          class="form-item"
          :class="'form-item-' + themes"
        >
          <el-select
            :disabled="!state.labelForm.childrenShow"
            size="small"
            :effect="themes"
            v-model="state.labelForm.labelFormatter.type"
            @change="changeLabelAttr('labelFormatter.type')"
          >
            <el-option
              v-for="type in formatterType"
              :key="type.value"
              :label="$t('chart.' + type.name)"
              :value="type.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="state.labelForm.labelFormatter && state.labelForm.labelFormatter.type !== 'auto'"
          :label="$t('chart.value_formatter_decimal_count')"
          class="form-item"
          :class="'form-item-' + themes"
        >
          <el-input-number
            :disabled="!state.labelForm.childrenShow"
            controls-position="right"
            :effect="themes"
            v-model="state.labelForm.labelFormatter.decimalCount"
            :precision="0"
            :min="0"
            :max="10"
            @change="changeLabelAttr('labelFormatter.decimalCount')"
          />
        </el-form-item>

        <template
          v-if="state.labelForm.labelFormatter && state.labelForm.labelFormatter.type !== 'percent'"
        >
          <el-row :gutter="8">
            <el-col :span="12" v-if="!isEnLocal">
              <el-form-item
                :label="$t('chart.value_formatter_unit_language')"
                class="form-item"
                :class="'form-item-' + themes"
              >
                <el-select
                  :disabled="!state.labelForm.childrenShow"
                  size="small"
                  :effect="themes"
                  v-model="state.labelForm.labelFormatter.unitLanguage"
                  :placeholder="$t('chart.pls_select_field')"
                  @change="
                    v =>
                      changeLabelUnitLanguage(state.labelForm.labelFormatter, v, 'labelFormatter')
                  "
                >
                  <el-option :label="$t('chart.value_formatter_unit_language_ch')" value="ch" />
                  <el-option :label="$t('chart.value_formatter_unit_language_en')" value="en" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="isEnLocal ? 24 : 12">
              <el-form-item
                :label="$t('chart.value_formatter_unit')"
                class="form-item"
                :class="'form-item-' + themes"
              >
                <el-select
                  :disabled="!state.labelForm.childrenShow"
                  size="small"
                  :effect="themes"
                  v-model="state.labelForm.labelFormatter.unit"
                  :placeholder="$t('chart.pls_select_field')"
                  @change="changeLabelAttr('labelFormatter')"
                >
                  <el-option
                    v-for="item in getUnitTypeList(state.labelForm.labelFormatter.unitLanguage)"
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
                :label="$t('chart.value_formatter_suffix')"
                class="form-item"
                :class="'form-item-' + themes"
              >
                <el-input
                  :disabled="!state.labelForm.childrenShow"
                  :effect="themes"
                  v-model="state.labelForm.labelFormatter.suffix"
                  clearable
                  :placeholder="$t('commons.input_content')"
                  @change="changeLabelAttr('labelFormatter.suffix')"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-checkbox
            size="small"
            :effect="themes"
            v-model="state.labelForm.labelFormatter.thousandSeparator"
            @change="changeLabelAttr('labelFormatter.thousandSeparator')"
            :label="t('chart.value_formatter_thousand_separator')"
            :disabled="!state.labelForm.childrenShow"
          />
        </el-form-item>
      </div>
      <el-form-item class="form-item form-item-checkbox" :class="'form-item-' + themes">
        <!-- 仪表盘占比文本使用 proportionSeriesFormatter，不能复用上方指标值格式。 -->
        <el-checkbox
          :effect="themes"
          size="small"
          @change="changeLabelAttr('proportionSeriesFormatter')"
          v-model="state.labelForm.proportionSeriesFormatter.show"
          label="quota"
        >
          {{ t('chart.proportion') }}
        </el-checkbox>
      </el-form-item>
      <div style="padding-left: 22px">
        <el-space>
          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            v-if="showProperty('color')"
            :label="t('chart.text')"
          >
            <el-color-picker
              :disabled="!state.labelForm.proportionSeriesFormatter.show"
              :effect="themes"
              v-model="state.labelForm.proportionSeriesFormatter.color"
              class="color-picker-style"
              :predefine="COLOR_PANEL"
              @change="changeLabelAttr('proportionSeriesFormatter.color')"
              is-custom
            />
          </el-form-item>
          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            v-if="showProperty('fontSize')"
          >
            <template #label>&nbsp;</template>
            <el-tooltip :content="t('chart.font_size')" :effect="toolTip" placement="top">
              <el-select
                :disabled="!state.labelForm.proportionSeriesFormatter.show"
                size="small"
                style="width: 108px"
                :effect="themes"
                v-model.number="state.labelForm.proportionSeriesFormatter.fontSize"
                :placeholder="t('chart.text_fontsize')"
                @change="changeLabelAttr('proportionSeriesFormatter.fontSize')"
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
        </el-space>
        <el-form-item
          :label="t('chart.label_reserve_decimal_count')"
          class="form-item"
          :class="'form-item-' + themes"
        >
          <el-select
            size="small"
            :effect="themes"
            :disabled="!state.labelForm.proportionSeriesFormatter.show"
            v-model="state.labelForm.proportionSeriesFormatter.formatterCfg.decimalCount"
            @change="changeLabelAttr('proportionSeriesFormatter')"
          >
            <el-option :label="t('chart.reserve_zero')" :value="0" />
            <el-option :label="t('chart.reserve_one')" :value="1" />
            <el-option :label="t('chart.reserve_two')" :value="2" />
          </el-select>
        </el-form-item>
      </div>
    </template>
  </el-form>
</template>

<style lang="less" scoped>
.form-item-checkbox {
  margin-bottom: 8px !important;
}

.series-select {
  :deep(.ed-select__prefix::after) {
    display: none;
  }

  :deep(.ed-select__prefix--dark) {
    padding-right: unset;
    border-right: unset;
  }
}

.series-select-option {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 0 11px;
}

.m-divider {
  margin: 0 0 16px;
  border-color: rgba(31, 35, 41, 0.15);

  &.divider-dark {
    border-color: rgba(255, 255, 255, 0.15);
  }
}
.data-area-label {
  text-align: left;
  position: relative;
  width: 100%;
  display: flex;
  flex-direction: row;
  align-items: center;
}
</style>
