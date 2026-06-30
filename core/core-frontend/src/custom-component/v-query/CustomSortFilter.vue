<template>
  <el-dialog
    destroy-on-close
    append-to-body
    :title="$t('v_query.custom_sort')"
    v-model="dialogShow"
    class="custom-sort_filter"
    width="300px"
  >
    <div>
      <draggable :list="sortList" animation="300" class="drag-list">
        <template #item="{ element }">
          <span :key="element.name" class="item-dimension" :title="element">
            <el-icon size="20px">
              <Icon name="drag"><drag class="svg-icon" /></Icon>
            </el-icon>
            <span class="item-span">
              {{ element }}
            </span>
          </span>
        </template>
      </draggable>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="closeDialog">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ $t('chart.confirm') }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import drag from '@/assets/svg/drag.svg'
import draggable from 'vuedraggable'
import { ref, unref } from 'vue'
import { cloneDeep } from 'lodash-es'

// 弹窗内可拖拽排序的候选值列表
const sortList = ref([])
// 控制自定义排序弹窗显示状态
const dialogShow = ref(false)
// 对外入口：复制传入列表并打开排序弹窗
const sortInit = list => {
  init(list)
  dialogShow.value = true
}
// 将传入列表复制为弹窗临时排序数据，避免直接修改父级状态
const init = list => {
  sortList.value = cloneDeep(list)
}

// 保存时向父组件回传新的排序列表
const emits = defineEmits(['save'])
// 关闭排序弹窗但不提交变更
const closeDialog = () => {
  dialogShow.value = false
}
// 提交当前排序并关闭弹窗
const save = () => {
  emits('save', unref(sortList))
  closeDialog()
}

defineExpose({
  sortInit
})
</script>

<style lang="less">
.custom-sort_filter {
  .drag-list {
    overflow: auto;
    max-height: 400px;
    .item-dimension {
      padding: 2px;
      margin: 2px;
      border: solid 1px #eee;
      text-align: left;
      color: #606266;
      background-color: white;
      display: flex;
      align-items: center;
    }

    .item-icon {
      cursor: move;
      margin: 0 2px;
    }

    .item-span {
      display: inline-block;
      width: 100%;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    .item-dimension + .item-dimension {
      margin-top: 6px;
    }

    .item-dimension:hover {
      color: #1890ff;
      background: #e8f4ff;
      border-color: #a3d3ff;
      cursor: pointer;
    }
  }
}
</style>
