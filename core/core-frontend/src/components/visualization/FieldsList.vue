<template>
  <div @mousedown="fieldsAreaDown" class="field-main">
    <el-button
      v-for="field in fields"
      :key="field.id"
      :title="field.name"
      size="mini"
      class="field-area"
      @click="fieldSelect(field)"
    >
      {{ field.name }}
    </el-button>
  </div>
</template>

<script lang="ts" setup>
import { PropType, toRefs } from 'vue'
import { useEmitt } from '@/hooks/web/useEmitt'

// 定义组件入参，约束外部传入配置
const props = defineProps({
  fields: {
    type: Array as PropType<Array<Record<string, any>>>,
    default: () => []
  },
  element: {
    type: Object as PropType<Record<string, any>>,
    default: null
  }
})

const { fields, element } = toRefs(props)

// 衔接当前组件交互和状态同步
const fieldSelect = field => {
  useEmitt().emitter.emit('fieldSelect-' + element.value.id, field)
}

// 衔接当前组件交互和状态同步
const fieldsAreaDown = e => {
  // ignore
  e.preventDefault()
}
</script>

<style scoped lang="less">
.field-main {
  width: 183px;
  max-height: 300px;
  overflow-x: hidden;
  overflow-y: auto;
}

.field-area {
  width: 174px;
  margin: 4px 0 0 0;
  text-align: left;
  margin-left: 0px !important;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
