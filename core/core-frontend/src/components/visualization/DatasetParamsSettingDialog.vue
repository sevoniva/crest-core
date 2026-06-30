<template>
  <el-row>
    <el-form ref="form" size="mini" label-width="70px">
      <el-form-item :label="t('visualization.enable_jump')">
        <el-switch v-model="state.linkInfo.enable" size="mini" />
        <span v-show="state.linkInfo.enable" class="tips-area">
          Tips:{{ t('visualization.link_open_tips') }}
        </span>
      </el-form-item>
      <el-form-item :label="t('visualization.open_mode')">
        <el-radio-group v-model="state.linkInfo.openMode" :disabled="!state.linkInfo.enable">
          <el-radio value="_blank">{{ t('visualization.new_window') }}</el-radio>
          <el-radio value="_self">{{ t('visualization.now_window') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="t('visualization.hyperLinks')">
        <el-input v-model="state.linkInfo.content" :disabled="!state.linkInfo.enable" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSubmit">{{ t('visualization.confirm') }}</el-button>
        <el-button @click="onClose">{{ t('visualization.cancel') }}</el-button>
      </el-form-item>
    </el-form>
  </el-row>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { checkAddHttp, deepCopy } from '@/utils/utils'
import { useI18n } from '@/hooks/web/useI18n'
// 数据集参数跳转设置弹窗文案函数
const { t } = useI18n()
// 快照仓库用于记录跳转配置修改
const snapshotStore = snapshotStoreWithOut()
// 关闭弹窗事件
const emits = defineEmits(['onClose'])
// 跳转链接配置属性
const props = defineProps({
  linkInfo: {
    type: Object,
    required: true
  }
})

// 弹窗内维护跳转配置副本
const state = reactive({
  componentType: null,
  linkInfo: deepCopy(props.linkInfo),
  linkageActiveStatus: false
})

// 大屏主状态用于写回当前组件配置
const dvMainStore = dvMainStoreWithOut()

// 保存跳转配置并关闭弹窗
const onSubmit = () => {
  state.linkInfo.content = checkAddHttp(state.linkInfo.content)
  dvMainStore.curComponent.hyperlinks = deepCopy(state.linkInfo)
  snapshotStore.recordSnapshotCache('ds-onSubmit')
  onClose()
}

// 通知父级关闭弹窗
const onClose = () => {
  emits('onClose')
}
</script>

<style lang="less" scoped>
.tips-area {
  color: #909399;
  font-size: 8px;
  margin-left: 3px;
}
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
:deep(.el-popover) {
  height: 200px;
  overflow: auto;
}
.icon-font {
  color: white;
}
</style>
