<template>
  <div
    style="display: flex; width: 100%; height: 100%; align-items: center"
    :style="{ 'justify-content': element.style.textAlign }"
  >
    <p>{{ state.nowDate }}</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, toRefs } from 'vue'

// 定义组件入参，约束外部传入配置
const props = defineProps({
  element: {
    type: Object
  }
})

const { element } = toRefs(props)
// 维护组件内部表单、弹窗和临时数据状态
const state = reactive({
  nowDate: '', // 当前日期
  nowWeek: '',
  timer: null
})

// 记录当前选中项和交互焦点
const currentTime = () => {
  state.timer = setInterval(formatDate, 500)
}
// 维护表单数据和校验规则
const formatDate = () => {
  const weekArr = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  let timeFormat = element.value.formatInfo.timeFormat
  const showWeek = element.value.formatInfo.showWeek
  const showDate = element.value.formatInfo.showDate
  const dateFormat = element.value.formatInfo.dateFormat || 'yyyy-MM-dd'
  if (showDate && dateFormat) {
    timeFormat = dateFormat + ' ' + timeFormat
  }

  const date = new Date()

  state.nowDate = date.format(timeFormat)

  if (showWeek) {
    state.nowWeek = weekArr[date.getDay()]
    state.nowDate = state.nowDate + ' ' + state.nowWeek
  }
}

onMounted(() => {
  currentTime()
})

onUnmounted(() => {
  if (state.timer) {
    clearInterval(state.timer) // 在Vue实例销毁前，清除时间定时器
  }
})
</script>

<style lang="less" scoped></style>
