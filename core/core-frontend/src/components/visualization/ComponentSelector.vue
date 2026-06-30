<template>
  <div class="bar-main">
    <el-checkbox v-model="checked" @change="checkChange" />
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue'
import { useViewSelectorStoreWithOut } from '@/store/modules/data-visualization/viewSelector'
const viewSelectorStore = useViewSelectorStoreWithOut()

// 校验当前数据是否满足业务规则
const checked = ref(false)

// 定义组件入参，约束外部传入配置
const props = defineProps({
  resourceId: {
    type: String,
    required: false
  }
})
onMounted(() => {
  checked.value = viewSelectorStore.getViewIdList.includes(props.resourceId)
})

// 校验当前数据是否满足业务规则
const checkChange = val => {
  if (val) {
    viewSelectorStore.add(props.resourceId)
  } else {
    viewSelectorStore.remove(props.resourceId)
  }
}
</script>

<style lang="less" scoped>
.bar-main {
  position: absolute;
  float: right;
  right: 10px;
  z-index: 10;
  border-radius: 2px;
  cursor: pointer !important;
  font-size: 16px !important;
}
</style>
