<script lang="ts" setup>
import { toRefs, computed, PropType, watch } from 'vue'
import { type TimeRange } from './time-format'
import { useI18n } from '@/hooks/web/useI18n'
import DynamicTime from './DynamicTimeFiltering.vue'
import DynamicTimeRange from './DynamicTimeRangeFiltering.vue'
import { ManipulateType } from 'dayjs'
// 时间范围过滤组件用于范围型日期字段，配置对象由父组件持有，本组件只修正可选项和预览入口。
const props = defineProps({
  timeRange: {
    type: Object as PropType<TimeRange>,
    default: () => ({
      // 默认不限制时间范围，避免新增筛选组件后立即改变已有查询结果。
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
    })
  },
  timeGranularityMultiple: {
    type: String,
    default: 'yearrange'
  }
})

const { t } = useI18n()
// 区间类型决定最终筛选条件写入开始时间、结束时间、完整区间或不写入时间限制。
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

// 设置区标题跟随区间类型变化，避免开始、结束和区间配置复用同一静态文案。
const regularOrTrendsTitle = computed(() => {
  return (
    intervalTypeList.find(ele => ele.value === timeRange.value.intervalType)?.label ||
    intervalTypeList[0].label
  )
})
const { timeRange } = toRefs(props)
// 动态时间按当前日期滚动计算，固定时间直接使用选择器保存的日期范围。
const dynamicTime = computed(() => {
  return timeRange.value.regularOrTrends !== 'fixed'
})
// 区间筛选使用范围组件，开始/结束筛选使用单点组件，预览区域保持同一 component 入口。
const filterTypeCom = computed(() => {
  const { intervalType } = timeRange.value
  return intervalType === 'timeInterval' ? DynamicTimeRange : DynamicTime
})

// 自定义相对时间的方向由动态时间组件解析，当前组件只提供枚举和值回填。
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
// 相对时间单位不能细于当前范围粒度，例如 yearrange 只允许按年偏移。
const relativeToCurrentTypeList = computed(() => {
  if (!timeRange.value) return []
  // 通过粒度在有序列表中的位置裁剪单位，保证范围字段和单点字段规则一致。
  let index =
    ['yearrange', 'monthrange', 'daterange', 'datetimerange'].indexOf(
      props.timeGranularityMultiple
    ) + 1
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

// 动态窗口的单位提示取当前粒度下最细单位，帮助用户理解最大查询范围的限制。
const relativeToCurrentTypeListTips = computed(() => {
  return (relativeToCurrentTypeList.value[relativeToCurrentTypeList.value.length - 1] || {}).label
})
// 单点快捷项按范围粒度收敛，保存值保持稳定枚举，便于后端和预览组件解析。
const relativeToCurrentList = computed(() => {
  let list = []
  if (!timeRange.value) return list
  // 年、月、日和日期时间的快捷值分别映射到后端可识别的稳定枚举。
  switch (props.timeGranularityMultiple) {
    case 'yearrange':
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
    case 'monthrange':
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
    case 'daterange':
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
          label: t('dynamic_time.endOfMonth'),
          value: 'monthEnd'
        },
        {
          label: t('dynamic_time.firstOfYear'),
          value: 'yearBeginning'
        }
      ]
      break
    case 'datetimerange':
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
          label: t('dynamic_time.endOfMonth'),
          value: 'monthEnd'
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

// 区间快捷项覆盖常见滚动窗口，custom 模式再展示起止偏移的细粒度输入。
const relativeToCurrentListRange = computed(() => {
  let list = []
  if (!timeRange.value) return list
  // 区间快捷项比单点快捷项多滚动窗口和累计到当前周期的业务语义。
  switch (props.timeGranularityMultiple) {
    case 'yearrange':
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
    case 'monthrange':
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
    case 'daterange':
    case 'datetimerange':
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

// 粒度切换后修正失效区间快捷项，避免旧枚举继续保存到筛选条件中。
watch(
  () => relativeToCurrentListRange.value,
  val => {
    // 当前粒度不再支持原快捷项时，立即回退到首个有效区间，避免预览组件解析失败。
    if (!val.some(ele => ele.value === timeRange.value.relativeToCurrentRange)) {
      timeRange.value.relativeToCurrentRange = val[0].value
    }
  },
  { immediate: true }
)

// 单点快捷项同样随粒度修正，保证开始/结束模式能够稳定生成预览时间。
watch(
  () => relativeToCurrentList.value,
  val => {
    // 开始/结束模式的快捷项随粒度变化独立校验，不复用区间快捷项。
    if (!val.some(ele => ele.value === timeRange.value.relativeToCurrent)) {
      timeRange.value.relativeToCurrent = val[0].value
    }
  },
  { immediate: true }
)

// 起止时间单位都必须属于当前粒度允许范围，避免 dayjs 收到不支持的单位。
watch(
  () => relativeToCurrentTypeList.value,
  val => {
    // 起点和终点的偏移单位都要落在当前粒度允许范围内。
    if (!val.some(ele => ele.value === timeRange.value.relativeToCurrentType)) {
      timeRange.value.relativeToCurrentType = val[0].value as ManipulateType
    }

    if (!val.some(ele => ele.value === timeRange.value.relativeToCurrentTypeRange)) {
      timeRange.value.relativeToCurrentTypeRange = val[0].value as ManipulateType
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="set-time-filtering-range">
    <div class="title">{{ t('v_query.time_filter_range') }}</div>
    <!-- 区间类型决定最终生成的过滤表达式结构，必须先于动态/固定时间配置选择。 -->
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
        <!-- 固定和动态时间共用同一份配置对象，切换时保留旧值用于回切恢复。 -->
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
          <!-- 区间模式使用范围快捷项，和开始/结束模式的单点快捷项分开维护。 -->
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
            <!-- 自定义区间分别配置起点和终点偏移，最终由动态范围组件计算实际边界。 -->
            <div
              class="setting"
              :class="
                ['yearrange', 'monthrange'].includes(timeGranularityMultiple) &&
                'is-year-month-range'
              "
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
              :class="
                ['yearrange', 'monthrange'].includes(timeGranularityMultiple) &&
                'is-year-month-range'
              "
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
          <!-- 预览组件复用同一份 timeRange，确保用户看到的时间与保存后的过滤条件一致。 -->
          <component
            :config="timeRange as any"
            :timeGranularityMultiple="timeGranularityMultiple as any"
            ref="inputCom"
            :is="filterTypeCom"
          ></component>
        </div>
      </div>
    </div>
    <div class="list-item">
      <div class="label">
        <el-checkbox v-model="timeRange.dynamicWindow" :label="t('v_query.query_time_window')" />
      </div>
      <!-- 动态窗口限制单次查询跨度，用于控制日期范围过大时的查询成本。 -->
      <div v-if="timeRange.dynamicWindow" class="setting-content maximum-single-query">
        <!-- 最大查询窗口按当前粒度最细单位解释，例如按日或按月限制范围跨度。 -->
        {{ t('v_query.maximum_single_query') }}
        <el-input-number
          v-model="timeRange.maximumSingleQuery"
          :min="1"
          controls-position="right"
        />
        {{ relativeToCurrentTypeListTips }}
      </div>
    </div>
  </div>
</template>

<style lang="less">
.set-time-filtering-range {
  // 时间筛选配置嵌在查询条件抽屉内，控件高度需要比普通表单更紧凑。
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
      // 固定时间和动态时间共用预览区，宽度按日期选择器的最大形态预留。
      width: 100%;
      margin-top: 8px;
      .w100 {
        width: 100%;
      }
      .ed-date-editor,
      .ed-date-editor--datetime .ed-input__wrapper,
      .ed-select-v2 {
        // 宽度覆盖 Element 日期选择器和虚拟选择器，避免不同控件切换时抖动。
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
        .ed-input-number {
          width: auto;
        }
        &.range {
          // 自定义区间的起止行各自独占右侧输入区。
          padding-left: 0px;
          width: 308px;
        }
        & > div + div {
          margin-left: 8px;
        }
      }

      &.is-year-month-range {
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
