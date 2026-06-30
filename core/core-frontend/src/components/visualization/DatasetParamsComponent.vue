<script lang="ts" setup>
import { computed, reactive, ref } from 'vue'
import { deepCopy } from '@/utils/utils'
import { useEmitt } from '@/hooks/web/useEmitt'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus-secondary'
// 参数弹窗加载状态
const loading = ref(false)
// 参数表单实例
const subject = ref()
// 参数弹窗显隐状态
const subjectDialogShow = ref(false)
const dvMainStore = dvMainStoreWithOut()
const { canvasViewInfo } = storeToRefs(dvMainStore)

// 当前参数弹窗状态
const state = reactive({
  viewId: null
})
// 当前数据集参数列表
const curDataSetParamsInfo = ref([])

// 重置参数表单并关闭弹窗
const resetForm = () => {
  subject.value.clearValidate()
  subjectDialogShow.value = false
}
// 参数表单模型
const subjectForm = ref(null)
// 参数表单校验规则
const rules = ref({})

// 初始化参数弹窗数据
const optInit = item => {
  if (item) {
    state.viewId = item.id
    const chartInfo = canvasViewInfo.value[state.viewId]
    curDataSetParamsInfo.value = deepCopy(chartInfo.calParams)
    subjectDialogShow.value = true
  }
}

// 保存数据集计算参数
const saveSubject = () => {
  if (disabledCheck.value) {
    ElMessage.error('请输入正确参数')
    return
  }
  canvasViewInfo.value[state.viewId]['calParams'] = curDataSetParamsInfo.value
  useEmitt().emitter.emit('calcData-' + state.viewId, canvasViewInfo.value[state.viewId])
  resetForm()
}

// 判断参数是否存在未填写项
const disabledCheck = computed(() => {
  return (
    !curDataSetParamsInfo.value ||
    (curDataSetParamsInfo.value &&
      curDataSetParamsInfo.value.filter(item => item.value === null || item.value === undefined)
        .length > 0)
  )
})

// 判断参数弹窗是否打开
const statesCheck = () => {
  return subjectDialogShow.value
}

// 阻止弹窗点击事件冒泡
const handleDialogClick = e => {
  e.preventDefault()
  e.stopPropagation()
}

defineExpose({
  optInit,
  statesCheck,
  resetForm
})
</script>

<template>
  <el-dialog
    title="计算参数输入"
    v-model="subjectDialogShow"
    width="400px"
    :before-close="resetForm"
    @click="handleDialogClick"
  >
    <div v-loading="loading">
      <el-form
        v-if="subjectDialogShow"
        label-position="top"
        ref="subject"
        :model="subjectForm"
        :rules="rules"
        @submit.prevent
      >
        <el-form-item
          v-for="paramsItem in curDataSetParamsInfo"
          :key="paramsItem"
          class="form-item"
          :prop="'value_' + paramsItem.name"
        >
          <template #label>
            <label class="m-label">
              计算字段[{{ paramsItem.name }}] <span style="color: red">*</span>
            </label>
          </template>
          <el-input-number
            style="width: 100%"
            v-model="paramsItem.value"
            placeholder="请输入一个数字"
            controls-position="right"
          />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button secondary @click="resetForm()">取消</el-button>
      <el-button type="primary" @click="saveSubject()">确认</el-button>
    </template>
  </el-dialog>
</template>

<style lang="less" scoped>
:deep(.ed-dialog__header) {
  text-align: left;
}
.m-label {
  color: #1f2329;
  font-size: 14px;
  font-style: normal;
  font-weight: 400;
  line-height: 14px;
  display: inline-block;
}
.form-item {
  margin-bottom: 16px;
  :deep(.ed-form-item__label) {
    line-height: 14px !important;
  }

  :deep(.ed-input__inner) {
    font-size: 14px !important;
  }

  :deep(.ed-form-item__error) {
    top: 88%;
  }
  .ed-input {
    --ed-input-height: 32px !important;
  }

  &:last-child {
    margin-bottom: 0;
  }

  :deep(.avatar-uploader-container) {
    margin-bottom: 0px;
  }
}
</style>
