import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { isMainCanvas } from '@/utils/canvasUtils'

const dvMainStore = dvMainStoreWithOut()
const { componentData } = storeToRefs(dvMainStore)

// 检查是否可以加入到分组
export function checkJoinGroup(item) {
  if (item.component === 'Tabs') {
    let result = true
    item.propValue?.forEach(tabItem => {
      tabItem.componentData?.forEach(tabComponent => {
        if (tabComponent.component === 'Group') {
          result = false
        }
      })
    })
    return result
  } else {
    return true
  }
}
// 检查是否可以移入tab
export function checkJoinTab(item) {
  if (item.component === 'Group') {
    let result = true
    item.propValue.forEach(groupItem => {
      if (groupItem.component === 'Tabs') {
        result = false
      }
    })
    return result
  } else {
    return true
  }
}

// 目前仅允许group中还有一层Tab 或者 Tab中含有一层group
export function itemCanvasPathCheck(item, checkType) {
  if (checkType === 'canvas-main') {
    return isMainCanvas(item.canvasId)
  }
  const pathMap = {}
  componentData.value.forEach(componentItem => {
    canvasIdMapCheck(componentItem, null, pathMap)
  })

  // 父组件是Tab且在group中
  if (checkType === 'pTabGroup') {
    return Boolean(
      pathMap[item.id] &&
        pathMap[item.id].component === 'Tabs' &&
        pathMap[pathMap[item.id].id] &&
        pathMap[pathMap[item.id].id].component === 'Group'
    )
  }
  // 当前组件是group且在Tab中
  if (checkType === 'groupInTab') {
    return Boolean(
      item.component === 'Group' &&
        pathMap[pathMap[item.id].id] &&
        pathMap[pathMap[item.id].id].component === 'Tabs'
    )
  }

  // 当前组件是Tab且在Group中
  if (checkType === 'tabInGroup') {
    return Boolean(
      item.component === 'Tabs' && pathMap[item.id] && pathMap[item.id].component === 'Group'
    )
  }
  return false
}

// 根据当前数据计算界面可用状态
export function canvasIdMapCheck(item, pItem, pathMap) {
  pathMap[item.id] = pItem
  if (item.component === 'Tabs') {
    item.propValue?.forEach(tabItem => {
      tabItem.componentData?.forEach(tabComponent => {
        canvasIdMapCheck(tabComponent, item, pathMap)
      })
    })
  } else if (item.component === 'Group') {
    item.propValue.forEach(groupItem => {
      canvasIdMapCheck(groupItem, item, pathMap)
    })
  }
}
