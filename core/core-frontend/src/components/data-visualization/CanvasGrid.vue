<template>
  <svg class="grid" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
    <defs>
      <pattern
        id="smallGrid"
        :width="smallGridW"
        :height="smallGridH"
        patternUnits="userSpaceOnUse"
      >
        <path
          :d="smallGridPathD"
          fill="none"
          stroke="rgba(207, 207, 207, 0.4)"
          stroke-width="0.8"
        />
      </pattern>
      <pattern
        id="middleGrid"
        :width="middleGridW"
        :height="middleGridH"
        patternUnits="userSpaceOnUse"
      >
        <rect :width="middleGridW" :height="middleGridH" fill="url(#smallGrid)" />
        <path :d="middleGridPathD" fill="none" stroke="rgba(207, 207, 207, 0.4)" stroke-width="1" />
      </pattern>
      <pattern id="grid" :width="gridW" :height="gridH" patternUnits="userSpaceOnUse">
        <rect :width="gridW" :height="gridH" fill="url(#middleGrid)" />
        <path :d="pathD" fill="none" stroke="rgba(207, 207, 207, 0.4)" stroke-width="1.2" />
      </pattern>
    </defs>
    <rect width="100%" height="100%" fill="url(#grid)" />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'
const matrixBase = 2

// 定义网格组件接收的矩阵样式和主题参数
const props = defineProps({
  matrixStyle: {
    type: Object
  },
  themes: {
    type: String,
    default: 'dark'
  }
})

// 生成最外层网格路径
const pathD = computed(() => {
  return 'M ' + gridW.value + ' 0 L 0 0 0 ' + gridH.value
})

// 生成中层网格路径
const middleGridPathD = computed(() => {
  return 'M ' + middleGridW.value + ' 0 L 0 0 0 ' + middleGridH.value
})

// 生成小网格路径
const smallGridPathD = computed(() => {
  return 'M ' + smallGridW.value + ' 0 L 0 0 0 ' + smallGridH.value
})

// 计算最外层网格宽度
const gridW = computed(() => {
  return props.matrixStyle.width * 2 * matrixBase
})

// 计算最外层网格高度
const gridH = computed(() => {
  return props.matrixStyle.height * 2 * matrixBase
})

// 计算中层网格宽度
const middleGridW = computed(() => {
  return props.matrixStyle.width * matrixBase
})

// 计算中层网格高度
const middleGridH = computed(() => {
  return props.matrixStyle.height * matrixBase
})

// 计算小网格宽度
const smallGridW = computed(() => {
  return props.matrixStyle.width
})

// 计算小网格高度
const smallGridH = computed(() => {
  return props.matrixStyle.height
})
</script>

<style lang="less" scoped>
.grid {
  position: absolute;
  top: 0;
  left: 0;
}
</style>
