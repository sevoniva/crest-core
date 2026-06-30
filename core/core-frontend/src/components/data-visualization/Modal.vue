<script setup lang="ts">
defineProps({
  show: {
    type: Boolean,
    default: false
  }
})

// 定义组件向父级抛出的事件
const emit = defineEmits(['change'])
// 根据当前数据计算界面可用状态
const hide = () => {
  emit('change')
}

// 衔接当前组件交互和状态同步
const stopPropagation = e => {
  e.stopPropagation()
}
</script>

<template>
  <div v-if="show" class="modal-bg" @click="hide">
    <div class="fadeInLeft animated modal" @click="stopPropagation">
      <slot></slot>
    </div>
  </div>
</template>

<style lang="less" scoped>
.modal-bg {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1001;

  .modal {
    width: 400px;
    background: #fff;
    height: 100%;
  }
}
</style>
