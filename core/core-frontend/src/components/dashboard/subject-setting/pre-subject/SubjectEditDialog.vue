<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { deepCopy } from '@/utils/utils'
import CrestUpload from '@/components/visualization/common/CrestUpload.vue'
const { t } = useI18n()
// 控制主题编辑弹窗的加载状态
const loading = ref(false)

const rules = {
  name: [
    {
      required: true,
      message: t('commons.input_content'),
      trigger: 'change'
    },
    {
      max: 50,
      message: t('commons.char_can_not_more_50'),
      trigger: 'change'
    }
  ],
  coverUrl: [
    {
      required: true,
      message: t('components.upload_a_cover'),
      trigger: 'change'
    }
  ]
}
// 保存表单实例，用于校验和重置
const subject = ref()
// 控制主题编辑弹窗显示状态
const subjectDialogShow = ref(false)
// 标记当前操作是新建还是编辑
const optType = ref('new')

// 重置表单校验并关闭弹窗
const resetForm = () => {
  subject.value.clearValidate()
  subjectDialogShow.value = false
}
// 保存主题表单数据
const subjectForm = ref(null)
// 根据操作类型生成弹窗标题
const title = computed(() =>
  optType.value === 'new' ? t('components.a_new_theme') : t('components.edit_theme')
)

// 初始化主题编辑弹窗
const optInit = (subjectItem, opt) => {
  optType.value = opt
  subjectDialogShow.value = true
  subjectForm.value = deepCopy(subjectItem)
}

// 校验主题表单并提交保存事件
const saveSubject = () => {
  subject.value.validate(result => {
    if (result) {
      emits('finish', subjectForm.value)
    }
  })
}

defineExpose({
  optInit,
  resetForm
})

// 声明主题保存完成事件
const emits = defineEmits(['finish'])
// 回填封面图片上传结果
const onImgChange = imgUrl => {
  subjectForm.value.coverUrl = imgUrl
}
</script>

<template>
  <el-dialog
    v-loading="loading"
    :title="title"
    v-model="subjectDialogShow"
    width="400px"
    :before-close="resetForm"
  >
    <el-form
      label-position="top"
      require-asterisk-position="right"
      ref="subject"
      :model="subjectForm"
      :rules="rules"
    >
      <el-form-item class="form-item" prop="name">
        <template #label>
          <label class="m-label"> {{ t('common.name') }} </label>
        </template>
        <el-input v-model="subjectForm.name" />
      </el-form-item>
      <el-form-item class="form-item" prop="coverUrl">
        <template #label>
          <label class="m-label"> {{ t('components.cover') }} </label>
        </template>
        <CrestUpload themes="light" :img-url="subjectForm.coverUrl" @onImgChange="onImgChange" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button secondary @click="resetForm()">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="saveSubject()">{{ t('chart.confirm') }}</el-button>
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
