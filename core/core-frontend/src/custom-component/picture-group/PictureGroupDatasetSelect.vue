<script setup lang="ts">
import { nextTick, PropType, reactive, ref, toRefs } from 'vue'
import { BASE_VIEW_CONFIG } from '@/views/chart/components/editor/util/chart'
import DatasetSelect from '@/views/chart/components/editor/dataset-select/DatasetSelect.vue'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { useEmitt } from '@/hooks/web/useEmitt'
import { getFieldByDQ } from '@/api/chart'
import { ElMessage } from 'element-plus-secondary'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { useI18n } from '@/hooks/web/useI18n'
import { useEmbedded } from '@/store/modules/embedded'
import { useAppStoreWithOut } from '@/store/modules/app'
import { useRouter } from 'vue-router_2'
import { useCache } from '@/hooks/web/useCache'
// 快照仓库用于记录图片组数据集切换后的撤销点
const snapshotStore = snapshotStoreWithOut()
// 大屏主状态用于读取页面保存状态
const dvMainStore = dvMainStoreWithOut()
// 数据集选择器文案函数
const { t } = useI18n()
// 嵌入式状态用于决定数据集创建入口
const embeddedStore = useEmbedded()
// 应用状态用于判断 iframe 场景
const appStore = useAppStoreWithOut()
// 路由实例用于解析数据集创建地址
const router = useRouter()
// 本地缓存用于读取后台打开方式
const { wsCache } = useCache('localStorage')

// 图片组数据集选择器属性
const props = defineProps({
  themes: {
    type: String as PropType<EditorTheme>,
    default: 'dark'
  },
  view: {
    type: Object as PropType<ChartObj>,
    required: false,
    default() {
      return { ...BASE_VIEW_CONFIG }
    }
  }
})
// 当前图片组视图配置引用
const { view } = toRefs(props)
// 透传给数据集选择器的辅助状态对象
const state = reactive({})

// 数据集变更后重新拉取字段并触发图表计算
const onDatasetUpdate = () => {
  nextTick(() => {
    if (view.value.tableId && view.value.id) {
      getFieldByDQ(view.value.tableId, view.value.id, { type: 'table-info' })
        .then(res => {
          view.value.xAxis = []
          res.quotaList.pop()
          view.value.xAxis.push(...res.dimensionList, ...res.quotaList)
          const viewTarget = view.value
          useEmitt().emitter.emit('calcData-' + viewTarget.id, viewTarget)
          snapshotStore.recordSnapshotCache('calc', view.value.id)
        })
        .catch(() => {
          // 字段刷新失败时保持当前轴配置不变
        })
    } else {
      view.value.xAxis = []
    }
  })
}

// 打开数据集创建页面，未保存大屏时先提示保存
const addDsWindow = () => {
  if (!dvMainStore.dvInfo.id) {
    ElMessage.warning(t('visualization.save_page_tips'))
    return
  }
  const path =
    embeddedStore.getToken && appStore.getIsIframe ? 'dataset-embedded-form' : '/dataset-form'
  let routeData = router.resolve(path)
  const openType = wsCache.get('open-backend') === '1' ? '_self' : '_blank'
  const newWindow = window.open(routeData.href, openType)
  initOpenHandler(newWindow)
}

// 外部宿主打开窗口处理器
const openHandler = ref(null)
// 嵌入式宿主存在时委托宿主处理新窗口
const initOpenHandler = newWindow => {
  if (openHandler?.value) {
    const pm = {
      methodName: 'initOpenHandler',
      args: newWindow
    }
    openHandler.value.invokeMethod(pm)
  }
}
</script>

<template>
  <dataset-select
    ref="datasetSelector"
    v-model="view.tableId"
    style="flex: 1"
    :view-id="view.id"
    :themes="themes"
    :disabled="!view.senior.threshold.enable"
    @on-dataset-change="onDatasetUpdate"
    @add-ds-window="addDsWindow"
    :state-obj="state"
  />
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

.pic-adaptor {
  margin: 8px 0 16px 0;
  :deep(.ed-form-item__content) {
    margin-top: 8px !important;
  }
}

.form-item-dark {
  .ed-radio {
    margin-right: 4px !important;
  }
}

.drag-data {
  padding-top: 8px;
  padding-bottom: 16px;

  .tree-btn {
    width: 100%;
    margin-top: 8px;
    background: #fff;
    height: 32px;
    border-radius: 6px;
    border: 1px solid #dcdfe6;
    display: flex;
    color: #cccccc;
    align-items: center;
    cursor: pointer;
    justify-content: center;
    font-size: 12px;
    &.tree-btn--dark {
      background: rgba(235, 235, 235, 0.05);
      border-color: #5f5f5f;
    }

    &.active {
      color: var(--ed-color-primary, #3b82f6);
      border-color: var(--ed-color-primary, #3b82f6);
    }
  }

  &.no-top-border {
    border-top: none !important;
  }
  &.no-top-padding {
    padding-top: 0 !important;
  }
  &:nth-child(n + 2) {
    border-top: 1px solid @side-outline-border-color;
  }
  &:first-child {
    border-top: none !important;
  }
}
</style>
