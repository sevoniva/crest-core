<template>
  <div class="svg-star-container">
    <svg version="1.1" baseProfile="full" xmlns="http://www.w3.org/2000/svg">
      <polygon
        ref="star"
        :points="points"
        :stroke="element.style.borderColor"
        :fill="element.style.backgroundColor"
        :stroke-width="element.style.borderWidth"
      />
    </svg>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, toRefs, watch } from 'vue'

// 五角星组件的文本值和元素样式配置
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
// 保留属性响应式引用，便于监听尺寸变化
const { propValue, element } = toRefs(props)
// SVG polygon 的 points 字符串
const points = ref('')

// 宽度变化后重新计算五角星顶点
watch(
  () => element.value.style.width,
  val => {
    draw()
  }
)

// 高度变化后重新计算五角星顶点
watch(
  () => element.value.style.height,
  val => {
    draw()
  }
)

onMounted(() => {
  draw()
})

// 根据元素样式中的宽高重绘五角星
const draw = () => {
  const { width, height } = element.value.style
  drawPolygon(width, height)
}

// 按容器宽高计算五角星十个顶点坐标
const drawPolygon = (width, height) => {
  // 五角星十个坐标点的比例集合
  const pointsArray = [
    [0.5, 0],
    [0.625, 0.375],
    [1, 0.375],
    [0.75, 0.625],
    [0.875, 1],
    [0.5, 0.75],
    [0.125, 1],
    [0.25, 0.625],
    [0, 0.375],
    [0.375, 0.375]
  ]

  const coordinatePoints = pointsArray.map(point => width * point[0] + ' ' + height * point[1])
  points.value = coordinatePoints.toString()
}
</script>

<style lang="less" scoped>
.svg-star-container {
  width: 100%;
  height: 100%;

  svg {
    width: 100%;
    height: 100%;
  }

  .v-text {
    position: absolute;
    top: 58%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 50%;
    height: 40%;
  }
}
</style>
