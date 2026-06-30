<script setup lang="ts">
import dvTabShow from '@/assets/svg/dv-tab-show.svg'
import { toRefs } from 'vue'
import eventBus from '@/utils/eventBus'
import DragComponent from '@/custom-component/component-group/DragComponent.vue'
import { commonHandleDragEnd, commonHandleDragStart } from '@/utils/canvasUtils'

// 定义组件入参，约束外部传入配置
const props = defineProps({
  propValue: {
    type: Array,
    default: () => []
  },
  dvModel: {
    type: String,
    default: 'dv'
  },
  element: {
    type: Object,
    default() {
      return {
        propValue: null
      }
    }
  },
  themes: {
    type: String,
    default: 'dark'
  }
})

const { dvModel } = toRefs(props)
// 创建新数据并写入当前配置
const newComponent = () => {
  eventBus.emit('handleNew', { componentName: 'Tabs', innerType: 'Tabs' })
}

// 处理界面事件并同步业务状态
const handleDragStart = e => {
  commonHandleDragStart(e, dvModel.value)
}

// 处理界面事件并同步业务状态
const handleDragEnd = e => {
  commonHandleDragEnd(e, dvModel.value)
}
</script>

<template>
  <div
    class="group"
    @dragstart="handleDragStart"
    @dragend="handleDragEnd"
    v-on:click="newComponent"
  >
    <drag-component
      :themes="themes"
      :icon="dvTabShow"
      label="Tab"
      drag-info="Tabs&Tabs"
    ></drag-component>
  </div>
</template>

<style lang="less" scoped>
.group {
  padding: 12px 8px;
}
</style>
