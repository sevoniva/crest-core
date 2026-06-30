<template>
  <div class="hang-main" @keydown.stop @keyup.stop>
    <div :key="index" v-for="(config, index) in hangComponentData">
      <component
        :is="findComponent(config['component'])"
        :view="canvasViewInfo[config['id']]"
        ref="component"
        class="component"
        :element="config"
        :scale="deepScale"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, toRefs } from 'vue'
import findComponent from '@/utils/components'
// 定义组件入参，约束外部传入配置
const props = defineProps({
  hangComponentData: {
    type: Object,
    required: true
  },
  canvasViewInfo: {
    type: Object,
    required: true
  },
  scale: {
    type: Number,
    required: false,
    default: 100
  }
})

const { hangComponentData, scale } = toRefs(props)
// 衔接当前组件交互和状态同步
const deepScale = computed(() => scale.value / 100)
</script>

<style lang="less" scoped>
.hang-main {
  overflow: auto;
  width: 100%;
  height: 300px;
}
</style>
