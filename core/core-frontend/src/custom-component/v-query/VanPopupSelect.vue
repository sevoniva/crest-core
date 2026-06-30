<script lang="ts" setup>
import { ref, computed, PropType } from 'vue'
import FixedSizeList from 'element-plus-secondary/es/components/virtual-list/src/components/fixed-size-list.mjs'
import VanPopup from 'vant/es/popup'
import 'vant/es/popup/style'

// 接收移动端弹窗选择器选项和选择模式
const props = defineProps({
  options: {
    type: Array as PropType<Array<Record<string, any>>>,
    default: () => []
  },
  selectValue: {
    type: Array as PropType<any[]>,
    default: () => []
  },
  multiple: {
    type: Boolean,
    default: false
  }
})

// 控制选择弹窗显隐
const showSelect = ref(false)
let oldCheckList = []
// 全选勾选状态
const checkAll = ref(false)
// 全选半选状态
const isIndeterminate = ref(false)
// 多选模式下已选择的全部项
const checkTableList = ref([])
// 当前弹窗内选中的项
const checkList = ref([])
// 选项搜索关键字
const keywords = ref('')
// 将传入选中值统一转换为数组
const toSelectValueList = value => {
  if (Array.isArray(value)) {
    return [...value]
  }
  return [value].filter(ele => ele !== undefined && ele !== null && ele !== '')
}
// 根据搜索关键字过滤选项
const tableListWithSearch = computed(() => {
  if (!keywords.value) return props.options
  return props.options.filter((ele: any) =>
    ele.label.toLowerCase().includes(keywords.value.toLowerCase())
  )
})
// 定义清空和确认事件
const emits = defineEmits(['onClear', 'onConfirm'])
// 重置弹窗选择状态
const reset = () => {
  oldCheckList = []
  showSelect.value = false
  checkAll.value = false
  isIndeterminate.value = false
  keywords.value = ''
}
// 打开选择弹窗并初始化选中值
const showPopup = () => {
  reset()
  if (props.multiple) {
    checkList.value = toSelectValueList(props.selectValue)
  } else {
    checkList.value = toSelectValueList(props.selectValue)
    oldCheckList = [...checkList.value]
  }
  showSelect.value = true
}

// 清空选择并通知父级
const onClear = () => {
  reset()
  emits('onClear')
}

// 确认选择并通知父级
const onConfirm = () => {
  showSelect.value = false
  checkAll.value = false
  isIndeterminate.value = false
  keywords.value = ''
  emits('onConfirm', checkList.value)
}

// 同步单选或多选勾选变化
const handleCheckedTablesChange = (value: any[]) => {
  if (!props.multiple) {
    if (!oldCheckList.length) {
      oldCheckList = [...value]
    } else {
      checkList.value = value.filter(ele => !oldCheckList.includes(ele))
      oldCheckList = [...checkList.value]
    }
    return
  }
  const checkedCount = value.length
  checkAll.value = checkedCount === tableListWithSearch.value.length
  isIndeterminate.value = checkedCount > 0 && checkedCount < tableListWithSearch.value.length
  const tableNameArr = tableListWithSearch.value.map((ele: any) => ele.label)
  checkTableList.value = [
    ...new Set([...checkTableList.value.filter(ele => !tableNameArr.includes(ele)), ...value])
  ]
}

// 同步当前搜索结果的全选变化
const handleCheckAllChange = (val: any) => {
  checkList.value = val
    ? [...new Set([...tableListWithSearch.value.map((ele: any) => ele.label), ...checkList.value])]
    : []
  isIndeterminate.value = false
  const tableNameArr = tableListWithSearch.value.map((ele: any) => ele.label)
  checkTableList.value = val
    ? [...new Set([...tableNameArr, ...checkTableList.value])]
    : checkTableList.value.filter(ele => !tableNameArr.includes(ele))
}
</script>

<template>
  <div class="vant-mobile_select" @click="showPopup" />
  <van-popup teleport="body" position="bottom" v-model:show="showSelect">
    <div class="container-vant_mobile">
      <div class="select-all">
        <el-checkbox
          v-model="checkAll"
          v-if="multiple"
          :indeterminate="isIndeterminate"
          @change="handleCheckAllChange"
        >
          {{ $t('component.allSelect') }}
        </el-checkbox>

        <el-input
          style="position: absolute; top: 3px; right: 150px; width: 150px"
          v-model="keywords"
          clearable
        ></el-input>
        <button
          style="position: absolute; top: 0; right: 60px"
          @click="onClear"
          class="van-picker__confirm van-haptics-feedback"
        >
          {{ $t('commons.clear') }}
        </button>
        <button
          style="position: absolute; top: 0; right: 10px"
          @click="onConfirm"
          class="van-picker__confirm van-haptics-feedback"
        >
          {{ $t('commons.confirm') }}
        </button>
      </div>
      <el-checkbox-group
        v-model="checkList"
        style="position: relative"
        @change="handleCheckedTablesChange"
      >
        <FixedSizeList
          :item-size="32"
          :data="tableListWithSearch"
          :total="tableListWithSearch.length"
          :height="460"
          :scrollbar-always-on="true"
          class-name="ed-select-dropdown__list"
          layout="vertical"
        >
          <template #default="{ index, style }">
            <div class="list-item_primary" :style="style">
              <el-checkbox :label="tableListWithSearch[index].value">
                {{ tableListWithSearch[index].label }}</el-checkbox
              >
            </div>
          </template>
        </FixedSizeList>
      </el-checkbox-group>
    </div>
  </van-popup>
</template>

<style lang="less">
.vant-mobile_select {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.container-vant_mobile {
  overflow: hidden;
  padding: 0 10px;

  .select-all {
    height: 40px;
    padding-left: 12px;
    display: flex;
    align-items: center;
    border-bottom: 1px solid #dee0e3;
    position: relative;

    .ed-input__wrapper {
      background-color: var(--ed-input-bg-color, var(--ed-fill-color-blank)) !important;
      box-shadow: 0 0 0 1px var(--ed-color-primary) inset !important;
    }
  }

  .ed-checkbox__label {
    display: inline-flex;
    align-items: center;
    color: #1f2329 !important;
  }

  .ed-vl__window {
    scrollbar-width: none;
  }
}
</style>
