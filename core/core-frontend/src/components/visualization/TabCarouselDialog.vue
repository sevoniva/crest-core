<template>
  <el-row>
    <el-form ref="form" size="mini" label-width="70px">
      <el-form-item :label="t('visualization.enable_carousel')">
        <el-switch v-model="state.carouselEnable" size="mini" />
      </el-form-item>
      <el-form-item :label="t('visualization.switch_time')">
        <el-input
          v-model="state.switchTime"
          :disabled="!state.carouselEnable"
          type="number"
          size="mini"
          :min="2"
          :max="3600"
          class="hide-icon-number number-padding"
          @change="switchTimeChange"
        >
          <template v-slot:append>S</template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSubmit">{{ t('visualization.confirm') }}</el-button>
        <el-button @click="onClose">{{ t('visualization.cancel') }}</el-button>
      </el-form-item>
    </el-form>
  </el-row>
</template>

<script lang="ts" setup>
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { reactive } from 'vue'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { useI18n } from '@/hooks/web/useI18n'
// 标签页轮播设置弹窗文案函数
const { t } = useI18n()
// 大屏主状态用于读取当前组件
const dvMainStore = dvMainStoreWithOut()
// 当前正在编辑的组件
const { curComponent } = storeToRefs(dvMainStore)
// 快照仓库用于记录轮播配置修改
const snapshotStore = snapshotStoreWithOut()
// 弹窗内维护轮播开关和切换时间
const state = reactive({
  carouselEnable: false,
  switchTime: 50
})

// 关闭弹窗事件
const emits = defineEmits(['onClose'])
state.carouselEnable = curComponent.value.style.carouselEnable
state.switchTime = curComponent.value.style.switchTime

// 限制轮播切换时间在允许范围内
const switchTimeChange = () => {
  if (!state.switchTime || state.switchTime < 2) {
    state.switchTime = 2
  } else if (state.switchTime && state.switchTime > 3600) {
    state.switchTime = 3600
  }
}

// 保存轮播配置并关闭弹窗
const onSubmit = () => {
  curComponent.value.style.carouselEnable = state.carouselEnable
  curComponent.value.style.switchTime = state.switchTime
  snapshotStore.recordSnapshotCache('onSubmit')
  onClose()
}

// 通知父级关闭弹窗
const onClose = () => {
  emits('onClose')
}
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
:deep(.el-popover) {
  height: 200px;
  overflow: auto;
}
.icon-font {
  color: white;
}
</style>
