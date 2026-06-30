<script lang="ts" setup>
import { inject, computed, PropType } from 'vue'
import { getCSSVariable } from '@/utils/color'

// 平铺筛选组件的选项、禁用状态和自定义样式属性
const props = defineProps({
  options: {
    type: Array as PropType<Array<Record<string, any>>>,
    default: () => []
  },
  disabled: {
    type: Boolean,
    default: false
  },
  activeItems: {
    type: Array as PropType<any[]>,
    default: () => []
  },
  selectStyle: {
    type: Object,
    default: () => ({})
  }
})
// 大屏筛选器注入的统一主题样式
const customStyle: any = inject('$custom-style-filter')

// 合并组件自带样式与全局筛选器背景色
const customSelectStyle = computed(() => {
  return customStyle
    ? { ...props.selectStyle, background: customStyle.background }
    : props.selectStyle
})

// 计算选项文字颜色和字号
const customColor = computed(() => {
  return customStyle
    ? { color: customStyle.text, fontSize: customStyle.placeholderSize + 'px' }
    : {}
})

// 筛选项高度跟随查询条件配置
const boxHeight = computed(() => {
  return `${customStyle?.queryConditionHeight || 32}px`
})

// 激活下划线颜色优先使用自定义按钮色
const btnColor = computed(() => {
  return customStyle ? customStyle.btnColor : getCSSVariable()
})

// 选中项点击时向父组件提交值
const emits = defineEmits(['handleItemClick'])
// 处理平铺项点击，并在禁用状态下阻止交互
const handleItemClick = (item: any) => {
  if (props.disabled) return
  emits('handleItemClick', item.value)
}
</script>

<template>
  <div :style="customSelectStyle" class="flat-select" :class="disabled && 'disabled-flat'">
    <el-scrollbar>
      <div class="scrollbar-flex-content">
        <p
          @click="handleItemClick(item)"
          v-for="item in options"
          :key="item.value"
          :style="customColor"
          class="select-item"
          :class="activeItems.includes(item.value) && 'active-select'"
        >
          {{ item.label }}
        </p>
      </div>
    </el-scrollbar>
  </div>
</template>

<style lang="less" scoped>
.flat-select {
  .ed-scrollbar.ed-scrollbar.ed-scrollbar {
    padding: 0;
  }
  .scrollbar-flex-content {
    display: flex;
    width: fit-content;
    .select-item {
      height: v-bind(boxHeight);
      padding: 0 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      cursor: pointer;
      white-space: nowrap;
      &.active-select::after {
        content: '';
        width: 80%;
        height: 2px;
        position: absolute;
        bottom: 0;
        left: 50%;
        transform: translateX(-50%);
        background-color: v-bind(btnColor) !important;
      }
    }
  }

  &.disabled-flat {
    .select-item {
      cursor: not-allowed;
      &.active-select::after {
        background-color: #eff0f1 !important;
      }
    }
  }
}
</style>
