<template>
  <div class="table-header-description-config" :class="{ dark: themes === 'dark' }">
    <div class="style-row">
      <el-form-item :label="t('chart.table_header_description_height')">
        <el-input-number
          v-model.number="state.form.rowHeight"
          :min="36"
          :max="260"
          :step="4"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item :label="t('chart.backgroundColor')">
        <el-color-picker
          :effect="themes"
          v-model="state.form.backgroundColor"
          is-custom
          :predefine="predefineColors"
        />
      </el-form-item>
      <el-form-item :label="t('chart.text')">
        <el-color-picker
          :effect="themes"
          v-model="state.form.fontColor"
          is-custom
          :predefine="predefineColors"
        />
      </el-form-item>
      <el-form-item :label="t('chart.font_size')">
        <el-input-number
          v-model.number="state.form.fontSize"
          :min="10"
          :max="40"
          :step="1"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item :label="t('chart.table_header_align')">
        <el-radio-group v-model="state.form.align">
          <el-radio value="left">{{ t('chart.text_pos_left') }}</el-radio>
          <el-radio value="center">{{ t('chart.text_pos_center') }}</el-radio>
          <el-radio value="right">{{ t('chart.text_pos_right') }}</el-radio>
        </el-radio-group>
      </el-form-item>
    </div>

    <div class="description-tip">{{ t('chart.table_header_description_tip') }}</div>
    <div class="description-list">
      <div v-for="item in descriptionRows" :key="item.field" class="description-item">
        <div class="field-name">{{ item.name }}</div>
        <el-input
          v-model="descriptionMap[item.field]"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 5 }"
          maxlength="300"
          show-word-limit
          :placeholder="t('chart.table_header_description_placeholder')"
        />
      </div>
    </div>

    <div class="button-group">
      <el-button :effect="themes" @click="onCancelConfig">{{ t('chart.cancel') }}</el-button>
      <el-button type="primary" @click="onConfigChange">{{ t('chart.confirm') }}</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, PropType, reactive } from 'vue'
import { cloneDeep, defaultsDeep } from 'lodash-es'
import { useI18n } from '@/hooks/web/useI18n'
import { COLOR_PANEL, DEFAULT_TABLE_HEADER } from '@/views/chart/components/editor/util/chart'
import {
  normalizeAuxiliaryDescriptions,
  normalizeAuxiliaryHeader
} from '@/views/chart/components/js/panel/common/tableAuxiliaryHeader.mjs'

const { t } = useI18n()
const predefineColors = COLOR_PANEL

// 定义表头描述配置面板接收的图表和表头参数
const props = defineProps({
  chart: {
    type: Object as PropType<ChartObj>,
    required: true
  },
  themes: {
    type: String as PropType<EditorTheme>,
    default: 'dark'
  },
  tableHeaderForm: {
    type: Object as PropType<ChartTableHeaderAttr>,
    required: true
  }
})

// 声明表头描述配置确认和取消事件
const emits = defineEmits(['onConfigChange', 'onCancelConfig'])

// 计算当前可展示表头描述的字段轴
const visibleAxis = computed(() => {
  const axis = [...(props.chart?.xAxis || [])]
  if (props.chart?.type === 'table-normal') {
    axis.push(...(props.chart?.yAxis || []))
  }
  return axis.filter(item => item.hide !== true)
})

// 生成表头描述编辑行
const descriptionRows = computed(() =>
  visibleAxis.value.map(item => ({
    field: item.engineFieldName,
    name: item.chartShowName ?? item.name
  }))
)

const normalizedHeader = defaultsDeep(
  cloneDeep(props.tableHeaderForm),
  cloneDeep(DEFAULT_TABLE_HEADER)
) as ChartTableHeaderAttr
const auxiliaryHeader = normalizeAuxiliaryHeader(normalizedHeader.auxiliaryHeader)
// 保存辅助表头描述配置表单
const state = reactive({
  form: {
    ...auxiliaryHeader,
    enabled: true
  }
})
// 保存字段与描述文案的映射
const descriptionMap = reactive(
  normalizeAuxiliaryDescriptions(
    state.form,
    descriptionRows.value.map(item => item.field)
  ).reduce((pre, cur) => {
    pre[cur.field] = cur.text
    return pre
  }, {})
)

// 取消表头描述配置
const onCancelConfig = () => {
  emits('onCancelConfig')
}

// 提交表头描述配置
const onConfigChange = () => {
  emits('onConfigChange', {
    ...state.form,
    enabled: true,
    descriptions: descriptionRows.value.map(item => ({
      field: item.field,
      text: descriptionMap[item.field] || ''
    }))
  })
}
</script>

<style lang="less" scoped>
.table-header-description-config {
  color: #1f2329;

  &.dark {
    color: #d5d6d9;
  }
}

.style-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;

  :deep(.ed-form-item) {
    margin-bottom: 8px;
  }
}

.description-tip {
  margin: 4px 0 12px;
  color: #8f959e;
  font-size: 12px;
}

.description-list {
  max-height: 420px;
  overflow: auto;
  padding-right: 4px;
}

.description-item {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.field-name {
  min-height: 32px;
  line-height: 32px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.button-group {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}
</style>
