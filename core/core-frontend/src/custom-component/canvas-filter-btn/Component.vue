<!-- 画布查询筛选按钮 -->
<template>
  <el-tooltip :offset="22" effect="dark" placement="left" :content="t('visualization.query')">
    <div
      class="canvas-filter"
      :class="{ 'filter-btn-fix': isFixed }"
      @mousedown.stop
      @mousedup.stop
    >
      <div class="icon-slider" @mouseenter="slideOut" @mouseleave="slideBack">
        <div
          class="icon-container"
          :class="{ 'icon-container-active': filterActive }"
          :style="{ transform: `translateX(${offset}px)` }"
          @click="popAreaActiveChange"
        >
          <el-icon><Filter /></el-icon>
        </div>
      </div>
    </div>
  </el-tooltip>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue'
import { ElTooltip } from 'element-plus-secondary'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { useI18n } from '@/hooks/web/useI18n'
// 大屏主状态用于切换查询面板显示状态
const dvMainStore = dvMainStoreWithOut()
// 当前按钮横向滑出偏移量
const offset = ref(0)
// 鼠标悬浮时按钮滑出的距离
const slideDistance = ref(14) // 滑动距离
// 画布状态中记录当前查询面板区域
const { canvasState } = storeToRefs(dvMainStore)
// 查询按钮提示文案
const { t } = useI18n()

// 查询按钮是否固定定位
defineProps({
  isFixed: {
    type: Boolean,
    default: false
  }
})
// 根据画布当前点位区域判断筛选按钮是否处于激活态
const filterActive = computed(() => canvasState.value.curPointArea === 'hidden')
// 鼠标移入时滑出按钮
const slideOut = () => {
  offset.value = -slideDistance.value
}

// 切换查询弹层显示状态
const popAreaActiveChange = () => {
  dvMainStore.popAreaActiveSwitch()
}
// 鼠标移出时收回按钮
const slideBack = () => {
  offset.value = 0
}
</script>

<style lang="less" scoped>
.canvas-filter {
  position: absolute;
  right: -14px;
  bottom: 50px;
  width: 28px;
  height: 32px;
}
.icon-slider {
  position: relative;
  z-index: 100;
  width: 28px;
  height: 32px;
}

.icon-container {
  transition: transform 0.3s ease; /* 过渡动画 */
  background: rgba(26, 26, 26, 1);
  font-size: 14px;
  border-bottom: 1px solid rgba(67, 67, 67, 1);
  border-left: 1px solid rgba(67, 67, 67, 1);
  border-top: 1px solid rgba(67, 67, 67, 1);
  border-radius: 16px 0 0 16px;
  padding: 6px 0 0 6px;
  cursor: pointer;
  &:hover {
    background: rgba(235, 235, 235, 0.1);
  }

  &:active {
    background: rgba(235, 235, 235, 0.2);
  }
}
.icon-container-active {
  transform: translateX(-14px) !important;
}

img {
  max-width: 100%;
  max-height: 100%;
}

.filter-btn-fix {
  position: fixed !important;
}
</style>
