<template>
  <div class="svg-triangle-container">
    <svg version="1.1" baseProfile="full" xmlns="http://www.w3.org/2000/svg">
      <polygon
        ref="star"
        :points="points"
        :stroke="element.style.borderColor"
        :fill="element.style.backgroundColor"
        :stroke-width="borderWidth"
      />
    </svg>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, toRefs, watch } from 'vue'

// 三角形组件的文本值和元素样式配置
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
        propValue: null
      }
    }
  }
})
// 保留元素响应式引用，便于监听宽高和边框样式
const { element } = toRefs(props)
// SVG polygon 的 points 字符串
const points = ref('')

// 宽度变化后重新计算三角形顶点
watch(
  () => element.value.style.width,
  () => {
    draw()
  }
)

// 高度变化后重新计算三角形顶点
watch(
  () => element.value.style.height,
  () => {
    draw()
  }
)

onMounted(() => {
  draw()
})

// 根据边框开关决定实际描边宽度
const borderWidth = computed(() => {
  return element.value.style.borderActive ? element.value.style.borderWidth : 0
})

// 从元素样式读取尺寸并重绘三角形
const draw = () => {
  const { width, height } = element.value.style
  drawPolygon(width, height)
}

// 按容器宽高计算等腰三角形的三个顶点坐标
const drawPolygon = (width, height) => {
  // 三角形三个坐标
  const pointsArray = [
    [0.5, 0.05],
    [1, 0.95],
    [0, 0.95]
  ]

  const coordinatePoints = pointsArray.map(point => width * point[0] + ' ' + height * point[1])
  points.value = coordinatePoints.toString()
}
</script>
<style lang="less" scoped>
.svg-triangle-container {
  width: 100%;
  height: 100%;

  svg {
    width: 100%;
    height: 100%;
  }

  .v-text {
    position: absolute;
    top: 72%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 50%;
    height: 40%;
  }
}
</style>
