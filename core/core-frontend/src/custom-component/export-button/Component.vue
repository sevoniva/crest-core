<script lang="ts" setup>
import { computed, toRefs } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { exportFilteredExcel } from '@/utils/visualization/filteredExcelExportService'
import { defaultExportButtonConfig } from '@/utils/visualization/filteredExcelExport.mjs'

defineOptions({
  inheritAttrs: false
})

// Excel 导出按钮组件属性
const props = defineProps({
  element: {
    type: Object,
    default() {
      return {
        exportButton: null,
        style: {}
      }
    }
  },
  showPosition: {
    type: String,
    required: false,
    default: 'canvas'
  }
})

// 保持组件配置和显示位置的响应式引用
const { element, showPosition } = toRefs(props)
// 合并后的导出按钮配置
const exportButton = computed(() => defaultExportButtonConfig(element.value?.exportButton))
// 仅预览场景允许点击导出
const isPreview = computed(() => showPosition.value.includes('preview'))
// 根据组件样式生成按钮样式
const buttonStyle = computed(() => {
  const style = element.value?.style || {}
  return {
    color: style.color || '#ffffff',
    backgroundColor: style.backgroundColor || '#3B82F6',
    fontSize: (style.fontSize || 14) + 'px',
    fontWeight: style.fontWeight || 400,
    borderColor: style.borderActive ? style.borderColor : 'transparent',
    borderWidth: style.borderActive ? style.borderWidth + 'px' : '0',
    borderStyle: style.borderActive ? style.borderStyle : 'solid',
    borderRadius: (style.borderRadius || 4) + 'px',
    cursor: isPreview.value ? 'pointer' : 'default'
  }
})

// 预览状态下触发筛选 Excel 导出
const handleClick = () => {
  if (!isPreview.value) {
    return
  }
  exportFilteredExcel({
    targetViewId: exportButton.value.targetViewId,
    scope: exportButton.value.scope,
    content: exportButton.value.content
  })
}
</script>

<template>
  <button class="excel-export-button component" :style="buttonStyle" @click.stop="handleClick">
    <el-icon v-if="element?.loading" class="button-icon"><Loading /></el-icon>
    <span>{{ exportButton.text }}</span>
  </button>
</template>

<style lang="less" scoped>
.excel-export-button {
  width: 100%;
  height: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 12px;
  box-sizing: border-box;
  outline: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  &:hover {
    filter: brightness(1.05);
  }

  &:active {
    filter: brightness(0.95);
  }
}

.button-icon {
  font-size: 14px;
}
</style>
