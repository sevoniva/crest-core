<script lang="tsx" setup>
import icon_sortAToZ_outlined from '@/assets/svg/icon_sort-a-to-z_outlined.svg'
import icon_sortZToA_outlined from '@/assets/svg/icon_sort-z-to-a_outlined.svg'
import icon_sort_outlined from '@/assets/svg/icon_sort_outlined.svg'
import icon_deleteTrash_outlined from '@/assets/svg/icon_delete-trash_outlined.svg'
import icon_down_outlined1 from '@/assets/svg/icon_down_outlined-1.svg'
import icon_right_outlined from '@/assets/svg/icon_right_outlined.svg'
import icon_done_outlined from '@/assets/svg/icon_done_outlined.svg'
import icon_functions_outlined from '@/assets/svg/icon_functions_outlined.svg'
import icon_visible_outlined from '@/assets/svg/icon_visible_outlined.svg'
import icon_invisible_outlined from '@/assets/svg/icon_invisible_outlined.svg'
import icon_edit_outlined from '@/assets/svg/icon_edit_outlined.svg'
import iconFilter from '@/assets/svg/icon-filter.svg'
import { useI18n } from '@/hooks/web/useI18n'
import { computed, onMounted, reactive, ref, toRefs, watch } from 'vue'
import { formatterItem } from '@/views/chart/components/js/formatter'
import { getItemType, resetValueFormatter } from '@/views/chart/components/editor/drag-item/utils'
import { quotaViews, notSupportAccumulateViews } from '@/views/chart/components/js/util'
import { SUPPORT_Y_M } from '@/views/chart/components/editor/util/chart'
import { fieldType } from '@/utils/attr'
import { iconFieldMap } from '@/components/icon-group/field-list'

const { t } = useI18n()

// 指标字段标签颜色类型
const tagType = ref('success')

// 指标拖拽项内部状态，保存格式化配置和图表能力开关
const state = reactive({
  formatterItem: formatterItem,
  disableEditCompare: false,
  // quotaViews 和 notSupportAccumulateViews 由图表能力表控制菜单可用性。
  quotaViews: quotaViews,
  notSupportAccumulateViews: notSupportAccumulateViews
})

// 指标拖拽项入参，包含字段、图表上下文和所在轴区域
const props = defineProps({
  param: {
    type: Object,
    required: false
  },
  item: {
    type: Object,
    required: true
  },
  index: {
    type: Number,
    required: true
  },
  chart: {
    type: Object,
    required: true
  },
  dimensionData: {
    type: Array,
    required: true
  },
  quotaData: {
    type: Array,
    required: true
  },
  type: {
    type: String,
    required: true
  },
  themes: {
    type: String,
    default: 'dark'
  }
})

// 指标拖拽项向编辑器上层抛出的操作事件
const emit = defineEmits([
  'onQuotaItemRemove',
  'onCustomSort',
  'onQuotaItemChange',
  'onNameEdit',
  'editItemFilter',
  'editItemCompare',
  'valueFormatter',
  'onToggleHide',
  'editSortPriority'
])

const { item, chart } = toRefs(props)
// 根据当前主题反转提示框主题，保证弹层可读
const toolTip = computed(() => {
  return props.themes || 'dark'
})
// 字段数据或图表类型变化时刷新标签颜色
watch(
  [() => props.quotaData, () => props.item, () => props.chart.type],
  () => {
    getItemTagType()
  },
  { deep: true }
)

// 图表配置变化时刷新同环比可用性，并清理不支持的快速计算
watch(
  () => props.chart,
  () => {
    isEnableCompare()
    // 不支持累加计算的图表自动清理快速计算配置
    if (
      state.notSupportAccumulateViews.indexOf(chart.value.type) > -1 &&
      item.value.compareCalc.type === 'accumulate'
    ) {
      quickCalc({ type: 'none' })
    }
  },
  { deep: true }
)
const AXIS_FORMAT_VIEW = ['table-normal', 'table-info', 'table-pivot', 'indicator', 'rich-text']
// 当前指标是否展示数值格式化入口
const showValueFormatter = computed<boolean>(() => {
  // 只有表格、指标和富文本等轴值展示型图表开放数值格式化入口。
  return (
    AXIS_FORMAT_VIEW.includes(props.chart.type) &&
    (props.item.fieldType === 2 || props.item.fieldType === 3)
  )
})

// 根据图表类型和时间维度判断同环比配置是否可用
const isEnableCompare = () => {
  // 指标卡始终开放同环比配置
  if (chart.value.type === 'indicator') {
    state.disableEditCompare = false
    return
  }
  let xAxis = null
  if (Object.prototype.toString.call(chart.value.xAxis) === '[object Array]') {
    // 兼容编辑器内数组态和持久化字符串态两种轴数据结构。
    xAxis = JSON.parse(JSON.stringify(chart.value.xAxis))
  } else {
    xAxis = JSON.parse(chart.value.xAxis)
  }
  const t1 = xAxis.filter(ele => {
    return ele.fieldType === 1 && SUPPORT_Y_M.includes(ele.dateStyle)
  })

  if (chart.value.type === 'table-pivot') {
    let xAxisExt = null
    if (Object.prototype.toString.call(chart.value.xAxisExt) === '[object Array]') {
      // 透视表的列维度也可能承载年月字段，需要一并参与同环比能力判断。
      xAxisExt = JSON.parse(JSON.stringify(chart.value.xAxisExt))
    } else {
      xAxisExt = JSON.parse(chart.value.xAxisExt)
    }
    const t2 = xAxisExt.filter(ele => {
      return ele.fieldType === 1 && SUPPORT_Y_M.includes(ele.dateStyle)
    })

    t1.push(...t2)
  }

  // 同环比配置仅对类别轴和维度中的时间字段生效。
  if (
    t1.length > 0 &&
    chart.value.type !== 'label' &&
    chart.value.type !== 'gauge' &&
    chart.value.type !== 'liquid'
  ) {
    state.disableEditCompare = false
  } else {
    state.disableEditCompare = true
  }
}

// 分发指标菜单点击操作
const clickItem = param => {
  if (!param) {
    return
  }
  switch (param.type) {
    case 'rename':
      showRename()
      break
    case 'remove':
      removeItem()
      break
    case 'filter':
      editFilter()
      break
    case 'formatter':
      valueFormatter()
      break
    case 'toggleHide':
      toggleHide()
      break
    case 'sortPriority':
      emit('editSortPriority')
      break
    default:
      break
  }
}

// 构造通用菜单命令对象
const beforeClickItem = type => {
  return {
    type
  }
}

// 处理指标排序，普通排序直接更新字段，自定义排序交给上层弹窗
const sort = param => {
  if (param.type === 'custom_sort') {
    // 自定义排序需要打开上层弹窗维护排序数组，这里只传递字段位置。
    const item = {
      index: props.index,
      sort: param.type
    }
    emit('onCustomSort', item)
  } else {
    // 普通升降序直接写回当前字段，并清空历史自定义排序。
    item.value.index = props.index
    item.value.sort = param.type
    item.value.customSort = []
    emit('onQuotaItemChange', item.value)
  }
}

// 构造排序菜单命令对象
const beforeSort = type => {
  return {
    type: type
  }
}

// 切换指标汇总方式
const summary = param => {
  item.value.summary = param.type
  emit('onQuotaItemChange', item.value)
}

// 构造汇总菜单命令对象
const beforeSummary = type => {
  return {
    type: type
  }
}

// 打开指标重命名弹窗
const showRename = () => {
  item.value.index = props.index
  item.value.renameType = props.type
  emit('onNameEdit', item.value)
}

// 删除当前指标字段
const removeItem = () => {
  item.value.index = props.index
  item.value.removeType = props.type
  emit('onQuotaItemRemove', item.value)
}

// 根据字段来源和类型刷新标签颜色
const getItemTagType = () => {
  tagType.value = getItemType(props.dimensionData, props.quotaData, props.item)
}

// 打开指标过滤配置弹窗
const editFilter = () => {
  item.value.index = props.index
  item.value.filterType = props.type
  emit('editItemFilter', item.value)
}

// 处理快速计算类型切换
const quickCalc = param => {
  switch (param.type) {
    case 'none':
      // 取消快速计算时恢复默认数值格式
      resetValueFormatter(item.value)
      item.value.compareCalc.type = 'none'
      emit('onQuotaItemChange', item.value)
      break
    case 'setting':
      // 自定义同环比配置前，非指标卡恢复默认数值格式
      if (chart.value.type !== 'indicator') {
        resetValueFormatter(item.value)
      }
      editCompare()
      break
    case 'percent':
      // 选择占比时自动切换为百分比并保留两位小数
      item.value.formatterCfg.type = 'percent'
      item.value.formatterCfg.decimalCount = 2

      item.value.compareCalc.type = 'percent'
      emit('onQuotaItemChange', item.value)
      break
    case 'accumulate':
      // 累加计算不额外修改格式化配置，由图表渲染层按当前格式输出。
      item.value.compareCalc.type = 'accumulate'
      emit('onQuotaItemChange', item.value)
      break
    default:
      break
  }
}

// 构造快速计算菜单命令对象
const beforeQuickCalc = type => {
  return {
    type: type
  }
}

// 打开同环比编辑弹窗
const editCompare = () => {
  item.value.index = props.index
  item.value.calcType = props.type
  emit('editItemCompare', item.value)
}

// 打开数值格式化配置弹窗
const valueFormatter = () => {
  item.value.index = props.index
  item.value.formatterType = props.type
  emit('valueFormatter', item.value)
}
// 切换字段隐藏状态
const toggleHide = () => {
  item.value.index = props.index
  item.value.hide = !item.value.hide
  item.value.axisType = props.type
  // 隐藏状态由上层按轴类型持久化，表格渲染时再决定是否展示字段。
  emit('onToggleHide', item.value)
}
// 表格字段隐藏后在标签上展示隐藏图标
const showHideIcon = computed(() => {
  return ['tale-info', 'table-normal'].includes(props.chart.type) && item.value.hide
})

const NOT_SUPPORT_SORT = [
  'circle-packing',
  'indicator',
  'liquid',
  'gauge',
  'word-cloud',
  'stock-line',
  'treemap'
]

// 判断当前图表和字段区域是否展示排序入口
const showSort = computed(() => {
  if (chart.value.type === 'multi-scatter') {
    return false
  }
  // 扩展标签、提示和气泡字段不参与主数据排序。
  return (
    props.type !== 'extLabel' &&
    props.type !== 'extTooltip' &&
    props.type !== 'extBubble' &&
    !NOT_SUPPORT_SORT.includes(chart.value.type) &&
    !chart.value.type.includes('chart-mix')
  )
})

// 同环比计算类型白名单
const yoyLabel = ['day_mom', 'month_yoy', 'year_yoy', 'month_mom', 'year_mom']

onMounted(() => {
  isEnableCompare()
  getItemTagType()
})
</script>

<template>
  <span class="item-style">
    <!-- 指标标签集成字段类型、排序状态、汇总方式和隐藏状态，点击后展开字段操作菜单。 -->
    <el-dropdown :effect="themes" trigger="click" @command="clickItem">
      <el-tag
        class="item-axis father"
        :class="['editor-' + props.themes, `${themes}_icon-right`]"
        :style="{ backgroundColor: tagType + '0a', border: '1px solid ' + tagType }"
      >
        <span style="display: flex; color: #646a73">
          <el-icon v-if="'asc' === item.sort && showSort">
            <Icon name="icon_sort-a-to-z_outlined"
              ><icon_sortAToZ_outlined class="svg-icon"
            /></Icon>
          </el-icon>
          <el-icon v-if="'desc' === item.sort && showSort">
            <Icon name="icon_sort-z-to-a_outlined"
              ><icon_sortZToA_outlined class="svg-icon"
            /></Icon>
          </el-icon>
          <el-icon v-if="'custom_sort' === item.sort && showSort">
            <Icon name="icon_sort_outlined"><icon_sort_outlined class="svg-icon" /></Icon>
          </el-icon>
          <el-icon>
            <Icon :className="`field-icon-${fieldType[[2, 3].includes(item.fieldType) ? 2 : 0]}`"
              ><component
                :class="`field-icon-${fieldType[[2, 3].includes(item.fieldType) ? 2 : 0]}`"
                class="svg-icon"
                :is="iconFieldMap[fieldType[item.fieldType]]"
              ></component
            ></Icon>
          </el-icon>
        </span>
        <el-tooltip :effect="toolTip" placement="top">
          <template #content>
            <table>
              <tbody>
                <tr>
                  <td>{{ t('dataset.field_origin_name') }}</td>
                  <td>:</td>
                  <td>{{ item.name }}</td>
                </tr>
                <tr>
                  <td>{{ t('chart.show_name') }}</td>
                  <td>:</td>
                  <td>{{ item.chartShowName ? item.chartShowName : item.name }}</td>
                </tr>
              </tbody>
            </table>
          </template>
          <span
            class="item-span-style"
            :class="{
              'hidden-status': showHideIcon,
              'sort-status': showSort && item.sort !== 'none'
            }"
          >
            <span class="item-name">{{ item.chartShowName ? item.chartShowName : item.name }}</span>
            <span
              v-if="item.summary !== '' && chart.type !== 'multi-scatter'"
              class="item-right-summary"
            >
              ({{ t('chart.' + item.summary) }})
            </span>
            <span :data-id="item.id" class="node-id_private"></span>
          </span>
        </el-tooltip>
        <span
          v-if="false && chart.type !== 'table-info' && item.summary && !item.chartId"
          class="summary-span"
        >
          {{ t('chart.' + item.summary) }}
          <span
            v-if="
              item.compareCalc &&
              item.compareCalc.type &&
              item.compareCalc.type !== '' &&
              item.compareCalc.type !== 'none'
            "
          >
            -{{ t('chart.' + item.compareCalc.type) }}
          </span>
        </span>
        <el-icon v-if="showHideIcon" style="margin-left: 4px">
          <Icon>
            <icon_invisible_outlined
              :class="`field-icon-${fieldType[[2, 3].includes(item.fieldType) ? 2 : 0]}`"
              class="svg-icon inner-class"
            />
          </Icon>
        </el-icon>
        <el-tooltip :effect="toolTip" placement="top">
          <template #content>
            <span>{{ t('chart.delete') }}</span>
          </template>
          <el-icon class="child remove-icon">
            <Icon class-name="inner-class" name="icon_delete-trash_outlined"
              ><icon_deleteTrash_outlined @click="removeItem" class="svg-icon inner-class"
            /></Icon>
          </el-icon>
        </el-tooltip>

        <el-icon class="child" style="position: absolute; top: 7px; right: 10px; cursor: pointer">
          <Icon name="icon_down_outlined-1"><icon_down_outlined1 class="svg-icon" /></Icon>
        </el-icon>
      </el-tag>
      <template #dropdown>
        <el-dropdown-menu
          :effect="themes"
          class="drop-style"
          :class="themes === 'dark' ? 'dark-dimension-quota' : ''"
        >
          <el-dropdown-item
            @click.prevent
            v-if="!['table-info', 'multi-scatter'].includes(chart.type) && item.summary !== ''"
          >
            <!-- 汇总方式只对可聚合指标开放，文本和日期字段会隐藏不适用的聚合项。 -->
            <el-dropdown
              :effect="themes"
              placement="right-start"
              popper-class="data-dropdown_popper_mr9"
              style="width: 100%; height: 100%"
              @command="summary"
            >
              <span class="el-dropdown-link inner-dropdown-menu menu-item-padding">
                <span class="menu-item-content">
                  <el-icon>
                    <Icon name="icon_functions_outlined"
                      ><icon_functions_outlined class="svg-icon"
                    /></Icon>
                  </el-icon>
                  <span>{{ t('chart.summary') }}</span>
                  <span class="summary-span-item">({{ t('chart.' + item.summary) }})</span>
                </span>
                <el-icon>
                  <Icon name="icon_right_outlined"><icon_right_outlined class="svg-icon" /></Icon>
                </el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu
                  :effect="themes"
                  class="drop-style sub"
                  :class="themes === 'dark' ? 'dark-dimension-quota' : ''"
                >
                  <el-dropdown-item
                    class="menu-item-padding"
                    v-if="item.id !== '-1' && ![0, 1, 5, 7].includes(item.fieldType)"
                    :command="beforeSummary('sum')"
                  >
                    <span
                      class="sub-menu-content"
                      :class="'sum' === item.summary ? 'content-active' : ''"
                    >
                      {{ t('chart.sum') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon name="icon_done_outlined" v-if="'sum' === item.summary"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item
                    class="menu-item-padding"
                    v-if="item.id !== '-1' && ![0, 1, 5, 7].includes(item.fieldType)"
                    :command="beforeSummary('avg')"
                  >
                    <span
                      class="sub-menu-content"
                      :class="'avg' === item.summary ? 'content-active' : ''"
                    >
                      {{ t('chart.avg') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon name="icon_done_outlined" v-if="'avg' === item.summary"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item
                    class="menu-item-padding"
                    v-if="item.id !== '-1' && ![0, 1, 5, 7].includes(item.fieldType)"
                    :command="beforeSummary('max')"
                  >
                    <span
                      class="sub-menu-content"
                      :class="'max' === item.summary ? 'content-active' : ''"
                    >
                      {{ t('chart.max') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon name="icon_done_outlined" v-if="'max' === item.summary"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item
                    class="menu-item-padding"
                    v-if="item.id !== '-1' && ![0, 1, 5, 7].includes(item.fieldType)"
                    :command="beforeSummary('min')"
                  >
                    <span
                      class="sub-menu-content"
                      :class="'min' === item.summary ? 'content-active' : ''"
                    >
                      {{ t('chart.min') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon name="icon_done_outlined" v-if="'min' === item.summary"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item
                    class="menu-item-padding"
                    v-if="item.id !== '-1' && ![0, 1, 5, 7].includes(item.fieldType)"
                    :command="beforeSummary('stddev_pop')"
                  >
                    <span
                      class="sub-menu-content"
                      :class="'stddev_pop' === item.summary ? 'content-active' : ''"
                    >
                      {{ t('chart.stddev_pop') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon name="icon_done_outlined" v-if="'stddev_pop' === item.summary"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item
                    class="menu-item-padding"
                    v-if="item.id !== '-1' && ![0, 1, 5, 7].includes(item.fieldType)"
                    :command="beforeSummary('var_pop')"
                  >
                    <span
                      class="sub-menu-content"
                      :class="'var_pop' === item.summary ? 'content-active' : ''"
                    >
                      {{ t('chart.var_pop') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon name="icon_done_outlined" v-if="'var_pop' === item.summary"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item class="menu-item-padding" :command="beforeSummary('count')">
                    <span
                      class="sub-menu-content"
                      :class="'count' === item.summary ? 'content-active' : ''"
                    >
                      {{ t('chart.count') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon name="icon_done_outlined" v-if="'count' === item.summary"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item
                    class="menu-item-padding"
                    v-if="item.id !== '-1'"
                    :command="beforeSummary('count_distinct')"
                  >
                    <span
                      class="sub-menu-content"
                      :class="'count_distinct' === item.summary ? 'content-active' : ''"
                    >
                      {{ t('chart.count_distinct') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon name="icon_done_outlined" v-if="'count_distinct' === item.summary"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-dropdown-item>

          <!-- 快速计算覆盖无计算、同环比、占比和累加，具体可用性由图表类型和时间维度决定。 -->
          <el-dropdown-item
            @click.prevent
            v-if="
              !['table-info', 'bullet-graph', 'multi-scatter'].includes(chart.type) &&
              props.type !== 'extBubble'
            "
          >
            <el-dropdown
              placement="right-start"
              :effect="themes"
              popper-class="data-dropdown_popper_mr9"
              style="width: 100%; height: 100%"
              @command="quickCalc"
            >
              <span class="el-dropdown-link inner-dropdown-menu menu-item-padding">
                <span class="menu-item-content">
                  <el-icon>
                    <!--                    <Icon name="icon_describe_outlined" ><icon_describe_outlined class="svg-icon" /></Icon>-->
                  </el-icon>
                  <span>{{ t('chart.quick_calc') }}</span>
                  <span class="summary-span-item">
                    ({{
                      !item.compareCalc ? t('chart.none') : t('chart.' + item.compareCalc.type)
                    }})
                  </span>
                </span>
                <el-icon>
                  <Icon name="icon_right_outlined"><icon_right_outlined class="svg-icon" /></Icon>
                </el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu
                  :effect="themes"
                  class="drop-style sub"
                  :class="themes === 'dark' ? 'dark-dimension-quota' : ''"
                >
                  <el-dropdown-item class="menu-item-padding" :command="beforeQuickCalc('none')">
                    <span
                      class="sub-menu-content"
                      :class="'none' === item.compareCalc.type ? 'content-active' : ''"
                    >
                      {{ t('chart.none') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon name="icon_done_outlined" v-if="'none' === item.compareCalc.type"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item
                    class="menu-item-padding"
                    :disabled="state.disableEditCompare"
                    :command="beforeQuickCalc('setting')"
                  >
                    <div
                      class="sub-menu-content"
                      :class="yoyLabel.includes(item.compareCalc.type) ? 'content-active' : ''"
                      :disabled="state.disableEditCompare"
                    >
                      {{ t('chart.yoy_label') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon
                          name="icon_done_outlined"
                          v-if="yoyLabel.includes(item.compareCalc.type)"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </div>
                  </el-dropdown-item>
                  <el-dropdown-item
                    class="menu-item-padding"
                    :disabled="state.quotaViews.indexOf(chart.type) > -1"
                    :command="beforeQuickCalc('percent')"
                  >
                    <div
                      class="sub-menu-content"
                      :class="'percent' === item.compareCalc.type ? 'content-active' : ''"
                      :disabled="state.quotaViews.indexOf(chart.type) > -1"
                    >
                      {{ t('chart.percent') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon name="icon_done_outlined" v-if="'percent' === item.compareCalc.type"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </div>
                  </el-dropdown-item>
                  <el-dropdown-item
                    class="menu-item-padding"
                    :disabled="state.notSupportAccumulateViews.indexOf(chart.type) > -1"
                    :command="beforeQuickCalc('accumulate')"
                  >
                    <div
                      class="sub-menu-content"
                      :class="'accumulate' === item.compareCalc.type ? 'content-active' : ''"
                      :disabled="state.notSupportAccumulateViews.indexOf(chart.type) > -1"
                    >
                      {{ t('chart.accumulate') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon
                          name="icon_done_outlined"
                          v-if="'accumulate' === item.compareCalc.type"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </div>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-dropdown-item>

          <el-dropdown-item @click.prevent v-if="showSort" :divided="chart.type !== 'table-info'">
            <!-- 排序菜单写回当前指标字段，自定义排序由上层弹窗维护完整排序列表。 -->
            <el-dropdown
              :effect="themes"
              placement="right-start"
              popper-class="data-dropdown_popper_mr9"
              style="width: 100%; height: 100%"
              @command="sort"
            >
              <span class="el-dropdown-link inner-dropdown-menu menu-item-padding">
                <span class="menu-item-content">
                  <el-icon>
                    <Icon name="icon_sort_outlined"><icon_sort_outlined class="svg-icon" /></Icon>
                  </el-icon>
                  <span>{{ t('chart.sort') }}</span>
                  <span class="summary-span-item">({{ t('chart.' + item.sort) }})</span>
                </span>
                <el-icon>
                  <Icon name="icon_right_outlined"><icon_right_outlined class="svg-icon" /></Icon>
                </el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu
                  :effect="themes"
                  class="drop-style sub"
                  :class="themes === 'dark' ? 'dark-dimension-quota' : ''"
                >
                  <el-dropdown-item class="menu-item-padding" :command="beforeSort('none')">
                    <span
                      class="sub-menu-content"
                      :class="'none' === item.sort ? 'content-active' : ''"
                    >
                      {{ t('chart.none') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon name="icon_done_outlined" v-if="'none' === item.sort"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item class="menu-item-padding" :command="beforeSort('asc')">
                    <span
                      class="sub-menu-content"
                      :class="'asc' === item.sort ? 'content-active' : ''"
                    >
                      {{ t('chart.asc') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon name="icon_done_outlined" v-if="'asc' === item.sort"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item class="menu-item-padding" :command="beforeSort('desc')">
                    <span
                      class="sub-menu-content"
                      :class="'desc' === item.sort ? 'content-active' : ''"
                    >
                      {{ t('chart.desc') }}
                      <el-icon class="sub-menu-content--icon">
                        <Icon name="icon_done_outlined" v-if="'desc' === item.sort"
                          ><icon_done_outlined class="svg-icon"
                        /></Icon>
                      </el-icon>
                    </span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-dropdown-item>

          <el-dropdown-item
            v-if="showSort"
            class="menu-item-padding"
            :command="beforeClickItem('sortPriority')"
          >
            <el-icon />
            <span>{{ t('chart.sort_priority') }}</span>
          </el-dropdown-item>

          <el-dropdown-item
            class="menu-item-padding"
            v-if="
              props.type !== 'extLabel' &&
              props.type !== 'extTooltip' &&
              props.type !== 'extBubble' &&
              chart.type !== 'multi-scatter'
            "
            :icon="iconFilter"
            :command="beforeClickItem('filter')"
            :divided="chart.type.includes('chart-mix')"
          >
            <!-- 指标过滤只作用于当前字段，不改变数据集全局筛选条件。 -->
            <span>{{ t('chart.filter') }}</span>
          </el-dropdown-item>

          <el-dropdown-item
            class="menu-item-padding"
            v-if="item.groupType === 'q' && props.type !== 'extBubble' && showValueFormatter"
            :divided="chart.type !== 'table-info'"
            :command="beforeClickItem('formatter')"
          >
            <el-icon />
            <span>{{ t('chart.value_formatter') }}</span>
          </el-dropdown-item>

          <el-dropdown-item class="menu-item-padding" :command="beforeClickItem('rename')">
            <el-icon>
              <icon name="icon_edit_outlined"><icon_edit_outlined class="svg-icon" /></icon>
            </el-icon>
            <span>{{ t('chart.show_name_set') }}</span>
          </el-dropdown-item>
          <el-dropdown-item
            class="menu-item-padding"
            v-if="['table-normal', 'table-info'].includes(chart.type)"
            :command="beforeClickItem('toggleHide')"
          >
            <el-icon>
              <icon
                ><icon_visible_outlined v-if="item.hide === true" class="svg-icon" />
                <icon_invisible_outlined v-else class="svg-icon"
              /></icon>
            </el-icon>
            <span>{{ item.hide === true ? t('chart.show') : t('chart.hide') }}</span>
          </el-dropdown-item>
          <el-dropdown-item class="menu-item-padding" :command="beforeClickItem('remove')">
            <el-icon>
              <icon name="icon_delete-trash_outlined"
                ><icon_deleteTrash_outlined class="svg-icon"
              /></icon>
            </el-icon>
            <span>{{ t('chart.delete') }}</span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </span>
</template>

<style lang="less" scoped>
:deep(.ed-dropdown-menu__item) {
  padding: 0;
}

:deep(.ed-dropdown-menu__item.menu-item-padding) {
  padding: 5px 16px;
}

.menu-item-padding {
  padding: 5px 16px;
}

.item-style {
  position: relative;
  width: 100%;
  display: block;
  overflow: hidden;
  .ed-dropdown {
    display: flex;
  }

  :deep(.ed-tag__content) {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
}

.item-axis {
  padding: 1px 8px;
  margin-bottom: 3px;
  height: 28px;
  line-height: 28px;
  display: flex;
  border-radius: 6px;
  box-sizing: border-box;
  white-space: nowrap;
  width: 100%;
  justify-content: space-between;
  align-items: center;
  background-color: #04b49c0a;
  border: 1px solid #04b49c;
}

.item-axis:hover {
  cursor: pointer;
}

span {
  font-size: 12px;
}

.summary-span {
  margin-left: 4px;
  color: #878d9f;
  position: absolute;
  right: 25px;
}

.inner-dropdown-menu {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;

  .menu-item-content {
    display: flex;
    flex-direction: row;
    align-items: center;
  }
}

.sub-menu-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;

  &.content-active {
    color: var(--ed-color-primary);
  }

  .sub-menu-content--icon {
    margin-left: 8px;
  }
}

.item-span-drop {
  color: #a6a6a6;
  display: flex;
}

.item-span-style {
  display: flex;
  max-width: 170px;
  color: #1f2329;
  margin-left: 4px;

  .item-name {
    flex: 1;
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
  }

  .item-right-summary {
    flex-shrink: 0;
    margin-left: 4px;
  }
  &.hidden-status,
  &.sort-status {
    max-width: 150px;
  }
  &.hidden-status[class*='sort-status'] {
    max-width: 135px !important;
  }
}

.editor-dark {
  .item-span-style {
    color: #ffffff !important;
  }
}

.summary-span-item {
  margin-left: 4px;
}

.drop-style {
  :deep(.ed-dropdown-menu__item) {
    height: 32px;
    min-width: 218px;
  }
  &.sub {
    :deep(.ed-dropdown-menu__item) {
      min-width: 118px;
    }
  }
  :deep(.ed-dropdown-menu__item:not(.is_disabled):focus) {
    color: inherit;
    background-color: rgba(31, 35, 41, 0.1);
  }
  &.dark-dimension-quota {
    background-color: #292929;
    border: 1px solid #434343;
    :deep(.ed-dropdown-menu__item--divided) {
      border-color: #ebebeb26;
    }
    :deep(.ed-dropdown-menu__item:not(.is-disabled):hover) {
      background-color: #ebebeb1a;
    }
    .inner-dropdown-menu {
      color: rgba(235, 235, 235, 1);
    }
    :deep(.ed-dropdown-menu__item) {
      color: rgba(235, 235, 235, 1);
    }
    :deep(.ed-dropdown-menu__item.is-disabled) {
      color: #a6a6a6;
    }
    :deep(.ed-dropdown-menu__item:not(.is_disabled):focus) {
      background-color: rgba(235, 235, 235, 0.1);
    }
  }
}

.remove-icon {
  position: absolute;
  top: 7px;
  right: 24px;
  cursor: pointer;

  .inner-class {
    font-size: 14px;
  }
}

.father {
  &.dark_icon-right {
    .child {
      color: #a6a6a6;
    }
  }

  &.light_icon-right {
    .child {
      color: #646a73;
    }
  }
  .child {
    font-size: 14px;
    visibility: hidden;
  }
}

.father:hover .child {
  visibility: visible;
}

.father:hover .item-span-style {
  max-width: 130px;
  &.hidden-status,
  &.sort-status {
    max-width: 120px;
  }
  &.hidden-status[class*='sort-status'] {
    max-width: 100px !important;
  }
}
</style>
<style lang="less">
.data-dropdown_popper_mr9 {
  margin-left: -9px !important;
}
.menu-item-padding {
  span {
    font-size: 14px;
    color: #1f2329;
  }
  .ed-icon {
    color: #646a73;
    font-size: 16px !important;
  }

  .sub-menu-content--icon {
    color: var(--ed-color-primary);
    margin-right: -7px;
  }
  :nth-child(1).ed-icon {
    margin-right: 8px;
  }
  .menu-item-content {
    :nth-child(1).ed-icon {
      margin-right: 8px;
    }
  }
}
.dark-dimension-quota {
  span {
    color: #ebebeb;
  }
  .ed-icon {
    color: #a6a6a6;
  }

  .sub-menu-content--icon {
    color: var(--ed-color-primary);
    margin-right: -7px !important;
  }
}
</style>
