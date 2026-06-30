<script lang="tsx" setup>
import { ref, watch, onMounted, computed } from 'vue'
import { customMergeColor } from '@/custom-component/decoration/component_details/config'
import { cloneDeep } from 'lodash-es'

/** 装饰边框十组件的颜色、背景、尺寸和缩放入参 */
interface Props {
  color?: string[]
  backgroundColor?: string
  curStyle: { width: number; height: number }
  scale: number
}

/** 装饰边框十的入参默认值 */
const props = withDefaults(defineProps<Props>(), {
  color: () => [],
  backgroundColor: 'transparent',
  curStyle: () => ({
    width: 320,
    height: 240
  })
})

/** 当前装饰边框宽度 */
const width = computed(() => props.curStyle.width)
/** 当前装饰边框高度 */
const height = computed(() => props.curStyle.height)
/** 四个角标的方位名称 */
const border = ['left-top', 'right-top', 'left-bottom', 'right-bottom']

/** 装饰边框默认主色和角标色 */
const defaultColor = ref(['#2862b7', '#2862b7'])
/** 合并用户配置后的边框颜色 */
const mergedColor = ref<string[]>([])

/** 合并默认颜色和用户自定义颜色 */
const mergeColor = () => {
  mergedColor.value = customMergeColor(cloneDeep(defaultColor.value), props.color)
}

/** 外层边框容器样式，包含缩放、内阴影和角标颜色变量 */
const border_style = computed(() => {
  return {
    width: `${width.value}px`,
    height: `${height.value}px`,
    transform: `scale(${props.scale})`,
    'transform-origin': '0 0',
    'will-change': 'transform', // 提示浏览器优化
    'box-shadow': `inset 0 0 25px 3px ${mergedColor.value[0]}`,
    '--border-color': mergedColor.value[1] // CSS变量传递颜色
  }
})

// 使用立即执行的watch
/** 监听颜色变化并立即刷新合并色 */
watch(() => props.color, mergeColor, { immediate: true })
/** 挂载后兜底刷新一次合并色 */
onMounted(mergeColor)
</script>

<template>
  <div class="dv-border-box-10" :style="border_style" ref="dv-border-box-10">
    <!-- 主边框SVG -->
    <svg class="dv-border-svg-container" :width="width" :height="height">
      <polygon
        :fill="backgroundColor"
        :points="`
        4, 0 ${width - 4}, 0 ${width}, 4 ${width}, ${height - 4} ${width - 4}, ${height}
        4, ${height} 0, ${height - 4} 0, 4
      `"
      />
    </svg>

    <!-- 四个角标 -->
    <template v-for="item in border" :key="item">
      <svg width="150px" height="150px" :class="`corner-${item} dv-border-svg-container`">
        <polygon fill="var(--border-color)" points="40, 0 5, 0 0, 5 0, 16 3, 19 3, 7 7, 3 35, 3" />
      </svg>
    </template>

    <div class="border-box-content">
      <slot></slot>
    </div>
  </div>
</template>

<style lang="less">
.dv-border-box-10 {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: 6px;
  /* 启用硬件加速 */
  transform: translateZ(0);
  backface-visibility: hidden;
  contain: content; /* 限制重绘范围 */

  .dv-border-svg-container {
    position: absolute;
    display: block;
    /* 优化SVG渲染 */
    shape-rendering: crispEdges;
    pointer-events: none; /* 禁用鼠标事件 */
  }

  /* 角标位置和变换 */
  .corner-left-top {
    top: 0;
    left: 0;
  }

  .corner-right-top {
    top: 0;
    right: 0;
    transform: rotateY(180deg) translateZ(0); /* 添加硬件加速 */
  }

  .corner-left-bottom {
    bottom: 0;
    left: 0;
    transform: rotateX(180deg) translateZ(0); /* 添加硬件加速 */
  }

  .corner-right-bottom {
    bottom: 0;
    right: 0;
    transform: rotateX(180deg) rotateY(180deg) translateZ(0); /* 添加硬件加速 */
  }

  .border-box-content {
    position: relative;
    width: 100%;
    height: 100%;
    isolation: isolate; /* 创建新的层叠上下文 */
  }
}
</style>
