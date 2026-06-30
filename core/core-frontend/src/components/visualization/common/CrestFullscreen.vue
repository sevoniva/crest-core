<script lang="ts" setup>
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { onBeforeUnmount, onMounted } from 'vue'
import { useEmitt } from '@/hooks/web/useEmitt'

const dvMainStore = dvMainStoreWithOut()

// 定义组件入参，约束外部传入配置
const props = defineProps({
  themes: {
    type: String,
    default: 'light'
  },
  componentType: {
    type: String,
    default: 'button'
  },
  showPosition: {
    required: false,
    type: String,
    default: 'preview'
  }
})

// 衔接当前组件交互和状态同步
const fullscreenChange = () => {
  const isFullscreen = !!document.fullscreenElement
  dvMainStore.setFullscreenFlag(isFullscreen)

  // 编辑界面使用
  if (props.showPosition === 'edit') {
    dvMainStore.setEditMode(isFullscreen ? 'preview' : 'edit')
  }

  // 大屏编辑使用
  if (props.showPosition === 'dvEdit') {
    useEmitt().emitter.emit('canvasScrollRestore')
  }
}

// 衔接当前组件交互和状态同步
const toggleFullscreen = () => {
  const bodyNode = document.querySelector('body')
  if (!document.fullscreenElement) {
    bodyNode?.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}

// 针对钉钉windows版无法退出全屏问题 这里主动退出
const handleKeydown = event => {
  if (event.key === 'Escape' && document.fullscreenElement) {
    document.exitFullscreen()
  }
}

onMounted(() => {
  document.addEventListener('fullscreenchange', fullscreenChange)
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', fullscreenChange)
  document.removeEventListener('keydown', handleKeydown)
})

defineExpose({
  toggleFullscreen
})
</script>

<template><span></span></template>

<style lang="less" scoped></style>
