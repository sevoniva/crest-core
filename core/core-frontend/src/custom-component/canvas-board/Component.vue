<template>
  <Board :name="element.innerType"></Board>
</template>

<script setup lang="ts">
import { toRefs } from 'vue'
import Board from '@/components/visual-board/Board.vue'
// 定义组件入参，约束外部传入配置
const props = defineProps({
  propValue: {
    type: String,
    required: true,
    default: ''
  },
  element: {
    type: Object,
    default() {
      return {
        innerType: null
      }
    }
  }
})

const { element } = toRefs(props)
</script>

<style lang="less" scoped></style>
