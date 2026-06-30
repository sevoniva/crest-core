<script lang="ts" setup>
import { toRefs, PropType, ref, Ref, onBeforeMount, watch, nextTick, computed, inject } from 'vue'
import { type DatePickType } from 'element-plus-secondary'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import type { ManipulateType } from 'dayjs'
import { type TimeRange } from './time-format'
import dayjs from 'dayjs'
import { useI18n } from '@/hooks/web/useI18n'
import { useShortcuts } from './shortcuts'
import {
  getThisStart,
  getThisEnd,
  getLastStart,
  getAround,
  getAroundStart,
  getCustomRange
} from './time-format-dayjs'
import VanPopup from 'vant/es/popup'
import VanDatePicker from 'vant/es/date-picker'
import VanTimePicker from 'vant/es/time-picker'
import VanPickerGroup from 'vant/es/picker-group'
import 'vant/es/popup/style'
import 'vant/es/date-picker/style'
import 'vant/es/picker-group/style'
import 'vant/es/time-picker/style'

// 时间查询组件配置，运行态和配置态共用该结构传递默认值与时间范围规则
interface SelectConfig {
  selectValue: any
  defaultValue: any
  defaultValueCheck: boolean
  id: string
  queryConditionWidth: number
  displayType: string
  timeGranularity: DatePickType
  timeGranularityMultiple: DatePickType
  timeRange: TimeRange
  placeholder: string
  setTimeRange: boolean
}
const { t } = useI18n()

// 组件属性同时承载查询控件配置和编辑器配置态标识
const props = defineProps({
  config: {
    type: Object as PropType<SelectConfig>,
    default: () => {
      return {
        selectValue: '',
        defaultValue: '',
        queryConditionWidth: 0,
        defaultValueCheck: false,
        displayType: '1',
        timeGranularity: 'date',
        setTimeRange: false,
        timeGranularityMultiple: 'daterange',
        timeRange: {
          intervalType: 'none',
          dynamicWindow: false,
          maximumSingleQuery: 0,
          regularOrTrends: 'fixed',
          regularOrTrendsValue: '',
          relativeToCurrent: 'custom',
          timeNum: 0,
          relativeToCurrentType: 'year',
          around: 'f',
          timeNumRange: 0,
          relativeToCurrentTypeRange: 'year',
          aroundRange: 'f'
        }
      }
    }
  },
  isConfig: {
    type: Boolean,
    default: false
  }
})
// 占位符策略由查询容器注入，决定是否使用控件自身配置的提示文案
const placeholder: Ref = inject('placeholder')
// 展示占位文案时返回配置值，否则保留空格避免日期控件布局抖动
const placeholderText = computed(() => {
  if (placeholder?.value?.placeholderShow) {
    return props.config.placeholder
  }
  return ' '
})
// 当前日期选择值，单值和范围值都统一存放在这里
const selectValue = ref()
// 是否为时间范围选择模式，受 displayType 等于 7 控制
const multiple = ref(false)
const dvMainStore = dvMainStoreWithOut()
const { config } = toRefs(props)
// 移动端日期选择器允许的最早日期
const minDate = new Date('1970/1/1')
// 移动端日期选择器允许的最晚日期
const maxDate = new Date('2100/1/1')
// 运行态默认值变化时同步内部值，配置态不消费外部默认值变更
watch(
  () => config.value.defaultValue,
  val => {
    if (props.isConfig) return
    const isMultiple = config.value.displayType === '7'
    if (isMultiple) {
      multiple.value = isMultiple
    }
    selectValue.value = Array.isArray(val) ? [...val] : val
    nextTick(() => {
      multiple.value = isMultiple
    })
  }
)
// 快捷区间回调复用禁用日期逻辑，确保快捷选项不会越过时间范围约束
const callback = param => {
  startWindowTime.value = param[0]
  const disabled = param.some(ele => {
    return disabledDate(ele)
  })
  startWindowTime.value = 0
  return disabled
}

const { shortcuts } = useShortcuts(callback)

// 控件配置编号变化代表查询项切换，需要重新初始化内部选择值
watch(
  () => config.value.id,
  () => {
    init()
  }
)

// 配置态切换单选和范围模式时重置默认值，避免旧类型值污染新控件
const displayTypeChange = () => {
  if (!props.isConfig) return
  if (multiple.value && config.value.displayType === '7') return
  selectValue.value = config.value.displayType === '7' ? [] : undefined
  multiple.value = config.value.displayType === '7'
  config.value.defaultValue = multiple.value ? [] : undefined
  selectValue.value = multiple.value ? [] : undefined
}
// 运行态查询值变化时同步本地选择器，并在单选模式下恢复非数组值
watch(
  () => config.value.selectValue,
  val => {
    if (props.isConfig) return
    if (config.value.displayType === '7') {
      selectValue.value = Array.isArray(val) ? [...val] : val
    }
    nextTick(() => {
      multiple.value = config.value.displayType === '7'
      if (!multiple.value) {
        selectValue.value = Array.isArray(config.value.selectValue)
          ? [...config.value.selectValue]
          : config.value.selectValue
      }
    })
  }
)

// 将选择器输出统一格式化为后端查询需要的时间字符串，并按配置态写回不同字段
const handleValueChange = () => {
  if (selectValue.value === null) {
    selectValue.value = multiple.value ? [] : undefined
  }

  selectValue.value = Array.isArray(selectValue.value)
    ? selectValue.value.map(ele => ele && dayjs(ele).format('YYYY/MM/DD HH:mm:ss'))
    : selectValue.value && dayjs(selectValue.value).format('YYYY/MM/DD HH:mm:ss')
  const value = Array.isArray(selectValue.value) ? [...selectValue.value] : selectValue.value
  if (!props.isConfig) {
    config.value.selectValue = Array.isArray(selectValue.value)
      ? [...selectValue.value]
      : selectValue.value
    nextTick(() => {
      isConfirmSearch(config.value.id)
    })
    return
  }
  config.value.defaultValue = Array.isArray(value)
    ? value.map(ele => new Date(ele).toLocaleString())
    : new Date(value).toLocaleString()
}

// 初始化控件值，默认值启用时回填默认值，否则根据单选或范围模式清空
const init = () => {
  const { defaultValueCheck, displayType, defaultValue } = config.value
  const plus = displayType === '7'
  if (defaultValueCheck) {
    config.value.selectValue = Array.isArray(defaultValue) ? [...defaultValue] : defaultValue
    selectValue.value = Array.isArray(defaultValue) ? [...defaultValue] : defaultValue
  } else {
    config.value.selectValue = plus ? [] : undefined
    selectValue.value = plus ? [] : undefined
  }
  multiple.value = config.value.displayType === '7'
  currentDate.value = currentDate.value.slice(0, getIndex() + 1)
}

// 查询条件容器宽度由父级注入，独立预览时使用固定宽度兜底
const queryConditionWidth = inject('com-width', Function, true)
// 根据占位符展示策略和组件配置计算日期控件宽度
const getCustomWidth = () => {
  if (placeholder?.value?.placeholderShow) {
    if (props.config.queryConditionWidth === undefined) {
      return queryConditionWidth()
    }
    return props.config.queryConditionWidth
  }
  return 227
}
// 运行态值变化后通知父级是否需要立即触发查询
const isConfirmSearch = inject('is-confirm-search', Function, true)
// 范围选择宽度翻倍，保证起止时间输入区在查询栏中可读
const selectStyle = computed(() => {
  return props.isConfig
    ? {}
    : {
        width: (multiple.value ? getCustomWidth() * 2 : getCustomWidth()) + 'px !important'
      }
})

// 移动端日期列按时间粒度裁剪，桌面模式不启用 Vant 列配置
const columnsType = computed(() => {
  if (!dvMainStore.mobileInPc) return []
  return ['year', 'month', 'day'].slice(0, getIndex() + 1)
})

// 移动端是否需要展示时分秒选择器，由当前单选或范围粒度决定
const showTimePick = computed(() => {
  if (!dvMainStore.mobileInPc) return false
  const type = multiple.value ? config.value.timeGranularityMultiple : config.value.timeGranularity
  return type.includes('datetime')
})
// 移动端时间列当前值，和日期列共同组装最终时间
const currentTime = ref([])
// 移动端日期列当前值，按粒度裁剪成年、年月或年月日
const currentDate = ref(['2021', '01', '01'])
// 移动端底部日期弹窗显隐状态
const showDate = ref(false)

// 移动端是否按范围选择渲染左右两个点击区域
const isRange = computed(() => {
  if (!dvMainStore.mobileInPc) return false
  return +config.value.displayType === 7
})

// 打开范围结束时间选择器，并用当前结束值回填移动端日期和时间列
const showPopupRight = () => {
  const end = selectValue.value?.length > 1 ? selectValue.value[1] : null
  if (!!end) {
    const time = new Date(end)
    currentDate.value = [
      `${time.getFullYear()}`,
      `${time.getMonth() + 1}`,
      `${time.getDate()}`
    ].slice(0, getIndex() + 1)
    showTimePick.value &&
      (currentTime.value = [`${time.getHours()}`, `${time.getMinutes()}`, `${time.getSeconds()}`])
  }
  selectSecond.value = true
  showDate.value = true
}

// 计算当前时间粒度在移动端日期列中的截断位置
const getIndex = () => {
  const type = multiple.value ? config.value.timeGranularityMultiple : config.value.timeGranularity
  const index = ['year', 'month', 'date'].findIndex(ele => type.includes(ele))
  return index
}
// 动态窗口校验的起点时间，日历范围选择开始后临时写入
const startWindowTime = ref(0)
// 桌面范围选择过程中记录起点，供动态窗口限制计算结束日期
const calendarChange = val => {
  startWindowTime.value = +new Date(val[0])
}

// 桌面日期选择器实例，用于面板关闭后主动移除焦点
const datePicker = ref()

// 日期面板关闭时清理动态窗口状态，并消除控件焦点残留
const visibleChange = (visible: boolean) => {
  startWindowTime.value = 0
  if (!visible) {
    datePicker.value?.blur()
  }
}

// 将范围粒度转换成 dayjs 可识别的单位，用于动态窗口和边界计算
const queryTimeType = computed(() => {
  const noTime = config.value.timeGranularityMultiple.split('time').join('').split('range')[0]
  return noTime === 'date' ? 'day' : (noTime as ManipulateType)
})

// 根据固定边界、相对当前时间和动态窗口规则判断日期是否不可选
const disabledDate = val => {
  const timeStamp = +new Date(val)
  if (!config.value.setTimeRange) {
    return false
  }
  const {
    intervalType,
    regularOrTrends,
    regularOrTrendsValue,
    relativeToCurrent,
    relativeToCurrentRange,
    timeNum,
    relativeToCurrentType,
    around,
    dynamicWindow,
    maximumSingleQuery,
    timeNumRange,
    relativeToCurrentTypeRange,
    aroundRange
  } = config.value.timeRange || {}
  let isDynamicWindowTime = false

  if (startWindowTime.value && dynamicWindow) {
    isDynamicWindowTime =
      dayjs(startWindowTime.value)
        .add(maximumSingleQuery, queryTimeType.value)
        .startOf(queryTimeType.value)
        .valueOf() -
        1000 <
        timeStamp ||
      dayjs(startWindowTime.value)
        .subtract(maximumSingleQuery, queryTimeType.value)
        .startOf(queryTimeType.value)
        .valueOf() +
        1000 >
        timeStamp
  }
  if (intervalType === 'none') {
    if (dynamicWindow) return isDynamicWindowTime
    return false
  }
  let startTime
  if (relativeToCurrent === 'custom') {
    startTime = getAroundStart(relativeToCurrentType, around === 'f' ? 'subtract' : 'add', timeNum)
  } else {
    switch (relativeToCurrent) {
      case 'thisYear':
        startTime = getThisStart('year')
        break
      case 'lastYear':
        startTime = getLastStart('year')
        break
      case 'thisMonth':
        startTime = getThisStart('month')
        break
      case 'lastMonth':
        startTime = getLastStart('month')
        break
      case 'thisQuarter':
        startTime = getThisStart('quarter')
        break
      case 'thisWeek':
        startTime = new Date(
          dayjs().startOf('week').add(1, 'day').startOf('day').format('YYYY/MM/DD HH:mm:ss')
        )
        break
      case 'today':
        startTime = getThisStart('day')
        break
      case 'yesterday':
        startTime = getLastStart('day')
        break
      case 'monthBeginning':
        startTime = getThisStart('month')
        break
      case 'monthEnd':
        startTime = getThisEnd('month')
        break
      case 'yearBeginning':
        startTime = getThisStart('year')
        break

      default:
        break
    }
  }
  const startValue = regularOrTrends === 'fixed' ? regularOrTrendsValue : startTime

  if (intervalType === 'start') {
    return (
      timeStamp < +new Date(dayjs(startValue).startOf('day').format('YYYY/MM/DD HH:mm:ss')) ||
      isDynamicWindowTime
    )
  }

  if (intervalType === 'end') {
    return timeStamp > +new Date(startValue) || isDynamicWindowTime
  }

  if (intervalType === 'timeInterval') {
    let endTime
    if (relativeToCurrentRange === 'custom') {
      startTime =
        regularOrTrends === 'fixed'
          ? new Date(
              dayjs(new Date(regularOrTrendsValue[0]))
                .startOf(queryTimeType.value)
                .format('YYYY/MM/DD HH:mm:ss')
            )
          : getAroundStart(relativeToCurrentType, around === 'f' ? 'subtract' : 'add', timeNum)
      endTime =
        regularOrTrends === 'fixed'
          ? new Date(
              dayjs(new Date(regularOrTrendsValue[1]))
                .endOf(queryTimeType.value)
                .format('YYYY/MM/DD HH:mm:ss')
            )
          : getAround(
              relativeToCurrentTypeRange,
              aroundRange === 'f' ? 'subtract' : 'add',
              timeNumRange
            )
    } else {
      ;[startTime, endTime] = getCustomRange(relativeToCurrentRange)
    }
    return (
      timeStamp < +new Date(startTime) - 1000 ||
      timeStamp > +new Date(endTime) ||
      isDynamicWindowTime
    )
  }
}

// 打开移动端起始时间或单值选择器，并按当前值回填日期和时间列
const showPopup = () => {
  if (isRange.value) {
    const [start] = selectValue.value || []
    if (!!start) {
      const time = new Date(start)
      currentDate.value = [
        `${time.getFullYear()}`,
        `${time.getMonth() + 1}`,
        `${time.getDate()}`
      ].slice(0, getIndex() + 1)
      showTimePick.value &&
        (currentTime.value = [`${time.getHours()}`, `${time.getMinutes()}`, `${time.getSeconds()}`])
    }
  } else {
    const time = selectValue.value ? new Date(selectValue.value) : new Date()
    currentDate.value = [
      `${time.getFullYear()}`,
      `${time.getMonth() + 1}`,
      `${time.getDate()}`
    ].slice(0, getIndex() + 1)
    showTimePick.value &&
      (currentTime.value = [`${time.getHours()}`, `${time.getMinutes()}`, `${time.getSeconds()}`])
  }
  selectSecond.value = false
  showDate.value = true
}

// 关闭移动端日期弹窗，不改变当前选择值
const onCancel = () => {
  showDate.value = false
}

// 移动端范围选择当前是否在编辑结束时间
const selectSecond = ref(false)

// 将移动端日期列和时间列合并为单值或范围值
const setArrValue = () => {
  currentDate.value = currentDate.value.slice(0, getIndex() + 1)
  const timeFormat = [1, 2].includes(currentDate.value.length)
    ? currentDate.value.concat(Array([0, 2, 1][currentDate.value.length]).fill('01'))
    : currentDate.value
  if (isRange.value) {
    const [start, end] = selectValue.value || []
    if (selectSecond.value) {
      selectValue.value = [
        start ? start : new Date(`${timeFormat.join('/')} ${currentTime.value.join(':')}`),
        new Date(`${timeFormat.join('/')} ${currentTime.value.join(':')}`)
      ]
    } else {
      selectValue.value = [
        new Date(`${timeFormat.join('/')} ${currentTime.value.join(':')}`),
        end ? end : new Date(`${timeFormat.join('/')} ${currentTime.value.join(':')}`)
      ]
    }
  } else {
    selectValue.value = new Date(`${timeFormat.join('/')} ${currentTime.value.join(':')}`)
  }
}

// 清空当前时间条件，并保持单选或范围模式下的空值形态一致
const onClear = () => {
  showDate.value = false
  const { displayType } = config.value
  const plus = displayType === '7'
  config.value.selectValue = plus ? [] : undefined
  selectValue.value = plus ? [] : undefined
  handleValueChange()
}

// 确认移动端选择结果，写回控件值并关闭弹窗
const onConfirm = () => {
  setArrValue()
  handleValueChange()
  showDate.value = false
}
// 移动端快捷时间选择弹窗显隐状态
const showDateQuick = ref(false)
// 打开移动端范围快捷选择面板
const showQuick = () => {
  showDateQuick.value = true
}

// 接收快捷选择组件返回的 dayjs 范围，并转成查询时间字符串
const emitMobile = (_, val) => {
  const [start, end] = val
  selectValue.value = [start?.format('YYYY/MM/DD HH:mm:ss'), end?.format('YYYY/MM/DD HH:mm:ss')]
  handleValueChange()
  showDateQuick.value = false
}

// 挂载前初始化选择值，确保首次渲染时桌面和移动端状态一致
onBeforeMount(() => {
  init()
})

// 暴露给配置面板，在外部切换展示类型时重建默认值
defineExpose({
  displayTypeChange
})

// 年范围选择器只展示年份，其余粒度使用组件默认格式
const formatDate = computed(() => {
  return (config.value.timeGranularityMultiple as string) === 'yearrange' ? 'YYYY' : undefined
})
</script>

<template>
  <el-date-picker
    v-model="selectValue"
    v-if="multiple"
    :key="config.timeGranularityMultiple"
    :type="config.timeGranularityMultiple"
    :style="selectStyle"
    ref="datePicker"
    @visible-change="visibleChange"
    :disabled-date="disabledDate"
    @calendar-change="calendarChange"
    :format="formatDate"
    :shortcuts="
      ['datetimerange', 'daterange'].includes(config.timeGranularityMultiple) ? shortcuts : []
    "
    @change="handleValueChange"
    :editable="false"
    :range-separator="$t('cron.to')"
    :start-placeholder="placeholderText"
    :end-placeholder="placeholderText"
  />
  <el-date-picker
    v-else
    :key="config.timeGranularity + 1"
    v-model="selectValue"
    @visible-change="visibleChange"
    :disabled-date="disabledDate"
    :type="config.timeGranularity"
    @change="handleValueChange"
    :style="selectStyle"
    :placeholder="placeholderText"
  />
  <div
    v-if="dvMainStore.mobileInPc"
    class="vant-mobile"
    :class="isRange && 'wl50'"
    @click="showPopup"
  />
  <div v-if="dvMainStore.mobileInPc && isRange" class="vant-mobile wr50" @click="showPopupRight">
    <div class="quick-selection" @click.stop="showQuick"></div>
    <van-popup teleport="body" position="bottom" v-model:show="showDateQuick">
      <div
        @click="ele.onClick({ emit: emitMobile })"
        class="shortcuts-mobile"
        v-for="ele in shortcuts"
        :key="ele.text"
      >
        {{ ele.text }}
      </div></van-popup
    >
  </div>
  <van-popup
    v-if="dvMainStore.mobileInPc"
    teleport="body"
    position="bottom"
    v-model:show="showDate"
  >
    <van-picker-group
      @confirm="onConfirm"
      @cancel="onCancel"
      v-if="showTimePick"
      :title="t('v_query.time_selection')"
      :tabs="[t('dataset.select_date'), t('dataset.select_time')]"
      :next-step-text="t('sync_datasource.next')"
    >
      <van-date-picker
        :min-date="minDate"
        :max-date="maxDate"
        :columns-type="columnsType as any"
        v-model="currentDate"
      />
      <van-time-picker
        :columns-type="(['hour', 'minute', 'second'] as any)"
        v-model="currentTime"
      />
    </van-picker-group>
    <van-date-picker
      :title="t('dataset.select_date')"
      :columns-type="columnsType as any"
      @confirm="onConfirm"
      @cancel="onCancel"
      :min-date="minDate"
      :max-date="maxDate"
      v-if="!showTimePick"
      v-model="currentDate"
    />
  </van-popup>
  <Teleport v-if="showDate" to=".van-picker__toolbar">
    <button
      style="position: absolute; top: 0; right: 60px"
      @click="onClear"
      class="van-picker__confirm van-haptics-feedback oooo"
    >
      {{ t('commons.clear') }}
    </button></Teleport
  >
</template>

<style lang="less">
.vant-mobile {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;

  &.wl50 {
    width: 50%;
  }

  &.wr50 {
    left: auto;
    right: 0;
    width: 50%;

    .quick-selection {
      position: absolute;
      top: 0px;
      right: 10px;
      width: 24px;
      height: 32px;
      z-index: 10;
    }
  }
}
.shortcuts-mobile {
  padding: 10px;
  text-align: center;
  border-bottom: 1px solid #eee;
}
</style>
