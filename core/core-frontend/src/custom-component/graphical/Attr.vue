<script setup lang="ts">
import CommonAttr from '@/custom-component/common/CommonAttr.vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'

import { storeToRefs } from 'pinia'
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { imgUrlTrans } from '@/utils/imgUtils'
import eventBus from '@/utils/eventBus'

// 图形组件属性面板接收编辑器主题
withDefaults(
  defineProps<{
    themes?: EditorTheme
  }>(),
  {
    themes: 'dark'
  }
)

// 大屏主状态用于读取当前图形组件
const dvMainStore = dvMainStoreWithOut()

// 当前正在编辑的图形组件
const { curComponent } = storeToRefs(dvMainStore)

// 上传组件展示的文件列表
const fileList = ref([])

// 隐藏文件选择器引用
const files = ref(null)

// 打开文件选择器
const goFile = () => {
  files.value.click()
}

// 根据当前图形图片地址初始化上传文件列表
const init = () => {
  if (curComponent.value.propValue.url) {
    fileList.value = [{ url: imgUrlTrans(curComponent.value.propValue.url) }]
  } else {
    fileList.value = []
  }
}

// 当前图形图片地址变化后刷新上传列表
watch(
  () => curComponent.value.propValue.url,
  () => {
    init()
  }
)

onMounted(() => {
  init()
  eventBus.on('uploadImg', goFile)
})
onBeforeUnmount(() => {
  eventBus.off('uploadImg', goFile)
})
</script>

<template>
  <div class="attr-list crest-collapse-style">
    <CommonAttr
      :themes="themes"
      :element="curComponent"
      :background-color-picker-width="197"
      :background-border-select-width="197"
    >
    </CommonAttr>
  </div>
</template>

<style lang="less" scoped>
.crest-collapse-style {
  :deep(.ed-collapse-item__header) {
    height: 36px !important;
    line-height: 36px !important;
    font-size: 12px !important;
    padding: 0 !important;
    font-weight: 500 !important;

    .ed-collapse-item__arrow {
      margin: 0 6px 0 8px;
    }
  }

  :deep(.ed-collapse-item__content) {
    padding: 16px 8px 0;
  }
  :deep(.ed-form-item) {
    display: block;
    margin-bottom: 8px;
  }
  :deep(.ed-form-item__label) {
    justify-content: flex-start;
  }
}

.disabled :deep(.el-upload--picture-card) {
  display: none;
}

.avatar-uploader :deep(.ed-upload) {
  width: 80px;
  height: 80px;
  line-height: 90px;
}

.avatar-uploader :deep(.ed-upload-list li) {
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
.img-area {
  height: 80px;
  width: 80px;
  margin-top: 10px;
  overflow: hidden;

  &.img-area_dark {
    :deep(.ed-upload-list__item).is-success {
      border-color: #434343;
    }
    :deep(.ed-upload--picture-card) {
      background: #373737;
      border-color: #434343;
      .ed-icon {
        color: #ebebeb;
      }
      &:hover {
        .ed-icon {
          color: var(--ed-color-primary);
        }
      }
    }
  }

  &.img-area_light {
    :deep(.ed-upload-list__item).is-success {
      border-color: #dee0e3;
    }
  }
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

.re-update-span {
  cursor: pointer;
  color: var(--ed-color-primary);
  size: 14px;
  line-height: 22px;
  font-weight: 400;
}
</style>
