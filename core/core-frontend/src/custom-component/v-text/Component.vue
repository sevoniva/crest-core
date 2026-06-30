<script lang="ts" setup>
import { keycodes } from '@/utils/shortcutKey.js'
import eventBus from '@/utils/eventBus'
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { toRefs } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { sanitizeHtml } from '@/utils/sanitizeHtml'

// 控制文本组件是否进入编辑态
const canEdit = ref(false)
// 记录 Ctrl 键键码
const ctrlKey = ref(17)
// 标记 Ctrl 键是否处于按下状态
const isCtrlDown = ref(false)

// 定义文本输入事件
const emit = defineEmits(['input'])
// 文本 DOM 引用
const text = ref(null)

// 接收文本内容和组件实例配置
const props = defineProps({
  propValue: {
    type: String,
    required: true,
    default: ''
  },
  element: {
    type: Object,
    default() {
      return {
        id: null,
        propValue: ''
      }
    }
  }
})

const { element } = toRefs(props)
// 过滤富文本内容后用于安全渲染
const safePropValue = computed(() => sanitizeHtml(element.value?.propValue))
const dvMainStore = dvMainStoreWithOut()
const { editMode, curComponent } = storeToRefs(dvMainStore)

// 在切换组件时退出文本编辑态
const onComponentClick = () => {
  if (curComponent.value.id !== element.value.id) {
    canEdit.value = false
  }
}

// 同步可编辑区域输入内容
const handleInput = e => {
  emit('input', element.value, e.target.innerHTML)
}

// 处理编辑态快捷键按下事件
const handleKeydown = e => {
  // 阻止冒泡，防止触发复制、粘贴组件操作
  canEdit.value && e.stopPropagation()
  if (e.keyCode == ctrlKey.value) {
    isCtrlDown.value = true
  } else if (isCtrlDown.value && canEdit.value && keycodes.includes(e.keyCode)) {
    e.stopPropagation()
  } else if (e.keyCode == 46) {
    // deleteKey
    e.stopPropagation()
  }
}

// 处理编辑态快捷键释放事件
const handleKeyup = e => {
  // 阻止冒泡，防止触发复制、粘贴组件操作
  canEdit.value && e.stopPropagation()
  if (e.keyCode == ctrlKey.value) {
    isCtrlDown.value = false
  }
}

// 阻止编辑态鼠标按下事件冒泡
const handleMousedown = e => {
  if (canEdit.value) {
    e.stopPropagation()
  }
}

// 清理粘贴内容样式并写入纯文本
const clearStyle = e => {
  e.preventDefault()
  const clp = e.clipboardData
  const text = clp.data('text/plain') || ''
  if (text !== '') {
    document.execCommand('insertText', false, text)
  }

  emit('input', element.value, e.target.innerHTML)
}

// 失焦时回写文本内容并退出编辑态
const handleBlur = e => {
  element.value.propValue = e.target.innerHTML || '&nbsp;'
  const html = e.target.innerHTML
  if (html !== '') {
    element.value.propValue = e.target.innerHTML
  } else {
    element.value.propValue = ''
    nextTick(function () {
      element.value.propValue = '&nbsp;'
    })
  }
  canEdit.value = false
}

// 进入文本编辑态并选中全部内容
const setEdit = () => {
  if (element.value['isLock']) return
  canEdit.value = true
  // 全选
  selectText(text.value)
}
// 选中指定元素中的全部文本
const selectText = element => {
  const selection = window.getSelection()
  const range = document.createRange()
  range.selectNodeContents(element)
  selection.removeAllRanges()
  selection.addRange(range)
}
onBeforeUnmount(() => {
  eventBus.off('componentClick', onComponentClick)
})
</script>

<template>
  <div v-if="editMode == 'edit'" class="v-text" @keydown="handleKeydown" @keyup="handleKeyup">
    <!-- nosemgrep: javascript.vue.security.audit.xss.templates.avoid-v-html.avoid-v-html -->
    <div
      ref="text"
      :contenteditable="canEdit"
      :class="{ 'can-edit': canEdit }"
      tabindex="0"
      :style="{ verticalAlign: element['style'].verticalAlign }"
      @dblclick="setEdit"
      @paste="clearStyle"
      @mousedown="handleMousedown"
      @blur="handleBlur"
      @input="handleInput"
      v-html="safePropValue"
    ></div>
  </div>
  <div v-else class="v-text preview">
    <!-- nosemgrep: javascript.vue.security.audit.xss.templates.avoid-v-html.avoid-v-html -->
    <div :style="{ verticalAlign: element['style'].verticalAlign }" v-html="safePropValue"></div>
  </div>
</template>

<style lang="less" scoped>
.v-text {
  width: 100%;
  height: 100%;
  display: table;
  div {
    display: table-cell;
    width: 100%;
    height: 100%;
    outline: none;
    word-break: break-all;
    padding: 4px;
  }

  .can-edit {
    cursor: text;
    height: 100%;
  }
}

.preview {
  user-select: none;
}
</style>
