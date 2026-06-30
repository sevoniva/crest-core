<script setup lang="ts">
import { propTypes } from '@/utils/propTypes'

// 定义组件入参，约束外部传入配置
const props = defineProps({
  slideShow: propTypes.bool.def(true)
})

// 定义组件向父级抛出的事件
const emits = defineEmits([])

// 衔接当前组件交互和状态同步
const slideOpenChange = () => {
  emits('update:slideShowChange', !props.slideShow)
}
</script>

<template>
  <div @click="slideOpenChange" class="flexible-button-area">
    <el-icon v-if="slideShow"><ArrowLeft /></el-icon>
    <el-icon v-else><ArrowRight /></el-icon>
  </div>
</template>

<style lang="less" scoped>
.flexible-button-area {
  position: absolute;
  height: 60px;
  width: 16px;
  left: 0;
  top: calc(50% - 30px);
  background-color: #ffffff;
  border-radius: 0 4px 4px 0;
  cursor: pointer;
  z-index: 10;
  display: flex;
  align-items: center;
  border-top: 1px solid #d7d7d7;
  border-right: 1px solid #d7d7d7;
  border-bottom: 1px solid #d7d7d7;
}
</style>
