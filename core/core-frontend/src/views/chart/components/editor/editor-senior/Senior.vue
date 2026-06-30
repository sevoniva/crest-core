<script lang="ts" setup>
import icon_deleteTrash_outlined from '@/assets/svg/icon_delete-trash_outlined.svg'
import icon_edit_outlined from '@/assets/svg/icon_edit_outlined.svg'
import { useI18n } from '@/hooks/web/useI18n'
import FunctionCfg from '@/views/chart/components/editor/editor-senior/components/FunctionCfg.vue'
import ScrollCfg from '@/views/chart/components/editor/editor-senior/components/ScrollCfg.vue'
import AssistLine from '@/views/chart/components/editor/editor-senior/components/AssistLine.vue'
import Threshold from '@/views/chart/components/editor/editor-senior/components/Threshold.vue'
import CollapseSwitchItem from '@/components/collapse-switch-item/src/CollapseSwitchItem.vue'
import { useAppStoreWithOut } from '@/store/modules/app'
import { computed, PropType, ref, toRefs, watch } from 'vue'
import LinkJumpSet from '@/components/visualization/LinkJumpSet.vue'
import LinkageSet from '@/components/visualization/LinkageSet.vue'
import { canvasSave } from '@/utils/canvasUtils'
import {
  queryVisualizationJumpInfo,
  deleteJumpSet,
  updateJumpSetActive
} from '@/api/visualization/linkJump'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import {
  getPanelAllLinkageInfo,
  deleteLinkage,
  updateLinkageActive
} from '@/api/visualization/linkage'
import { includesAny } from '../util/StringUtils'
import { ElCollapseItem, ElIcon, ElMessage } from 'element-plus-secondary'
import { storeToRefs } from 'pinia'
import { BASE_VIEW_CONFIG } from '../util/chart'
import { cloneDeep, defaultsDeep } from 'lodash-es'
import BubbleAnimateCfg from '@/views/chart/components/editor/editor-senior/components/BubbleAnimateCfg.vue'
import CarouselSetting from '@/custom-component/common/CarouselSetting.vue'
import { Icon } from 'vant'
import CommonEvent from '@/custom-component/common/CommonEvent.vue'
const dvMainStore = dvMainStoreWithOut()

const { nowPanelTrackInfo, nowPanelJumpInfo, dvInfo, curComponent, batchOptStatus } =
  storeToRefs(dvMainStore)

const { t } = useI18n()
// 跳转配置弹窗实例
const linkJumpRef = ref(null)
// 联动配置弹窗实例
const linkageRef = ref(null)

// 高级设置折叠面板展开状态
const state = {
  attrActiveNames: [],
  styleActiveNames: []
}

// 向父级同步高级配置变更
const emit = defineEmits([
  'onFunctionCfgChange',
  'onAssistLineChange',
  'onScrollCfgChange',
  'onThresholdChange',
  'onBubbleAnimateChange'
])

// 接收图表、高级设置属性和事件上下文
const props = defineProps({
  chart: {
    type: Object as PropType<ChartObj>,
    required: true
  },
  quotaData: {
    type: Array as PropType<Array<Record<string, any>>>,
    required: true
  },
  quotaExtData: {
    type: Array as PropType<Array<Record<string, any>>>,
    required: true
  },
  fieldsData: {
    type: Array as PropType<Array<Record<string, any>>>,
    required: true
  },
  themes: {
    type: String as PropType<EditorTheme>,
    default: 'dark'
  },
  properties: {
    type: Array as PropType<EditorProperty[]>,
    required: false,
    default: () => {
      return []
    }
  },
  propertyInnerAll: {
    type: Object as PropType<EditorPropertyInner>,
    required: false,
    default: () => {
      return {}
    }
  },
  eventInfo: {
    type: Object,
    required: false
  }
})

const { chart, themes, properties, propertyInnerAll } = toRefs(props)
// 补齐图表高级配置默认结构
watch(
  () => chart.value?.senior,
  () => {
    chart.value.senior = defaultsDeep(chart.value?.senior || {}, cloneDeep(BASE_VIEW_CONFIG.senior))
  }
)
// 统计当前图表已配置的联动和跳转数量
const seniorCounts = computed(() => {
  let linkageCount = 0
  let jumpCount = 0
  props.fieldsData?.forEach(item => {
    const sourceInfo = props.chart.id + '#' + item.id
    if (nowPanelTrackInfo.value[sourceInfo]) {
      linkageCount++
    }
    if (nowPanelJumpInfo.value[sourceInfo]) {
      jumpCount++
    }
  })

  return {
    linkageCount,
    jumpCount
  }
})

// 判断通用事件配置是否需要显示
const eventsShow = computed(() => {
  return (
    !batchOptStatus.value &&
    ['indicator', 'rich-text'].includes(chart.value.type) &&
    props.eventInfo
  )
})

// 转发功能配置变更
const onFunctionCfgChange = val => {
  emit('onFunctionCfgChange', val)
}

// 转发辅助线配置变更
const onAssistLineChange = val => {
  emit('onAssistLineChange', val)
}

// 转发滚动配置变更
const onScrollCfgChange = val => {
  emit('onScrollCfgChange', val)
}

// 转发阈值配置变更
const onThresholdChange = val => {
  emit('onThresholdChange', val)
}

// 转发气泡动画配置变更
const onBubbleAnimateChange = val => {
  emit('onBubbleAnimateChange', val)
}

// 判断指定高级属性是否开放
const showProperties = (prop: EditorProperty) => {
  return properties?.value?.includes(prop)
}

// 打开跳转配置弹窗
const linkJumpSetOpen = () => {
  if (!dvInfo.value.id) {
    ElMessage.warning(t('visualization.save_page_tips'))
    return
  }
  // 保存画布后打开跳转配置，保证目标图表状态已落盘
  canvasSave(() => {
    linkJumpRef.value.dialogInit({ id: chart.value.id, type: chart.value.type })
  })
}
// 打开联动配置弹窗
const linkageSetOpen = () => {
  if (!dvInfo.value.id) {
    ElMessage.warning(t('visualization.save_page_tips'))
    return
  }
  // 保存画布后打开联动配置，保证当前图表状态已落盘
  canvasSave(() => {
    linkageRef.value.dialogInit({ id: chart.value.id })
  })
}

const SENIOR_PROP: EditorProperty[] = [
  'function-cfg',
  'assist-line',
  'scroll-cfg',
  'threshold',
  'jump-set',
  'linkage',
  'bubble-animate'
]
const excludeTypeList = ['chart-mix', 'chart-mix-stack', 'chart-mix-group']
// 判断当前图表是否没有可用的高级设置
const noSenior = computed(() => {
  return (
    !includesAny(properties.value, ...SENIOR_PROP) && excludeTypeList.includes(chart.value.type)
  )
})

// 切换跳转配置启用状态
const linkJumpActiveChange = () => {
  // 更新后刷新面板缓存中的跳转信息
  const params = {
    sourceDvId: dvInfo.value.id,
    sourceViewId: chart.value.id,
    activeStatus: chart.value.jumpActive
  }
  updateJumpSetActive(params).then(rsp => {
    dvMainStore.setNowPanelJumpInfo(rsp.data)
  })
}
// 切换联动配置启用状态
const linkageActiveChange = () => {
  const params = {
    dvId: dvInfo.value.id,
    sourceViewId: chart.value.id,
    activeStatus: chart.value.linkageActive
  }
  updateLinkageActive(params).then(rsp => {
    dvMainStore.setNowPanelTrackInfo(rsp.data)
  })
}
const appStore = useAppStoreWithOut()
// 判断当前是否处于嵌入式集成模式
const isCrestBi = computed(() => appStore.getIsCrestBi)

// 删除当前图表的联动高级配置
const removeLinkageSenior = () => {
  deleteLinkage({ dvId: dvInfo.value.id, sourceViewId: chart.value.id }).then(() => {
    // 删除后重新拉取联动信息，避免面板状态滞后
    getPanelAllLinkageInfo(dvInfo.value.id).then(rsp => {
      dvMainStore.setNowPanelTrackInfo(rsp.data)
    })
  })
}

// 删除当前图表的跳转高级配置
const removeJumpSenior = () => {
  deleteJumpSet({ sourceDvId: dvInfo.value.id, sourceViewId: chart.value.id }).then(() => {
    // 删除后重新拉取跳转信息，避免面板状态滞后
    queryVisualizationJumpInfo(dvInfo.value.id).then(rsp => {
      dvMainStore.setNowPanelJumpInfo(rsp.data)
    })
  })
}
</script>

<template>
  <el-row class="view-panel" :class="'senior-' + themes">
    <div @keydown.stop @keyup.stop class="attr-style" v-if="!noSenior">
      <el-row class="crest-collapse-style">
        <el-collapse v-model="state.attrActiveNames" class="style-collapse">
          <el-collapse-item
            :effect="themes"
            v-if="showProperties('function-cfg')"
            name="function"
            :title="t('chart.function_cfg')"
            @modelChange="onFunctionCfgChange"
          >
            <function-cfg
              :themes="themes"
              :chart="props.chart"
              :property-inner="propertyInnerAll['function-cfg']"
              @onFunctionCfgChange="onFunctionCfgChange"
            />
          </el-collapse-item>

          <collapse-switch-item
            :effect="themes"
            :title="t('chart.assist_line')"
            :change-model="chart.senior.assistLineCfg"
            v-if="showProperties('assist-line')"
            v-model="chart.senior.assistLineCfg.enable"
            name="analyse"
            @modelChange="val => onAssistLineChange({ data: val })"
          >
            <assist-line
              :chart="props.chart"
              :themes="themes"
              :quota-data="props.quotaData"
              :quota-ext-data="props.quotaExtData"
              :property-inner="propertyInnerAll['assist-line']"
              @onAssistLineChange="onAssistLineChange"
            />
          </collapse-switch-item>

          <collapse-switch-item
            :effect="themes"
            :title="t('chart.scroll_cfg')"
            :change-model="chart.senior.scrollCfg"
            v-if="showProperties('scroll-cfg')"
            v-model="chart.senior.scrollCfg.open"
            name="scroll"
            @modelChange="onScrollCfgChange"
          >
            <scroll-cfg
              :themes="themes"
              :chart="props.chart"
              :property-inner="propertyInnerAll['scroll-cfg']"
              @onScrollCfgChange="onScrollCfgChange"
            />
          </collapse-switch-item>

          <collapse-switch-item
            :effect="themes"
            :title="t('chart.threshold')"
            :change-model="chart.senior.threshold"
            v-model="chart.senior.threshold.enable"
            v-if="showProperties('threshold')"
            name="threshold"
            @modelChange="onThresholdChange"
          >
            <threshold
              :themes="themes"
              :chart="props.chart"
              :property-inner="propertyInnerAll['threshold']"
              @onThresholdChange="onThresholdChange"
            />
          </collapse-switch-item>

          <collapse-switch-item
            v-if="showProperties('linkage')"
            :themes="themes"
            name="linkage"
            :title="t('visualization.linkage_setting')"
            v-model="chart.linkageActive"
            @modelChange="linkageActiveChange"
          >
            <div class="inner-container">
              <span class="label" :class="'label-' + props.themes">{{
                t('visualization.linkage_setting')
              }}</span>
              <span class="right-btns">
                <template v-if="seniorCounts.linkageCount > 0">
                  <span class="set-text-info" :class="{ 'set-text-info-dark': themes === 'dark' }">
                    {{ t('visualization.already_setting') }}
                  </span>
                  <button
                    :class="'label-' + props.themes"
                    class="circle-button_icon"
                    :title="t('chart.delete')"
                    :style="{ margin: '0 8px' }"
                    @click="removeLinkageSenior"
                  >
                    <el-icon>
                      <Icon
                        ><icon_deleteTrash_outlined
                          :class="chart.linkageActive && 'primary-color'"
                          class="svg-icon"
                      /></Icon>
                    </el-icon>
                  </button>
                </template>
                <button
                  :class="'label-' + props.themes"
                  class="circle-button_icon"
                  :title="t('chart.edit')"
                  @click="linkageSetOpen"
                  :disabled="!chart.linkageActive"
                >
                  <el-icon>
                    <Icon
                      ><icon_edit_outlined
                        :class="chart.linkageActive && 'primary-color'"
                        class="svg-icon"
                    /></Icon>
                  </el-icon>
                </button>
              </span>
            </div>
          </collapse-switch-item>
          <collapse-switch-item
            v-if="showProperties('jump-set') && !isCrestBi"
            :themes="themes"
            name="jumpSet"
            :title="t('visualization.jump_set')"
            v-model="chart.jumpActive"
            @modelChange="linkJumpActiveChange"
          >
            <div class="inner-container">
              <span class="label" :class="'label-' + props.themes">{{
                t('visualization.jump_set')
              }}</span>
              <span class="right-btns">
                <template v-if="seniorCounts.jumpCount">
                  <span class="set-text-info" :class="{ 'set-text-info-dark': themes === 'dark' }">
                    {{ t('visualization.already_setting') }}
                  </span>
                  <button
                    :class="'label-' + props.themes"
                    class="circle-button_icon"
                    :title="t('chart.delete')"
                    :style="{ margin: '0 8px' }"
                    @click="removeJumpSenior"
                  >
                    <el-icon>
                      <Icon
                        ><icon_deleteTrash_outlined
                          :class="chart.jumpActive && 'primary-color'"
                          class="svg-icon"
                      /></Icon>
                    </el-icon>
                  </button>
                </template>
                <button
                  :class="'label-' + props.themes"
                  class="circle-button_icon"
                  :title="t('chart.edit')"
                  @click="linkJumpSetOpen"
                  :disabled="!chart.jumpActive"
                >
                  <el-icon>
                    <Icon
                      ><icon_edit_outlined
                        :class="chart.jumpActive && 'primary-color'"
                        class="svg-icon"
                    /></Icon>
                  </el-icon>
                </button>
              </span>
            </div>
          </collapse-switch-item>
          <collapse-switch-item
            :effect="themes"
            :title="t('visualization.bubble_dynamic_effect')"
            :change-model="chart.senior.bubbleCfg"
            v-if="showProperties('bubble-animate')"
            v-model="chart.senior.bubbleCfg.enable"
            name="bubbleAnimate"
            @modelChange="onBubbleAnimateChange"
          >
            <bubble-animate-cfg
              :themes="themes"
              :chart="props.chart"
              :property-inner="propertyInnerAll['bubble-animate']"
              @onBubbleAnimateChange="onBubbleAnimateChange"
            />
          </collapse-switch-item>
          <carousel-setting
            v-if="curComponent?.innerType === 'picture-group'"
            :element="curComponent"
            :themes="themes"
          ></carousel-setting>
          <el-collapse-item
            :effect="themes"
            name="events"
            :title="t('visualization.event')"
            v-if="eventsShow"
          >
            <common-event :themes="themes" :events-info="eventInfo"></common-event>
          </el-collapse-item>
        </el-collapse>
      </el-row>
    </div>
    <div v-if="noSenior" class="no-senior">
      {{ t('chart.chart_no_senior') }}
    </div>
    <!--跳转设置-->
    <link-jump-set ref="linkJumpRef" />
    <!--联动设置-->
    <linkage-set ref="linkageRef" />
  </el-row>
</template>

<style lang="less" scoped>
.ed-row {
  display: block;
}

span {
  font-size: 12px;
}

.view-panel {
  display: flex;
  height: 100%;
  width: 100%;
  .attr-style {
    width: 100%;
  }
}

.prop {
  border-bottom: 1px solid @side-outline-border-color;
}
.prop-top {
  border-top: 1px solid @side-outline-border-color;
}
.no-senior {
  width: 100%;
  text-align: center;
  font-size: 12px;
  padding-top: 40px;
  overflow: auto;
  height: 100%;

  color: #646a73;
}

.crest-collapse-style {
  :deep(.ed-form-item) {
    display: block;
    margin-bottom: 8px;
  }
  :deep(.ed-form-item__label) {
    justify-content: flex-start;
  }
  :deep(.style-collapse) {
    border-bottom: none;
  }
}

.label-dark {
  font-family: var(--crest-custom_font, 'PingFang');
  font-style: normal;
  font-weight: 400;
  line-height: 20px;
  color: #a6a6a6;
}

.inner-container {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  flex-direction: row;
  justify-content: space-between;

  .label {
    cursor: default;
    color: #646a73;
    font-size: 12px;
    font-style: normal;
    font-weight: 400;
    line-height: 20px;
  }

  .right-btns {
    display: flex;
    align-items: center;
    flex-direction: row;
  }

  .set-text-info {
    cursor: default;
    padding: 1.5px 4px;
    border-radius: 2px;
    background: rgba(31, 35, 41, 0.1);

    color: #646a73;

    font-size: 10px;
    font-style: normal;
    font-weight: 500;
    line-height: 13px;

    &.set-text-info-dark {
      color: #a6a6a6;
      background: rgba(235, 235, 235, 0.1);
    }
  }
}
</style>

<style>
.senior-dark {
  .label-dark {
    color: #a6a6a6 !important;
  }
}
</style>
