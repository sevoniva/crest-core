<script lang="ts" setup>
import { toRefs, onBeforeMount, type PropType, type Ref, inject, computed, nextTick } from 'vue'
interface SelectConfig {
  id: string
  defaultValueCheck: boolean
  defaultNumValueEnd: number
  numValueEnd: number
  queryConditionWidth: number
  numValueStart: number
  defaultNumValueStart: number
  placeholder: string
}
// 查询条件占位符显示配置由上层注入
const placeholder: Ref = inject('placeholder')

// 根据占位符开关决定是否展示配置中的提示文案
const placeholderText = computed(() => {
  if (placeholder?.value?.placeholderShow) {
    return props.config.placeholder
  }
  return ' '
})

// 数值范围查询组件的配置属性和编辑态标记
const props = defineProps({
  config: {
    type: Object as PropType<SelectConfig>,
    default: () => {
      return {
        id: '',
        queryConditionWidth: 0,
        defaultNumValueEnd: '',
        defaultNumValueStart: '',
        numValueEnd: '',
        numValueStart: '',
        defaultValueCheck: false
      }
    }
  },
  isConfig: {
    type: Boolean,
    default: false
  }
})

// 保持查询配置的响应式引用，便于直接回写起止值
const { config } = toRefs(props)
// 根据默认值开关初始化数值范围查询参数
const setParams = () => {
  if (!config.value.defaultValueCheck) {
    config.value.numValueEnd = undefined
    config.value.numValueStart = undefined
    return
  }
  const { defaultNumValueEnd, defaultNumValueStart } = config.value
  config.value.numValueEnd = defaultNumValueEnd
  config.value.numValueStart = defaultNumValueStart
}
onBeforeMount(() => {
  setParams()
})
// 上层注入的查询条件宽度计算函数
const queryConditionWidth = inject('com-width', Function, true)
// 大屏筛选器注入的背景和边框样式
const customStyle = inject<{ background: string; border: string }>('$custom-style-filter')
// 上层注入的确认查询触发函数
const isConfirmSearch = inject('is-confirm-search', Function, true)

// 根据占位符模式和配置宽度计算输入框宽度
const getCustomWidth = () => {
  if (placeholder?.value?.placeholderShow) {
    if (props.config.queryConditionWidth === undefined) {
      return queryConditionWidth()
    }
    return props.config.queryConditionWidth
  }
  return 227
}
// 数字输入框宽度样式
const selectStyle = computed(() => {
  return { width: getCustomWidth() + 'px' }
})
// 非配置态数值变化后触发确认查询
const handleValueChange = () => {
  if (!props.isConfig) {
    nextTick(() => {
      isConfirmSearch(config.value.id)
    })
    return
  }
}

// 输入框控制按钮边框色跟随自定义主题
const color = computed(() => {
  return customStyle.border
})
</script>

<template>
  <div class="num-search-select" :style="{ background: customStyle.background }">
    <el-input-number
      :placeholder="placeholderText"
      @change="handleValueChange"
      :style="selectStyle"
      controls-position="right"
      v-model="config.numValueStart"
    />
    <div class="num-value_line"></div>
    <el-input-number
      :placeholder="placeholderText"
      :style="selectStyle"
      @change="handleValueChange"
      controls-position="right"
      v-model="config.numValueEnd"
    />
  </div>
</template>

<style lang="less" scoped>
.num-search-select {
  display: flex;
  align-items: center;
  border-radius: 6px;
  .num-value_line {
    background: #1f2329;
    width: 12px;
    height: 1px;
    margin: 0 8px;
  }

  :deep(.ed-input-number__increase),
  :deep(.ed-input-number__decrease) {
    background-color: transparent !important;
    border-color: v-bind(color) !important;
  }
}
</style>
