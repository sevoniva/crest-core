<script setup lang="ts">
import { ElCollapseItem, ElSwitch } from 'element-plus-secondary'
import { computed, PropType, ref, toRefs } from 'vue'

// 可折叠开关项属性
const props = defineProps({
  modelValue: {
    type: Boolean
  },
  changeModel: {
    type: Object
  },
  title: String,
  themes: {
    type: String as PropType<EditorTheme>,
    default: 'dark'
  },
  showSwitch: {
    type: Boolean,
    required: false,
    default: true
  }
})
// 开关值和模型变化事件
const emit = defineEmits(['update:modelValue', 'modelChange'])
// 保持标题、主题和模型配置的响应式引用
const { changeModel, title, themes } = toRefs(props)

// 折叠项实例引用，用于在开关变化时同步展开状态
const collapseItem = ref()
// 开关点击时同步模型变化并切换折叠状态
const onSwitchChange = () => {
  emit('modelChange', changeModel.value)
  if (!props.modelValue && !collapseItem.value.isActive) {
    collapseItem.value.handleHeaderClick()
  }

  if (props.modelValue && collapseItem.value.isActive) {
    collapseItem.value.handleHeaderClick()
  }
}
// 对外 v-model 的双向计算值
const switchValue = computed({
  get() {
    return props.modelValue
  },
  set(value) {
    emit('update:modelValue', value)
  }
})
</script>
<template>
  <el-collapse-item ref="collapseItem" :effect="themes" v-bind="$attrs">
    <template #title>
      <div class="collapse-header">
        <span>
          {{ title }}
        </span>
        <div>
          <el-switch
            v-show="showSwitch"
            v-model="switchValue"
            :effect="themes"
            size="small"
            @click.stop="onSwitchChange"
          />
        </div>
      </div>
    </template>
    <slot />
  </el-collapse-item>
</template>
<style scoped lang="less">
.collapse-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-right: 10px;
  flex-grow: 1;
  :deep(.ed-switch.is-checked .ed-switch__core > .ed-switch__action) {
    left: calc(100% - 12px);
  }
  :deep(span.ed-switch__core) {
    min-width: 24px;
    border: none;
    height: 6px;
    border-radius: 3px;
    .ed-switch__action {
      left: 0;
      box-shadow: 0 2px 4px rgba(31, 35, 41, 0.12);
    }
  }
}
</style>
