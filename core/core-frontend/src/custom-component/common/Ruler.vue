<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
/** 主画布仓库提供标尺所需的画布尺寸和当前组件状态 */
const dvMainStore = dvMainStoreWithOut()
/** 标尺滚动容器引用，用于与画布滚动保持同步 */
const wRuleRef = ref(null)
/** 标尺组件入参，控制方向、尺寸和刻度文本格式化 */
const props = defineProps({
  tickLabelFormatter: {
    type: Function,
    default: value => value.toString() // 刻度标签格式化函数，默认直接转为字符串
  },
  size: {
    type: Number,
    default: 300 // 尺子方向
  },
  direction: {
    type: String,
    default: 'horizontal' // 尺子方向
  }
})

/** 每隔多少个刻度显示一次长刻度标签 */
const labelInterval = 5
/** 向父级同步当前缩放后的刻度间距 */
const emits = defineEmits(['update:tickSize'])

/** 画布尺寸、当前组件和组件列表状态引用 */
const { canvasStyleData, curComponent, componentData } = storeToRefs(dvMainStore)

/** 标尺在当前方向上的逻辑长度 */
const rulerSize = computed(() =>
  props.direction === 'horizontal' ? canvasStyleData.value.width : canvasStyleData.value.height
)

// 计算复合画布内部组件偏移量
/** 计算当前组件所在分组、Tabs 或屏幕内的父级偏移 */
const parentStyle = computed(() => {
  const style = { left: 0, top: 0 }
  if (curComponent.value && curComponent.value.canvasId !== 'canvas-main') {
    componentData.value.forEach(item => {
      if (curComponent.value.canvasId.indexOf(item.id) > -1) {
        style.left = item.style.left
        style.top = item.style.top
      }
    })
    // tab页头部偏移量
    if (curComponent.value.canvasId.indexOf('Group') === -1) {
      style.top = style.top + 56
    }
  }
  return style
})

/** 当前组件在标尺上的投影区域 */
const curComponentShadow = computed(() => {
  if (curComponent.value) {
    return {
      left:
        (props.direction === 'horizontal'
          ? curComponent.value.style.left + parentStyle.value.left
          : curComponent.value.style.top + parentStyle.value.top) + 'px',
      width:
        (props.direction === 'horizontal'
          ? curComponent.value.style.width
          : curComponent.value.style.height) + 'px'
    }
  } else {
    return {}
  }
})

/** 根据画布缩放和刻度间距生成标尺刻度集合 */
const ticks = computed(() => {
  const result = []
  let currentValue = 0
  while (currentValue <= rulerSize.value) {
    const isLong = currentValue % (labelInterval * tickSize.value) === 0
    const label = isLong ? props.tickLabelFormatter(currentValue) : ''
    result.push({ position: (currentValue * canvasStyleData.value.scale) / 100, label, isLong })
    currentValue += tickSize.value
  }
  return result
})

/** 标尺内部滚动层宽度，预留长画布的滚动空间 */
const wStyle = computed(() => {
  return {
    width: rulerSize.value * 1.5 + 'px'
  }
})

/** 当前方向长度与画布宽度的比例，用于估算刻度密度 */
const radio = computed(() => rulerSize.value / canvasStyleData.value.width)
/** 依据画布缩放动态计算基础刻度间距 */
const tickSize = computed(
  () =>
    10 *
    Math.max(
      Math.floor((200000 * radio.value) / (rulerSize.value * canvasStyleData.value.scale)),
      1
    )
)

/** 标尺在当前缩放下的可视宽度 */
const scaleWidth = computed(() => (rulerSize.value * canvasStyleData.value.scale) / 100)

/** 同步外部画布滚动位置到标尺滚动容器 */
const rulerScroll = e => {
  const left = props.direction === 'vertical' ? e.scrollTop : e.scrollLeft
  wRuleRef.value.scrollTo(left, 0)
}

/** 标尺外层尺寸样式，纵向标尺需要扣除头部留白 */
const outerStyle = computed(() => {
  return {
    width: props.direction === 'vertical' ? props.size - 30 + 'px' : '100%'
  }
})

/** 当前组件未隐藏时展示组件投影 */
const curShadowShow = computed(() => curComponent.value && curComponent.value.category !== 'hidden')

/** 缩放后的刻度间距，供父级辅助线计算使用 */
const tickSizeScale = computed(() => (tickSize.value * canvasStyleData.value.scale) / 100)

/** 刻度间距变化时立即同步给父组件 */
watch(
  () => tickSizeScale.value,
  () => {
    emits('update:tickSize', tickSizeScale.value)
  },
  { immediate: true }
)

defineExpose({
  rulerScroll
})
</script>

<template>
  <div
    class="ruler-outer"
    :style="outerStyle"
    :class="{ 'ruler-vertical': direction === 'vertical' }"
    ref="wRuleRef"
  >
    <!--覆盖着尺子上方防止鼠标移到尺子位置滑动-->
    <div class="ruler-shadow" :style="outerStyle"></div>
    <div :style="wStyle" class="ruler-outer-scroll">
      <div class="ruler" :style="{ width: `${scaleWidth}px` }">
        <div v-if="curShadowShow" :style="curComponentShadow" class="cur-shadow"></div>
        <div class="ruler-line" :style="{ width: `${scaleWidth}px` }"></div>
        <div
          v-for="(tick, index) in ticks"
          :key="index"
          class="ruler-tick"
          :class="{ 'long-tick': tick.isLong }"
          :style="{ left: `${tick.position}px` }"
        >
          <span v-if="tick.isLong" class="tick-label">{{ tick.label }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="less">
.ruler-vertical {
  position: absolute;
  left: 30px;
  top: 30px;
  transform-origin: top left;
  transform: rotate(90deg);
  overflow-y: auto;
  overflow-x: hidden;
  z-index: 2;
  .ruler {
    .ruler-line {
      top: 0;
    }
    .ruler-tick {
      top: 0;
      .tick-label {
        transform: rotate(180deg);
      }
    }
  }
}

.ruler-shadow {
  position: absolute;
  height: 30px;
  z-index: 10;
  overflow: hidden;
}

.ruler-outer {
  overflow-x: auto;
  background-color: #2c2c2c;
  &::-webkit-scrollbar {
    width: 0 !important;
    height: 0 !important;
  }
}

.ruler-outer-scroll {
  min-width: 1600px;
  display: flex;
  justify-content: center;
}
.ruler {
  position: relative;
  height: 30px;
  display: flex;
  align-items: center;
  background-color: #2c2c2c;
}

.ruler-line {
  position: absolute;
  bottom: 0;
  height: 1px;
  background-color: #ac2a2a;
}

.ruler-tick {
  position: absolute;
  bottom: 1px;
  height: 3px;
  width: 1px;
  background-color: #e38a8a;
}

.long-tick {
  width: 1px;
  height: 15px;
}

.tick-label {
  position: absolute;
  bottom: 2px;
  font-size: 8px;
  left: 50%;
  transform: translateX(2%);
  white-space: nowrap;
}

.cur-shadow {
  background: rgba(10, 123, 224, 0.3);
  height: 30px;
  position: absolute;
}
</style>
