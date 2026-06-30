<template>
  <div>
    <div style="width: 100%">
      <el-dropdown trigger="click" @mouseup="handleMouseUp">
        <slot name="icon" />
        <el-dropdown-menu v-if="curComponent">
          <el-dropdown-item
            v-if="state.editFilter.includes(curComponent.type)"
            icon="el-icon-edit-outline"
            @click="edit"
            >{{ t('visualization.edit') }}
          </el-dropdown-item>
          <el-dropdown-item
            v-if="curComponent.type != 'custom-button'"
            icon="el-icon-document-copy"
            @click="copy"
            ><span
              >{{ t('visualization.copy') }}(<span v-show="state.systemOS === 'Mac'"
                ><i class="icon iconfont icon-command" />+ D</span
              >
              <span v-show="state.systemOS !== 'Mac'">Control + D</span>)</span
            >
          </el-dropdown-item>
          <el-dropdown-item icon="el-icon-delete" @click="deleteComponent"
            >{{ t('visualization.delete') }}
          </el-dropdown-item>
          <el-dropdown-item
            v-if="curComponent.component === 'Tabs'"
            icon="el-icon-sort"
            @click="openCustomSort"
            >{{ t('chart.sort') }}
          </el-dropdown-item>
          <el-dropdown-item v-if="!curComponent.auxiliaryMatrix">
            <el-dropdown placement="right-start">
              <span class="el-icon-copy-document">
                {{ t('visualization.level') }} <i class="el-icon-arrow-right el-icon--right" />
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item icon="el-icon-upload2" @click="topComponent"
                    >{{ t('visualization.topComponent') }}
                  </el-dropdown-item>
                  <el-dropdown-item icon="el-icon-download" @click="bottomComponent"
                    >{{ t('visualization.bottomComponent') }}
                  </el-dropdown-item>
                  <el-dropdown-item icon="el-icon-arrow-up" @click="upComponent"
                    >{{ t('visualization.upComponent') }}
                  </el-dropdown-item>
                  <el-dropdown-item icon="el-icon-arrow-down" @click="downComponent"
                    >{{ t('visualization.downComponent') }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-dropdown-item>
          <el-dropdown-item v-if="linkageSettingShow" icon="el-icon-link" @click="linkageSetting"
            >{{ t('visualization.linkage_setting') }}
          </el-dropdown-item>
          <el-dropdown-item
            v-if="tabCarouselShow"
            icon="el-icon-switch-button"
            @click="tabCarouselSet"
            >{{ t('visualization.carousel') }}
          </el-dropdown-item>
          <el-dropdown-item
            v-if="'Tabs' === curComponent.component"
            icon="el-icon-plus"
            @click="addTab"
            >{{ t('visualization.add_tab') }}
          </el-dropdown-item>
          <el-dropdown-item v-if="linkJumpSetShow" icon="el-icon-connection" @click="linkJumpSet"
            >{{ t('visualization.setting_jump') }}
          </el-dropdown-item>
          <el-dropdown-item
            v-if="curComponent.type != 'custom-button'"
            icon="el-icon-magic-stick"
            @click="boardSet"
            >{{ t('visualization.component_style') }}
          </el-dropdown-item>
          <el-dropdown-item v-if="curComponent.type != 'custom-button'" @click="hyperlinksSet"
            ><i class="icon iconfont icon-chaolianjie1" />{{ t('visualization.hyperlinks') }}
          </el-dropdown-item>
          <el-dropdown-item
            v-if="curComponent.type !== 'view' && !curComponent.auxiliaryMatrix"
            @click="positionAdjust"
          >
            <i class="el-icon-map-location" />
            {{ t('visualization.position_adjust') }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </el-dropdown>
    </div>
    <!--图表详情-->
    <el-dialog
      v-model:visible="state.hyperlinksSetVisible"
      width="400px"
      class="dialog-css"
      :destroy-on-close="true"
      :append-to-body="true"
      :show-close="true"
    >
      <hyperlinks-dialog
        v-if="state.hyperlinksSetVisible"
        :link-info="curComponent.hyperlinks"
        @onClose="state.hyperlinksSetVisible = false"
      ></hyperlinks-dialog>
    </el-dialog>

    <!--tab 轮播设计-->
    <el-dialog
      v-model:visible="state.tabCarouselVisible"
      width="400px"
      class="dialog-css"
      :destroy-on-close="true"
      :append-to-body="true"
      :show-close="true"
    >
      <tab-carousel-dialog
        v-if="state.tabCarouselVisible"
        @onClose="state.tabCarouselVisible = false"
      ></tab-carousel-dialog>
    </el-dialog>

    <el-dialog
      v-if="state.showCustomSort"
      :title="t('chart.custom_sort')"
      :visible="state.showCustomSort"
      :show-close="false"
      width="500px"
      :append-to-body="true"
      class="dialog-css"
    >
      <custom-tabs-sort ref="customTabsSort" :element="curComponent" />
      <template #footer>
        <div class="dialog-footer">
          <el-button size="mini" @click="closeCustomSort">{{ t('chart.cancel') }} </el-button>
          <el-button type="primary" size="mini" @click="saveCustomSort"
            >{{ t('chart.confirm') }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { viewLinkageGather } from '@/api/visualization/linkage'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import eventBus from '@/utils/eventBus'
import { copyStoreWithOut } from '@/store/modules/data-visualization/copy'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { layerStoreWithOut } from '@/store/modules/data-visualization/layer'
import { useI18n } from '@/hooks/web/useI18n'
import TabCarouselDialog from '@/components/visualization/TabCarouselDialog.vue'
import HyperlinksDialog from '@/components/visualization/HyperlinksDialog.vue'
/**
 * 国际化翻译函数
 */
const { t } = useI18n()
/**
 * 快照仓库，用于菜单操作后记录撤销点
 */
const snapshotStore = snapshotStoreWithOut()
/**
 * 复制仓库，用于组件复制和粘贴
 */
const copyStore = copyStoreWithOut()
/**
 * 大屏主仓库，提供当前组件和画布组件数据
 */
const dvMainStore = dvMainStoreWithOut()
/**
 * 图层仓库，用于调整组件层级
 */
const layerStore = layerStoreWithOut()
/**
 * 当前组件、组件列表和大屏信息引用
 */
const { curComponent, componentData, dvInfo } = storeToRefs(dvMainStore)
/**
 * 自定义标签排序组件引用
 */
const customTabsSort = ref(null)
/**
 * 右键菜单向父级派发的操作事件
 */
const emits = defineEmits(['amRemoveItem', 'linkJumpSet', 'boardSet'])
/**
 * 组件右键菜单状态
 */
const state = reactive({
  tabCarouselVisible: false,
  systemOS: 'Mac',
  showCustomSort: false,
  jumpExcludeViewType: [
    'richTextView',
    'liquid',
    'gauge',
    'indicator',
    'label',
    'word-cloud',
    'flow-map',
    'bidirectional-bar',
    'symbolic-map'
  ],
  linkageExcludeViewType: [
    'richTextView',
    'liquid',
    'gauge',
    'indicator',
    'label',
    'word-cloud',
    'flow-map',
    'bidirectional-bar',
    'symbolic-map'
  ],
  copyData: null,
  hyperlinksSetVisible: false,
  editFilter: ['view', 'custom', 'custom-button']
})

/**
 * 是否展示标签页轮播设置入口
 */
const tabCarouselShow = computed(() => {
  return curComponent.value.component === 'Tabs'
})

/**
 * 是否展示跳转设置入口
 */
const linkJumpSetShow = computed(() => {
  return (
    curComponent.value.type === 'view' &&
    !state.jumpExcludeViewType.includes(curComponent.value.propValue.innerType) &&
    !(
      curComponent.value.propValue.innerType?.includes('table') &&
      curComponent.value.propValue.render === 'echarts'
    )
  )
})

/**
 * 是否展示图表联动设置入口
 */
const linkageSettingShow = computed(() => {
  return (
    curComponent.value.type === 'view' &&
    !state.linkageExcludeViewType.includes(curComponent.value.propValue.innerType) &&
    !(
      curComponent.value.propValue.innerType?.includes('table') &&
      curComponent.value.propValue.render === 'echarts'
    )
  )
})

/**
 * 打开自定义标签排序弹窗
 */
const openCustomSort = () => {
  state.showCustomSort = true
}
/**
 * 关闭自定义标签排序弹窗
 */
const closeCustomSort = () => {
  state.showCustomSort = false
}
/**
 * 保存自定义标签排序配置
 */
const saveCustomSort = () => {
  customTabsSort.value.save()
  nextTick(() => {
    state.showCustomSort = false
  })
}
/**
 * 打开组件位置调整面板
 */
const positionAdjust = () => {
  eventBus.emit('change_panel_right_draw', true)
}

/**
 * 根据组件类型打开对应编辑入口
 */
const edit = () => {
  if (curComponent.value.type === 'custom') {
    eventBus.emit('component-dialog-edit', 'update')
  } else if (curComponent.value.type === 'custom-button') {
    eventBus.emit('button-dialog-edit')
  } else if (
    curComponent.value.type === 'v-text' ||
    curComponent.value.type === 'rich-text' ||
    curComponent.value.type === 'rect-shape'
  ) {
    eventBus.emit('component-dialog-style')
  } else {
    eventBus.emit('change_panel_right_draw', true)
  }
}

// 点击菜单时不取消当前组件的选中状态
const handleMouseUp = () => {
  dvMainStore.setClickComponentStatus(true)
}

/**
 * 复制当前组件并立即粘贴
 */
const copy = () => {
  copyStore.copy()
  copyStore.paste(false)
}

/**
 * 删除当前组件并记录快照
 */
const deleteComponent = () => {
  if (
    curComponent.value.type === 'custom-button' &&
    curComponent.value.serviceName === 'buttonSureWidget'
  ) {
    let len = componentData.value.length
    while (len--) {
      const item = componentData.value[len]

      if (item.type === 'custom-button' && item.serviceName === 'buttonResetWidget') {
        componentData.value.splice(len, 1)
      }
    }
  }
  emits('amRemoveItem')
  deleteCurCondition()
  dvMainStore.deleteComponentById(curComponent.value?.id)
  snapshotStore.recordSnapshotCache('SettingMenu-deleteComponent')
  dvMainStore.setCurComponent({ component: null, index: null })
}

/**
 * 删除当前自定义组件关联的筛选条件
 */
const deleteCurCondition = () => {
  if (curComponent.value.type === 'custom') {
    dvMainStore.removeViewFilter(curComponent.value.id)
    eventBus.emit('delete-condition', { componentId: curComponent.value.id })
  }
}

/**
 * 将当前组件上移一层
 */
const upComponent = () => {
  layerStore.upComponent()
  snapshotStore.recordSnapshotCache('SettingMenu-upComponent')
}

/**
 * 将当前组件下移一层
 */
const downComponent = () => {
  layerStore.downComponent()
  snapshotStore.recordSnapshotCache('SettingMenu-downComponent')
}

/**
 * 将当前组件置顶
 */
const topComponent = () => {
  layerStore.topComponent()
  snapshotStore.recordSnapshotCache('SettingMenu-topComponent')
}

/**
 * 将当前组件置底
 */
const bottomComponent = () => {
  layerStore.bottomComponent()
  snapshotStore.recordSnapshotCache('SettingMenu-bottomComponent')
}
/**
 * 查询并打开当前图表的联动设置
 */
const linkageSetting = () => {
  // sourceViewId 也加入查询
  const targetViewIds = componentData.value
    .filter(item => item.type === 'view' && item.propValue && item.propValue.viewId)
    .map(item => item.propValue.viewId)

  // 获取当前仪表板当前图表联动信息
  const requestInfo = {
    panelId: dvInfo.value.id,
    sourceViewId: curComponent.value.propValue.viewId,
    targetViewIds: targetViewIds
  }
  viewLinkageGather(requestInfo).then(rsp => {
    dvMainStore.setLinkageTargetInfo(rsp.data)
  })
}
/**
 * 为当前标签页组件新增标签
 */
const addTab = () => {
  eventBus.emit('add-new-tab', curComponent.value.id)
}
// 跳转设置
const linkJumpSet = () => {
  emits('linkJumpSet')
}
// 设置边框
const boardSet = () => {
  emits('boardSet')
}
// 超链接设置
const hyperlinksSet = () => {
  state.hyperlinksSetVisible = true
}
// 轮播设置
const tabCarouselSet = () => {
  state.tabCarouselVisible = true
}

onMounted(() => {
  if (navigator.platform.indexOf('Mac') === -1) {
    state.systemOS = 'Other'
  }
})
</script>

<style lang="less" scoped>
.contextmenu {
  position: absolute;
  z-index: 1000;

  ul {
    border: 1px solid #e4e7ed;
    border-radius: 6px;
    background-color: #fff;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
    box-sizing: border-box;
    margin: 5px 0;
    padding: 6px 0;

    li {
      font-size: 14px;
      padding: 0 20px;
      position: relative;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      color: #606266;
      height: 34px;
      line-height: 34px;
      box-sizing: border-box;
      cursor: pointer;

      &:hover {
        background-color: var(--background-color-base, #f5f7fa);
      }
    }
  }
}
</style>
