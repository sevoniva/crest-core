<script setup lang="ts">
import dbMoreWeb from '@/assets/svg/db-more-web.svg'
import dvTabScreen from '@/assets/svg/dv-tab-screen.svg'
import iconDownloadOutlined from '@/assets/svg/icon_download_outlined.svg'
import { toRefs } from 'vue'
import eventBus from '@/utils/eventBus'
import DragComponent from '@/custom-component/component-group/DragComponent.vue'
import { commonHandleDragEnd, commonHandleDragStart } from '@/utils/canvasUtils'

// 定义组件入参，约束外部传入配置
const props = defineProps({
  dvModel: {
    type: String,
    default: 'dv'
  },
  themes: {
    type: String,
    default: 'dark'
  }
})

const { dvModel } = toRefs(props)
// 创建新数据并写入当前配置
const newComponent = (componentName: string, innerType: string) => {
  eventBus.emit('handleNew', { componentName: componentName, innerType: innerType })
}

// 处理界面事件并同步业务状态
const handleDragStart = e => {
  commonHandleDragStart(e, dvModel.value)
}

// 处理界面事件并同步业务状态
const handleDragEnd = e => {
  commonHandleDragEnd(e, dvModel.value)
}
</script>

<template>
  <div class="group" @dragstart="handleDragStart" @dragend="handleDragEnd">
    <drag-component
      :themes="themes"
      :icon="dbMoreWeb"
      :label="$t('visualization.web')"
      drag-info="Frame&Frame"
      v-on:click="newComponent('Frame', 'Frame')"
    ></drag-component>
    <drag-component
      :themes="themes"
      :icon="iconDownloadOutlined"
      label="导出按钮"
      drag-info="ExportButton&ExportButton"
      v-on:click="newComponent('ExportButton', 'ExportButton')"
    ></drag-component>
    <!--    <drag-component-->
    <!--      :themes="themes"-->
    <!--      :icon="dvTabScreen"-->
    <!--      :label="$t('visualization.screen_page')"-->
    <!--      drag-info="Screen&Screen"-->
    <!--      v-on:click="newComponent('Screen', 'Screen')"-->
    <!--    ></drag-component>-->
  </div>
</template>

<style lang="less" scoped>
.group {
  width: 100%;
  display: flex;
  padding: 12px 8px;
}
</style>
