<template>
  <div class="dynamic-shape">
    <component
      :curStyle="curStyleAdaptor"
      :scale="calScale"
      :is="findDecoration(element.innerType)"
      :color="curColor"
    ></component>
  </div>
</template>

<script setup lang="ts">
import { findDecoration } from '@/custom-component/decoration/component_details/config'
import { computed } from 'vue'
// 衔接当前组件交互和状态同步
const calScale = computed(() => {
  return props.scale
})

// 记录当前选中项和交互焦点
const curStyleAdaptor = computed(() => {
  if (props.showPosition.includes('edit')) {
    return {
      width: parseInt(props.curStyle.width) / props.scale,
      height: parseInt(props.curStyle.height) / props.scale
    }
  } else {
    return {
      width: parseInt(props.curStyle.width),
      height: parseInt(props.curStyle.height)
    }
  }
})

// 记录当前选中项和交互焦点
const curColor = computed(() => {
  return [props.element.style?.color0 || null, props.element.style?.color1 || null]
})
// 定义组件入参，约束外部传入配置
const props = defineProps({
  curStyle: {
    type: Object
  },
  scale: {
    type: Number
  },
  showPosition: {
    required: false,
    type: String,
    default: 'preview'
  },
  element: {
    type: Object,
    default() {
      return {
        innerType: null
      }
    }
  }
})
</script>

<style lang="less" scoped>
.dynamic-shape {
  width: 100%;
  height: 100%;
}
</style>
