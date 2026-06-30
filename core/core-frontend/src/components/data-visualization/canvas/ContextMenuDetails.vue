<script setup lang="ts">
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { lockStoreWithOut } from '@/store/modules/data-visualization/lock'
import { copyStoreWithOut } from '@/store/modules/data-visualization/copy'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { layerStoreWithOut } from '@/store/modules/data-visualization/layer'
import { composeStoreWithOut } from '@/store/modules/data-visualization/compose'
import { storeToRefs } from 'pinia'
import { computed, toRefs } from 'vue'
import { ElDivider } from 'element-plus-secondary'
import eventBus from '@/utils/eventBus'
import { componentArraySort, getCurInfo } from '@/store/modules/data-visualization/common'
import { useEmitt } from '@/hooks/web/useEmitt'
import { useI18n } from '@/hooks/web/useI18n'
/** 主画布仓库提供当前组件和组件列表状态 */
const dvMainStore = dvMainStoreWithOut()
/** 复制仓库处理复制、剪切和粘贴操作 */
const copyStore = copyStoreWithOut()
/** 锁定仓库处理组件锁定状态 */
const lockStore = lockStoreWithOut()
/** 快照仓库记录右键菜单操作后的可撤销节点 */
const snapshotStore = snapshotStoreWithOut()
/** 图层仓库处理隐藏、显示和层级调整 */
const layerStore = layerStoreWithOut()
/** 组合仓库处理框选区域、组合和拆组操作 */
const composeStore = composeStoreWithOut()

/** 框选区域内的组件集合 */
const { areaData } = storeToRefs(composeStore)
/** 当前选中组件和画布组件列表 */
const { curComponent, componentData } = storeToRefs(dvMainStore)
/** 右键菜单向父级通知关闭和重命名事件 */
const emit = defineEmits(['close', 'rename'])
/** 全局事件发射器，用于触发查询条件编辑等跨组件行为 */
const { emitter } = useEmitt()
/** 菜单入参，区分画布菜单和侧边栏菜单 */
const props = defineProps({
  activePosition: {
    type: String,
    default: 'canvas'
  }
})

/** 当前菜单展示位置 */
const { activePosition } = toRefs(props)
/** 国际化翻译函数，提供菜单文案 */
const { t } = useI18n()
/** 当前隐藏组件数量，用于控制查询组件移动菜单可见性 */
const popComponentDataLength = computed(
  () => componentData.value.filter(ele => ele.category === 'hidden').length
)

/** 锁定当前组件或框选区域内所有组件 */
const lock = () => {
  if (curComponent.value && !isGroupArea.value) {
    lockStore.lock()
  } else if (areaData.value.components.length) {
    areaData.value.components.forEach(component => {
      lockStore.lock(component)
    })
  }
  snapshotStore.recordSnapshotCache('lock')
  menuOpt('lock')
}

/** 解锁当前组件或框选区域内所有组件 */
const unlock = () => {
  if (curComponent.value && !isGroupArea.value) {
    lockStore.unlock()
  } else if (areaData.value.components.length) {
    areaData.value.components.forEach(component => {
      lockStore.unlock(component)
    })
  }
  menuOpt('unlock')
}

// 点击菜单时不取消当前组件的选中状态
/** 处理菜单鼠标抬起事件，侧边栏场景同步关闭菜单 */
const handleMouseUp = () => {
  dvMainStore.setClickComponentStatus(true)
  activePosition.value === 'aside' && emit('close')
}

/** 统一处理菜单操作完成后的关闭事件 */
const menuOpt = optName => {
  const param = { opt: optName }
  activePosition.value === 'aside' && emit('close', param)
}

/** 剪切当前组件或框选区域 */
const cut = () => {
  if (curComponent.value) {
    const curInfo = getCurInfo()
    copyStore.cut(curInfo.componentData)
  } else if (areaData.value.components.length) {
    copyStore.cut()
  }
  menuOpt('cut')
}

/** 复制当前组件或框选区域 */
const copy = () => {
  copyStore.copy()
  menuOpt('copy')
}

/** 隐藏当前组件或框选区域内所有组件 */
const hide = () => {
  if (curComponent.value && !isGroupArea.value) {
    layerStore.hideComponentWithComponent()
  } else if (areaData.value.components.length) {
    areaData.value.components.forEach(component => {
      layerStore.hideComponentWithComponent(component.id)
    })
  }
  snapshotStore.recordSnapshotCache('hide')
  menuOpt('hide')
}

/** 显示当前组件或框选区域内所有组件 */
const show = () => {
  if (curComponent.value && !isGroupArea.value) {
    layerStore.showComponent()
  } else if (areaData.value.components.length) {
    areaData.value.components.forEach(component => {
      layerStore.showComponent(component.id)
    })
  }
  snapshotStore.recordSnapshotCache('show')
  menuOpt('show')
}
/** 仅在主画布查询组件且无隐藏组件时展示移动菜单 */
const showMoveMenu = computed(
  () =>
    curComponent?.value?.canvasId === 'canvas-main' &&
    curComponent?.value['category'] === 'base' &&
    curComponent.value?.component === 'VQuery' &&
    popComponentDataLength.value === 0
)
/** 切换组件类别，隐藏时同步当前点位区域 */
const categoryChange = type => {
  if (curComponent.value) {
    snapshotStore.recordSnapshotCache('categoryChange')
    curComponent.value['category'] = type
    if (type === 'hidden') {
      dvMainStore.canvasStateChange({ key: 'curPointArea', value: 'hidden' })
    }
  }
}
/** 触发组件重命名并关闭菜单 */
const rename = () => {
  emit('rename')
  menuOpt('rename')
}
/** 粘贴组件并记录渲染快照 */
const paste = () => {
  copyStore.paste(true)
  snapshotStore.recordSnapshotCache('renderChart')
  menuOpt('paste')
}

/** 删除当前组件或框选区域内所有组件 */
const deleteComponent = () => {
  if (curComponent.value && !isGroupArea.value) {
    const curInfo = getCurInfo()
    dvMainStore.deleteComponentById(curComponent.value?.id, curInfo.componentData)
  } else if (areaData.value.components.length) {
    areaData.value.components.forEach(component => {
      dvMainStore.deleteComponentById(component.id)
    })
  }
  eventBus.emit('hideArea-canvas-main')
  snapshotStore.recordSnapshotCache('deleteComponent')
  menuOpt('deleteComponent')
}

/** 将当前组件或框选组件上移一层 */
const upComponent = () => {
  if (curComponent.value && !isGroupArea.value) {
    layerStore.upComponent(curComponent.value.id)
  } else if (areaData.value.components.length) {
    componentArraySort(areaData.value.components)
    areaData.value.components.forEach(component => {
      layerStore.upComponent(component.id)
    })
  }
  snapshotStore.recordSnapshotCache('upComponent')
  menuOpt('upComponent')
}

/** 将当前组件或框选组件下移一层 */
const downComponent = () => {
  if (curComponent.value && !isGroupArea.value) {
    layerStore.downComponent(curComponent.value.id)
  } else if (areaData.value.components.length) {
    componentArraySort(areaData.value.components, 'top')
    areaData.value.components.forEach(component => {
      layerStore.downComponent(component.id)
    })
  }
  snapshotStore.recordSnapshotCache('downComponent')
  menuOpt('downComponent')
}

/** 将当前组件或框选组件置顶 */
const topComponent = () => {
  if (curComponent.value && !isGroupArea.value) {
    layerStore.topComponent(curComponent.value.id)
  } else if (areaData.value.components.length) {
    componentArraySort(areaData.value.components, 'top')
    areaData.value.components.forEach(component => {
      layerStore.topComponent(component.id)
    })
  }
  snapshotStore.recordSnapshotCache('topComponent')
  menuOpt('topComponent')
}

/** 将当前组件或框选组件置底 */
const bottomComponent = () => {
  if (curComponent.value && !isGroupArea.value) {
    layerStore.bottomComponent(curComponent.value.id)
  } else if (areaData.value.components.length) {
    componentArraySort(areaData.value.components)
    areaData.value.components.forEach(component => {
      layerStore.bottomComponent(component.id)
    })
  }
  snapshotStore.recordSnapshotCache('bottomComponent')
  menuOpt('bottomComponent')
}

/** 触发 Tabs 自定义排序弹窗 */
const customSort = () => {
  // 通过事件总线交给 Tabs 排序面板处理
  eventBus.emit('tabSort')
}

/** 将框选区域组合为组件组 */
const componentCompose = () => {
  composeStore.compose()
  snapshotStore.recordSnapshotCache('componentCompose')
  menuOpt('componentCompose')
}

/** 拆分当前组件组 */
const decompose = () => {
  composeStore.decompose()
  snapshotStore.recordSnapshotCache('decompose')
  menuOpt('decompose')
}

/** 对框选区域执行对齐操作 */
const alignment = params => {
  composeStore.alignment(params)
  snapshotStore.recordSnapshotCache('decompose')
}

// 阻止事件向父级组件传播调用父级的handleMouseDown 导致areaData 被隐藏
/** 阻止组合菜单鼠标事件冒泡，避免框选区域被父组件清空 */
const handleComposeMouseDown = e => {
  e.preventDefault()
  e.stopPropagation()
}

/** 判断是否展示组合操作分割线 */
const composeDivider = computed(() => {
  return !(
    !curComponent.value ||
    curComponent.value['isLock'] ||
    curComponent.value['component'] != 'Group' ||
    curComponent.value.category === 'hidden'
  )
})

/** 当前选中对象是否为框选区域 */
const isGroupArea = computed(() => {
  return curComponent.value?.component === 'GroupArea'
})

/** 打开查询组件的条件编辑入口 */
const editQueryCriteria = () => {
  emitter.emit(`editQueryCriteria${curComponent.value.id}`)
}
</script>

<template>
  <div class="context-menu-base context-menu-details" @mousedown="handleComposeMouseDown">
    <ul @mouseup="handleMouseUp">
      <template v-if="areaData.components.length">
        <li @mousedown="handleComposeMouseDown" @click="componentCompose">
          {{ t('visualization.view_group') }}
        </li>
        <el-dropdown
          style="width: 100%"
          trigger="hover"
          effect="dark"
          placement="right-start"
          :teleported="false"
          popper-class="context-menu-details"
        >
          <li>
            <div>
              <span>{{ t('visualization.alignment') }}</span
              ><el-icon><ArrowRight /></el-icon>
            </div>
          </li>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item style="width: 118px" @click="alignment('left')">{{
                t('visualization.left_justifying')
              }}</el-dropdown-item>
              <el-dropdown-item style="width: 118px" @click="alignment('right')">{{
                t('visualization.right_justifying')
              }}</el-dropdown-item>
              <el-dropdown-item @click="alignment('top')">{{
                t('visualization.top_justifying')
              }}</el-dropdown-item>
              <el-dropdown-item @click="alignment('bottom')">{{
                t('visualization.bottom_justifying')
              }}</el-dropdown-item>
              <el-dropdown-item @click="alignment('transverse')">{{
                t('visualization.horizontally_centered')
              }}</el-dropdown-item>
              <el-dropdown-item @click="alignment('direction')">{{
                t('visualization.vertically_centered')
              }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-divider class="custom-divider" />
        <li @click="copy">{{ t('visualization.copy') }}</li>
        <li @click="paste">{{ t('visualization.paste') }}</li>
        <li @click="cut">{{ t('visualization.cut') }}</li>
        <el-divider class="custom-divider" />
        <li @click="deleteComponent">{{ t('visualization.delete') }}</li>
      </template>
      <template v-else>
        <li
          v-show="
            !(!curComponent || curComponent['isLock'] || curComponent['component'] != 'Group')
          "
          @click="decompose()"
        >
          {{ t('visualization.cancel_group') }}
        </li>
        <el-divider class="custom-divider" v-show="composeDivider" />
        <template v-if="curComponent">
          <template v-if="!curComponent['isLock'] && curComponent.category === 'hidden'">
            <li @click="categoryChange('base')">{{ t('visualization.move_to_screen_show') }}</li>
            <li @click="editQueryCriteria">{{ t('visualization.edit') }}</li>
            <li v-if="activePosition === 'aside'" @click="rename">
              {{ t('visualization.rename') }}
            </li>
            <li @click="copy">{{ t('visualization.copy') }}</li>
            <li @click="paste">{{ t('visualization.paste') }}</li>
            <el-divider class="custom-divider" />
            <li @click="deleteComponent">{{ t('visualization.delete') }}</li>
          </template>
          <template v-if="!curComponent['isLock'] && curComponent.category !== 'hidden'">
            <li v-if="curComponent.component === 'VQuery'" @click="editQueryCriteria">
              {{ t('visualization.edit') }}
            </li>
            <li @click="upComponent">{{ t('visualization.up_component') }}</li>
            <li @click="downComponent">{{ t('visualization.down_component') }}</li>
            <li @click="topComponent">{{ t('visualization.top_component') }}</li>
            <li @click="bottomComponent">{{ t('visualization.bottom_component') }}</li>
            <li @click="customSort" v-if="curComponent.component === 'Tabs'">
              {{ t('visualization.sort') }}
            </li>
            <li @click="categoryChange('hidden')" v-show="showMoveMenu">
              {{ t('visualization.move_to_pop_area') }}
            </li>
            <el-divider class="custom-divider" />
            <li @click="hide" v-show="curComponent['isShow']">{{ t('visualization.hidden') }}</li>
            <li @click="show" v-show="!curComponent['isShow'] || isGroupArea">
              {{ t('visualization.cancel_hidden') }}
            </li>
            <li @click="lock">{{ t('visualization.lock') }}</li>
            <li v-if="curComponent['isLock'] || isGroupArea" @click="unlock">
              {{ t('visualization.unlock') }}
            </li>
            <el-divider class="custom-divider" />
            <li v-if="activePosition === 'aside'" @click="rename">
              {{ t('visualization.rename') }}
            </li>
            <li @click="copy">{{ t('visualization.copy') }}</li>
            <li @click="paste">{{ t('visualization.paste') }}</li>
            <li @click="cut">{{ t('visualization.cut') }}</li>
            <el-divider class="custom-divider" />
            <li @click="deleteComponent">{{ t('visualization.delete') }}</li>
          </template>
          <li v-if="curComponent['isLock']" @click="unlock">{{ t('visualization.unlock') }}</li>
        </template>
        <li v-else-if="!curComponent && !areaData.components.length" @click="paste">
          {{ t('visualization.paste') }}
        </li>
      </template>
    </ul>
  </div>
</template>

<style lang="less">
.context-menu-base {
  width: 220px;
}
.context-menu-details {
  z-index: 1000;
  border: #434343 1px solid;
  ul {
    padding: 4px 0;
    background-color: #292929;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
    box-sizing: border-box;
    :deep(.ed-divider) {
      margin: 8px 0;
    }

    li {
      width: 100%;
      font-size: 14px;
      padding: 0 12px;
      position: relative;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      color: #ebebeb;
      height: 34px;
      line-height: 34px;
      box-sizing: border-box;
      cursor: pointer;

      i {
        position: absolute;
        right: 0px;
        top: 50%;
        transform: translate(-50%, -50%);
      }

      &:hover {
        background-color: #333 !important;
      }
    }
  }
}

.custom-divider {
  border-color: #434343 !important;
  margin: 0 !important;
}
</style>
