<template>
  <div class="link-pwd-dialog-container">
    <el-dialog
      v-model="dialogVisible"
      :title="t('user.change_password')"
      width="420"
      :append-to-body="true"
      :before-close="handleClose"
    >
      <div class="link-pwd-container">
        <el-form ref="pwdForm" :model="state.form" :rules="rule" label-position="top">
          <el-form-item :label="t('system.new_password')" prop="pwd">
            <el-input v-model="state.form.pwd" :placeholder="t('commons.input_password')" />
            <div class="tips ed-form-item__error">
              {{ t('work_branch.password_hint') }}
            </div>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button secondary @click.stop="cancel">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" @click.stop="save">
            {{ t('common.save') }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'

const { t } = useI18n()
// 控制密码修改弹窗的显示状态
const dialogVisible = ref(false)
// 持有密码表单实例以便触发校验和重置
const pwdForm = ref()
// 保存打开弹窗时的原始密码，用于判断是否发生变更
const originPwd = ref('')
// 保存密码修改表单数据
const state = reactive({
  form: reactive<any>({
    pwd: ''
  })
})
// 定义分享链接密码的表单校验规则
const rule = reactive<any>({
  pwd: [
    { required: true, message: t('work_branch.password_null_hint'), trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!@#$%^&*()_+])[A-Za-z\d!@#$%^&*()_+]{4,10}$/,
      message: t('work_branch.password_hint'),
      trigger: 'blur'
    }
  ]
})

// 关闭弹窗前清空表单并执行外部关闭回调
const handleClose = done => {
  state.form.pwd = ''
  originPwd.value = ''
  pwdForm.value.resetFields()
  done()
}
// 取消编辑并重置弹窗状态
const cancel = () => {
  state.form.pwd = ''
  originPwd.value = ''
  pwdForm.value.resetFields()
  dialogVisible.value = false
}
// 声明密码变更时向父组件派发的事件
const emits = defineEmits(['pwdChange'])
// 校验密码表单并在密码变化时通知父组件
const save = () => {
  const formEl = pwdForm.value
  if (!formEl) return
  formEl.validate(valid => {
    if (valid) {
      if (originPwd.value !== state.form.pwd) {
        emits('pwdChange', state.form.pwd)
      }
      cancel()
    }
  })
}
// 打开弹窗并填充当前密码
const open = (pwd: string) => {
  state.form.pwd = pwd
  originPwd.value = pwd
  dialogVisible.value = true
}

defineExpose({
  open
})
</script>

<style lang="less" scoped>
.link-pwd-container {
  width: 100%;
  :deep(.ed-form-item) {
    margin-bottom: 2px;
    height: 108px;
  }
  :deep(.ed-form-item__label) {
    line-height: 22px !important;
    height: 22px;
  }
  :deep(.ed-form-item__error:not(.tips)) {
    display: none;
  }
  .tips {
    color: #8f959e;
    line-height: 22px;
    font-size: 14px;
    font-weight: 400;
  }
  :deep(.is-error) {
    .tips {
      color: red !important;
    }
  }
}
</style>
