<script lang="tsx" setup>
import icon_info_filled from '@/assets/svg/icon_info_filled.svg'
import icon_deleteTrash_outlined from '@/assets/svg/icon_delete-trash_outlined.svg'
import icon_add_outlined from '@/assets/svg/icon_add_outlined.svg'
import { PropType, reactive } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { COLOR_PANEL } from '../../../util/chart'
import { fieldType } from '@/utils/attr'
import { iconFieldMap } from '@/components/icon-group/field-list'
import { cloneDeep } from 'lodash-es'
import {
  transDateFormat,
  transDatePickerType
} from '@/views/chart/components/editor/util/DateFormatUtil'

const { t } = useI18n()

// 接收当前图表和已有表格阈值配置
const props = defineProps({
  chart: {
    type: Object as PropType<ChartObj>,
    required: true
  },
  threshold: {
    type: Array as PropType<TableThreshold[]>,
    required: true
  }
})

// 向父级同步表格阈值配置
const emit = defineEmits(['onTableThresholdChange'])

const thresholdCondition = {
  // 条件默认值同时覆盖固定值和动态字段两种模式，新增规则时会按字段类型再修正。
  term: '',
  field: '0',
  value: '0',
  color: '#ff0000ff',
  backgroundColor: '#ffffff00',
  min: '0',
  max: '1',
  type: 'fixed',
  dynamicField: { summary: 'value' },
  dynamicMinField: { summary: 'value' },
  dynamicMaxField: { summary: 'value' },
  target: 'self',
  targetFieldId: null
}
const textOptions = [
  {
    label: '',
    options: [
      {
        value: 'eq',
        label: t('chart.filter_eq')
      },
      {
        value: 'not_eq',
        label: t('chart.filter_not_eq')
      }
    ]
  },
  {
    label: '',
    options: [
      {
        value: 'like',
        label: t('chart.filter_like')
      },
      {
        value: 'not like',
        label: t('chart.filter_not_like')
      }
    ]
  },
  {
    label: '',
    options: [
      {
        value: 'null',
        label: t('chart.filter_null')
      },
      {
        value: 'not_null',
        label: t('chart.filter_not_null')
      }
    ]
  }
]
const dateOptions = [
  {
    label: '',
    options: [
      {
        value: 'eq',
        label: t('chart.filter_eq')
      },
      {
        value: 'not_eq',
        label: t('chart.filter_not_eq')
      }
    ]
  },
  {
    label: '',
    options: [
      {
        value: 'lt',
        label: t('chart.filter_lt')
      },
      {
        value: 'gt',
        label: t('chart.filter_gt')
      }
    ]
  },
  {
    label: '',
    options: [
      {
        value: 'le',
        label: t('chart.filter_le')
      },
      {
        value: 'ge',
        label: t('chart.filter_ge')
      }
    ]
  },
  {
    label: '',
    options: [
      {
        value: 'null',
        label: t('chart.filter_null')
      },
      {
        value: 'not_null',
        label: t('chart.filter_not_null')
      }
    ]
  }
]
const valueOptions = [
  {
    label: '',
    options: [
      {
        value: 'eq',
        label: t('chart.filter_eq')
      },
      {
        value: 'not_eq',
        label: t('chart.filter_not_eq')
      }
    ]
  },
  {
    label: '',
    options: [
      {
        value: 'lt',
        label: t('chart.filter_lt')
      },
      {
        value: 'gt',
        label: t('chart.filter_gt')
      }
    ]
  },
  {
    label: '',
    options: [
      {
        value: 'le',
        label: t('chart.filter_le')
      },
      {
        value: 'ge',
        label: t('chart.filter_ge')
      }
    ]
  },
  {
    label: '',
    options: [
      {
        value: 'between',
        label: t('chart.filter_between')
      }
    ]
  },
  {
    label: '',
    options: [
      {
        value: 'null',
        label: t('chart.filter_null')
      },
      {
        value: 'not_null',
        label: t('chart.filter_not_null')
      }
    ]
  }
]
const predefineColors = COLOR_PANEL

const targetOptions = [
  { label: t('chart.self'), value: 'self' },
  { label: t('chart.total_row'), value: 'total_row' },
  { label: t('chart.custom'), value: 'custom' }
]

// 维护阈值编辑弹窗的字段和条件状态
const state = reactive<any>({
  thresholdArr: [] as TableThreshold[],
  fields: [],
  thresholdObj: {
    fieldId: '',
    field: {},
    conditions: []
  } as TableThreshold
})

// 初始化阈值配置副本和可选字段
const init = () => {
  state.thresholdArr = JSON.parse(JSON.stringify(props.threshold)) as TableThreshold[]
  initFields()
}
// 根据字段类型初始化可用条件选项
const initOptions = (item, fieldObj) => {
  if (fieldObj) {
    // 主字段类型决定条件操作符集合，切换字段后必须清空已选操作符。
    if ([0, 5, 7].includes(fieldObj.fieldType)) {
      item.options = JSON.parse(JSON.stringify(textOptions))
    } else if (fieldObj.fieldType === 1) {
      item.options = JSON.parse(JSON.stringify(dateOptions))
    } else {
      item.options = JSON.parse(JSON.stringify(valueOptions))
    }
    item.conditions &&
      item.conditions.forEach(ele => {
        ele.term = ''
      })
  }
}
// 按表格类型汇总阈值可选择字段
const initFields = () => {
  let fields = []
  if (props.chart.type === 'table-info') {
    fields = JSON.parse(JSON.stringify(props.chart.xAxis))
  } else if (props.chart.type === 'table-pivot') {
    const xAxis = JSON.parse(JSON.stringify(props.chart.xAxis))
    const xAxisExt = JSON.parse(JSON.stringify(props.chart.xAxisExt))
    const yAxis = JSON.parse(JSON.stringify(props.chart.yAxis))
    fields = [...xAxis, ...xAxisExt, ...yAxis]
  } else {
    const xAxis = JSON.parse(JSON.stringify(props.chart.xAxis))
    const yAxis = JSON.parse(JSON.stringify(props.chart.yAxis))
    fields = [...xAxis, ...yAxis]
  }
  state.fields.splice(0, state.fields.length, ...fields)
  // 字段列表变化时清空失效阈值字段，避免保存已删除或隐藏字段的条件。
  let change = false
  state.thresholdArr.forEach(item => {
    const fieldItemObj = state.fields.filter(ele => ele.id === item.fieldId)
    if (fieldItemObj.length === 0) {
      change = true
      item.fieldId = null
    }
  })
  if (change) {
    changeThreshold()
  }
}
// 新增一个字段阈值配置
const addThreshold = () => {
  state.thresholdArr.push(JSON.parse(JSON.stringify(state.thresholdObj)))
  changeThreshold()
}
// 删除指定字段阈值配置
const removeThreshold = index => {
  state.thresholdArr.splice(index, 1)
  changeThreshold()
}

// 通知父级保存最新阈值配置
const changeThreshold = () => {
  emit('onTableThresholdChange', state.thresholdArr)
}

// 为字段阈值新增一条条件规则
const addConditions = item => {
  const newCondition = JSON.parse(JSON.stringify(thresholdCondition))
  if (item.field.dateStyle === 'H_m_s') {
    newCondition.value = '00:00:00'
  }
  // 新条件默认继承表格单元格底色，用户未设置背景时保持原表格视觉。
  const tableCell = props.chart?.customAttr?.tableCell
  if (tableCell) {
    newCondition.backgroundColor = cloneDeep(tableCell.tableItemBgColor)
  }
  item.conditions.push(newCondition)
  changeThreshold()
}
// 删除字段阈值中的指定条件规则
const removeCondition = (item, index) => {
  item.conditions.splice(index, 1)
  changeThreshold()
}

// 绑定阈值配置中选择的字段对象
const addField = item => {
  // 根据字段 ID 回填字段快照
  if (state.fields && state.fields.length > 0) {
    state.fields.forEach(ele => {
      if (item.fieldId === ele.id) {
        item.field = JSON.parse(JSON.stringify(ele))
        initOptions(item, item.field)
      }
      if (item.dynamicField?.fieldId === ele.id) {
        item.dynamicField.field = JSON.parse(JSON.stringify(ele))
        initOptions(item, item.dynamicField.field)
      }
      if (item.dynamicMinField?.fieldId === ele.id) {
        item.dynamicMinField.field = JSON.parse(JSON.stringify(ele))
        initOptions(item, item.dynamicMinField.field)
      }
      if (item.dynamicMaxField?.fieldId === ele.id) {
        item.dynamicMaxField.field = JSON.parse(JSON.stringify(ele))
        initOptions(item, item.dynamicMaxField.field)
      }
    })
  }
  // 保存字段快照后立即通知父级，保证关闭弹窗前也能拿到最新阈值结构。
  changeThreshold()
}

const fieldOptions = [
  { label: t('chart.field_fixed'), value: 'fixed' },
  { label: t('chart.field_dynamic'), value: 'dynamic' }
]
const dynamicSummaryOptions = [
  {
    id: 'value',
    name: t('chart.field') + t('chart.drag_block_label_value')
  },
  {
    id: 'avg',
    name: t('chart.avg') + t('chart.drag_block_label_value')
  },
  {
    id: 'max',
    name: t('chart.max')
  },
  {
    id: 'min',
    name: t('chart.min')
  }
]

// 根据主字段类型筛选动态条件可用字段
const getConditionsFields = (fieldItem, conditionItem, conditionItemField) => {
  const fieldItemObj = state.fields.filter(ele => ele.id === fieldItem.fieldId)

  if (
    fieldItemObj.length === 0 ||
    fieldItemObj[0]?.fieldType === undefined ||
    fieldItemObj[0]?.fieldType === null
  ) {
    fieldItem.fieldId = null
    conditionItem.fieldId = null
    conditionItemField.fieldId = null
    return []
  }

  // 动态阈值只允许和主字段可比较的字段参与，避免渲染时出现跨类型比较。
  const fieldItemDeType = fieldItemObj[0]?.fieldType
  const result =
    state.fields.filter(item => {
      // 文本、时间、布尔和地理字段只允许同类型动态比较，避免不同类型值被错误比较。
      if ([0, 1, 4, 5].includes(fieldItemDeType)) {
        return item.fieldType === fieldItemDeType
      } else if ([2, 3].includes(fieldItemDeType)) {
        // 数值字段允许整型和浮点互相比较，和表格指标计算口径一致。
        return item.fieldType === 2 || item.fieldType === 3
      } else {
        return false
      }
    }) ?? []
  if (!result.find(ele => ele.id === conditionItemField.fieldId)) {
    conditionItemField.fieldId = result[0]?.id
    addField(conditionItem)
  }
  return result
}

// 根据字段类型限制动态汇总方式
const getDynamicSummaryOptions = itemId => {
  const fieldType = state.fields.filter(ele => ele.id === itemId)?.[0]?.fieldType
  if (fieldType === 1) {
    // 时间字段不支持平均值汇总，避免日期值被转换成无业务含义的数值均值。
    return dynamicSummaryOptions.filter(ele => {
      return ele.id !== 'avg'
    })
  } else if (fieldType === 0 || fieldType === 5) {
    // 文本和地理字段只能取原值参与比较，不能执行数值型汇总。
    return dynamicSummaryOptions.filter(ele => {
      return ele.id === 'value'
    })
  } else {
    return dynamicSummaryOptions
  }
}

// 判断条件是否需要录入比较值
const isNotEmptyAndNull = item => {
  return !item.term.includes('null') && !item.term.includes('empty')
}

// 判断当前条件是否为区间比较
const isBetween = item => {
  return item.term === 'between'
}

// 判断当前条件是否使用动态字段
const isDynamic = item => {
  return item.type === 'dynamic'
}
// 动态条件切换时重置汇总方式
const changeConditionItemType = item => {
  if (item.type === 'dynamic') {
    // 从固定值切到动态字段时恢复默认汇总方式，避免复用旧字段的聚合配置。
    item.dynamicField.summary = 'value'
    item.dynamicMinField.summary = 'value'
    item.dynamicMaxField.summary = 'value'
  }
}
// 获取字段来源选项
const getFieldOptions = (_fieldItem?: TableThreshold) => {
  return fieldOptions
}

// 转换日期字段的选择器显示格式
const datePickerFormat = (fieldItem: { dateStyle: any; datePattern: any }) => {
  return transDateFormat(fieldItem.dateStyle, fieldItem.datePattern)
}

// 转换日期字段的选择器类型
const datePickerType = (fieldItem: { dateStyle: string }) => {
  return transDatePickerType(fieldItem.dateStyle)
}

init()
</script>

<template>
  <el-col>
    <div class="tip">
      <Icon name="icon_info_filled" class="icon-style"
        ><icon_info_filled class="svg-icon icon-style"
      /></Icon>
      <span style="padding-left: 10px">{{ t('chart.table_threshold_tip') }}</span>
    </div>

    <div @keydown.stop @keyup.stop style="max-height: 50vh; overflow-y: auto">
      <div
        v-for="(fieldItem, fieldIndex) in state.thresholdArr"
        :key="fieldIndex"
        class="field-item"
      >
        <el-row style="margin-top: 6px; align-items: center; justify-content: space-between">
          <el-form-item class="form-item">
            <el-select
              style="width: 181px"
              v-model="fieldItem.fieldId"
              @change="addField(fieldItem)"
            >
              <el-option
                class="series-select-option"
                v-for="fieldOption in state.fields"
                :key="fieldOption.id"
                :label="fieldOption.name"
                :value="fieldOption.id"
                :disabled="chart.type === 'table-info' && fieldOption.fieldType === 7"
              >
                <el-icon style="margin-right: 8px">
                  <Icon
                    ><component
                      :class="`field-icon-${
                        fieldType[[2, 3].includes(fieldOption.fieldType) ? 2 : 0]
                      }`"
                      class="svg-icon"
                      :is="iconFieldMap[fieldType[fieldOption.fieldType]]"
                    ></component
                  ></Icon>
                </el-icon>
                {{ fieldOption.name }}
              </el-option>
            </el-select>
          </el-form-item>

          <el-button
            class="circle-button m-icon-btn"
            text
            :style="{ float: 'right' }"
            @click="removeThreshold(fieldIndex)"
          >
            <el-icon size="20px" style="color: #646a73">
              <Icon name="icon_delete-trash_outlined"
                ><icon_deleteTrash_outlined class="svg-icon"
              /></Icon>
            </el-icon>
          </el-button>
        </el-row>

        <el-row :style="{ marginTop: '16px', borderTop: '1px solid #d5d6d8' }">
          <el-row
            v-for="(item, index) in fieldItem.conditions"
            :key="index"
            class="line-item"
            :gutter="12"
          >
            <el-col :span="!isNotEmptyAndNull(item) ? 11 : 3">
              <el-form-item class="form-item">
                <el-select v-model="item.term" @change="changeThreshold">
                  <el-option-group
                    v-for="(group, idx) in fieldItem.options"
                    :key="idx"
                    :label="group.label"
                  >
                    <el-option
                      v-for="opt in group.options"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </el-option-group>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col
              :span="2"
              v-if="isNotEmptyAndNull(item) && chart.type !== 'rich-text'"
              style="padding-left: 0 !important"
            >
              <el-form-item class="form-item">
                <el-select
                  v-model="item.type"
                  class="select-item"
                  @change="changeConditionItemType(item)"
                  style="width: 100%"
                >
                  <el-option
                    v-for="opt in getFieldOptions(fieldItem)"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <!-- 固定单值条件直接录入比较值，控件类型跟随主字段类型切换。 -->
            <el-col
              v-if="isNotEmptyAndNull(item) && !isBetween(item) && !isDynamic(item)"
              :span="6"
              style="text-align: center"
            >
              <el-form-item class="form-item">
                <el-input-number
                  v-model="item.value"
                  v-if="[2, 3].includes(fieldItem.field.fieldType)"
                  :placeholder="t('chart.drag_block_label_value')"
                  controls-position="right"
                  class="value-item"
                  clearable
                  @change="changeThreshold"
                />
                <el-date-picker
                  v-model="item.value"
                  v-else-if="
                    [1].includes(fieldItem.field.fieldType) && fieldItem.field.dateStyle !== 'H_m_s'
                  "
                  :type="datePickerType(fieldItem.field)"
                  :placeholder="t('chart.drag_block_label_value')"
                  :format="datePickerFormat(fieldItem.field)"
                  :value-format="datePickerFormat(fieldItem.field)"
                  size="default"
                  class="value-item"
                  @change="changeThreshold"
                  style="width: 100%"
                />
                <el-time-picker
                  v-model="item.value"
                  v-else-if="
                    [1].includes(fieldItem.field.fieldType) && fieldItem.field.dateStyle === 'H_m_s'
                  "
                  :placeholder="t('chart.drag_block_label_value')"
                  :format="datePickerFormat(fieldItem.field)"
                  :value-format="datePickerFormat(fieldItem.field)"
                  size="default"
                  class="value-item"
                  @change="changeThreshold"
                  style="width: 100%"
                />
                <el-input
                  v-model="item.value"
                  v-else
                  :placeholder="t('chart.drag_block_label_value')"
                  controls-position="right"
                  clearable
                  @change="changeThreshold"
                />
              </el-form-item>
            </el-col>
            <!-- 动态单值条件从同类型字段中选择比较字段。 -->
            <el-col v-if="isNotEmptyAndNull(item) && !isBetween(item) && isDynamic(item)" :span="3">
              <el-form-item class="form-item">
                <el-select
                  v-model="item.dynamicField.fieldId"
                  @change="addField(item)"
                  style="width: 100%"
                >
                  <el-option
                    class="series-select-option"
                    v-for="itemFieldOption in getConditionsFields(
                      fieldItem,
                      item,
                      item.dynamicField
                    )"
                    :key="itemFieldOption.id"
                    :label="itemFieldOption.name"
                    :value="itemFieldOption.id"
                    :disabled="chart.type === 'table-info' && itemFieldOption.fieldType === 7"
                  >
                    <el-icon style="margin-right: 8px">
                      <Icon
                        ><component
                          :class="`field-icon-${
                            fieldType[[2, 3].includes(itemFieldOption.fieldType) ? 2 : 0]
                          }`"
                          class="svg-icon"
                          :is="iconFieldMap[fieldType[itemFieldOption.fieldType]]"
                        ></component
                      ></Icon>
                    </el-icon>
                    {{ itemFieldOption.name }}
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <!-- 动态单值条件可选择原值、最大值、最小值等汇总方式。 -->
            <el-col
              v-if="isNotEmptyAndNull(item) && !isBetween(item) && isDynamic(item)"
              :span="3"
              style="text-align: center"
            >
              <el-form-item class="form-item">
                <el-select
                  :placeholder="t('chart.aggregation')"
                  v-model="item.dynamicField.summary"
                  @change="changeThreshold"
                  style="width: 100%"
                >
                  <el-option
                    v-for="opt in getDynamicSummaryOptions(item.dynamicField.fieldId)"
                    :key="opt.id"
                    :label="opt.name"
                    :value="opt.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <!-- 固定区间条件分别录入最小值和最大值。 -->
            <el-col
              v-if="isNotEmptyAndNull(item) && isBetween(item) && !isDynamic(item)"
              :span="2"
              style="text-align: center"
            >
              <el-form-item class="form-item">
                <el-input-number
                  v-model="item.min"
                  controls-position="right"
                  class="between-item"
                  :placeholder="t('chart.axis_value_min')"
                  clearable
                  @change="changeThreshold"
                />
              </el-form-item>
            </el-col>
            <el-col
              v-if="isBetween(item) && !isDynamic(item)"
              :span="2"
              style="margin-top: 4px; text-align: center"
            >
              <span style="margin: 0 -5px">
                ≤&nbsp;{{ t('chart.drag_block_label_value') }}&nbsp;≤
              </span>
            </el-col>
            <!-- 固定区间条件的最大值输入。 -->
            <el-col
              v-if="isNotEmptyAndNull(item) && isBetween(item) && !isDynamic(item)"
              :span="2"
              style="text-align: center"
            >
              <el-form-item class="form-item">
                <el-input-number
                  v-model="item.max"
                  controls-position="right"
                  class="between-item"
                  :placeholder="t('chart.axis_value_max')"
                  clearable
                  @change="changeThreshold"
                />
              </el-form-item>
            </el-col>

            <!-- 动态区间条件的最小值字段。 -->
            <el-col
              v-if="isNotEmptyAndNull(item) && isBetween(item) && isDynamic(item)"
              class="minField"
              :span="2"
            >
              <el-form-item class="form-item">
                <el-select v-model="item.dynamicMinField.fieldId" @change="addField(item)">
                  <el-option
                    class="series-select-option"
                    v-for="itemFieldOption in getConditionsFields(
                      fieldItem,
                      item,
                      item.dynamicMinField
                    )"
                    :key="itemFieldOption.id"
                    :label="itemFieldOption.name"
                    :value="itemFieldOption.id"
                    :disabled="chart.type === 'table-info' && itemFieldOption.fieldType === 7"
                  >
                    <el-icon style="margin-right: 8px">
                      <Icon
                        ><component
                          :class="`field-icon-${
                            fieldType[[2, 3].includes(itemFieldOption.fieldType) ? 2 : 0]
                          }`"
                          class="svg-icon"
                          :is="iconFieldMap[fieldType[itemFieldOption.fieldType]]"
                        ></component
                      ></Icon>
                    </el-icon>
                    {{ itemFieldOption.name }}
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <!-- 动态区间最小值字段的汇总方式。 -->
            <el-col
              v-if="isNotEmptyAndNull(item) && isBetween(item) && isDynamic(item)"
              class="minValue"
              :span="2"
              style="padding-left: 0 !important"
            >
              <el-form-item class="form-item">
                <el-select v-model="item.dynamicMinField.summary" @change="changeThreshold">
                  <el-option
                    v-for="opt in getDynamicSummaryOptions(item.dynamicMinField.fieldId)"
                    :key="opt.id"
                    :label="opt.name"
                    :value="opt.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col
              v-if="isBetween(item) && isDynamic(item)"
              class="term"
              :span="1"
              style="margin-top: 4px; text-align: center"
            >
              <span style="margin: 0 -5px">
                ≤&nbsp;{{ t('chart.drag_block_label_value') }}&nbsp;≤
              </span>
            </el-col>
            <!-- 动态区间条件的最大值字段。 -->
            <el-col
              v-if="isNotEmptyAndNull(item) && isBetween(item) && isDynamic(item)"
              class="maxField"
              :span="2"
            >
              <el-form-item class="form-item">
                <el-select v-model="item.dynamicMaxField.fieldId" @change="addField(item)">
                  <el-option
                    class="series-select-option"
                    v-for="itemFieldOption in getConditionsFields(
                      fieldItem,
                      item,
                      item.dynamicMaxField
                    )"
                    :key="itemFieldOption.id"
                    :label="itemFieldOption.name"
                    :value="itemFieldOption.id"
                    :disabled="chart.type === 'table-info' && itemFieldOption.fieldType === 7"
                  >
                    <el-icon style="margin-right: 8px">
                      <Icon
                        ><component
                          :class="`field-icon-${
                            fieldType[[2, 3].includes(itemFieldOption.fieldType) ? 2 : 0]
                          }`"
                          class="svg-icon"
                          :is="iconFieldMap[fieldType[itemFieldOption.fieldType]]"
                        ></component
                      ></Icon>
                    </el-icon>
                    {{ itemFieldOption.name }}
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <!-- 动态区间最大值字段的汇总方式。 -->
            <el-col
              v-if="isNotEmptyAndNull(item) && isBetween(item) && isDynamic(item)"
              class="maxValue"
              :span="2"
              style="padding-left: 0 !important"
            >
              <el-form-item class="form-item">
                <el-select v-model="item.dynamicMaxField.summary" @change="changeThreshold">
                  <el-option
                    v-for="opt in getDynamicSummaryOptions(item.dynamicMaxField.fieldId)"
                    :key="opt.id"
                    :label="opt.name"
                    :value="opt.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="item.target === 'custom' ? 3 : 5">
              <el-form-item class="form-item">
                <el-select
                  v-model="item.target"
                  style="width: 100%"
                  :placeholder="t('chart.apply_to')"
                  @change="changeThreshold"
                >
                  <el-option
                    v-for="opt in targetOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="2" v-if="item.target === 'custom'">
              <el-form-item class="form-item">
                <el-select
                  v-model="item.targetFieldId"
                  :placeholder="t('chart.field')"
                  style="width: 100%"
                  @change="changeThreshold"
                >
                  <el-option
                    class="series-select-option"
                    v-for="targetField in state.fields"
                    :key="targetField.id"
                    :label="targetField.name"
                    :value="targetField.id"
                  >
                    <el-icon style="margin-right: 8px">
                      <Icon
                        ><component
                          :class="`field-icon-${
                            fieldType[[2, 3].includes(targetField.fieldType) ? 2 : 0]
                          }`"
                          class="svg-icon"
                          :is="iconFieldMap[fieldType[targetField.fieldType]]"
                        ></component
                      ></Icon>
                    </el-icon>
                    {{ targetField.name }}
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>

            <el-col :span="2">
              <el-form-item class="form-item" :label="t('chart.textColor')">
                <el-color-picker
                  is-custom
                  v-model="item.color"
                  show-alpha
                  :trigger-width="54"
                  class="color-picker-style"
                  :predefine="predefineColors"
                  @change="changeThreshold"
                />
              </el-form-item>
            </el-col>
            <el-col :span="2">
              <el-form-item class="form-item" :label="t('chart.backgroundColor')">
                <el-color-picker
                  is-custom
                  size="default"
                  v-model="item.backgroundColor"
                  :trigger-width="54"
                  show-alpha
                  class="color-picker-style"
                  :predefine="predefineColors"
                  @change="changeThreshold"
                />
              </el-form-item>
            </el-col>
            <el-col :span="1">
              <div style="display: flex; align-items: center; justify-content: center">
                <el-button
                  class="circle-button m-icon-btn"
                  text
                  @click="removeCondition(fieldItem, index)"
                >
                  <el-icon size="20px" style="color: #646a73">
                    <Icon name="icon_delete-trash_outlined"
                      ><icon_deleteTrash_outlined class="svg-icon"
                    /></Icon>
                  </el-icon>
                </el-button>
              </div>
            </el-col>
          </el-row>
        </el-row>

        <el-button
          style="margin-top: 10px"
          class="circle-button"
          type="primary"
          text
          @click="addConditions(fieldItem)"
        >
          <template #icon>
            <Icon name="icon_add_outlined"><icon_add_outlined class="svg-icon" /></Icon>
          </template>
          {{ t('chart.add_style') }}
        </el-button>
      </div>
    </div>

    <el-button
      class="circle-button"
      text
      type="primary"
      style="margin-top: 10px"
      @click="addThreshold"
    >
      <template #icon>
        <Icon name="icon_add_outlined"><icon_add_outlined class="svg-icon" /></Icon>
      </template>
      {{ t('chart.add_condition') }}
    </el-button>
  </el-col>
</template>

<style lang="less" scoped>
.field-item {
  width: 100%;
  border-radius: 6px;
  padding: 10px 16px;
  margin-top: 10px;
  background: #f5f6f7;
}

.line-item {
  width: 100%;
  display: flex;
  justify-content: left;
  align-items: center;
  margin-top: 16px;
}

.form-item {
  height: 28px !important;
  :deep(.el-form-item__label) {
    font-size: 12px;
  }
}

span {
  font-size: 12px;
}

.value-item {
  position: relative;
  display: inline-block;
  width: 100% !important;
}

.between-item {
  position: relative;
  display: inline-block;
  width: 100% !important;
}

.select-item {
  position: relative;
  display: inline-block;
  width: 100% !important;
}

.el-select-dropdown__item {
  padding: 0 20px;
  font-size: 12px;
}

:deep(.color-picker-style) {
  cursor: pointer;
  z-index: 1003;
  height: 32px;
  line-height: 32px;
}
.color-title {
  color: #646a73;
  font-size: 14px;
  font-style: normal;
  font-weight: 400;
  line-height: 22px;
  padding: 0 8px;
}

.tip {
  font-size: 12px;
  background: #d6e2ff;
  border-radius: 6px;
  padding: 10px 20px;
  display: flex;
  align-items: center;
}

:deep(.ed-form-item) {
  margin-bottom: 0 !important;
}

.icon-style {
  width: 14px;
  height: 14px;
  color: var(--ed-color-primary);
}

.m-icon-btn {
  &:hover {
    background: rgba(31, 35, 41, 0.1) !important;
  }
  &:focus {
    background: rgba(31, 35, 41, 0.1) !important;
  }
  &:active {
    background: rgba(31, 35, 41, 0.2) !important;
  }
}

.series-select-option {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 0 11px;
}
</style>
