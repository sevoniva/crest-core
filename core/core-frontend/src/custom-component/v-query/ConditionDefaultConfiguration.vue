<script lang="ts" setup>
import icon_admin_outlined from '@/assets/svg/icon_admin_outlined.svg'
import { ElSelect } from 'element-plus-secondary'
import { computed, nextTick, ref, toRefs, watch } from 'vue'
import RangeFilterTime from '@/custom-component/v-query/RangeFilterTime.vue'
import FilterTime from '@/custom-component/v-query/FilterTime.vue'
import { useI18n } from '@/hooks/web/useI18n'
import DynamicTime from '@/custom-component/v-query/DynamicTime.vue'
import DynamicTimeRange from '@/custom-component/v-query/DynamicTimeRange.vue'
import Time from '@/custom-component/v-query/Time.vue'
import Select from '@/custom-component/v-query/Select.vue'
import Tree from '@/custom-component/v-query/Tree.vue'
const { t } = useI18n()

// 时间范围配置弹层由本组件控制，避免默认值区域点击影响外层面板收起逻辑。
const visiblePopover = ref(false)

// 子级选择框聚焦时主动关闭范围弹层，防止多个浮层同时覆盖。
const handleDialogClick = () => {
  visiblePopover.value = false
}

// 切换时间范围配置弹层时阻止冒泡，避免触发外层点击关闭。
const handleVisiblePopover = ev => {
  ev.stopPropagation()
  visiblePopover.value = !visiblePopover.value
}

// 根据查询组件展示类型和时间类型选择默认值编辑组件。
const filterTypeCom = computed(() => {
  const { displayType, timeType = 'fixed' } = curComponent.value
  // displayType 1/7 是日期类组件，动态默认值需要切换到专用预览组件。
  return ['1', '7'].includes(displayType)
    ? timeType === 'dynamic'
      ? displayType === '1'
        ? DynamicTime
        : DynamicTimeRange
      : Time
    : '9' === displayType
    ? Tree
    : Select
})

// 动态时间切换需要父级补齐动态时间默认结构，因此单独暴露事件。
const emits = defineEmits(['handleTimeTypeChange'])

// 只有切换到动态时间时才触发父级同步，固定时间不需要重建结构。
const handleTimeTypeChange = val => {
  if (val === 'dynamic') {
    emits('handleTimeTypeChange')
  }
}

// 查询条件默认值配置直接编辑当前查询组件，showPosition 控制主/侧栏差异。
const props = defineProps({
  curComponent: {
    type: Object,
    required: true
  },
  showPosition: {
    type: String,
    default: 'main'
  }
})

// 部分选项只在主配置区展示，避免弹层或侧栏重复出现高级配置。
const showFlag = computed(() => props.showPosition === 'main')
const { curComponent } = toRefs(props)
// 切换组件时临时隐藏默认值编辑器，避免旧组件状态残留。
const loadingDefault = ref(true)
// 监听当前查询组件变化并重新挂载默认值编辑器，确保内部缓存被清理。
watch(
  () => curComponent.value.id,
  val => {
    if (!val) return
    loadingDefault.value = false
    nextTick(() => {
      loadingDefault.value = true
    })
  },
  { immediate: true }
)
// 动态时间自定义偏移只允许选择不高于当前时间粒度的单位。
const relativeToCurrentTypeList = computed(() => {
  if (!curComponent.value) return []
  // 单日期和日期范围分别按自己的粒度字段裁剪可选偏移单位。
  let index = ['year', 'month', 'date', 'datetime'].indexOf(curComponent.value.timeGranularity) + 1
  if (+curComponent.value.displayType === 7) {
    index =
      ['yearrange', 'monthrange', 'daterange', 'datetimerange'].indexOf(
        curComponent.value.timeGranularityMultiple
      ) + 1
  }
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
      value: 'date'
    }
  ].slice(0, index)
})

// 单日期动态时间快捷选项随时间粒度变化，避免展示无效的年/月/日选项。
const relativeToCurrentList = computed(() => {
  let list = []
  if (!curComponent.value) return list
  switch (curComponent.value.timeGranularity) {
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
          label: t('dynamic_time.endOfMonth'),
          value: 'monthEnd'
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

// 日期范围动态时间快捷选项按范围粒度区分，兼容年、月、日期和日期时间范围。
const relativeToCurrentListRange = computed(() => {
  let list = []
  if (!curComponent.value) return list
  switch (curComponent.value.timeGranularityMultiple) {
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
          label: t('dynamic_time.tquarter'),
          value: 'thisQuarter'
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
          label: t('dynamic_time.cweek'),
          value: 'thisWeek'
        },
        {
          label: t('cron.this_month'),
          value: 'thisMonth'
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

// 下拉选项来自自定义选项时才展示“首项默认值”，接口选项不保证稳定顺序。
const defaultValueFirstItemShow = computed(() => {
  const { displayType, optionValueSource } = curComponent.value
  return +displayType === 0 && optionValueSource === 1
})

// 自定义动态时间偏移方向，f 表示向前，b 表示向后。
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

// 当前默认值是否为动态时间配置，用于切换预览和相对时间表单。
const dynamicTime = computed(() => {
  const { displayType, timeType } = curComponent.value
  return timeType === 'dynamic' && [1, 7].includes(+displayType)
})

// 文本条件默认值支持精确匹配和模糊匹配两种操作符。
const operators = [
  {
    label: t('v_query.exact_match'),
    value: 'eq'
  },
  {
    label: t('v_query.fuzzy_match'),
    value: 'like'
  }
]

// 当前查询组件是否为多选，和组件配置分离以便子组件同步。
const multiple = ref(false)

// 切换单双选时同步 defaultValue/selectValue 结构，避免单值和数组混用。
const multipleChange = (val: boolean, isMultipleChange = false) => {
  if (isMultipleChange) {
    // 用户主动切换单双选时重置默认值，避免旧类型残留。
    curComponent.value.defaultValue = val ? [] : undefined
  }
  const { defaultValue } = curComponent.value
  if (Array.isArray(defaultValue)) {
    curComponent.value.selectValue = val ? defaultValue : undefined
  } else {
    curComponent.value.selectValue = val
      ? defaultValue !== undefined
        ? [defaultValue]
        : []
      : defaultValue
  }

  if (curComponent.value.field.fieldType === 1) {
    curComponent.value.multiple = val
    return
  }

  curComponent.value.multiple = val
}

// 子组件内部变更多选状态后回写到父组件局部状态。
const changeMultiple = val => {
  multiple.value = val
}
// 当前默认值输入组件实例用于调用子组件暴露的清理和刷新方法。
const inputCom = ref()
// 展示类型变化后通知子组件重置内部选项和默认值状态。
const displayTypeChange = () => {
  inputCom.value?.displayTypeChange?.()
}

// 外层关闭时同步关闭多选默认值下拉面板，避免浮层残留。
const mult = () => {
  inputCom.value?.mult?.handleClickOutside?.()
}

// 外层关闭时同步关闭单选默认值下拉面板，避免浮层残留。
const single = () => {
  inputCom.value?.single?.handleClickOutside?.()
}

defineExpose({
  multipleChange,
  handleDialogClick,
  changeMultiple,
  displayTypeChange,
  mult,
  single
})
</script>

<template>
  <!-- 文本条件支持双条件默认值，展示类型 8 使用独立结构保存操作符和值。 -->
  <div class="list-item top-item" v-if="curComponent.displayType === '8'" @click.stop>
    <div class="label">{{ t('dynamic_time.set_default') }}</div>
    <div class="value" :class="curComponent.hideConditionSwitching && 'hide-condition_switching'">
      <div class="condition-type">
        <el-select
          class="condition-value-select"
          v-if="!curComponent.hideConditionSwitching"
          popper-class="condition-value-select-popper"
          v-model="curComponent.defaultConditionValueOperatorF"
        >
          <el-option
            v-for="ele in operators"
            :key="ele.value"
            :label="ele.label"
            :value="ele.value"
          >
          </el-option>
        </el-select>
        <el-input class="condition-value-input" v-model="curComponent.defaultConditionValueF" />
        <div class="bottom-line"></div>
      </div>
      <div class="condition-type" v-if="[1, 2].includes(curComponent.conditionType)">
        <span class="condition-type-tip">{{
          curComponent.conditionType === 1 ? t('chart.and') : t('chart.or')
        }}</span>
        <el-select
          v-if="!curComponent.hideConditionSwitching"
          class="condition-value-select"
          popper-class="condition-value-select-popper"
          v-model="curComponent.defaultConditionValueOperatorS"
        >
          <el-option
            v-for="ele in operators"
            :key="ele.value"
            :label="ele.label"
            :value="ele.value"
          >
          </el-option>
        </el-select>
        <el-input class="condition-value-input" v-model="curComponent.defaultConditionValueS" />
        <div class="bottom-line next-line"></div>
      </div>
    </div>
  </div>
  <div class="list-item top-item" v-if="curComponent.displayType === '22'" @click.stop>
    <!-- 数值范围默认值需要显式开启，避免空最小值或最大值被误当作过滤边界。 -->
    <div class="label">
      <el-checkbox
        v-model="curComponent.defaultValueCheck"
        :label="t('dynamic_time.set_default')"
      />
    </div>
    <div class="setting-content" style="display: flex; align-items: center">
      <el-input-number
        :disabled="!curComponent.defaultValueCheck"
        :placeholder="t('system.the_minimum_value')"
        style="width: 192.5px"
        controls-position="right"
        v-model="curComponent.defaultNumValueStart"
      />
      <div class="num-value_line"></div>
      <el-input-number
        :placeholder="t('system.the_maximum_value')"
        style="width: 192.5px"
        controls-position="right"
        :disabled="!curComponent.defaultValueCheck"
        v-model="curComponent.defaultNumValueEnd"
      />
    </div>
  </div>
  <div
    v-if="!['1', '7', '8', '22'].includes(curComponent.displayType) && showFlag"
    class="list-item"
  >
    <div class="label">{{ t('v_query.option_type') }}</div>
    <div class="value">
      <!-- 单选和多选共用默认值组件，切换时同步 selectValue 的值类型。 -->
      <el-radio-group
        class="larger-radio"
        @change="val => multipleChange(val as boolean, true)"
        v-model="multiple"
      >
        <el-radio :label="false">{{ t('visualization.single_choice') }}</el-radio>
        <el-radio :label="true">{{ t('visualization.multiple_choice') }}</el-radio>
      </el-radio-group>
    </div>
  </div>
  <div v-if="['7', '1'].includes(curComponent.displayType) && showFlag" class="list-item">
    <!-- 时间范围限制属于查询条件约束，不等同于默认值，因此独立放在默认值开关之前。 -->
    <div class="label">
      <el-checkbox v-model="curComponent.setTimeRange" :label="t('v_query.time_filter_range')" />
    </div>
    <div class="setting-content">
      <el-popover
        :show-arrow="false"
        popper-class="range-filter-time"
        placement="bottom-start"
        :width="452"
        :visible="visiblePopover"
        :offset="4"
      >
        <template #reference>
          <el-button
            :disabled="!curComponent.setTimeRange"
            @click="handleVisiblePopover($event)"
            text
            style="margin-left: -4px"
          >
            <template #icon>
              <Icon name="icon_admin_outlined"><icon_admin_outlined class="svg-icon" /></Icon>
            </template>
            {{ t('dynamic_time.set') }}
          </el-button>
        </template>
        <RangeFilterTime
          v-if="curComponent.displayType === '7'"
          :timeRange="curComponent.timeRange"
          :timeGranularityMultiple="curComponent.timeGranularityMultiple"
        />
        <FilterTime
          v-else
          :timeRange="curComponent.timeRange"
          :timeGranularity="curComponent.timeGranularity"
        />
      </el-popover>
      <span
        v-if="
          curComponent.timeRange &&
          (curComponent.timeRange.intervalType !== 'none' || curComponent.timeRange.dynamicWindow)
        "
        class="config-flag range-filter-time-flag"
        >{{ t('v_query.configured') }}</span
      >
    </div>
  </div>
  <div
    class="list-item"
    v-if="+curComponent.displayType === 0 && curComponent.optionValueSource !== 1 && showFlag"
  >
    <div class="label">
      <el-tooltip
        effect="dark"
        :content="t('v_query.is_not_supported')"
        :disabled="!curComponent.parametersCheck"
        placement="top"
      >
        <el-checkbox
          :disabled="curComponent.parametersCheck"
          v-model="curComponent.showEmpty"
          :label="t('v_query.contains_empty_data')"
        />
      </el-tooltip>
    </div>
  </div>
  <div v-if="!['8', '22'].includes(curComponent.displayType)" class="list-item">
    <div class="label">
      <el-checkbox
        v-model="curComponent.defaultValueCheck"
        :label="t('dynamic_time.set_default')"
      />
    </div>
    <div
      class="setting-content"
      v-if="curComponent.defaultValueCheck && ['1', '7'].includes(curComponent.displayType)"
    >
      <!-- 日期默认值支持固定时间和相对当前时间，两类配置共享下方预览区域。 -->
      <div class="setting">
        <el-radio-group @change="handleTimeTypeChange" v-model="curComponent.timeType">
          <el-radio value="fixed">{{ t('dynamic_time.fix') }}</el-radio>
          <el-radio value="dynamic">{{ t('dynamic_time.dynamic') }}</el-radio>
        </el-radio-group>
      </div>
      <template v-if="dynamicTime && curComponent.displayType === '1'">
        <!-- 单日期动态默认值先选择快捷规则，自定义规则再补充偏移量和时间点。 -->
        <div class="setting">
          <div :title="t('dynamic_time.relative')" class="setting-label ellipsis">
            {{ t('dynamic_time.relative') }}
          </div>
          <div class="setting-value select">
            <el-select @focus="handleDialogClick" v-model="curComponent.relativeToCurrent">
              <el-option
                v-for="item in relativeToCurrentList"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
        </div>
        <div class="setting" v-if="curComponent.relativeToCurrent === 'custom'">
          <div
            class="setting-input"
            :class="curComponent.timeGranularity === 'datetime' && 'with-date'"
          >
            <el-input-number
              step-strictly
              v-model="curComponent.timeNum"
              :min="0"
              controls-position="right"
            />
            <el-select @focus="handleDialogClick" v-model="curComponent.relativeToCurrentType">
              <el-option
                v-for="item in relativeToCurrentTypeList"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-select @focus="handleDialogClick" v-model="curComponent.around">
              <el-option
                v-for="item in aroundList"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-time-picker
              v-if="curComponent.timeGranularity === 'datetime'"
              v-model="curComponent.arbitraryTime"
            />
          </div>
        </div>
      </template>
      <template v-else-if="dynamicTime && curComponent.displayType === '7'">
        <!-- 日期范围动态默认值同时维护开始和结束规则，确保范围两端可独立偏移。 -->
        <div class="setting">
          <div class="setting-label">{{ t('dynamic_time.relative') }}</div>
          <div class="setting-value select">
            <el-select @focus="handleDialogClick" v-model="curComponent.relativeToCurrentRange">
              <el-option
                v-for="item in relativeToCurrentListRange"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
        </div>
        <div
          class="setting"
          v-if="curComponent.relativeToCurrentRange === 'custom'"
          :class="
            ['yearrange', 'monthrange', 'daterange'].includes(
              curComponent.timeGranularityMultiple
            ) && 'is-year-month-range'
          "
        >
          <!-- 日期范围自定义默认值分别保存开始和结束偏移。 -->
          <div class="setting-label">{{ t('datasource.start_time') }}</div>
          <div class="setting-input with-date range">
            <el-input-number
              step-strictly
              v-model="curComponent.timeNum"
              :min="0"
              controls-position="right"
            />
            <el-select @focus="handleDialogClick" v-model="curComponent.relativeToCurrentType">
              <el-option
                v-for="item in relativeToCurrentTypeList"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-select @focus="handleDialogClick" v-model="curComponent.around">
              <el-option
                v-for="item in aroundList"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-time-picker v-model="curComponent.arbitraryTime" />
          </div>
        </div>
        <div
          class="setting"
          v-if="curComponent.relativeToCurrentRange === 'custom'"
          :class="
            ['yearrange', 'monthrange', 'daterange'].includes(
              curComponent.timeGranularityMultiple
            ) && 'is-year-month-range'
          "
        >
          <div class="setting-label">{{ t('datasource.end_time') }}</div>
          <div class="setting-input with-date range">
            <el-input-number
              v-model="curComponent.timeNumRange"
              :min="0"
              step-strictly
              controls-position="right"
            />
            <el-select @focus="handleDialogClick" v-model="curComponent.relativeToCurrentTypeRange">
              <el-option
                v-for="item in relativeToCurrentTypeList"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-select @focus="handleDialogClick" v-model="curComponent.aroundRange">
              <el-option
                v-for="item in aroundList"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-time-picker v-model="curComponent.arbitraryTimeRange" />
          </div>
        </div>
      </template>
    </div>
    <div
      v-if="curComponent.defaultValueCheck && loadingDefault"
      class="parameters"
      :class="dynamicTime && 'setting'"
    >
      <!-- 默认值编辑组件按 displayType 动态挂载，切换组件时通过 loadingDefault 清理内部缓存。 -->
      <div class="setting-label" v-if="dynamicTime">{{ t('template_manage.preview') }}</div>
      <div v-if="defaultValueFirstItemShow" class="first-item" style="margin-bottom: 8px">
        <el-checkbox v-model="curComponent.defaultValueFirstItem">{{
          $t('common.first_item')
        }}</el-checkbox>
      </div>
      <div :class="dynamicTime ? 'setting-value' : 'w100'">
        <component
          :config="curComponent as any"
          isConfig
          ref="inputCom"
          :is="filterTypeCom"
        ></component>
      </div>
    </div>
  </div>
</template>

<style lang="less" scoped>
.list-item {
  // 默认值配置嵌入查询条件设置面板，采用左右标签加可换行内容区的紧凑布局。
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10.5px;
  flex-wrap: wrap;

  .setting-content {
    width: 100%;
    padding-left: 24px;

    .num-value_line {
      background: #1f2329;
      width: 12px;
      height: 1px;
      margin: 0 8px;
    }
  }

  &.top-item {
    .label {
      margin-bottom: auto;
      padding-top: 5.5px;
    }
  }
  .label {
    width: 105px;
    color: #1f2329;
  }

  .value {
    .ed-select {
      width: 321px;
    }
    width: 321px;
    .value {
      margin-top: 8px;
      &:first-child {
        margin-top: -0.5px;
      }
      .search-field {
        width: 257px;
      }

      .sort-field {
        width: 176px;
      }

      .label {
        line-height: 32px;
        font-size: 14px;
        margin-right: 8px;
      }
    }
  }

  .value {
    width: 321px;
    .condition-type {
      // 文本双条件默认值使用无边框输入，底线模拟旧版配置样式。
      margin-top: 3px !important;
      display: flex;
      position: relative;
      .ed-input__wrapper {
        border: none;
        border-radius: 0;
        box-shadow: none;
        height: 26px;
        font-family: var(--crest-custom_font, 'PingFang');
        word-wrap: break-word;
        text-align: left;
        color: rgba(0, 0, 0, 0.65);
        list-style: none;
        user-select: none;
        cursor: pointer;
        line-height: 26px;
        box-sizing: border-box;
        max-width: 100%;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        opacity: 1;
      }

      .ed-select .ed-input.is-focus .ed-input__wrapper,
      .ed-select:hover:not(.ed-select--disabled) .ed-input__wrapper,
      .ed-select .ed-input__wrapper.is-focus {
        box-shadow: none !important;
      }

      .ed-select {
        width: 120px;
        .ed-input__wrapper {
          padding: 0;
        }
      }

      .condition-type-tip {
        font-size: 12px;
        color: #646a73;
        line-height: 26px;
        margin-right: 8px;
        width: 35px;
      }

      .bottom-line {
        box-sizing: border-box;
        height: 1px;
        background-color: #000;
        opacity: 0.3;
        position: absolute;
        right: 5px;
        bottom: 3px;
        width: 220px;
        z-index: 10;

        &.next-line {
          width: 206px;
        }
      }
      &:first-child {
        margin-top: -0.5px;
      }
    }

    &.hide-condition_switching {
      .bottom-line {
        width: 307px !important;

        &.next-line {
          width: 288px !important;
        }
      }
    }
  }
  .value {
    .sort-field {
      width: 240px;
    }
    .sort-type {
      width: 73px;
      margin-left: 8px;
    }
  }
  .parameters {
    // 固定默认值和动态时间预览共用参数区，宽度按日期选择器最大形态预留。
    margin-left: auto;
    margin-top: 8px;

    .w100 {
      width: 100%;
    }
    .ed-select,
    .ed-date-editor,
    .ed-date-editor--datetime .ed-input__wrapper,
    .ed-select-v2 {
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
      .ed-date-editor {
        width: 325px !important;
      }
    }
    margin-left: auto;
    display: flex;
    justify-content: space-between;
    align-items: center;
    .setting-label {
      width: 80px;
      margin-right: 8px;
    }

    .setting-value {
      margin: 8px 0;
      &.select {
        margin-top: 0;
        .ed-select {
          width: 325px;
        }
      }
    }

    .setting-input {
      display: flex;
      padding-left: 86px;
      justify-content: flex-end;
      align-items: center;
      width: 100%;
      .ed-select {
        --ed-select-width: 100px;
      }

      &.range {
        padding-left: 0px;
      }
      & > div + div {
        margin-left: 8px;
      }

      &.with-date {
        .ed-input-number {
          width: 71px;
        }
        .ed-select {
          width: 62px;
        }

        .ed-date-editor.ed-input {
          width: 106px;
        }
      }
    }

    &.is-year-month-range {
      // 年、月和日期范围不需要单独时间选择器，只保留偏移量和单位。
      .setting-input {
        &.with-date {
          .ed-input-number,
          .ed-select {
            width: 103px;
          }
        }
        .ed-date-editor.ed-input {
          display: none;
        }
      }
    }
  }
}
</style>
<style lang="less">
.condition-value-select-popper {
  .ed-select-dropdown__item.selected::after {
    display: none;
  }
}
</style>
