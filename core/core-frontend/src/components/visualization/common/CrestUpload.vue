<template>
  <div class="avatar-uploader-container" :class="`img-area_${themes}`">
    <el-upload
      action=""
      :effect="themes"
      accept=".jpeg,.jpg,.png,.gif,.svg"
      class="avatar-uploader"
      list-type="picture-card"
      :class="{ disabled: state.uploadDisabled }"
      :on-preview="handlePictureCardPreview"
      :on-remove="handleRemove"
      :http-request="upload"
      :before-upload="beforeUploadCheck"
      :file-list="state.fileList"
    >
      <el-icon><Plus /></el-icon>
    </el-upload>

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
      v-on:change="reUpload"
    />

    <img-view-dialog v-model="state.dialogVisible" :image-url="state.dialogImageUrl" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, toRefs, watch } from 'vue'
import { imgUrlTrans } from '@/utils/imgUtils'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { beforeUploadCheck, uploadFileResult } from '@/api/staticResource'
import { ElMessage } from 'element-plus-secondary'
import ImgViewDialog from '@/custom-component/ImgViewDialog.vue'
const snapshotStore = snapshotStoreWithOut()
// 定义图片变更事件
const emits = defineEmits(['onImgChange'])
// 文件选择器引用
const files = ref(null)
const maxImageSize = 15000000

// 接收图片地址和主题配置
const props = defineProps({
  imgUrl: {
    type: String
  },
  themes: {
    type: String,
    default: 'dark'
  }
})

const { themes, imgUrl } = toRefs(props)
// 组件内部图片地址
const imgUrlInner = ref(null)

// 维护上传列表和预览弹窗状态
const state = reactive({
  fileList: [],
  dialogImageUrl: '',
  dialogVisible: false,
  uploadDisabled: false
})

// 初始化上传列表
const init = () => {
  imgUrlInner.value = imgUrl.value
  if (imgUrlInner.value) {
    state.fileList.push({ url: imgUrlTrans(imgUrlInner.value) })
  } else {
    state.fileList = []
  }
}

// 移除当前图片
const handleRemove = () => {
  state.uploadDisabled = false
  imgUrlInner.value = null
  state.fileList = []
  emits('onImgChange')
}
// 打开图片预览弹窗
const handlePictureCardPreview = file => {
  state.dialogImageUrl = file.url
  state.dialogVisible = true
}
// 上传图片并通知父级
const upload = file => {
  uploadFileResult(file.file, fileUrl => {
    snapshotStore.recordSnapshotCache('upload')
    imgUrlInner.value = fileUrl
    emits('onImgChange', fileUrl)
  })
}

// 重新上传图片
const reUpload = e => {
  const file = e.target.files[0]
  if (file.size > maxImageSize) {
    sizeMessage()
    return
  }
  uploadFileResult(file, fileUrl => {
    snapshotStore.recordSnapshotCache('uploadFileResult')
    imgUrlInner.value = fileUrl
    emits('onImgChange', fileUrl)
  })
}

// 提示图片大小超过限制
const sizeMessage = () => {
  ElMessage.error('图片大小不能超过15M')
}

onMounted(() => {
  init()
})

// 监听外部图片地址变化
watch(
  () => imgUrl.value,
  () => {
    init()
  }
)
</script>

<style scoped lang="less">
.avatar-uploader-container {
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

.tips-area {
  color: #909399;
  font-size: 8px;
  margin-left: 10px;
  line-height: 40px;
}
.ed-card-template {
  width: 100%;
  height: 100%;
}

.main-col {
  background-size: 100% 100% !important;
  padding-left: 10px;
  margin-top: 10px;
  height: 230px;
  overflow-y: auto;
  flex-direction: row;
}

.root-class {
  margin: 15px 0px 5px;
  text-align: center;
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

:deep(.ed-upload--picture-card) {
  background: none;
}
:deep(.ed-upload-list__item) {
  background: none;
}

.disabled :deep(.ed-upload--picture-card) {
  display: none;
}

.shape-item {
  padding: 6px;
  border: none;
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.image-hint {
  color: #8f959e;
  size: 14px;
  line-height: 22px;
  font-weight: 400;
  margin-top: 2px;
}

.re-update-span {
  cursor: pointer;
  color: var(--ed-color-primary);
  size: 14px;
  line-height: 22px;
  font-weight: 400;
}
</style>
