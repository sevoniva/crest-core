<script setup lang="ts">
import { SpreadSheet, Node } from '@antv/s2'
import { PropType } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { S2Event, SortFuncParam } from '@antv/s2'
import { SortUp, SortDown, Sort } from '@element-plus/icons-vue'
import { cloneDeep } from 'lodash-es'

const { t } = useI18n()
// 定义组件入参，约束外部传入配置
const props = defineProps({
  table: {
    type: Object as PropType<SpreadSheet>,
    required: true
  },
  meta: {
    type: Object as PropType<Node>,
    required: true
  }
})
// 处理排序配置并同步展示顺序
const sortFunc = (sortParams: SortFuncParam) => {
  if (!sortParams.sortMethod) {
    return sortParams.data
  }
  const data = cloneDeep(sortParams.data)
  return data.sort((a, b) => {
    // 总计行放最后
    if (a['SUMMARY']) {
      return 1
    }
    const field = sortParams.sortFieldId
    const aValue = a[field]
    const bValue = b[field]
    if (aValue === bValue) {
      return 0
    }
    if (sortParams.sortMethod === 'asc') {
      if (typeof aValue === 'number') {
        return aValue - bValue
      }
      return aValue < bValue ? -1 : 1
    }
    if (typeof aValue === 'number') {
      return bValue - aValue
    }
    return aValue > bValue ? -1 : 1
  })
}
// 处理排序配置并同步展示顺序
const sort = (type?) => {
  props.table.updateSortMethodMap(props.meta.field, type, true)
  props.table.emit(S2Event.RANGE_SORT, [
    {
      sortFieldId: props.meta.field,
      sortMethod: type,
      sortFunc
    }
  ])
}
</script>
<template>
  <el-col style="color: black">
    <el-row align="middle">
      <el-icon><SortUp /></el-icon>
      <span class="sort-btn" @click="() => sort('asc')">{{ t('chart.asc') }}</span>
    </el-row>
    <el-row align="middle">
      <el-icon><SortDown /></el-icon>
      <span class="sort-btn" @click="() => sort('desc')">{{ t('chart.desc') }}</span>
    </el-row>
    <el-row align="middle">
      <el-icon><Sort /></el-icon>
      <span class="sort-btn" @click="() => sort()">{{ t('chart.default') }}</span>
    </el-row>
  </el-col>
</template>
<style scoped>
.sort-btn {
  font-size: 14px;
  cursor: pointer;
}
</style>
