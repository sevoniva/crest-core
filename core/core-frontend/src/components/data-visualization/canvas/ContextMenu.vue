<script setup lang="ts">
import { contextmenuStoreWithOut } from '@/store/modules/data-visualization/contextmenu'
import { storeToRefs } from 'pinia'
import ContextMenuDetails from '@/components/data-visualization/canvas/ContextMenuDetails.vue'
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    showPosition?: string
  }>(),
  {
    showPosition: 'canvasCore'
  }
)
const contextmenuStore = contextmenuStoreWithOut()

const { menuTop, menuLeft, menuShow, position } = storeToRefs(contextmenuStore)
// 控制弹窗、面板或区域的展示状态
const menuDetailsShow = computed(() => menuShow.value && props.showPosition === position.value)
</script>

<template>
  <context-menu-details
    v-show="menuDetailsShow"
    class="contextmenu"
    :style="{ top: menuTop + 'px', left: menuLeft + 'px' }"
  ></context-menu-details>
</template>

<style lang="less" scoped>
.contextmenu {
  position: absolute;
}
</style>
