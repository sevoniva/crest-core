<script lang="tsx" setup>
import { reactive, watch } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { COLOR_PANEL, COLOR_CASES } from '@/views/chart/components/editor/util/chart'

const { t } = useI18n()

// 定义颜色方案选择器依赖的图表和主题配置
const props = defineProps({
  chart: {
    type: Object,
    required: true
  },
  themes: {
    type: String,
    default: 'dark'
  }
})

const colorCases = COLOR_CASES

const predefineColors = COLOR_PANEL

// 获取默认颜色方案及默认透明度
const getDefaultColorCase = () => {
  const defaultCase = colorCases.find(ele => ele.value === 'default') || colorCases[0]
  return {
    value: defaultCase?.value || 'default',
    colors: [...(defaultCase?.colors || [])],
    alpha: 100
  }
}

// 保存颜色方案表单和当前自定义颜色状态
const state = reactive({
  colorForm: getDefaultColorCase(),
  customColor: null,
  colorIndex: 0
})

// 声明颜色方案变更事件
const emit = defineEmits(['onColorChange'])

// 监听图表配置变化并刷新颜色表单
watch(
  () => props.chart,
  () => {
    init()
  }
)

// 切换系统颜色方案并重置当前自定义颜色
const changeColorOption = (_prop?: string) => {
  const selectedColorCase =
    colorCases.find(ele => ele.value === state.colorForm.value) || colorCases[0]
  state.colorForm.value = selectedColorCase?.value || state.colorForm.value
  state.colorForm.colors = [...(selectedColorCase?.colors || [])]

  state.customColor = state.colorForm.colors[0] || null
  state.colorIndex = 0

  changeColorCase()
}

// 重置自定义颜色为当前系统方案
const resetCustomColor = () => {
  changeColorOption()
}

// 切换当前正在编辑的颜色下标
const switchColor = index => {
  state.colorIndex = index
}

// 将自定义颜色写入颜色方案
const switchColorCase = () => {
  state.colorForm.colors[state.colorIndex] = state.customColor
  changeColorCase()
}

// 向父组件同步颜色方案变更
const changeColorCase = (_prop?: string) => {
  emit('onColorChange', state.colorForm)
}

// 从图表自定义属性初始化颜色方案表单
const init = () => {
  const chart = JSON.parse(JSON.stringify(props.chart))
  if (chart.customAttr) {
    let customAttr = null
    if (Object.prototype.toString.call(chart.customAttr) === '[object Object]') {
      customAttr = JSON.parse(JSON.stringify(chart.customAttr))
    } else {
      customAttr = JSON.parse(chart.customAttr)
    }
    if (customAttr.color) {
      const defaultColorCase = getDefaultColorCase()
      const colors = Array.isArray(customAttr.color.colors) ? customAttr.color.colors : []
      state.colorForm = {
        ...defaultColorCase,
        ...customAttr.color,
        colors: colors.length ? colors : defaultColorCase.colors
      }
      if (!state.customColor || !state.colorForm.colors.includes(state.customColor)) {
        state.customColor = state.colorForm.colors[0] || null
        state.colorIndex = 0
      }
    }
  }
}

init()
</script>

<template>
  <el-form ref="colorForm" :model="state.colorForm" label-width="80px" size="small">
    <div>
      <el-form-item :label="t('chart.color_case')" class="form-item">
        <el-popover placement="bottom" width="400" trigger="click">
          <template #reference>
            <div :style="{ cursor: 'pointer', marginTop: '2px', width: '180px' }">
              <span
                v-for="(c, index) in state.colorForm.colors"
                :key="index"
                :style="{
                  width: '20px',
                  height: '20px',
                  display: 'inline-block',
                  backgroundColor: c
                }"
              />
            </div>
          </template>

          <div style="padding: 6px 10px">
            <div>
              <span class="color-label">{{ t('chart.system_case') }}</span>
              <el-select
                v-model="state.colorForm.value"
                :placeholder="t('chart.pls_slc_color_case')"
                size="small"
                @change="changeColorOption('value')"
              >
                <el-option
                  v-for="option in colorCases"
                  :key="option.value"
                  :label="option.name"
                  :value="option.value"
                  style="display: flex; align-items: center"
                >
                  <div style="float: left">
                    <span
                      v-for="(c, index) in option.colors"
                      :key="index"
                      :style="{
                        width: '20px',
                        height: '20px',
                        float: 'left',
                        backgroundColor: c
                      }"
                    />
                  </div>
                  <span style="margin-left: 4px">{{ option.name }}</span>
                </el-option>
              </el-select>
              <el-button size="small" type="text" style="margin-left: 2px" @click="resetCustomColor"
                >{{ t('chart.reset') }}
              </el-button>
            </div>
            <!-- 自定义配色方案 -->
            <div>
              <div style="display: flex; align-items: center; margin-top: 10px">
                <span class="color-label">{{ t('chart.custom_case') }}</span>
                <span>
                  <el-radio-group v-model="state.customColor" class="color-type">
                    <el-radio
                      v-for="(c, index) in state.colorForm.colors"
                      :key="index"
                      :value="c"
                      style="padding: 2px"
                      @click="switchColor(index)"
                    >
                      <span
                        :style="{
                          width: '20px',
                          height: '20px',
                          display: 'inline-block',
                          backgroundColor: c
                        }"
                      />
                    </el-radio>
                  </el-radio-group>
                </span>
              </div>
              <div style="display: flex; align-items: center; margin-top: 10px">
                <span class="color-label" />
                <span>
                  <el-color-picker
                    v-model="state.customColor"
                    class="color-picker-style"
                    :predefine="predefineColors"
                    @change="switchColorCase"
                  />
                </span>
              </div>
            </div>
          </div>
        </el-popover>
      </el-form-item>
      <!-- 透明度设置 -->
      <el-form-item :label="t('chart.not_alpha')" class="form-item form-item-slider">
        <el-input-number
          :effect="props.themes"
          v-model="state.colorForm.alpha"
          :min="0"
          :max="100"
          size="small"
          controls-position="right"
          @change="changeColorCase('alpha')"
        />
      </el-form-item>
    </div>
  </el-form>
</template>

<style lang="less" scoped>
.form-item-slider :deep(.ed-form-item__label) {
  font-size: 12px;
  line-height: 38px;
}

.form-item :deep(.ed-form-item__label) {
  font-size: 12px;
}

.color-picker-style {
  cursor: pointer;
  z-index: 1003;
}

.color-label {
  display: inline-block;
  width: 60px;
}

.color-type :deep(.ed-radio__input) {
  display: none;
}

.ed-radio {
  margin: 0 2px 0 0 !important;
  border: 1px solid transparent;
}

.ed-radio :deep(.ed-radio__label) {
  padding-left: 0;
}

.ed-radio.is-checked {
  border: 1px solid #0a7be0;
}

.custom-color-style {
  height: 300px;
  overflow-y: auto;
  padding: 4px 12px;
  border: 1px solid #e6e6e6;
}
</style>
