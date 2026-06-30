<script lang="ts" setup>
import icon_bold_outlined from '@/assets/svg/icon_bold_outlined.svg'
import icon_italic_outlined from '@/assets/svg/icon_italic_outlined.svg'
import icon_leftAlignment_outlined from '@/assets/svg/icon_left-alignment_outlined.svg'
import icon_centerAlignment_outlined from '@/assets/svg/icon_center-alignment_outlined.svg'
import icon_rightAlignment_outlined from '@/assets/svg/icon_right-alignment_outlined.svg'
import icon_customAlignment_outlined from '@/assets/svg/icon_custom-alignment_outlined.svg'
import icon_info_outlined from '@/assets/svg/icon_info_outlined.svg'
import { computed, onMounted, PropType, reactive, watch } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { COLOR_PANEL, DEFAULT_TABLE_CELL } from '@/views/chart/components/editor/util/chart'
import { ElSpace } from 'element-plus-secondary'
import { cloneDeep, defaultsDeep } from 'lodash-es'
import { convertToAlphaColor, isAlphaColor } from '@/views/chart/components/js/util'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { SERIES_NUMBER_FIELD } from '@antv/s2'
import { getMergeableFieldOptions } from '@/views/chart/components/js/panel/common/tableMergeCells.mjs'
import { createTableCellChangePayload } from './tableCellMergeConfig.mjs'
const dvMainStore = dvMainStoreWithOut()
const { mobileInPc } = storeToRefs(dvMainStore)

const { t } = useI18n()

// 单元格面板控制数据区样式、冻结、字段级对齐和合并规则，propertyInner 决定当前图表开放项。
const props = defineProps({
  chart: {
    type: Object as PropType<ChartObj>,
    required: true
  },
  themes: {
    type: String,
    default: 'dark'
  },
  propertyInner: {
    type: Array<string>
  }
})

// 单元格配置依赖表头序号列、维度轴和指标轴，任一变化都需要重建字段候选项。
watch(
  [
    () => props.chart.customAttr.tableCell,
    () => props.chart.xAxis,
    () => props.chart.yAxis,
    () => props.chart.customAttr.tableHeader
  ],
  () => {
    init()
  },
  { deep: true }
)

const predefineColors = COLOR_PANEL

// 单元格字号与表头保持同一档位，既覆盖后台表格，也覆盖大屏表格放大展示。
const fontSizeList = computed(() => {
  const arr = []
  for (let i = 10; i <= 40; i = i + 2) {
    arr.push({
      name: i + '',
      value: i
    })
  }
  for (let i = 50; i <= 200; i = i + 10) {
    arr.push({
      name: i + '',
      value: i
    })
  }
  return arr
})

// 表单状态保存当前单元格样式快照，提交前会根据合并字段规则生成最终 payload。
const state = reactive({
  tableCellForm: {} as ChartTableCellAttr
})

// 当前字段对齐配置只表示下拉框选中的字段，完整配置仍保存在 tableCellForm.alignConfig。
const alignConfig = reactive({
  id: '',
  align: 'left'
})

// 自定义字段对齐只适用于明细表和普通表，透视表由 S2 统一计算单元格布局。
const showCustomAlign = computed(() => {
  return ['table-info', 'table-normal'].includes(props.chart.type)
})

// 对齐字段候选来自可见字段和可选序号列，顺序需要和表格列顺序保持一致。
const alignConfigOptions = reactive([])
// 可合并字段只包含连续相同值可合并的维度字段，指标列不进入该列表。
const mergeFieldOptions = reactive([])

// 父组件负责落盘和重绘，本组件只负责生成表格单元格配置变更事件。
const emit = defineEmits(['onTableCellChange'])

// 合并单元格会影响冻结和斑马纹等能力，payload 需要由专门工具统一规整。
const changeTableCell = prop => {
  if (prop === 'alignConfig') {
    // 自定义对齐只更新当前选中字段，其余字段沿用原配置。
    state.tableCellForm.alignConfig = alignConfigOptions.map(item => ({
      id: item.id,
      align: item.id === alignConfig.id ? alignConfig.align : item.align
    }))
  }
  const payload = createTableCellChangePayload(state.tableCellForm, prop, mergeFieldOptions)
  if (prop === 'mergeCells' || prop === 'mergeFields') {
    // 工具函数会根据合并字段状态反向修正开关和字段列表。
    state.tableCellForm.mergeCells = payload.mergeCells
    state.tableCellForm.mergeFields = payload.mergeFields
  }
  emit('onTableCellChange', payload, prop)
}

// 切换字段时回显该字段已保存的对齐方式，避免覆盖其他字段配置。
const changeAlignConfig = () => {
  const selected = state.tableCellForm.alignConfig.find(item => item.id === alignConfig.id)
  if (selected) {
    alignConfig.align = selected.align
  }
}

// 初始化时兼容历史配置、补齐透明度，并根据当前字段结构重建对齐和合并候选。
const init = () => {
  const tableCell = props.chart?.customAttr?.tableCell
  if (tableCell) {
    state.tableCellForm = defaultsDeep(cloneDeep(tableCell), cloneDeep(DEFAULT_TABLE_CELL))
    if (tableCell.mergeCells === undefined) {
      // 旧图表没有显式保存合并开关，默认关闭，避免打开编辑器后自动改变表格布局。
      state.tableCellForm.mergeCells = false
    }
    const alpha = props.chart.customAttr.basicStyle.alpha

    if (!isAlphaColor(state.tableCellForm.tableItemBgColor)) {
      // 旧版纯色背景按图表透明度补成 rgba，保证颜色选择器和渲染层使用同一格式。
      state.tableCellForm.tableItemBgColor = convertToAlphaColor(
        state.tableCellForm.tableItemBgColor,
        alpha
      )
    }

    if (!isAlphaColor(state.tableCellForm.tableItemSubBgColor)) {
      // 斑马纹背景同样需要补齐透明度，避免启用后与主背景透明度不一致。
      state.tableCellForm.tableItemSubBgColor = convertToAlphaColor(
        state.tableCellForm.tableItemSubBgColor,
        alpha
      )
    }

    if (['table-info', 'table-normal'].includes(props.chart.type)) {
      // 明细表和普通表支持按列对齐，普通表需要把维度和指标字段都纳入候选。
      const axis = [...props.chart?.xAxis]
      if (props.chart?.type === 'table-normal') {
        axis.push(...props.chart?.yAxis)
      }
      const alignCfg = props.chart?.customAttr?.tableCell?.alignConfig || []
      // 已保存对齐配置转为 Map，便于新增字段使用默认左对齐。
      const alignCfgMap = alignCfg?.reduce((p, n) => {
        p[n.id] = n.align
        return p
      }, {})
      alignConfigOptions.splice(0, alignConfigOptions.length)
      const tableHeader = props.chart?.customAttr?.tableHeader
      if (tableHeader?.showIndex) {
        // 序号列不是业务字段，使用固定字段名参与对齐配置。
        alignConfigOptions.push({
          id: SERIES_NUMBER_FIELD,
          label: tableHeader.indexLabel,
          align: alignCfgMap[SERIES_NUMBER_FIELD] || 'left'
        })
      }
      axis.forEach(item => {
        // 新增字段默认左对齐，已有字段沿用保存的独立对齐配置。
        const align = alignCfgMap[item.engineFieldName] || 'left'
        alignConfigOptions.push({
          id: item.engineFieldName,
          label: item.chartShowName ?? item.name,
          align
        })
      })
      if (alignConfigOptions.length) {
        // 当前选中字段不存在时回退到第一项，避免下拉框引用失效字段。
        const exist = alignConfigOptions.findIndex(item => item.id === alignConfig.id) !== -1
        if (!exist) {
          alignConfig.id = alignConfigOptions[0].id
          alignConfig.align = alignConfigOptions[0].align
        }
      } else {
        alignConfig.id = ''
        alignConfig.align = 'left'
      }
    }
    mergeFieldOptions.splice(0, mergeFieldOptions.length)
    if (props.chart.type === 'table-info') {
      // 明细表合并只允许选择可合并维度字段，字段被隐藏后要清理失效配置。
      const options = getMergeableFieldOptions(props.chart?.xAxis || [])
      mergeFieldOptions.push(...options)
      const optionIds = new Set(options.map(item => item.id))
      if (state.tableCellForm.mergeCells && !options.length) {
        // 没有可合并字段时强制关闭合并，防止保存不可执行的合并规则。
        state.tableCellForm.mergeCells = false
        state.tableCellForm.mergeFields = []
      } else if (Array.isArray(tableCell.mergeFields)) {
        // 保留仍然存在的字段，清理已经从图表中移除的历史字段。
        state.tableCellForm.mergeFields = tableCell.mergeFields.filter(id => optionIds.has(id))
      } else if (state.tableCellForm.mergeCells) {
        // 老配置只保存了开关时，默认使用所有可合并字段恢复历史行为。
        state.tableCellForm.mergeFields = options.map(item => item.id)
      }
    }
  }
}
// 属性显示由图表类型和编辑器配置决定，避免提交当前渲染器不支持的单元格字段。
const showProperty = prop => props.propertyInner?.includes(prop)

onMounted(() => {
  // 挂载后立即根据当前图表字段重建对齐、合并和颜色兼容配置。
  init()
})
</script>

<template>
  <el-form size="small" ref="tableCellForm" :model="state.tableCellForm" label-position="top">
    <el-form-item
      :label="t('chart.backgroundColor')"
      class="form-item"
      :class="'form-item-' + themes"
      v-if="showProperty('tableItemBgColor') && state.tableCellForm.tableItemBgColor"
    >
      <el-color-picker
        :effect="themes"
        is-custom
        :trigger-width="108"
        v-model="state.tableCellForm.tableItemBgColor"
        :predefine="predefineColors"
        show-alpha
        @change="changeTableCell('tableItemBgColor')"
      />
    </el-form-item>
    <el-form-item
      :class="'form-item-' + themes"
      class="form-item"
      v-if="showProperty('enableTableCrossBG')"
      label=""
    >
      <!-- 斑马纹和合并单元格同时开启会破坏连续行判断，合并开启时禁用斑马纹。 -->
      <el-checkbox
        v-model="state.tableCellForm.enableTableCrossBG"
        :effect="themes"
        :disabled="showProperty('mergeCells') && state.tableCellForm.mergeCells"
        @change="changeTableCell('enableTableCrossBG')"
      >
        <span class="data-area-label">
          <span style="margin-right: 4px">{{ t('chart.stripe') }}</span>
          <el-tooltip
            class="item"
            effect="dark"
            placement="bottom"
            v-if="state.tableCellForm.mergeCells"
          >
            <template #content>
              <div>{{ t('chart.table_cross_bg_tip') }}</div>
            </template>
            <el-icon class="hint-icon" :class="{ 'hint-icon--dark': themes === 'dark' }">
              <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
            </el-icon>
          </el-tooltip>
        </span>
      </el-checkbox>
    </el-form-item>
    <el-form-item
      :class="'form-item-' + themes"
      class="form-item"
      label=""
      v-if="showProperty('tableItemSubBgColor') && state.tableCellForm.tableItemSubBgColor"
    >
      <el-color-picker
        v-model="state.tableCellForm.tableItemSubBgColor"
        :effect="themes"
        :predefine="predefineColors"
        :disabled="!state.tableCellForm.enableTableCrossBG"
        is-custom
        show-alpha
        @change="changeTableCell('tableItemSubBgColor')"
      />
    </el-form-item>
    <el-space>
      <el-form-item
        class="form-item"
        :class="'form-item-' + themes"
        v-if="showProperty('tableFontColor')"
        :label="t('chart.text')"
      >
        <el-color-picker
          :effect="themes"
          is-custom
          v-model="state.tableCellForm.tableFontColor"
          :predefine="predefineColors"
          @change="changeTableCell('tableFontColor')"
        />
      </el-form-item>
      <el-form-item
        class="form-item"
        :class="'form-item-' + themes"
        v-if="showProperty('tableItemFontSize')"
      >
        <template #label>&nbsp;</template>
        <el-select
          style="width: 58px"
          :effect="themes"
          v-model="state.tableCellForm.tableItemFontSize"
          @change="changeTableCell('tableItemFontSize')"
        >
          <el-option
            v-for="option in fontSizeList"
            :key="option.value"
            :label="option.name"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
    </el-space>
    <el-space :class="{ 'mobile-style': mobileInPc }">
      <el-form-item class="form-item" :class="'form-item-' + themes">
        <el-checkbox
          :effect="themes"
          class="icon-checkbox"
          v-model="state.tableCellForm.isBolder"
          @change="changeTableCell('isBolder')"
        >
          <el-tooltip effect="dark" placement="top">
            <template #content>
              {{ t('chart.bolder') }}
            </template>
            <div
              class="icon-btn"
              :class="{ dark: themes === 'dark', active: state.tableCellForm.isBolder }"
            >
              <el-icon>
                <Icon name="icon_bold_outlined"><icon_bold_outlined class="svg-icon" /></Icon>
              </el-icon>
            </div>
          </el-tooltip>
        </el-checkbox>
      </el-form-item>

      <el-form-item class="form-item" :class="'form-item-' + themes">
        <el-checkbox
          :effect="themes"
          class="icon-checkbox"
          v-model="state.tableCellForm.isItalic"
          @change="changeTableCell('isItalic')"
        >
          <el-tooltip effect="dark" placement="top">
            <template #content>
              {{ t('chart.italic') }}
            </template>
            <div
              class="icon-btn"
              :class="{ dark: themes === 'dark', active: state.tableCellForm.isItalic }"
            >
              <el-icon>
                <Icon name="icon_italic_outlined"><icon_italic_outlined class="svg-icon" /></Icon>
              </el-icon>
            </div>
          </el-tooltip>
        </el-checkbox>
      </el-form-item>

      <div class="position-divider" :class="'position-divider--' + themes"></div>
      <el-form-item
        class="form-item"
        :class="'form-item-' + themes"
        v-if="showProperty('tableItemAlign')"
      >
        <el-radio-group
          class="icon-radio-group"
          v-model="state.tableCellForm.tableItemAlign"
          @change="changeTableCell('tableItemAlign')"
        >
          <el-radio value="left">
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.text_pos_left') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: state.tableCellForm.tableItemAlign === 'left'
                }"
              >
                <el-icon>
                  <Icon name="icon_left-alignment_outlined"
                    ><icon_leftAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
          <el-radio value="center">
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.text_pos_center') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: state.tableCellForm.tableItemAlign === 'center'
                }"
              >
                <el-icon>
                  <Icon name="icon_center-alignment_outlined"
                    ><icon_centerAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
          <el-radio value="right">
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.text_pos_right') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: state.tableCellForm.tableItemAlign === 'right'
                }"
              >
                <el-icon>
                  <Icon name="icon_right-alignment_outlined"
                    ><icon_rightAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
          <el-radio label="custom" v-if="showCustomAlign">
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('commons.custom') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: state.tableCellForm.tableItemAlign === 'custom'
                }"
              >
                <el-icon>
                  <Icon name="icon_custom-alignment_outlined"
                    ><icon_customAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
        </el-radio-group>
      </el-form-item>
    </el-space>

    <el-row
      v-if="showProperty('tableItemAlign') && state.tableCellForm.tableItemAlign === 'custom'"
    >
      <el-col :span="12">
        <!-- 字段级对齐先选字段，再对该字段写入独立对齐方式 -->
        <el-select :effect="themes" v-model="alignConfig.id" @change="changeAlignConfig">
          <el-option
            v-for="item in alignConfigOptions"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
      </el-col>
      <el-col :span="12" style="display: flex; align-items: center">
        <el-radio-group
          class="icon-radio-group"
          v-model="alignConfig.align"
          @change="changeTableCell('alignConfig')"
        >
          <el-radio label="left">
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.text_pos_left') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: alignConfig.align === 'left'
                }"
              >
                <el-icon>
                  <Icon name="icon_left-alignment_outlined"
                    ><icon_leftAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
          <el-radio label="center">
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.text_pos_center') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: alignConfig.align === 'center'
                }"
              >
                <el-icon>
                  <Icon name="icon_center-alignment_outlined"
                    ><icon_centerAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
          <el-radio label="right">
            <el-tooltip effect="dark" placement="top">
              <template #content>
                {{ t('chart.text_pos_right') }}
              </template>
              <div
                class="icon-btn"
                :class="{
                  dark: themes === 'dark',
                  active: alignConfig.align === 'right'
                }"
              >
                <el-icon>
                  <Icon name="icon_right-alignment_outlined"
                    ><icon_rightAlignment_outlined class="svg-icon"
                  /></Icon>
                </el-icon>
              </div>
            </el-tooltip>
          </el-radio>
        </el-radio-group>
      </el-col>
    </el-row>
    <el-row :gutter="8">
      <el-col :span="12">
        <el-form-item
          :label="t('visualization.lineHeight')"
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('tableItemHeight')"
        >
          <el-input-number
            :effect="themes"
            controls-position="right"
            v-model="state.tableCellForm.tableItemHeight"
            :min="20"
            :max="1000"
            @change="changeTableCell('tableItemHeight')"
          />
        </el-form-item>
      </el-col>
    </el-row>
    <el-form-item
      class="form-item"
      :class="'form-item-' + themes"
      v-if="showProperty('tableFreeze')"
    >
      <!-- 合并单元格会改变行列定位，开启后冻结配置需禁用 -->
      <el-checkbox
        size="small"
        :effect="themes"
        :disabled="showProperty('mergeCells') && state.tableCellForm.mergeCells"
        v-model="state.tableCellForm.tableFreeze"
        @change="changeTableCell('tableFreeze')"
      >
        <span class="data-area-label">
          <span style="margin-right: 4px">{{ t('chart.table_freeze') }}</span>
          <el-tooltip
            class="item"
            effect="dark"
            placement="bottom"
            v-if="state.tableCellForm.mergeCells"
          >
            <template #content>
              <div>{{ t('chart.table_freeze_tip') }}</div>
            </template>
            <el-icon class="hint-icon" :class="{ 'hint-icon--dark': themes === 'dark' }">
              <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
            </el-icon>
          </el-tooltip>
        </span>
      </el-checkbox>
    </el-form-item>
    <el-row :gutter="8" v-if="showProperty('tableFreeze')">
      <el-col :span="12">
        <el-form-item
          :label="t('chart.table_col_freeze_tip')"
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('tableColumnFreezeHead')"
        >
          <!-- 冻结列数只在冻结开关开启且未合并单元格时生效。 -->
          <el-input-number
            :effect="themes"
            controls-position="right"
            v-model="state.tableCellForm.tableColumnFreezeHead"
            :disabled="
              (showProperty('mergeCells') && state.tableCellForm.mergeCells) ||
              !state.tableCellForm.tableFreeze
            "
            :min="0"
            :max="100"
            @change="changeTableCell('tableColumnFreezeHead')"
          />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item
          :label="t('chart.table_row_freeze_tip')"
          class="form-item"
          :class="'form-item-' + themes"
          v-if="showProperty('tableRowFreezeHead')"
        >
          <el-input-number
            :effect="themes"
            controls-position="right"
            v-model="state.tableCellForm.tableRowFreezeHead"
            :disabled="
              (showProperty('mergeCells') && state.tableCellForm.mergeCells) ||
              !state.tableCellForm.tableFreeze
            "
            :min="0"
            :max="100"
            @change="changeTableCell('tableRowFreezeHead')"
          />
        </el-form-item>
      </el-col>
    </el-row>
    <el-form-item
      class="form-item"
      :class="'form-item-' + themes"
      v-if="showProperty('mergeCells')"
    >
      <el-checkbox
        size="small"
        :effect="themes"
        :disabled="showProperty('mergeFields') && !mergeFieldOptions.length"
        v-model="state.tableCellForm.mergeCells"
        @change="changeTableCell('mergeCells')"
      >
        <span class="data-area-label">
          <span style="margin-right: 4px">{{ t('chart.merge_cells') }}</span>
          <el-tooltip class="item" effect="dark" placement="bottom">
            <template #content>
              <div>{{ t('chart.merge_cells_tips') }}</div>
            </template>
            <el-icon class="hint-icon" :class="{ 'hint-icon--dark': themes === 'dark' }">
              <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
            </el-icon>
          </el-tooltip>
        </span>
      </el-checkbox>
    </el-form-item>
    <el-form-item
      :label="t('chart.merge_cells_fields')"
      class="form-item"
      :class="'form-item-' + themes"
      v-if="showProperty('mergeFields') && state.tableCellForm.mergeCells"
    >
      <!-- 合并字段顺序与表格维度顺序一致，渲染层按连续相同值合并。 -->
      <el-select
        :effect="themes"
        v-model="state.tableCellForm.mergeFields"
        multiple
        clearable
        collapse-tags
        :placeholder="t('chart.merge_cells_fields_placeholder')"
        @change="changeTableCell('mergeFields')"
      >
        <el-option
          v-for="item in mergeFieldOptions"
          :key="item.id"
          :label="item.label"
          :value="item.id"
        />
      </el-select>
      <div class="merge-fields-tip" :class="{ 'merge-fields-tip--dark': themes === 'dark' }">
        {{ t('chart.merge_cells_fields_tip') }}
      </div>
    </el-form-item>
    <el-form-item
      class="form-item"
      :class="'form-item-' + themes"
      v-if="showProperty('showHorizonBorder')"
    >
      <el-checkbox
        size="small"
        :effect="themes"
        v-model="state.tableCellForm.showHorizonBorder"
        @change="changeTableCell('showHorizonBorder')"
      >
        {{ t('chart.table_cell_show_horizon_border') }}
      </el-checkbox>
    </el-form-item>
    <el-form-item
      class="form-item"
      :class="'form-item-' + themes"
      v-if="showProperty('showVerticalBorder')"
    >
      <el-checkbox
        size="small"
        :effect="themes"
        v-model="state.tableCellForm.showVerticalBorder"
        @change="changeTableCell('showVerticalBorder')"
      >
        {{ t('chart.table_cell_show_vertical_border') }}
      </el-checkbox>
    </el-form-item>
  </el-form>
</template>

<style lang="less" scoped>
.icon-btn {
  font-size: 16px;
  line-height: 16px;
  width: 24px;
  height: 24px;
  text-align: center;
  vertical-align: middle;
  border-radius: 6px;
  padding-top: 4px;

  color: #1f2329;

  cursor: pointer;

  &.dark {
    color: #a6a6a6;
    &.active {
      color: var(--ed-color-primary);
      background-color: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1));
    }
    &:hover {
      background-color: rgba(255, 255, 255, 0.1);
    }
  }

  &.active {
    color: var(--ed-color-primary);
    background-color: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1));
  }

  &:hover {
    background-color: rgba(31, 35, 41, 0.1);
  }
}
.icon-radio-group {
  :deep(.ed-radio) {
    margin-right: 8px;

    &:last-child {
      margin-right: 0;
    }
  }
  :deep(.ed-radio__input) {
    display: none;
  }
  :deep(.ed-radio__label) {
    padding: 0;
  }
}
.position-divider {
  width: 1px;
  height: 18px;
  margin-bottom: 8px;
  background: rgba(31, 35, 41, 0.15);

  &.position-divider--dark {
    background: rgba(235, 235, 235, 0.15);
  }
}
.icon-checkbox {
  :deep(.ed-checkbox__input) {
    display: none;
  }
  :deep(.ed-checkbox__label) {
    padding: 0;
  }
}

.mobile-style {
  margin-top: 25px;
}
.data-area-label {
  text-align: left;
  position: relative;
  width: 100%;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.merge-fields-tip {
  margin-top: 6px;
  color: rgba(31, 35, 41, 0.6);
  font-size: 12px;
  line-height: 18px;

  &.merge-fields-tip--dark {
    color: rgba(235, 235, 235, 0.65);
  }
}
</style>
