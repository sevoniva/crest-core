<script setup lang="tsx">
import icon_info_outlined from '@/assets/svg/icon_info_outlined.svg'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { storeToRefs } from 'pinia'
import { nextTick, onMounted, ref } from 'vue'
import { ElFormItem, ElIcon } from 'element-plus-secondary'

import { merge, cloneDeep } from 'lodash-es'
import { useEmitt } from '@/hooks/web/useEmitt'
import ComponentColorSelector from '@/components/dashboard/subject-setting/dashboard-style/ComponentColorSelector.vue'
import OverallSetting from '@/components/dashboard/subject-setting/dashboard-style/OverallSetting.vue'
import CanvasBackground from '@/components/visualization/component-background/CanvasBackground.vue'
import SeniorStyleSetting from '@/components/dashboard/subject-setting/dashboard-style/SeniorStyleSetting.vue'
import Icon from '../icon-custom/src/Icon.vue'
import CanvasBaseSetting from '@/components/visualization/CanvasBaseSetting.vue'
import { useI18n } from '@/hooks/web/useI18n'
import ValueFormatterSetting from '@/components/dashboard/subject-setting/dashboard-style/ValueFormatterSetting.vue'
import { formatterViewInfo } from '@/views/chart/components/js/formatter'
const dvMainStore = dvMainStoreWithOut()
const snapshotStore = snapshotStoreWithOut()
const { canvasStyleData, canvasViewInfo } = storeToRefs(dvMainStore)
// 画布属性面板直接操作画布级配置，并负责把全局样式同步到已存在的图表。
// 初始化完成前不向已有图表批量写入主题属性，避免面板挂载过程覆盖画布初始状态。
let canvasAttrInit = false

// 默认展开尺寸、基础、背景和配色，覆盖创建大屏时最常调整的画布配置。
const canvasAttrActiveNames = ref(['size', 'baseSetting', 'background', 'color'])
const { t } = useI18n()
// 适配方式仅在预览态生效，编辑态仍按画布实际尺寸计算组件坐标。
const screenAdaptorList = [
  { label: t('visualization.screen_adaptor_width_first'), value: 'widthFirst' },
  { label: t('visualization.screen_adaptor_height_first'), value: 'heightFirst' },
  { label: t('visualization.screen_adaptor_full'), value: 'full' },
  { label: t('visualization.screen_adaptor_keep'), value: 'keep' },
  { label: t('visualization.screen_adaptor_keep_proportion'), value: 'keepProportion' }
]
// 延后一帧标记初始化完成，确保 collapse 子组件完成默认值写入后再允许批量同步。
const init = () => {
  nextTick(() => {
    canvasAttrInit = true
  })
}

// 全局数值格式化配置会应用到所有图表，用于统一大屏内指标单位和千分位展示。
const onFormatterItemChange = val => {
  themeAttrChange('formatterCfg', 'formatterCfg', val)
}

// 全局配色配置会批量合并到图表 customAttr，单图表专属字段在合并时保留。
const onColorChange = val => {
  themeAttrChange('customAttr', 'color', val)
}

// 画布样式变更只需要重新渲染图表，不改变数据查询条件。
const onStyleChange = () => {
  snapshotStore.recordSnapshotCache('renderChart')
}

// 尺寸和适配方式变化后需要刷新滚动容器，保证画布滚动条和缩放区域同步。
const onBaseChange = () => {
  snapshotStore.recordSnapshotCache('renderChart')
  useEmitt().emitter.emit('initScroll')
}

// 批量主题变更会遍历当前画布视图并触发对应图表重绘，富文本额外触发数据计算保持内容同步。
const themeAttrChange = (custom, property, value) => {
  if (canvasAttrInit) {
    Object.keys(canvasViewInfo.value).forEach(function (viewId) {
      try {
        const viewInfo = canvasViewInfo.value[viewId]
        if (custom === 'formatterCfg') {
          // 数值格式化需要通过专用方法写入不同图表的 formatter 配置。
          formatterViewInfo(viewInfo, value)
        } else if (custom === 'customAttr') {
          if (viewInfo.type === 'flow-map') {
            // 流向图 misc 中包含线路和点位专属配置，批量主题色只覆盖通用字段，保留图表个性化参数。
            const { customAttr } = viewInfo
            const tmpValue = cloneDeep(value)
            const miscObj = cloneDeep(customAttr.misc)
            for (const key in miscObj) {
              if (miscObj.hasOwnProperty(key) && tmpValue.misc?.[key] !== undefined) {
                tmpValue.misc[key] = miscObj[key]
              }
            }
            merge(viewInfo['customAttr'], tmpValue)
          } else {
            // 普通图表直接深度合并全局配色和样式默认值。
            merge(viewInfo['customAttr'], value)
          }
        } else {
          // 非 customAttr 配置按指定 property 局部覆盖，避免丢失其他画布属性。
          Object.keys(value).forEach(function (key) {
            if (viewInfo[custom][property][key] !== undefined) {
              viewInfo[custom][property][key] = value[key]
            }
          })
        }
        useEmitt().emitter.emit('renderChart-' + viewId, viewInfo)
        if (viewInfo.type === 'rich-text') {
          // 富文本渲染内容可能引用全局格式化结果，重绘后还需要重新计算文本数据。
          useEmitt().emitter.emit('calcData-' + viewId, viewInfo)
        }
      } catch (e) {
        console.warn('themeAttrChange-error')
      }
    })
    snapshotStore.recordSnapshotCache('renderChart')
  }
}

onMounted(() => {
  init()
})
</script>

<template>
  <div class="attr-container crest-collapse-style">
    <el-collapse v-model="canvasAttrActiveNames">
      <el-collapse-item effect="dark" :title="t('visualization.size')" name="size">
        <el-form label-position="left" :label-width="14">
          <el-row :gutter="8" class="m-size">
            <el-col :span="12">
              <el-form-item class="form-item form-item-dark" label="W">
                <el-input-number
                  effect="dark"
                  size="small"
                  :min="100"
                  :max="50000"
                  v-model="canvasStyleData.width"
                  @change="onBaseChange"
                  controls-position="right"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item class="form-item form-item-dark" label="H">
                <el-input-number
                  effect="dark"
                  size="small"
                  :min="100"
                  :max="50000"
                  v-model="canvasStyleData.height"
                  @change="onBaseChange"
                  controls-position="right"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row v-if="canvasStyleData.screenAdaptor">
            <el-form-item style="margin: 8px 0 16px">
              <span class="form-item-scroll"> {{ t('visualization.screen_adaptor') }} </span>
              <el-tooltip class="item" effect="dark" placement="top">
                <template #content>
                  <div>{{ t('visualization.effective_during_preview') }}</div>
                </template>
                <el-icon class="hint-icon--dark">
                  <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
                </el-icon>
              </el-tooltip>
              <el-select
                style="width: 139px; margin: 0 0 0 8px; flex: 1"
                effect="dark"
                v-model="canvasStyleData.screenAdaptor"
                @change="onStyleChange"
                size="small"
              >
                <el-option
                  v-for="option in screenAdaptorList"
                  size="small"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
          </el-row>
        </el-form>
      </el-collapse-item>
      <el-collapse-item effect="dark" :title="t('visualization.base_config')" name="baseSetting">
        <canvas-base-setting themes="dark"></canvas-base-setting>
      </el-collapse-item>
      <el-collapse-item effect="dark" :title="t('visualization.background')" name="background">
        <canvas-background themes="dark"></canvas-background>
      </el-collapse-item>
      <el-collapse-item
        effect="dark"
        :title="t('visualization.color_config')"
        name="color"
        class="no-padding no-border-bottom"
      >
        <component-color-selector themes="dark" @onColorChange="onColorChange" />
      </el-collapse-item>
      <el-collapse-item
        effect="dark"
        :title="t('visualization.refresh_config')"
        name="overallSetting"
      >
        <overall-setting style="padding-bottom: 8px" themes="dark" />
      </el-collapse-item>
      <el-collapse-item
        effect="dark"
        :title="t('visualization.number_formatter')"
        name="formatterItem"
      >
        <ValueFormatterSetting
          :formatter-cfg="canvasStyleData.component.formatterItem"
          themes="dark"
          @onFormatterItemChange="onFormatterItemChange"
        ></ValueFormatterSetting>
      </el-collapse-item>
      <el-collapse-item
        effect="dark"
        :title="t('visualization.advanced_style_settings')"
        name="seniorStyleSetting"
        class="no-padding no-border-bottom"
      >
        <senior-style-setting themes="dark"></senior-style-setting>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style lang="less" scoped>
.form-item-scroll {
  font-size: 12px;
  color: @canvas-main-font-color-dark;
}
:deep(.ed-collapse-item) {
  &:first-child {
    .ed-collapse-item__header {
      border-top: none;
    }
  }
}

.crest-collapse-style {
  :deep(.ed-collapse-item__header) {
    height: 36px !important;
    line-height: 36px !important;
    font-size: 12px !important;
    padding: 0 !important;
    font-weight: 500 !important;

    .ed-collapse-item__arrow {
      margin: 0 6px 0 8px;
    }
  }
  :deep(.ed-collapse-item__content) {
    padding: 16px 8px 0;
    border: none;
  }
  :deep(.ed-form-item) {
    display: block;
    margin-bottom: 8px;
  }
  :deep(.ed-form-item__label) {
    justify-content: flex-start;
  }
}

.item-show {
  display: flex;
  text-align: center;
  padding-top: 18px;
  min-width: 220px;
}

.attr-container {
  // 画布属性面板固定使用深色底，和编辑器右侧配置区保持一致。
  background-color: rgba(37, 45, 54, 1);
  color: #fff;
  z-index: 20;
  height: 100%;
  width: 100%;
  min-width: 220px;
}

:deep(.ed-collapse-item__wrap) {
  border-bottom: none;
}
:deep(.ed-collapse) {
  // 折叠面板需要占满右侧属性容器，避免不同设置项切换时宽度抖动。
  width: 100%;
}

.disabled :deep(.el-upload--picture-card) {
  display: none;
}

.avatar-uploader :deep(.ed-upload) {
  width: 80px;
  height: 80px;
  line-height: 90px;
}

.avatar-uploader :deep(.ed-upload-list li) {
  width: 80px !important;
  height: 80px !important;
}
.avatar-uploader :deep(.ed-upload--picture-card) {
  background: rgba(0, 0, 0, 0);
}
.img-area {
  width: 80px;
  height: 80px;
  margin-top: 0px;
  margin-bottom: 20px;
  overflow: hidden;
}

.color-picker-style {
  cursor: pointer;
  z-index: 1003;
}

.color-label {
  display: inline-block;
  width: 60px;
}

.color-type :deep(.ed-radio__input) {
  display: none;
}

.ed-radio {
  color: #757575;
}

.custom-color-style :deep(.ed-radio) {
  margin: 0 2px 0 0 !important;
  border: 1px solid transparent;
}

.custom-color-style :deep(.ed-radio__label) {
  padding-left: 0;
}

.custom-color-style :deep(.ed-radio.is-checked) {
  border: 1px solid #0a7be0;
}

.image-hint {
  color: #8f959e;
  size: 14px;
  line-height: 22px;
  font-weight: 400;
  margin-top: 2px;
}

.re-update-span {
  cursor: pointer;
  color: var(--ed-color-primary);
  size: 14px;
  line-height: 22px;
  font-weight: 400;
}

.m-size {
  // 尺寸输入项采用左右并排布局，覆盖默认表单项的块级排列。
  :deep(.ed-form-item) {
    display: flex !important;
  }
}

:deep(.ed-form-item) {
  // 属性面板内控件统一使用紧凑尺寸，减少大屏编辑器右侧栏的滚动成本。
  .ed-radio.ed-radio--small .ed-radio__inner {
    width: 14px;
    height: 14px;
  }
  .ed-input__inner {
    font-size: 12px;
    font-weight: 400;
  }
  .ed-input {
    --ed-input-height: 28px;

    .ed-input__suffix {
      height: 26px;
    }
  }
  .ed-input-number {
    width: 100%;

    .ed-input-number__decrease {
      --ed-input-number-controls-height: 13px;
    }
    .ed-input-number__increase {
      --ed-input-number-controls-height: 13px;
    }

    .ed-input__inner {
      text-align: start;
    }
  }
  .ed-select {
    width: 100%;
    .ed-input__inner {
      height: 26px !important;
    }
  }
  .ed-checkbox {
    .ed-checkbox__label {
      font-size: 12px;
    }
  }
  .ed-color-picker {
    .ed-color-picker__mask {
      height: 26px;
      width: calc(100% - 2px) !important;
    }
  }
  .ed-radio {
    height: 20px;
    .ed-radio__label {
      font-size: 12px;
      font-style: normal;
      font-weight: 400;
      line-height: 20px;
    }
  }

  &.margin-bottom-8 {
    margin-bottom: 8px;
  }
}
:deep(.ed-checkbox__label) {
  color: #1f2329;
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
  line-height: 20px;
}
:deep(.ed-checkbox--dark) {
  .ed-checkbox__label {
    color: @dv-canvas-main-font-color;
  }
}

:deep(.ed-form-item__label) {
  color: @canvas-main-font-color;
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
}
:deep(.form-item-dark) {
  .ed-form-item__label {
    color: @canvas-main-font-color-dark;
  }
}
.no-padding {
  :deep(.ed-collapse-item__content) {
    padding: 0 !important;
  }
}
.no-border-bottom {
  :deep(.ed-collapse-item__wrap) {
    border-bottom: none;
  }
}

.hint-icon--dark {
  color: #a6a6a6;
}
</style>
