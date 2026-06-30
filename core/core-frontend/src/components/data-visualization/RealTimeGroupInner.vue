<script lang="ts" setup>
import group from '@/assets/svg/group.svg'
import bar from '@/assets/svg/bar.svg'
import dbMoreWeb from '@/assets/svg/db-more-web.svg'
import dvMoreTimeClock from '@/assets/svg/dv-more-time-clock.svg'
import dvPictureReal from '@/assets/svg/dv-picture-real.svg'
import dvTab from '@/assets/svg/dv-tab.svg'
import iconStream from '@/assets/svg/icon-stream.svg'
import iconVideo from '@/assets/svg/icon-video.svg'
import icon_graphical from '@/assets/svg/icon_graphical.svg'
import icon_search from '@/assets/svg/icon_search.svg'
import other_material_board from '@/assets/svg/other_material_board.svg'
import other_material_icon from '@/assets/svg/other_material_icon.svg'
import scrollText from '@/assets/svg/scroll-text.svg'
import areaOrigin from '@/assets/svg/area-origin.svg'
import areaStackOrigin from '@/assets/svg/area-stack-origin.svg'
import barGroupOrigin from '@/assets/svg/bar-group-origin.svg'
import barGroupStackOrigin from '@/assets/svg/bar-group-stack-origin.svg'
import barHorizontalOrigin from '@/assets/svg/bar-horizontal-origin.svg'
import barOrigin from '@/assets/svg/bar-origin.svg'
import barRangeOrigin from '@/assets/svg/bar-range-origin.svg'
import barStackHorizontalOrigin from '@/assets/svg/bar-stack-horizontal-origin.svg'
import barStackOrigin from '@/assets/svg/bar-stack-origin.svg'
import bidirectionalBarOrigin from '@/assets/svg/bidirectional-bar-origin.svg'
import chartMixGroupOrigin from '@/assets/svg/chart-mix-group-origin.svg'
import chartMixOrigin from '@/assets/svg/chart-mix-origin.svg'
import chartMixStackOrigin from '@/assets/svg/chart-mix-stack-origin.svg'
import chartMixDualLineOrigin from '@/assets/svg/chart-mix-dual-line-origin.svg'
import funnelOrigin from '@/assets/svg/funnel-origin.svg'
import gaugeOrigin from '@/assets/svg/gauge-origin.svg'
import indicatorOrigin from '@/assets/svg/indicator-origin.svg'
import lineOrigin from '@/assets/svg/line-origin.svg'
import liquidOrigin from '@/assets/svg/liquid-origin.svg'
import percentageBarStackHorizontalOrigin from '@/assets/svg/percentage-bar-stack-horizontal-origin.svg'
import percentageBarStackOrigin from '@/assets/svg/percentage-bar-stack-origin.svg'
import pieDonutOrigin from '@/assets/svg/pie-donut-origin.svg'
import pieDonutRoseOrigin from '@/assets/svg/pie-donut-rose-origin.svg'
import pieOrigin from '@/assets/svg/pie-origin.svg'
import pieRoseOrigin from '@/assets/svg/pie-rose-origin.svg'
import progressBarOrigin from '@/assets/svg/progress-bar-origin.svg'
import quadrantOrigin from '@/assets/svg/quadrant-origin.svg'
import radarOrigin from '@/assets/svg/radar-origin.svg'
import richTextOrigin from '@/assets/svg/rich-text-origin.svg'
import sankeyOrigin from '@/assets/svg/sankey-origin.svg'
import scatterOrigin from '@/assets/svg/scatter-origin.svg'
import stockLineOrigin from '@/assets/svg/stock-line-origin.svg'
import tableInfoOrigin from '@/assets/svg/table-info-origin.svg'
import tableNormalOrigin from '@/assets/svg/table-normal-origin.svg'
import tablePivotOrigin from '@/assets/svg/table-pivot-origin.svg'
import treemapOrigin from '@/assets/svg/treemap-origin.svg'
import waterfallOrigin from '@/assets/svg/waterfall-origin.svg'
import wordCloudOrigin from '@/assets/svg/word-cloud-origin.svg'
import tHeatmapOrigin from '@/assets/svg/t-heatmap-origin.svg'
import dvEyeClose from '@/assets/svg/dv-eye-close.svg'
import dvShow from '@/assets/svg/dv-show.svg'
import dvUnlock from '@/assets/svg/dv-unlock.svg'
import dvLock from '@/assets/svg/dv-lock.svg'
import dvMore from '@/assets/svg/dv-more.svg'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { layerStoreWithOut } from '@/store/modules/data-visualization/layer'
import { storeToRefs } from 'pinia'
import { ElIcon, ElMessage, ElRow } from 'element-plus-secondary'
import Icon from '../icon-custom/src/Icon.vue'
import { nextTick, PropType, ref, toRefs } from 'vue'
import draggable from 'vuedraggable'
import { lockStoreWithOut } from '@/store/modules/data-visualization/lock'
import ContextMenuAsideDetails from '@/components/data-visualization/canvas/ContextMenuAsideDetails.vue'
import ComposeShow from '@/components/data-visualization/canvas/ComposeShow.vue'
import { composeStoreWithOut } from '@/store/modules/data-visualization/compose'
import circlePackingOrigin from '@/assets/svg/circle-packing-origin.svg'
import bulletGraphOrigin from '@/assets/svg/bullet-graph-origin.svg'
import { syncViewTitle } from '@/utils/canvasUtils'
import { useI18n } from '@/hooks/web/useI18n'
// 图层更多操作下拉实例，供右键菜单关闭和重命名流程复用
const dropdownMore = ref(null)
const lockStore = lockStoreWithOut()
const { t } = useI18n()
const dvMainStore = dvMainStoreWithOut()
const snapshotStore = snapshotStoreWithOut()
const layerStore = layerStoreWithOut()
const composeStore = composeStoreWithOut()

// 框选区域和当前组件状态分别来自组合、画布仓库，图层列表只负责同步选择结果
const { areaData } = storeToRefs(composeStore)

const { curComponent, canvasViewInfo } = storeToRefs(dvMainStore)

// 接收图层列表位置和组件数据；内层列表只展示当前分组或标签页下的子组件
const props = defineProps({
  tabPosition: {
    type: String,
    required: false,
    default: 'main'
  },
  componentData: {
    type: Array as PropType<any[]>,
    default: () => []
  }
})

const { componentData } = toRefs(props)

// 按视觉倒序获取组件
const getComponent = index => {
  return componentData.value[componentData.value.length - 1 - index]
}
// 将视觉索引转换为真实组件索引
const transformIndex = index => {
  return componentData.value.length - 1 - index
}

// 选择当前图层组件
const onClick = index => {
  setCurComponent(index)
  // 点击图层时清理框选区域
  areaData.value.components.splice(0, areaData.value.components.length)
}

// 设置当前选中组件
const setCurComponent = index => {
  dvMainStore.setCurComponent({ component: componentData.value[index], index })
}

let nameEdit = ref(false)
let editComponentId = ref('')
let inputName = ref('')
let nameInput = ref(null)
let curEditComponent = null
// 进入图层名称编辑状态
const editComponentName = item => {
  curEditComponent = curComponent.value
  editComponentId.value = `#component-label-${item.id}`
  nameEdit.value = true
  inputName.value = item.name
  nextTick(() => {
    nameInput.value.focus()
  })
}
// 保存图层名称并退出编辑状态
const closeEditComponentName = () => {
  nameEdit.value = false
  if (!inputName.value || !inputName.value.trim()) {
    return
  }
  if (inputName.value.trim() === curEditComponent.name) {
    return
  }
  if (inputName.value.length < 1 || inputName.value.length > 64) {
    ElMessage.warning(t('components.length_1_64_characters'))
    return
  }
  curEditComponent.name = inputName.value
  syncViewTitle(curEditComponent)
  inputName.value = ''
  curEditComponent = null
}

// 锁定当前组件
const lock = () => {
  setTimeout(() => {
    lockStore.lock()
    snapshotStore.recordSnapshotCache('realTime-lock')
  })
}

// 解锁当前组件
const unlock = () => {
  setTimeout(() => {
    lockStore.unlock()
    snapshotStore.recordSnapshotCache('realTime-unlock')
  })
}

// 隐藏当前组件
const hideComponent = () => {
  setTimeout(() => {
    layerStore.hideComponent()
    snapshotStore.recordSnapshotCache('realTime-hideComponent')
  })
}

// 显示当前组件
const showComponent = () => {
  setTimeout(() => {
    layerStore.showComponent()
    snapshotStore.recordSnapshotCache('showComponent')
  })
}

// 拖拽结束后同步倒序图层顺序
const dragOnEnd = ({ oldIndex, newIndex }) => {
  const source = componentData.value[newIndex]
  const comLength = componentData.value.length
  // 先还原拖拽库调整过的数组顺序
  componentData.value.splice(newIndex, 1)
  componentData.value.splice(oldIndex, 0, source)
  const target = componentData.value[comLength - 1 - oldIndex]
  // 再按图层视觉倒序移动真实目标组件
  componentData.value.splice(comLength - 1 - oldIndex, 1)
  componentData.value.splice(comLength - 1 - newIndex, 0, target)
  dvMainStore.setCurComponent({ component: target, index: transformIndex(comLength - oldIndex) })
}

const iconMap = {
  bar: bar,
  'db-more-web': dbMoreWeb,
  'dv-more-time-clock': dvMoreTimeClock,
  'dv-picture-real': dvPictureReal,
  'dv-tab': dvTab,
  'icon-stream': iconStream,
  'icon-video': iconVideo,
  icon_graphical: icon_graphical,
  icon_search: icon_search,
  other_material_board: other_material_board,
  other_material_icon: other_material_icon,
  'scroll-text': scrollText,
  'area-origin': areaOrigin,
  'area-stack-origin': areaStackOrigin,
  'bar-group-origin': barGroupOrigin,
  'bar-group-stack-origin': barGroupStackOrigin,
  'bar-horizontal-origin': barHorizontalOrigin,
  'bar-origin': barOrigin,
  'bar-range-origin': barRangeOrigin,
  'bar-stack-horizontal-origin': barStackHorizontalOrigin,
  'bar-stack-origin': barStackOrigin,
  'bidirectional-bar-origin': bidirectionalBarOrigin,
  'chart-mix-group-origin': chartMixGroupOrigin,
  'chart-mix-origin': chartMixOrigin,
  'chart-mix-stack-origin': chartMixStackOrigin,
  'chart-mix-dual-line': chartMixDualLineOrigin,
  'funnel-origin': funnelOrigin,
  'gauge-origin': gaugeOrigin,
  'indicator-origin': indicatorOrigin,
  'line-origin': lineOrigin,
  'liquid-origin': liquidOrigin,
  'percentage-bar-stack-horizontal-origin': percentageBarStackHorizontalOrigin,
  'percentage-bar-stack-origin': percentageBarStackOrigin,
  'pie-donut-origin': pieDonutOrigin,
  'pie-donut-rose-origin': pieDonutRoseOrigin,
  'pie-origin': pieOrigin,
  'pie-rose-origin': pieRoseOrigin,
  'progress-bar-origin': progressBarOrigin,
  'quadrant-origin': quadrantOrigin,
  'radar-origin': radarOrigin,
  'rich-text-origin': richTextOrigin,
  'sankey-origin': sankeyOrigin,
  'scatter-origin': scatterOrigin,
  'stock-line-origin': stockLineOrigin,
  'table-info-origin': tableInfoOrigin,
  'table-normal-origin': tableNormalOrigin,
  'table-pivot-origin': tablePivotOrigin,
  'treemap-origin': treemapOrigin,
  'waterfall-origin': waterfallOrigin,
  'word-cloud-origin': wordCloudOrigin,
  't-heatmap-origin': tHeatmapOrigin,
  group: group,
  'circle-packing-origin': circlePackingOrigin,
  'bullet-graph-origin': bulletGraphOrigin
}
// 根据组件类型解析图层图标，图表组件需要从画布视图信息反查真实图表类型
const getIconName = item => {
  if (item.component === 'UserView') {
    const viewInfo = canvasViewInfo.value[item.id]
    return iconMap[`${viewInfo.type}-origin`]
  } else {
    return iconMap[item.icon]
  }
}

// 关闭右键菜单并按需进入重命名状态，延迟执行用于等待下拉层销毁完成
const menuAsideClose = (param, index) => {
  const iconDom = document.getElementById('close-button')
  if (iconDom) {
    iconDom.click()
  }
  if (param?.opt === 'rename') {
    setTimeout(() => {
      editComponentName(getComponent(index))
    }, 200)
  }
}

// 拦截原生右键菜单，右键操作由组件内的下拉菜单统一承接
const handleContextMenu = e => {
  e.preventDefault()
  // 记录鼠标点击位置以定位临时菜单容器
  const x = e.clientX
  const y = e.clientY
  const customContextMenu = document.createElement('div')
  customContextMenu.style.position = 'fixed'
  customContextMenu.style.left = x + 'px'
  customContextMenu.style.top = y + 'px'

  // 将临时菜单容器添加到页面
  document.body.appendChild(customContextMenu)

  // 点击临时菜单后清理容器
  customContextMenu.addEventListener('click', () => {
    document.body.removeChild(customContextMenu)
  })
}
// 切换分组图层展开状态，展开后由上层列表继续递归渲染子图层
const expandClick = component => {
  component['expand'] = !component['expand']
}
</script>

<template>
  <!-- 图层面板按视觉层级倒序展示，拖拽和选中索引需要同步转换到真实组件数组。 -->
  <div class="real-time-component-list">
    <button hidden="true" id="close-button"></button>
    <el-row class="list-wrap">
      <div class="list-container" @contextmenu="handleContextMenu">
        <draggable
          @end="dragOnEnd"
          :list="componentData"
          animation="100"
          class="drag-list"
          item-key="id"
        >
          <template #item="{ index }">
            <div>
              <div
                :title="getComponent(index)?.name"
                class="component-item"
                :class="{
                  'container-item-not-show': !getComponent(index)?.isShow,
                  'component-item-group-tab': tabPosition === 'groupTab',
                  activated:
                    (curComponent && curComponent?.id === getComponent(index)?.id) ||
                    areaData.components.includes(getComponent(index))
                }"
                @click="onClick(transformIndex(index))"
              >
                <el-icon class="component-icon">
                  <Icon><component :is="getIconName(getComponent(index))"></component></Icon>
                </el-icon>
                <span
                  :id="`component-label-${getComponent(index)?.id}`"
                  class="component-label"
                  @dblclick="editComponentName(getComponent(index))"
                >
                  {{ getComponent(index)?.name }}
                </span>
                <div
                  v-show="!nameEdit || (nameEdit && curComponent?.id !== getComponent(index)?.id)"
                  class="icon-container"
                  :class="{
                    'icon-container-lock':
                      getComponent(index)?.isLock && getComponent(index)?.isShow,
                    'icon-container-show': !getComponent(index)?.isShow
                  }"
                >
                  <el-icon
                    class="component-base component-icon-display"
                    v-show="!getComponent(index).isShow"
                    @click="showComponent"
                  >
                    <Icon name="dv-eye-close"><dvEyeClose class="svg-icon opt-icon" /></Icon>
                  </el-icon>
                  <el-icon
                    class="component-base"
                    v-show="getComponent(index)?.isShow"
                    @click="hideComponent"
                  >
                    <Icon name="dv-show"><dvShow class="svg-icon opt-icon" /></Icon>
                  </el-icon>
                  <el-icon
                    v-show="!getComponent(index)?.isLock"
                    class="component-base"
                    @click="lock"
                  >
                    <Icon name="dv-unlock"><dvUnlock class="svg-icon opt-icon" /></Icon>
                  </el-icon>
                  <el-icon
                    class="component-base component-icon-display"
                    v-show="getComponent(index)?.isLock"
                    @click="unlock"
                  >
                    <Icon name="dv-lock"><dvLock class="svg-icon opt-icon" /></Icon>
                  </el-icon>
                  <el-dropdown
                    ref="dropdownMore"
                    trigger="click"
                    placement="bottom-start"
                    effect="dark"
                    :hide-timeout="0"
                  >
                    <span :class="'dropdownMore-' + index" @click="onClick(transformIndex(index))">
                      <el-icon class="component-base">
                        <Icon name="dv-more"><dvMore class="svg-icon opt-icon" /></Icon>
                      </el-icon>
                    </span>
                    <template #dropdown>
                      <context-menu-aside-details
                        :element="getComponent(index)"
                        @close="menuAsideClose($event, index)"
                      ></context-menu-aside-details>
                    </template>
                  </el-dropdown>
                </div>
                <el-dropdown
                  class="compose-dropdown"
                  trigger="contextmenu"
                  placement="bottom-start"
                  effect="dark"
                  :hide-timeout="0"
                >
                  <compose-show
                    :show-border="false"
                    :element-index="transformIndex(index)"
                    :element="getComponent(index)"
                  ></compose-show>
                  <template #dropdown>
                    <context-menu-aside-details
                      :element="getComponent(index)"
                      @close="menuAsideClose($event, index)"
                    ></context-menu-aside-details>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </template>
        </draggable>
      </div>
    </el-row>
    <Teleport v-if="editComponentId && nameEdit" :to="editComponentId">
      <input
        class="custom-teleport"
        @keydown.stop
        @keyup.stop
        ref="nameInput"
        v-model="inputName"
        @blur="closeEditComponentName"
      />
    </Teleport>
  </div>
</template>

<style lang="less" scoped>
.real-time-component-list {
  white-space: nowrap;
  .list-wrap {
    // 图层列表高度跟随工具栏预留空间，避免长列表覆盖底部操作区。
    max-height: calc(100% - @component-toolbar-height);
    overflow-y: auto;
    width: 100%;
    .list-container {
      width: 100%;
      .component-item {
        // 单行图层项固定高度，避免图标显示、编辑输入框和拖拽态造成列表抖动。
        position: relative;
        height: 30px;
        width: 100%;
        cursor: grab;
        color: @dv-canvas-main-font-color;
        display: flex;
        align-items: center;
        justify-content: flex-start;
        font-size: 12px;
        padding: 0 2px 0 28px;
        user-select: none;

        .component-icon {
          color: #a6a6a6;
          font-size: 14px;
        }
        .component-label {
          color: #ebebeb;
        }

        > span.component-label {
          font-size: 12px;
          margin-left: 10px;
          position: relative;
          min-width: 10px;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          input {
            position: absolute;
            left: 0;
            width: 100%;
            background-color: white;
            outline: none;
            border: none;
            border-radius: 2px;
            padding: 0 4px;
            height: 100%;
          }
        }

        &:active {
          cursor: grabbing;
        }

        &:hover {
          background-color: rgba(235, 235, 235, 0.1);

          .icon-container {
            // 操作区默认收起，悬停时展开显示锁定、显隐和更多操作。
            .component-base {
              opacity: 1;
            }
            width: 55px !important;
          }
        }

        .icon-container {
          .component-base {
            opacity: 0;
          }
          width: 0;
          display: flex;
          justify-content: flex-end;
          align-items: center;
          flex-grow: 1;
          cursor: none;
          i {
            font-size: 16px;
            cursor: pointer;
          }
        }
      }
      .activated {
        background-color: var(--ed-color-primary-1a, rgba(59, 130, 246, 0.1)) !important;
        :deep(.component-icon) {
          color: var(--ed-color-primary);
        }
        :deep(.component-label) {
          color: var(--ed-color-primary);
        }
      }
    }
  }
}

.real-time-component-list :deep(.ed-popper) {
  background: #303133 !important;
}

.component-base {
  // 统一图层操作按钮尺寸，保证内层列表与主图层列表视觉一致。
  cursor: pointer;
  height: 22px !important;
  width: 22px !important;
  border-radius: 6px;
  padding: 0 4px;

  .opt-icon {
    font-size: 14px;
  }

  &:hover {
    background: rgba(235, 235, 235, 0.1);
  }

  &:active {
    background: rgba(235, 235, 235, 0.1);
  }
}

.component-icon-display {
  opacity: 1 !important;
}

.icon-container-show {
  width: 55px !important;
}

.icon-container-lock {
  width: 45px !important;
}

.container-item-not-show {
  // 隐藏图层使用低亮文本和图标，保留可识别层级但降低视觉权重。
  color: #5f5f5f !important;
  :deep(.component-icon) {
    color: #5f5f5f !important;
  }
  :deep(.component-label) {
    color: #5f5f5f !important;
  }
}
.custom-teleport {
  background: #1a1a1a !important;
}
.component-item-group-tab {
  padding-left: 60px !important;
}

.component-expand {
  cursor: pointer;
  height: 16px !important;
  width: 16px !important;
  border-radius: 2px;
  padding: 0 2px;

  .expand-icon {
    font-size: 10px;
  }

  &:hover {
    background: rgba(235, 235, 235, 0.1);
  }

  &:active {
    background: rgba(235, 235, 235, 0.1);
  }
}
</style>

<style lang="less">
.compose-dropdown {
  position: initial !important;
}
</style>
