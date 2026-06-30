<script lang="ts" setup>
import {
  shallowRef,
  defineAsyncComponent,
  ref,
  onBeforeUnmount,
  onBeforeMount,
  onMounted,
  nextTick
} from 'vue'
import { debounce } from 'lodash-es'
import { useEmitt } from '@/hooks/web/useEmitt'
import { useLoading } from '@/hooks/web/useLoading'

const { close } = useLoading()
// 当前动态组件引用
const currentComponent = shallowRef()
// 懒加载画布预览页
const Preview = defineAsyncComponent(() => import('@/views/data-visualization/PreviewCanvas.vue'))
// 懒加载数据大屏编辑器
const VisualizationEditor = defineAsyncComponent(
  () => import('@/views/data-visualization/index.vue')
)
// 懒加载仪表板编辑器
const DashboardEditor = defineAsyncComponent(() => import('@/views/dashboard/index.vue'))

// 懒加载仪表板预览页
const Dashboard = defineAsyncComponent(() => import('@/pages/panel/DashboardPreview.vue'))
// 懒加载图表包裹页
const ViewWrapper = defineAsyncComponent(() => import('@/pages/panel/ViewWrapper.vue'))
// 懒加载数据集列表页
const Dataset = defineAsyncComponent(() => import('@/views/visualized/data/dataset/index.vue'))
// 懒加载数据源列表页
const Datasource = defineAsyncComponent(
  () => import('@/views/visualized/data/datasource/index.vue')
)
// 懒加载数据大屏预览页
const ScreenPanel = defineAsyncComponent(() => import('@/views/data-visualization/PreviewShow.vue'))
// 懒加载仪表板预览面板
const DashboardPanel = defineAsyncComponent(
  () => import('@/views/dashboard/DashboardPreviewShow.vue')
)

const componentMap = {
  DashboardEditor,
  VisualizationEditor,
  ViewWrapper,
  Preview,
  Dashboard,
  Dataset,
  Datasource,
  ScreenPanel,
  DashboardPanel
}
// iframe 容器样式
const iframeStyle = ref(null)
const setStyle = debounce(() => {
  iframeStyle.value = {
    height: window.innerHeight + 'px',
    width: window.innerWidth + 'px'
  }
}, 300)
onBeforeMount(() => {
  window.addEventListener('resize', setStyle)
  setStyle()
})
onMounted(() => {
  close()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', setStyle)
})

// 控制动态组件显示状态
const showComponent = ref(false)

// 初始化 iframe 中显示的动态组件
const initIframe = (name: string) => {
  showComponent.value = false
  nextTick(() => {
    currentComponent.value = componentMap[name || 'ViewWrapper']
    showComponent.value = true
  })
}

useEmitt({
  name: 'changeCurrentComponent',
  callback: initIframe
})
</script>

<template>
  <div :style="iframeStyle">
    <component :is="currentComponent" v-if="showComponent"></component>
  </div>
</template>
