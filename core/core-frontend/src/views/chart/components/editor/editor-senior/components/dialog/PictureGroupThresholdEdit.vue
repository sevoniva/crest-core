<script lang="tsx" setup>
import icon_info_filled from '@/assets/svg/icon_info_filled.svg'
import icon_deleteTrash_outlined from '@/assets/svg/icon_delete-trash_outlined.svg'
import icon_add_outlined from '@/assets/svg/icon_add_outlined.svg'
import { PropType, reactive } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { fieldType } from '@/utils/attr'
import { iconFieldMap } from '@/components/icon-group/field-list'
import PictureItem from '@/custom-component/picture-group/PictureItem.vue'
import PictureOptionPrefix from '@/custom-component/picture-group/PictureOptionPrefix.vue'

const { t } = useI18n()

// 图片组阈值编辑器根据图表字段命中条件后切换展示图片，不直接修改图表数据。
const props = defineProps({
  chart: {
    type: Object as PropType<ChartObj>,
    required: true
  },
  element: {
    type: Object,
    default() {
      return {
        propValue: { urlList: [] }
      }
    }
  },
  threshold: {
    type: Array as PropType<TableThreshold[]>,
    required: true
  }
})

// 父组件负责保存阈值数组，本组件每次增删改后都同步完整配置。
const emit = defineEmits(['onTableThresholdChange'])

// 新增条件的默认结构保留颜色字段，兼容旧阈值模型中的表格样式条件。
const thresholdCondition = {
  term: 'eq',
  field: '0',
  value: '0',
  color: '#ff0000ff',
  backgroundColor: '#ffffff00',
  min: '0',
  max: '1'
}
// 文本字段只开放等值、包含、空值和默认分支，避免数值区间条件误用于字符串。
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
  },
  {
    label: '',
    options: [
      {
        value: 'default',
        label: '默认'
      }
    ]
  }
]
// 日期字段支持等值和大小比较，日期区间由外层筛选器负责处理。
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
        value: 'default',
        label: '默认'
      }
    ]
  }
]
// 数值字段支持大小比较和 between 区间，作为图片切换的主要判断条件。
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
        value: 'default',
        label: '默认'
      }
    ]
  }
]

// 状态保存可编辑阈值副本、字段候选和新增字段级阈值的初始结构。
const state = reactive<any>({
  thresholdArr: [] as TableThreshold[],
  fields: [],
  thresholdObj: {
    fieldId: '',
    field: {},
    conditions: []
  } as TableThreshold
})

// 初始化时复制父级阈值，避免编辑过程直接改写外部数组引用。
const init = () => {
  state.thresholdArr = JSON.parse(JSON.stringify(props.threshold)) as TableThreshold[]
  initFields()
}
// 字段类型决定操作符集合，切换字段后清空条件操作符，要求用户重新确认规则。
const initOptions = item => {
  if (item.field) {
    if ([0, 5, 7].includes(item.field.fieldType)) {
      item.options = JSON.parse(JSON.stringify(textOptions))
    } else if (item.field.fieldType === 1) {
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
// 不同表格图表的字段分布不同，透视表需要合并行、列和指标字段作为候选。
const initFields = () => {
  let fields = []
  if (props.chart.type === 'table-info') {
    // 明细表只使用维度字段作为图片切换条件，URL 字段在模板中禁用。
    fields = JSON.parse(JSON.stringify(props.chart.xAxis))
  } else if (props.chart.type === 'table-pivot') {
    // 透视表条件需要覆盖行维度、列维度和指标，保持与表格展示结构一致。
    const xAxis = JSON.parse(JSON.stringify(props.chart.xAxis))
    const xAxisExt = JSON.parse(JSON.stringify(props.chart.xAxisExt))
    const yAxis = JSON.parse(JSON.stringify(props.chart.yAxis))
    fields = [...xAxis, ...xAxisExt, ...yAxis]
  } else {
    // 普通图表按维度和指标合并生成条件字段候选。
    const xAxis = JSON.parse(JSON.stringify(props.chart.xAxis))
    const yAxis = JSON.parse(JSON.stringify(props.chart.yAxis))
    fields = [...xAxis, ...yAxis]
  }
  state.fields.splice(0, state.fields.length, ...fields)
}
// 新增字段级阈值时先插入空规则，再通知父组件刷新保存状态。
const addThreshold = () => {
  state.thresholdArr.push(JSON.parse(JSON.stringify(state.thresholdObj)))
  changeThreshold()
}
// 删除字段级阈值后立即同步，避免弹窗关闭时保留已删除规则。
const removeThreshold = index => {
  state.thresholdArr.splice(index, 1)
  changeThreshold()
}

// 所有条件变化都上报完整阈值数组，父组件不需要推断局部变更。
const changeThreshold = () => {
  emit('onTableThresholdChange', state.thresholdArr)
}

// 条件行使用默认模板深拷贝，避免多行共享同一对象引用。
const addConditions = item => {
  item.conditions.push(JSON.parse(JSON.stringify(thresholdCondition)))
  changeThreshold()
}
// 删除条件行后保留字段级阈值本身，允许用户继续为同一字段添加新条件。
const removeCondition = (item, index) => {
  item.conditions.splice(index, 1)
  changeThreshold()
}

// 选择字段后回填完整字段信息，并按字段类型刷新操作符候选。
const addField = item => {
  if (state.fields && state.fields.length > 0) {
    state.fields.forEach(ele => {
      if (item.fieldId === ele.id) {
        item.field = JSON.parse(JSON.stringify(ele))
        initOptions(item)
      }
    })
  }
  changeThreshold()
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
        <!-- 字段级阈值先选择匹配字段，再在其下维护多条图片切换规则。 -->
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
            :gutter="10"
          >
            <el-col :span="4">
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
            <!-- 非区间条件使用单值输入，命中后切换到所选图片。 -->
            <el-col
              v-if="
                !item.term.includes('null') &&
                !item.term.includes('empty') &&
                item.term !== 'between' &&
                !item.term.includes('default')
              "
              :span="10"
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

            <!-- 区间条件拆分最小值和最大值，适用于数值字段图片分档。 -->
            <el-col v-if="item.term === 'between'" :span="4" style="text-align: center">
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

            <el-col v-if="item.term === 'between'" :span="2" style="text-align: center">
              <span style="margin: 0 4px">
                ≤&nbsp;&nbsp;{{ t('chart.drag_block_label_value') }}&nbsp;&nbsp;≤
              </span>
            </el-col>

            <el-col v-if="item.term === 'between'" :span="4" style="text-align: center">
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
            <!-- 每条条件必须选择展示图片，渲染层按条件顺序匹配。 -->
            <div
              style="display: flex; align-items: center; justify-content: center; margin-left: 8px"
            >
              <div class="color-title">展示图片</div>
              <el-form-item class="form-item">
                <el-select
                  v-model="item.url"
                  @change="changeThreshold"
                  style="width: 181px"
                  popper-class="picture-group-select"
                >
                  <template v-if="item.url" #prefix>
                    <picture-option-prefix :url="item.url"></picture-option-prefix>
                  </template>
                  <el-option
                    v-for="urlInfo in element.propValue.urlList"
                    :key="urlInfo.url"
                    :label="urlInfo.name"
                    :value="urlInfo.url"
                  >
                    <picture-item
                      :active="item.url === urlInfo.url"
                      :url-info="urlInfo"
                    ></picture-item>
                  </el-option>
                </el-select>
              </el-form-item>
            </div>
            <div
              style="display: flex; align-items: center; justify-content: center; margin-left: 8px"
            >
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
  /* 每个字段级阈值独立成块，方便用户分辨字段和条件行的层级。 */
  width: 100%;
  border-radius: 6px;
  padding: 10px 16px;
  margin-top: 10px;
  background: #f5f6f7;
}

.line-item {
  /* 条件行横向排列操作符、取值、图片选择和删除按钮。 */
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

.color-picker-style {
  cursor: pointer;
  z-index: 1003;
  width: 28px;
  height: 28px;
}

.color-picker-style :deep(.el-color-picker__trigger) {
  width: 28px;
  height: 28px;
}

.color-title {
  /* 展示图片标签跟随条件行展示，提示用户当前规则命中后的图片结果。 */
  color: #646a73;
  font-size: 14px;
  font-style: normal;
  font-weight: 400;
  line-height: 22px;
  padding: 0 8px;
}

.tip {
  /* 顶部提示强调阈值只影响图片展示，不改变图表原始数据。 */
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
  /* 字段下拉项对齐字段图标和名称，和图表编辑器字段列表保持一致。 */
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 0 11px;
}
</style>

<style lang="less">
.picture-group-select {
  min-width: 50px !important;
  width: 304px;
  .ed-scrollbar__view {
    /* 图片候选以三列网格展示，减少图片较多时的纵向滚动距离。 */
    display: grid !important;
    grid-template-columns: repeat(3, 1fr) !important;
  }
  .ed-select-dropdown__item {
    height: 100px !important;
    text-align: center;
    padding: 0px 5px;
  }

  .ed-select-dropdown__item.selected::after {
    display: none;
  }

  .ed-select-dropdown__item.hover {
    background-color: rgba(0, 0, 0, 0) !important;
  }
  .ed-select-dropdown__item.selected {
    background-color: rgba(0, 0, 0, 0) !important;
  }
}
</style>
