<script setup lang="ts">
import { computed, nextTick, onMounted, ref, toRefs } from 'vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import ComponentPosition from '@/components/visualization/common/ComponentPosition.vue'
import BackgroundOverallCommon from '@/components/visualization/component-background/BackgroundOverallCommon.vue'
import { useI18n } from '@/hooks/web/useI18n'
import elementResizeDetectorMaker from 'element-resize-detector'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import CommonStyleSet from '@/custom-component/common/CommonStyleSet.vue'
import CommonEvent from '@/custom-component/common/CommonEvent.vue'
import CarouselSetting from '@/custom-component/common/CarouselSetting.vue'
import CommonBorderSetting from '@/custom-component/common/CommonBorderSetting.vue'
import CollapseSwitchItem from '../../components/collapse-switch-item/src/CollapseSwitchItem.vue'
import TabBackgroundOverall from '@/custom-component/tabs/TabBackgroundOverall.vue'
import CustomTabsSortSide from '@/custom-component/tabs/CustomTabsSortSide.vue'
/** 快照仓库用于在通用属性变更后记录可撤销节点 */
const snapshotStore = snapshotStoreWithOut()

/** 国际化翻译函数，统一属性面板标题 */
const { t } = useI18n()
/** 属性面板向上通知通用属性变化 */
const emits = defineEmits(['onAttrChange'])

/** 通用属性面板入参，控制主题、目标组件和局部控件宽度 */
const props = withDefaults(
  defineProps<{
    type?: 'light' | 'dark'
    themes?: EditorTheme
    element: any
    showStyle?: boolean
    backgroundColorPickerWidth?: number
    backgroundBorderSelectWidth?: number
  }>(),
  {
    showStyle: true,
    themes: 'dark',
    backgroundColorPickerWidth: 50,
    backgroundBorderSelectWidth: 108
  }
)

/** 将主题和当前组件转为响应式引用，便于面板内部联动 */
const { themes, element } = toRefs(props)
/** 主画布仓库提供当前资源类型、批量模式和移动端预览状态 */
const dvMainStore = dvMainStoreWithOut()
/** 画布状态引用，决定通用属性分组的可见性 */
const { dvInfo, batchOptStatus, mobileInPc } = storeToRefs(dvMainStore)
/** 当前展开的折叠面板名称，同步回组件配置 */
const activeName = ref(element.value.collapseName)

/** 折叠面板切换后把当前分组写回组件配置 */
const onChange = () => {
  element.value.collapseName = activeName
}

/** 非批量编辑且非仪表板组件时显示位置设置 */
const positionComponentShow = computed(() => {
  return !batchOptStatus.value && !dashboardActive.value
})

/** 当前资源是否为仪表板 */
const dashboardActive = computed(() => {
  return dvInfo.value.type === 'dashboard'
})

/** 更新组件通用背景并记录快照 */
const onBackgroundChange = val => {
  element.value.commonBackground = val
  snapshotStore.recordSnapshotCacheToMobile('commonBackground')
  emits('onAttrChange', { custom: 'commonBackground' })
}

/** 标题背景启用状态变化后记录移动端快照 */
const onTitleBackgroundEnableChange = (_val?: boolean) => {
  snapshotStore.recordSnapshotCacheToMobile('titleBackground')
}

/** 更新标题背景配置并通知父级刷新 */
const onTitleBackgroundChange = val => {
  element.value.titleBackground = val
  snapshotStore.recordSnapshotCacheToMobile('titleBackground')
  emits('onAttrChange', { custom: 'titleBackground' })
}

/** 更新组件样式字段，并把具体属性透传给父级处理 */
const onStyleAttrChange = ({ key, value }) => {
  snapshotStore.recordSnapshotCacheToMobile('style')
  emits('onAttrChange', { custom: 'style', property: key, value: value })
}

/** 面板容器引用，用于监听属性栏宽度变化 */
const containerRef = ref()
/** 属性面板当前宽度，供内部控件做紧凑布局适配 */
const containerWidth = ref()

/** 仅对存在边框样式且非装饰类组件展示边框配置 */
const borderSettingShow = computed(() => {
  return (
    !!element.value.style['borderStyle'] &&
    !['Decoration', 'DynamicBackground'].includes(element.value.component)
  )
})

// 当前仅保留事件配置的可见性规则，不在属性面板展开独立开关。
/** 判断当前组件是否支持通用事件配置 */
const eventsShow = computed(() => {
  return (
    ['Picture', 'CanvasIcon', 'CircleShape', 'SvgTriangle', 'RectShape', 'ScrollText'].includes(
      element.value.component
    ) || element.value.innerType === 'rich-text'
  )
})

/** Tabs 和大屏组件在非移动端预览下展示轮播设置 */
const carouselShow = computed(() => {
  return (
    ['Tabs', 'Screen'].includes(element.value.component) &&
    element.value.carousel &&
    !mobileInPc.value
  )
})

/** 根据资源类型和组件类型判断是否展示自定义背景 */
const backgroundCustomShow = computed(() => {
  return (
    dashboardActive.value ||
    (!dashboardActive.value &&
      ![
        'CanvasBoard',
        'CanvasIcon',
        'CircleShape',
        'RectShape',
        'Decoration',
        'DynamicBackground'
      ].includes(element.value.component))
  )
})

/** 标题背景配置仅在 Tabs 和大屏组件开启标题背景时展示 */
const titleBackgroundShow = computed(
  () => ['Tabs', 'Screen'].includes(element.value.component) && element.value.titleBackground
)

/** Tabs 和大屏组件使用独立的标题样式配置区 */
const tabTitleShow = computed(() => {
  return (
    element.value && element.value.style && ['Tabs', 'Screen'].includes(element.value.component)
  )
})

/** 常规样式区排除装饰、动态背景、Tabs 和大屏等特殊组件 */
const styleShow = computed(() => {
  return (
    element.value &&
    element.value.style &&
    !['Decoration', 'DynamicBackground', 'Tabs', 'Screen'].includes(element.value.component) &&
    Object.keys(element.value.style).length > 0
  )
})

/** 挂载后监听属性面板宽度，驱动内部选择器宽度自适应 */
onMounted(() => {
  const erd = elementResizeDetectorMaker()
  containerWidth.value = containerRef.value?.offsetWidth
  erd.listenTo(containerRef.value, () => {
    nextTick(() => {
      containerWidth.value = containerRef.value?.offsetWidth
    })
  })
})
</script>

<template>
  <div class="v-common-attr" ref="containerRef">
    <el-collapse v-model="activeName" @change="onChange()">
      <el-collapse-item
        :effect="themes"
        :title="t('visualization.position')"
        name="position"
        v-if="positionComponentShow"
      >
        <component-position :themes="themes" />
      </el-collapse-item>
      <el-collapse-item
        :effect="themes"
        :title="t('visualization.background')"
        name="background"
        v-if="element && backgroundCustomShow"
      >
        <background-overall-common
          :themes="themes"
          :common-background-pop="element.commonBackground"
          component-position="component"
          @onBackgroundChange="onBackgroundChange"
          :background-color-picker-width="backgroundColorPickerWidth"
          :background-border-select-width="backgroundBorderSelectWidth"
        />
      </el-collapse-item>

      <collapse-switch-item
        :effect="themes"
        :title="t('visualization.title_background')"
        name="titleBackground"
        v-model="element.titleBackground.enable"
        @modelChange="val => onTitleBackgroundEnableChange(val)"
        v-if="element && titleBackgroundShow"
      >
        <tab-background-overall
          :themes="themes"
          :element="element"
          component-position="component"
          @onTitleBackgroundChange="onTitleBackgroundChange"
        ></tab-background-overall>
      </collapse-switch-item>
      <slot></slot>
      <collapse-switch-item
        v-if="tabTitleShow"
        v-model="element.style.showTabTitle"
        @modelChange="
          () => onStyleAttrChange({ key: 'showTabTitle', value: element.style.showTabTitle })
        "
        :themes="themes"
        :title="t('visualization.tab_title')"
        name="tabTitle"
        class="common-style-area"
      >
        <common-style-set
          @onStyleAttrChange="onStyleAttrChange"
          :themes="themes"
          :element="element"
        ></common-style-set>
        <CustomTabsSortSide :themes="themes" :config="element"></CustomTabsSortSide>
      </collapse-switch-item>
      <el-collapse-item
        v-if="styleShow"
        :effect="themes"
        :title="t('visualization.style')"
        name="style"
        class="common-style-area"
      >
        <common-style-set
          @onStyleAttrChange="onStyleAttrChange"
          :themes="themes"
          :element="element"
        ></common-style-set>
      </el-collapse-item>
      <el-collapse-item
        v-if="element && element.events && eventsShow"
        :effect="themes"
        :title="t('visualization.event')"
        name="events"
        class="common-style-area"
      >
        <common-event :themes="themes" :events-info="element.events"></common-event>
      </el-collapse-item>
      <collapse-switch-item
        v-if="element && borderSettingShow"
        v-model="element.style.borderActive"
        @modelChange="
          () => onStyleAttrChange({ key: 'borderActive', value: element.style.borderActive })
        "
        :themes="themes"
        :title="t('visualization.board')"
        name="borderSetting"
        class="common-style-area"
      >
        <common-border-setting
          :style-info="element.style"
          :themes="themes"
          @onStyleAttrChange="onStyleAttrChange"
        ></common-border-setting>
      </collapse-switch-item>
      <slot name="threshold" />
      <slot name="carousel" />
      <CarouselSetting v-if="carouselShow" :element="element" :themes="themes"></CarouselSetting>
    </el-collapse>
  </div>
</template>

<style lang="less" scoped>
.v-common-attr {
  .ed-input-group__prepend {
    padding: 0 10px;
  }
  :deep(.ed-collapse-item__content) {
    border-top: none;
  }

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
    padding: 16px 8px 8px !important;
    border: none;
  }
  :deep(.ed-form-item) {
    display: block;
    margin-bottom: 16px;
  }
  :deep(.ed-form-item__label) {
    justify-content: flex-start;
  }
}

:deep(.ed-collapse-item) {
  &:first-child {
    .ed-collapse-item__header {
      border-top: none;
    }
  }
}

:deep(.ed-collapse) {
  width: 100%;
}

.attr-custom-icon-main {
  padding-top: 4px;
  width: 30px;
  overflow: hidden;
  text-align: right;
}

.attr-custom-icon {
  font-size: 16px;
  color: #646a73;
  margin-right: 5px;
}

.common-style-inner {
  width: 100%;
  min-width: 230px;
  margin-left: -12px;
}

.bash-icon {
  width: 24px;
  height: 24px;
}

.custom-color {
  margin-left: 4px;
}

:deep(.ed-color-picker.is-custom .ed-color-picker__mask) {
  height: 26px;
  width: 48px;
}

:deep(.ed-form-item) {
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

.icon-radio-group {
  :deep(.ed-radio) {
    margin-right: 8px;
    height: 28px;

    &:last-child {
      margin-right: 0;
    }
  }
  :deep(.ed-radio__input) {
    display: none;
  }
  :deep(.ed-radio__label) {
    padding: 0;
  }
}
.icon-btn {
  font-size: 16px;
  line-height: 16px;
  width: 24px;
  height: 24px;
  text-align: center;
  border-radius: 6px;
  padding-top: 4px;

  color: #1f2329;

  cursor: pointer;

  &.dark {
    color: #a6a6a6;
    &.active {
      color: var(--ed-color-primary);
      background-color: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1));
    }
    &:hover {
      background-color: rgba(255, 255, 255, 0.1);
    }
  }

  &.active {
    color: var(--ed-color-primary);
    background-color: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1));
  }

  &:hover {
    background-color: rgba(31, 35, 41, 0.1);
  }
}
</style>
