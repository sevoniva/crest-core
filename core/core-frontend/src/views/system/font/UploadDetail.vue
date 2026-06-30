<script lang="ts" setup>
import icon_upload_outlined from '@/assets/svg/icon_upload_outlined.svg'
import { ref, reactive } from 'vue'
import { uploadFontFile } from '@/api/font'
import { useI18n } from '@/hooks/web/useI18n'
import FontInfo from './FontInfo.vue'
import { ElMessage } from 'element-plus-secondary'
import { edit } from '@/api/font'
import { cloneDeep } from 'lodash-es'

// 维护上传文件状态
const state = reactive({
  fileList: null
})
// 字体文件上传加载态
const loading = ref(false)
// 上传组件实例
const upload = ref()
const { t } = useI18n()
// 上传字体文件并回填字体信息
const uploadExcel = () => {
  const formData = new FormData()
  formData.append('file', state.fileList.raw)
  loading.value = true
  return uploadFontFile(formData)
    .then(res => {
      ruleForm.name = res.data.name
      ruleForm.size = res.data.size
      ruleForm.sizeType = res.data.sizeType
      ruleForm.fileTransName = res.data.fileTransName
      ruleForm.fileName = state.fileList.raw.name
      upload.value?.clearFiles()
    })
    .catch(error => {
      if (error.code === 'ECONNABORTED') {
        ElMessage({
          type: 'error',
          message: error.message,
          showClose: true
        })
      }
    })
    .finally(() => {
      loading.value = false
    })
}
// 当前弹窗标题
const dialogTitle = ref('')
// 控制字体上传弹窗显隐
const dialogVisible = ref(false)
// 当前字体操作类型
const action = ref('')
const defaultForm = {
  id: null,
  name: '',
  fileName: '',
  fileTransName: '',
  size: 0,
  sizeType: '',
  isDefault: 0,
  isBuiltin: 0,
  updateTime: 0
}
// 字体表单数据
const ruleForm = reactive(cloneDeep(defaultForm))

// 初始化字体上传或编辑弹窗
const init = (val, type, item) => {
  dialogTitle.value = val || t('system.add_font')
  action.value = type
  dialogVisible.value = true
  Object.assign(ruleForm, cloneDeep(defaultForm))
  Object.assign(ruleForm, JSON.parse(JSON.stringify(item)))
}

// 清空已选择的字体文件
const fontDel = () => {
  state.fileList = null
}

// 字体表单实例
const ruleFormRef = ref()
const rules = {
  name: [
    { required: true, message: t('system.the_font_name'), trigger: 'blur' },
    { min: 1, max: 50, message: t('system.character_length_1_50'), trigger: 'blur' }
  ]
}
defineExpose({
  init
})

// 上传前校验字体文件格式
const beforeAvatarUpload = rawFile => {
  if (!rawFile.name.toLocaleLowerCase().endsWith('.ttf')) {
    ElMessage.error(t('system.in_ttf_format'))
    return false
  }
  return true
}
// 选择字体文件后同步上传状态
const onChange = file => {
  if (file.raw?.name?.toLocaleLowerCase().endsWith('.ttf')) {
    state.fileList = file
  }
}

// 处理上传失败响应
const uploadFail = response => {
  let myError = response.toString()
  myError.replace('Error: ', '')
}

// 定义保存完成事件
const emits = defineEmits(['finish'])
// 取消并重置字体表单
const cancel = () => {
  Object.assign(ruleForm, cloneDeep(defaultForm))
  ruleFormRef.value.clearValidate()
  state.fileList = null
  dialogVisible.value = false
}
// 校验并保存字体配置
const confirm = () => {
  ruleFormRef.value.validate(val => {
    if (val) {
      if (action.value === 'uploadFile') {
        if (ruleForm.fileTransName === '') {
          ElMessage.error(t('system.upload_font_file_required'))
          return
        }
      }
      edit(ruleForm).then(() => {
        ElMessage.success(dialogTitle.value + t('data_set.success'))
        cancel()
        emits('finish')
      })
    }
  })
}
</script>

<template>
  <el-dialog
    class="create-dialog add-form_font_dialog"
    v-model="dialogVisible"
    :before-close="cancel"
    :title="dialogTitle"
    width="420"
  >
    <el-form
      @submit.prevent
      ref="ruleFormRef"
      :model="ruleForm"
      label-position="top"
      :rules="rules"
      label-width="auto"
      class="demo-ruleForm"
    >
      <el-form-item v-if="action !== 'uploadFile'" :label="t('system.font_name')" prop="name">
        <el-input :placeholder="t('system.the_font_name')" v-model.trim="ruleForm.name" />
      </el-form-item>
      <el-form-item
        v-loading="loading"
        v-if="action !== 'rename'"
        :label="t('system.font_file_label')"
      >
        <el-upload
          action=""
          :multiple="false"
          ref="uploadAgain"
          :show-file-list="false"
          accept=".ttf"
          :on-change="onChange"
          :before-upload="beforeAvatarUpload"
          :http-request="uploadExcel"
          :on-error="uploadFail"
          name="file"
          v-show="!state.fileList"
        >
          <template #trigger>
            <el-button secondary>
              <template #icon>
                <Icon name="icon_upload_outlined"><icon_upload_outlined class="svg-icon" /></Icon>
              </template>
              {{ t('system.upload_font_file') }}
            </el-button>
          </template>
        </el-upload>
        <FontInfo
          @del="fontDel"
          v-show="state.fileList"
          :size="ruleForm.size + ' ' + ruleForm.sizeType"
          :name="ruleForm.fileName"
        ></FontInfo>
        <el-upload
          action=""
          :multiple="false"
          ref="uploadAgain"
          :before-upload="beforeAvatarUpload"
          :show-file-list="false"
          accept=".ttf"
          :on-change="onChange"
          :http-request="uploadExcel"
          :on-error="uploadFail"
          name="file"
          v-show="state.fileList"
        >
          <template #trigger>
            <el-button text> {{ t('data_source.reupload') }} </el-button>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="cancel">{{ t('userimport.cancel') }}</el-button>
        <el-button v-loading="loading" type="primary" @click="confirm">
          {{ t('userimport.sure') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="less">
.add-form_font_dialog {
  .ed-dialog__footer {
    border: none;
    padding-top: 0;
    margin-top: -2px;
  }
}
</style>
