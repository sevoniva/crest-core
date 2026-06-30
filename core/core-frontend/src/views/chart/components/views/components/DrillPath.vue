<script lang="tsx" setup>
import { computed, PropType } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { ArrowRight } from '@element-plus/icons-vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
// 读取大屏画布状态，用于同步下钻路径的主题颜色
const dvMainStore = dvMainStoreWithOut()

// 下钻路径展示的多语言翻译入口
const { t } = useI18n()

// 下钻面包屑接收筛选路径、主题样式和禁用状态
const props = defineProps({
  drillFilters: {
    type: Array as PropType<Array<Record<string, any>>>,
    default: () => []
  },
  themeStyle: {
    type: Object,
    required: false,
    default: null
  },
  disabled: {
    type: Boolean,
    required: false,
    default: false
  }
})

// 下钻跳转事件交给父级处理，以复用图表筛选逻辑
const emit = defineEmits(['onDrillJump'])

// 下钻文字颜色跟随大屏高级样式配置
const textColor = computed(
  () => dvMainStore.canvasStyleData.component.seniorStyleSetting.drillLayerColor
)

// 点击面包屑节点时回退到指定下钻层级
const drillJump = index => {
  if (index < props.drillFilters.length) {
    emit('onDrillJump', index)
  }
}

// 将主题颜色写入 CSS 变量，供分隔图标和文本统一使用
const drillPathVar = computed(() => [{ '--drill-color': textColor.value }])
</script>

<template>
  <div
    v-if="props.drillFilters && props.drillFilters.length > 0"
    class="drill"
    :style="drillPathVar"
    :class="{ noClick: disabled }"
  >
    <el-breadcrumb :separator-icon="ArrowRight" class="drill-style">
      <el-breadcrumb-item class="drill-item" @click="drillJump(0)">
        <span :style="{ color: textColor }">{{ t('commons.all') }}</span>
      </el-breadcrumb-item>
      <el-breadcrumb-item
        v-for="(filter, index) in props.drillFilters"
        :key="index"
        class="drill-item"
        @click="drillJump(index + 1)"
      >
        <span class="item-name" :style="{ color: textColor }" :title="filter.value[0]">{{
          filter.value[0]
        }}</span>
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<style lang="less" scoped>
.drill-style {
  font-size: 12px;
}
.drill-style :deep(.el-breadcrumb__separator) {
  margin: 0 !important;
}
.drill-item {
  cursor: pointer;
  .item-name {
    max-width: 200px;
    display: inline-block;
    text-overflow: ellipsis;
    white-space: nowrap;
    overflow: hidden;
  }
}
.drill {
  z-index: 1;
  height: 20px;
  padding: 0 16px;
  ::v-deep(.ed-icon) {
    color: var(--drill-color) !important;
  }
}
.noClick {
  pointer-events: none; /* 禁止鼠标点击 */
}
</style>
