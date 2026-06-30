<template>
  <div
    v-if="state.tabShow"
    style="width: 100%; height: 100%"
    :class="[
      headClass,
      `ed-tabs-${curThemes}`,
      { 'title-hidde-tab': !showTabTitleFlag },
      { 'title-show-tab': showTabTitleFlag }
    ]"
    class="custom-tabs-head"
    ref="tabComponentRef"
  >
    <CustomTab
      v-model="element.editableTabsValue"
      @tab-add="addTab"
      :addable="isEditMode"
      :font-color="fontColor"
      :active-color="activeColor"
      :border-color="noBorderColor"
      :border-active-color="borderActiveColor"
      :hide-title="!showTabTitleFlag"
    >
      <template :key="tabItem.name" v-for="tabItem in tabItems">
        <el-tab-pane
          class="el-tab-pane-custom"
          :lazy="isEditMode"
          :label="tabItem.title"
          :name="tabItem.name"
          v-if="!tabItem.hidden"
        >
          <template #label>
            <div class="custom-tab-title" @mousedown.stop>
              <span class="title-inner" :style="titleStyle(tabItem.name)"
                >{{ tabItem.title }}
                <Board
                  v-show="svgInnerActiveEnable(tabItem.name)"
                  :style="{
                    color: element.titleBackground.active.innerImageColor,
                    pointerEvents: 'none'
                  }"
                  :name="titleBackgroundActiveSvgInner"
                ></Board>

                <Board
                  v-show="svgInnerInActiveEnable(tabItem.name)"
                  :style="{
                    color: element.titleBackground.inActive.innerImageColor,
                    pointerEvents: 'none'
                  }"
                  :name="titleBackgroundInActiveSvgInner"
                ></Board>
                <span v-if="isEditMode">
                  <el-dropdown
                    popper-class="custom-crest-tab-dropdown"
                    :effect="curThemes"
                    trigger="click"
                    @command="handleCommand"
                  >
                    <span class="el-dropdown-link">
                      <el-icon v-if="isEdit"><ArrowDown /></el-icon>
                    </span>
                    <template #dropdown>
                      <el-dropdown-menu :style="{ 'font-family': fontFamily }">
                        <el-dropdown-item :command="beforeHandleCommand('editTitle', tabItem)">
                          {{ t('visualization.edit_title') }}
                        </el-dropdown-item>
                        <el-dropdown-item :command="beforeHandleCommand('copyCur', tabItem)">
                          {{ t('visualization.copy') }}
                        </el-dropdown-item>
                        <el-dropdown-item
                          v-if="tabItems.length > 1"
                          :command="beforeHandleCommand('deleteCur', tabItem)"
                        >
                          {{ t('visualization.delete') }}
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </span>
              </span>
            </div>
          </template>
        </el-tab-pane>
      </template>
      <div
        class="tab-content-custom"
        :key="tabItem.name + '-content'"
        @mouseenter="handleMouseEnter"
        @mouseleave="handleMouseLeave"
        v-for="(tabItem, index) in tabItems"
        :class="{ 'switch-hidden': element.editableTabsValue !== tabItem.name }"
      >
        <template v-if="!tabItem.hidden">
          <CanvasRenderer
            v-if="isEdit && !mobileInPc"
            :ref="'tabCanvas_' + index"
            :component-data="tabItem.componentData"
            :canvas-style-data="canvasStyleData"
            :canvas-view-info="canvasViewInfo"
            :canvas-id="element.id + '--' + tabItem.name"
            :class="moveActive ? 'canvas-move-in' : ''"
            :canvas-position="'tab'"
            :canvas-active="element.editableTabsValue === tabItem.name"
            :font-family="fontFamily"
          ></CanvasRenderer>
          <CrestPreview
            v-else
            :ref="'dashboardPreview'"
            :dv-info="dvInfo"
            :cur-gap="curPreviewGap"
            :component-data="tabItem.componentData"
            :canvas-style-data="{}"
            :canvas-view-info="canvasViewInfo"
            :canvas-id="element.id + '--' + tabItem.name"
            :preview-active="element.editableTabsValue === tabItem.name"
            :show-position="showPosition"
            :outer-scale="scale"
            :font-family="fontFamily"
            :outer-search-count="searchCount"
          ></CrestPreview>
        </template>
      </div>
    </CustomTab>
    <el-dialog
      title="编辑标题"
      :append-to-body="true"
      v-model="state.dialogVisible"
      width="30%"
      :show-close="false"
      :close-on-click-modal="false"
      center
    >
      <el-input
        v-model="state.textarea"
        maxlength="50"
        :placeholder="$t('dataset.input_content')"
      />
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="state.dialogVisible = false">取消</el-button>
          <el-button :disabled="!titleValid" type="primary" @click="sureCurTitle">确认</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  computed,
  getCurrentInstance,
  nextTick,
  onBeforeMount,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  toRefs,
  watch
} from 'vue'
import CanvasRenderer from '@/views/canvas/CanvasRenderer.vue'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { guid } from '@/views/visualized/data/dataset/form/util'
import eventBus from '@/utils/eventBus'
import { canvasChangeAdaptor, findComponentIndexById, isDashboard } from '@/utils/canvasUtils'
import CustomTab from '@/custom-component/tabs/CustomTab.vue'
import CrestPreview from '@/components/data-visualization/canvas/CrestPreview.vue'
import { getPanelAllLinkageInfo } from '@/api/visualization/linkage'
import { dataVTabComponentAdd, groupSizeStyleAdaptor } from '@/utils/style'
import { deepCopyTabItemHelper } from '@/store/modules/data-visualization/copy'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { useI18n } from '@/hooks/web/useI18n'
import { setBackgroundImageStyle } from '@/utils/backgroundStyleUtils'
import Board from '@/components/visual-board/Board.vue'
import ChartCarouselTooltip from '@/views/chart/components/js/g2plot_tooltip_carousel'
import { debounce } from 'lodash-es'
import { useEmitt } from '@/hooks/web/useEmitt'
import { CommonBackground } from '@/components/visualization/component-background/Types'
import { ShorthandMode } from '@/Types'
import { checkFilterRemove } from '@/custom-component/v-query/QueryUtils'
/** 可视化主画布状态仓库 */
const dvMainStore = dvMainStoreWithOut()
/** 快照状态仓库 */
const snapshotStore = snapshotStoreWithOut()
/** 标签拖入状态、画布矩阵和编辑模式等全局状态 */
const { tabMoveInActiveId, bashMatrixInfo, editMode, mobileInPc } = storeToRefs(dvMainStore)
/** 标签组件根节点引用 */
const tabComponentRef = ref(null)
/** 标签自动轮播计时器 */
let carouselTimer = null
/** 国际化文本读取方法 */
const { t } = useI18n()

/** 标签页组件输入属性 */
const props = defineProps({
  canvasStyleData: {
    type: Object,
    required: true
  },
  canvasViewInfo: {
    type: Object,
    required: true
  },
  dvInfo: {
    type: Object,
    required: true
  },
  element: {
    type: Object,
    default() {
      return {
        propValue: []
      }
    }
  },
  isEdit: {
    type: Boolean,
    default: false
  },
  showPosition: {
    type: String,
    required: false,
    default: 'canvas'
  },
  scale: {
    type: Number,
    required: false,
    default: 1
  },
  // 仪表板刷新计时器
  searchCount: {
    type: Number,
    required: false,
    default: 0
  },
  // 仪表板字体
  fontFamily: {
    type: String,
    required: false,
    default: 'inherit'
  }
})
const {
  element,
  isEdit,
  showPosition,
  canvasStyleData,
  canvasViewInfo,
  dvInfo,
  scale,
  searchCount
} = toRefs(props)

/** 当前组件内的标签页列表 */
const tabItems = computed(() =>
  Array.isArray(element.value?.propValue) ? element.value.propValue : []
)

/** 确保标签页数据始终是数组 */
const ensureTabItems = () => {
  if (!Array.isArray(element.value.propValue)) {
    element.value.propValue = []
  }
  return element.value.propValue
}

/** 激活态标题内置背景图片名称 */
const titleBackgroundActiveSvgInner = computed(() => {
  return element.value.titleBackground.active.innerImage.replace('board/', '').replace('.svg', '')
})

/** 非激活态标题内置背景图片名称 */
const titleBackgroundInActiveSvgInner = computed(() => {
  return element.value.titleBackground.inActive.innerImage.replace('board/', '').replace('.svg', '')
})

/** 判断非激活标签是否启用内置 SVG 背景 */
const svgInnerInActiveEnable = itemName => {
  const { backgroundImageEnable, backgroundType, innerImage } =
    element.value.titleBackground.inActive
  return (
    element.value.editableTabsValue !== itemName &&
    !element.value.titleBackground.multiply &&
    element.value.titleBackground?.enable &&
    backgroundImageEnable &&
    backgroundType === 'innerImage' &&
    typeof innerImage === 'string'
  )
}

/** 判断激活标签是否启用内置 SVG 背景 */
const svgInnerActiveEnable = itemName => {
  const { backgroundImageEnable, backgroundType, innerImage } = element.value.titleBackground.active
  return (
    (element.value.editableTabsValue === itemName || element.value.titleBackground.multiply) &&
    element.value.titleBackground?.enable &&
    backgroundImageEnable &&
    backgroundType === 'innerImage' &&
    typeof innerImage === 'string'
  )
}

/** 根据当前激活标签暂停或恢复图表 Tooltip 轮播 */
const viewToolTipsChange = () => {
  element.value.propValue?.forEach(tabItem => {
    const tMethod =
      element.value.editableTabsValue === tabItem.name
        ? ChartCarouselTooltip.resume
        : ChartCarouselTooltip.paused
    tabItem.componentData?.forEach(componentItem => {
      tMethod(componentItem.id)
      if (componentItem.component === 'Group')
        componentItem.propValue.forEach(groupItem => {
          tMethod(groupItem.id)
        })
    })
  })
}

/** 鼠标进入组件时标记悬停态 */
const handleMouseEnter = () => {
  state.hoverFlag = true
}

/** 鼠标离开组件时清除悬停态 */
const handleMouseLeave = () => {
  state.hoverFlag = false
}
/** 标签组件内部运行状态 */
const state = reactive<any>({
  activeTabName: '',
  curItem: {},
  textarea: '',
  dialogVisible: false,
  tabShow: true,
  hoverFlag: false
})
/** 标签标题区域是否出现横向滚动 */
const tabsAreaScroll = ref(false)

// 无边框颜色占位
const noBorderColor = ref('none')
/** 当前组件实例引用 */
let currentInstance

/** 是否显示标签标题 */
const showTabTitleFlag = computed(() => {
  if (element.value && element.value.style && element.value.style?.showTabTitle === false) {
    return false
  } else {
    return element.value.style?.showTabTitle
  }
})

/** 当前是否处于可编辑模式 */
const isEditMode = computed(() => editMode.value === 'edit' && isEdit.value && !mobileInPc.value)
/** 当前组件主题 */
const curThemes = isDashboard() ? 'light' : 'dark'
/** 计算标签标题是否需要启用滚动区域 */
const calcTabLength = () => {
  setTimeout(() => {
    const tabs = tabItems.value
    if (tabs.length > 1) {
      const containerDom = document.getElementById('tab-' + tabs[tabs.length - 1].name)
      if (containerDom) {
        tabsAreaScroll.value =
          containerDom?.parentNode?.clientWidth > tabComponentRef.value.clientWidth - 100
      }
    } else {
      tabsAreaScroll.value = false
    }
  })
}

/** 统一包装下拉命令和命令参数 */
const beforeHandleCommand = (item, param) => {
  return {
    command: item,
    param: param
  }
}
/** 仪表板预览模式下的组件间距 */
const curPreviewGap = computed(() =>
  dvInfo.value.type === 'dashboard' && canvasStyleData.value['dashboard'].gap === 'yes'
    ? canvasStyleData.value['dashboard'].gapSize
    : 0
)

/** 确认当前标签标题修改并记录快照 */
function sureCurTitle() {
  state.curItem.title = state.textarea
  state.dialogVisible = false
  snapshotStore.recordSnapshotCache('sureCurTitle')
}

/** 新增一个空白标签页 */
function addTab() {
  const newName = guid()
  const newTab = {
    name: newName,
    title: t('visualization.new_tab'),
    hidden: false,
    componentData: [],
    closable: true
  }
  ensureTabItems().push(newTab)
  element.value.editableTabsValue = newTab.name
  snapshotStore.recordSnapshotCache('addTab')
}

/** 删除指定标签页并切换到相邻标签 */
function deleteCur(param) {
  state.curItem = param
  const tabs = ensureTabItems()
  let len = tabs.length
  while (len--) {
    if (tabs[len].name === param.name) {
      const deletedTab = tabs[len]
      tabs.splice(len, 1)
      if (tabs.length) {
        const activeIndex = (len - 1 + tabs.length) % tabs.length
        element.value.editableTabsValue = tabs[activeIndex].name
      } else {
        element.value.editableTabsValue = ''
      }
      state.tabShow = false
      nextTick(() => {
        state.tabShow = true
        deletedTab.componentData?.forEach(tabComponent => {
          checkFilterRemove(tabComponent)
        })
      })
    }
  }
}
/** 复制指定标签页及其内部组件 */
function copyCur(param) {
  addTab()
  const tabs = ensureTabItems()
  const newTabItem = tabs[tabs.length - 1]
  if (!newTabItem) return
  const idMap = {}
  const newCanvasId = element.value.id + '--' + newTabItem.name
  newTabItem.componentData = deepCopyTabItemHelper(newCanvasId, param.componentData, idMap)
  dvMainStore.updateCopyCanvasView(idMap)
}

/** 打开当前标签标题编辑弹窗 */
function editCurTitle(param) {
  state.activeTabName = param.name
  state.curItem = param
  state.textarea = param.title
  state.dialogVisible = true
}

/** 分发标签操作菜单命令 */
function handleCommand(command) {
  switch (command.command) {
    case 'editTitle':
      editCurTitle(command.param)
      break
    case 'deleteCur':
      deleteCur(command.param)
      snapshotStore.recordSnapshotCache('deleteCur')
      break
    case 'copyCur':
      copyCur(command.param)
      snapshotStore.recordSnapshotCache('copyCur')
      break
  }
}

/** 刷新当前资源的图表联动信息 */
const reloadLinkage = () => {
  // 刷新联动信息
  if (dvInfo.value.id) {
    const resourceTable = ['canvas', 'canvasDataV', 'edit'].includes(showPosition.value)
      ? 'snapshot'
      : 'core'
    getPanelAllLinkageInfo(dvInfo.value.id, resourceTable).then(rsp => {
      dvMainStore.setNowPanelTrackInfo(rsp.data)
    })
  }
}

/** 将主画布组件移入当前激活标签页 */
const componentMoveIn = component => {
  element.value.propValue.forEach((tabItem, index) => {
    if (element.value.editableTabsValue === tabItem.name) {
      //获取主画布当前组件的index
      if (isDashboard()) {
        eventBus.emit('removeMatrixItemById-canvas-main', component.id)
        dvMainStore.setCurComponent({ component: null, index: null })
        component.canvasId = element.value.id + '--' + tabItem.name
        const refInstance = currentInstance?.refs?.['tabCanvas_' + index]?.[0]
        if (refInstance) {
          const matrixBase = refInstance.getBaseMatrixSize() // 矩阵基础大小。
          canvasChangeAdaptor(component, matrixBase)
          component.x = 1
          component.y = 200
          component.style.left = 0
          component.style.top = 0
          tabItem.componentData.push(component)
          refInstance.addItemBox(component) //在适当的时候初始化布局组件
          nextTick(() => {
            refInstance.canvasInitImmediately()
          })
        }
      } else {
        const curIndex = findComponentIndexById(component.id)
        // 从主画布删除
        dvMainStore.deleteComponent(curIndex)
        dvMainStore.setCurComponent({ component: null, index: null })
        component.canvasId = element.value.id + '--' + tabItem.name
        dataVTabComponentAdd(component, element.value)
        tabItem.componentData.push(component)
      }
    }
  })

  reloadLinkage()
}

/** 将标签页内组件移回主画布 */
const componentMoveOut = component => {
  if (isDashboard()) {
    canvasChangeAdaptor(component, bashMatrixInfo.value, true)
  }
  // 从Tab画布中移除
  eventBus.emit('removeMatrixItemById-' + component.canvasId, component.id)
  dvMainStore.setCurComponent({ component: null, index: null })
  // 主画布中添加
  if (isDashboard()) {
    eventBus.emit('moveOutFromTab-canvas-main', component)
  } else {
    addToMain(component)
  }
  reloadLinkage()
}

/** 将数据大屏标签页组件添加回主画布 */
const addToMain = component => {
  const { left, top } = element.value.style
  component.style.left = component.style.left + left
  component.style.top = component.style.top + top
  component.canvasId = 'canvas-main'
  dvMainStore.addComponent({
    component,
    index: undefined,
    isFromGroup: true
  })
}

/** 当前标签组件是否处于拖入激活态 */
const moveActive = computed(() => {
  return tabMoveInActiveId.value && tabMoveInActiveId.value === element.value.id
})

/** 标签标题区域的对齐样式类 */
const headClass = computed(() => {
  if (tabsAreaScroll.value) {
    return 'tab-head-left'
  } else {
    return 'tab-head-' + element.value.style.headHorizontalPosition
  }
})

/** 根据标题背景配置生成样式对象 */
const backgroundStyle = backgroundParams => {
  if (backgroundParams) {
    const {
      backdropFilterEnable,
      backdropFilter,
      backgroundColorSelect,
      backgroundColor,
      backgroundImageEnable,
      backgroundType,
      outerImage,
      innerPadding,
      borderRadius
    } = backgroundParams
    const commonBackground = backgroundParams as CommonBackground
    const innerPaddingTarget = ['Group'].includes(element.value.component) ? 0 : innerPadding
    let innerPaddingStyle = innerPaddingTarget * scale.value + 'px'
    const paddingMode = commonBackground.innerPadding?.mode
    if (paddingMode === ShorthandMode.Uniform) {
      innerPaddingStyle = `${commonBackground.innerPadding?.top * scale.value}px`
    } else if (paddingMode === ShorthandMode.Axis) {
      innerPaddingStyle = `${commonBackground.innerPadding?.top * scale.value}px ${
        commonBackground.innerPadding?.left * scale.value
      }px`
    } else if (paddingMode === ShorthandMode.PerEdge) {
      innerPaddingStyle = `${commonBackground.innerPadding?.top * scale.value}px ${
        commonBackground.innerPadding?.right * scale.value
      }px ${commonBackground.innerPadding?.bottom * scale.value}px ${
        commonBackground.innerPadding?.left * scale.value
      }px`
    }

    let borderRadiusStyle = borderRadius + 'px'
    const borderRadiusMode = commonBackground.borderRadius?.mode
    if (borderRadiusMode === ShorthandMode.Uniform) {
      borderRadiusStyle = `${commonBackground.borderRadius?.topLeft * scale.value}px`
    } else if (borderRadiusMode === ShorthandMode.Axis) {
      borderRadiusStyle = `${commonBackground.borderRadius?.topLeft * scale.value}px ${
        commonBackground.borderRadius?.bottomLeft * scale.value
      }px`
    } else if (borderRadiusMode === ShorthandMode.PerEdge) {
      borderRadiusStyle = `${commonBackground.borderRadius?.topLeft * scale.value}px ${
        commonBackground.borderRadius?.topRight * scale.value
      }px ${commonBackground.borderRadius?.bottomRight * scale.value}px ${
        commonBackground.borderRadius?.bottomLeft * scale.value
      }px`
    }

    let style = {
      padding: innerPaddingStyle,
      borderRadius: borderRadiusStyle
    }
    let colorRGBA = ''
    if (backgroundColorSelect && backgroundColor) {
      colorRGBA = backgroundColor
    }
    if (colorRGBA) {
      style['backgroundColor'] = colorRGBA
    }
    if (
      backgroundImageEnable &&
      backgroundType === 'outerImage' &&
      typeof outerImage === 'string'
    ) {
      setBackgroundImageStyle(style, outerImage)
    }
    if (backdropFilterEnable) {
      style['backdrop-filter'] = 'blur(' + backdropFilter + 'px)'
    }
    return style
  }
  return {}
}

/** 根据标签激活状态生成标题样式 */
const titleStyle = itemName => {
  let style = {}
  if (element.value.editableTabsValue === itemName) {
    style = {
      textDecoration: element.value.style.textDecoration,
      fontStyle: element.value.style.fontStyle,
      fontWeight: element.value.style.fontWeight,
      fontSize: (element.value.style.activeFontSize || 18) + 'px',
      lineHeight: (element.value.style.activeFontSize || 18) + 'px'
    }
    if (element.value.titleBackground?.enable) {
      style = {
        ...style,
        ...backgroundStyle(element.value.titleBackground.active)
      }
    }
  } else {
    style = {
      textDecoration: element.value.style.textDecoration,
      fontStyle: element.value.style.fontStyle,
      fontWeight: element.value.style.fontWeight,
      fontSize: (element.value.style.fontSize || 16) + 'px',
      lineHeight: (element.value.style.fontSize || 16) + 'px'
    }
    if (element.value.titleBackground?.enable) {
      style = {
        ...style,
        ...backgroundStyle(
          element.value.titleBackground.multiply
            ? element.value.titleBackground.active
            : element.value.titleBackground.inActive
        )
      }
    }
  }
  return style
}

/** 标签标题默认字体颜色 */
const fontColor = computed(() => {
  if (
    element.value &&
    element.value.style &&
    element.value.style.headFontColor &&
    typeof element.value.style.headFontColor === 'string'
  ) {
    return element.value.style.headFontColor
  } else {
    return 'none'
  }
})

/** 标签标题激活态字体颜色 */
const activeColor = computed(() => {
  if (
    element.value &&
    element.value.style &&
    element.value.style.headFontActiveColor &&
    typeof element.value.style.headFontActiveColor === 'string'
  ) {
    return element.value.style.headFontActiveColor
  } else {
    return 'none'
  }
})

/** 标签标题激活态边框颜色 */
const borderActiveColor = computed(() => {
  if (
    element.value &&
    element.value.style &&
    element.value.style.headBorderActiveColor &&
    typeof element.value.style.headBorderActiveColor === 'string'
  ) {
    return element.value.style.headBorderActiveColor
  } else {
    return 'none'
  }
})

/** 当前编辑标题是否有效 */
const titleValid = computed(() => {
  return !!state.textarea && !!state.textarea.trim()
})

/** 防抖刷新图表 Tooltip 轮播状态 */
const viewToolTipsChangeDebounce = debounce(() => {
  viewToolTipsChange()
}, 500)

/** 画布缩放变化时刷新 Tooltip 轮播状态 */
watch(
  () => scale.value,
  () => {
    viewToolTipsChangeDebounce()
  }
)

/** 组件配置变化时刷新标题滚动和 Tooltip 轮播状态 */
watch(
  () => element.value,
  () => {
    calcTabLength()
    viewToolTipsChangeDebounce()
  },
  { deep: true }
)

/** 强制重建标签内容区域 */
const reShow = () => {
  state.tabShow = false
  nextTick(() => {
    state.tabShow = true
  })
}

/** 编辑模式变化时同步自动轮播状态 */
watch(
  () => isEditMode.value,
  () => {
    initCarousel()
  }
)

/** 初始化非编辑态下的标签自动轮播 */
const initCarousel = () => {
  carouselTimer && clearInterval(carouselTimer)
  carouselTimer = null
  if (!isEditMode.value) {
    if (element.value.carousel?.enable) {
      const switchTime = (element.value.carousel.time || 5) * 1000
      // 过滤出可见的标签页
      const visibleTabs = tabItems.value.filter(tab => !tab.hidden)

      // 如果没有可见的标签页，则不启动轮播
      if (visibleTabs.length === 0) return
      let switchCount = 1
      // 轮播定时器
      carouselTimer = setInterval(() => {
        // 鼠标移入时 停止轮播
        if (!state.hoverFlag) {
          const nowIndex = switchCount % visibleTabs.length
          switchCount++
          nextTick(() => {
            element.value.editableTabsValue = visibleTabs[nowIndex].name
          })
        }
      }, switchTime)
    }
  }
}

/** 组件挂载后注册事件并初始化标签状态 */
onMounted(() => {
  document.addEventListener('visibilitychange', viewToolTipsChange)
  const tabs = tabItems.value
  if (tabs.length > 0) {
    element.value.editableTabsValue = tabs[0].name
  }
  calcTabLength()
  if (['canvas', 'canvasDataV', 'edit'].includes(showPosition.value) && !mobileInPc.value) {
    eventBus.on('onTabMoveIn-' + element.value.id, componentMoveIn)
    eventBus.on('onTabMoveOut-' + element.value.id, componentMoveOut)
    eventBus.on('onTabSortChange-' + element.value.id, reShow)
    eventBus.on('onTabDelete-' + element.value.id, deleteCur)
    eventBus.on('onTabCopy-' + element.value.id, copyCur)
  }
  currentInstance = getCurrentInstance()
  initCarousel()
  nextTick(() => {
    groupSizeStyleAdaptor(element.value)
  })
  setTimeout(() => {
    viewToolTipsChange()
  }, 1000)
  useEmitt({
    name: 'showEnlargeDialog',
    callback: show => {
      if (show) {
        carouselTimer && clearInterval(carouselTimer)
      } else {
        initCarousel()
      }
    }
  })
})
/** 组件卸载前解绑事件监听 */
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', viewToolTipsChange)
  if (['canvas', 'canvasDataV', 'edit'].includes(showPosition.value) && !mobileInPc.value) {
    eventBus.off('onTabMoveIn-' + element.value.id, componentMoveIn)
    eventBus.off('onTabMoveOut-' + element.value.id, componentMoveOut)
    eventBus.off('onTabSortChange-' + element.value.id, reShow)
    eventBus.off('onTabDelete-' + element.value.id, deleteCur)
    eventBus.off('onTabCopy-' + element.value.id, copyCur)
  }
})
onBeforeMount(() => {
  if (carouselTimer) {
    clearInterval(carouselTimer)
    carouselTimer = null
  }
})
</script>
<style lang="less" scoped>
.title-hidde-tab {
  :deep(.ed-tabs__content) {
    height: 100% !important;
  }
}

.title-show-tab {
  :deep(.ed-tabs__content) {
    height: calc(100% - 46px) !important;
  }
  :deep(.ed-tabs__item) {
    font-family: inherit;
    padding-right: 0 !important;
  }
}

.ed-tabs-dark {
  :deep(.ed-tabs__new-tab) {
    margin-right: 25px;
    color: #fff;
  }
  :deep(.el-dropdown-link) {
    color: #fff;
  }
}
.ed-tabs-light {
  :deep(.ed-tabs__new-tab) {
    margin-right: 25px;
    background-color: #fff;
  }
}
.el-tab-pane-custom {
  width: 100%;
}
.canvas-move-in {
  border: 2px dotted transparent;
  border-color: blueviolet;
}

.tab-head-left :deep(.ed-tabs__nav-scroll) {
  display: flex;
  justify-content: flex-start;
}

.tab-head-right :deep(.ed-tabs__nav-scroll) {
  display: flex;
  justify-content: flex-end;
}

.tab-head-center :deep(.ed-tabs__nav-scroll) {
  display: flex;
  justify-content: center;
}

.switch-hidden {
  opacity: 0;
  z-index: -1;
}

.tab-content-custom {
  position: absolute;
  width: 100%;
  height: 100%;
  font-style: normal;
  font-weight: normal;
}
.custom-tab-title {
  .title-inner {
    position: relative;
    background-size: 100% 100% !important;
  }
  :deep(.ed-dropdown) {
    vertical-align: middle !important;
  }
}
</style>
