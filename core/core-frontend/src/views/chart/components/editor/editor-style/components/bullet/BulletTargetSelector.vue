<script lang="tsx" setup>
import { onMounted, PropType, reactive, watch } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { COLOR_PANEL, DEFAULT_MISC } from '@/views/chart/components/editor/util/chart'
import { cloneDeep, defaultsDeep } from 'lodash-es'

// 子弹图目标线设置面板的国际化文案函数
const { t } = useI18n()
// 目标线样式选择器属性
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
  },
  selectorType: {
    type: String
  }
})

// 颜色选择器预设色板
const predefineColors = COLOR_PANEL

// 子弹图目标线样式表单状态
const state = reactive({
  bulletTargetForm: {
    bar: {
      target: {
        fill: 'rgb(0,0,0)',
        size: 20,
        showType: 'dynamic',
        value: 0,
        name: ''
      }
    }
  }
})
// 样式变化时通知父级更新图表杂项配置
const emit = defineEmits(['onBasicStyleChange', 'onMiscChange'])
// 监听相关数据变化并同步组件状态
watch(
  () => props.chart.customAttr.misc,
  () => {
    init()
  },
  { deep: true }
)
// 提交目标线样式变更，并保证固定目标值不为空
const changeStyle = (prop?) => {
  if (state.bulletTargetForm.bar.target.value === null) {
    state.bulletTargetForm.bar.target.value = 1
  }
  emit('onMiscChange', { data: { bullet: { ...state.bulletTargetForm } }, requestData: true }, prop)
}

// 从图表配置中读取子弹图目标线样式，并补齐默认值
const init = () => {
  const chart = JSON.parse(JSON.stringify(props.chart))
  if (chart.customAttr) {
    let customAttr = null
    if (Object.prototype.toString.call(chart.customAttr) === '[object Object]') {
      customAttr = JSON.parse(JSON.stringify(chart.customAttr))
    } else {
      customAttr = JSON.parse(chart.customAttr)
    }
    state.bulletTargetForm = defaultsDeep(customAttr.misc.bullet, cloneDeep(DEFAULT_MISC.bullet))
  }
}

onMounted(() => {
  init()
})
</script>

<template>
  <el-form
    ref="bulletTargetForm"
    :model="state.bulletTargetForm"
    size="small"
    label-position="top"
    @submit.prevent
  >
    <div v-if="selectorType === 'target'">
      <el-form-item class="form-item" :class="'form-item-' + themes">
        <el-radio-group
          :effect="themes"
          v-model="state.bulletTargetForm.bar.target.showType"
          @change="changeStyle('bar.target.name')"
        >
          <el-radio :effect="themes" label="dynamic">{{ t('chart.dynamic') }}</el-radio>
          <el-radio :effect="themes" label="fixed">{{ t('chart.fix') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <div v-if="state.bulletTargetForm.bar.target.showType === 'dynamic'">
        <div style="flex: 1; display: flex">
          <el-form-item
            :label="t('visualization.color')"
            class="form-item"
            :class="'form-item-' + themes"
            style="padding-right: 4px"
          >
            <el-color-picker
              v-model="state.bulletTargetForm.bar.target.fill"
              :predefine="predefineColors"
              :effect="themes"
              @change="changeStyle('bar.target.fill')"
              show-alpha
              is-custom
            />
          </el-form-item>
          <el-form-item
            :label="t('chart.height')"
            class="form-item"
            :class="'form-item-' + themes"
            style="width: 100%; padding-left: 4px"
          >
            <el-input-number
              :effect="props.themes"
              v-model="state.bulletTargetForm.bar.target.size"
              :min="1"
              :max="100"
              size="small"
              controls-position="right"
              @change="changeStyle('bar.target.size')"
            />
          </el-form-item>
        </div>
      </div>
      <div v-if="state.bulletTargetForm.bar.target.showType === 'fixed'">
        <div style="flex: 1; display: flex">
          <el-form-item
            :label="t('chart.progress_target')"
            class="form-item"
            :class="'form-item-' + themes"
            style="width: 100%"
          >
            <el-input-number
              :effect="props.themes"
              v-model="state.bulletTargetForm.bar.target.value"
              :min="1"
              size="small"
              controls-position="right"
              @change="changeStyle('bar.target.value')"
            />
          </el-form-item>
        </div>
        <div style="flex: 1; display: flex">
          <el-form-item
            :label="t('visualization.color')"
            class="form-item"
            :class="'form-item-' + themes"
            style="padding-left: 4px"
          >
            <el-color-picker
              v-model="state.bulletTargetForm.bar.target.fill"
              :predefine="predefineColors"
              :effect="themes"
              @change="changeStyle('bar.target.fill')"
              show-alpha
              is-custom
            />
          </el-form-item>
          <el-form-item
            :label="t('chart.height')"
            class="form-item"
            :class="'form-item-' + themes"
            style="width: 100%; padding-left: 4px"
          >
            <el-input-number
              :effect="props.themes"
              v-model="state.bulletTargetForm.bar.target.size"
              :min="1"
              :max="100"
              size="small"
              controls-position="right"
              @change="changeStyle('bar.target.size')"
            />
          </el-form-item>
        </div>
      </div>
    </div>
  </el-form>
</template>

<style lang="less" scoped></style>
