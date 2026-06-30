import { cloneDeep } from 'lodash-es'
import { useEmitt } from '@/hooks/web/useEmitt'
const { emitter } = useEmitt()

// 触发组件重新渲染或刷新
export const reRenderAll = (oldArr, newArr) => {
  const newArrIds = newArr.map(ele => ele.id)
  const emitterList = (oldArr || []).reduce((pre, next) => {
    if (newArrIds.includes(next.id)) return pre
    const keyList = getKeyList(next)
    pre = [...new Set([...keyList, ...pre])]
    return pre
  }, [])
  if (!emitterList.length) return
  emitterList.forEach(ele => {
    emitter.emit(`query-data-${ele}`)
  })
}

// 处理拖拽交互并更新目标位置
export const checkFilterRemove = componentTarget => {
  if (componentTarget?.component === 'VQuery') {
    reRenderAfterDelete(componentTarget.propValue)
  } else if (componentTarget.component === 'Group') {
    componentTarget.propValue.forEach(groupItem => {
      checkFilterRemove(groupItem)
    })
  } else if (componentTarget.component === 'Tabs') {
    componentTarget.propValue.forEach(tabItem => {
      tabItem.componentData?.forEach(tabComponent => {
        checkFilterRemove(tabComponent)
      })
    })
  }
}

// 触发组件重新渲染或刷新
export const reRenderAfterDelete = oldArr => {
  const emitterList = (oldArr || []).reduce((pre, next) => {
    const keyList = getKeyList(next)
    pre = [...new Set([...keyList, ...pre])]
    return pre
  }, [])
  if (!emitterList.length) return
  emitterList.forEach(ele => {
    emitter.emit(`query-data-${ele}`)
  })
}

export const getKeyList = next => {
  const checkedFields = next.checkedFields || []
  const checkedFieldsMap = next.checkedFieldsMap || {}
  let checkedFieldsMapArr = Object.entries(checkedFieldsMap).filter(ele =>
    checkedFields.includes(ele[0])
  )
  if (next.displayType === '9') {
    const treeFieldList = next.treeFieldList || []
    checkedFieldsMapArr = (
      next.treeCheckedList?.length
        ? next.treeCheckedList.filter((_, index) => index < treeFieldList.length)
        : treeFieldList.map(() => {
            return {
              checkedFields: [...checkedFields],
              checkedFieldsMap: cloneDeep(checkedFieldsMap)
            }
          })
    )
      .map(item =>
        Object.entries(item.checkedFieldsMap || {}).filter(ele =>
          (item.checkedFields || []).includes(ele[0])
        )
      )
      .flat()
  }
  return checkedFieldsMapArr.filter(ele => !!ele[1]).map(ele => ele[0])
}
