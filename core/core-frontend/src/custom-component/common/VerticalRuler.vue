<script setup lang="ts">
import { computed, ref } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
// 读取大屏画布尺寸和缩放比例
const dvMainStore = dvMainStoreWithOut()
// 垂直尺子滚动容器引用，用于跟随画布滚动
const wRuleRef = ref(null)
// 尺子刻度文案和方向配置
const props = defineProps({
  tickLabelFormatter: {
    type: Function,
    default: value => value.toString() // 刻度标签格式化函数，默认直接转为字符串
  },
  direction: {
    type: String,
    default: 'horizontal' // 尺子方向
  }
})

// 每隔固定数量的小刻度显示一次长刻度标签
const labelInterval = 5

// 画布样式数据决定尺子的高度、缩放和刻度密度
const { canvasStyleData } = storeToRefs(dvMainStore)

// 根据画布高度和缩放比例生成所有刻度位置
const ticks = computed(() => {
  const result = []
  let currentValue = 0
  while (currentValue <= canvasStyleData.value.height) {
    const isLong = currentValue % (labelInterval * tickSize.value) === 0
    const label = isLong ? props.tickLabelFormatter(currentValue) : ''
    result.push({ position: (currentValue * canvasStyleData.value.scale) / 100, label, isLong })
    currentValue += tickSize.value
  }
  return result
})

// 外层滚动区高度略高于画布，保证底部刻度可见
const hStyle = computed(() => {
  return {
    height: canvasStyleData.value.height * 1.5 + 'px'
  }
})
// 根据画布缩放动态调整小刻度间隔，避免缩放后刻度过密
const tickSize = computed(
  () =>
    10 *
    Math.max(Math.floor(200000 / (canvasStyleData.value.height * canvasStyleData.value.scale)), 1)
)

// 缩放后的尺子可视高度
const scaleHeight = computed(
  () => (canvasStyleData.value.height * canvasStyleData.value.scale) / 100
)

// 将尺子滚动位置同步到画布滚动高度
const rulerScroll = e => {
  wRuleRef.value.scrollTo(0, e.scrollHeight)
}

defineExpose({
  rulerScroll
})
</script>

<template>
  <div class="ruler-outer-vertical" ref="wRuleRef">
    testtest
    <!--覆盖着尺子上方防止鼠标移到尺子位置滑动-->
    <div class="ruler-shadow-vertical"></div>
    <div :style="hStyle" class="ruler-outer-vertical-scroll">
      <div class="ruler" :style="{ height: `${scaleHeight}px` }">
        <div class="ruler-line" :style="{ height: `${scaleHeight}px` }"></div>
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
.ruler-shadow-vertical {
  position: absolute;
  width: 30px;
  height: 100%;
  z-index: 10;
  overflow: hidden;
}

.ruler-outer-vertical {
  position: absolute;
  width: 30px;
  height: 100%;
  overflow-y: auto;
  background-color: #2c2c2c;
  &::-webkit-scrollbar {
    width: 0 !important;
    height: 0 !important;
  }
}

.ruler-outer-vertical-scroll {
  display: flex;
  align-items: center;
  justify-content: center;
}
.ruler {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  height: 100%;
  border-left: 1px solid #974e4e;
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
</style>
