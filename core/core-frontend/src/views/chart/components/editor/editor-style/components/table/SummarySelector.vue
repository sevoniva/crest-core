<script setup lang="ts">
import { useI18n } from '@/hooks/web/useI18n'
import { computed, onMounted, PropType, reactive, watch, ref, nextTick, inject } from 'vue'
import { DEFAULT_BASIC_STYLE } from '@/views/chart/components/editor/util/chart'
import { cloneDeep, defaultsDeep, filter, find } from 'lodash-es'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import CustomAggrEdit from './CustomAggrEdit.vue'

const dvMainStore = dvMainStoreWithOut()
const { batchOptStatus } = storeToRefs(dvMainStore)
const { t } = useI18n()
// 接收图表、主题和可编辑属性范围
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
// 判断指定汇总属性是否可编辑
const showProperty = prop => props.propertyInner?.includes(prop)

// 维护汇总配置表单和当前字段选择
const state = reactive({
  basicStyleForm: JSON.parse(JSON.stringify(DEFAULT_BASIC_STYLE)) as ChartBasicStyle,
  currentAxis: undefined as string,
  currentAxisSummary: undefined as {
    show: boolean
    field: string
    summary: string
    originName?: string
  }
})

// 定义基础样式变更事件
const emit = defineEmits(['onBasicStyleChange'])
// 提交汇总配置变更
const changeBasicStyle = (prop?: string, requestData = false) => {
  emit('onBasicStyleChange', { data: state.basicStyleForm, requestData }, prop)
}
// 切换汇总方式并处理自定义汇总校验
const changeSummaryType = () => {
  if (state.currentAxisSummary.summary === 'custom' && !state.currentAxisSummary.originName) {
    return
  }
  changeBasicStyle('seriesSummary')
}
// 监听汇总开关和字段变化并刷新配置
watch(
  [
    () => props.chart.customAttr.basicStyle.showSummary,
    () => props.chart.xAxis,
    () => props.chart.yAxis
  ],
  () => {
    init()
  },
  {
    deep: true
  }
)

// 获取可参与汇总的轴字段列表
function getAxisList() {
  return props.chart.type === 'table-info'
    ? filter(props.chart.xAxis, axis => [2, 3, 4].includes(axis.fieldType))
    : props.chart.yAxis
}

// 计算当前可选汇总字段
const computedAxis = computed(() => {
  return getAxisList()
})
const summaryTypes = [
  { key: 'sum', name: t('chart.sum') },
  { key: 'avg', name: t('chart.avg') },
  { key: 'max', name: t('chart.max') },
  { key: 'min', name: t('chart.min') },
  { key: 'custom', name: t('commons.custom') }
]

// 切换当前正在编辑的汇总字段
function onSelectAxis(value) {
  state.currentAxisSummary = find(state.basicStyleForm.seriesSummary, s => s.field === value)
}

// 自定义聚合编辑器实例
const calcEdit = ref()
// 控制自定义聚合编辑弹窗显隐
const editCalcField = ref(false)
const dimension = inject('dimension', () => [])
const quota = inject('quota', () => [])
// 打开自定义汇总字段编辑器
const editField = () => {
  editCalcField.value = true
  nextTick(() => {
    calcEdit.value.initEdit(
      state.currentAxisSummary,
      quota().filter(ele => ele.id !== '-1')
    )
  })
}
// 关闭自定义汇总字段编辑器
const closeEditCalc = () => {
  editCalcField.value = false
}
// 保存自定义汇总字段并同步汇总配置
const confirmEditCalc = () => {
  calcEdit.value.setFieldForm()
  const obj = cloneDeep(calcEdit.value.fieldForm)
  state.currentAxisSummary.originName = obj.originName
  setFieldDefaultValue(state.currentAxisSummary)
  closeEditCalc()
  changeSummaryType()
}
// 初始化汇总配置并补齐字段默认值
const init = () => {
  const basicStyle = cloneDeep(props.chart.customAttr.basicStyle)

  state.basicStyleForm = defaultsDeep(basicStyle, cloneDeep(DEFAULT_BASIC_STYLE)) as ChartBasicStyle

  const axisList = getAxisList()

  const tempList = []
  for (let i = 0; i < axisList.length; i++) {
    const axis = axisList[i]
    let savedAxis = find(state.basicStyleForm.seriesSummary, s => s.field === axis.engineFieldName)
    if (savedAxis) {
      if (savedAxis.summary == undefined) {
        savedAxis.summary = 'sum'
      }
      if (savedAxis.show == undefined) {
        savedAxis.show = true
      }
    } else {
      savedAxis = {
        field: axis.engineFieldName,
        summary: 'sum',
        show: true
      }
    }
    tempList.push(savedAxis)
  }

  state.basicStyleForm.seriesSummary = tempList

  if (state.basicStyleForm.seriesSummary.length > 0 && state.basicStyleForm.showSummary) {
    state.currentAxis = state.basicStyleForm.seriesSummary[0].field
    onSelectAxis(state.currentAxis)
  } else {
    state.currentAxis = undefined
    state.currentAxisSummary = undefined
  }
}
// 补齐自定义汇总字段的图表上下文默认值
const setFieldDefaultValue = field => {
  field.extField = 2
  field.chartId = props.chart.id
  field.datasetGroupId = props.chart.tableId
  field.lastSyncTime = null
  field.columnIndex = dimension().length + quota().length
  field.extractedFieldType = field.fieldType
}
onMounted(() => {
  init()
})
</script>

<template>
  <div style="width: 100%">
    <el-form
      ref="summaryForm"
      :disabled="!state.basicStyleForm.showSummary"
      :model="state.basicStyleForm"
      size="small"
      label-position="top"
    >
      <el-form-item
        v-if="showProperty('summaryLabel')"
        :label="t('chart.table_summary_label')"
        :class="'form-item-' + themes"
        class="form-item"
      >
        <el-input
          v-model="state.basicStyleForm.summaryLabel"
          type="text"
          :effect="themes"
          :max-length="10"
          @blur="changeBasicStyle('summaryLabel')"
        />
      </el-form-item>

      <el-form-item v-if="!batchOptStatus" class="form-item" :class="'form-item-' + themes">
        <el-select
          v-model="state.currentAxis"
          :class="'form-item-' + themes"
          class="form-item"
          :effect="themes"
          @change="onSelectAxis"
        >
          <el-option
            v-for="c in computedAxis"
            :key="c.engineFieldName"
            :value="c.engineFieldName"
            :label="c.chartShowName ?? c.name"
          />
        </el-select>
      </el-form-item>

      <template v-if="state.currentAxis && state.currentAxisSummary">
        <el-form-item class="form-item" :class="'form-item-' + themes">
          <el-checkbox
            size="small"
            :effect="themes"
            v-model="state.currentAxisSummary.show"
            @change="changeBasicStyle('seriesSummary')"
          >
            {{ t('chart.table_show_summary') }}
          </el-checkbox>
        </el-form-item>

        <div class="indented-container">
          <el-form-item class="form-item" :class="'form-item-' + themes">
            <el-col :span="state.currentAxisSummary.summary === 'custom' ? 19 : 22" :offset="2">
              <el-select
                v-model="state.currentAxisSummary.summary"
                :class="'form-item-' + themes"
                class="form-item"
                :effect="themes"
                :disabled="!state.currentAxisSummary.show"
                @change="changeSummaryType"
              >
                <el-option v-for="c in summaryTypes" :key="c.key" :value="c.key" :label="c.name" />
              </el-select>
            </el-col>
            <el-col v-if="state.currentAxisSummary.summary === 'custom'" :span="2" :offset="1">
              <el-icon style="cursor: pointer">
                <Setting @click="editField()" />
              </el-icon>
            </el-col>
          </el-form-item>
        </div>
      </template>
    </el-form>
  </div>
  <!--图表计算字段-->
  <el-dialog
    v-model="editCalcField"
    width="1000px"
    title="自定义总计"
    :close-on-click-modal="false"
  >
    <custom-aggr-edit ref="calcEdit" />
    <template #footer>
      <el-button secondary @click="closeEditCalc()">{{ t('dataset.cancel') }} </el-button>
      <el-button type="primary" @click="confirmEditCalc()">{{ t('dataset.confirm') }} </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="less">
.indented-container {
  margin-top: 8px;
  width: 100%;
  padding-left: 22px;
}
</style>
