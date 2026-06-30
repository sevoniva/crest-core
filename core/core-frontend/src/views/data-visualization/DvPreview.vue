<script setup lang="ts">
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { computed, onBeforeUnmount, onMounted, ref, type CSSProperties } from 'vue'
import CrestPreview from '@/components/data-visualization/canvas/CrestPreview.vue'
import { storeToRefs } from 'pinia'

const dvMainStore = dvMainStoreWithOut()
const { fullscreenFlag } = storeToRefs(dvMainStore)
// 持有预览画布组件实例，便于对外触发恢复操作
const crestPreviewRef = ref<InstanceType<typeof CrestPreview> | null>(null)
// 持有预览外层容器元素，用于监听尺寸变化
const crestPreviewOuterRef = ref<HTMLElement | null>(null)
// 标记预览数据是否已准备好渲染
const dataInitState = ref(true)
// 保存保持比例模式下当前采用的宽高优先策略
const keepProportion = ref('heightFirst')
let resizeObserver: ResizeObserver | null = null
// 定义预览组件接收的画布、资源和显示控制参数
const props = defineProps({
  canvasStylePreview: {
    required: true,
    type: Object
  },
  canvasDataPreview: {
    required: true,
    type: Object
  },
  canvasViewInfoPreview: {
    required: true,
    type: Object
  },
  dvInfo: {
    required: true,
    type: Object
  },
  curPreviewGap: {
    required: false,
    type: Number,
    default: 0
  },
  // 联动按钮位置
  showLinkageButton: {
    type: Boolean,
    default: true
  },
  showPosition: {
    required: false,
    type: String,
    default: 'preview'
  },
  // 显示悬浮按钮
  showPopBar: {
    type: Boolean,
    default: false
  },
  downloadStatus: {
    required: false,
    type: Boolean,
    default: false
  }
})

// 对外暴露的预览画布恢复入口
const restore = () => {
  crestPreviewRef.value?.restore()
}
// 根据屏幕适配方式计算内层预览容器类名
const contentInnerClass = computed(() => {
  //屏幕适配方式 widthFirst=宽度优先(默认) heightFirst=高度优先 full=铺满全屏 keepSize=不缩放
  if (screenAdaptor.value === 'heightFirst') {
    return 'preview-content-inner-height-first'
  } else if (screenAdaptor.value === 'full') {
    return 'preview-content-inner-full'
  } else if (screenAdaptor.value === 'keep') {
    return 'preview-content-inner-size-keep'
  } else {
    return 'preview-content-inner-width-first'
  }
})

// 根据高度优先模式调整外层容器排列方向
const outerStyle = computed<CSSProperties>(() => {
  return {
    flexDirection: props.canvasStylePreview.screenAdaptor === 'heightFirst' ? 'row' : 'column'
  }
})

// 解析最终生效的屏幕适配方式
const screenAdaptor = computed(() => {
  if (props.canvasStylePreview.screenAdaptor === 'keepProportion') {
    return keepProportion.value
  } else {
    return props.canvasStylePreview.screenAdaptor
  }
})

// 根据外层容器和画布尺寸判断保持比例时的优先方向
const keepProportionCheck = outerContentRect => {
  const { width, height } = outerContentRect
  const { innerWidth, innerHeight } = crestPreviewRef.value.getPreviewCanvasSize()
  if (width > innerWidth || height < innerHeight) {
    keepProportion.value = 'heightFirst'
  } else {
    keepProportion.value = 'widthFirst'
  }
}

onMounted(() => {
  resizeObserver = new ResizeObserver(entries => {
    for (let entry of entries) {
      // entry.contentRect 包含 width, height, top, left 等属性
      keepProportionCheck(entry.contentRect)
    }
  })

  if (crestPreviewOuterRef.value) {
    resizeObserver.observe(crestPreviewOuterRef.value)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})

defineExpose({
  restore
})
</script>

<template>
  <div
    id="crest-preview-content"
    ref="crestPreviewOuterRef"
    :class="{ 'screen-full': fullscreenFlag }"
    :style="outerStyle"
    class="content-outer"
  >
    <div class="content-inner" :class="contentInnerClass">
      <CrestPreview
        ref="crestPreviewRef"
        v-if="canvasStylePreview && dataInitState"
        :component-data="canvasDataPreview"
        :canvas-style-data="canvasStylePreview"
        :canvas-view-info="canvasViewInfoPreview"
        :dv-info="dvInfo"
        :cur-gap="curPreviewGap"
        :show-position="showPosition"
        :download-status="downloadStatus"
        :outer-screen-adaptor="screenAdaptor"
        :show-pop-bar="showPopBar"
        :show-linkage-button="showLinkageButton"
      ></CrestPreview>
    </div>
  </div>
</template>

<style lang="less">
.content-outer {
  width: 100%;
  height: calc(100vh - 100px);
  background: #f5f6f7;
  display: flex;
  overflow-y: auto;
  align-items: center;
  flex-direction: column;
  justify-content: center; /* 上下居中 */
  ::-webkit-scrollbar {
    display: none;
  }
}
</style>
