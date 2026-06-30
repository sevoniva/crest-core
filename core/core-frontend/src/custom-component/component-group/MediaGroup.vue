<script setup lang="ts">
import iconVideo from '@/assets/svg/icon-video.svg'
import dvPictureShow from '@/assets/svg/dv-picture-show.svg'
import iconStream from '@/assets/svg/icon-stream.svg'
import pictureGroupOrigin from '@/assets/svg/picture-group-origin.svg'
import { toRefs } from 'vue'
import eventBus from '@/utils/eventBus'
import DragComponent from '@/custom-component/component-group/DragComponent.vue'
import { commonHandleDragEnd, commonHandleDragStart } from '@/utils/canvasUtils'
import { useI18n } from '@/hooks/web/useI18n'
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
const newComponent = (componentName, innerType) => {
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
      class="media-component"
      :themes="themes"
      :icon="dvPictureShow"
      :label="t('visualization.picture')"
      drag-info="Picture&Picture"
      v-on:click="newComponent('Picture', 'Picture')"
    ></drag-component>
    <drag-component
      class="media-component"
      :themes="themes"
      :icon="iconVideo"
      :label="t('visualization.video')"
      drag-info="Video&Video"
      v-on:click="newComponent('Video', 'Video')"
    ></drag-component>
    <drag-component
      class="media-component"
      :themes="themes"
      :icon="iconStream"
      :label="t('visualization.stream_media')"
      drag-info="StreamMedia&StreamMedia"
      v-on:click="newComponent('StreamMedia', 'StreamMedia')"
    ></drag-component>
    <drag-component
      class="media-component"
      :themes="themes"
      :icon="pictureGroupOrigin"
      :label="t('visualization.pic_group')"
      drag-info="UserView&picture-group"
      v-on:click="newComponent('UserView', 'picture-group')"
    ></drag-component>
  </div>
</template>

<style lang="less" scoped>
.group {
  padding: 12px 8px;
}
.media-component {
  float: left;
  margin: 0 6px 12px 6px !important;
}
</style>
