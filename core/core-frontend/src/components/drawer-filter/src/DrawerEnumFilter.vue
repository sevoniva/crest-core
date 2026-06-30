<script setup lang="ts">
import { propTypes } from '@/utils/propTypes'
import { reactive } from 'vue'

// 枚举筛选组件属性
const props = defineProps({
  optionList: propTypes.arrayOf(
    propTypes.shape({
      id: propTypes.string,
      name: propTypes.string
    })
  ),
  title: propTypes.string
})

// 当前选中枚举值和预留选项状态
const state = reactive({
  activeStatus: [],
  optionList: []
})

// 切换枚举项选中状态并通知父级
const nodeChange = (id: string | number) => {
  const len = state.activeStatus.indexOf(id)
  if (len >= 0) {
    state.activeStatus.splice(len, 1)
  } else {
    state.activeStatus.push(id)
  }
  emits('filter-change', state.activeStatus)
}
// 清空枚举筛选状态
const clear = () => {
  state.activeStatus = []
}
// 筛选变化事件
const emits = defineEmits(['filter-change'])
defineExpose({
  clear
})
</script>

<template>
  <div class="draw-filter_enum">
    <span>{{ title }}</span>
    <div class="filter-item">
      <span
        v-for="ele in props.optionList"
        :key="ele.id"
        class="item"
        :class="[state.activeStatus.includes(ele.id) ? 'active' : '']"
        @click="nodeChange(ele.id)"
        >{{ ele.name }}</span
      >
    </div>
  </div>
</template>
<style lang="less" scope>
.draw-filter_enum {
  margin-bottom: 4px;

  > :nth-child(1) {
    color: var(--crestTextSecondary, #1f2329);
    font-family: var(--crest-custom_font, 'PingFang');
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    white-space: nowrap;
  }

  .filter-item {
    margin-top: 8px;
    .item,
    .more {
      font-family: var(--crest-custom_font, 'PingFang');
      white-space: nowrap;
      font-size: 14px;
      font-weight: 400;
      line-height: 24px;
      margin-right: 12px;
      text-align: center;
      padding: 1px 6px;
      background: var(--crestTextPrimary5, #f5f6f7);
      color: var(--crestTextPrimary, #1f2329);
      border-radius: 2px;
      cursor: pointer;
      display: inline-block;
      margin-bottom: 12px;
    }

    .active,
    .more:hover {
      background: var(--primary10, var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1)));
      color: var(--primaryselect, #0c296e);
    }

    .more {
      white-space: nowrap;
      display: inline-flex;
      align-items: center;
      i {
        margin-right: 5px;
      }
    }
  }
}
</style>
<style lang="less">
.filter-popper {
  padding: 0 !important;
  background: #fff !important;
}
</style>
