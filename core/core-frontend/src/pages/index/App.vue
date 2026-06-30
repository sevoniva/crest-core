<script setup lang="ts">
import { ref } from 'vue'
import { useEmitt } from '@/hooks/web/useEmitt'
import configGlobal from '@/components/config-global/src/ConfigGlobal.vue'
import { useRoute } from 'vue-router_2'
import ExportExcel from '@/views/visualized/data/dataset/ExportExcel.vue'
const route = useRoute()
// 衔接当前组件交互和状态同步
const exportExcelRef = ref()
// 衔接当前组件交互和状态同步
const exportExcelCenter = params => {
  exportExcelRef.value.init(params)
}
useEmitt({
  name: 'data-export-center',
  callback: exportExcelCenter
})
</script>
<template>
  <config-global>
    <router-view :key="route.fullPath" />
    <ExportExcel ref="exportExcelRef"></ExportExcel>
  </config-global>
</template>
