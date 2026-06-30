<script setup lang="ts">
import { propTypes } from '@/utils/propTypes'
import { ElSelect, ElOption } from 'element-plus-secondary'
import { computed, reactive } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
// 抽屉筛选项的国际化文案函数
const { t } = useI18n()
// 多选筛选组件属性，包含候选项、标题和占位配置
const props = defineProps({
  optionList: propTypes.arrayOf(
    propTypes.shape({
      id: propTypes.string,
      name: propTypes.string
    })
  ),
  title: propTypes.string,
  property: {
    type: Object,
    default: () => {
      placeholder: ''
    }
  }
})

// 记录当前选中的筛选项
const state = reactive({
  activeStatus: []
})
// 筛选变化时向父级提交选中 ID
const emits = defineEmits(['filter-change'])

// 将选中对象转换为 ID 或值后触发筛选事件
const selectStatus = ids => {
  emits(
    'filter-change',
    ids.map(item => item.id || item.value)
  )
}

// 当前可选择项列表，保持独立计算入口便于后续扩展过滤逻辑
const optionListNotSelect = computed(() => {
  return [...props.optionList]
})
// 清空当前筛选选择
const clear = () => {
  state.activeStatus = []
}
defineExpose({
  clear
})
</script>

<template>
  <div class="draw-filter_base">
    <span>{{ title }}</span>
    <div class="filter-item">
      <el-select
        :teleported="false"
        style="width: 100%"
        v-model="state.activeStatus"
        value-key="id"
        filterable
        :placeholder="t('common.please_select') + props.property.placeholder"
        multiple
        @change="selectStatus"
      >
        <el-option
          v-for="item in optionListNotSelect"
          :key="item.name"
          :label="item.name"
          :value="item"
        />
      </el-select>
    </div>
  </div>
</template>
<style lang="less" scope>
.draw-filter_base {
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
