<script lang="ts" setup>
import type { DatePickType } from 'element-plus-secondary'
import { toRefs, computed, watch } from 'vue'
import { type TimeRange } from './time-format'
import { useI18n } from '@/hooks/web/useI18n'
import DynamicTime from './DynamicTimeFiltering.vue'
import DynamicTimeRange from './DynamicTimeRangeFiltering.vue'
import { ManipulateType } from 'dayjs'
// 时间筛选配置由父组件持有，本组件只根据粒度提供固定时间和动态时间的编辑入口。
const props = withDefaults(
  defineProps<{
    timeRange: TimeRange
    timeGranularity: DatePickType
  }>(),
  {
    timeRange: () => ({
      // 新建单时间筛选默认不附加时间限制，避免影响已有查询条件结果。
      intervalType: 'none',
      dynamicWindow: false,
      maximumSingleQuery: 0,
      regularOrTrends: 'fixed',
      relativeToCurrentRange: 'custom',
      regularOrTrendsValue: '',
      relativeToCurrent: 'custom',
      timeNum: 0,
      relativeToCurrentType: 'year',
      around: 'f',
      timeNumRange: 0,
      relativeToCurrentTypeRange: 'year',
      aroundRange: 'f'
    }),
    timeGranularity: 'year'
  }
)

const { t } = useI18n()
// 区间类型决定筛选组件展示单端时间、双端区间或完全关闭时间限制。
const intervalTypeList = [
  {
    label: t('chart.line_symbol_none'),
    value: 'none'
  },
  {
    label: t('v_query.start_at'),
    value: 'start'
  },
  {
    label: t('v_query.end_at'),
    value: 'end'
  },
  {
    label: t('v_query.time_interval'),
    value: 'timeInterval'
  }
]

// 设置区标题跟随区间类型变化，保持开始、结束和区间三种配置语义一致。
const regularOrTrendsTitle = computed(() => {
  return (
    intervalTypeList.find(ele => ele.value === timeRange.value.intervalType)?.label ||
    intervalTypeList[0].label
  )
})
const { timeRange } = toRefs(props)
// 动态模式会把保存值解释为相对当前时间，固定模式则直接保存选择器中的具体日期。
const dynamicTime = computed(() => {
  return timeRange.value.regularOrTrends !== 'fixed'
})
// 区间模式使用范围组件，开始/结束模式使用单时间组件，避免模板中重复维护两套预览逻辑。
const filterTypeCom = computed(() => {
  const { intervalType } = timeRange.value
  return intervalType === 'timeInterval' ? DynamicTimeRange : DynamicTime
})
// Element Plus 的范围选择类型通过粒度后缀拼接得到，例如 monthrange、datetimerange。
const timeGranularityMultiple = computed<DatePickType>(() => {
  // Element 日期范围组件使用 range 后缀命名，按当前单点粒度派生即可。
  return (props.timeGranularity + 'range') as DatePickType
})
// 自定义相对时间使用前后方向描述，实际计算由动态时间组件按该方向解析。
const aroundList = [
  {
    label: t('dynamic_time.before'),
    value: 'f'
  },
  {
    label: t('dynamic_time.after'),
    value: 'b'
  }
]
// 相对时间单位不能细于当前筛选粒度，避免按年筛选时出现按日偏移这类无效配置。
const relativeToCurrentTypeList = computed(() => {
  if (!timeRange.value) return []
  // 按粒度列表位置截断单位，避免低粒度筛选保存高精度偏移。
  let index = ['year', 'month', 'date', 'datetime'].indexOf(props.timeGranularity) + 1
  return [
    {
      label: t('dynamic_time.year'),
      value: 'year'
    },
    {
      label: t('dynamic_time.month'),
      value: 'month'
    },
    {
      label: t('dynamic_time.date'),
      value: 'day'
    }
  ].slice(0, index)
})

// 单点动态快捷项按当前粒度收敛，保存值是稳定枚举，文案只用于界面展示。
const relativeToCurrentList = computed(() => {
  let list = []
  if (!timeRange.value) return list
  // 单点快捷项只表达一个时间点或周期边界，区间窗口放到范围列表处理。
  switch (props.timeGranularity) {
    case 'year':
      list = [
        {
          label: t('dynamic_year.current'),
          value: 'thisYear'
        },
        {
          label: t('dynamic_year.last'),
          value: 'lastYear'
        }
      ]
      break
    case 'month':
      list = [
        {
          label: t('cron.this_month'),
          value: 'thisMonth'
        },
        {
          label: t('dynamic_month.last'),
          value: 'lastMonth'
        }
      ]
      break
    case 'date':
      list = [
        {
          label: t('dynamic_time.today'),
          value: 'today'
        },
        {
          label: t('dynamic_time.yesterday'),
          value: 'yesterday'
        },
        {
          label: t('dynamic_time.firstOfMonth'),
          value: 'monthBeginning'
        },
        {
          label: t('dynamic_time.firstOfYear'),
          value: 'yearBeginning'
        }
      ]
      break
    case 'datetime':
      list = [
        {
          label: t('dynamic_time.today'),
          value: 'today'
        },
        {
          label: t('dynamic_time.yesterday'),
          value: 'yesterday'
        },
        {
          label: t('dynamic_time.firstOfMonth'),
          value: 'monthBeginning'
        },
        {
          label: t('dynamic_time.firstOfYear'),
          value: 'yearBeginning'
        }
      ]
      break

    default:
      break
  }

  return [
    ...list,
    {
      label: t('dynamic_time.custom'),
      value: 'custom'
    }
  ]
})

// 区间动态快捷项覆盖常用累计窗口，区间两端的自定义配置只在 custom 模式下显示。
const relativeToCurrentListRange = computed(() => {
  let list = []
  if (!timeRange.value) return list
  // 区间快捷项覆盖常见累计窗口，保存值保持与范围过滤组件一致。
  switch (props.timeGranularity) {
    case 'year':
      list = [
        {
          label: t('dynamic_year.current'),
          value: 'thisYear'
        },
        {
          label: t('dynamic_year.last'),
          value: 'lastYear'
        }
      ]
      break
    case 'month':
      list = [
        {
          label: t('cron.this_month'),
          value: 'thisMonth'
        },
        {
          label: t('dynamic_month.last'),
          value: 'lastMonth'
        },
        {
          label: t('v_query.last_3_months'),
          value: 'LastThreeMonths'
        },
        {
          label: t('v_query.last_6_months'),
          value: 'LastSixMonths'
        },
        {
          label: t('v_query.last_12_months'),
          value: 'LastTwelveMonths'
        },
        {
          label: t('common.to_this_month'),
          value: 'YearToThisMonth'
        }
      ]
      break
    case 'date':
    case 'datetime':
      list = [
        {
          label: t('dynamic_time.today'),
          value: 'today'
        },
        {
          label: t('dynamic_time.yesterday'),
          value: 'yesterday'
        },
        {
          label: t('v_query.last_3_days'),
          value: 'LastThreeDays'
        },
        {
          label: t('v_query.month_to_date'),
          value: 'monthBeginning'
        },
        {
          label: t('v_query.year_to_date'),
          value: 'yearBeginning'
        },
        {
          label: t('common.month_to_yesterday'),
          value: 'monthToYesterday'
        }
      ]
      break

    default:
      break
  }

  return [
    ...list,
    {
      label: t('dynamic_time.custom'),
      value: 'custom'
    }
  ]
})
// 粒度切换后若原区间快捷项不可用，回退到当前列表第一个有效项，避免保存脏值。
watch(
  () => relativeToCurrentListRange.value,
  val => {
    // 区间快捷项跟随粒度变化，旧值失效时回退到当前粒度的第一个可用项。
    if (!val.some(ele => ele.value === timeRange.value.relativeToCurrentRange)) {
      timeRange.value.relativeToCurrentRange = val[0].value
    }
  },
  { immediate: true }
)

// 单点快捷项同样需要随粒度回退，保证预览组件始终能解析当前枚举值。
watch(
  () => relativeToCurrentList.value,
  val => {
    // 单点快捷项独立校验，避免开始/结束模式误用区间枚举。
    if (!val.some(ele => ele.value === timeRange.value.relativeToCurrent)) {
      timeRange.value.relativeToCurrent = val[0].value
    }
  },
  { immediate: true }
)

// 自定义相对单位随粒度收敛后同步修正，避免 dayjs 计算收到当前粒度不支持的单位。
watch(
  () => relativeToCurrentTypeList.value,
  val => {
    // 偏移单位必须与当前时间粒度兼容，否则动态时间计算会产生越级偏移。
    if (!val.some(ele => ele.value === timeRange.value.relativeToCurrentType)) {
      timeRange.value.relativeToCurrentType = val[0].value as ManipulateType
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="set-time-filtering-range">
    <div class="title">{{ t('v_query.time_filter_range') }}</div>
    <!-- 单时间字段先选择开始、结束或区间语义，再决定固定时间还是动态时间。 -->
    <div class="list-item">
      <div class="label">{{ t('v_query.interval_type') }}</div>
      <div class="setting-content">
        <div class="setting">
          <el-radio-group v-model="timeRange.intervalType">
            <el-radio v-for="ele in intervalTypeList" :key="ele.value" :label="ele.value">{{
              ele.label
            }}</el-radio>
          </el-radio-group>
        </div>
      </div>
    </div>
    <div class="list-item" v-if="timeRange.intervalType !== 'none'">
      <div class="label">{{ regularOrTrendsTitle }}</div>
      <div class="setting-content">
        <div class="setting">
          <el-radio-group v-model="timeRange.regularOrTrends">
            <el-radio value="fixed">{{ t('dynamic_time.fix') }}</el-radio>
            <el-radio value="dynamic">{{ t('dynamic_time.dynamic') }}</el-radio>
          </el-radio-group>
        </div>
        <template v-if="dynamicTime && timeRange.intervalType !== 'timeInterval'">
          <div class="setting" v-if="timeRange.intervalType !== 'timeInterval'">
            <div class="setting-label">{{ t('dynamic_time.relative') }}</div>
            <div class="setting-value select">
              <el-select :teleported="false" v-model="timeRange.relativeToCurrent">
                <el-option
                  v-for="item in relativeToCurrentList"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </div>
          </div>
          <div class="setting" v-if="timeRange.relativeToCurrent === 'custom'">
            <div class="setting-input">
              <el-input-number v-model="timeRange.timeNum" :min="0" controls-position="right" />
              <el-select :teleported="false" v-model="timeRange.relativeToCurrentType">
                <el-option
                  v-for="item in relativeToCurrentTypeList"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
              <el-select :teleported="false" v-model="timeRange.around">
                <el-option
                  v-for="item in aroundList"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </div>
          </div>
        </template>
        <template v-else-if="dynamicTime && timeRange.intervalType === 'timeInterval'">
          <div class="setting">
            <div class="setting-label">{{ t('dynamic_time.relative') }}</div>
            <div class="setting-value select">
              <el-select :teleported="false" v-model="timeRange.relativeToCurrentRange">
                <el-option
                  v-for="item in relativeToCurrentListRange"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </div>
          </div>
          <template v-if="timeRange.relativeToCurrentRange === 'custom'">
            <!-- 自定义区间需要同时配置起点和终点偏移，预览组件负责换算实际时间。 -->
            <div
              class="setting"
              :class="['year', 'month'].includes(timeGranularity) && 'is-year-month-range'"
            >
              <div class="setting-label">{{ t('datasource.start_time') }}</div>
              <div class="setting-input range">
                <el-input-number
                  step-strictly
                  v-model="timeRange.timeNum"
                  :min="0"
                  controls-position="right"
                />
                <el-select :teleported="false" v-model="timeRange.relativeToCurrentType">
                  <el-option
                    v-for="item in relativeToCurrentTypeList"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <el-select :teleported="false" v-model="timeRange.around">
                  <el-option
                    v-for="item in aroundList"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </div>
            </div>
            <div
              class="setting"
              :class="['year', 'month'].includes(timeGranularity) && 'is-year-month-range'"
            >
              <div class="setting-label">{{ t('datasource.end_time') }}</div>
              <div class="setting-input range">
                <el-input-number
                  v-model="timeRange.timeNumRange"
                  :min="0"
                  step-strictly
                  controls-position="right"
                />
                <el-select :teleported="false" v-model="timeRange.relativeToCurrentTypeRange">
                  <el-option
                    v-for="item in relativeToCurrentTypeList"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <el-select :teleported="false" v-model="timeRange.aroundRange">
                  <el-option
                    v-for="item in aroundList"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </div>
            </div>
          </template>
        </template>
      </div>
      <div class="parameters" :class="dynamicTime && 'setting'">
        <div class="setting-label" v-if="dynamicTime">{{ t('template_manage.preview') }}</div>
        <div :class="dynamicTime ? 'setting-value' : 'w100'">
          <!-- 预览组件直接消费当前配置，便于用户在保存前确认动态时间解析结果。 -->
          <component
            :config="timeRange as any"
            :timeGranularityMultiple="timeGranularityMultiple"
            ref="inputCom"
            :is="filterTypeCom"
          ></component>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="less">
.set-time-filtering-range {
  // 单时间筛选嵌在查询条件配置中，控件高度和间距按紧凑表单处理。
  .ed-radio,
  .ed-checkbox.ed-checkbox--default {
    height: 22px;
    margin-right: 24px;
    --ed-radio-input-height: 16px;
    --ed-radio-input-width: 16px;
  }
  .ed-select {
    --ed-select-width: 100px;
  }
  .title {
    font-size: 14px;
    font-weight: 500;
    line-height: 22px;
    margin-bottom: 16px;
  }
  .list-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
    flex-wrap: wrap;

    .setting-content {
      width: 100%;
      &.maximum-single-query {
        padding-left: 24px;
        display: flex;
        align-items: center;
        margin-top: 8px;
        .ed-input-number {
          width: 120px;
          margin: 0 8px;
        }
      }
    }

    &.top-item {
      .label {
        margin-bottom: auto;
        padding-top: 5.5px;
      }
    }
    .label {
      width: 100px;
      color: #1f2329;
    }

    .value {
      width: 321px;
      .value {
        margin-top: 8px;
        &:first-child {
          margin-top: -0.5px;
        }
      }
      .ed-select {
        width: 321px;
      }
    }

    .parameters {
      // 固定时间选择器和动态时间预览共用容器，宽度按最大日期控件预留。
      width: 100%;
      margin-top: 8px;
      .w100 {
        width: 100%;
      }
      .ed-date-editor,
      .ed-date-editor--datetime .ed-input__wrapper,
      .ed-select-v2 {
        // 多种时间控件共用固定宽度，保证固定和动态模式切换时布局稳定。
        width: 415px;
      }

      .ed-date-editor {
        .ed-input__wrapper {
          width: 100%;
        }
      }
    }
    .parameters-range {
      width: 100%;
      padding-left: 24px;
      display: flex;
      flex-wrap: wrap;

      .range-title,
      .params-start,
      .params-end {
        width: 50%;
      }

      .params-start,
      .params-end {
        margin-top: 8px;
        .ed-select {
          width: 100%;
        }
      }

      .params-end {
        padding-left: 4px;
      }

      .params-start {
        padding-right: 4px;
      }
    }

    .setting {
      &.setting {
        margin-top: 8px;
      }
      &.parameters {
        width: 100%;
        padding-left: 24px;

        .setting-label {
          margin-left: 0;
        }
        .ed-date-editor {
          width: 308px !important;
        }
      }
      margin-left: auto;
      display: flex;
      justify-content: space-between;
      align-items: center;
      .setting-label {
        width: 80px;
        margin: 0 8px 0 24px;
      }

      .setting-value {
        &.select {
          .ed-select {
            width: 308px;
          }
        }
      }

      .setting-input {
        display: flex;
        padding-left: 112px;
        justify-content: flex-end;
        align-items: center;
        &.range {
          // 起止偏移输入不需要左侧缩进，保持与区间标签对齐。
          padding-left: 0px;
          width: 308px;
        }
        & > div + div {
          margin-left: 8px;
        }
      }

      &.is-year-month-range {
        // 年、月粒度的自定义区间不展示日期输入，只保留偏移量和单位选择。
        .setting-input {
          .ed-date-editor.ed-input {
            display: none;
          }
        }
      }
    }
  }
}
</style>
<style>
.range-filter-time {
  padding: 15px !important;
}
</style>
