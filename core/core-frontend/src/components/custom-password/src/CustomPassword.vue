<script lang="ts" setup>
import { useAttrs, computed } from 'vue'
import icon_visible_outlined from '@/assets/svg/icon_visible_outlined.svg'
import icon_invisible_outlined from '@/assets/svg/icon_invisible_outlined.svg'
const attrs = useAttrs()
// 定义组件入参，约束外部传入配置
const props = defineProps(['modelValue'])
// 定义组件向父级抛出的事件
const emits = defineEmits(['update:modelValue'])
// 转换当前值并同步表单状态
const value = computed({
  get() {
    return props.modelValue
  },
  set(value) {
    emits('update:modelValue', value)
  }
})
</script>

<template>
  <el-input
    :icon-view-custom="icon_visible_outlined"
    :icon-hide-custom="icon_invisible_outlined"
    v-bind="attrs"
    v-model="value"
  />
</template>
