<script lang="ts" setup>
import { keycodes } from '@/utils/shortcutKey.js'
import eventBus from '@/utils/eventBus'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { toRefs } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { sanitizeHtml } from '@/utils/sanitizeHtml'

/** 当前文本是否处于可编辑状态 */
const canEdit = ref(false)
/** 用于识别组合键的 Ctrl 键键码 */
const ctrlKey = ref(17)
/** 当前是否按下 Ctrl 键 */
const isCtrlDown = ref(false)

/** 向外同步文本输入内容的事件 */
const emit = defineEmits(['input'])
/** 跑马灯文本内容节点引用 */
const text = ref(null)
/** 跑马灯外层容器节点引用 */
const textOut = ref(null)
/** 动画起点的缩放偏移值 */
const scrollScale0 = ref('100%')
/** 动画终点的缩放偏移值 */
const scrollScale100 = ref('100%')
/** 定时刷新滚动尺寸的计时器 */
let timeId = null
/** 外层容器的实时宽度 */
const textOutClientWidth = ref(200)

/** 滚动文本组件的输入属性 */
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
  },
  showPosition: {
    required: false,
    type: String,
    default: 'preview'
  }
})

/** 从属性中解构可响应的组件数据和展示位置 */
const { element, showPosition } = toRefs(props)
/** 经过安全过滤后的文本 HTML */
const safePropValue = computed(() => sanitizeHtml(element.value?.propValue))
/** 可视化主画布状态仓库 */
const dvMainStore = dvMainStoreWithOut()
/** 当前编辑模式和选中组件状态 */
const { editMode, curComponent } = storeToRefs(dvMainStore)

/** 画布切换选中组件时退出当前文本编辑状态 */
const onComponentClick = () => {
  if (curComponent.value.id !== element.value.id) {
    canEdit.value = false
  }
}

/** 将编辑区输入内容同步给外层组件 */
const handleInput = e => {
  emit('input', element.value, e.target.innerHTML)
}

/** 处理文本编辑时的快捷键拦截，避免触发画布级快捷操作 */
const handleKeydown = e => {
  // 阻止冒泡，防止触发复制、粘贴组件操作
  canEdit.value && e.stopPropagation()
  if (e.keyCode == ctrlKey.value) {
    isCtrlDown.value = true
  } else if (isCtrlDown.value && canEdit.value && keycodes.includes(e.keyCode)) {
    e.stopPropagation()
  } else if (e.keyCode == 46) {
    // 删除键在编辑态内消费，避免误删画布组件
    e.stopPropagation()
  }
}

/** 释放 Ctrl 键时恢复快捷键状态 */
const handleKeyup = e => {
  // 阻止冒泡，防止触发复制、粘贴组件操作
  canEdit.value && e.stopPropagation()
  if (e.keyCode == ctrlKey.value) {
    isCtrlDown.value = false
  }
}

/** 编辑态按下鼠标时阻断画布拖拽事件 */
const handleMousedown = e => {
  if (canEdit.value) {
    e.stopPropagation()
  }
}

/** 粘贴文本时移除来源样式，仅保留纯文本内容 */
const clearStyle = e => {
  e.preventDefault()
  const clp = e.clipboardData
  const text = clp.data('text/plain') || ''
  if (text !== '') {
    document.execCommand('insertText', false, text)
  }
}

/** 编辑区失焦时保存文本内容并退出编辑态 */
const handleBlur = e => {
  element.value.propValue = e.target.innerHTML || ''
  const html = e.target.innerHTML
  if (html !== '') {
    element.value.propValue = e.target.innerHTML
  } else {
    element.value.propValue = ''
    nextTick(function () {
      element.value.propValue = ''
    })
  }
  canEdit.value = false
}

/** 当前是否应启用跑马灯动画样式 */
const marqueeTxt = computed(
  () => !canEdit.value && !element.value['resizing'] && !element.value['dragging']
)

/** 双击组件后进入文本编辑态并选中全部文本 */
const setEdit = () => {
  if (['canvas', 'canvasDataV', 'edit'].includes(showPosition.value) && !element.value['isLock']) {
    canEdit.value = true
    // 进入编辑时全选文本，方便直接替换内容
    selectText(text.value)
  }
}
/** 选中指定节点内的全部文本内容 */
const selectText = element => {
  const selection = window.getSelection()
  const range = document.createRange()
  range.selectNodeContents(element)
  selection.removeAllRanges()
  selection.addRange(range)
}
onBeforeUnmount(() => {
  eventBus.off('componentClick', onComponentClick)
  if (timeId) {
    clearInterval(timeId)
    timeId = null
  }
})

/** 基于实际容器宽度计算跑马灯动画变量，保证不同缩放比例下的视觉速度一致 */
const varStyle = computed(() => [
  {
    '--scroll-speed': `${
      element.value.style.scrollSpeed === 0 || !textOut.value
        ? 0
        : (textOutClientWidth.value + text.value.clientWidth) / element.value.style.scrollSpeed
    }s`,
    '--scroll-scale0': `${scrollScale0.value}`,
    '--scroll-scale100': `${scrollScale100.value}`
  }
])

/** 初始化跑马灯尺寸刷新任务 */
const init = () => {
  timeId = setInterval(() => {
    const outerId = ['canvas', 'canvasDataV', 'edit'].includes(showPosition.value)
      ? 'shape-id-' + element.value.id
      : 'wrapper-outer-id-' + element.value.id
    const componentOut = document.getElementById(outerId)
    if (componentOut && text.value) {
      const textValue = text.value.clientWidth
      textOutClientWidth.value = componentOut.clientWidth
      scrollScale0.value = (textOutClientWidth.value * 100) / textValue + '%'
      scrollScale100.value = '-100%'
    } else {
      scrollScale0.value = '100%'
      scrollScale100.value = '-100%'
    }
  }, 1000)
}
onMounted(() => {
  init()
})
</script>

<template>
  <div
    v-if="editMode == 'edit'"
    :style="varStyle"
    class="v-text"
    ref="textOut"
    @keydown="handleKeydown"
    @keyup="handleKeyup"
    @dblclick="setEdit"
  >
    <!-- nosemgrep: javascript.vue.security.audit.xss.templates.avoid-v-html.avoid-v-html -->
    <div
      ref="text"
      :contenteditable="canEdit"
      :class="{ 'can-edit': canEdit, 'marquee-txt': marqueeTxt }"
      tabindex="0"
      @paste="clearStyle"
      @mousedown="handleMousedown"
      @blur="handleBlur"
      @input="handleInput"
      v-html="safePropValue"
    ></div>
  </div>
  <div v-else class="v-text preview" ref="textOut" :style="varStyle">
    <!-- nosemgrep: javascript.vue.security.audit.xss.templates.avoid-v-html.avoid-v-html -->
    <div class="marquee-txt" ref="text" v-html="safePropValue"></div>
  </div>
</template>

<style lang="less" scoped>
.v-text {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  div {
    outline: none;
    word-break: break-all;
    padding: 4px;
    white-space: nowrap;
  }

  .can-edit {
    cursor: text;
    width: 100%;
    display: grid;
    align-items: center;
    height: 100%;
  }
}

.preview {
  user-select: none;
}

.marquee {
  margin-left: 100px;
  width: 300px;
  white-space: nowrap;
  overflow: hidden;
  border: 1px solid #4c7cee;
}
.marquee-txt {
  display: inline-block;
  animation: marqueeAnimation var(--scroll-speed) linear infinite;
}
@keyframes marqueeAnimation {
  0% {
    transform: translate(var(--scroll-scale0), 0);
  }
  100% {
    transform: translate(var(--scroll-scale100), 0);
  }
}
</style>
