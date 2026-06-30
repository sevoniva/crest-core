<script lang="tsx" setup>
import { onMounted, PropType, reactive, ref, watch } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import {
  DEFAULT_YAXIS_EXT_STYLE,
  DEFAULT_YAXIS_STYLE
} from '@/views/chart/components/editor/util/chart'
import { cloneDeep } from 'lodash-es'
import DualYAxisSelectorInner from './DualYAxisSelectorInner.vue'

const { t } = useI18n()

// 定义双轴样式面板接收的图表和主题参数
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

// 保存当前激活的轴配置页签
const activeName = ref('left')

// 保存主轴和副轴的本地样式表单
const state = reactive<any>({
  axisForm: JSON.parse(JSON.stringify(DEFAULT_YAXIS_STYLE)),
  subAxisForm: JSON.parse(JSON.stringify(DEFAULT_YAXIS_EXT_STYLE))
})

// 声明主轴和副轴样式变更事件
const emit = defineEmits(['onChangeYAxisForm', 'onChangeYAxisExtForm'])

// 监听图表 Y 轴配置变化并刷新本地表单
watch(
  () => props.chart.customStyle.yAxis,
  () => {
    init()
  },
  { deep: true }
)

// 同步主轴样式变更
const changeAxisStyle = (val, prop) => {
  emit('onChangeYAxisForm', val, prop)
}

// 同步副轴样式变更
const changeSubAxisStyle = (val, prop) => {
  emit('onChangeYAxisExtForm', val, prop)
}

// 从图表样式配置初始化双轴表单
const init = () => {
  const chart = JSON.parse(JSON.stringify(props.chart))
  if (chart.customStyle) {
    let customStyle = null
    if (Object.prototype.toString.call(chart.customStyle) === '[object Object]') {
      customStyle = JSON.parse(JSON.stringify(chart.customStyle))
    } else {
      customStyle = JSON.parse(chart.customStyle)
    }
    if (customStyle.yAxis) {
      state.axisForm = cloneDeep(customStyle.yAxis)
      state.axisForm.position = 'left'
    }

    if (customStyle.yAxisExt) {
      state.subAxisForm = cloneDeep(customStyle.yAxisExt)
    }
    state.subAxisForm.position = 'right'
    state.subAxisForm.show = state.axisForm.show
    if (chart.type === 'bidirectional-bar') {
      state.axisForm.position = customStyle.yAxis.position
      state.subAxisForm.position = customStyle.yAxisExt.position
    }
  }
}

onMounted(() => {
  init()
})
</script>

<template>
  <el-tabs v-model="activeName" id="axis-tabs" stretch>
    <el-tab-pane
      :label="
        chart.type === 'bidirectional-bar'
          ? t('chart.text_pos_left') + t('chart.xAxis')
          : t('chart.yAxisLeft')
      "
      name="left"
    >
      <dual-y-axis-selector-inner
        style="margin-top: 8px"
        v-if="state.axisForm"
        :form="state.axisForm"
        :property-inner="propertyInner"
        :themes="themes"
        type="left"
        :chart-type="chart.type"
        :layout="chart.customAttr.basicStyle.layout"
        @on-change-y-axis-form="changeAxisStyle"
      />
    </el-tab-pane>
    <el-tab-pane
      :label="
        chart.type === 'bidirectional-bar'
          ? t('chart.text_pos_right') + t('chart.xAxis')
          : t('chart.yAxisRight')
      "
      name="right"
    >
      <dual-y-axis-selector-inner
        style="margin-top: 8px"
        v-if="state.subAxisForm"
        :form="state.subAxisForm"
        :property-inner="propertyInner"
        :themes="themes"
        type="right"
        :chart-type="chart.type"
        :layout="chart.customAttr.basicStyle.layout"
        @on-change-y-axis-form="changeSubAxisStyle"
      />
    </el-tab-pane>
  </el-tabs>
</template>

<style lang="less" scoped>
#axis-tabs {
  margin-top: -16px;
  --ed-tabs-header-height: 34px;

  :deep(.ed-tabs__header) {
    border-top: none !important;
  }
}

.custom-form-item-label {
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
  border-color: rgba(31, 35, 41, 0.15);
  margin: 0 0 16px;

  &.m-divider--dark {
    border-color: rgba(235, 235, 235, 0.15);
  }
}
</style>
