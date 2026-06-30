<script lang="ts" setup>
import icon_add_outlined from '@/assets/svg/icon_add_outlined.svg'
import icon_deleteTrash_outlined from '@/assets/svg/icon_delete-trash_outlined.svg'
import { guid } from '@/views/visualized/data/dataset/form/util.js'
import { useI18n } from '@/hooks/web/useI18n'
import { ElMessage } from 'element-plus-secondary'
import { ref, shallowRef, computed } from 'vue'
import { cloneDeep } from 'lodash-es'

// 控制树形字段配置弹窗的显示状态
const dialogVisible = ref(false)
// 保存当前配置的级联字段列表
const treeList = ref([])
// 保存可选的数据集字段副本
const datasetMap = shallowRef([])
// 声明保存树形字段配置的事件
const emits = defineEmits(['saveTree'])
const { t } = useI18n()
// 新增一个待选择字段的树形层级
const addCascadeItem = () => {
  treeList.value.push({
    field: {
      id: ''
    },
    id: guid()
  })
}
// 关闭树形字段配置弹窗
const handleBeforeClose = () => {
  dialogVisible.value = false
}
// 初始化可选字段和已配置层级，并打开弹窗
const init = (arr, treeArr) => {
  datasetMap.value = cloneDeep(arr)
  treeList.value = cloneDeep(treeArr).map(ele => ({ field: ele, id: ele.id }))
  dialogVisible.value = true
}

// 计算已被当前层级占用的字段，避免重复选择
const disableFieldArr = computed(() => {
  return treeList.value.map(ele => ele.field).map(ele => ele.id)
})

// 取消配置并关闭弹窗
const cancelClick = () => {
  handleBeforeClose()
}
// 将弹窗中的层级配置转换为保存结构
const setCascadeArrBack = () => {
  let isError = false
  const arr = cloneDeep(treeList.value).map(item => {
    if (!item.field?.id) {
      isError = true
    }
    return item.field
  })

  return {
    arr,
    isError
  }
}
// 校验并提交树形字段配置
const confirmClick = () => {
  const { isError, arr } = setCascadeArrBack()
  if (isError) {
    ElMessage.error(t('v_query.select_a_field'))
    return
  }
  emits('saveTree', arr)
  handleBeforeClose()
}
const indexNumCascade = [
  t('visualization.number1'),
  t('visualization.number2'),
  t('visualization.number3'),
  t('visualization.number4'),
  t('visualization.number5')
]

// 删除指定层级的树形字段配置
const deleteCascade = idx => {
  treeList.value.splice(idx, 1)
}

defineExpose({
  init
})
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    width="600"
    modal-class="tree-field_dialog"
    :before-close="handleBeforeClose"
    @mousedown.stop
    @mousedup.stop
    :title="t('v_query.tree_structure_design')"
  >
    <div class="cascade-content">
      <div style="display: flex; align-items: center; justify-content: space-between">
        <el-button :disabled="treeList.length === 5" text @click="addCascadeItem">
          <template #icon>
            <Icon name="icon_add_outlined"><icon_add_outlined class="svg-icon" /></Icon>
          </template>
          {{ t('v_query.add_level') }}
        </el-button>
      </div>
      <div class="cascade-item">
        <div class="label">{{ t('visualization.level') }}</div>
        <div class="item-name">{{ t('v_query.tree_query_field') }}</div>
      </div>
      <div class="cascade-item" v-for="(ele, idx) in treeList" :key="ele.id">
        <div class="label">{{ t('visualization.level') }}{{ indexNumCascade[idx] }}</div>
        <div class="item-name">
          <el-select value-key="id" v-model="ele.field" style="width: 300px">
            <el-option
              :disabled="disableFieldArr.includes(item.id)"
              v-for="item in datasetMap"
              :key="item.id"
              :label="item.name"
              :value="item"
            />
          </el-select>
        </div>
        <el-button @click="deleteCascade(idx)" class="cascade-delete" text>
          <template #icon>
            <Icon name="icon_delete-trash_outlined"
              ><icon_deleteTrash_outlined class="svg-icon"
            /></Icon>
          </template>
        </el-button>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button secondary @click="cancelClick">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="confirmClick"> {{ t('pblink.sure_bt') }} </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="less">
.tree-field_dialog {
  .cascade-content {
    box-shadow: 0px 0px 12px rgba(0, 0, 0, 0.12);
    border: 1px solid #e4e7ed;
    padding: 24px;
    padding-top: 8px;
    margin-top: 8px;

    .cascade-item {
      display: flex;
      align-items: center;
      width: 100%;
      height: 40px;
      .label {
        width: 100px;
      }

      .item-name {
        width: 300px;
      }
    }

    .cascade-delete-block,
    .cascade-delete {
      width: 40px;
      font-size: 20px;
      color: #646a73;
      margin-left: 20px;
    }
  }
}
</style>
