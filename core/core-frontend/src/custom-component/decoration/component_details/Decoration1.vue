<template>
  <div class="dv-decoration-1" :style="border_style" :ref="refName">
    <svg
      :width="`${svgWH[0]}px`"
      :height="`${svgWH[1]}px`"
      :style="`transform:scale(${svgScale[0]},${svgScale[1]});`"
    >
      <template v-for="(point, i) in points" :key="i">
        <rect
          v-if="Math.random() > 0.6"
          :fill="mergedColor[0]"
          :x="point[0] - halfPointSideLength"
          :y="point[1] - halfPointSideLength"
          :width="pointSideLength"
          :height="pointSideLength"
        >
          <animate
            v-if="Math.random() > 0.6"
            attributeName="fill"
            :values="`${mergedColor[0]};transparent`"
            dur="1s"
            :begin="Math.random() * 2"
            repeatCount="indefinite"
          />
        </rect>
      </template>

      <rect
        v-if="rects[0]"
        :fill="mergedColor[1]"
        :x="rects[0][0] - pointSideLength"
        :y="rects[0][1] - pointSideLength"
        :width="pointSideLength * 2"
        :height="pointSideLength * 2"
      >
        <animate
          attributeName="width"
          :values="`0;${pointSideLength * 2}`"
          dur="2s"
          repeatCount="indefinite"
        />
        <animate
          attributeName="height"
          :values="`0;${pointSideLength * 2}`"
          dur="2s"
          repeatCount="indefinite"
        />
        <animate
          attributeName="x"
          :values="`${rects[0][0]};${rects[0][0] - pointSideLength}`"
          dur="2s"
          repeatCount="indefinite"
        />
        <animate
          attributeName="y"
          :values="`${rects[0][1]};${rects[0][1] - pointSideLength}`"
          dur="2s"
          repeatCount="indefinite"
        />
      </rect>

      <rect
        v-if="rects[1]"
        :fill="mergedColor[1]"
        :x="rects[1][0] - 40"
        :y="rects[1][1] - pointSideLength"
        :width="40"
        :height="pointSideLength * 2"
      >
        <animate attributeName="width" values="0;40;0" dur="2s" repeatCount="indefinite" />
        <animate
          attributeName="x"
          :values="`${rects[1][0]};${rects[1][0] - 40};${rects[1][0]}`"
          dur="2s"
          repeatCount="indefinite"
        />
      </rect>
    </svg>
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, watch, onMounted } from 'vue'
import { cloneDeep } from 'lodash-es'
import { customMergeColor } from '@/custom-component/decoration/component_details/config'

/** 装饰一组件的尺寸、缩放和主题色入参 */
interface Props {
  color?: string[]
  curStyle: { width: number; height: number }
  scale: number
}

/** 组件入参默认值，保证未配置时仍按基础尺寸渲染 */
const props = withDefaults(defineProps<Props>(), {
  color: () => [],
  curStyle: () => {
    return {
      width: 320,
      height: 240
    }
  }
})

/** 当前装饰组件的设计宽度 */
const width = computed(() => props.curStyle.width)
/** 当前装饰组件的设计高度 */
const height = computed(() => props.curStyle.height)

/** 外层容器样式，使用缩放保持装饰在画布中的实际尺寸 */
const border_style = computed(() => ({
  width: `${width.value}px`,
  height: `${height.value}px`,
  transform: `scale(${props.scale})`,
  'transform-origin': '0 0',
  'will-change': 'transform' // 提示浏览器优化
}))

/** 单个闪烁方块的边长 */
const pointSideLength = 2.5
/** 装饰根节点标识，兼容旧配置中的引用名称 */
const refName = ref('decoration-1')
/** SVG 基准画布尺寸，后续通过缩放适配真实尺寸 */
const svgWH = ref([200, 50])
/** SVG 横纵向缩放比例 */
const svgScale = ref([1, 1])
/** 点阵的行数 */
const rowNum = 4
/** 每行点阵的点数量 */
const rowPoints = 20
/** 方块半边长，用于把坐标点转换为矩形左上角 */
const halfPointSideLength = computed(() => pointSideLength / 2)
/** 点阵中所有方块的中心坐标 */
const points = ref<number[][]>([])
/** 动画高亮矩形的中心坐标 */
const rects = ref<number[][]>([])
/** 装饰组件默认主色和强调色 */
const defaultColor = ref(['#fff', '#0de7c2'])
/** 合并默认色与用户配置后的最终颜色 */
const mergedColor = ref<string[]>([])

/** 按基准 SVG 尺寸计算点阵坐标 */
const calcPointsPosition = () => {
  const [w, h] = svgWH.value
  const horizontalGap = w / (rowPoints + 1)
  const verticalGap = h / (rowNum + 1)

  let pointsArray = new Array(rowNum)
    .fill(0)
    .map((_, i) =>
      new Array(rowPoints).fill(0).map((_, j) => [horizontalGap * (j + 1), verticalGap * (i + 1)])
    )

  points.value = pointsArray.reduce((all, item) => [...all, ...item], [])
}

/** 从点阵中选取两个高亮矩形的动画起点 */
const calcRectsPosition = () => {
  const rect1 = points.value[rowPoints * 2 - 1]
  const rect2 = points.value[rowPoints * 2 - 3]
  rects.value = [rect1, rect2]
}

/** 根据组件实际宽高计算 SVG 缩放比例 */
const calcScale = () => {
  const [w, h] = svgWH.value
  svgScale.value = [width.value / w, height.value / h]
}

/** 统一刷新点阵、矩形和缩放数据 */
const calcSVGData = () => {
  calcPointsPosition()
  calcRectsPosition()
  calcScale()
}

/** 合并主题色，优先使用用户配置覆盖默认颜色 */
const mergeColor = () => {
  mergedColor.value = customMergeColor(cloneDeep(defaultColor.value), props.color)
}

/** 尺寸变化时重新计算 SVG 绘制数据 */
const onResize = () => {
  calcSVGData()
}

/** 监听颜色配置变化并刷新最终颜色 */
watch(
  () => props.color,
  () => {
    mergeColor()
  }
)

/** 首次挂载时初始化颜色和 SVG 坐标 */
onMounted(() => {
  mergeColor()
  calcSVGData()
})

// 兼容旧版 autoResize 行为，尺寸变化时重新计算 SVG 坐标
// Vue 3 场景下这里直接使用 watch 触发缩放刷新
watch([width, height], () => {
  onResize()
})
</script>

<style lang="less">
.dv-decoration-1 {
  position: relative;
  width: 100%;
  height: 100%;
  /* 启用硬件加速 */
  transform: translateZ(0);
  backface-visibility: hidden;
  contain: content; /* 限制重绘范围 */

  svg {
    position: absolute;
    transform-origin: left top;
    /* 优化SVG渲染 */
    shape-rendering: optimizeSpeed;
    pointer-events: none;
  }
}
</style>
