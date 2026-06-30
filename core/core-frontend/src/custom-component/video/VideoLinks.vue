<template>
  <el-row>
    <el-form @submit.prevent ref="form" size="small" style="width: 100%">
      <el-form-item :effect="themes" :label="t('visualization.auto_play')">
        <el-switch
          :effect="themes"
          @change="onChange"
          v-model="state.linkInfoTemp[state.linkInfoTemp.videoType].autoplay"
          size="mini"
        />
      </el-form-item>
      <el-form-item
        v-if="state.linkInfoTemp.videoType === 'web'"
        :label="t('visualization.play_frequency')"
        :effect="themes"
      >
        <el-radio-group
          :effect="themes"
          @change="onChange"
          v-model="state.linkInfoTemp[state.linkInfoTemp.videoType].loop"
        >
          <el-radio :effect="themes" :label="false">{{ t('visualization.play_once') }}</el-radio>
          <el-radio :effect="themes" :label="true">{{ t('visualization.play_circle') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :effect="themes" :label="t('visualization.video_links')">
        <el-input
          :effect="themes"
          @blur="onChange"
          v-model="state.linkInfoTemp[state.linkInfoTemp.videoType].src"
        />
        <span class="tips-class"> Tips:{{ t('visualization.video_tips') }} </span>
      </el-form-item>
    </el-form>
  </el-row>
</template>

<script setup lang="ts">
import { reactive, toRefs, watch } from 'vue'
import { dvMainStoreWithOut } from '../../store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia/dist/pinia'
import { checkAddHttp, deepCopy } from '../../utils/utils'
import { snapshotStoreWithOut } from '../../store/modules/data-visualization/snapshot'
import { useI18n } from '../../hooks/web/useI18n'
import { useEmitt } from '@/hooks/web/useEmitt'
// 大屏主状态用于写回视频链接配置
const dvMainStore = dvMainStoreWithOut()
// 当前组件和当前标签页内组件引用
const { curComponent, curActiveTabInner } = storeToRefs(dvMainStore)
// 快照仓库用于记录视频链接修改
const snapshotStore = snapshotStoreWithOut()
// 视频链接设置文案函数
const { t } = useI18n()

// 视频链接设置属性
const props = defineProps({
  themes: {
    type: String,
    required: true,
    default: 'dark'
  },
  linkInfo: {
    type: Object,
    required: true
  },
  // 属性所属组件位置
  attrPosition: {
    type: String,
    required: false,
    default: 'panel'
  }
})

// 保持链接配置和属性位置的响应式引用
const { linkInfo, attrPosition } = toRefs(props)

// 弹窗内维护视频链接临时配置
const state = reactive({
  linkInfoTemp: null,
  componentType: null,
  linkageActiveStatus: false,
  editFilter: ['view', 'custom']
})

// 初始化视频链接临时配置
const init = () => {
  state.linkInfoTemp = deepCopy(linkInfo.value)
}

// 写回视频链接配置并通知播放器刷新
const onChange = () => {
  state.linkInfoTemp[state.linkInfoTemp.videoType].src = checkAddHttp(
    state.linkInfoTemp[state.linkInfoTemp.videoType].src
  )

  if (attrPosition.value === 'panel') {
    curComponent.value.videoLinks = state.linkInfoTemp
  } else {
    curActiveTabInner.value.videoLinks = state.linkInfoTemp
  }

  curComponent.value.videoLinks = state.linkInfoTemp
  snapshotStore.recordSnapshotCache('video-onChange')
  useEmitt().emitter.emit('videoLinksChange-' + curComponent.value.id)
}

init()

// 外部视频链接配置变化后刷新临时编辑态
watch(
  () => linkInfo.value,
  () => {
    init()
  },
  { deep: true }
)
</script>

<style lang="less" scoped>
.slot-class {
  color: white;
}

.bottom {
  margin-top: 20px;
  text-align: center;
}
.ellipsis-area {
  margin-left: 10px;
  margin-right: 10px;
  overflow: hidden; /*超出部分隐藏*/
  white-space: nowrap; /*不换行*/
  text-overflow: ellipsis; /*超出部分文字以...显示*/
  background-color: #f7f8fa;
  color: #3d4d66;
  font-size: 12px;
  line-height: 24px;
  height: 24px;
  border-radius: 3px;
}

.select-filed {
  margin-left: 10px;
  margin-right: 10px;
  overflow: hidden; /*超出部分隐藏*/
  white-space: nowrap; /*不换行*/
  text-overflow: ellipsis; /*超出部分文字以...显示*/
  color: #3d4d66;
  font-size: 12px;
  line-height: 35px;
  height: 35px;
  border-radius: 3px;
}

.tips-class {
  color: #909399;
  font-size: 8px;
  margin-left: 3px;
}

.hint-icon {
  cursor: pointer;
  font-size: 14px;
  color: #646a73;

  &.hint-icon--dark {
    color: #a6a6a6;
  }
}
.data-area-label {
  display: flex;
  flex-direction: row;
  align-items: center;
}
</style>
