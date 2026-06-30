<template>
  <div class="ticket-dialog-container">
    <el-dialog
      v-model="dialogVisible"
      :title="t('user.change_password')"
      width="600"
      class="is-ticket-dialog hidden-footer"
      :append-to-body="true"
      :before-close="handleClose"
    >
      <slot />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'

const { t } = useI18n()
// 控制弹窗、面板或区域的展示状态
const dialogVisible = ref(false)

// 处理界面事件并同步业务状态
const handleClose = done => {
  done()
}
// 打开对应弹窗或操作入口
const open = () => {
  dialogVisible.value = true
}
// 关闭对应弹窗并清理临时状态
const close = () => {
  dialogVisible.value = false
}
defineExpose({
  open,
  close
})
</script>
<style lang="less" scoped>
.ticket-dialog-container {
  :deep(.is-ticket-dialog) {
    .ed-dialog__header {
      display: none;
    }
  }
}
</style>
