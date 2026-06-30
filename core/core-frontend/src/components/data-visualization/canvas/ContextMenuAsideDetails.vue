<script setup lang="ts">
import ContextMenuDetails from '@/components/data-visualization/canvas/ContextMenuDetails.vue'

// 定义组件向父级抛出的事件
const emit = defineEmits(['close'])
defineProps({
  element: {
    type: Object
  },
  index: {
    type: Number
  }
})

// 关闭对应弹窗并清理临时状态
const close = param => {
  emit('close', param)
}
</script>

<template>
  <context-menu-details active-position="aside" @close="close"></context-menu-details>
</template>
