<script lang="ts" setup>
import { ref, reactive, computed } from 'vue'
import { ElDrawer, ElButton } from 'element-plus-secondary'
import DrawerFilter from '@/components/drawer-filter/src/DrawerFilter.vue'
import DrawerEnumFilter from '@/components/drawer-filter/src/DrawerEnumFilter.vue'
import DrawerTimeFilter from '@/components/drawer-filter/src/DrawerTimeFilter.vue'
import DrawerTreeFilter from '@/components/drawer-filter/src/DrawerTreeFilter.vue'
import { useI18n } from '@/hooks/web/useI18n'
const { t } = useI18n()
type FilterOption = {
  type: string
  field: string
  option?: any[]
  title?: string
  property?: Record<string, any>
  operator?: string
}
const props = withDefaults(
  defineProps<{
    filterOptions?: FilterOption[]
    title?: string
  }>(),
  {
    filterOptions: () => []
  }
)
// 抽屉内筛选组件引用列表
const myRefs = ref<any[]>([])
// 当前筛选组件配置列表
const componentList = computed<any[]>(() => {
  return props.filterOptions as any[]
})

// 维护抽屉筛选条件状态
const state = reactive<{ conditions: any[] }>({
  conditions: []
})
// 控制筛选抽屉显隐
const userDrawer = ref(false)

// 打开筛选抽屉
const init = () => {
  userDrawer.value = true
}
// 清理指定筛选组件的内部值
const cleanrInnerValue = (index: number) => {
  const field = componentList.value[index]?.field
  if (!field) {
    return
  }
  myRefs.value[index]?.clear()
  for (let i = 0; i < state.conditions.length; i++) {
    if (state.conditions[i].field === field) {
      state.conditions[i].value = []
    }
  }
}
// 清理指定筛选标签对应组件的内部值
const clearInnerTag = (index?: number) => {
  if (isNaN(index)) {
    for (let i = 0; i < componentList.value.length; i++) {
      myRefs.value[i]?.clear()
    }
    return
  }
  const condition = state.conditions[index]
  const field = condition?.field
  for (let i = 0; i < componentList.value.length; i++) {
    if (componentList.value[i].field === field) {
      myRefs.value[i]?.clear()
    }
  }
}
// 清理筛选条件并触发变更
const clearFilter = (id?: number) => {
  clearInnerTag(id)
  if (isNaN(id)) {
    const len = state.conditions.length
    state.conditions.splice(0, len)
  } else {
    state.conditions.splice(id, 1)
  }
  trigger()
}
// 同步筛选条件变化
const filterChange = (value, field, operator) => {
  let exits = false
  let len = state.conditions.length
  while (len--) {
    const condition = state.conditions[len]
    if (condition.field === field) {
      exits = true
      condition['value'] = value
    }
    if (!condition?.value?.length) {
      state.conditions.splice(len, 1)
    }
  }
  if (!exits && value?.length) {
    state.conditions.push({ field, value, operator })
  }
  treeFilterChange(value, field, operator)
}
// 重置筛选条件并关闭抽屉
const reset = () => {
  clearFilter()
  userDrawer.value = false
}
// 关闭筛选抽屉
const close = () => {
  userDrawer.value = false
}
// 定义筛选抽屉事件
const emits = defineEmits(['trigger-filter', 'tree-filter-change'])
// 通知父级筛选条件变化
const trigger = () => {
  emits('trigger-filter', state.conditions)
}
// 通知父级树筛选条件变化
const treeFilterChange = (value, field, operator) => {
  emits('tree-filter-change', {
    value,
    field,
    operator
  })
}
defineExpose({
  init,
  clearFilter,
  close,
  cleanrInnerValue
})
</script>

<template>
  <el-drawer
    :title="t('common.filter_condition')"
    v-model="userDrawer"
    size="600px"
    modal-class="drawer-main-container"
    direction="rtl"
  >
    <div v-for="(component, index) in componentList" :key="index">
      <drawer-tree-filter
        :ref="el => (myRefs[index] = el)"
        v-if="component.type === 'tree-select'"
        :option-list="component.option"
        :title="component.title"
        :property="component.property"
        @filter-change="v => filterChange(v, component.field, 'in')"
      />
      <drawer-filter
        :ref="el => (myRefs[index] = el)"
        v-if="component.type === 'select'"
        :option-list="component.option"
        :title="component.title"
        :property="component.property"
        @filter-change="v => filterChange(v, component.field, 'in')"
      />
      <drawer-enum-filter
        :ref="el => (myRefs[index] = el)"
        v-if="component.type === 'enum'"
        :option-list="component.option"
        :title="component.title"
        @filter-change="v => filterChange(v, component.field, 'in')"
      />
      <drawer-time-filter
        :ref="el => (myRefs[index] = el)"
        v-if="component.type === 'time'"
        :title="component.title"
        :property="component.property"
        @filter-change="v => filterChange(v, component.field, component.operator)"
      />
    </div>

    <template #footer>
      <el-button secondary @click="reset">{{ t('commons.reset') }}</el-button>
      <el-button @click="trigger" type="primary">{{ t('commons.adv_search.search') }}</el-button>
    </template>
  </el-drawer>
</template>

<style lang="less">
.drawer-main-container {
  .ed-drawer__body {
    padding: 16px 24px 80px !important;
  }
  .ed-drawer__footer {
    padding: 16px 24px;
    height: 64px;
  }
}
</style>
