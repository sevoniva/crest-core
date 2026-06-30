<template>
  <div class="drag-list">
    <div style="flex: 1; min-width: 0">
      <draggable :list="config.propValue" animation="300" @end="onSortChange">
        <template #item="{ element }">
          <span
            :key="element.name"
            class="item-dimension"
            :class="{ 'item-dimension-dark': themes === 'dark' }"
            :title="element.title"
            @dblclick="startEditTitle(element)"
          >
            <el-icon size="20px">
              <Icon name="drag"><drag class="svg-icon" /></Icon>
            </el-icon>
            <span v-if="editingItem !== element" class="item-span">
              {{ element.title }}
            </span>
            <el-input
              v-else
              ref="titleInput"
              v-model="editingTitle"
              size="small"
              class="edit-input"
              @blur="saveTitleEdit(element)"
              @keyup.enter="saveTitleEdit(element)"
              @keyup.esc="cancelEdit"
            />
          </span>
        </template>
      </draggable>
    </div>
    <div style="width: 80px">
      <div v-for="item in config.propValue" :key="item" class="item-icon">
        <el-icon class="component-base" @click="onDelete(item)">
          <Icon name="dv-show"><Delete class="svg-icon f16" /></Icon>
        </el-icon>
        <el-icon
          v-show="item.hidden"
          class="component-base component-icon-display"
          @click="onShow(item)"
        >
          <Icon name="dv-eye-close"><dvEyeClose class="svg-icon f16" /></Icon>
        </el-icon>
        <el-icon v-show="!item.hidden" class="component-base" @click="onHidden(item)">
          <Icon name="dv-show"><dvShow class="svg-icon f16" /></Icon>
        </el-icon>

        <el-icon v-show="!item.hidden" class="component-base" @click="onCopy(item)">
          <Icon name="dv-show"><CopyDocument class="svg-icon f16" /></Icon>
        </el-icon>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import drag from '@/assets/svg/drag.svg'
import draggable from 'vuedraggable'
import { nextTick, toRefs, ref } from 'vue'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import eventBus from '@/utils/eventBus'
import dvEyeClose from '@/assets/svg/dv-eye-close.svg'
import dvShow from '@/assets/svg/dv-show.svg'

/**
 * 快照仓库，用于记录标签页排序、显示和标题变更
 */
const snapshotStore = snapshotStoreWithOut()

/**
 * 标签排序侧栏组件入参
 */
const props = withDefaults(
  defineProps<{
    themes?: EditorTheme
    config: any
  }>(),
  {
    themes: 'dark',
    config: {
      propValue: []
    }
  }
)

/**
 * 标签配置和主题引用
 */
const { config, themes } = toRefs(props)

// 编辑相关状态
const editingItem = ref<any>(null)
/**
 * 当前正在编辑的标题文本
 */
const editingTitle = ref('')
/**
 * 标题输入框引用
 */
const titleInput = ref<any>(null)

/**
 * 进入标签标题编辑状态
 */
const startEditTitle = (element: any) => {
  editingItem.value = element
  editingTitle.value = element.title

  nextTick(() => {
    if (titleInput.value) {
      titleInput.value.focus()
      // 选中全部标题文本，便于直接覆盖输入
      titleInput.value.select()
    }
  })
}

/**
 * 保存标签标题编辑结果
 */
const saveTitleEdit = (element: any) => {
  if (!editingItem.value || editingItem.value !== element) return

  const newTitle = editingTitle.value.trim()

  // 标题为空时恢复原状态
  if (!newTitle) {
    editingItem.value = null
    editingTitle.value = ''
    return
  }

  // 标题有变化时保存快照并通知画布更新
  if (newTitle !== element.title) {
    snapshotStore.recordSnapshotCache('tab-title-edit')
    element.title = newTitle
    eventBus.emit('onTabTitleChange-' + config.value.id, {
      ...element,
      title: newTitle
    })
  }
  snapshotStore.recordSnapshotCache('tab-title-edit')
  // 退出编辑模式
  editingItem.value = null
  editingTitle.value = ''
}

/**
 * 取消当前标题编辑
 */
const cancelEdit = () => {
  editingItem.value = null
  editingTitle.value = ''
}

/**
 * 隐藏当前激活标签页后，自动切换到第一个可见标签页
 */
const checkAndFixCurrentTab = tabItem => {
  // 如果隐藏的不是当前激活标签页，不需要处理
  if (tabItem.name !== config.value.editableTabsValue) return

  // 过滤出可见标签页
  const visibleTabs = config.value.propValue.filter(tab => !tab.hidden)

  // 没有可见标签页时不做处理
  if (visibleTabs.length === 0) return

  // 切换到第一个可见标签页
  nextTick(() => {
    config.value.editableTabsValue = visibleTabs[0].name
  })
}

/**
 * 处理标签排序变更
 */
const onSortChange = () => {
  snapshotStore.recordSnapshotCache('tab-sort-save')
  eventBus.emit('onTabSortChange-' + config.value.id)
}

/**
 * 显示指定标签页
 */
const onShow = tabItem => {
  snapshotStore.recordSnapshotCache('tab')
  tabItem['hidden'] = false
  config.value.editableTabsValue = tabItem.name
}

/**
 * 隐藏指定标签页
 */
const onHidden = tabItem => {
  snapshotStore.recordSnapshotCache('tab')
  tabItem['hidden'] = true
  checkAndFixCurrentTab(tabItem)
}

/**
 * 删除指定标签页
 */
const onDelete = item => {
  snapshotStore.recordSnapshotCache('tab')
  eventBus.emit('onTabDelete-' + config.value.id, deepCopy(item))
  checkAndFixCurrentTab(item)
}

/**
 * 复制指定标签页
 */
const onCopy = item => {
  snapshotStore.recordSnapshotCache('tab')
  eventBus.emit('onTabCopy-' + config.value.id, deepCopy(item))
}
import { ElIcon, ElInput } from 'element-plus-secondary'
import Icon from '../../components/icon-custom/src/Icon.vue'
import { deepCopy } from '@/utils/utils'
</script>

<style scoped lang="less">
.drag-list {
  overflow: auto;
  max-height: 400px;
  display: flex;
  width: 100%;
}

.item-icon {
  height: 31px;
  padding: 2px;
  margin: 2px !important;
  text-align: left;
  color: #606266;
  display: flex;
  align-items: center;
}

.item-dimension {
  padding: 2px;
  margin: 2px;
  border: solid 1px #eee;
  text-align: left;
  color: #606266;
  /*background-color: rgba(35,46,64,.05);*/
  background-color: white;
  display: flex;
  align-items: center;
  cursor: pointer;
}

.item-icon {
  cursor: move;
  margin: 0 2px;
}

.item-span {
  display: inline-block;
  width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.edit-input {
  flex: 1;
  min-width: 80px;

  :deep(.el-input__wrapper) {
    padding: 0 8px;
    background-color: transparent;

    .el-input__inner {
      font-size: 12px;
      line-height: 24px;
    }
  }
}

.blackTheme .item-dimension {
  border: solid 1px;
  border-color: var(--ed-border-color);
  color: var(--TextPrimary);
  background-color: var(--MainBG);
}

.item-dimension-dark {
  border: solid 1px;
  border-color: var(--TableBorderColor);
  color: var(--TextPrimary);
  background-color: var(--MainBG);
}

.item-dimension + .item-dimension {
  margin-top: 6px;
}

.item-dimension:hover {
  color: #1890ff;
  background: #e8f4ff;
  border-color: #a3d3ff;
  cursor: pointer;
}

.blackTheme .item-dimension:hover {
  color: var(--Main);
  background: var(--ContentBG);
  cursor: pointer;
}

.component-base {
  cursor: pointer;
  height: 22px !important;
  width: 22px !important;
  border-radius: 6px;
  padding: 0 4px;
  color: #a6a6a6;

  .f16 {
    font-size: 16px;
  }

  &:hover {
    background: rgba(235, 235, 235, 0.1);
  }

  &:active {
    background: rgba(235, 235, 235, 0.1);
  }
}
</style>
