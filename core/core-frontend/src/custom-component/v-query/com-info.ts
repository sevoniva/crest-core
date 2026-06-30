import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
interface DatasetField {
  type?: string
  innerType?: string
  title: string
  id: string
  tableId: string
}
const dvMainStore = dvMainStoreWithOut()
const { componentData, canvasViewInfo } = storeToRefs(dvMainStore)

// 衔接当前组件交互和状态同步
export const comInfo = () => {
  // 整理当前数据并同步界面状态
  const dfsComponentData = () => {
    let arr = componentData.value.filter(
      com => !['VQuery', 'Tabs'].includes(com.innerType) && com.component !== 'Group'
    )
    componentData.value.forEach(ele => {
      if (ele.innerType === 'Tabs') {
        ele.propValue.forEach(itx => {
          arr = [
            ...arr,
            ...itx.componentData.filter(
              com => !['VQuery', 'Tabs'].includes(com.innerType) && com.component !== 'Group'
            )
          ]

          itx.componentData.forEach(element => {
            if (element.component === 'Group') {
              arr = [
                ...arr,
                element.propValue.filter(
                  coms => !['VQuery', 'Tabs'].includes(coms.innerType) && coms.component !== 'Group'
                )
              ]
            }
          })
        })
      } else if (ele.component === 'Group') {
        arr = [
          ...arr,
          ele.propValue.filter(
            com => !['VQuery', 'Tabs'].includes(com.innerType) && com.component !== 'Group'
          )
        ]
        ele.propValue.forEach(element => {
          if (element.innerType === 'Tabs') {
            element.propValue.forEach(itx => {
              arr = [
                ...arr,
                ...itx.componentData.filter(
                  com => !['VQuery', 'Tabs'].includes(com.innerType) && com.component !== 'Group'
                )
              ]
            })
          }
        })
      }
    })

    return arr.flat()
  }

  // 整理当前数据并同步界面状态
  const datasetFieldList = computed(() => {
    return dfsComponentData()
      .map(ele => {
        const obj = canvasViewInfo.value[ele.id]
        if (!obj) return null
        const { id, title, tableId, type } = obj as DatasetField
        return !!id && !!tableId
          ? {
              id,
              type,
              title,
              tableId
            }
          : null
      })
      .filter(ele => !!ele)
  })
  return {
    datasetFieldList
  }
}
