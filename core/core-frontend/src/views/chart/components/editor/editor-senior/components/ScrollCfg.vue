<script lang="tsx" setup>
import { PropType, reactive, watch } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'

// 滚动配置面板的国际化文案函数
const { t } = useI18n()

// 滚动配置面板属性
const props = defineProps({
  chart: {
    type: Object,
    required: true
  },
  themes: {
    type: String as PropType<EditorTheme>,
    default: 'dark'
  },
  propertyInner: {
    type: Array<string>
  }
})

// 滚动配置变化时通知父级
const emit = defineEmits(['onScrollCfgChange'])

// 监听相关数据变化并同步组件状态
watch(
  () => props.chart.senior.scrollCfg,
  () => {
    init()
  },
  { deep: true }
)

// 表格滚动配置表单状态
const state = reactive({
  scrollForm: {} as ScrollCfg,
  isAutoBreakLine: false
})

// 提交当前滚动配置
const changeScrollCfg = () => {
  emit('onScrollCfgChange', state.scrollForm)
}

// 从图表高级配置中解析滚动配置
const init = () => {
  const chart = JSON.parse(JSON.stringify(props.chart))
  if (chart.senior) {
    let senior = null
    if (Object.prototype.toString.call(chart.senior) === '[object Object]') {
      senior = JSON.parse(JSON.stringify(chart.senior))
    } else {
      senior = JSON.parse(chart.senior)
    }
    if (senior.scrollCfg) {
      state.scrollForm = senior.scrollCfg
    }
  }
}

init()
</script>

<template>
  <div :style="{ width: '100%', display: 'block' }" @keydown.stop @keyup.stop>
    <el-form
      ref="scrollForm"
      :model="state.scrollForm"
      :disabled="!state.scrollForm.open"
      label-position="top"
    >
      <el-form-item
        v-show="!state.isAutoBreakLine"
        :label="t('chart.row')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-input-number
          :effect="props.themes"
          size="small"
          v-model.number="state.scrollForm.row"
          :min="1"
          :max="1000"
          :precision="0"
          controls-position="right"
          @change="changeScrollCfg"
        />
      </el-form-item>
      <el-form-item
        v-show="state.isAutoBreakLine"
        :label="t('chart.step')"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-input-number
          :effect="props.themes"
          size="small"
          v-model="state.scrollForm.step"
          :min="1"
          :max="10000"
          :precision="0"
          controls-position="right"
          @change="changeScrollCfg"
        />
      </el-form-item>
      <el-form-item
        :label="t('chart.interval') + '(ms)'"
        class="form-item"
        :class="'form-item-' + themes"
      >
        <el-input-number
          :effect="props.themes"
          size="small"
          v-model="state.scrollForm.interval"
          :min="500"
          :step="1000"
          :precision="0"
          controls-position="right"
          @change="changeScrollCfg"
        />
      </el-form-item>
    </el-form>
  </div>
</template>

<style lang="less" scoped>
.shape-item {
  padding: 6px;
  border: none;
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.form-item-slider :deep(.el-form-item__label) {
  font-size: 12px;
  line-height: 38px;
}
.form-item :deep(.el-form-item__label) {
  font-size: 12px;
}
.el-select-dropdown__item {
  padding: 0 20px;
}
span {
  font-size: 12px;
}
.el-form-item {
  margin-bottom: 6px;
}

.switch-style {
  position: absolute;
  right: 10px;
  margin-top: -4px;
}
.color-picker-style {
  cursor: pointer;
  z-index: 1003;
}
</style>
