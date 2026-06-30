<script lang="ts" setup>
import icon_expandLeft_filled from '@/assets/svg/icon_expand-left_filled.svg'
import icon_expandRight_filled from '@/assets/svg/icon_expand-right_filled.svg'
import { toRefs, ref, watch, nextTick } from 'vue'
import { propTypes } from '@/utils/propTypes'
// 定义工作表页签组件接收的页签列表和当前页签
const props = defineProps({
  tabList: propTypes.arrayOf(
    propTypes.shape({
      label: String,
      value: String,
      sheet: Boolean,
      inspectionStatus: String,
      inspectionMessage: String
    })
  ),
  activeTab: propTypes.string.def('')
})

// 保存当前激活页签在列表中的索引
const activeTabIndex = ref(0)

// 声明页签点击时向父组件派发的事件
const emits = defineEmits(['TabClick'])
const { activeTab } = toRefs(props)
// 处理页签点击并滚动到可见区域
const handleTabClick = tab => {
  let tabDom = document.getElementById(`tab-${tab.value}`)
  if (tabDom.offsetLeft + tabDom.offsetWidth > tabWrapper.value.offsetWidth) {
    tabWrapper.value.scrollLeft =
      tabDom.offsetLeft + tabDom.offsetWidth - tabWrapper.value.offsetWidth
  } else {
    tabWrapper.value.scrollLeft = 0
  }
  emits('TabClick', tab)
}
// 持有页签滚动容器实例
const tabWrapper = ref()
// 控制左右滚动按钮是否展示
const showBtn = ref(false)
// 监听当前激活页签并同步索引
watch(
  () => activeTab.value,
  val => {
    activeTabIndex.value = props.tabList.findIndex(ele => ele.value === val)
  },
  { immediate: true }
)

// 监听页签列表变化并判断是否需要显示滚动按钮
watch(
  () => props.tabList,
  () => {
    nextTick(() => {
      showBtn.value = tabWrapper.value.scrollWidth > tabWrapper.value.offsetWidth
    })
  },
  { immediate: true }
)

// 向左滚动页签列表
const prevClick = () => {
  let domWrapper = tabWrapper.value
  if (!domWrapper.scrollLeft) return
  domWrapper.scrollLeft -= 30
}

// 向右滚动页签列表
const nextClick = () => {
  let domWrapper = tabWrapper.value
  domWrapper.scrollLeft += 30
}
</script>

<template>
  <div class="sheet-tabs">
    <div ref="tabWrapper" class="tab-wrapper">
      <div
        v-for="tab in tabList"
        :key="tab.label"
        :id="`tab-${tab.value}`"
        :title="tab.inspectionMessage || tab.label"
        :class="[
          { active: activeTab === tab.value, skipped: tab.inspectionStatus === 'SKIPPED' },
          'sheet-tab'
        ]"
        @click="handleTabClick(tab)"
      >
        <span class="ellipsis">
          {{ tab.label }}
        </span>
        <span class="sheet-tab-status" v-if="tab.inspectionStatus === 'SKIPPED'">已跳过</span>
      </div>
    </div>
    <div class="tab-btn" v-if="showBtn">
      <el-icon size="12px" @click="prevClick">
        <Icon name="icon_expand-left_filled"><icon_expandLeft_filled class="svg-icon" /></Icon>
      </el-icon>
      <el-icon size="12px" @click="nextClick">
        <Icon name="icon_expand-right_filled"><icon_expandRight_filled class="svg-icon" /></Icon>
      </el-icon>
    </div>
  </div>
</template>

<style lang="less" scoped>
.sheet-tabs {
  border-top-left-radius: 3px;
  width: 100%;
  position: relative;
  padding-right: 60px;

  .tab-wrapper {
    height: 100%;
    display: flex;
    overflow-x: auto;
    &::-webkit-scrollbar {
      display: none;
    }
  }

  .tab-btn {
    padding: 8px 12px;
    display: flex;
    justify-content: space-between;
    width: 60px;
    height: 28px;
    position: absolute;
    right: 0;
    top: 4px;
    background: #fff;

    .ed-icon {
      color: #8d9199;
      cursor: pointer;

      &.disabled {
        cursor: not-allowed;
      }

      &:not(.disabled):hover {
        color: var(--ed-color-primary);
      }

      & + .ed-icon {
        margin-left: 12px;
      }
    }
  }

  .sheet-tab {
    color: #1f2329;
    cursor: pointer;
    position: relative;
    padding: 0 20px;
    display: flex;
    align-items: center;
    height: 36px;
    max-width: 200px;
    border-bottom: 1px solid rgba(31, 35, 41, 0.15);
    &:hover {
      color: var(--ed-color-primary);
    }

    .ellipsis {
      max-width: 200px;
      font-size: 14px;
    }

    .sheet-tab-status {
      flex: none;
      margin-left: 8px;
      padding: 0 6px;
      height: 18px;
      line-height: 18px;
      color: #646a73;
      background: rgba(31, 35, 41, 0.08);
      border-radius: 3px;
      font-size: 12px;
    }

    &.skipped {
      color: #8f959e;
    }

    &::after,
    &::before {
      content: '';
      position: absolute;
      height: 24px;
      width: 1px;
      top: 50%;
      transform: translateY(-50%);
      background: rgba(31, 35, 41, 0.15);
    }

    &::after {
      right: 0;
    }
    &::before {
      left: 0;
    }

    & + .active {
      ::before {
        content: '';
        left: -3px;
        height: 30px;
        width: 2px;
        position: absolute;
        top: 0;
        background: #fff;
      }
    }
  }
  .active {
    box-shadow: 0px -1px 0px 0px #f5f6f7 inset;
    color: var(--ed-color-primary);
    border: 1px solid rgba(31, 35, 41, 0.15);
    border-bottom: none;
    border-top-left-radius: 4px;
    border-top-right-radius: 4px;
    background: #f5f6f7;

    &::before,
    &::after {
      display: none;
    }

    & + .sheet-tab {
      &::before {
        display: none;
      }
    }
  }
}
</style>
