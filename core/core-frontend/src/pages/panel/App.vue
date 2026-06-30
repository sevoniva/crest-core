<script setup lang="ts">
import { shallowRef, defineAsyncComponent, onMounted, nextTick, ref } from 'vue'
import { propTypes } from '@/utils/propTypes'
import { useEmitt } from '@/hooks/web/useEmitt'

// 懒加载数据大屏编辑器
const VisualizationEditor = defineAsyncComponent(
  () => import('@/views/data-visualization/index.vue')
)
// 懒加载仪表板编辑器
const DashboardEditor = defineAsyncComponent(() => import('@/views/dashboard/index.vue'))

// 懒加载仪表板预览页
const Dashboard = defineAsyncComponent(() => import('./DashboardPreview.vue'))
// 懒加载图表包裹页
const ViewWrapper = defineAsyncComponent(() => import('./ViewWrapper.vue'))
// 懒加载 iframe 容器页
const Iframe = defineAsyncComponent(() => import('./Iframe.vue'))
// 懒加载数据集列表页
const Dataset = defineAsyncComponent(() => import('@/views/visualized/data/dataset/index.vue'))
// 懒加载数据集编辑页
const DatasetEditor = defineAsyncComponent(
  () => import('@/views/visualized/data/dataset/form/index.vue')
)
// 懒加载数据源列表页
const Datasource = defineAsyncComponent(
  () => import('@/views/visualized/data/datasource/index.vue')
)
// 懒加载数据大屏预览面板
const ScreenPanel = defineAsyncComponent(() => import('@/views/data-visualization/PreviewShow.vue'))
// 懒加载仪表板预览面板
const DashboardPanel = defineAsyncComponent(
  () => import('@/views/dashboard/DashboardPreviewShow.vue')
)

// 懒加载画布预览页
const Preview = defineAsyncComponent(() => import('@/views/data-visualization/PreviewCanvas.vue'))

// 接收初始组件名称
const props = defineProps({
  componentName: propTypes.string.def('Iframe')
})
// 当前动态组件引用
const currentComponent = shallowRef()

const componentMap = {
  DashboardEditor,
  VisualizationEditor,
  ViewWrapper,
  Preview,
  Dashboard,
  Dataset,
  Iframe,
  Datasource,
  ScreenPanel,
  DashboardPanel,
  DatasetEditor
}

// 控制动态组件切换时的临时隐藏状态
const showComponent = ref(false)

// 切换当前显示的动态组件
const changeCurrentComponent = val => {
  showComponent.value = true
  currentComponent.value = undefined
  nextTick(() => {
    currentComponent.value = componentMap[val]
    showComponent.value = false
  })
}

useEmitt({
  name: 'changeCurrentComponent',
  callback: changeCurrentComponent
})

onMounted(() => {
  changeCurrentComponent(props.componentName)
})
</script>
<template>
  <component :is="currentComponent" v-if="!showComponent"></component>
</template>
