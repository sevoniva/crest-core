<template>
  <code-mirror
    :quotaMap="props.linkJumpInfoArray.map(ele => ele.sourceFieldName)"
    ref="myCm"
    dom-id="jumpSetField"
    style="height: 100%"
  ></code-mirror>
</template>

<script setup lang="ts">
import { onBeforeUnmount, PropType, reactive, ref, toRefs } from 'vue'
import CodeMirror from '@/views/visualized/data/dataset/form/SqlCodeEditor.vue'
// 持有 CodeMirror 封装组件实例
const myCm = ref(null)
// 持有真实 CodeMirror 编辑器实例
const mirror = ref(null)
// 定义跳转内容编辑器接收的字段映射和跳转配置
const props = defineProps({
  linkJumpInfoArray: Array as PropType<Array<Record<string, any>>>,
  linkJumpInfo: Object as PropType<Record<string, any>>
})

const { linkJumpInfo } = toRefs(props)
// 保存字段名称自动补全映射和编辑器内容
const state = reactive({
  name2Auto: [],
  content: ''
})
// 保存定时同步编辑器内容的任务句柄
const timer = ref(null)

// 获取当前编辑器光标位置
const getCursor = view => {
  return (
    view?.state?.selection?.main?.from ??
    view?.viewState?.state?.selection?.ranges?.[0]?.from ??
    view?.state?.doc?.length ??
    0
  )
}

// 在当前光标位置插入字段占位符
const insertFieldToCodeMirror = (value: string) => {
  if (!mirror.value) return
  const from = getCursor(mirror.value)
  mirror.value.dispatch({
    changes: { from, insert: value },
    selection: { anchor: from + value.length }
  })
}

// 在字段名称和字段 ID 之间转换跳转表达式内容
const setNameIdTrans = (from, to, originName, name2Auto?: string[]) => {
  if (!originName) {
    return originName
  }
  let name2Id = originName
  const nameIdMap = props.linkJumpInfoArray.reduce((pre, next) => {
    pre[next[from]] = next[to]
    return pre
  }, {})
  const on = originName.match(/\[(.+?)\]/g) || []
  if (on) {
    on.forEach(itm => {
      const ele = itm.slice(1, -1)
      if (name2Auto) {
        name2Auto.push(nameIdMap[ele])
      }
      name2Id = name2Id.replace(`[${ele}]`, `[${nameIdMap[ele]}]`)
    })
  }
  return name2Id
}

// 初始化编辑器内容并定时同步跳转表达式
const editorInit = content => {
  state.name2Auto = []
  if (!mirror.value) {
    mirror.value = myCm.value.codeComInit()
  }
  state.content = setNameIdTrans('sourceFieldId', 'sourceFieldName', content, state.name2Auto)
  mirror.value.dispatch({
    changes: {
      from: 0,
      to: mirror.value.viewState.state.doc.length,
      insert: state.content
    }
  })
  clearTimeout(timer.value)
  timer.value = setInterval(() => {
    const content = mirror.value ? mirror.value.state.doc.toString() : ''
    const contentTrans = setNameIdTrans(
      'sourceFieldName',
      'sourceFieldId',
      content,
      state.name2Auto
    )
    linkJumpInfo.value.content = contentTrans
  }, 1500)
}
defineExpose({
  editorInit,
  insertFieldToCodeMirror
})

onBeforeUnmount(() => {
  clearTimeout(timer.value)
  mirror.value && mirror.value.destroy?.()
})
</script>

<style lang="less" scoped></style>
