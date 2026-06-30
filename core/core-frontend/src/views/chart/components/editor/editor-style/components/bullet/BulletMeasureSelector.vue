<script lang="tsx" setup>
import { onMounted, PropType, reactive, watch } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { COLOR_PANEL, DEFAULT_MISC } from '@/views/chart/components/editor/util/chart'
import { cloneDeep, defaultsDeep } from 'lodash-es'

// 子弹图度量条设置面板的国际化文案函数
const { t } = useI18n()
// 度量条样式选择器属性
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

// 子弹图度量条样式表单状态
const state = reactive({
  bulletMeasureForm: {
    bar: {
      measures: {
        fill: 'rgba(0,128,255,1)',
        size: 15,
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
// 提交度量条样式变更
const changeStyle = (prop?) => {
  emit(
    'onMiscChange',
    { data: { bullet: { ...state.bulletMeasureForm } }, requestData: true },
    prop
  )
}

// 从图表配置中读取子弹图度量条样式，并补齐默认值
const init = () => {
  const chart = JSON.parse(JSON.stringify(props.chart))
  if (chart.customAttr) {
    let customAttr = null
    if (Object.prototype.toString.call(chart.customAttr) === '[object Object]') {
      customAttr = JSON.parse(JSON.stringify(chart.customAttr))
    } else {
      customAttr = JSON.parse(chart.customAttr)
    }
    state.bulletMeasureForm = defaultsDeep(customAttr.misc.bullet, cloneDeep(DEFAULT_MISC.bullet))
  }
}

onMounted(() => {
  init()
})
</script>

<template>
  <el-form
    ref="bulletMeasureForm"
    :model="state.bulletMeasureForm"
    size="small"
    label-position="top"
    @submit.prevent
  >
    <div v-if="selectorType === 'measure'">
      <div style="flex: 1; display: flex">
        <el-form-item
          :label="t('visualization.backgroundColor')"
          class="form-item"
          :class="'form-item-' + themes"
          style="padding-right: 4px"
        >
          <el-color-picker
            v-model="state.bulletMeasureForm.bar.measures.fill"
            :predefine="predefineColors"
            :effect="themes"
            @change="changeStyle('bar.measures.fill')"
            show-alpha
            is-custom
          />
        </el-form-item>
        <el-form-item
          :label="t('chart.radar_size')"
          class="form-item"
          :class="'form-item-' + themes"
          style="width: 100%; padding-left: 4px"
        >
          <el-input-number
            :effect="props.themes"
            v-model="state.bulletMeasureForm.bar.measures.size"
            :min="1"
            :max="100"
            size="small"
            controls-position="right"
            @change="changeStyle('bar.measures.size')"
          />
        </el-form-item>
      </div>
    </div>
  </el-form>
</template>

<style lang="less" scoped></style>
