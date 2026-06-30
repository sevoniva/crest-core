<template>
  <div>
    <el-dropdown
      ref="dropdownRef"
      :id="'view-track-bar-' + chartId"
      :teleported="true"
      trigger="click"
      placement="bottom"
      popper-class="track_bar_custom"
      @visible-change="visibleChange"
    >
      <input id="input" style="opacity: 0" ref="trackButton" type="button" />
      <template #dropdown>
        <div :class="{ 'data-mobile': isDataVMobile }">
          <el-dropdown-menu
            class="track-menu"
            :style="{ 'font-family': fontFamily }"
            :append-to-body="false"
          >
            <el-dropdown-item
              v-for="(item, key) in trackMenu"
              :key="key"
              @mousedown.stop
              @click="trackMenuClick(item)"
              ><span class="menu-item">{{ state.i18n_map[item] }}</span></el-dropdown-item
            >
          </el-dropdown-menu>
        </div>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { PropType, reactive, ref, toRefs } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
/** 国际化翻译函数，生成追踪菜单文案 */
const { t } = useI18n()
/** 隐藏触发按钮引用，用于由图表事件主动打开菜单 */
const trackButton = ref(null)
/** 下拉菜单实例引用，用于点击后主动关闭菜单 */
const dropdownRef = ref<any>(null)
/** 向父级派发追踪菜单点击事件 */
const emits = defineEmits(['trackClick'])

/** 追踪菜单入参，控制菜单项、移动端缩放和字体 */
const props = defineProps({
  trackMenu: {
    type: Array as PropType<string[]>,
    required: true
  },
  isDataVMobile: {
    type: Boolean,
    required: false,
    default: false
  },
  fontFamily: {
    type: String,
    required: false,
    default: 'inherit'
  }
})
/** 菜单项列表引用 */
const { trackMenu } = toRefs(props)
/** 追踪菜单项的国际化文案映射 */
const state = reactive({
  i18n_map: {
    drill: t('visualization.drill'),
    linkage: t('visualization.linkage'),
    linkageAndDrill: t('visualization.linkage_and_drill'),
    jump: t('visualization.jump'),
    enlarge: t('visualization.enlarge'),
    event_jump: t('visualization.jump'),
    event_download: t('visualization.download'),
    event_share: t('visualization.share'),
    event_fullScreen: t('visualization.fullscreen'),
    event_showHidden: t('visualization.pop_area'),
    event_refreshDataV: t('visualization.refresh'),
    event_refreshView: t('visualization.refresh_view')
  }
})
/** 菜单展开变化时隐藏当前图表的 G2 tooltip，避免遮挡菜单 */
const visibleChange = () => {
  document.querySelectorAll('.g2-tooltip')?.forEach(tooltip => {
    if (tooltip.id?.includes(chartId.value)) {
      tooltip.classList.toggle('hidden-tooltip', true)
    }
  })
}
// 添加图表标识，用于区分不同图表的 tooltip
/** 当前触发追踪菜单的图表编号 */
const chartId = ref(null)
/** 记录图表编号并模拟点击隐藏按钮打开菜单 */
const trackButtonClick = (id?: string) => {
  chartId.value = id
  trackButton.value.click()
}

/** 菜单项点击后关闭下拉并通知父组件 */
const trackMenuClick = menu => {
  dropdownRef.value?.handleClose?.()
  emits('trackClick', menu)
}

defineExpose({
  trackButtonClick
})
</script>

<style lang="less">
.track_bar_custom {
  transform: translate(50px, -30px) !important;
}
</style>

<style lang="less" scoped>
.menu-item {
  font-size: 12px;
}

:deep(.ed-dropdown__popper) {
  position: static !important;
}

.ed-popper[x-placement^='bottom'] .popper__arrow {
  display: none;
}

:deep(.ed-popper[x-placement^='bottom']) {
  margin-top: -80px !important;
}

.data-mobile {
  zoom: 0.3;
}
</style>
