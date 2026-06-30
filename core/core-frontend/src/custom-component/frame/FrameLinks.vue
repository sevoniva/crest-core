<template>
  <el-row>
    <el-form @submit.prevent ref="form" size="small" style="width: 100%">
      <el-form-item>
        <template #label>
          <span class="data-area-label">
            <span style="margin-right: 4px">
              {{ t('visualization.web_url') }}
            </span>
            <el-tooltip class="item" :effect="toolTip" placement="bottom">
              <template #content>
                <div>
                  {{ t('visualization.web_set_tips') }}
                </div>
              </template>
              <el-icon class="hint-icon" :class="{ 'hint-icon--dark': themes === 'dark' }">
                <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
              </el-icon>
            </el-tooltip>
          </span>
        </template>
        <el-input :effect="themes" v-model="state.linkInfoTemp.src" @blur="onBlur" />
      </el-form-item>
    </el-form>
  </el-row>
</template>

<script setup lang="ts">
import icon_info_outlined from '@/assets/svg/icon_info_outlined.svg'
import { reactive, toRefs, watch, computed } from 'vue'
import { dvMainStoreWithOut } from '../../store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia/dist/pinia'
import { checkAddHttp, deepCopy } from '../../utils/utils'
import { snapshotStoreWithOut } from '../../store/modules/data-visualization/snapshot'
import { useI18n } from '../../hooks/web/useI18n'
import { useEmitt } from '@/hooks/web/useEmitt'
// 大屏主状态用于读取当前框架组件
const dvMainStore = dvMainStoreWithOut()
// 当前正在编辑的框架组件
const { curComponent } = storeToRefs(dvMainStore)
// 快照仓库用于记录链接编辑后的撤销点
const snapshotStore = snapshotStoreWithOut()
// 链接设置面板的国际化文案函数
const { t } = useI18n()

// 框架链接设置属性
const props = defineProps({
  canvasId: {
    type: String,
    require: true
  },
  frameLinks: {
    type: Object,
    required: true
  },
  themes: {
    type: String,
    required: true,
    default: 'dark'
  }
})

// 保持框架链接配置的响应式引用
const { frameLinks } = toRefs(props)

// tooltip 主题与当前编辑器主题反向匹配
const toolTip = computed(() => {
  return props.themes === 'dark' ? 'light' : 'dark'
})

// 框架链接编辑的临时状态
const state = reactive({
  linkInfoTemp: null,
  componentType: null,
  linkageActiveStatus: false,
  editFilter: ['view', 'custom']
})

// 初始化临时链接状态，避免输入过程直接污染外部配置
const init = () => {
  state.linkInfoTemp = deepCopy(frameLinks.value)
}
// 输入框失焦后补齐协议、写回组件并通知画布刷新
const onBlur = () => {
  state.linkInfoTemp.src = checkAddHttp(state.linkInfoTemp.src)
  curComponent.value.frameLinks.src = state.linkInfoTemp.src
  snapshotStore.recordSnapshotCache('frame-onBlur')
  useEmitt().emitter.emit('frameLinksChange-' + curComponent.value.id)
}
init()
// 监听相关数据变化并同步组件状态
watch(
  () => frameLinks.value,
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
