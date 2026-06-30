<template>
  <el-form size="small" label-position="top">
    <!-- 仪表板主题色只在 dashboard 类型展示，大屏沿用画布自身主题配置。 -->
    <el-form-item
      class="form-item"
      :class="'form-item-' + themes"
      v-if="dvInfo.type === 'dashboard'"
      :label="t('visualization.theme_color')"
    >
      <el-space :size="24">
        <color-button
          :color-type="'light'"
          :label="canvasStyleData.dashboard.themeColor"
          @onClick="colorButtonClick"
        >
          <div class="color-btn-text">{{ t('visualization.theme_color_light') }}</div>
        </color-button>
        <color-button
          :color-type="'dark'"
          :label="canvasStyleData.dashboard.themeColor"
          @onClick="colorButtonClick"
        >
          <div class="color-btn-text">{{ t('visualization.theme_color_dark') }}</div>
        </color-button>
      </el-space>
    </el-form-item>
    <el-form-item
      v-if="dvInfo.type === 'dashboard'"
      class="form-item"
      :class="'form-item-' + themes"
      :label="t('visualization.font_family_select')"
    >
      <el-select :effect="themes" v-model="canvasStyleData.fontFamily" @change="fontFamilyChange()">
        <el-option
          v-for="option in fontFamily"
          :key="option.value"
          :label="option.name"
          :value="option.value"
        />
      </el-select>
    </el-form-item>
    <!-- 组件间距配置影响仪表板栅格布局，预设模式写入固定间距，自定义模式保留用户输入。 -->
    <template v-if="dvInfo.type === 'dashboard'">
      <el-form-item
        class="form-item"
        :class="'form-item-' + themes"
        :label="t('visualization.component_gap')"
      >
        <el-radio-group v-model="canvasStyleData.dashboard.gap" @change="themeChange">
          <el-radio :effect="themes" label="yes">{{ t('visualization.gap') }}</el-radio>
          <el-radio :effect="themes" label="no">{{ t('visualization.no_gap') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        v-if="canvasStyleData.dashboard.gap === 'yes'"
        class="form-item"
        :class="'form-item-' + themes"
        :label="t('visualization.gap_size')"
      >
        <el-radio-group v-model="canvasStyleData.dashboard.gapMode" @change="onGapModeChange">
          <el-radio :effect="themes" label="small">{{ t('visualization.small') }}</el-radio>
          <el-radio :effect="themes" label="middle">{{ t('visualization.middle') }}</el-radio>
          <el-radio :effect="themes" label="large">{{ t('visualization.large') }}</el-radio>
          <el-radio :effect="themes" label="custom">{{ t('visualization.custom') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        class="form-item"
        :class="'form-item-' + themes"
        v-show="canvasStyleData.dashboard.gapMode === 'custom'"
      >
        <el-input-number
          v-model="canvasStyleData.dashboard.gapSize"
          :effect="themes"
          controls-position="right"
          :min="0"
          :max="10"
          @change="themeChange"
        />
      </el-form-item>
    </template>
    <!-- 仪表板适配方式决定画布缩放策略，变更后需要重新计算画布尺寸。 -->
    <el-form-item
      v-if="dvInfo.type === 'dashboard'"
      class="form-item"
      :class="'form-item-' + themes"
      :label="t('visualization.dashboard_adaptor')"
    >
      <el-radio-group v-model="canvasStyleData.dashboardAdaptor" @change="onKeepSizeChange">
        <el-radio :effect="themes" label="keepHeightAndWidth">{{
          t('visualization.scale_keep_height_and_width')
        }}</el-radio>
        <el-radio :effect="themes" label="withWidth">{{
          t('visualization.scale_with_width')
        }}</el-radio>
      </el-radio-group>
    </el-form-item>
    <!-- 图表刷新和浏览器刷新分别控制数据重取与整页重载，两个定时器独立配置。 -->
    <el-form-item class="form-item" :class="'form-item-' + themes" style="margin-bottom: 8px">
      <el-checkbox
        :effect="themes"
        size="small"
        v-model="canvasStyleData.refreshViewEnable"
        @change="themeChange"
      >
        {{ t('visualization.refresh_frequency') }}
      </el-checkbox>
    </el-form-item>
    <el-form-item class="form-item" :class="'form-item-' + themes" style="padding-left: 20px">
      <el-input
        v-model="canvasStyleData.refreshTime"
        :effect="themes"
        class="time-input-number"
        :class="[dvInfo.type === 'dashboard' && 'padding20', themes === 'dark' && 'dv-dark']"
        type="number"
        :min="1"
        :max="3600"
        :disabled="!canvasStyleData.refreshViewEnable"
        @change="onRefreshChange"
      >
        <template #append>
          <el-select
            v-model="canvasStyleData.refreshUnit"
            :effect="themes"
            :disabled="!canvasStyleData.refreshViewEnable"
            style="width: 90px"
            @change="themeChange"
          >
            <el-option :label="t('visualization.minute')" :value="'minute'" />
            <el-option :label="t('visualization.second')" :value="'second'" />
          </el-select>
        </template>
      </el-input>
    </el-form-item>
    <el-form-item class="form-item" :class="'form-item-' + themes" style="margin-bottom: 8px">
      <el-checkbox
        :effect="themes"
        size="small"
        v-model="canvasStyleData.refreshBrowserEnable"
        @change="themeChange"
      >
        {{ t('components.overall_refresh') }}
      </el-checkbox>
      <el-tooltip class="item" :effect="toolTip" placement="bottom">
        <template #content>
          <div>{{ t('components.previews_take_effect') }}</div>
        </template>
        <el-icon
          class="hint-icon"
          style="margin-left: 4px"
          :class="{ 'hint-icon--dark': themes === 'dark' }"
        >
          <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
        </el-icon>
      </el-tooltip>
    </el-form-item>
    <el-form-item class="form-item" :class="'form-item-' + themes" style="padding-left: 20px">
      <el-input
        v-model="canvasStyleData.refreshBrowserTime"
        :effect="themes"
        class="time-input-number"
        :class="[dvInfo.type === 'dashboard' && 'padding20', themes === 'dark' && 'dv-dark']"
        type="number"
        :min="1"
        :max="3600"
        :disabled="!canvasStyleData.refreshBrowserEnable"
        @change="onRefreshChange"
      >
        <template #append>
          <el-select
            v-model="canvasStyleData.refreshBrowserUnit"
            :effect="themes"
            :disabled="!canvasStyleData.refreshBrowserEnable"
            style="width: 90px"
            @change="themeChange"
          >
            <el-option :label="t('visualization.minute')" :value="'minute'" />
            <el-option :label="t('visualization.second')" :value="'second'" />
          </el-select>
        </template>
      </el-input>
    </el-form-item>
    <el-form-item class="form-item" :class="'form-item-' + themes">
      <el-checkbox
        :effect="themes"
        size="small"
        v-model="canvasStyleData.refreshViewLoading"
        @change="themeChange"
        >{{ t('visualization.enable_view_loading') }}</el-checkbox
      >
    </el-form-item>

    <!-- 结果数量控制图表默认取数范围，自定义模式用于限制单个组件返回量。 -->
    <el-form-item class="form-item" :class="'form-item-' + themes" style="margin-bottom: 8px">
      <template #label>
        <span class="data-area-label">
          <span style="margin-right: 4px">
            {{ t('visualization.panel_view_result_show') }}
          </span>
          <el-tooltip class="item" :effect="toolTip" placement="bottom">
            <template #content>
              <div>
                {{ t('visualization.panel_view_result_tips', [resourceType]) }}
              </div>
            </template>
            <el-icon class="hint-icon" :class="{ 'hint-icon--dark': themes === 'dark' }">
              <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
            </el-icon>
          </el-tooltip>
        </span>
      </template>
      <el-radio-group
        :effect="themes"
        v-model="canvasStyleData.dashboard.resultMode"
        class="radio-span"
        @change="themeChange"
      >
        <el-radio value="all" :effect="themes"> {{ t('visualization.view') }} </el-radio>
        <el-radio value="custom" :effect="themes">
          {{ resourceType }}
        </el-radio>
      </el-radio-group>
    </el-form-item>
    <!-- 公开链接悬浮按钮仅对仪表板链接生效，桌面端预览不展示该选项。 -->
    <el-form-item
      class="form-item"
      :class="'form-item-' + themes"
      v-show="canvasStyleData.dashboard.resultMode === 'custom'"
    >
      <el-input-number
        v-model="canvasStyleData.dashboard.resultCount"
        :effect="themes"
        controls-position="right"
        :min="1"
        :max="10000"
        @change="themeChange"
        :disabled="canvasStyleData.dashboard.resultMode === 'all'"
      />
    </el-form-item>
    <el-form-item
      v-show="dvInfo.type === 'dashboard' && !isDesktopFlag"
      style="margin-top: 16px; margin-bottom: 8px"
      :class="'form-item-' + themes"
    >
      <el-checkbox
        :effect="themes"
        size="small"
        v-model="canvasStyleData.suspensionButtonAvailable"
        @change="themeChange"
      >
        <span class="data-area-label">
          <span style="margin-right: 4px"> {{ t('visualization.button_tips') }}</span>
          <el-tooltip class="item" :effect="toolTip" placement="bottom">
            <template #content>
              <div>{{ t('visualization.effective_during_link') }}</div>
            </template>
            <el-icon class="hint-icon" :class="{ 'hint-icon--dark': themes === 'dark' }">
              <Icon name="icon_info_outlined"><icon_info_outlined class="svg-icon" /></Icon>
            </el-icon>
          </el-tooltip>
        </span>
      </el-checkbox>
    </el-form-item>
    <!-- 辅助网格只影响编辑态定位，不写入组件自身样式。 -->
    <el-form-item
      v-show="dvInfo.type === 'dashboard'"
      class="form-item"
      :class="'form-item-' + themes"
      style="margin-bottom: 0"
    >
      <el-checkbox
        :effect="themes"
        size="small"
        v-model="canvasStyleData.dashboard.showGrid"
        @change="themeChange"
        >{{ t('visualization.display_auxiliary_grid') }}</el-checkbox
      >
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import icon_info_outlined from '@/assets/svg/icon_info_outlined.svg'
import { useI18n } from '@/hooks/web/useI18n'
const { t } = useI18n()
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
const dvMainStore = dvMainStoreWithOut()
const { canvasStyleData, dvInfo } = storeToRefs(dvMainStore)
import {
  adaptCurThemeCommonStyleAll,
  adaptTitleFontFamilyAll,
  DARK_THEME_DASHBOARD_BACKGROUND,
  LIGHT_THEME_DASHBOARD_BACKGROUND
} from '@/utils/canvasStyle'
import {
  CHART_FONT_FAMILY_ORIGIN,
  DEFAULT_COLOR_CASE_DARK,
  DEFAULT_COLOR_CASE_LIGHT,
  DEFAULT_TAB_COLOR_CASE_DARK,
  DEFAULT_TAB_COLOR_CASE_LIGHT,
  DEFAULT_TITLE_STYLE_DARK,
  DEFAULT_TITLE_STYLE_LIGHT,
  FILTER_COMMON_STYLE_DARK,
  FILTER_COMMON_STYLE_LIGHT,
  SENIOR_STYLE_SETTING_DARK,
  SENIOR_STYLE_SETTING_LIGHT
} from '@/views/chart/components/editor/util/chart'
import ColorButton from '@/components/assist-button/ColorButton.vue'
import { computed } from 'vue'
import { deepCopy } from '@/utils/utils'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { storeToRefs } from 'pinia'
import {
  COMMON_COMPONENT_BACKGROUND_DARK,
  COMMON_COMPONENT_BACKGROUND_LIGHT
} from '@/custom-component/component-list'
import { ElFormItem, ElIcon, ElSpace } from 'element-plus-secondary'
import Icon from '@/components/icon-custom/src/Icon.vue'
import { useAppearanceStoreWithOut } from '@/store/modules/appearance'
import { isDesktop } from '@/utils/ModelUtil'
import eventBus from '@/utils/eventBus'
const appearanceStore = useAppearanceStoreWithOut()
const isDesktopFlag = isDesktop()
const snapshotStore = snapshotStoreWithOut()
// 整体设置面板管理仪表板主题、字体、刷新和结果数量等全局画布配置。
const props = defineProps({
  themes: {
    type: String,
    default: 'light'
  }
})
// 字体列表合并系统默认字体和用户上传字体，value 直接写入画布字体变量。
const fontFamily = CHART_FONT_FAMILY_ORIGIN.concat(
  appearanceStore.fontList.map(ele => ({
    name: ele.name,
    value: ele.name
  }))
)

// 提示浮层使用反向主题，保证深浅背景下都有足够的文字对比度。
const toolTip = computed(() => {
  return props.themes === 'dark' ? 'light' : 'dark'
})

// 结果数量说明需要根据仪表板/大屏使用不同资源名。
const resourceType = computed(() =>
  dvInfo.value.type === 'dashboard' ? t('work_branch.dashboard') : t('work_branch.big_data_screen')
)

// 刷新间隔限制在 1 到 3600，避免无效值导致定时器异常。
const onRefreshChange = val => {
  if (val === '' || parseFloat(val).toString() === 'NaN' || parseFloat(val) < 1) {
    // 空值和非法输入统一回退到最小刷新间隔，避免保存不可执行的定时配置。
    canvasStyleData.value.refreshTime = 1
    return
  } else if (parseFloat(val) > 3600) {
    // 刷新间隔上限与输入框 max 保持一致，防止手动输入绕过控件限制。
    canvasStyleData.value.refreshTime = 3600
  }
  themeChange()
}
// 字体切换需要同步 CSS 变量、所有标题样式和快照。
const fontFamilyChange = () => {
  appearanceStore.setCurrentFont(canvasStyleData.value.fontFamily)
  document.documentElement.style.setProperty(
    '--crest-canvas_custom_font',
    `${canvasStyleData.value.fontFamily}`
  )
  adaptTitleFontFamilyAll(canvasStyleData.value.fontFamily)
  snapshotStore.recordSnapshotCache('renderChart')
}

// 适配方式变化后重新计算画布尺寸，并记录可撤销快照。
const onKeepSizeChange = () => {
  eventBus.emit('event-canvas-size-init')
  snapshotStore.recordSnapshotCache('renderChart')
}
// 预设间距模式写入固定像素值，自定义模式保留用户输入。
const onGapModeChange = () => {
  snapshotStore.recordSnapshotCache('renderChart')
  switch (canvasStyleData.value.dashboard.gapMode) {
    case 'small':
      // 预设间距只写入 gapSize，渲染层继续复用统一的间距计算逻辑。
      canvasStyleData.value.dashboard.gapSize = 3
      break
    case 'middle':
      canvasStyleData.value.dashboard.gapSize = 5
      break
    case 'large':
      canvasStyleData.value.dashboard.gapSize = 10
      break
    default:
      break
  }
}

// 主题切换会批量替换画布背景、组件背景、图表标题、配色、筛选器和高级样式。
const themeChange = (modifyName?) => {
  if (modifyName === 'themeColor') {
    // 主题色切换需要打开背景色选择开关，确保渲染层读取新的主题值。
    canvasStyleData.value.component.chartCommonStyle.backgroundColorSelect = true
    canvasStyleData.value.backgroundColorSelect = true
    if (canvasStyleData.value.dashboard.themeColor === 'light') {
      // 浅色主题成套替换默认背景、组件背景、标题、图表配色和筛选器样式。
      canvasStyleData.value.backgroundColor = LIGHT_THEME_DASHBOARD_BACKGROUND
      canvasStyleData.value.component.chartCommonStyle = deepCopy(COMMON_COMPONENT_BACKGROUND_LIGHT)
      canvasStyleData.value.component.chartTitle = deepCopy(DEFAULT_TITLE_STYLE_LIGHT)
      canvasStyleData.value.component.chartColor = deepCopy(DEFAULT_COLOR_CASE_LIGHT)
      canvasStyleData.value.component.filterStyle = deepCopy(FILTER_COMMON_STYLE_LIGHT)
      canvasStyleData.value.component.tabStyle = deepCopy(DEFAULT_TAB_COLOR_CASE_LIGHT)
      canvasStyleData.value.component.seniorStyleSetting = deepCopy(SENIOR_STYLE_SETTING_LIGHT)
    } else {
      // 深色主题同样成套替换，避免只切换画布背景导致组件仍保留浅色默认值。
      canvasStyleData.value.backgroundColor = DARK_THEME_DASHBOARD_BACKGROUND
      canvasStyleData.value.component.chartCommonStyle = deepCopy(COMMON_COMPONENT_BACKGROUND_DARK)
      canvasStyleData.value.component.chartTitle = deepCopy(DEFAULT_TITLE_STYLE_DARK)
      canvasStyleData.value.component.chartColor = deepCopy(DEFAULT_COLOR_CASE_DARK)
      canvasStyleData.value.component.filterStyle = deepCopy(FILTER_COMMON_STYLE_DARK)
      canvasStyleData.value.component.tabStyle = deepCopy(DEFAULT_TAB_COLOR_CASE_DARK)
      canvasStyleData.value.component.seniorStyleSetting = deepCopy(SENIOR_STYLE_SETTING_DARK)
    }
    adaptCurThemeCommonStyleAll()
  }
  snapshotStore.recordSnapshotCache('renderChart')
}
// 重复点击当前主题只保持当前值，不重复记录快照。
const colorButtonClick = val => {
  if (val !== canvasStyleData.value.dashboard.themeColor) {
    canvasStyleData.value.dashboard.themeColor = val
    themeChange('themeColor')
  } else {
    canvasStyleData.value.dashboard.themeColor = val
  }
}
</script>

<style scoped lang="less">
.color-btn-text {
  /* 主题色按钮文本固定居中，配合色块缩略图形成统一点击区域。 */
  width: 100%;
  text-align: center;
  padding-top: 4px;
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
  line-height: 20px;
}
:deep(.ed-form-item) {
  margin-bottom: 16px;
}
.time-input-number {
  &.padding20 {
    /* 仪表板场景下追加区域更宽，避免单位下拉文本贴边。 */
    :deep(.ed-input-group__append) {
      padding: 0 20px;
    }
  }
  :deep(&.dv-dark) {
    .ed-input-group__append .ed-select {
      margin: 0 -20px;
    }
  }

  :deep(&.dv-dark:not(.is-disabled)) {
    & > .ed-input__wrapper {
      background-color: #1a1a1a;
    }
  }
  :deep(input) {
    /* 刷新间隔只保留文本框外观，隐藏浏览器默认数字步进按钮。 */
    -webkit-appearance: none;
    -moz-appearance: textfield;

    &::-webkit-inner-spin-button,
    &::-webkit-outer-spin-button {
      -webkit-appearance: none;
    }
  }
}

.ed-select--dark {
  background: #292929 !important;
}

.hint-icon {
  cursor: pointer;
  font-size: 14px;
  color: #646a73;

  &.hint-icon--dark {
    color: #a6a6a6;
  }
}
.data-area-label {
  display: flex;
  flex-direction: row;
  align-items: center;
}
</style>
