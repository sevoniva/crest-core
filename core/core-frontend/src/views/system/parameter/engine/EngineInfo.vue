<template>
  <EngineInfoTemplate ref="engineInfoTemplate" setting-key="engine" @edit="edit" />
  <engine-edit ref="editor" @saved="refresh" />
</template>

<script lang="ts" setup>
import { ref } from 'vue'

import EngineInfoTemplate from '@/views/system/parameter/engine/EngineInfoTemplate.vue'
import EngineEdit from '@/views/system/parameter/engine/EngineEdit.vue'
// 衔接当前组件交互和状态同步
const editor = ref()
// 衔接当前组件交互和状态同步
const engineInfoTemplate = ref()

// 衔接当前组件交互和状态同步
const edit = () => {
  editor?.value.edit()
}

// 触发组件重新渲染或刷新
const refresh = () => {
  engineInfoTemplate?.value.getEngine()
}
</script>
