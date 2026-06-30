<script lang="ts" setup>
import iconSetting from '@/assets/svg/icon-setting.svg'
import { reactive, ref, toRaw } from 'vue'
import { Icon } from '@/components/icon-custom'
import {
  ElDropdown,
  ElDropdownMenu,
  ElCheckbox,
  ElCheckboxGroup,
  ElIcon,
  ElButton
} from 'element-plus-secondary'

import type { CheckboxValueType } from 'element-plus-secondary'
import { propTypes } from '@/utils/propTypes'

// 定义列显示控制组件接收的列信息和插件状态
const props = defineProps({
  columnNames: propTypes.arrayOf(
    propTypes.shape({
      label: propTypes.string,
      props: propTypes.string
    })
  ),
  isPluginLoaded: propTypes.bool.def(false)
})

// 声明列显示配置变更事件
const emit = defineEmits(['columnChange'])

// 控制是否全选列
const checkAll = ref(true)
// 控制全选框的半选状态
const isIndeterminate = ref(false)

// 保存当前选中的列字段
const state = reactive({
  checkedColumnNames: props.columnNames.map(ele => ele.props)
})

// 处理单列勾选变化并同步选中字段
const handleCheckedColumnNamesChange = (value: string[]) => {
  const checkedCount = value.length
  checkAll.value = checkedCount === props.columnNames.length
  isIndeterminate.value = checkedCount > 0 && checkedCount < props.columnNames.length

  emit('columnChange', toRaw(state.checkedColumnNames))
}
// 处理全选状态变化并同步选中字段
const handleCheckAllChange = (val: CheckboxValueType) => {
  state.checkedColumnNames = val ? props.columnNames.map(ele => ele.props) : []
  if (!props.isPluginLoaded) {
    state.checkedColumnNames = state.checkedColumnNames.filter(
      ele => !['dept', 'roles'].includes(ele)
    )
  }
  isIndeterminate.value = false
  emit('columnChange', toRaw(state.checkedColumnNames))
}
</script>

<template>
  <el-dropdown trigger="click" :hide-on-click="false">
    <el-button secondary>
      <template #icon>
        <el-icon>
          <Icon name="icon-setting"><iconSetting class="svg-icon" /></Icon>
        </el-icon>
      </template>
      {{ $t('component.columnList') }}
    </el-button>
    <template #dropdown>
      <el-dropdown-menu class="list-columns-select">
        <el-main class="main-div-select">
          <p class="title">{{ $t('component.selectInfo') }}</p>
          <el-checkbox
            v-model="checkAll"
            :indeterminate="isIndeterminate"
            @change="handleCheckAllChange"
            >{{ $t('component.allSelect') }}</el-checkbox
          >
          <el-checkbox-group
            v-model="state.checkedColumnNames"
            @change="handleCheckedColumnNamesChange"
          >
            <el-checkbox v-for="column in columnNames" :key="column.props" :label="column.props">{{
              $t(column.label)
            }}</el-checkbox>
          </el-checkbox-group>
        </el-main>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style lang="less" scoped>
.list-columns-select {
  padding: 8px 11px !important;
  width: 238px;

  .title,
  .ed-checkbox {
    font-family: var(--crest-custom_font, 'PingFang');
    font-size: 14px;
    font-weight: 400;
    padding: 5px 0;
    margin: 0;
    color: #8f959e;
  }

  .ed-checkbox {
    width: 100%;
  }
  .main-div-select {
    max-height: 320px;
    padding: 0;
  }
}
</style>
