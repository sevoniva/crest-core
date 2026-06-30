import eventBus from '@/utils/eventBus'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { snapshotStoreWithOut } from '@/store/modules/data-visualization/snapshot'
import { copyStoreWithOut } from '@/store/modules/data-visualization/copy'
import { composeStoreWithOut } from '@/store/modules/data-visualization/compose'
import { lockStoreWithOut } from '@/store/modules/data-visualization/lock'
import { storeToRefs } from 'pinia'
import { getCurInfo } from '@/store/modules/data-visualization/common'
import { isGroupCanvas, isTabCanvas } from '@/utils/canvasUtils'
import { groupStyleRevert } from '@/utils/style'

/**
 * 大屏主状态仓库，提供当前组件和编辑模式等信息
 */
const dvMainStore = dvMainStoreWithOut()
/**
 * 组合状态仓库，维护框选、组合和辅助按键状态
 */
const composeStore = composeStoreWithOut()
/**
 * 快照仓库，用于快捷键操作后的撤销重做记录
 */
const snapshotStore = snapshotStoreWithOut()
/**
 * 复制粘贴仓库，封装复制、剪切和粘贴动作
 */
const copyStore = copyStoreWithOut()
/**
 * 锁定仓库，处理组件锁定与解锁命令
 */
const lockStore = lockStoreWithOut()
/**
 * 当前组件和编辑模式的响应式引用
 */
const { curComponent, editMode } = storeToRefs(dvMainStore)
/**
 * 组合区域内组件数据的响应式引用
 */
const { areaData } = storeToRefs(composeStore)

/**
 * 快捷键 keyCode 常量集合，统一映射键盘事件中的编码
 */
const ctrlKey = 17,
  shiftKey = 16, // shift
  commandKey = 91, // mac command
  leftKey = 37, // 向左
  upKey = 38, // 向上
  rightKey = 39, // 向右
  downKey = 40, // 向下
  vKey = 86, // 粘贴
  cKey = 67, // 复制
  xKey = 88, // 剪切
  yKey = 89, // 重做
  zKey = 90, // 撤销
  gKey = 71, // 组合
  bKey = 66, // 拆分
  lKey = 76, // 锁定
  uKey = 85, // 解锁
  sKey = 83, // 保存
  pKey = 80, // 预览
  dKey = 68, // 删除
  deleteKey = 46, // 删除
  macDeleteKey = 8, // 删除
  eKey = 69, // 清空画布
  spaceKey = 32 // 空格键

/**
 * 编辑器需要拦截处理的按键编码列表
 */
export const keycodes = [8, 37, 38, 39, 40, 66, 67, 68, 69, 71, 76, 80, 83, 85, 86, 88, 89, 90]

// 与组件状态无关的操作
const basemap = {
  [vKey]: paste,
  [yKey]: redo,
  [zKey]: undo,
  [sKey]: save,
  [pKey]: preview,
  [eKey]: clearCanvas
}

// 当处于大屏状态时，按上下左右键移动组件位置
const positionMoveKey = {
  [leftKey]: move,
  [upKey]: move,
  [rightKey]: move,
  [downKey]: move
}

// 组件锁定状态下可以执行的操作
const lockMap = {
  ...basemap,
  [uKey]: unlock
}

// 组件未锁定状态下可以执行的操作
const unlockMap = {
  ...basemap,
  [cKey]: copy,
  [xKey]: cut,
  [gKey]: compose,
  [bKey]: decompose,
  [dKey]: deleteComponent,
  [deleteKey]: deleteComponent,
  [lKey]: lock
}

// 检查当前页面是否有弹框
const checkDialog = () => {
  let haveDialog = false
  document.querySelectorAll('.ed-overlay').forEach(element => {
    if (window.getComputedStyle(element).getPropertyValue('display') != 'none') {
      haveDialog = true
    }
  })
  document.querySelectorAll('.ed-popper').forEach(element => {
    if (
      !element.classList?.contains('template-popper-tips') &&
      window.getComputedStyle(element).getPropertyValue('display') != 'none'
    ) {
      haveDialog = true
    }
  })
  // 富文本单框
  if (document.querySelector('.tox-dialog-wrap')) {
    haveDialog = true
  }

  return haveDialog
}

/**
 * 标记 Ctrl 或 Command 是否处于按下状态
 */
let isCtrlOrCommandDown = false
/**
 * 标记 Shift 是否处于按下状态
 */
let isShiftDown = false
// 全局监听按键操作并执行相应命令
export function listenGlobalKeyDown() {
  window.onkeydown = e => {
    if (editMode.value === 'preview' || checkDialog()) return
    const { keyCode } = e
    if (positionMoveKey[keyCode] && curComponent.value) {
      positionMoveKey[keyCode](keyCode)
      e.preventDefault()
      e.stopPropagation()
    } else if (keyCode === shiftKey) {
      isShiftDown = true
      composeStore.setIsShiftDownStatus(true)
      releaseKeyCheck('shift')
    } else if (keyCode === ctrlKey || keyCode === commandKey) {
      isCtrlOrCommandDown = true
      composeStore.setIsCtrlOrCmdDownStatus(true)
      releaseKeyCheck('ctrl')
    } else if (keyCode === spaceKey) {
      composeStore.setSpaceDownStatus(true)
      e.preventDefault()
      e.stopPropagation()
    } else if ((keyCode == deleteKey || keyCode == macDeleteKey) && curComponent.value) {
      deleteComponent()
    } else if (isCtrlOrCommandDown) {
      if (unlockMap[keyCode] && (!curComponent.value || !curComponent.value.isLock)) {
        e.preventDefault()
        unlockMap[keyCode]()
      } else if (lockMap[keyCode] && curComponent.value && curComponent.value.isLock) {
        e.preventDefault()
        lockMap[keyCode]()
      }
    }
  }

  window.onkeyup = e => {
    if (e.keyCode === ctrlKey || e.keyCode === commandKey) {
      isCtrlOrCommandDown = false
      composeStore.setIsCtrlOrCmdDownStatus(false)
    } else if (e.keyCode === shiftKey) {
      isShiftDown = true
      composeStore.setIsShiftDownStatus(false)
    } else if (e.keyCode === spaceKey) {
      composeStore.setSpaceDownStatus(false)
      e.preventDefault()
      e.stopPropagation()
    }
  }

  window.onmousedown = () => {
    dvMainStore.setInEditorStatus(false)
  }
}

/**
 * 释放外部场景遗留的组合按键状态
 */
export function releaseAttachKey() {
  isCtrlOrCommandDown = false
  composeStore.setIsCtrlOrCmdDownStatus(false)
  isShiftDown = false
  composeStore.setIsShiftDownStatus(false)
}

// 当前不支持同时 Ctrl 和 Shift 操作
function releaseKeyCheck(keyType) {
  if (keyType === 'shift' && isCtrlOrCommandDown) {
    isCtrlOrCommandDown = false
    composeStore.setIsCtrlOrCmdDownStatus(false)
  } else if (keyType === 'ctrl' && isShiftDown) {
    isShiftDown = false
    composeStore.setIsShiftDownStatus(false)
  }
}

/**
 * 执行复制当前组件命令
 */
function copy() {
  copyStore.copy()
}

/**
 * 执行粘贴命令并记录快照
 */
function paste() {
  copyStore.paste(false)
  snapshotStore.recordSnapshotCache('key-paste')
}

/**
 * 根据方向键移动当前组件或组合区域
 */
function move(keyCode) {
  if (curComponent.value) {
    const scale = dvMainStore.canvasStyleData.scale / 100
    if (keyCode === leftKey) {
      curComponent.value.style.left = curComponent.value.style.left - scale
      groupAreaAdaptor(-scale, 0)
    } else if (keyCode === rightKey) {
      curComponent.value.style.left = curComponent.value.style.left + scale
      groupAreaAdaptor(scale, 0)
    } else if (keyCode === upKey) {
      curComponent.value.style.top = curComponent.value.style.top - scale
      groupAreaAdaptor(0, -scale)
    } else if (keyCode === downKey) {
      curComponent.value.style.top = curComponent.value.style.top + scale
      groupAreaAdaptor(0, scale)
    }
    snapshotStore.recordSnapshotCache('key-move')
  }
}

/**
 * 在移动分组或内嵌画布组件后同步组合区域坐标
 */
function groupAreaAdaptor(leftOffset = 0, topOffset = 0) {
  const canvasId = curComponent.value.canvasId
  const parentNode = document.querySelector('#editor-' + canvasId)

  //如果当前画布是Group内部画布 则对应组件定位在resize时要还原到groupStyle中
  if (isGroupCanvas(canvasId) || isTabCanvas(canvasId)) {
    groupStyleRevert(curComponent.value, {
      width: parentNode.offsetWidth,
      height: parentNode.offsetHeight
    })
  } else if (curComponent.value.component === 'GroupArea' && areaData.value.components.length > 0) {
    areaData.value.components.forEach(component => {
      component.style.top = component.style.top + topOffset
      component.style.left = component.style.left + leftOffset
    })
  }
}

/**
 * 执行剪切当前组件命令
 */
function cut() {
  copyStore.cut()
}

/**
 * 执行重做命令
 */
function redo() {
  snapshotStore.redo()
}

/**
 * 执行撤销命令
 */
function undo() {
  snapshotStore.undo()
}

/**
 * 将框选区域中的组件组合为一个组件
 */
function compose() {
  if (areaData.value.components.length) {
    composeStore.compose()
    snapshotStore.recordSnapshotCache('key-compose')
  }
}

/**
 * 将当前未锁定组合组件拆分为独立组件
 */
function decompose() {
  const curComponentLink = curComponent.value
  if (curComponentLink && !curComponentLink.isLock && curComponentLink.component == 'Group') {
    composeStore.decompose()
    snapshotStore.recordSnapshotCache('key-decompose')
  }
}

/**
 * 触发大屏保存事件
 */
function save() {
  eventBus.emit('save')
}

/**
 * 触发大屏预览事件
 */
function preview() {
  eventBus.emit('preview')
}

/**
 * 删除当前组件或框选区域内的多个组件
 */
function deleteComponent() {
  if (curComponent.value && curComponent.value.component !== 'GroupArea') {
    const curInfo = getCurInfo()
    if (curInfo) {
      dvMainStore.deleteComponent(curInfo.index, curInfo.componentData)
    }
  } else if (areaData.value.components.length) {
    areaData.value.components.forEach(component => {
      dvMainStore.deleteComponentById(component.id)
    })
    eventBus.emit('hideArea-canvas-main')
  }
  snapshotStore.recordSnapshotCache('listenGlobalKeyDown')
}

/**
 * 触发清空画布事件
 */
function clearCanvas() {
  eventBus.emit('clearCanvas')
}

/**
 * 锁定当前组件
 */
function lock() {
  lockStore.lock()
}

/**
 * 解锁当前组件
 */
function unlock() {
  lockStore.unlock()
}
