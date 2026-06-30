<script lang="tsx" setup>
import { ref, watch, onMounted, computed } from 'vue'
import { customMergeColor } from '@/custom-component/decoration/component_details/config'
import { cloneDeep } from 'lodash-es'

// 边框组件接收颜色、背景、尺寸和缩放比例
interface Props {
  color?: string[]
  backgroundColor?: string
  curStyle: { width: number; height: number }
  scale: number
}

// 规范化组件属性默认值，保证未配置时仍能绘制默认边框
const props = withDefaults(defineProps<Props>(), {
  color: () => [],
  backgroundColor: 'transparent',
  curStyle: () => ({
    width: 320,
    height: 240
  })
})

// 当前边框宽度来自外部样式配置
const width = computed(() => props.curStyle.width)
// 当前边框高度来自外部样式配置
const height = computed(() => props.curStyle.height)

// 组件内置的默认边框颜色
const defaultColor = ref(['#2862b7', '#2862b7'])
// 合并后的实际渲染颜色列表
const mergedColor = ref<string[]>([])

// 将用户自定义颜色与默认颜色合并，保证多层线条都有可用颜色
const mergeColor = () => {
  mergedColor.value = customMergeColor(cloneDeep(defaultColor.value), props.color)
}

// 计算边框容器尺寸和缩放样式
const border_style = computed(() => {
  return {
    width: `${width.value}px`,
    height: `${height.value}px`,
    transform: `scale(${props.scale})`,
    'transform-origin': '0 0',
    'will-change': 'transform' // 提示浏览器准备变换
  }
})

// 监听相关数据变化并同步组件状态
watch(() => props.color, mergeColor, { immediate: true })
onMounted(mergeColor)
</script>

<template>
  <div class="dv-border-box-3" :style="border_style" :ref="ref">
    <svg class="dv-border-svg-container" :width="width" :height="height">
      <polygon
        :fill="backgroundColor"
        :points="`
        23, 23 ${width - 24}, 23 ${width - 24}, ${height - 24} 23, ${height - 24}
      `"
      />
      <polyline
        class="dv-bb3-line1"
        :stroke="mergedColor[0]"
        :points="`4, 4 ${width - 22} ,4 ${width - 22}, ${height - 22} 4, ${height - 22} 4, 4`"
      />
      <polyline
        class="dv-bb3-line2"
        :stroke="mergedColor[1]"
        :points="`10, 10 ${width - 16}, 10 ${width - 16}, ${height - 16} 10, ${height - 16} 10, 10`"
      />
      <polyline
        class="dv-bb3-line2"
        :stroke="mergedColor[1]"
        :points="`16, 16 ${width - 10}, 16 ${width - 10}, ${height - 10} 16, ${height - 10} 16, 16`"
      />
      <polyline
        class="dv-bb3-line2"
        :stroke="mergedColor[1]"
        :points="`22, 22 ${width - 4}, 22 ${width - 4}, ${height - 4} 22, ${height - 4} 22, 22`"
      />
    </svg>

    <div class="border-box-content">
      <slot></slot>
    </div>
  </div>
</template>

<style lang="less">
.dv-border-box-3 {
  position: relative;
  width: 100%;
  height: 100%;
  /* 启用硬件加速 */
  transform: translateZ(0);
  backface-visibility: hidden;
  contain: content; /* 限制重绘范围 */

  .dv-border-svg-container {
    position: absolute;
    width: 100%;
    height: 100%;
    top: 0;
    left: 0;
    /* 优化SVG渲染 */
    shape-rendering: crispEdges;
    pointer-events: none; /* 禁用鼠标事件 */

    & > polyline {
      fill: none;
      vector-effect: non-scaling-stroke; /* 保持线条宽度不受缩放影响 */
    }

    .dv-bb3-line1 {
      stroke-width: 3;
      stroke-linecap: round; /* 线条端点圆角 */
    }

    .dv-bb3-line2 {
      stroke-width: 1;
      stroke-dasharray: 100; /* 添加虚线效果提升视觉层次 */
      stroke-dashoffset: 100;
      animation: dash 5s linear infinite;
    }
  }

  .border-box-content {
    position: relative;
    width: 100%;
    height: 100%;
    isolation: isolate; /* 创建新的层叠上下文 */
  }
}

@keyframes dash {
  to {
    stroke-dashoffset: 0;
  }
}
</style>
