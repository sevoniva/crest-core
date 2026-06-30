<script lang="ts" setup>
import { computed } from 'vue'
import { useEmbedded } from '@/store/modules/embedded'
const embeddedStore = useEmbedded()
// 生成跳转地址或资源链接
const outerUrl = computed(() => {
  return embeddedStore.outerUrl
})
</script>

<template>
  <iframe class="crest-jump-outer-url" :src="outerUrl" frameborder="0"></iframe>
</template>

<style lang="less" scoped>
.crest-jump-outer-url {
  width: 100%;
  height: 100%;
}
</style>
