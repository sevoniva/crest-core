<template>
  <div style="width: 100%" ref="bgForm">
    <input
      id="input"
      ref="files"
      type="file"
      accept=".jpeg,.jpg,.png,.gif,.svg"
      hidden
      @click="
        e => {
          e.target.value = ''
        }
      "
      @change="reUpload"
    />
    <el-form size="small" label-position="top" style="width: 100%">
      <el-row :gutter="8">
        <el-col :span="24">
          <!-- 内边距支持统一、轴向和逐边三种简写模式，脚本会在提交前展开为四边值。 -->
          <el-form-item
            :label="t('visualization.inner_padding')"
            class="form-item w100"
            :class="'form-item-' + themes"
          ></el-form-item>
          <el-form-item
            :label="t('visualization.inner_padding_shorthand_mode')"
            class="form-item w100"
            :class="'form-item-' + themes"
          >
            <div style="display: flex; align-items: center; width: 100%; margin-bottom: 8px">
              <el-select
                :effect="themes"
                v-model="state.commonBackground.innerPadding.mode"
                size="small"
                style="width: 100%"
                @change="onBackgroundChange"
              >
                <el-option
                  class="custom-style-option"
                  v-for="option in paddingModes"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </div>
            <el-row :gutter="8">
              <el-col :span="12">
                <div style="display: flex; align-items: center; margin-bottom: 8px">
                  <span style="width: 30%; padding-right: 8px">{{
                    t('visualization.edge_top')
                  }}</span>
                  <el-input-number
                    style="width: 70%"
                    :effect="themes"
                    controls-position="right"
                    :min="0"
                    :max="100"
                    v-model="state.commonBackground.innerPadding.top"
                    @change="onBackgroundChange"
                  />
                </div>
                <div style="display: flex; align-items: center">
                  <span style="width: 30%; padding-right: 8px">{{
                    t('visualization.edge_left')
                  }}</span>
                  <el-input-number
                    style="width: 70%"
                    :effect="themes"
                    controls-position="right"
                    :min="0"
                    :max="100"
                    v-model="state.commonBackground.innerPadding.left"
                    :disabled="state.commonBackground.innerPadding.mode === ShorthandMode.Uniform"
                    @change="onBackgroundChange"
                  />
                </div>
              </el-col>
              <el-col :span="12">
                <div style="display: flex; align-items: center; margin-bottom: 8px">
                  <span style="width: 30%; padding-right: 8px">{{
                    t('visualization.edge_bottom')
                  }}</span>
                  <el-input-number
                    style="width: 70%"
                    :effect="themes"
                    :disabled="state.commonBackground.innerPadding.mode !== ShorthandMode.PerEdge"
                    controls-position="right"
                    :min="0"
                    :max="100"
                    v-model="state.commonBackground.innerPadding.bottom"
                    @change="onBackgroundChange"
                  />
                </div>
                <div style="display: flex; align-items: center">
                  <span style="width: 30%; padding-right: 8px">{{
                    t('visualization.edge_right')
                  }}</span>
                  <el-input-number
                    style="width: 70%"
                    :effect="themes"
                    :disabled="state.commonBackground.innerPadding.mode !== ShorthandMode.PerEdge"
                    controls-position="right"
                    :min="0"
                    :max="100"
                    v-model="state.commonBackground.innerPadding.right"
                    @change="onBackgroundChange"
                  />
                </div>
              </el-col>
            </el-row>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="8">
        <el-col :span="24">
          <!-- 圆角同样支持简写模式，保证画布背景和组件背景使用一致的配置结构。 -->
          <el-form-item
            :label="t('visualization.board_radio')"
            class="form-item w100"
            :class="'form-item-' + themes"
          >
          </el-form-item>
          <el-form-item
            :label="t('visualization.corner_shorthand_mode')"
            class="form-item w100"
            :class="'form-item-' + themes"
          >
            <div style="display: flex; align-items: center; width: 100%; margin-bottom: 8px">
              <el-select
                :effect="themes"
                v-model="state.commonBackground.borderRadius.mode"
                size="small"
                @change="onBackgroundChange"
              >
                <el-option
                  class="custom-style-option"
                  v-for="option in cornerModes"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </div>
            <el-row :gutter="8">
              <el-col :span="12">
                <div style="display: flex; align-items: center; margin-bottom: 8px">
                  <span style="width: 30%; padding-right: 6px">{{
                    t('visualization.corner_top_left')
                  }}</span>
                  <el-input-number
                    style="width: 70%"
                    :effect="themes"
                    controls-position="right"
                    :min="0"
                    :max="100"
                    v-model="state.commonBackground.borderRadius.topLeft"
                    @change="onBackgroundChange"
                  />
                </div>
                <div style="display: flex; align-items: center">
                  <span style="width: 30%; padding-right: 6px">{{
                    t('visualization.corner_bottom_left')
                  }}</span>
                  <el-input-number
                    style="width: 70%"
                    :effect="themes"
                    controls-position="right"
                    :min="0"
                    :max="100"
                    v-model="state.commonBackground.borderRadius.bottomLeft"
                    :disabled="state.commonBackground.borderRadius.mode === ShorthandMode.Uniform"
                    @change="onBackgroundChange"
                  />
                </div>
              </el-col>
              <el-col :span="12">
                <div style="display: flex; align-items: center; margin-bottom: 8px">
                  <span style="width: 30%; padding-right: 6px">{{
                    t('visualization.corner_top_right')
                  }}</span>
                  <el-input-number
                    style="width: 70%"
                    :effect="themes"
                    :disabled="state.commonBackground.borderRadius.mode !== ShorthandMode.PerEdge"
                    controls-position="right"
                    :min="0"
                    :max="100"
                    v-model="state.commonBackground.borderRadius.topRight"
                    @change="onBackgroundChange"
                  />
                </div>
                <div style="display: flex; align-items: center">
                  <span style="width: 30%; padding-right: 6px">{{
                    t('visualization.corner_bottom_right')
                  }}</span>
                  <el-input-number
                    style="width: 70%"
                    :effect="themes"
                    :disabled="state.commonBackground.borderRadius.mode !== ShorthandMode.PerEdge"
                    controls-position="right"
                    :min="0"
                    :max="100"
                    v-model="state.commonBackground.borderRadius.bottomRight"
                    @change="onBackgroundChange"
                  />
                </div>
              </el-col>
            </el-row>
          </el-form-item>
        </el-col>
      </el-row>
      <template v-if="editPosition === 'canvas'">
        <!-- 毛玻璃只在画布编辑位置开放，避免组件背景在普通卡片中过度消耗渲染性能。 -->
        <el-form-item class="form-item no-margin-bottom" :class="'form-item-' + themes">
          <el-checkbox
            size="small"
            :effect="themes"
            v-model="state.commonBackground.backdropFilterEnable"
            @change="onBackgroundChange"
          >
            {{ $t('chart.backdrop_blur') }}
          </el-checkbox>
        </el-form-item>
        <div class="indented-container">
          <div class="indented-item">
            <el-form-item class="form-item" :class="'form-item-' + themes">
              <el-input-number
                style="width: 100%"
                :effect="themes"
                controls-position="right"
                :min="0"
                :max="30"
                :disabled="!state.commonBackground.backdropFilterEnable"
                v-model="state.commonBackground.backdropFilter"
                @change="onBackgroundChange"
              />
            </el-form-item>
          </div>
        </div>
      </template>

      <el-form-item class="form-item no-margin-bottom" :class="'form-item-' + themes">
        <el-checkbox
          size="small"
          :effect="themes"
          v-model="state.commonBackground.backgroundColorSelect"
          @change="onBackgroundChange"
        >
          {{ $t('chart.color') }}
        </el-checkbox>
      </el-form-item>

      <div class="indented-container">
        <div class="indented-item">
          <!-- 背景色可独立开关，关闭时保留已选颜色，便于用户再次启用。 -->
          <el-form-item class="form-item" :class="'form-item-' + themes">
            <el-color-picker
              v-if="state.commonBackground.backgroundColor"
              v-model="state.commonBackground.backgroundColor"
              :effect="themes"
              :disabled="!state.commonBackground.backgroundColorSelect"
              :trigger-width="computedBackgroundColorPickerWidth"
              is-custom
              show-alpha
              class="color-picker-style"
              :predefine="state.predefineColors"
              @change="onBackgroundChange"
            />
          </el-form-item>
        </div>
      </div>

      <el-form-item class="form-item no-margin-bottom" :class="'form-item-' + themes">
        <el-checkbox
          size="small"
          :effect="themes"
          v-model="state.commonBackground.backgroundImageEnable"
          @change="onBackgroundChange"
        >
          {{ t('visualization.background') }}
        </el-checkbox>
      </el-form-item>

      <div class="indented-container">
        <div class="indented-item">
          <el-form-item class="form-item margin-bottom-8" :class="'form-item-' + themes">
            <el-radio-group
              :effect="themes"
              :disabled="!state.commonBackground.backgroundImageEnable"
              v-model="state.commonBackground.backgroundType"
              @change="onBackgroundChange"
            >
              <el-radio :effect="themes" label="outerImage">{{
                t('visualization.photo')
              }}</el-radio>
              <el-radio :effect="themes" label="innerImage">{{
                t('visualization.board')
              }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </div>
        <div class="indented-item" v-if="state.commonBackground.backgroundType === 'innerImage'">
          <!-- 内置边框使用资源库预设，并允许单独调整边框着色。 -->
          <el-form-item class="form-item" :class="'form-item-' + themes">
            <el-color-picker
              v-model="state.commonBackground.innerImageColor"
              :disabled="!state.commonBackground.backgroundImageEnable"
              :effect="themes"
              :title="t('visualization.border_color_setting')"
              is-custom
              show-alpha
              class="color-picker-style"
              :predefine="state.predefineColors"
              @change="onBackgroundChange"
            />
          </el-form-item>
          <el-form-item
            class="form-item fill"
            style="padding-left: 8px"
            :class="'form-item-' + themes"
          >
            <el-select
              :style="{ width: computedBackgroundBorderSelectWidth + 'px' }"
              v-model="state.commonBackground.innerImage"
              popper-class="board-select"
              :effect="themes"
              :disabled="!state.commonBackground.backgroundImageEnable"
              placeholder="选择边框..."
              @change="onBackgroundChange"
            >
              <template v-if="state.commonBackground.innerImage" #prefix>
                <border-option-prefix
                  inner-image-color="state.commonBackground.innerImageColor"
                  :url="state.commonBackground.innerImage"
                ></border-option-prefix>
              </template>
              <el-option
                v-for="(item, index) in state.BackgroundShowMap['default']"
                :key="index"
                :label="item.name"
                :value="item.url"
              >
                <board-item
                  :themes="themes"
                  :active="item.url === state.commonBackground.innerImage"
                  :inner-image-color="state.commonBackground.innerImageColor"
                  :item="item"
                ></board-item>
              </el-option>
            </el-select>
          </el-form-item>
        </div>
        <div
          class="indented-item"
          v-if="state.commonBackground.backgroundType === 'outerImage'"
          :class="{
            disabled: !state.commonBackground.backgroundImageEnable || state.uploadDisabled
          }"
        >
          <!-- 外部图片走上传组件，文件列表只保存当前背景图的预览项。 -->
          <div class="avatar-uploader-container" :class="`img-area_${themes}`">
            <el-upload
              action=""
              :effect="themes"
              accept=".jpeg,.jpg,.png,.gif,.svg"
              class="avatar-uploader"
              list-type="picture-card"
              :on-preview="handlePictureCardPreview"
              :on-remove="handleRemove"
              :before-upload="beforeUploadCheck"
              :http-request="upload"
              :file-list="state.fileList"
              :disabled="!state.commonBackground.backgroundImageEnable"
            >
              <el-icon><Plus /></el-icon>
            </el-upload>
            <el-row>
              <span
                style="margin-top: 2px"
                v-if="!state.commonBackground.outerImage"
                class="image-hint"
                :class="`image-hint_${themes}`"
              >
                {{ t('visualization.panel_background_image_tips') }}
              </span>

              <el-button
                size="small"
                style="margin: 8px 0 0 -4px"
                v-if="state.commonBackground.outerImage"
                text
                @click="goFile"
                :disabled="!state.commonBackground.backgroundImageEnable"
              >
                {{ t('visualization.reUpload') }}
              </el-button>
            </el-row>
          </div>

          <img-view-dialog v-model="state.dialogVisible" :image-url="state.dialogImageUrl" />
        </div>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { queryVisualizationBackground } from '@/api/visualization/visualizationBackground'
import { COLOR_PANEL } from '@/views/chart/components/editor/util/chart'
import { computed, effect, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { imgUrlTrans } from '@/utils/imgUtils'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { beforeUploadCheck, uploadFileResult } from '@/api/staticResource'
import { useI18n } from '@/hooks/web/useI18n'
import { deepCopy } from '@/utils/utils'
import elementResizeDetectorMaker from 'element-resize-detector'
import { ElMessage } from 'element-plus-secondary'
import BoardItem from '@/components/visualization/component-background/BoardItem.vue'
import ImgViewDialog from '@/custom-component/ImgViewDialog.vue'
import BorderOptionPrefix from '@/components/visualization/component-background/BorderOptionPrefix.vue'
const snapshotStore = snapshotStoreWithOut()
const { t } = useI18n()
// 通用背景面板负责组件/画布背景色、背景图、内边距、圆角和毛玻璃配置。
const emits = defineEmits(['onBackgroundChange'])
// 隐藏文件选择器用于重新上传背景图，避免额外展示原生 input。
const files = ref(null)
// 背景图上传限制为 15MB，避免大图影响画布加载性能。
const maxImageSize = 15000000

// 接收背景配置、主题和控件宽度，宽度会随面板容器动态调整。
const props = withDefaults(
  defineProps<{
    componentPosition?: string
    editPosition?: string
    themes?: EditorTheme
    commonBackgroundPop: any
    backgroundColorPickerWidth?: number
    backgroundBorderSelectWidth?: number
  }>(),
  {
    themes: 'dark',
    componentPosition: 'dashboard',
    editPosition: 'canvas',
    backgroundColorPickerWidth: 50,
    backgroundBorderSelectWidth: 108
  }
)

import { State } from '@/components/visualization/component-background/Types'
import { ShorthandMode } from '@/Types'

// 维护背景配置表单、图片上传、预览弹窗和内置边框资源状态。
const state = reactive<State>({
  commonBackground: {
    innerPadding: {},
    borderRadius: {}
  },
  BackgroundShowMap: {},
  checked: false,
  backgroundOrigin: {},
  fileList: [],
  dialogImageUrl: '',
  dialogVisible: false,
  uploadDisabled: false,
  panel: null,
  predefineColors: COLOR_PANEL
})

// 内边距简写模式来自统一枚举，保证画布和组件背景配置语义一致。
const paddingModes = Object.values(ShorthandMode).map(item => ({
  label: t(`visualization.inner_padding_shorthand_mode_${item}`),
  value: item
})) as { label: string; value: ShorthandMode }[]

// 圆角简写模式与内边距共用枚举，但展示文案按圆角语义翻译。
const cornerModes = Object.values(ShorthandMode).map(item => ({
  label: t(`visualization.corner_shorthand_mode_${item}`),
  value: item
})) as { label: string; value: ShorthandMode }[]

// 触发隐藏文件选择器，复用浏览器原生文件选择能力。
const goFile = () => {
  files.value.click()
}

// 提示上传图片超过大小限制。
const sizeMessage = () => {
  ElMessage.error('图片大小不能超过15M')
}

// 重新上传背景图片并同步背景配置，成功后刷新文件列表预览。
const reUpload = e => {
  const file = e.target.files[0]
  if (file.size > maxImageSize) {
    sizeMessage()
    return
  }
  uploadFileResult(file, fileUrl => {
    state.commonBackground.outerImage = fileUrl
    state.fileList = [{ name: 'background', url: imgUrlTrans(state.commonBackground.outerImage) }]
    onBackgroundChange()
  })
}

// 查询系统内置背景展示配置，用于内置边框选择器展示。
const queryBackground = () => {
  queryVisualizationBackground().then(response => {
    state.BackgroundShowMap = response.data
  })
}

// 初始化背景表单并兼容旧版数字简写值。
const init = () => {
  const commonBackgroundPop = deepCopy(props.commonBackgroundPop)
  const innerPadding = commonBackgroundPop.innerPadding
  if (typeof innerPadding === 'number') {
    // 旧版本 innerPadding 是单个数字，新版转换为统一简写结构。
    commonBackgroundPop.innerPadding = {
      mode: ShorthandMode.Uniform,
      top: innerPadding,
      right: innerPadding,
      bottom: innerPadding,
      left: innerPadding
    }
  }
  const borderRadius = commonBackgroundPop.borderRadius
  if (typeof borderRadius === 'number') {
    // 旧版本 borderRadius 是单个数字，新版转换为统一简写结构。
    commonBackgroundPop.borderRadius = {
      mode: ShorthandMode.Uniform,
      topLeft: borderRadius,
      topRight: borderRadius,
      bottomLeft: borderRadius,
      bottomRight: borderRadius
    }
  }
  state.commonBackground = commonBackgroundPop
  updateInnerPadding()
  updateBorderRadius()
  if (state.commonBackground.outerImage) {
    // 已有背景图需要转换为可访问地址后回填上传组件文件列表。
    state.fileList = [{ name: 'background', url: imgUrlTrans(state.commonBackground.outerImage) }]
  } else {
    state.fileList = []
  }
}
queryBackground()
// 记录背景样式快照，移动端和桌面端共用撤销栈。
const commitStyle = () => {
  snapshotStore.recordSnapshotCacheToMobile('commonBackground')
}

// 移除当前背景图片并同步父级配置。
const handleRemove = () => {
  state.uploadDisabled = false
  state.commonBackground.outerImage = null
  state.fileList = []
  onBackgroundChange()
  commitStyle()
}
// 打开背景图片预览弹窗。
const handlePictureCardPreview = file => {
  state.dialogImageUrl = file.url
  state.dialogVisible = true
}
// 上传背景图片并写入配置，上传组件会负责调用该自定义请求。
const upload = file => {
  uploadFileResult(file.file, fileUrl => {
    state.commonBackground.outerImage = fileUrl
    onBackgroundChange()
  })
}

// 根据简写模式展开内边距，Uniform 同步四边，Axis 同步水平和垂直边。
const updateInnerPadding = () => {
  if (state.commonBackground.innerPadding.mode === ShorthandMode.Uniform) {
    state.commonBackground.innerPadding.left = state.commonBackground.innerPadding.top
    state.commonBackground.innerPadding.right = state.commonBackground.innerPadding.top
    state.commonBackground.innerPadding.bottom = state.commonBackground.innerPadding.top
  } else if (state.commonBackground.innerPadding.mode === ShorthandMode.Axis) {
    state.commonBackground.innerPadding.right = state.commonBackground.innerPadding.left
    state.commonBackground.innerPadding.bottom = state.commonBackground.innerPadding.top
  }
}

// 根据简写模式展开圆角，Uniform 同步四角，Axis 同步对角。
const updateBorderRadius = () => {
  if (state.commonBackground.borderRadius.mode === ShorthandMode.Uniform) {
    state.commonBackground.borderRadius.topRight = state.commonBackground.borderRadius.topLeft
    state.commonBackground.borderRadius.bottomLeft = state.commonBackground.borderRadius.topLeft
    state.commonBackground.borderRadius.bottomRight = state.commonBackground.borderRadius.topLeft
  } else if (state.commonBackground.borderRadius.mode === ShorthandMode.Axis) {
    state.commonBackground.borderRadius.bottomRight = state.commonBackground.borderRadius.topLeft
    state.commonBackground.borderRadius.topRight = state.commonBackground.borderRadius.bottomLeft
  }
}

// 归一化背景配置后通知父级，父级负责持久化并触发渲染。
const onBackgroundChange = () => {
  updateInnerPadding()
  updateBorderRadius()
  emits('onBackgroundChange', state.commonBackground)
}

// 背景表单容器实例用于监听宽度变化。
const bgForm = ref()
// 背景表单容器宽度会影响颜色选择器和边框选择器尺寸。
const containerWidth = ref()

// 根据容器宽度计算颜色选择器宽度，小面板使用紧凑宽度。
const computedBackgroundColorPickerWidth = computed(() => {
  if (containerWidth.value <= 240) {
    return 50
  } else {
    return props.backgroundColorPickerWidth
  }
})
// 根据容器宽度计算边框选择器宽度，避免窄面板中控件溢出。
const computedBackgroundBorderSelectWidth = computed(() => {
  if (containerWidth.value <= 240) {
    return 108
  } else {
    return props.backgroundBorderSelectWidth
  }
})

onMounted(() => {
  init()
  const erd = elementResizeDetectorMaker()
  containerWidth.value = bgForm.value?.offsetWidth
  erd.listenTo(bgForm.value, () => {
    nextTick(() => {
      // resize detector 回调中延后一帧读取宽度，避开布局尚未稳定的瞬间。
      containerWidth.value = bgForm.value?.offsetWidth
    })
  })
})

// 外部背景配置变化时刷新表单，支持主题切换和批量样式操作。
watch(
  () => props.commonBackgroundPop,
  () => {
    init()
  }
)
</script>

<style scoped lang="less">
:deep(.ed-form-item) {
  display: block;
  margin-bottom: 16px;
}
.avatar-uploader-container {
  // 上传区域在深浅主题下复用同一尺寸，只切换边框和图标颜色。
  margin-bottom: 16px;
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

.shape-item {
  padding: 6px;
  border: none;
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.board-select .ed-select-dropdown__item {
  height: 100px !important;
  text-align: center;
  padding: 0px 5px;
}

.board-select .ed-select-dropdown__item.selected::after {
  display: none;
}

.indented-container {
  // 子配置统一缩进，和开关项形成父子层级关系。
  margin-top: 8px;
  width: 100%;
  padding-left: 22px;

  .indented-item {
    width: 100%;
    display: flex;

    .fill {
      flex: 1;
    }

    &.disabled {
      cursor: not-allowed;
      color: #8f959e;

      :deep(.avatar-uploader) {
        width: 90px;
        pointer-events: none;
      }

      :deep(.ed-upload--picture-card) {
        cursor: not-allowed;
      }

      .img-area_dark {
        :deep(.ed-upload--picture-card) {
          .ed-icon {
            color: #5f5f5f;
          }
        }
      }
      .img-area_light {
        :deep(.ed-upload--picture-card) {
          .ed-icon {
            color: #bbbfc4;
          }
        }
      }

      &:hover {
        .ed-icon {
          color: #8f959e;
        }
      }
    }
  }
}
.form-item {
  &.margin-bottom-8 {
    margin-bottom: 8px !important;
  }
  &.no-margin-bottom {
    margin-bottom: 0 !important;
  }
}

.re-update-span {
  cursor: pointer;
  color: var(--ed-color-primary);
  size: 14px;
  line-height: 22px;
  font-weight: 400;
}

.image-hint {
  color: #8f959e;
  size: 14px;
  line-height: 22px;
  font-weight: 400;
  margin-top: 2px;
  &.image-hint_dark {
    color: #757575;
  }
}
</style>

<style lang="less">
.board-select {
  min-width: 50px !important;
  width: 304px;
  .ed-scrollbar__view {
    display: grid !important;
    grid-template-columns: repeat(3, 1fr) !important;
  }
  .ed-select-dropdown__item.hover {
    background-color: rgba(0, 0, 0, 0) !important;
  }
  .ed-select-dropdown__item.selected {
    background-color: rgba(0, 0, 0, 0) !important;
  }

  .is-selected::after {
    display: none;
  }
}
</style>
