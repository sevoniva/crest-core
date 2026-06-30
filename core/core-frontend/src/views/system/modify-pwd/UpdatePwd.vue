<script lang="ts" setup>
import { ref, reactive } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { cloneDeep } from 'lodash-es'
import request from '@/config/axios'
import { rsaEncryp } from '@/utils/encryption'
import { ElMessage } from 'element-plus-secondary'
import { logoutHandler } from '@/utils/logout'
import { CustomPassword } from '@/components/custom-password'
import { isMobile } from '@/utils/utils'

// 修改密码表单的国际化文案函数
const { t } = useI18n()

// 修改密码表单默认值
const defaultForm = {
  pwd: '',
  newPwd: '',
  confirm: ''
}
// 当前修改密码表单数据
const pwdForm = reactive(cloneDeep(defaultForm))

// 校验新密码复杂度，并避免与原密码相同
const validatePwd = (_: any, value: any, callback: any) => {
  if (value === pwdForm.pwd) {
    callback(new Error(t('system.be_the_same')))
  }
  const pattern =
    /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[~!@#$%^&*()_+\-\={}|":<>?`[\];',.\/])[a-zA-Z0-9~!@#$%^&*()_+\-\={}|":<>?`[\];',.\/]{8,100}$/
  const regep = new RegExp(pattern)
  if (!regep.test(value)) {
    const msg = t('user.pwd_pattern_error')
    callback(new Error(msg))
  } else {
    callback()
  }
}

// 校验确认密码是否与新密码一致
const validateConfirmPwd = (_: any, value: any, callback: any) => {
  if (value !== pwdForm.newPwd) {
    callback(new Error(t('system.twice_are_inconsistent')))
  } else {
    callback()
  }
}

// 修改密码表单校验规则
const rule = {
  pwd: [
    {
      required: true,
      message: t('common.require'),
      trigger: 'blur'
    },
    {
      min: 6,
      max: 100,
      message: t('commons.input_limit', [6, 100]),
      trigger: 'blur'
    }
  ],
  newPwd: [
    {
      required: true,
      message: t('common.require'),
      trigger: 'blur'
    },
    { validator: validatePwd, trigger: 'blur' }
  ],
  confirm: [
    {
      required: true,
      message: t('common.require'),
      trigger: 'blur'
    },
    {
      min: 8,
      max: 100,
      message: t('commons.input_limit', [8, 100]),
      trigger: 'blur'
    },
    { validator: validateConfirmPwd, trigger: 'blur' }
  ]
}
// 表单实例引用，用于触发表单校验
const updatePwdForm = ref()

// 移动端修改成功后通知父级关闭或刷新
const emits = defineEmits(['success'])

// 提交修改密码请求，并按终端类型处理后续登录状态
const save = () => {
  updatePwdForm.value.validate(val => {
    if (val) {
      const pwd = rsaEncryp(pwdForm.pwd)
      const newPwd = rsaEncryp(pwdForm.newPwd)
      request.post({ url: '/user/modify-password', data: { pwd, newPwd } }).then(() => {
        if (isMobile()) {
          emits('success')
          return
        }

        ElMessage.success(t('system.log_in_again'))
        logoutHandler()
      })
    }
  })
}
</script>

<template>
  <el-form
    ref="updatePwdForm"
    require-asterisk-position="right"
    :model="pwdForm"
    :rules="rule"
    class="mt16"
    label-width="80px"
    label-position="top"
  >
    <el-form-item :label="t('system.original_password')" prop="pwd">
      <CustomPassword
        v-model="pwdForm.pwd"
        show-password
        type="password"
        :placeholder="t('system.the_original_password')"
      />
    </el-form-item>
    <el-form-item :label="t('system.new_password')" prop="newPwd">
      <CustomPassword
        v-model="pwdForm.newPwd"
        show-password
        type="password"
        :placeholder="t('system.the_new_password')"
      />
    </el-form-item>
    <el-form-item :label="t('system.confirm_password')" prop="confirm">
      <CustomPassword
        v-model="pwdForm.confirm"
        show-password
        type="password"
        :placeholder="t('system.the_confirmation_password')"
      />
    </el-form-item>
    <el-button @click="save" type="primary">
      {{ t('common.save') }}
    </el-button>
  </el-form>
</template>

<style lang="less" scoped>
.mt16 {
  max-width: 520px;
  margin-top: 0;
  .ed-form-item {
    margin-bottom: 18px;
    :deep(label) {
      line-height: 22px !important;
      color: #334155;
      font-weight: 600;
    }
  }

  :deep(.ed-input__wrapper) {
    height: 34px;
  }

  .ed-button {
    min-width: 86px;
    height: 34px;
  }
}
</style>
