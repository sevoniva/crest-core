<template>
  <el-dialog
    ref="canvasCacheDialogRef"
    :append-to-body="true"
    v-model="dialogShow"
    width="420px"
    :show-close="false"
    trigger="click"
    modal-class="canvasCacheDialog-modal"
    class="canvasCacheDialog"
  >
    <div style="display: flex; flex-direction: row; align-items: flex-start">
      <Icon name="warn-tree">
        <warnTree class="svg-icon warn-tree" />
      </Icon>
      <span class="tips">
        {{ dialogInfo.tips }}
      </span>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button size="mini" @click="doUseCache(false)">{{ t('visualization.no') }}</el-button>
        <el-button type="primary" size="mini" @click="doUseCache(true)"
          >{{ t('visualization.yes') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import warnTree from '@/assets/svg/warn-tree.svg'
import { ref } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
// 控制弹窗、面板或区域的展示状态
const dialogShow = ref(false)
const { t } = useI18n()
// 定义组件向父级抛出的事件
const emits = defineEmits(['doUseCache'])

const dialogInfo = {
  resourceId: null,
  title: '',
  tips: ''
}

// 控制弹窗、面板或区域的展示状态
const dialogInit = initInfo => {
  const canvasTypeName =
    initInfo.canvasType === 'dataV' ? t('work_branch.big_data_screen') : t('work_branch.dashboard')
  dialogInfo.resourceId = initInfo.resourceId
  dialogInfo.title = t('visualization.no_save_tips', [canvasTypeName])
  dialogInfo.tips = t('visualization.no_save_tips2')
  dialogShow.value = true
}

// 衔接当前组件交互和状态同步
const doUseCache = flag => {
  emits('doUseCache', flag)
  dialogShow.value = false
}

defineExpose({
  dialogInit
})
</script>

<style lang="less">
.canvasCacheDialog-modal {
  background: rgba(31, 35, 41, 0.4);
}
.canvasCacheDialog {
  .warn-tree {
    width: 24px;
    height: 24px;
  }
  .tips {
    margin-left: 16px;
    font-size: 16px;
    font-weight: 500;
    line-height: 24px;
  }
  .ed-dialog__header {
    display: none;
  }
  :deep(.ed-dialog__header) {
    display: none;
  }
}
</style>
