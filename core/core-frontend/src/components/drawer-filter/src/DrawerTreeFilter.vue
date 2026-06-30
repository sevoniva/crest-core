<script setup lang="ts">
import { propTypes } from '@/utils/propTypes'
import { ElTreeSelect } from 'element-plus-secondary'
import { computed, reactive, ref, PropType, toRefs, watch } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
const { t } = useI18n()
interface TreeConfig {
  checkStrictly: boolean
  showCheckbox: boolean
  checkOnClickNode: boolean
  placeholder: string
}
// 定义抽屉树筛选器接收的选项、标题和树配置
const props = defineProps({
  optionList: propTypes.arrayOf(
    propTypes.shape({
      value: propTypes.string,
      label: propTypes.string,
      children: Array,
      disabled: Boolean
    })
  ),
  title: propTypes.string,
  property: Object as PropType<TreeConfig>
})

const { property } = toRefs(props)
// 合并默认配置与外部传入的树组件配置
const treeConfig = computed(() => {
  let obj = Object.assign(
    {
      checkStrictly: false,
      showCheckbox: true,
      checkOnClickNode: true,
      placeholder: t('user.role')
    },
    property.value
  )
  return obj
})

// 保存当前选中的节点值和对应节点数据
const state = reactive({
  currentStatus: [],
  activeStatus: []
})

// 声明筛选条件变化时向父组件派发的事件
const emits = defineEmits(['filter-change'])
// 持有树选择组件实例
const filterTree = ref()
// 根据当前选中节点生成筛选值并通知父组件
const treeChange = () => {
  const nodes = state.currentStatus.map(id => {
    return filterTree.value?.getNode(id).data
  })
  state.activeStatus = [...nodes]
  emits(
    'filter-change',
    state.activeStatus.map(item => item.value)
  )
}
// 返回当前可选节点列表
const optionListNotSelect = computed(() => {
  return [...props.optionList]
})
// 清空当前树筛选选中状态
const clear = () => {
  state.currentStatus = []
}
// 监听选中节点变化并同步筛选结果
watch(
  () => state.currentStatus,
  () => {
    treeChange()
  },
  {
    immediate: true
  }
)
defineExpose({
  clear
})
</script>

<template>
  <div class="draw-filter_tree">
    <span>{{ title }}</span>
    <div class="filter-item">
      <el-tree-select
        node-key="value"
        ref="filterTree"
        :teleported="false"
        style="width: 100%"
        v-model="state.currentStatus"
        :data="optionListNotSelect"
        :highlight-current="true"
        multiple
        :render-after-expand="false"
        :placeholder="$t('common.please_select') + treeConfig.placeholder"
        :show-checkbox="treeConfig.showCheckbox"
        :check-strictly="treeConfig.checkStrictly"
        :check-on-click-node="treeConfig.checkOnClickNode"
      />
    </div>
  </div>
</template>
<style lang="less" scope>
.draw-filter_tree {
  margin-bottom: 16px;

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
  }
}
</style>
