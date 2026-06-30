<template>
  <div
    v-if="isComposeSelected"
    class="compose-shadow"
    :class="{ 'shadow-border': props.showBorder }"
    @mousedown="handleMouseDown"
  ></div>
</template>

<script setup lang="ts">
import { composeStoreWithOut } from '@/store/modules/data-visualization/compose'
import { storeToRefs } from 'pinia'
import { computed } from 'vue'
const composeStore = composeStoreWithOut()
const { areaData } = storeToRefs(composeStore)

// 定义组件入参，约束外部传入配置
const props = defineProps({
  element: {
    required: true,
    type: Object
  },
  elementIndex: {
    required: false
  },
  showBorder: {
    required: false,
    type: Boolean,
    default: true
  }
})

// 记录当前选中项和交互焦点
const isComposeSelected = computed(() => areaData.value.components.includes(props.element))

// 处理界面事件并同步业务状态
const handleMouseDown = e => {
  // 右键返回
  if (e.buttons === 2) {
    return
  }
  const index = areaData.value.components.findIndex(component => component === props.element)
  if (index != -1 && props.element.component !== 'GroupArea') {
    areaData.value.components.splice(index, 1)
    e.stopPropagation()
  }
}
</script>

<style lang="less" scoped>
.compose-shadow {
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  z-index: 10;
}

.shadow-border {
  border: 1px solid var(--ed-color-primary);
}
</style>
