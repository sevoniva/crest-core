<script lang="tsx" setup>
import icon_deleteTrash_outlined from '@/assets/svg/icon_delete-trash_outlined.svg'
import icon_down_outlined1 from '@/assets/svg/icon_down_outlined-1.svg'
import { onMounted, reactive, toRefs, watch } from 'vue'
import { formatterItem } from '@/views/chart/components/js/formatter'
import { getItemType } from './utils'
import { Delete, Filter } from '@element-plus/icons-vue'
import { fieldType } from '@/utils/attr'
import { iconFieldMap } from '@/components/icon-group/field-list'
import { getCSSVariable } from '@/utils/color'

// 保存筛选字段标签的格式化配置和颜色状态
const state = reactive({
  formatterItem: formatterItem,
  tagColor: getCSSVariable()
})

// 定义筛选字段标签接收的字段、索引和主题参数
const props = defineProps({
  param: {
    type: Object,
    required: false
  },
  item: {
    type: Object,
    required: true
  },
  index: {
    type: Number,
    required: true
  },
  dimensionData: {
    type: Array,
    required: true
  },
  quotaData: {
    type: Array,
    required: true
  },
  themes: {
    type: String,
    default: 'dark'
  }
})

// 声明删除字段和编辑字段筛选时向父组件派发的事件
const emit = defineEmits(['onFilterItemRemove', 'editItemFilter'])

const { item } = toRefs(props)

// 监听字段集合变化并刷新标签颜色
watch(
  [() => props.dimensionData, () => props.quotaData, props.item, item],
  () => {
    getItemTagColor()
  },
  { deep: true }
)

// 根据下拉菜单命令执行删除或编辑筛选操作
const clickItem = param => {
  if (!param) {
    return
  }
  switch (param.type) {
    case 'remove':
      removeItem()
      break
    case 'filter':
      editFilter()
      break
    default:
      break
  }
}

// 将点击类型包装为下拉菜单命令对象
const beforeClickItem = type => {
  return {
    type: type
  }
}

// 打开当前字段的筛选编辑
const editFilter = () => {
  item.value.index = props.index
  emit('editItemFilter', item.value)
}

// 删除当前筛选字段
const removeItem = () => {
  item.value.index = props.index
  emit('onFilterItemRemove', item.value)
}

// 根据字段类型计算标签展示颜色
const getItemTagColor = () => {
  state.tagColor = getItemType(props.dimensionData, props.quotaData, props.item)
}

// 挂载时初始化字段标签颜色
onMounted(() => {
  getItemTagColor()
})
</script>

<template>
  <span class="item-style">
    <el-dropdown :effect="themes" trigger="click" @command="clickItem">
      <el-tag
        class="item-axis father"
        :class="'editor-' + props.themes"
        :style="{ backgroundColor: state.tagColor + '0a', border: '1px solid ' + state.tagColor }"
      >
        <span style="display: flex">
          <el-icon>
            <Icon :className="`field-icon-${fieldType[item.fieldType]}`"
              ><component
                class="svg-icon"
                :class="`field-icon-${fieldType[item.fieldType]}`"
                :is="iconFieldMap[fieldType[item.fieldType]]"
              ></component
            ></Icon>
          </el-icon>
        </span>
        <span class="item-span-style" :title="item.name">{{ item.name }}</span>
        <span :data-id="item.id" class="node-id_private"></span>
        <el-icon class="child remove-icon" size="14px">
          <Icon name="icon_delete-trash_outlined" class-name="inner-class"
            ><icon_deleteTrash_outlined @click="removeItem" class="svg-icon inner-class"
          /></Icon>
        </el-icon>
        <el-icon
          class="child"
          style="position: absolute; top: 7px; right: 10px; color: #a6a6a6; cursor: pointer"
        >
          <Icon name="icon_down_outlined-1"
            ><icon_down_outlined1 class="svg-icon el-icon-arrow-down el-icon-delete"
          /></Icon>
        </el-icon>
      </el-tag>
      <template #dropdown>
        <el-dropdown-menu :effect="themes" class="drop-style">
          <el-dropdown-item :icon="Filter" :command="beforeClickItem('filter')">
            <span>{{ $t('chart.filter') }}...</span>
          </el-dropdown-item>
          <el-dropdown-item :icon="Delete" divided :command="beforeClickItem('remove')">
            <span>{{ $t('chart.delete') }}</span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </span>
</template>

<style lang="less" scoped>
.item-style {
  position: relative;
  width: 100%;
  display: block;
  .ed-dropdown {
    display: flex;
  }
  :deep(.ed-tag__content) {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
}

.item-axis {
  padding: 1px 8px;
  margin: 0 3px 2px 3px;
  height: 28px;
  line-height: 28px;
  display: flex;
  border-radius: 6px;
  box-sizing: border-box;
  white-space: nowrap;
  width: 100%;
  justify-content: space-between;
  align-items: center;
}

.item-axis:hover {
  cursor: pointer;
}

span {
  font-size: 12px;
}

.summary-span {
  margin-left: 4px;
  color: #a6a6a6;
}

.inner-dropdown-menu {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.item-span-drop {
  color: #a6a6a6;
  display: flex;
}

.item-span-style {
  display: inline-block;
  width: 115px;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  color: #1f2329;
  margin-left: 4px;
}

.editor-dark {
  .item-span-style {
    color: #ffffff !important;
  }
}

.drop-style {
  :deep(.ed-dropdown-menu__item) {
    height: 32px;
  }
}

.remove-icon {
  position: absolute;
  top: 7px;
  right: 24px;
  color: #646a73;
  cursor: pointer;

  .inner-class {
    font-size: 14px;
  }
}

.father .child {
  visibility: hidden;
}

.father:hover .child {
  visibility: visible;
}
</style>
