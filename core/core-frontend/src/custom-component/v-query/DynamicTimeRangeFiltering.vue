<script lang="ts" setup>
import { toRefs, PropType, onBeforeMount, watch, computed } from 'vue'
import { Calendar } from '@element-plus/icons-vue'
import { type DatePickType } from 'element-plus-secondary'
import type { ManipulateType } from 'dayjs'
import { getAround, getCustomRange, getAroundStart } from './time-format-dayjs'
// 动态时间范围查询配置
interface SelectConfig {
  regularOrTrends: string
  regularOrTrendsValue: [Date, Date]
  intervalType: string
  relativeToCurrentRange: string
  timeNum: number
  relativeToCurrentType: ManipulateType
  around: string
  timeGranularity: DatePickType
  timeNumRange: number
  relativeToCurrentTypeRange: ManipulateType
  aroundRange: string
}

// 时间范围过滤组件属性
const props = defineProps({
  config: {
    type: Object as PropType<SelectConfig>,
    default: () => {
      return {
        timeGranularityMultiple: 'daterange',
        regularOrTrendsValue: [],
        regularOrTrends: 'fixed',
        timeNum: 0,
        intervalType: 'none',
        relativeToCurrentRange: 'custom',
        relativeToCurrentType: 'year',
        around: 'f',
        timeGranularity: 'date',
        timeNumRange: 0,
        relativeToCurrentTypeRange: 'year',
        aroundRange: 'f'
      }
    }
  },
  timeGranularityMultiple: {
    type: String as PropType<DatePickType>,
    default: () => {
      return 'yearrange'
    }
  }
})
// 保持配置对象的响应式引用，便于直接回写默认时间范围
const { config } = toRefs(props)

// 抽取会影响动态范围计算的配置项
const timeConfig = computed(() => {
  const {
    timeNum,
    relativeToCurrentType,
    around,
    relativeToCurrentRange,
    intervalType,
    regularOrTrends,
    timeGranularity,
    timeNumRange,
    relativeToCurrentTypeRange,
    aroundRange
  } = config.value
  return {
    timeNum,
    relativeToCurrentType,
    around,
    intervalType,
    relativeToCurrentRange,
    regularOrTrends,
    timeGranularity,
    timeNumRange,
    relativeToCurrentTypeRange,
    aroundRange,
    timeGranularityMultiple: props.timeGranularityMultiple
  }
})
// 根据区间模式计算日期选择器类型
const timeInterval = computed<DatePickType>(() => {
  const noTime = props.timeGranularityMultiple.split('time').join('')
  return config.value.intervalType === 'timeInterval'
    ? (noTime as DatePickType)
    : (noTime.split('range')[0] as DatePickType)
})
// 监听相关数据变化并同步组件状态
watch(
  () => timeConfig.value,
  () => {
    init()
  },
  {
    deep: true
  }
)

// 根据固定或动态规则初始化时间范围
const init = () => {
  const {
    timeNum,
    relativeToCurrentType,
    around,
    relativeToCurrentRange,
    regularOrTrends,
    timeNumRange,
    relativeToCurrentTypeRange,
    aroundRange
  } = timeConfig.value

  if (regularOrTrends === 'fixed') {
    if (
      Array.isArray(config.value.regularOrTrendsValue) &&
      !!config.value.regularOrTrendsValue.length
    )
      return
    config.value.regularOrTrendsValue = [
      getAroundStart(relativeToCurrentTypeRange, 'add', 0),
      getAround(relativeToCurrentTypeRange, 'add', 1)
    ]
    return
  }

  const startTime = getAroundStart(
    relativeToCurrentType,
    around === 'f' ? 'subtract' : 'add',
    timeNum
  )
  const endTime = getAround(
    relativeToCurrentTypeRange,
    aroundRange === 'f' ? 'subtract' : 'add',
    timeNumRange
  )

  if (!!relativeToCurrentRange && relativeToCurrentRange !== 'custom') {
    config.value.regularOrTrendsValue = getCustomRange(relativeToCurrentRange)
    return
  }

  config.value.regularOrTrendsValue = [startTime, endTime]
}

onBeforeMount(() => {
  init()
})

// 年范围选择时使用年份格式，其它类型沿用组件默认格式
const formatDate = computed(() => {
  return (props.timeGranularityMultiple as string) === 'yearrange' ? 'YYYY' : undefined
})
</script>

<template>
  <el-date-picker
    :disabled="config.regularOrTrends !== 'fixed'"
    v-model="config.regularOrTrendsValue"
    :key="timeInterval"
    :type="timeInterval"
    :prefix-icon="Calendar"
    :format="formatDate"
    :popper-class="'custom-dynamic-time-range-filter-popper_class'"
    :range-separator="$t('cron.to')"
    :start-placeholder="$t('datasource.start_time')"
    :end-placeholder="$t('datasource.end_time')"
  />
</template>

<style lang="less">
.custom-dynamic-time-range-filter-popper_class {
  font-family: var(--crest-canvas_custom_font);
}
</style>
