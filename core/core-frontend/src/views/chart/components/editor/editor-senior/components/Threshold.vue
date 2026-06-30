<script lang="ts" setup>
import icon_edit_outlined from '@/assets/svg/icon_edit_outlined.svg'
import { PropType, reactive, watch } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { ElIcon, ElMessage } from 'element-plus-secondary'
import { DEFAULT_THRESHOLD } from '@/views/chart/components/editor/util/chart'
import TableThresholdEdit from '@/views/chart/components/editor/editor-senior/components/dialog/TableThresholdEdit.vue'
import TextLabelThresholdEdit from '@/views/chart/components/editor/editor-senior/components/dialog/TextLabelThresholdEdit.vue'
import TextThresholdEdit from '@/views/chart/components/editor/editor-senior/components/dialog/TextThresholdEdit.vue'
import LineThresholdEdit from '@/views/chart/components/editor/editor-senior/components/dialog/LineThresholdEdit.vue'
import { fieldType } from '@/utils/attr'
import { defaultsDeep } from 'lodash-es'
import { iconFieldMap } from '@/components/icon-group/field-list'
import PictureGroupThresholdEdit from '@/views/chart/components/editor/editor-senior/components/dialog/PictureGroupThresholdEdit.vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { imgUrlTrans } from '@/utils/imgUtils'
const dvMainStore = dvMainStoreWithOut()
const { curComponent } = storeToRefs(dvMainStore)
const { t } = useI18n()

// 阈值配置组件入参，包含当前图表、主题和可展示属性集合
const props = defineProps({
  chart: {
    type: Object as PropType<ChartObj>,
    required: true
  },
  themes: {
    type: String as PropType<EditorTheme>,
    default: 'dark'
  },
  propertyInner: {
    type: Array<string>
  }
})
// 判断当前阈值配置项是否在图表属性白名单内
const showProperty = prop => props.propertyInner?.includes(prop)

// 阈值配置变更事件
const emit = defineEmits(['onThresholdChange'])

// 监听外部阈值配置变化，重新初始化本地编辑副本
watch(
  () => props.chart.senior.threshold,
  () => {
    init()
  },
  { deep: true }
)

// 阈值编辑状态，分别缓存文本、指标、表格和折线阈值弹窗数据
const state = reactive({
  thresholdForm: {} as ChartThreshold,
  // 各弹窗维护独立数组副本，确认保存前不直接改写 thresholdForm。
  editTextLabelThresholdDialog: false,
  textThresholdArr: [],
  editLabelThresholdDialog: false,
  thresholdArr: [],
  editTableThresholdDialog: false,
  tableThresholdArr: [],
  editLineThresholdDialog: false,
  lineThresholdArr: []
})

// 从图表高级配置初始化阈值表单和各弹窗编辑数组
const init = () => {
  const chart = JSON.parse(JSON.stringify(props.chart))
  if (chart.senior) {
    const senior = chart.senior
    if (senior.threshold) {
      // 使用默认阈值补齐历史配置，防止旧图表缺少新字段时弹窗报错。
      state.thresholdForm = defaultsDeep(senior.threshold, DEFAULT_THRESHOLD)
    }
    // 弹窗数据深拷贝自表单，用户取消时不会影响已保存阈值。
    state.textThresholdArr = JSON.parse(JSON.stringify(state.thresholdForm.textLabelThreshold))
    state.thresholdArr = JSON.parse(JSON.stringify(state.thresholdForm.labelThreshold))
    state.tableThresholdArr = JSON.parse(JSON.stringify(state.thresholdForm.tableThreshold))
    state.lineThresholdArr = JSON.parse(JSON.stringify(state.thresholdForm.lineThreshold ?? []))
  }
}
// 将当前阈值表单提交给父组件
const changeThreshold = () => {
  // 父组件统一保存高级阈值配置并触发图表重绘。
  emit('onThresholdChange', state.thresholdForm)
}
// 校验仪表盘或水波图的百分比分割阈值
const changeSplitThreshold = (threshold: string) => {
  // 分割阈值只能填写 0 到 100 之间的递增数字序列
  if (threshold) {
    const regex = /^(\d+(?:\.\d+)?)(,\d+(?:\.\d+)?)*$/
    if (!regex.test(threshold)) {
      ElMessage.error(t('chart.gauge_threshold_format_error'))
      return
    }
    const arr = threshold.split(',')
    for (let i = 0; i < arr.length; i++) {
      const ele = arr[i]
      if (parseFloat(ele) <= 0 || parseFloat(ele) >= 100) {
        ElMessage.error(t('chart.gauge_threshold_format_error'))
        return
      }
      if (i > 0) {
        if (parseFloat(ele) <= parseFloat(arr[i - 1])) {
          ElMessage.error(t('chart.gauge_threshold_compare_error'))
          return
        }
      }
    }
  }
  changeThreshold()
}
// 打开指标标签阈值编辑弹窗
const editLabelThreshold = () => {
  state.editLabelThresholdDialog = true
}
// 关闭指标标签阈值编辑弹窗
const closeLabelThreshold = () => {
  state.editLabelThresholdDialog = false
}
// 校验并保存指标标签阈值配置
const changeLabelThreshold = () => {
  // 指标标签阈值必须配置表达式，并按表达式类型校验数值边界
  for (let i = 0; i < state.thresholdArr.length; i++) {
    const ele = state.thresholdArr[i]
    if (ele.term === undefined || ele.term === '') {
      ElMessage.error(t('chart.exp_can_not_empty'))
      return
    }
    if (ele.term === 'between') {
      if (ele.min === undefined || ele.max === undefined) {
        ElMessage.error(t('chart.value_can_not_empty'))
        return
      }
      if (parseFloat(ele.min).toString() === 'NaN' || parseFloat(ele.max).toString() === 'NaN') {
        ElMessage.error(t('chart.value_error'))
        return
      }
      if (parseFloat(ele.min) > parseFloat(ele.max)) {
        ElMessage.error(t('chart.value_min_max_invalid'))
        return
      }
    } else {
      if (ele.value === undefined) {
        ElMessage.error(t('chart.value_can_not_empty'))
        return
      }
      if (parseFloat(ele.value).toString() === 'NaN') {
        ElMessage.error(t('chart.value_error'))
        return
      }
    }
  }
  state.thresholdForm.labelThreshold = JSON.parse(JSON.stringify(state.thresholdArr))
  // 校验通过后才覆盖正式表单，避免弹窗中的半成品规则进入图表配置。
  changeThreshold()
  closeLabelThreshold()
}
// 同步指标标签阈值弹窗编辑结果
const thresholdChange = val => {
  state.thresholdArr = val
}

// 打开文本标签阈值编辑弹窗
const editTextLabelThreshold = () => {
  state.editTextLabelThresholdDialog = true
}
// 关闭文本标签阈值编辑弹窗
const closeTextLabelThreshold = () => {
  state.editTextLabelThresholdDialog = false
}
// 校验并保存文本标签阈值配置
const changeTextLabelThreshold = () => {
  // 文本标签阈值必须配置表达式和值
  for (let i = 0; i < state.textThresholdArr.length; i++) {
    const ele = state.textThresholdArr[i]
    if (!ele.term || ele.term === '') {
      ElMessage.error(t('chart.exp_can_not_empty'))
      return
    }
    if (!ele.value) {
      ElMessage.error(t('chart.value_can_not_empty'))
      return
    }
  }
  state.thresholdForm.textLabelThreshold = JSON.parse(JSON.stringify(state.textThresholdArr))
  // 文本卡阈值只影响文字颜色，不参与数值字段校验。
  changeThreshold()
  closeTextLabelThreshold()
}
// 同步文本标签阈值弹窗编辑结果
const thresholdTextChange = val => {
  state.textThresholdArr = val
}

// 同步表格阈值弹窗编辑结果
const tableThresholdChange = val => {
  state.tableThresholdArr = val
}
// 打开表格阈值编辑弹窗
const editTableThreshold = () => {
  state.editTableThresholdDialog = true
}
// 关闭表格阈值编辑弹窗
const closeTableThreshold = () => {
  state.editTableThresholdDialog = false
}
// 校验并保存表格阈值配置
const changeTableThreshold = () => {
  // 表格阈值按字段分组校验条件，动态条件需要配置参照字段
  for (let i = 0; i < state.tableThresholdArr.length; i++) {
    const field = state.tableThresholdArr[i]
    if (!field.fieldId) {
      ElMessage.error(t('chart.field_can_not_empty'))
      return
    }
    if (!field.conditions || field.conditions.length === 0) {
      ElMessage.error(t('chart.conditions_can_not_empty'))
      return
    }
    for (let j = 0; j < field.conditions.length; j++) {
      const ele = field.conditions[j]
      if (props.chart.type === 'picture-group' && !ele.url) {
        // 图片组复用表格阈值结构，但每条命中规则必须绑定展示图片。
        ElMessage.error(t('visualization.img_can_not_null'))
        return
      }
      if (!ele.term || ele.term === '') {
        ElMessage.error(t('chart.exp_can_not_empty'))
        return
      }
      if (ele.type !== 'dynamic') {
        // 固定阈值直接校验当前输入值，数值字段额外校验数字合法性。
        if (ele.term === 'between') {
          if (
            !ele.term.includes('null') &&
            !ele.term.includes('empty') &&
            (ele.min === '' || ele.max === '')
          ) {
            ElMessage.error(t('chart.value_can_not_empty'))
            return
          }
          if (
            (field.field.fieldType === 2 ||
              field.field.fieldType === 3 ||
              field.field.fieldType === 4) &&
            (parseFloat(ele.min).toString() === 'NaN' || parseFloat(ele.max).toString() === 'NaN')
          ) {
            ElMessage.error(t('chart.value_error'))
            return
          }
          if (
            (field.field.fieldType === 2 ||
              field.field.fieldType === 3 ||
              field.field.fieldType === 4) &&
            parseFloat(ele.min) > parseFloat(ele.max)
          ) {
            ElMessage.error(t('chart.value_min_max_invalid'))
            return
          }
        } else {
          if (!ele.term.includes('null') && !ele.term.includes('empty') && ele.value === '') {
            ElMessage.error(t('chart.value_can_not_empty'))
            return
          }
          if (
            !ele.term.includes('null') &&
            !ele.term.includes('empty') &&
            (field.field.fieldType === 2 ||
              field.field.fieldType === 3 ||
              field.field.fieldType === 4) &&
            parseFloat(ele.value).toString() === 'NaN'
          ) {
            ElMessage.error(t('chart.value_error'))
            return
          }
        }
      } else {
        // 动态阈值比较的是另一个字段，保存前必须确认参照字段已经配置。
        if (ele.term === 'between') {
          if (
            !ele.term.includes('null') &&
            !ele.term.includes('empty') &&
            (!ele.dynamicMinField?.fieldId || !ele.dynamicMaxField?.fieldId)
          ) {
            ElMessage.error(t('chart.field_can_not_empty'))
            return
          }
        } else {
          if (
            !ele.term.includes('null') &&
            !ele.term.includes('empty') &&
            !ele.dynamicField?.fieldId
          ) {
            ElMessage.error(t('chart.field_can_not_empty'))
            return
          }
        }
      }
    }
  }
  state.thresholdForm.tableThreshold = JSON.parse(JSON.stringify(state.tableThresholdArr))
  // 表格和图片组阈值共享保存字段，由图表类型决定渲染为颜色还是图片。
  changeThreshold()
  closeTableThreshold()
}

// 同步折线阈值弹窗编辑结果
const lineThresholdChange = val => {
  state.lineThresholdArr = val
}
// 打开折线阈值编辑弹窗
const editLineThreshold = () => {
  state.editLineThresholdDialog = true
}
// 关闭折线阈值编辑弹窗
const closeLineThreshold = () => {
  state.editLineThresholdDialog = false
}
// 校验并保存折线阈值配置
const changeLineThreshold = () => {
  // 折线阈值必须配置字段和条件，数值字段需要额外校验数值合法性
  for (let i = 0; i < state.lineThresholdArr?.length; i++) {
    const field = state.lineThresholdArr[i]
    if (!field.fieldId) {
      ElMessage.error(t('chart.field_can_not_empty'))
      return
    }
    if (!field.conditions || field.conditions.length === 0) {
      ElMessage.error(t('chart.conditions_can_not_empty'))
      return
    }
    for (let j = 0; j < field.conditions.length; j++) {
      const ele = field.conditions[j]
      if (!ele.term || ele.term === '') {
        ElMessage.error(t('chart.exp_can_not_empty'))
        return
      }
      if (ele.term === 'between') {
        if (
          !ele.term.includes('null') &&
          !ele.term.includes('empty') &&
          (ele.min === '' || ele.max === '')
        ) {
          ElMessage.error(t('chart.value_can_not_empty'))
          return
        }
        if (
          (field.field.fieldType === 2 ||
            field.field.fieldType === 3 ||
            field.field.fieldType === 4) &&
          (parseFloat(ele.min).toString() === 'NaN' || parseFloat(ele.max).toString() === 'NaN')
        ) {
          ElMessage.error(t('chart.value_error'))
          return
        }
        if (
          (field.field.fieldType === 2 ||
            field.field.fieldType === 3 ||
            field.field.fieldType === 4) &&
          parseFloat(ele.min) > parseFloat(ele.max)
        ) {
          ElMessage.error(t('chart.value_min_max_invalid'))
          return
        }
      } else {
        if (!ele.term.includes('null') && !ele.term.includes('empty') && ele.value === '') {
          ElMessage.error(t('chart.value_can_not_empty'))
          return
        }
        if (
          (field.field.fieldType === 2 ||
            field.field.fieldType === 3 ||
            field.field.fieldType === 4) &&
          parseFloat(ele.value).toString() === 'NaN'
        ) {
          ElMessage.error(t('chart.value_error'))
          return
        }
      }
    }
  }
  state.thresholdForm.lineThreshold = JSON.parse(JSON.stringify(state.lineThresholdArr ?? []))
  // 折线阈值单独保存，避免和表格阈值互相覆盖。
  changeThreshold()
  closeLineThreshold()
}

// 计算字段展示名称，优先使用图表展示别名
const getFieldName = field => (field.chartShowName ? field.chartShowName : field.name)

// 生成动态阈值条件的字段摘要文案
const getDynamicStyleLabel = (item, fieldObj) => {
  // 按字段汇总方式生成条件标签
  const handleSummary = field => {
    if (!field?.field) {
      return ''
    }
    if (field.summary === 'value') {
      return getFieldName(field.field) + '(' + t('chart.field') + ')'
    } else {
      let suffix = field.summary === 'avg' ? t('chart.drag_block_label_value') : ''
      return getFieldName(field.field) + '(' + t('chart.' + field.summary) + suffix + ')'
    }
  }
  if (item.type === 'dynamic') {
    // 只有动态阈值需要展示参照字段摘要，固定阈值直接展示输入值。
    return handleSummary(fieldObj)
  }
}
init()
</script>

<template>
  <div @keydown.stop @keyup.stop style="width: 100%; margin-bottom: 16px">
    <!-- 仪表盘阈值按百分比分段，保存前校验 0 到 100 之间的递增边界。 -->
    <el-col v-show="showProperty('gaugeThreshold')">
      <el-form
        :model="state.thresholdForm"
        ref="thresholdForm"
        label-position="top"
        @submit.prevent
      >
        <el-form-item
          :label="t('chart.threshold_range') + '(%)'"
          class="form-item"
          label-width="auto"
        >
          <span>0,</span>
          <el-input
            :effect="themes"
            :placeholder="t('chart.threshold_range')"
            :disabled="!state.thresholdForm.enable"
            v-model="state.thresholdForm.gaugeThreshold"
            style="width: 100px; margin: 0 10px"
            size="small"
            clearable
            @change="changeSplitThreshold"
          />
          <span>,100</span>
          <el-tooltip effect="dark" placement="bottom">
            <el-icon style="margin-left: 10px"><InfoFilled /></el-icon>
            <template #content>
              <span>{{ t('chart.gauge_condition_style_tips') }}</span>
            </template>
          </el-tooltip>
        </el-form-item>
      </el-form>
    </el-col>
    <el-col v-show="showProperty('liquidThreshold')">
      <el-form
        :model="state.thresholdForm"
        ref="thresholdForm"
        label-position="top"
        @submit.prevent
      >
        <el-form-item
          :label="t('chart.threshold_range') + '(%)'"
          class="form-item"
          label-width="auto"
        >
          <span>0,</span>
          <el-input
            :effect="themes"
            :placeholder="t('chart.threshold_range')"
            :disabled="!state.thresholdForm.enable"
            v-model="state.thresholdForm.liquidThreshold"
            style="width: 100px; margin: 0 10px"
            size="small"
            clearable
            @change="changeSplitThreshold"
          />
          <span>,100</span>
          <el-tooltip effect="dark" placement="bottom">
            <el-icon style="margin-left: 10px"><InfoFilled /></el-icon>
            <template #content>
              <span>{{ t('chart.liquid_condition_style_tips') }}</span>
            </template>
          </el-tooltip>
        </el-form-item>
      </el-form>
    </el-col>

    <!-- 文本卡阈值预览展示匹配条件、取值和命中后的文本颜色。 -->
    <el-col v-if="props.chart.type && props.chart.type === 'label'">
      <el-col>
        <el-button
          :title="t('chart.edit')"
          class="circle-button"
          type="primary"
          text
          size="small"
          style="width: 24px; margin-left: 4px"
          @click="editTextLabelThreshold"
        >
          <template #icon>
            <el-icon size="14px">
              <Icon name="icon_edit_outlined"><icon_edit_outlined class="svg-icon" /></Icon>
            </el-icon>
          </template>
        </el-button>
        <el-col style="padding: 0 18px">
          <el-row
            v-for="(item, index) in state.thresholdForm.textLabelThreshold"
            :key="index"
            class="line-style"
          >
            <el-col :span="6">
              <span v-if="item.term === 'eq'" :title="t('chart.filter_eq')">{{
                t('chart.filter_eq')
              }}</span>
              <span v-else-if="item.term === 'not_eq'" :title="t('chart.filter_not_eq')">{{
                t('chart.filter_not_eq')
              }}</span>
              <span v-else-if="item.term === 'like'" :title="t('chart.filter_like')">{{
                t('chart.filter_like')
              }}</span>
              <span v-else-if="item.term === 'not like'" :title="t('chart.filter_not_like')">{{
                t('chart.filter_not_like')
              }}</span>
              <span v-else-if="item.term === 'null'" :title="t('chart.filter_null')">{{
                t('chart.filter_null')
              }}</span>
              <span v-else-if="item.term === 'not_null'" :title="t('chart.filter_not_null')">{{
                t('chart.filter_not_null')
              }}</span>
            </el-col>
            <el-col :span="12">
              <span v-if="!item.term.includes('null')" :title="item.value + ''">{{
                item.value
              }}</span>
              <span v-else>&nbsp;</span>
            </el-col>
            <el-col :span="6">
              <span
                :style="{
                  width: '14px',
                  height: '14px',
                  backgroundColor: item.color,
                  border: 'solid 1px #e1e4e8'
                }"
              />
            </el-col>
          </el-row>
        </el-col>
      </el-col>
    </el-col>

    <!-- 指标卡阈值预览同时展示文字色和背景色，编辑前要求总开关已启用。 -->
    <el-col v-if="props.chart.type && props.chart.type === 'indicator'">
      <el-col>
        <div class="inner-container">
          <span class="label" :class="'label-' + props.themes">{{
            $t('visualization.condition_style_set')
          }}</span>
          <span class="right-btns">
            <span
              class="set-text-info"
              :class="{ 'set-text-info-dark': themes === 'dark' }"
              v-if="state.thresholdForm?.labelThreshold?.length > 0"
            >
              {{ t('visualization.already_setting') }}
            </span>
            <el-button
              :title="t('chart.edit')"
              :class="'label-' + props.themes"
              :style="{ width: '24px', marginLeft: '6px' }"
              :disabled="!state.thresholdForm.enable"
              class="circle-button"
              text
              size="small"
              @click="editLabelThreshold"
            >
              <template #icon>
                <el-icon size="14px">
                  <Icon name="icon_edit_outlined"><icon_edit_outlined class="svg-icon" /></Icon>
                </el-icon>
              </template>
            </el-button>
          </span>
        </div>

        <div
          class="threshold-container"
          :class="{ 'threshold-container-dark': themes === 'dark' }"
          v-if="state.thresholdForm.labelThreshold.length > 0"
        >
          <div class="field-style" :class="{ 'field-style-dark': themes === 'dark' }">
            <span class="field-text" style="padding-left: 12px">
              {{ t('chart.indicator_value') }}
            </span>
          </div>

          <div
            v-for="(item, index) in state.thresholdForm.labelThreshold"
            :key="index"
            class="line-style"
          >
            <div style="flex: 1">
              <span v-if="item.term === 'eq'" :title="t('chart.filter_eq')">{{
                t('chart.filter_eq')
              }}</span>
              <span v-else-if="item.term === 'not_eq'" :title="t('chart.filter_not_eq')">{{
                t('chart.filter_not_eq')
              }}</span>
              <span v-else-if="item.term === 'lt'" :title="t('chart.filter_lt')">{{
                t('chart.filter_lt')
              }}</span>
              <span v-else-if="item.term === 'gt'" :title="t('chart.filter_gt')">{{
                t('chart.filter_gt')
              }}</span>
              <span v-else-if="item.term === 'le'" :title="t('chart.filter_le')">{{
                t('chart.filter_le')
              }}</span>
              <span v-else-if="item.term === 'ge'" :title="t('chart.filter_ge')">{{
                t('chart.filter_ge')
              }}</span>
              <span v-else-if="item.term === 'between'" :title="t('chart.filter_between')">{{
                t('chart.filter_between')
              }}</span>
            </div>
            <div style="flex: 1; margin: 0 8px">
              <span v-if="item.term !== 'between'" :title="item.value + ''">{{ item.value }}</span>
              <span v-if="item.term === 'between'">
                {{ item.min }}&nbsp;≤{{ t('chart.drag_block_label_value') }}≤&nbsp;{{ item.max }}
              </span>
            </div>
            <div
              :title="t('chart.textColor')"
              :style="{
                backgroundColor: item.color
              }"
              class="color-div"
              :class="{ 'color-div-dark': themes === 'dark' }"
            ></div>
            <div
              :title="t('chart.backgroundColor')"
              :style="{
                backgroundColor: item.backgroundColor
              }"
              class="color-div"
              :class="{ 'color-div-dark': themes === 'dark' }"
            ></div>
          </div>
        </div>
      </el-col>
    </el-col>

    <!-- 表格和图片组共用字段级阈值结构，渲染结果根据图表类型展示为颜色或图片。 -->
    <el-col v-show="showProperty('tableThreshold')">
      <el-col>
        <div class="inner-container">
          <span class="label" :class="'label-' + props.themes">{{
            $t('visualization.condition_style_set')
          }}</span>
          <span class="right-btns">
            <span
              class="set-text-info"
              :class="{ 'set-text-info-dark': themes === 'dark' }"
              v-if="state.thresholdForm?.tableThreshold?.length > 0"
            >
              {{ t('visualization.already_setting') }}
            </span>
            <el-button
              :title="t('chart.edit')"
              :class="'label-' + props.themes"
              :style="{ width: '24px', marginLeft: '6px' }"
              :disabled="!state.thresholdForm.enable"
              class="circle-button"
              text
              size="small"
              @click="editTableThreshold"
            >
              <template #icon>
                <el-icon size="14px">
                  <Icon name="icon_edit_outlined"><icon_edit_outlined class="svg-icon" /></Icon>
                </el-icon>
              </template>
            </el-button>
          </span>
        </div>

        <div
          class="threshold-container"
          :class="{ 'threshold-container-dark': themes === 'dark' }"
          v-if="state.thresholdForm.tableThreshold.length > 0"
        >
          <el-row
            v-for="(fieldItem, fieldIndex) in state.thresholdForm.tableThreshold"
            :key="fieldIndex"
            style="flex-direction: column"
          >
            <div class="field-style" :class="{ 'field-style-dark': themes === 'dark' }">
              <el-icon>
                <Icon :className="`field-icon-${fieldType[fieldItem.field.fieldType]}`"
                  ><component
                    class="svg-icon"
                    :class="`field-icon-${fieldType[fieldItem.field.fieldType]}`"
                    :is="iconFieldMap[fieldType[fieldItem.field.fieldType]]"
                  ></component
                ></Icon>
              </el-icon>
              <span :title="fieldItem.field.name" class="field-text">{{
                fieldItem.field.name
              }}</span>
            </div>
            <div v-for="(item, index) in fieldItem.conditions" :key="index" class="line-style">
              <div style="flex: 1">
                <span v-if="item.term === 'eq'" :title="t('chart.filter_eq')">
                  {{ t('chart.filter_eq') }}
                </span>
                <span v-else-if="item.term === 'not_eq'" :title="t('chart.filter_not_eq')">
                  {{ t('chart.filter_not_eq') }}
                </span>
                <span v-else-if="item.term === 'lt'" :title="t('chart.filter_lt')">
                  {{ t('chart.filter_lt') }}
                </span>
                <span v-else-if="item.term === 'gt'" :title="t('chart.filter_gt')">
                  {{ t('chart.filter_gt') }}
                </span>
                <span v-else-if="item.term === 'le'" :title="t('chart.filter_le')">
                  {{ t('chart.filter_le') }}
                </span>
                <span v-else-if="item.term === 'ge'" :title="t('chart.filter_ge')">
                  {{ t('chart.filter_ge') }}
                </span>
                <span v-else-if="item.term === 'between'" :title="t('chart.filter_between')">
                  {{ t('chart.filter_between') }}
                </span>
                <span v-else-if="item.term === 'like'" :title="t('chart.filter_like')">
                  {{ t('chart.filter_like') }}
                </span>
                <span v-else-if="item.term === 'not like'" :title="t('chart.filter_not_like')">
                  {{ t('chart.filter_not_like') }}
                </span>
                <span v-else-if="item.term === 'null'" :title="t('chart.filter_null')">
                  {{ t('chart.filter_null') }}
                </span>
                <span v-else-if="item.term === 'not_null'" :title="t('chart.filter_not_null')">
                  {{ t('chart.filter_not_null') }}
                </span>
                <span v-else-if="item.term === 'empty'" :title="t('chart.filter_empty')">
                  {{ t('chart.filter_empty') }}
                </span>
                <span v-else-if="item.term === 'not_empty'" :title="t('chart.filter_not_empty')">
                  {{ t('chart.filter_not_empty') }}
                </span>
                <span v-else-if="item.term === 'default'" title="默认"> 默认 </span>
              </div>
              <div v-if="item.type !== 'dynamic'" style="flex: 1; margin: 0 8px">
                <span style="margin: 0 8px">
                  {{ t('chart.fix') }}
                </span>
              </div>
              <div v-else style="flex: 1; margin: 0 8px">
                <span style="margin: 0 8px">
                  {{ t('chart.dynamic') }}
                </span>
              </div>
              <div v-if="item.type !== 'dynamic'" style="flex: 1; margin: 0 8px">
                <span
                  v-if="
                    !item.term.includes('null') &&
                    !item.term.includes('default') &&
                    !item.term.includes('empty') &&
                    item.term !== 'between'
                  "
                  :title="item.value + ''"
                  >{{ item.value }}</span
                >
                <span
                  v-else-if="
                    !item.term.includes('null') &&
                    !item.term.includes('empty') &&
                    item.term === 'between'
                  "
                  :title="item.min + ' ≤= ' + t('chart.drag_block_label_value') + ' ≤ ' + item.max"
                >
                  {{ item.min }}&nbsp;≤{{ t('chart.drag_block_label_value') }}≤&nbsp;{{ item.max }}
                </span>
                <span v-else>&nbsp;</span>
              </div>
              <div v-else style="flex: 1; margin: 0 8px">
                <span
                  v-if="
                    !item.term.includes('null') &&
                    !item.term.includes('default') &&
                    !item.term.includes('empty') &&
                    item.term !== 'between'
                  "
                  :title="getDynamicStyleLabel(item, item.dynamicField) + ''"
                >
                  {{ getDynamicStyleLabel(item, item.dynamicField) }}</span
                >
                <span
                  v-else-if="
                    !item.term.includes('null') &&
                    !item.term.includes('empty') &&
                    item.term === 'between'
                  "
                  :title="
                    getDynamicStyleLabel(item, item.dynamicMinField) +
                    '≤' +
                    t('chart.drag_block_label_value') +
                    '≤' +
                    getDynamicStyleLabel(item, item.dynamicMaxField)
                  "
                >
                  {{ getDynamicStyleLabel(item, item.dynamicMinField) }}≤{{
                    t('chart.drag_block_label_value')
                  }}≤{{ getDynamicStyleLabel(item, item.dynamicMaxField) }}
                </span>
                <span v-else>&nbsp;</span>
              </div>
              <template v-if="chart.type === 'picture-group'">
                <div title="显示图片" class="pic-group-main">
                  <img
                    draggable="false"
                    v-if="item.url"
                    class="pic-group-img"
                    :src="imgUrlTrans(item.url)"
                  />
                </div>
              </template>

              <template v-if="chart.type !== 'picture-group'">
                <div
                  :title="t('chart.textColor')"
                  :style="{
                    backgroundColor: item.color
                  }"
                  class="color-div"
                  :class="{ 'color-div-dark': themes === 'dark' }"
                ></div>
                <div
                  :title="t('chart.backgroundColor')"
                  :style="{
                    backgroundColor: item.backgroundColor
                  }"
                  class="color-div"
                  :class="{ 'color-div-dark': themes === 'dark' }"
                ></div>
              </template>
            </div>
          </el-row>
        </div>
      </el-col>
    </el-col>
    <!-- 折线阈值按字段分组展示线条颜色，保存时独立写入 lineThreshold。 -->
    <el-col v-show="showProperty('lineThreshold')">
      <el-col>
        <div class="inner-container">
          <span class="label" :class="'label-' + props.themes">{{
            $t('visualization.condition_style_set')
          }}</span>
          <span class="right-btns">
            <span
              class="set-text-info"
              :class="{ 'set-text-info-dark': themes === 'dark' }"
              v-if="state.thresholdForm?.lineThresholdArr?.length > 0"
            >
              $t('visualization.already_setting')
            </span>
            <el-button
              :title="t('chart.edit')"
              :class="'label-' + props.themes"
              :style="{ width: '24px', marginLeft: '6px' }"
              :disabled="!state.thresholdForm.enable"
              class="circle-button"
              text
              size="small"
              @click="editLineThreshold"
            >
              <template #icon>
                <el-icon size="14px">
                  <Icon name="icon_edit_outlined"><icon_edit_outlined class="svg-icon" /></Icon>
                </el-icon>
              </template>
            </el-button>
          </span>
        </div>

        <div
          class="threshold-container"
          :class="{ 'threshold-container-dark': themes === 'dark' }"
          v-if="state.thresholdForm.lineThreshold?.length > 0"
        >
          <el-row
            v-for="(fieldItem, fieldIndex) in state.thresholdForm.lineThreshold"
            :key="fieldIndex"
            style="flex-direction: column"
          >
            <div class="field-style" :class="{ 'field-style-dark': themes === 'dark' }">
              <el-icon>
                <Icon :className="`field-icon-${fieldType[fieldItem.field.fieldType]}`"
                  ><component
                    class="svg-icon"
                    :class="`field-icon-${fieldType[fieldItem.field.fieldType]}`"
                    :is="iconFieldMap[fieldType[fieldItem.field.fieldType]]"
                  ></component
                ></Icon>
              </el-icon>
              <span :title="fieldItem.field.name" class="field-text">{{
                fieldItem.field.name
              }}</span>
            </div>
            <div v-for="(item, index) in fieldItem.conditions" :key="index" class="line-style">
              <div style="flex: 1">
                <span v-if="item.term === 'eq'" :title="t('chart.filter_eq')">
                  {{ t('chart.filter_eq') }}</span
                >
                <span v-else-if="item.term === 'not_eq'" :title="t('chart.filter_not_eq')">
                  {{ t('chart.filter_not_eq') }}</span
                >
                <span v-if="item.term === 'lt'" :title="t('chart.filter_lt')">
                  {{ t('chart.filter_lt') }}
                </span>
                <span v-else-if="item.term === 'gt'" :title="t('chart.filter_gt')">
                  {{ t('chart.filter_gt') }}
                </span>
                <span v-else-if="item.term === 'le'" :title="t('chart.filter_le')">
                  {{ t('chart.filter_le') }}
                </span>
                <span v-else-if="item.term === 'ge'" :title="t('chart.filter_ge')">
                  {{ t('chart.filter_ge') }}
                </span>
                <span v-else-if="item.term === 'between'" :title="t('chart.filter_between')">
                  {{ t('chart.filter_between') }}
                </span>
                <span v-else-if="item.term === 'default'" title="默认"> 默认 </span>
              </div>
              <div v-if="item.type !== 'dynamic'" style="flex: 1; margin: 0 8px">
                <span style="margin: 0 8px">
                  {{ t('chart.fix') }}
                </span>
              </div>
              <div v-else style="flex: 1; margin: 0 8px">
                <span style="margin: 0 8px">
                  {{ t('chart.dynamic') }}
                </span>
              </div>
              <div v-if="item.type !== 'dynamic'" style="flex: 1; margin: 0 8px">
                <span
                  v-if="
                    !item.term.includes('null') &&
                    !item.term.includes('default') &&
                    !item.term.includes('empty') &&
                    item.term !== 'between'
                  "
                  :title="item.value + ''"
                  >{{ item.value }}</span
                >
                <span
                  v-else-if="
                    !item.term.includes('null') &&
                    !item.term.includes('empty') &&
                    item.term === 'between'
                  "
                  :title="item.min + ' ≤= ' + t('chart.drag_block_label_value') + ' ≤ ' + item.max"
                >
                  {{ item.min }}&nbsp;≤{{ t('chart.drag_block_label_value') }}≤&nbsp;{{ item.max }}
                </span>
                <span v-else>&nbsp;</span>
              </div>
              <template v-if="chart.type !== 'picture-group'">
                <div
                  :title="t('chart.color')"
                  :style="{
                    backgroundColor: item.color
                  }"
                  class="color-div"
                  :class="{ 'color-div-dark': themes === 'dark' }"
                ></div>
              </template>
            </div>
          </el-row>
        </div>
      </el-col>
    </el-col>

    <!-- 符号地图复用折线阈值编辑器，仅展示气泡颜色相关规则。 -->
    <el-col v-show="showProperty('symbolicBubbleThreshold')">
      <el-col>
        <div class="inner-container">
          <span class="label" :class="'label-' + props.themes">{{
            $t('visualization.condition_style_set')
          }}</span>
          <span class="right-btns">
            <span
              class="set-text-info"
              :class="{ 'set-text-info-dark': themes === 'dark' }"
              v-if="state.thresholdForm?.tableThreshold?.length > 0"
            >
              $t('visualization.already_setting')
            </span>
            <el-button
              :title="t('chart.edit')"
              :class="'label-' + props.themes"
              :style="{ width: '24px', marginLeft: '6px' }"
              :disabled="!state.thresholdForm.enable"
              class="circle-button"
              text
              size="small"
              @click="editLineThreshold"
            >
              <template #icon>
                <el-icon size="14px">
                  <Icon name="icon_edit_outlined"><icon_edit_outlined class="svg-icon" /></Icon>
                </el-icon>
              </template>
            </el-button>
          </span>
        </div>

        <div
          class="threshold-container"
          :class="{ 'threshold-container-dark': themes === 'dark' }"
          v-if="state.thresholdForm.lineThreshold?.length > 0"
        >
          <el-row
            v-for="(fieldItem, fieldIndex) in state.thresholdForm.lineThreshold"
            :key="fieldIndex"
            style="flex-direction: column"
          >
            <div class="field-style" :class="{ 'field-style-dark': themes === 'dark' }">
              <el-icon>
                <Icon :className="`field-icon-${fieldType[fieldItem.field.fieldType]}`"
                  ><component
                    class="svg-icon"
                    :class="`field-icon-${fieldType[fieldItem.field.fieldType]}`"
                    :is="iconFieldMap[fieldType[fieldItem.field.fieldType]]"
                  ></component
                ></Icon>
              </el-icon>
              <span :title="fieldItem.field.name" class="field-text">{{
                fieldItem.field.name
              }}</span>
            </div>
            <div v-for="(item, index) in fieldItem.conditions" :key="index" class="line-style">
              <div style="flex: 1">
                <span v-if="item.term === 'lt'" :title="t('chart.filter_lt')">
                  {{ t('chart.filter_lt') }}
                </span>
                <span v-else-if="item.term === 'gt'" :title="t('chart.filter_gt')">
                  {{ t('chart.filter_gt') }}
                </span>
                <span v-else-if="item.term === 'le'" :title="t('chart.filter_le')">
                  {{ t('chart.filter_le') }}
                </span>
                <span v-else-if="item.term === 'ge'" :title="t('chart.filter_ge')">
                  {{ t('chart.filter_ge') }}
                </span>
                <span v-else-if="item.term === 'between'" :title="t('chart.filter_between')">
                  {{ t('chart.filter_between') }}
                </span>
                <span v-else-if="item.term === 'default'" title="默认"> 默认 </span>
              </div>
              <div v-if="item.type !== 'dynamic'" style="flex: 1; margin: 0 8px">
                <span style="margin: 0 8px">
                  {{ t('chart.fix') }}
                </span>
              </div>
              <div v-else style="flex: 1; margin: 0 8px">
                <span style="margin: 0 8px">
                  {{ t('chart.dynamic') }}
                </span>
              </div>
              <div v-if="item.type !== 'dynamic'" style="flex: 1; margin: 0 8px">
                <span
                  v-if="
                    !item.term.includes('null') &&
                    !item.term.includes('default') &&
                    !item.term.includes('empty') &&
                    item.term !== 'between'
                  "
                  :title="item.value + ''"
                  >{{ item.value }}</span
                >
                <span
                  v-else-if="
                    !item.term.includes('null') &&
                    !item.term.includes('empty') &&
                    item.term === 'between'
                  "
                  :title="item.min + ' ≤= ' + t('chart.drag_block_label_value') + ' ≤ ' + item.max"
                >
                  {{ item.min }}&nbsp;≤{{ t('chart.drag_block_label_value') }}≤&nbsp;{{ item.max }}
                </span>
                <span v-else>&nbsp;</span>
              </div>
              <template v-if="chart.type !== 'picture-group'">
                <div
                  :title="t('chart.color')"
                  :style="{
                    backgroundColor: item.color
                  }"
                  class="color-div"
                  :class="{ 'color-div-dark': themes === 'dark' }"
                ></div>
              </template>
            </div>
          </el-row>
        </div>
      </el-col>
    </el-col>

    <!-- 文本卡阈值弹窗编辑副本，确认后才覆盖正式配置。 -->
    <el-dialog
      v-if="state.editTextLabelThresholdDialog"
      v-model="state.editTextLabelThresholdDialog"
      :title="t('chart.threshold')"
      :visible="state.editTextLabelThresholdDialog"
      width="800px"
      class="dialog-css"
      append-to-body
    >
      <text-label-threshold-edit
        :threshold="state.thresholdForm.textLabelThreshold"
        @onTextLabelThresholdChange="thresholdTextChange"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="closeTextLabelThreshold">{{ t('chart.cancel') }}</el-button>
          <el-button type="primary" @click="changeTextLabelThreshold">{{
            t('chart.confirm')
          }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 指标卡阈值弹窗负责数值条件校验和颜色配置。 -->
    <el-dialog
      v-if="state.editLabelThresholdDialog"
      v-model="state.editLabelThresholdDialog"
      :title="t('chart.threshold')"
      :visible="state.editLabelThresholdDialog"
      width="800px"
      class="dialog-css"
      append-to-body
    >
      <text-threshold-edit
        :threshold="state.thresholdForm.labelThreshold"
        @onLabelThresholdChange="thresholdChange"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="closeLabelThreshold">{{ t('chart.cancel') }}</el-button>
          <el-button type="primary" @click="changeLabelThreshold">{{
            t('chart.confirm')
          }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 表格阈值弹窗根据图表类型切换普通表格规则或图片组规则。 -->
    <el-dialog
      v-if="state.editTableThresholdDialog"
      v-model="state.editTableThresholdDialog"
      :title="t('chart.threshold')"
      :visible="state.editTableThresholdDialog"
      width="1250px"
      class="dialog-css"
      append-to-body
    >
      <picture-group-threshold-edit
        v-if="chart.type === 'picture-group' && curComponent"
        :threshold="state.thresholdForm.tableThreshold"
        :chart="chart"
        :element="curComponent"
        @onTableThresholdChange="tableThresholdChange"
      ></picture-group-threshold-edit>
      <table-threshold-edit
        v-else
        :threshold="state.thresholdForm.tableThreshold"
        :chart="chart"
        @onTableThresholdChange="tableThresholdChange"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="closeTableThreshold">{{ t('chart.cancel') }}</el-button>
          <el-button type="primary" @click="changeTableThreshold">{{
            t('chart.confirm')
          }}</el-button>
        </div>
      </template>
    </el-dialog>
    <!-- 折线阈值弹窗编辑字段条件和线条颜色，确认后同步给父级。 -->
    <el-dialog
      v-if="state.editLineThresholdDialog"
      v-model="state.editLineThresholdDialog"
      :title="t('chart.threshold')"
      :visible="state.editLineThresholdDialog"
      width="1050px"
      class="dialog-css"
      append-to-body
    >
      <line-threshold-edit
        :threshold="state.thresholdForm.lineThreshold"
        :chart="chart"
        @onLineThresholdChange="lineThresholdChange"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="closeLineThreshold">{{ t('chart.cancel') }}</el-button>
          <el-button type="primary" @click="changeLineThreshold">{{
            t('chart.confirm')
          }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="less" scoped>
.shape-item {
  padding: 6px;
  border: none;
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.form-item-slider :deep(.el-form-item__label) {
  font-size: 12px;
  line-height: 38px;
}
.form-item :deep(.el-form-item__label) {
  font-size: 12px;
}
.el-select-dropdown__item {
  padding: 0 20px;
}
span {
  font-size: 12px;
}
.el-form-item {
  margin-bottom: 6px;
}

.switch-style {
  position: absolute;
  right: 10px;
  margin-top: -4px;
}
.color-picker-style {
  cursor: pointer;
  z-index: 1003;
}

.dialog-css :deep(.el-dialog__title) {
  font-size: 14px;
}

.dialog-css :deep(.el-dialog__header) {
  padding: 20px 20px 0;
}

.dialog-css :deep(.el-dialog__body) {
  padding: 10px 20px 20px;
}

.field-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #8492a6;
  font-size: 12px;
}

.label-dark {
  color: #a6a6a6;
}

.inner-container {
  display: flex;
  align-items: center;
  flex-direction: row;
  justify-content: space-between;

  .label {
    cursor: default;
    color: #646a73;
    font-size: 12px;
    font-style: normal;
    font-weight: 400;
    line-height: 20px;
  }

  .right-btns {
    display: flex;
    align-items: center;
    flex-direction: row;
  }

  .set-text-info {
    cursor: default;
    padding: 1.5px 4px;
    border-radius: 2px;
    background: rgba(31, 35, 41, 0.1);

    color: #646a73;

    font-size: 10px;
    font-style: normal;
    font-weight: 500;
    line-height: 13px;

    &.set-text-info-dark {
      color: #a6a6a6;
      background: rgba(235, 235, 235, 0.1);
    }
  }
}

.line-style {
  width: 100%;
  font-weight: 400;
  padding: 4px 8px;
  display: flex;
  flex-direction: row;
  align-items: center;
  flex-wrap: nowrap;
  :nth-child(1) {
    width: 48px;
  }
  :nth-child(2) {
    width: 40px !important;
  }
  :nth-child(3) {
    width: 30px !important;
  }
  &:deep(span) {
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
    cursor: default;
    display: block;
  }
}

.threshold-container {
  border-radius: 6px;
  border: 1px solid #dee0e3;

  margin-top: 8px;

  max-height: 500px;
  overflow-y: auto;

  .color-div {
    margin-right: 8px;
    width: 14px;
    height: 14px;
    border: solid 1px #e1e4e8;
    border-radius: 2px;

    &:last-child {
      margin-right: unset;
    }

    &.color-div-dark {
      border-color: rgba(255, 255, 255, 0.15);
    }
  }

  &.threshold-container-dark {
    border-color: rgba(255, 255, 255, 0.15);
  }

  .field-style {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    background: #f5f6f7;
    &.field-style-dark {
      background: #1a1a1a;
      :deep(.field-text) {
        color: #a6a6a6;
      }
    }
  }
}
.label-dark {
  font-family: var(--crest-custom_font, 'PingFang');
  font-style: normal;
  font-weight: 400;
  line-height: 20px;
  color: #a6a6a6 !important;
  &.ed-button {
    color: var(--ed-color-primary) !important;
  }
  &.is-disabled {
    color: #5f5f5f !important;
  }
}

.pic-group-main {
  margin-right: 8px;
  width: 24px;
  height: 24px;
  border: solid 1px #e1e4e8;
  border-radius: 2px;
}
.pic-group-img {
  width: 100% !important;
  height: 100% !important;
}
</style>
