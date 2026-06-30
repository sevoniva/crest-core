import { getRange } from '@/utils/timeUitils'
import { union } from 'lodash-es'

// 整理输入数据并返回工具处理结果
export function viewFieldTimeTrans(viewDataInfo, params) {
  if (viewDataInfo && params && params.dimensionList) {
    const fields = viewDataInfo.fields
      ? viewDataInfo.fields
      : viewDataInfo.left?.fields || viewDataInfo.right?.fields
      ? union(viewDataInfo.left?.fields, viewDataInfo.right?.fields)
      : []

    const idNameMap = fields.reduce((pre, next) => {
      pre[next['id']] = next['engineFieldName']
      return pre
    }, {})

    const nameTypeMap = fields.reduce((pre, next) => {
      pre[next['engineFieldName']] = next['fieldType']
      return pre
    }, {})

    const nameDateStyleMap = fields.reduce((pre, next) => {
      pre[next['engineFieldName']] = next['dateStyle']
      return pre
    }, {})

    params.dimensionList.forEach(dimension => {
      const engineFieldName = idNameMap[dimension.id]
      // fieldType === 1 表示是时间类型
      if (nameTypeMap[engineFieldName] === 1) {
        dimension['timeValue'] = getRange(dimension.value, nameDateStyleMap[engineFieldName])
      }
    })
  }
}

// 校验当前数据是否满足业务规则
export function checkIsSameDs(canvasViewInfo, sourceViewId, targetViewId) {
  return canvasViewInfo[sourceViewId]['tableId'] === canvasViewInfo[targetViewId]['tableId']
}
