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
    <el-form size="small" label-position="top" style="width: 100%; margin-bottom: 16px">
      <el-form-item class="form-item" :class="'form-item-' + themes" v-if="showWatermarkSetting">
        <el-checkbox
          size="small"
          :effect="themes"
          v-model="dvInfo.selfWatermarkStatus"
          @change="onBackgroundChange"
        >
          {{ t('visualization.watermark') }}
        </el-checkbox>
      </el-form-item>
      <el-form-item class="form-item no-margin-bottom" :class="'form-item-' + themes">
        <el-checkbox
          size="small"
          :effect="themes"
          v-model="canvasStyleData.backgroundColorSelect"
          @change="onBackgroundChange"
        >
          {{ t('chart.color') }}
        </el-checkbox>
      </el-form-item>

      <div class="indented-container">
        <div class="indented-item">
          <el-form-item class="form-item" :class="'form-item-' + themes">
            <el-color-picker
              v-model="canvasStyleData.backgroundColor"
              :effect="themes"
              :disabled="!canvasStyleData.backgroundColorSelect"
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
          v-model="canvasStyleData.backgroundImageEnable"
          @change="onBackgroundChange"
        >
          {{ t('visualization.background') }}
        </el-checkbox>
      </el-form-item>

      <div class="indented-container">
        <div
          class="indented-item"
          :class="{
            disabled: !canvasStyleData.backgroundImageEnable
          }"
        >
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
              :disabled="!canvasStyleData.backgroundImageEnable"
            >
              <el-icon><Plus /></el-icon>
            </el-upload>
            <el-row>
              <span
                style="margin-top: 2px"
                v-if="!canvasStyleData.background"
                class="image-hint"
                :class="`image-hint_${themes}`"
              >
                {{ t('visualization.pic_upload_tips2') }}
              </span>
              <el-button
                size="small"
                style="margin: 8px 0 0 -4px"
                v-if="canvasStyleData.background"
                text
                @click="goFile"
                :disabled="!canvasStyleData.backgroundImageEnable"
              >
                {{ t('visualization.re_upload') }}
              </el-button>
            </el-row>
          </div>

          <img-view-dialog v-model="state.dialogVisible" :image-url="state.dialogImageUrl" />
        </div>
      </div>
      <el-divider class="m-divider" :class="'m-divider-' + themes"></el-divider>
      <div class="indented-container">
        <div class="indented-item">
          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            :label="t('visualization.jump_dialog_background')"
          >
            <el-color-picker
              v-model="canvasStyleData.dialogBackgroundColor"
              :effect="themes"
              :trigger-width="computedBackgroundColorPickerWidth"
              is-custom
              show-alpha
              class="color-picker-style"
              :predefine="state.predefineColors"
              @change="onBackgroundChange"
            />
          </el-form-item>
          <el-form-item
            class="form-item"
            :class="'form-item-' + themes"
            :label="t('visualization.jump_dialog_button')"
          >
            <el-color-picker
              v-model="canvasStyleData.dialogButton"
              :effect="themes"
              :trigger-width="computedBackgroundColorPickerWidth"
              is-custom
              class="color-picker-style"
              :predefine="state.predefineColors"
              @change="onBackgroundChange"
            />
          </el-form-item>
        </div>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { COLOR_PANEL } from '@/views/chart/components/editor/util/chart'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { imgUrlTrans } from '@/utils/imgUtils'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { beforeUploadCheck, uploadFileResult } from '@/api/staticResource'
import { useI18n } from '@/hooks/web/useI18n'
import { ElButton, ElMessage } from 'element-plus-secondary'
import ImgViewDialog from '@/custom-component/ImgViewDialog.vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
const snapshotStore = snapshotStoreWithOut()
const { t } = useI18n()
// 背景图片文件选择器引用，重新上传按钮通过它触发系统文件选择框。
const files = ref(null)
// 背景图限制为 15MB，和上传前校验保持一致。
const maxImageSize = 15000000

const dvMainStore = dvMainStoreWithOut()
const { canvasStyleData, dvInfo } = storeToRefs(dvMainStore)

withDefaults(
  defineProps<{
    themes?: EditorTheme
  }>(),
  {
    themes: 'dark'
  }
)

// 维护画布背景配置状态
const state = reactive({
  BackgroundShowMap: {},
  checked: false,
  backgroundOrigin: {},
  // 上传组件使用 fileList 展示当前背景图，真实地址仍写在 canvasStyleData.background。
  fileList: [],
  dialogImageUrl: '',
  dialogVisible: false,
  uploadDisabled: false,
  predefineColors: COLOR_PANEL
})

// 判断是否显示水印自定义设置
const showWatermarkSetting = computed(() => {
  // 只有系统水印启用且允许面板级自定义时，才展示当前资源的水印开关。
  return (
    dvInfo.value.watermarkInfo &&
    dvInfo.value.watermarkInfo?.settingContent?.enable &&
    dvInfo.value.watermarkInfo?.settingContent?.enablePanelCustom
  )
})

// 触发背景图片文件选择器
const goFile = () => {
  files.value.click()
}

// 提示背景图片大小超过限制
const sizeMessage = () => {
  ElMessage.success(t('visualization.pic_size_error'))
}

// 重新上传背景图片
const reUpload = e => {
  const file = e.target.files[0]
  if (file.size > maxImageSize) {
    sizeMessage()
    return
  }
  uploadFileResult(file, fileUrl => {
    // 上传完成后写回画布背景地址，并刷新上传列表的可预览 URL。
    canvasStyleData.value.background = fileUrl
    state.fileList = [{ url: imgUrlTrans(canvasStyleData.value.background) }]
    onBackgroundChange()
  })
}

// 初始化背景图片上传列表
const init = () => {
  if (canvasStyleData.value.background) {
    // 后端保存的是资源地址，上传控件展示前需要转换为浏览器可访问地址。
    state.fileList.push({ url: imgUrlTrans(canvasStyleData.value.background) })
  } else {
    state.fileList = []
  }
}

// 移除当前背景图片
const handleRemove = () => {
  state.uploadDisabled = false
  canvasStyleData.value.background = null
  state.fileList = []
  onBackgroundChange()
}
// 打开背景图片预览弹窗
const handlePictureCardPreview = file => {
  state.dialogImageUrl = file.url
  state.dialogVisible = true
}
// 上传背景图片
const upload = file => {
  uploadFileResult(file.file, fileUrl => {
    // el-upload 自定义上传入口只负责更新背景地址，快照由统一变更方法记录。
    canvasStyleData.value.background = fileUrl
    onBackgroundChange()
  })
}

// 记录背景配置变更快照
const onBackgroundChange = () => {
  snapshotStore.recordSnapshotCache('onBackgroundChange')
}

// 背景设置表单实例，供后续扩展校验或滚动定位使用。
const bgForm = ref()

// 背景色选择器在侧边栏内使用固定宽度，避免随文本长度跳动。
const computedBackgroundColorPickerWidth = 50

onMounted(() => {
  init()
})

// 监听背景图片地址变化，兼容外部主题切换或撤销操作导致的背景回填。
watch(
  () => canvasStyleData.value.background,
  () => {
    init()
  }
)
</script>

<style scoped lang="less">
:deep(.ed-form-item) {
  /* 背景面板使用纵向表单布局，控件之间保持固定间距。 */
  display: block;
  margin-bottom: 16px;
}
.avatar-uploader-container {
  :deep(.ed-upload--picture-card) {
    /* 上传入口使用虚线边框，和 Element Plus 图片卡片态保持一致。 */
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

.ed-select-dropdown__item {
  height: 100px !important;
  text-align: center;
  padding: 0px 5px;
}

.ed-select-dropdown__item.selected::after {
  display: none;
}

.indented-container {
  /* 子配置统一缩进，突出它们依赖上方开关启用。 */
  width: 100%;
  padding-left: 22px;
  margin-top: 8px;

  .indented-item {
    width: 100%;
    display: flex;

    .fill {
      flex: 1;
    }

    &.disabled {
      /* 禁用态阻止图片上传交互，同时保留当前背景预览。 */
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

.image-hint {
  color: #8f959e;
  size: 14px;
  line-height: 22px;
  font-weight: 400;
  margin-top: -6px;
  &.image-hint_dark {
    color: #757575;
  }
}

.m-divider {
  border-color: rgba(31, 35, 41, 0.15);
  margin: 8px 0 8px;
}

.m-divider-dark {
  border-color: rgba(233, 236, 241, 0.15) !important;
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
}
</style>
