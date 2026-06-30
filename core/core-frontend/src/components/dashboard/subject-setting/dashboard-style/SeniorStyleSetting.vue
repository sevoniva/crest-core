<template>
  <div style="width: 100%; padding-bottom: 8px">
    <el-form label-position="top" style="width: 100%">
      <div style="width: 100%; padding: 16px 8px 0">
        <el-row :gutter="8">
          <el-col :span="12">
            <el-form-item
              :effect="themes"
              class="form-item h-auto"
              :class="'form-item-' + themes"
              :label="t('components.jump_icon_color')"
            >
              <el-color-picker
                :effect="themes"
                size="small"
                v-model="seniorStyleSetting.linkageIconColor"
                :trigger-width="100"
                is-custom
                :predefine="state.predefineColors"
                @change="themeChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              :effect="themes"
              class="form-item h-auto"
              :class="'form-item-' + themes"
              :label="t('components.level_display_color')"
            >
              <el-color-picker
                v-model="seniorStyleSetting.drillLayerColor"
                :effect="themes"
                :trigger-width="100"
                size="small"
                is-custom
                :predefine="state.predefineColors"
                @change="themeChange"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, computed } from 'vue'
import { COLOR_PANEL } from '@/views/chart/components/editor/util/chart'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { useI18n } from '@/hooks/web/useI18n'
import { cloneDeep } from 'lodash-es'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import eventBus from '@/utils/eventBus'
const { t } = useI18n()
const dvMainStore = dvMainStoreWithOut()
const snapshotStore = snapshotStoreWithOut()
// 根据当前配置计算界面样式
const seniorStyleSetting = computed<any>(() => {
  return dvMainStore.canvasStyleData.component.seniorStyleSetting
})

defineProps({
  themes: {
    type: String,
    default: 'light'
  }
})
// 维护组件内部表单、弹窗和临时数据状态
const state = reactive({
  fontSize: [],
  isSetting: false,
  predefineColors: COLOR_PANEL
})

// 初始化组件状态和默认配置
const initForm = () => {
  // do
}
// 衔接当前组件交互和状态同步
const themeChange = () => {
  dvMainStore.canvasStyleData.component.seniorStyleSetting = cloneDeep(seniorStyleSetting.value)
  snapshotStore.recordSnapshotCache('seniorStyleSettingSimpleSelector-themeChange')
}

onMounted(() => {
  eventBus.on('onThemeColorChange', initForm)
})
</script>

<style scoped lang="less">
.hover-icon {
  &.active {
    color: var(--ed-color-primary) !important;
    background-color: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1));
  }
  & + & {
    margin-left: 8px;
  }
}
.m-divider {
  border-color: rgba(31, 35, 41, 0.15);
  margin: 0 0 8px;
}
.inner-collapse {
  :deep(.ed-collapse-item__header) {
    background-color: transparent !important;
  }
  :deep(.ed-collapse-item__header) {
    border: none;
  }
  :deep(.ed-collapse-item__wrap) {
    border: none;
  }
}
.ed-form-item {
  margin-bottom: 8px;

  :deep(.ed-form-item__label) {
    color: #646a73;
    font-size: 12px;
    font-weight: 400;
    line-height: 20px;
  }
}

.h-auto {
  :deep(.ed-form-item__label) {
    height: auto;
  }
}
.form-item-dark {
  :deep(.ed-form-item__label) {
    color: #6a6a6a;
    font-size: 12px;
    font-weight: 400;
    line-height: 20px;
  }
}
</style>
