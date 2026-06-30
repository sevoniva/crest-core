<script setup lang="ts">
import { propTypes } from '@/utils/propTypes'
import { computed, PropType, reactive, toRefs, h } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'

// 时间筛选组件的国际化文案函数
const { t } = useI18n()

// 抽屉时间筛选配置
interface Config {
  // 显示类型
  showType: string
  // 日期分隔符
  rangeSeparator: string
  // 开始日期label
  startPlaceholder: string
  // 结束日期label
  endPlaceholder: string
  // 日期格式
  format: string
  // 日期值格式
  valueFormat: string
  // 尺寸
  size: string
  // 弹出位置
  placement: string
}

// 时间筛选组件属性，包含配置和标题
const props = defineProps({
  property: Object as PropType<Config>,
  title: propTypes.string
})
// 保持时间配置的响应式引用
const { property } = toRefs(props)
// 合并默认时间选择器配置和外部传入配置
const timeConfig = computed(() => {
  let obj = Object.assign(
    {
      showType: 'datetime',
      rangeSeparator: '-',
      startPlaceholder: t('datasource.start_time'),
      endPlaceholder: t('datasource.end_time'),
      format: 'YYYY-MM-DD HH:mm:ss',
      valueFormat: 'YYYY-MM-DD HH:mm:ss',
      size: 'default',
      placement: 'bottom-end'
    },
    property.value
  )
  return obj
})
// 记录当前日期范围选择值
const state = reactive({
  modelValue: []
})

// 时间筛选变化时通知父级
const emits = defineEmits(['filter-change'])
// 提交当前时间范围
const onChange = () => {
  emits('filter-change', state.modelValue)
}
// 清空当前时间范围
const clear = () => {
  state.modelValue = []
}
defineExpose({
  clear
})
</script>

<template>
  <div class="draw-filter_time">
    <span>{{ title }}</span>
    <div class="filter-item">
      <el-date-picker
        v-model="state.modelValue"
        :type="timeConfig.showType"
        :range-separator="timeConfig.rangeSeparator"
        :start-placeholder="timeConfig.startPlaceholder"
        :end-placeholder="timeConfig.endPlaceholder"
        :format="timeConfig.format"
        :value-format="timeConfig.valueFormat"
        key="drawer-time-filt"
        :size="timeConfig.size"
        @change="onChange"
        :placement="timeConfig.placement"
      />
    </div>
  </div>
</template>
<style lang="less" scope>
.draw-filter_time {
  margin-bottom: 16px;

  > :nth-child(1) {
    color: var(--crestTextSecondary, #1f2329);
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    white-space: nowrap;
  }

  .filter-item {
    margin-top: 8px;
    .ed-date-editor {
      width: 100%;
    }
  }
}
</style>
