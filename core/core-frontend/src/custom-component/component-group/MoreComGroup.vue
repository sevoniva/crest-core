<script setup lang="ts">
import dbMoreWeb from '@/assets/svg/db-more-web.svg'
import { toRefs } from 'vue'
import eventBus from '@/utils/eventBus'
import DragComponent from '@/custom-component/component-group/DragComponent.vue'
import { commonHandleDragEnd, commonHandleDragStart } from '@/utils/canvasUtils'
import { useI18n } from '@/hooks/web/useI18n'
import dvTabScreen from '@/assets/svg/dv-tab-screen.svg'
import iconDownloadOutlined from '@/assets/svg/icon_download_outlined.svg'
const { t } = useI18n()
// 定义组件入参，约束外部传入配置
const props = defineProps({
  propValue: {
    type: Array,
    default: () => []
  },
  dvModel: {
    type: String,
    default: 'dv'
  },
  element: {
    type: Object,
    default() {
      return {
        propValue: null
      }
    }
  },
  themes: {
    type: String,
    default: 'dark'
  }
})

const { dvModel } = toRefs(props)
// 创建新数据并写入当前配置
const newComponent = params => {
  eventBus.emit('handleNew', { componentName: params, innerType: params })
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
      name="YYYY-MM-DD 08:00:00"
      :label="t('visualization.date_time')"
      drag-info="TimeClock&TimeClock"
      v-on:click="newComponent('TimeClock')"
    ></drag-component>
    <drag-component
      :themes="themes"
      :icon="dbMoreWeb"
      :label="t('visualization.web')"
      drag-info="Frame&Frame"
      v-on:click="newComponent('Frame')"
    ></drag-component>
    <drag-component
      :themes="themes"
      :icon="iconDownloadOutlined"
      label="导出按钮"
      drag-info="ExportButton&ExportButton"
      v-on:click="newComponent('ExportButton')"
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
  padding: 12px 8px;
  display: inline-flex;
}
</style>
