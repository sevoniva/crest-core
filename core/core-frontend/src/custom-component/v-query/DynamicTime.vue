<script lang="ts" setup>
import type { ManipulateType } from 'dayjs'
import { toRefs, PropType, ref, onBeforeMount, watch, computed } from 'vue'
import { Calendar } from '@element-plus/icons-vue'
import { type DatePickType } from 'element-plus-secondary'
import {
  getThisYear,
  getLastYear,
  getThisMonth,
  getLastMonth,
  getToday,
  getYesterday,
  getMonthBeginning,
  getMonthEnd,
  getYearBeginning,
  getCustomTime
} from './time-format'
interface SelectConfig {
  timeType: string
  defaultValue: string
  selectValue: string
  relativeToCurrent: string
  defaultValueCheck: boolean
  id: string
  timeNum: number
  relativeToCurrentType: ManipulateType
  around: string
  arbitraryTime: Date
  timeGranularity: DatePickType
}

// 查询条件的动态时间配置属性
const props = defineProps({
  config: {
    type: Object as PropType<SelectConfig>,
    default: () => {
      return {
        defaultValue: '',
        selectValue: '',
        timeType: 'fixed',
        relativeToCurrent: 'custom',
        timeNum: 0,
        relativeToCurrentType: 'year',
        around: 'f',
        arbitraryTime: new Date(),
        defaultValueCheck: false,
        timeGranularity: 'date'
      }
    }
  }
})
// 日期选择器当前展示的默认时间值
const selectValue = ref()

// 保持 config 的响应式引用，便于直接回写默认值
const { config } = toRefs(props)

// 抽取会影响动态时间计算的配置项，减少无关字段触发
const timeConfig = computed(() => {
  const {
    relativeToCurrent,
    timeNum,
    relativeToCurrentType,
    around,
    defaultValueCheck,
    arbitraryTime,
    timeGranularity
  } = config.value
  return {
    relativeToCurrent,
    timeNum,
    relativeToCurrentType,
    around,
    defaultValueCheck,
    arbitraryTime,
    timeGranularity
  }
})

// 动态时间配置变化后重新计算默认日期
watch(
  () => timeConfig.value,
  () => {
    init()
  },
  {
    deep: true
  }
)

// 日期值变化后同步到查询条件配置
watch(
  () => selectValue.value,
  val => {
    config.value.defaultValue = val
    config.value.selectValue = val
  }
)

// 查询项切换后重新初始化默认日期
watch(
  () => config.value.id,
  () => {
    init()
  }
)

// 根据动态时间配置计算默认日期，并同步到查询参数
const init = () => {
  const {
    relativeToCurrent,
    timeNum,
    relativeToCurrentType,
    around,
    defaultValueCheck,
    arbitraryTime,
    timeGranularity
  } = timeConfig.value
  if (!defaultValueCheck) {
    selectValue.value = null
    return
  }
  if (relativeToCurrent === 'custom') {
    selectValue.value = getCustomTime(
      timeNum,
      relativeToCurrentType,
      timeGranularity,
      around,
      timeGranularity === 'datetime' ? arbitraryTime : null
    )
  } else {
    switch (relativeToCurrent) {
      case 'thisYear':
        selectValue.value = getThisYear()
        break
      case 'lastYear':
        selectValue.value = getLastYear()
        break
      case 'thisMonth':
        selectValue.value = getThisMonth()
        break
      case 'lastMonth':
        selectValue.value = getLastMonth()
        break
      case 'today':
        selectValue.value = getToday()
        break
      case 'yesterday':
        selectValue.value = getYesterday()
        break
      case 'monthBeginning':
        selectValue.value = getMonthBeginning()
        break
      case 'monthEnd':
        selectValue.value = getMonthEnd()
        break
      case 'yearBeginning':
        selectValue.value = getYearBeginning()
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
    disabled
    :key="config.timeGranularity"
    v-model="selectValue"
    :type="config.timeGranularity"
    :prefix-icon="Calendar"
    :popper-class="'custom-dynamic-time-popper_class'"
    :placeholder="$t('commons.date.select_date_time')"
  />
</template>

<style lang="less">
.custom-dynamic-time-popper_class {
  font-family: var(--crest-canvas_custom_font);
}
</style>
