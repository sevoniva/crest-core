<script lang="ts" setup>
import { toRefs, PropType, onBeforeMount, watch, computed } from 'vue'
import { Calendar } from '@element-plus/icons-vue'
import { type DatePickType } from 'element-plus-secondary'
import type { ManipulateType } from 'dayjs'
import { getThisStart, getThisEnd, getLastStart, getAroundStart } from './time-format-dayjs'
// 单值动态时间查询配置
interface SelectConfig {
  intervalType: string
  regularOrTrendsValue: Date
  regularOrTrends: string
  relativeToCurrent: string
  timeNum: number
  relativeToCurrentType: ManipulateType
  around: string
}

// 单值动态时间过滤组件属性
const props = defineProps({
  config: {
    type: Object as PropType<SelectConfig>,
    default: () => {
      return {
        regularOrTrendsValue: '',
        intervalType: 'none',
        regularOrTrends: 'fixed',
        relativeToCurrent: 'custom',
        timeNum: 0,
        relativeToCurrentType: 'year',
        around: 'f'
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

// 保持动态时间配置的响应式引用
const { config } = toRefs(props)

// 抽取会影响动态时间计算的配置项
const timeConfig = computed(() => {
  const {
    relativeToCurrent,
    regularOrTrends,
    intervalType,
    timeNum,
    relativeToCurrentType,
    around
  } = config.value
  return {
    relativeToCurrent,
    timeNum,
    regularOrTrends,
    intervalType,
    relativeToCurrentType,
    around,
    timeGranularityMultiple: props.timeGranularityMultiple
  }
})

// 根据配置变化重新计算默认时间
watch(
  () => timeConfig.value,
  () => {
    init()
  },
  {
    deep: true
  }
)

// 根据区间模式计算日期选择器类型
const timeInterval = computed<DatePickType>(() => {
  const noTime = props.timeGranularityMultiple.split('time').join('')
  return config.value.intervalType === 'timeInterval'
    ? (noTime as DatePickType)
    : (noTime.split('range')[0] as DatePickType)
})

// 初始化固定或相对当前的时间值
const init = () => {
  const { relativeToCurrent, regularOrTrends, timeNum, relativeToCurrentType, around } =
    timeConfig.value
  if (regularOrTrends === 'fixed') {
    if (!!config.value.regularOrTrendsValue && !Array.isArray(config.value.regularOrTrendsValue))
      return
    config.value.regularOrTrendsValue = new Date()
    return
  }
  if (relativeToCurrent === 'custom') {
    config.value.regularOrTrendsValue = getAroundStart(
      relativeToCurrentType,
      around === 'f' ? 'subtract' : 'add',
      timeNum
    )
  } else {
    switch (relativeToCurrent) {
      case 'thisYear':
        config.value.regularOrTrendsValue = getThisStart('year')
        break
      case 'lastYear':
        config.value.regularOrTrendsValue = getLastStart('year')
        break
      case 'thisMonth':
        config.value.regularOrTrendsValue = getThisStart('month')
        break
      case 'lastMonth':
        config.value.regularOrTrendsValue = getLastStart('month')
        break
      case 'today':
        config.value.regularOrTrendsValue = getThisStart('day')
        break
      case 'yesterday':
        config.value.regularOrTrendsValue = getLastStart('day')
        break
      case 'monthBeginning':
        config.value.regularOrTrendsValue = getThisStart('month')
        break
      case 'monthEnd':
        config.value.regularOrTrendsValue = getThisEnd('month')
        break
      case 'yearBeginning':
        config.value.regularOrTrendsValue = getThisStart('year')
        break

      default:
        break
    }
  }
}

onBeforeMount(() => {
  init()
})
</script>

<template>
  <el-date-picker
    :disabled="config.regularOrTrends !== 'fixed'"
    :key="timeInterval"
    v-model="config.regularOrTrendsValue"
    :type="timeInterval"
    :prefix-icon="Calendar"
    :placeholder="$t('commons.date.select_date_time')"
  />
</template>
