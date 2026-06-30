<script setup lang="ts">
import { computed, reactive, ref, toRefs, watch } from 'vue'
import { ElFormItem, ElInputNumber } from 'element-plus-secondary'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { COLOR_PANEL } from '@/views/chart/components/editor/util/chart'
import { useI18n } from '@/hooks/web/useI18n'
const dvMainStore = dvMainStoreWithOut()

const snapshotStore = snapshotStoreWithOut()
const { canvasStyleData, curComponent } = storeToRefs(dvMainStore)
const { t } = useI18n()

const props = withDefaults(
  defineProps<{
    themes?: EditorTheme
    styleInfo: any
  }>(),
  {
    themes: 'dark'
  }
)
// 声明边框样式变更时向父组件派发的事件
const emits = defineEmits(['onStyleAttrChange'])
const { themes, styleInfo } = toRefs(props)
// 保存边框设置面板的临时状态和预设颜色
const state = reactive({
  fontSize: [],
  isSetting: false,
  predefineColors: COLOR_PANEL
})
// 保存按画布比例换算后的边框宽度和圆角
const styleMounted = ref({
  borderWidth: 0,
  borderRadius: 5
})

const borderStyleList = [
  { name: t('visualization.border_style_solid'), value: 'solid' },
  { name: t('visualization.border_style_dashed'), value: 'dashed' },
  { name: t('visualization.border_style_dotted'), value: 'dotted' }
]

// 根据当前画布比例初始化表单展示值
const styleInit = () => {
  if (styleInfo.value) {
    Object.keys(styleMounted.value).forEach(key => {
      styleMounted.value[key] = Math.round(
        (styleInfo.value[key] * 100) / canvasStyleData.value.scale
      )
    })
  }
}

// 暴露当前边框样式表单对象
const styleForm = computed<any>(() => styleInfo.value)

// 记录快照并派发边框样式变更
const changeStyle = params => {
  snapshotStore.recordSnapshotCache('border-changeStyle')
  emits('onStyleAttrChange', params)
}

// 将指定样式键的当前值派发给父组件
const changeStylePre = key => {
  changeStyle({ key: key, value: styleInfo.value[key] })
}

// 将表单尺寸按画布比例换算后写回样式对象
const sizeChange = key => {
  styleInfo.value[key] = (styleMounted.value[key] * canvasStyleData.value.scale) / 100
  changeStyle({ key: key, value: styleInfo.value[key] })
}

// 判断当前组件是否为需要特殊边框处理的 SVG 三角形
const isSvgComponent = computed(
  () => curComponent.value && curComponent.value.component === 'SvgTriangle'
)

// 监听样式对象变化并刷新按比例换算后的表单值
watch(
  () => styleInfo.value,
  () => {
    styleInit()
  },
  {
    deep: true,
    immediate: true
  }
)
</script>

<template>
  <el-row class="custom-row" style="padding-bottom: 8px">
    <el-form size="small" label-position="top">
      <template v-if="isSvgComponent">
        <el-row style="display: flex">
          <el-form-item
            style="width: 70px"
            :label="t('visualization.color')"
            class="form-item"
            :class="'form-item-' + themes"
          >
            <el-color-picker
              :title="t('visualization.color')"
              :disabled="!styleForm.borderActive"
              v-model="styleForm.borderColor"
              class="color-picker-style"
              :triggerWidth="65"
              is-custom
              show-alpha
              :predefine="state.predefineColors"
              @change="changeStylePre('borderColor')"
            >
            </el-color-picker>
          </el-form-item>
          <el-form-item
            style="width: 150px"
            :label="t('visualization.board_width')"
            class="form-item"
            :class="'form-item-' + themes"
          >
            <el-input-number
              :title="t('visualization.board_width')"
              :min="0"
              :max="50"
              :effect="themes"
              :disabled="!styleForm.borderActive"
              controls-position="right"
              v-model="styleMounted.borderWidth"
              class="color-picker-style"
              @change="sizeChange('borderWidth')"
            >
            </el-input-number>
          </el-form-item>
        </el-row>
      </template>
      <template v-else>
        <el-row style="display: flex">
          <el-form-item
            style="width: 70px"
            :label="t('visualization.color')"
            class="form-item"
            :class="'form-item-' + themes"
          >
            <el-color-picker
              :title="t('visualization.color')"
              :disabled="!styleForm.borderActive"
              v-model="styleForm.borderColor"
              class="color-picker-style"
              :triggerWidth="65"
              is-custom
              show-alpha
              :effect="themes"
              :predefine="state.predefineColors"
              @change="changeStylePre('borderColor')"
            >
            </el-color-picker>
          </el-form-item>
          <el-form-item
            style="width: 150px"
            :label="t('visualization.board_radius')"
            class="form-item"
            :class="'form-item-' + themes"
          >
            <el-input-number
              :title="t('visualization.board_radius')"
              :disabled="!styleForm.borderActive"
              :effect="themes"
              :min="0"
              :max="200"
              controls-position="right"
              v-model="styleMounted.borderRadius"
              class="color-picker-style"
              @change="sizeChange('borderRadius')"
            >
            </el-input-number>
          </el-form-item>
        </el-row>
        <el-row style="display: flex">
          <el-form-item
            style="width: 70px"
            :label="t('visualization.style')"
            class="form-item"
            :class="'form-item-' + themes"
          >
            <el-select
              :effect="themes"
              :disabled="!styleForm.borderActive"
              v-model="styleForm.borderStyle"
              size="small"
              style="width: 65px"
              @change="changeStylePre('borderStyle')"
            >
              <el-option
                class="custom-style-option"
                v-for="option in borderStyleList"
                :key="option.value"
                :label="option.name"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            style="width: 150px"
            :label="t('visualization.board_width')"
            class="form-item"
            :class="'form-item-' + themes"
          >
            <el-input-number
              :title="t('visualization.board_width')"
              :disabled="!styleForm.borderActive"
              :min="0"
              :max="50"
              :effect="themes"
              controls-position="right"
              v-model="styleMounted.borderWidth"
              class="color-picker-style"
              @change="sizeChange('borderWidth')"
            >
            </el-input-number>
          </el-form-item>
        </el-row>
      </template>
    </el-form>
  </el-row>
</template>
