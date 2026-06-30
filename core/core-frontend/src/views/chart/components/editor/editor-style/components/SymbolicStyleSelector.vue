<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { DEFAULT_BASIC_STYLE } from '@/views/chart/components/editor/util/chart'
import { ElMessage, UploadProps } from 'element-plus-secondary'
import { svgStrToUrl } from '@/views/chart/components/js/util'
import { useI18n } from '@/hooks/web/useI18n'
import { cloneDeep, debounce, defaultsDeep } from 'lodash-es'

const props = withDefaults(
  defineProps<{
    chart: ChartObj
    themes?: EditorTheme
    propertyInner?: Array<string>
  }>(),
  {
    themes: 'dark'
  }
)

const { t } = useI18n()
// 判断当前属性是否需要在符号样式面板中展示
const showProperty = prop => props.propertyInner?.includes(prop)
// 声明基础样式和杂项配置变更时向父组件派发的事件
const emit = defineEmits(['onBasicStyleChange', 'onMiscChange'])
// 保存符号样式表单、自定义颜色和上传文件列表状态
const state = reactive({
  basicStyleForm: JSON.parse(JSON.stringify(DEFAULT_BASIC_STYLE)) as ChartBasicStyle,
  customColor: null,
  colorIndex: 0,
  fieldColumnWidth: {
    fieldId: '',
    width: 0
  },
  // 上传控件的预览列表独立维护，实际自定义图标内容存储在 basicStyleForm.customIcon。
  fileList: []
})

// 派发基础样式变更，并按需触发数据重新请求
const changeBasicStyle = (prop?: string, requestData = false) => {
  emit('onBasicStyleChange', { data: state.basicStyleForm, requestData }, prop)
}

// 持有自定义图标上传组件实例
const iconUpload = ref()
// 自定义符号只允许 SVG、JPEG 和 PNG，避免地图渲染层处理未知媒体类型。
const acceptedFileType = ['image/svg+xml', 'image/jpeg', 'image/png']

// 地图符号选项值需要与渲染层 G2/G2Plot symbol 名称保持一致。
const mapSymbolOptions = [
  { name: t('chart.line_symbol_circle'), value: 'circle' },
  { name: t('chart.line_symbol_rect'), value: 'square' },
  { name: t('chart.line_symbol_triangle'), value: 'triangle' },
  { name: t('chart.map_symbol_pentagon'), value: 'pentagon' },
  { name: t('chart.map_symbol_hexagon'), value: 'hexagon' },
  { name: t('chart.map_symbol_octagon'), value: 'octogon' },
  { name: t('chart.line_symbol_diamond'), value: 'rhombus' },
  { name: t('commons.custom'), value: 'custom' }
]

// 校验并读取自定义符号图标文件
const onIconChange: UploadProps['onChange'] = async uploadFile => {
  const rawFile = uploadFile.raw
  let validIcon = true
  if (!acceptedFileType.includes(rawFile.type)) {
    ElMessage.error(t('chart.symbolic_error_icon'))
    validIcon = false
  }
  if (rawFile.size / 1024 / 1024 > 1) {
    ElMessage.error(t('chart.symbolic_error_size'))
    validIcon = false
  }
  if (!validIcon) {
    // 校验失败时恢复已有自定义图标预览，避免上传控件停留在无效文件状态。
    iconUpload.value?.clearFiles()
    state.fileList.splice(0)
    const customIcon = state.basicStyleForm.customIcon
    if (customIcon) {
      let file = ''
      // 图片
      if (customIcon.startsWith('data')) {
        file = customIcon
      } else {
        // svg
        file = svgStrToUrl(customIcon)
      }
      file && (state.fileList[0] = { url: file })
    }
  } else {
    if (rawFile.type === 'image/svg+xml') {
      // SVG 保留原始文本，渲染时可继续按符号路径或图片资源处理。
      state.basicStyleForm.customIcon = await rawFile.text()
      changeBasicStyle('customIcon')
    } else {
      // 位图转成 data URL 存储，避免额外依赖静态资源上传流程。
      const fileReader = new FileReader()
      fileReader.onloadend = () => {
        state.basicStyleForm.customIcon = fileReader.result as string
        changeBasicStyle('customIcon')
      }
      fileReader.readAsDataURL(rawFile)
    }
  }
}

// 切换地图符号并同步自定义图标预览
const changeMapSymbol = () => {
  const { mapSymbol, customIcon } = state.basicStyleForm
  if (mapSymbol === 'custom' && customIcon) {
    // 从内置符号切回自定义符号时恢复上一次上传的预览图。
    let file
    if (customIcon.startsWith('data')) {
      file = customIcon
    } else {
      file = svgStrToUrl(state.basicStyleForm.customIcon)
    }
    file && (state.fileList[0] = { url: file })
  }
  changeBasicStyle('mapSymbol')
}

// 判断符号地图是否需要使用动态尺寸范围
const customSymbolicMapSizeRange = computed(() => {
  let { extBubble } = JSON.parse(JSON.stringify(props.chart))
  // 绑定气泡指标后，符号尺寸由指标值映射范围控制，禁用固定尺寸滑块。
  return ['symbolic-map'].includes(props.chart.type) && extBubble?.length > 0
})
// 校验自定义符号尺寸范围并派发样式变更
const mapCustomRangeValidate = prop => {
  const { mapSymbolSizeMax = '0', mapSymbolSizeMin = '1' } = state.basicStyleForm
  let max = parseInt(String(mapSymbolSizeMax))
  let min = parseInt(String(mapSymbolSizeMin))
  // 尺寸范围使用非负最小值和正数最大值，避免渲染出不可见符号。
  state.basicStyleForm.mapSymbolSizeMin = Math.max(min, 0)
  state.basicStyleForm.mapSymbolSizeMax = Math.max(max, 1)
  if (max < min) {
    ElMessage.warning(t('chart.symbolic_error_range'))
    return
  }
  changeBasicStyle(prop)
}

// 使用图表当前配置初始化符号样式表单
const init = () => {
  const basicStyle = cloneDeep(props.chart.customAttr.basicStyle)
  if (
    basicStyle.mapSymbol === 'custom' &&
    state.basicStyleForm.customIcon !== basicStyle.customIcon
  ) {
    // 自定义图标可能是 data URL 或 SVG 文本，预览前统一转换为可访问 URL。
    let file
    if (basicStyle.customIcon?.startsWith('data')) {
      file = basicStyle.customIcon
    } else {
      file = svgStrToUrl(basicStyle.customIcon)
    }
    file && (state.fileList[0] = { url: file })
  }
  state.basicStyleForm = defaultsDeep(basicStyle, cloneDeep(DEFAULT_BASIC_STYLE)) as ChartBasicStyle
  if (!state.customColor) {
    // 首次初始化保留默认颜色索引，后续图表配置变更不覆盖用户正在编辑的颜色位置。
    state.customColor = state.basicStyleForm.colors[0]
    state.colorIndex = 0
  }
}

const debouncedInit = debounce(init, 500)
// 监听图表基础样式和坐标轴变化并延迟刷新表单
watch(
  [() => props.chart.customAttr.basicStyle, () => props.chart.xAxis, () => props.chart.yAxis],
  debouncedInit,
  { deep: true }
)
onMounted(() => {
  init()
})
</script>

<template>
  <el-form size="small" style="width: 100%">
    <div class="map-flow-style" v-if="showProperty('symbolicMapStyle')">
      <el-row style="flex: 1">
        <el-col>
          <el-form-item class="form-item" :class="'form-item-' + themes">
            <template #label>
              <span class="data-area-label">
                <span style="margin-right: 4px">{{ t('chart.symbolic_shape') }}</span>
                <el-tooltip class="item" effect="dark" placement="bottom">
                  <template v-if="state.basicStyleForm.mapSymbol === 'custom'" #content>
                    <div>{{ t('chart.symbolic_upload_hint') }}</div>
                  </template>
                  <el-icon class="hint-icon" :class="{ 'hint-icon--dark': themes === 'dark' }">
                    <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
                  </el-icon>
                </el-tooltip>
              </span>
            </template>
            <el-select
              :effect="themes"
              v-model="state.basicStyleForm.mapSymbol"
              @change="changeMapSymbol()"
            >
              <el-option
                v-for="item in mapSymbolOptions"
                :key="item.name"
                :label="item.name"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row style="flex: 1" v-if="state.basicStyleForm.mapSymbol === 'custom'">
        <el-col>
          <el-form-item class="form-item uploader" :class="'form-item-' + themes">
            <div class="avatar-uploader-container" :class="`img-area_${themes}`">
              <el-upload
                action="#"
                accept=".svg,.png,.jpeg,.jpg"
                class="avatar-uploader"
                list-type="picture-card"
                ref="iconUpload"
                :effect="themes"
                :auto-upload="false"
                :file-list="state.fileList"
                :on-change="onIconChange"
                :limit="1"
              >
                <el-icon><Plus /></el-icon>
              </el-upload>
            </div>
          </el-form-item>
        </el-col>
      </el-row>
      <div class="alpha-setting">
        <label class="alpha-label" :class="{ dark: 'dark' === themes }">
          {{ t('chart.size') }}
        </label>
        <el-row style="flex: 1">
          <el-col>
            <el-form-item class="form-item alpha-slider" :class="'form-item-' + themes">
              <el-slider
                :effect="themes"
                :min="1"
                :max="40"
                v-model="state.basicStyleForm.mapSymbolSize"
                @change="changeBasicStyle('mapSymbolSize')"
                :disabled="customSymbolicMapSizeRange"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </div>
      <div class="alpha-setting">
        <label class="alpha-label" :class="{ dark: 'dark' === themes }">
          {{ t('chart.size') }}{{ t('chart.symbolic_range') }}
        </label>
        <el-row style="flex: 1">
          <el-col :span="11">
            <el-form-item class="form-item alpha-slider" :class="'form-item-' + themes">
              <el-input
                type="number"
                :effect="themes"
                v-model="state.basicStyleForm.mapSymbolSizeMin"
                class="basic-input-number"
                :controls="false"
                @blur="mapCustomRangeValidate('mapSymbolSizeMin')"
                :disabled="!customSymbolicMapSizeRange"
              >
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="1.2">
            <span>-</span>
          </el-col>
          <el-col :span="11">
            <el-form-item class="form-item alpha-slider" :class="'form-item-' + themes">
              <el-input
                type="number"
                :effect="themes"
                v-model="state.basicStyleForm.mapSymbolSizeMax"
                class="basic-input-number"
                :controls="false"
                @blur="mapCustomRangeValidate('mapSymbolSizeMax')"
                :disabled="!customSymbolicMapSizeRange"
              >
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
      </div>
      <div v-if="state.basicStyleForm.mapSymbol !== 'custom'" class="alpha-setting">
        <label class="alpha-label" :class="{ dark: 'dark' === themes }">
          {{ t('chart.not_alpha') }}
        </label>
        <el-row style="flex: 1">
          <el-col>
            <el-form-item class="form-item alpha-slider" :class="'form-item-' + themes">
              <el-slider
                :effect="themes"
                :min="1"
                :max="10"
                v-model="state.basicStyleForm.mapSymbolOpacity"
                @change="changeBasicStyle('mapSymbolOpacity')"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </div>
      <div v-if="state.basicStyleForm.mapSymbol !== 'custom'" class="alpha-setting">
        <label class="alpha-label" :class="{ dark: 'dark' === themes }">
          {{ t('visualization.borderWidth') }}
        </label>
        <el-row style="flex: 1">
          <el-col>
            <el-form-item class="form-item alpha-slider" :class="'form-item-' + themes">
              <el-slider
                :effect="themes"
                :min="1"
                :max="5"
                v-model="state.basicStyleForm.mapSymbolStrokeWidth"
                @change="changeBasicStyle('mapSymbolStrokeWidth')"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </div>
    </div>
  </el-form>
</template>

<style scoped lang="less">
.color-picker-style {
  /* 颜色选择器浮层层级高于属性面板，避免被折叠容器遮挡。 */
  cursor: pointer;
  z-index: 1003;
}

.alpha-setting {
  /* 滑块设置项使用标签加控件的横向布局，和其它编辑器样式面板保持一致。 */
  display: flex;
  width: 100%;

  .alpha-slider {
    padding: 0 8px;
    :deep(.ed-slider__button-wrapper) {
      --ed-slider-button-wrapper-size: 36px;
      --ed-slider-button-size: 16px;
    }
  }

  .alpha-label {
    padding-right: 8px;
    font-size: 12px;
    font-style: normal;
    font-weight: 400;
    height: 32px;
    line-height: 32px;
    display: inline-flex;
    align-items: flex-start;

    min-width: 56px;

    &.dark {
      color: #a6a6a6;
    }
  }
}
.data-area-label {
  text-align: left;
  position: relative;
  width: 100%;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.avatar-uploader-container {
  :deep(.ed-upload--picture-card) {
    /* 自定义符号上传入口复用背景图上传的图片卡片视觉。 */
    background: #eff0f1;
    border: 1px dashed #dee0e3;
    border-radius: 6px;

    .ed-icon {
      color: #1f2329;
    }

    &:hover {
      .ed-icon {
        color: var(--ed-color-primary);
      }
    }
  }

  &.img-area_dark {
    :deep(.ed-upload-list__item).is-ready {
      border-color: #434343;
    }
    :deep(.ed-upload--picture-card) {
      background: #373737;
      border-color: #434343;
      .ed-icon {
        color: #ebebeb;
      }
    }
  }

  &.img-area_light {
    :deep(.ed-upload-list__item).is-ready {
      border-color: #dee0e3;
    }
  }
  :deep(.ed-upload-list__item-preview) {
    display: none !important;
  }
  :deep(.ed-upload-list__item-delete) {
    margin-left: 0 !important;
  }
  :deep(.ed-upload-list__item-status-label) {
    display: none !important;
  }
  :deep(.ed-icon--close-tip) {
    display: none !important;
  }
}
.avatar-uploader {
  width: 90px;
  height: 80px;
  overflow: hidden;
}
.avatar-uploader {
  width: 90px;
  :deep(.ed-upload) {
    width: 80px;
    height: 80px;
    line-height: 90px;
  }

  :deep(.ed-upload-list li) {
    width: 80px !important;
    height: 80px !important;
  }

  :deep(.ed-upload--picture-card) {
    background: #eff0f1;
    border: 1px dashed #dee0e3;
    border-radius: 6px;

    .ed-icon {
      color: #1f2329;
    }

    &:hover {
      .ed-icon {
        color: var(--ed-color-primary);
      }
    }
  }
}
.uploader {
  :deep(.ed-form-item__content) {
    justify-content: center;
  }
}
</style>
