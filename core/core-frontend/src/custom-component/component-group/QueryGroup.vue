<script setup lang="ts">
import dvFilterShow from '@/assets/svg/dv-filter-show.svg'
import { toRefs } from 'vue'
import eventBus from '@/utils/eventBus'
import DragComponent from '@/custom-component/component-group/DragComponent.vue'
import { commonHandleDragEnd, commonHandleDragStart } from '@/utils/canvasUtils'
import { useI18n } from '@/hooks/web/useI18n'
const { t } = useI18n()
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

// 处理界面事件并同步业务状态
const handleDragStart = e => {
  commonHandleDragStart(e, dvModel.value)
}

// 处理界面事件并同步业务状态
const handleDragEnd = e => {
  commonHandleDragEnd(e, dvModel.value)
}

// 创建新数据并写入当前配置
const newComponent = componentName => {
  eventBus.emit('handleNew', { componentName: componentName, innerType: componentName })
}
</script>

<template>
  <div
    class="group"
    @dragstart="handleDragStart"
    @dragend="handleDragEnd"
    v-on:click="newComponent('VQuery')"
  >
    <drag-component
      :themes="themes"
      :icon="dvFilterShow"
      :label="t('visualization.query_component')"
      drag-info="VQuery&VQuery"
    ></drag-component>
  </div>
</template>

<style lang="less" scoped>
.group {
  padding: 12px 8px;
}
.custom_img {
  width: 100px;
  height: 70px;
  cursor: pointer;
}
</style>
