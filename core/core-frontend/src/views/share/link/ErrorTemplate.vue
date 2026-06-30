<script lang="ts" setup>
import EmptyBackground from '@/components/empty-background/src/EmptyBackground.vue'
import { propTypes } from '@/utils/propTypes'
// 定义组件入参，约束外部传入配置
const props = defineProps({
  msg: propTypes.string.def('')
})
</script>
<template>
  <EmptyBackground img-type="noneWhite" :description="props.msg" />
</template>
