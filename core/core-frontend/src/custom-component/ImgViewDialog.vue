<script setup lang="ts">
import {
  toRefs,
  computed,
  ref,
  effectScope,
  onMounted,
  onBeforeUnmount,
  type CSSProperties
} from 'vue'
import { throttle } from 'lodash-es'
import { useEventListener } from '@vueuse/core'
// 定义组件入参，约束外部传入配置
const props = defineProps({
  modelValue: Boolean,
  imageUrl: String
})

// 图片预览支持的缩放和旋转动作类型
type ImageViewerAction = 'zoomIn' | 'zoomOut' | 'clockwise' | 'anticlockwise'

// 图片预览当前变换状态
const transform = ref({
  scale: 1
})
// 独立事件作用域，便于组件卸载时统一清理监听
const scopeEventListener = effectScope()
// 保持弹窗显示状态和图片地址的响应式引用
const { modelValue, imageUrl } = toRefs(props)
// 根据缩放比例生成图片内联样式
const imgStyle = computed(() => {
  const { scale } = transform.value
  const style: CSSProperties = {
    transform: `scale(${scale})`
  }
  return style
})

// 执行图片缩放动作，并限制最小和最大缩放比例
const handleActions = (action: ImageViewerAction, options = {}) => {
  const { zoomRate } = {
    zoomRate: 1.4,
    ...options
  }
  switch (action) {
    case 'zoomOut':
      if (transform.value.scale > 0.2) {
        transform.value.scale = Number.parseFloat((transform.value.scale / zoomRate).toFixed(3))
      }
      break
    case 'zoomIn':
      if (transform.value.scale < 7) {
        transform.value.scale = Number.parseFloat((transform.value.scale * zoomRate).toFixed(3))
      }
      break
  }
}

// 鼠标滚轮缩放图片，节流后避免连续滚动造成过量渲染
const mousewheelHandler = throttle((e: WheelEvent | any /* 兼容旧浏览器的 wheelDelta 字段。 */) => {
  const delta = e.wheelDelta ? e.wheelDelta : -e.detail
  if (delta > 0) {
    handleActions('zoomIn', {
      zoomRate: 1.2,
      enableTransition: false
    })
  } else {
    handleActions('zoomOut', {
      zoomRate: 1.2,
      enableTransition: false
    })
  }
})

onMounted(() => {
  scopeEventListener.run(() => {
    useEventListener(document, 'mousewheel', mousewheelHandler)
  })
})
onBeforeUnmount(() => {
  scopeEventListener.stop()
})
// 弹窗关闭时同步父组件的 v-model
const emits = defineEmits(['update:modelValue'])
// 关闭预览弹窗并重置缩放比例
const HandleBeforeClose = () => {
  transform.value.scale = 1
  modelValue.value = false
  emits('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    class="img-enlarge-dialog"
    append-to-body
    :before-close="HandleBeforeClose"
    destroy-on-close
    close-on-click-modal
    v-model="modelValue"
  >
    <div class="img-content 13">
      <img :style="imgStyle" :src="imageUrl" alt="Preview Image" />
    </div>
  </el-dialog>
</template>

<style lang="less" scoped>
.img-content {
  width: 100vw;
  height: calc(100vh - 70px);
  overflow-y: hidden;
  padding: 60px 120px;
  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
}
</style>
