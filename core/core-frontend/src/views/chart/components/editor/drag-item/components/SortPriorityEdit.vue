<script lang="ts" setup>
import icon_drag_outlined from '@/assets/svg/icon_drag_outlined.svg'
import draggable from 'vuedraggable'
import { reactive, watch, ref } from 'vue'
import chartViewManager from '../../../js/panel'

// 拖拽列表的加载状态
const loading = ref(false)

// 保存当前图表可排序字段列表
const state = reactive({
  sortList: []
})

// 图表配置用于解析轴字段和已有排序优先级
const props = defineProps({
  chart: {
    type: Object,
    required: true
  }
})

// 拖拽排序变化后向父级提交新的优先级
const emit = defineEmits(['onPriorityChange'])

// 监听相关数据变化并同步组件状态
watch(
  () => props.chart,
  () => {
    init()
  },
  { deep: true }
)

// 根据图表轴配置同步排序字段，过滤已不存在的字段并补齐新增字段
const init = () => {
  const chart = props.chart
  if (!chart.sortPriority?.length) {
    state.sortList.splice(0, state.sortList.length)
  } else {
    state.sortList.splice(0, state.sortList.length, ...chart.sortPriority)
  }
  const chartInstance = chartViewManager.getChartView(chart.render, chart.type)
  if (chartInstance) {
    const axis = chartInstance.axis
    const axisMap = axis?.reduce((p, n) => {
      let axisArr
      switch (n) {
        case 'xAxis':
          axisArr = chart.xAxis
          break
        case 'yAxis':
          axisArr = chart.yAxis
          break
        case 'xAxisExt':
          axisArr = chart.xAxisExt
          break
        case 'yAxisExt':
          axisArr = chart.yAxisExt
          break
        case 'extBubble':
          axisArr = chart.extBubble
          break
        case 'flowMapEndName':
          axisArr = chart.flowMapEndName
          break
        case 'flowMapStartName':
          axisArr = chart.flowMapStartName
          break
        case 'extColor':
          axisArr = chart.extColor
          break
        case 'extStack':
          axisArr = chart.extStack
          break
        case 'drill':
          axisArr = chart.drillFields
          break
        default:
          break
      }
      axisArr?.forEach(ele => {
        if (!p[ele.id]) {
          p[ele.id] = ele.chartShowName ?? ele.name
        }
      })
      return p
    }, {})
    state.sortList = state.sortList.reduce((p, n) => {
      if (axisMap[n.id]) {
        n.name = axisMap[n.id]
        p.push(n)
      }
      return p
    }, [])
    Object.entries(axisMap).forEach(([key, value]) => {
      if (!state.sortList.find(item => item.id === key)) {
        state.sortList.push({ id: key, name: value })
      }
    })
  }
}
// 将拖拽后的排序列表提交给父组件
const onUpdate = () => {
  emit('onPriorityChange', state.sortList)
}

init()
</script>

<template>
  <el-scrollbar height="100%" max-height="599px">
    <draggable
      v-loading="loading"
      :list="state.sortList"
      animation="300"
      class="drag-list"
      item-key="id"
      @update="onUpdate"
    >
      <template #item="{ element }">
        <span :key="element.id" class="item-dimension" :title="element.name">
          <el-icon class="item-icon">
            <Icon name="icon_drag_outlined"><icon_drag_outlined class="svg-icon" /></Icon>
          </el-icon>
          <span class="item-span">
            {{ element.name }}
          </span>
        </span>
      </template>
    </draggable>
  </el-scrollbar>
</template>

<style lang="less" scoped>
.drag-list {
  height: 50vh;
}

.item-dimension {
  padding: 2px;
  margin: 2px;
  border: 1px solid #dee0e3;
  border-radius: 6px;
  text-align: left;
  color: #606266;
  background-color: white;
  display: flex;
  align-items: center;
  cursor: move;
}

.item-icon {
  font-size: 16px;
  margin: 0 4px;
  color: #646a73;
}

.item-span {
  display: inline-block;
  width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;

  color: #1f2329;

  font-size: 14px;
  font-style: normal;
  font-weight: 400;
  line-height: 22px;
}

.blackTheme .item-dimension {
  border: solid 1px;
  border-color: var(--TableBorderColor);
  color: var(--TextPrimary);
  background-color: var(--MainBG);
}

.item-dimension + .item-dimension {
  margin-top: 6px;
}

.item-dimension:hover {
  box-shadow: 0px 4px 8px 0px rgba(31, 35, 41, 0.1);
}

.blackTheme .item-dimension:hover {
  color: var(--Main);
  background: var(--ContentBG);
}
</style>
