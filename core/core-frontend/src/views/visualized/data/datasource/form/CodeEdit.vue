<script lang="ts" setup>
import { ref, PropType, watch, onMounted } from 'vue'
import { propTypes } from '@/utils/propTypes'
import { VAceEditor } from 'vue3-ace-editor'
import { formatJson, formatXml } from './format-utils'
import './ace-config'

// 代码编辑器属性，控制高度、主题、格式化模式和只读状态
const props = defineProps({
  height: [String, Number],
  data: propTypes.string.def(''),
  theme: propTypes.string.def('chrome'),
  init: Function,
  enableFormat: propTypes.bool.def(true),
  readOnly: propTypes.bool.def(true),
  modes: {
    type: Array as PropType<string[]>,
    default: () => ['text', 'json', 'xml']
  },
  mode: propTypes.string.def('text')
})

// 编辑器展示和回传的格式化文本
const formatData = ref('')
// 编辑内容变化后同步到父组件 v-model
watch(formatData, () => {
  emits('update:modelValue', formatData.value)
})

// 主题变化时重新格式化编辑器展示内容
watch([props.theme], () => {
  format()
})
onMounted(() => {
  format()
})
// 初始化 Ace 编辑器实例并应用只读和外部初始化回调
const editorInit = editor => {
  if (props.readOnly) {
    editor.setReadOnly(true)
  }
  if (props.init) {
    props.init(editor)
  }
}
// 根据当前模式格式化 JSON、XML 或原始文本
const format = () => {
  if (props.enableFormat) {
    if (props.data) {
      switch (props.mode) {
        case 'json':
          formatData.value = formatJson(props.data)
          break
        case 'xml':
          formatData.value = formatXml(props.data)
          break
        default:
          formatData.value = props.data
      }
    }
  } else {
    formatData.value = props.data
  }
}
// 文本变化时同步到父组件的 v-model
const emits = defineEmits(['update:modelValue'])
</script>

<template>
  <v-ace-editor
    v-model:value="formatData"
    :lang="mode"
    :theme="theme"
    :style="{ height }"
    @init="editorInit"
  />
</template>
