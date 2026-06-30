import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import type { ManipulateType } from 'dayjs'
import { storeToRefs } from 'pinia'
import dayjs from 'dayjs'
import { getDynamicRange, getCustomTime } from '@/custom-component/v-query/time-format'
import { getCustomRange } from '@/custom-component/v-query/time-format-dayjs'
import { decodeTreeLevelValue } from '@/utils/treeLevelSeparator'
const dvMainStore = dvMainStoreWithOut()
const { componentData, canvasStyleData } = storeToRefs(dvMainStore)

// 根据动态时间筛选配置计算时间范围，最终统一转换为毫秒时间戳数组。
const getDynamicRangeTime = (type: number, selectValue: any, timeGranularityMultiple: string) => {
  const timeType = (timeGranularityMultiple || '').split('range')[0]

  if ('datetimerange' === timeGranularityMultiple || type === 1 || !timeType) {
    // 日期时间范围和单日期组件保留用户选择的精确时间。
    return selectValue.map(ele => +new Date(ele))
  }

  if (timeGranularityMultiple.includes('range') && type === 7) {
    // 年/月/日范围需要扩展到对应粒度的完整起止时间。
    return [
      +new Date(
        dayjs(selectValue[0])
          .startOf(timeType as 'month' | 'year' | 'date')
          .format('YYYY/MM/DD HH:mm:ss')
      ),
      +new Date(
        dayjs(selectValue[1])
          .endOf(timeType as 'month' | 'year' | 'date')
          .format('YYYY/MM/DD HH:mm:ss')
      )
    ]
  }

  const [start] = selectValue

  // 单点范围以起始时间为准，结束时间取该粒度的自然结束点。
  return [
    +new Date(start),
    +getCustomTime(
      1,
      timeType as ManipulateType,
      timeType,
      'b',
      null,
      timeGranularityMultiple,
      'start-config'
    ) - 1000
  ]
}

// 将不同筛选类型的原始值格式化为统一数组值，方便后续构造查询条件。
const forMatterValue = (
  type: number,
  selectValue: any,
  timeGranularity: string,
  timeGranularityMultiple: string
) => {
  if (![1, 7].includes(type)) {
    // 非时间组件统一转成数组，保持查询参数结构稳定。
    return Array.isArray(selectValue) ? selectValue : [selectValue]
  }
  return Array.isArray(selectValue)
    ? getDynamicRangeTime(type, selectValue, timeGranularityMultiple)
    : getRange(selectValue, timeGranularity)
}

// 根据时间粒度计算固定时间范围，单日期筛选会扩展为完整自然周期。
export const getRange = (selectValue, timeGranularity) => {
  switch (timeGranularity) {
    case 'year':
      return getYearEnd(selectValue)
    case 'month':
      return getMonthEnd(selectValue)
    case 'date':
      return getDayEnd(selectValue)
    case 'datetime':
      return [+new Date(selectValue), +new Date(selectValue)]
    default:
      break
  }
}
// 计算指定时间所在年份的起止时间。
const getYearEnd = timestamp => {
  return [
    +new Date(dayjs(timestamp).startOf('year').format('YYYY/MM/DD HH:mm:ss')),
    +new Date(dayjs(timestamp).endOf('year').format('YYYY/MM/DD HH:mm:ss'))
  ]
}

// 计算指定时间所在月份的起止时间。
const getMonthEnd = timestamp => {
  return [
    +new Date(dayjs(timestamp).startOf('month').format('YYYY/MM/DD HH:mm:ss')),
    +new Date(dayjs(timestamp).endOf('month').format('YYYY/MM/DD HH:mm:ss'))
  ]
}

// 计算指定日期的起止时间。
const getDayEnd = timestamp => {
  return [
    +new Date(dayjs(timestamp).startOf('day').format('YYYY/MM/DD HH:mm:ss')),
    +new Date(dayjs(timestamp).endOf('day').format('YYYY/MM/DD HH:mm:ss'))
  ]
}

// 根据树形筛选结果解析目标字段 ID 和筛选值，支持多层级字段映射到不同图表字段。
const getFieldId = (arr, result, relationshipChartIndex, ids) => {
  const [obj] = [...result].reverse()
  const valArr = obj.split(',')
  const idArr = arr.map(ele => ele.id)
  const indexArr = relationshipChartIndex.filter(ele => valArr[ele])
  if (!relationshipChartIndex.length) {
    // 未配置层级映射时按结果层级顺序拼接字段 ID。
    return [idArr.slice(0, valArr.length).join(','), [...new Set(result)]]
  } else {
    // 配置层级映射时只保留目标图表关联的层级值。
    for (const key in result) {
      result[key] = indexArr.map(ele => result[key].split(',')[ele]).join(',')
    }
    return [
      indexArr.map(ele => ids[ele]).join(','),
      result.filter(ele => !ele.endsWith(',') && !!ele)
    ]
  }
}

// 根据默认值、首次加载状态和当前选择值计算实际筛选值。
const getValueByDefaultValueCheckOrFirstLoad = (
  defaultValueCheck: boolean,
  defaultValue: any,
  selectValue: any,
  firstLoad: boolean,
  multiple: boolean,
  defaultMapValue: any,
  optionValueSource: number,
  mapValue: any,
  displayType: string,
  displayId: string
) => {
  if (+displayType === 9) {
    // 树形值会转义层级分隔符，生成查询前需要还原。
    if (firstLoad) {
      return defaultValueCheck
        ? multiple
          ? defaultValue.map(decodeTreeLevelValue)
          : decodeTreeLevelValue(defaultValue || '')
        : []
    }
    return selectValue?.length
      ? multiple
        ? selectValue.map(decodeTreeLevelValue)
        : decodeTreeLevelValue(selectValue || '')
      : []
  }
  if (
    optionValueSource === 1 &&
    (defaultMapValue?.length || displayId) &&
    ![1, 7].includes(+displayType)
  ) {
    // 自定义选项默认值使用映射值，避免展示值和实际查询值混用。
    if (firstLoad) {
      return defaultValueCheck ? defaultMapValue : multiple ? [] : ''
    }
    return (selectValue?.length ? mapValue : selectValue) || ''
  }

  if (firstLoad) {
    // 首次加载按默认值开关决定是否带入默认筛选。
    return defaultValueCheck ? defaultValue : multiple ? [] : ''
  }
  return selectValue ? selectValue : multiple ? [] : ''
}

// 收集当前组件关联的查询组件筛选条件，覆盖画布、分组和标签页中的查询组件。
export const useFilter = (curComponentId: string, firstLoad = false) => {
  // popupAvailable 控制隐藏区域查询组件是否参与筛选。
  const popupAvailable = canvasStyleData.value.popupAvailable
  const filter = []
  const queryComponentList = componentData.value.filter(
    ele =>
      ele.component === 'VQuery' &&
      (popupAvailable || (!popupAvailable && ele.category !== 'hidden'))
  )
  searchQuery(queryComponentList, filter, curComponentId, firstLoad)
  componentData.value.forEach(ele => {
    if (ele.component === 'Group') {
      // 分组组件中的查询组件需要按内部 propValue 继续遍历。
      const list = ele.propValue.filter(
        item =>
          item.innerType === 'VQuery' &&
          (popupAvailable || (!popupAvailable && ele.category !== 'hidden'))
      )
      searchQuery(list, filter, curComponentId, firstLoad)

      list.forEach(element => {
        if (element.innerType === 'Tabs') {
          // 分组内标签页还会嵌套查询组件，需要继续下钻到页签内容。
          element.propValue.forEach(itx => {
            const elementArr = itx.componentData.filter(
              item =>
                item.innerType === 'VQuery' &&
                (popupAvailable || (!popupAvailable && ele.category !== 'hidden'))
            )
            searchQuery(elementArr, filter, curComponentId, firstLoad)
          })
        }
      })

      ele.propValue.forEach(element => {
        if (element.innerType === 'Tabs') {
          element.propValue.forEach(itx => {
            const elementArr = itx.componentData.filter(
              item =>
                item.innerType === 'VQuery' &&
                (popupAvailable || (!popupAvailable && ele.category !== 'hidden'))
            )
            searchQuery(elementArr, filter, curComponentId, firstLoad)
          })
        }
      })
    }

    if (ele.innerType === 'Tabs') {
      // 顶层标签页中的分组和查询组件也需要纳入筛选条件收集。
      ele.propValue.forEach(itx => {
        itx.componentData.forEach(v => {
          if (v.component === 'Group') {
            const listGroup = v.propValue.filter(
              item =>
                item.innerType === 'VQuery' &&
                (popupAvailable || (!popupAvailable && v.category !== 'hidden'))
            )
            searchQuery(listGroup, filter, curComponentId, firstLoad)
          }
        })

        const arr = itx.componentData.filter(item => item.innerType === 'VQuery')
        searchQuery(arr, filter, curComponentId, firstLoad)
      })
    }
  })
  return {
    filter
  }
}

// 获取文本条件筛选的实际值，支持单条件和双条件组合。
const getResult = (
  conditionType,
  defaultConditionValueF,
  defaultConditionValueS,
  conditionValueF,
  conditionValueS,
  firstLoad
) => {
  const valueF = firstLoad ? defaultConditionValueF : conditionValueF
  const valueS = firstLoad ? defaultConditionValueS : conditionValueS
  if (conditionType === 0) {
    return valueF === '' ? [] : valueF
  }
  return [valueF || '', valueS || ''].filter(ele => ele !== '')
}

// 获取数值范围筛选的实际值，首次加载且未启用默认值时不生成筛选条件。
const getResultNum = (
  defaultNumValueEnd,
  numValueEnd,
  numValueStart,
  defaultNumValueStart,
  defaultValueCheck,
  firstLoad
) => {
  if (firstLoad && !defaultValueCheck) {
    return []
  }
  const valueS = firstLoad ? defaultNumValueStart : numValueStart
  const valueE = firstLoad ? defaultNumValueEnd : numValueEnd
  return [valueS ?? '', valueE ?? ''].filter(ele => ele !== '')
}

// 根据筛选组件类型和条件配置计算查询操作符。
const getOperator = (
  displayType,
  multiple,
  conditionType,
  defaultConditionValueOperatorF,
  defaultConditionValueF,
  defaultConditionValueOperatorS,
  defaultConditionValueS,
  conditionValueOperatorF,
  conditionValueF,
  conditionValueOperatorS,
  conditionValueS,
  firstLoad
) => {
  if (+displayType === 9) {
    // 树形筛选始终使用 in 查询多个层级值。
    return 'in'
  }

  if (+displayType === 22) {
    // 数值范围固定使用 between。
    return 'between'
  }

  const valueF = firstLoad ? defaultConditionValueF : conditionValueF
  const valueS = firstLoad ? defaultConditionValueS : conditionValueS
  const operatorF = firstLoad ? defaultConditionValueOperatorF : conditionValueOperatorF
  const operatorS = firstLoad ? defaultConditionValueOperatorS : conditionValueOperatorS
  if (displayType === '8') {
    if (conditionType === 0) {
      return operatorF
    }
    const operatorArr = [valueF === '' ? '' : operatorF, valueS === '' ? '' : operatorS].filter(
      ele => ele !== ''
    )
    if (operatorArr.length === 2) {
      // 双条件根据 AND/OR 配置拼接组合操作符。
      return operatorArr.join(`-${conditionType === 1 ? 'and' : 'or'}-`)
    }
    return valueF === '' ? operatorS : operatorF
  }

  return [1, 7].includes(+displayType) ? 'between' : 'in'
}

// 根据字段 ID 对参数数组去重，保留每个字段最后一次出现的参数配置。
const duplicateRemoval = arr => {
  const objList = []
  let idList = arr.map(ele => ele.id)
  for (let index = 0; index < arr.length; index++) {
    const element = arr[index]
    if (idList.includes(element.id)) {
      objList.push(element)
      idList = idList.filter(ele => ele !== element.id)
    }
  }
  return objList
}

// 将查询组件配置转换为图表查询筛选条件。
export const searchQuery = (queryComponentList, filter, curComponentId, firstLoad) => {
  queryComponentList.forEach(ele => {
    if (!!ele.propValue?.length) {
      ele.propValue.forEach(item => {
        let shouldSearch = false
        const relationshipChartIndex = []
        const ids = Array(5).fill(1)
        if (item.displayType === '9' && item.treeCheckedList?.length) {
          // 树形筛选允许不同层级绑定不同图表字段，需要记录命中的层级索引。
          item.treeCheckedList.forEach((itx, idx) => {
            if (
              itx.checkedFields.includes(curComponentId) &&
              itx.checkedFieldsMap[curComponentId] &&
              idx < item.treeFieldList.length
            ) {
              relationshipChartIndex.push(idx)
              ids[idx] = itx.checkedFieldsMap[curComponentId]
            }
          })
        } else {
          shouldSearch =
            item.checkedFields.includes(curComponentId) && item.checkedFieldsMap[curComponentId]
        }
        if (shouldSearch || relationshipChartIndex.length) {
          // 只有当前图表字段被查询组件绑定时，才生成对应筛选条件。
          let selectValue
          const {
            id,
            selectValue: value,
            timeGranularityMultiple,
            defaultNumValueEnd,
            numValueEnd,
            numValueStart,
            defaultNumValueStart,
            conditionType = 0,
            treeFieldList = [],
            defaultConditionValueOperatorF = 'eq',
            defaultConditionValueF = '',
            defaultConditionValueOperatorS = 'like',
            defaultConditionValueS = '',
            conditionValueOperatorF = 'eq',
            conditionValueF = '',
            conditionValueOperatorS = 'like',
            conditionValueS = '',
            defaultValueCheck,
            timeType = 'fixed',
            defaultValue,
            optionValueSource,
            defaultMapValue,
            mapValue,
            parameters = [],
            timeGranularity = 'date',
            displayType,
            displayId,
            multiple,
            optionFilter
          } = item

          const isTree = +displayType === 9
          if (optionFilter) {
            // 选项过滤来自候选值过滤，作为独立筛选条件先写入。
            let fieldIdOption = item.checkedFieldsMap[curComponentId]
            const optionFilterValue = isTree ? optionFilter.map(decodeTreeLevelValue) : optionFilter
            if (isTree) {
              const [i, r] = getFieldId(
                treeFieldList,
                optionFilterValue,
                relationshipChartIndex,
                ids
              )
              fieldIdOption = i
            }
            filter.push({
              filterId: id,
              filterFrom: 'optionFilter',
              componentId: ele.id,
              fieldId: fieldIdOption,
              operator: 'in',
              value: optionFilterValue,
              parameters: [],
              isTree
            })
          }

          if (
            timeType === 'dynamic' &&
            [1, 7].includes(+displayType) &&
            firstLoad &&
            defaultValueCheck
          ) {
            // 首次加载动态时间默认值时即时计算时间范围，并回写组件当前值。
            if (+displayType === 1) {
              selectValue = getDynamicRange(item)
              item.defaultValue = new Date(selectValue[0])
              item.selectValue = new Date(selectValue[0])
            } else {
              const {
                timeNum,
                relativeToCurrentType,
                around,
                relativeToCurrentRange,
                arbitraryTime,
                timeGranularity,
                timeNumRange,
                relativeToCurrentTypeRange,
                aroundRange,
                timeGranularityMultiple,
                arbitraryTimeRange
              } = item

              let startTime = getCustomTime(
                timeNum,
                relativeToCurrentType,
                timeGranularity,
                around,
                arbitraryTime,
                timeGranularityMultiple,
                'start-panel'
              )
              let endTime = getCustomTime(
                timeNumRange,
                relativeToCurrentTypeRange,
                timeGranularity,
                aroundRange,
                arbitraryTimeRange,
                timeGranularityMultiple,
                'end-panel'
              )

              if (!!relativeToCurrentRange && relativeToCurrentRange !== 'custom') {
                // 非自定义范围直接使用快捷范围计算结果。
                ;[startTime, endTime] = getCustomRange(relativeToCurrentRange)
              }
              item.defaultValue = [startTime, endTime]
              item.selectValue = [startTime, endTime]
              selectValue = [startTime, endTime]
            }
          } else if (displayType === '8') {
            selectValue = getResult(
              conditionType,
              defaultConditionValueF,
              defaultConditionValueS,
              conditionValueF,
              conditionValueS,
              firstLoad
            )
          } else if (displayType === '22') {
            selectValue = getResultNum(
              defaultNumValueEnd,
              numValueEnd,
              numValueStart,
              defaultNumValueStart,
              defaultValueCheck,
              firstLoad
            )
          } else {
            selectValue = getValueByDefaultValueCheckOrFirstLoad(
              defaultValueCheck,
              defaultValue,
              value,
              firstLoad,
              multiple,
              defaultMapValue,
              optionValueSource,
              mapValue,
              displayType,
              displayId
            )
          }
          if (
            !!selectValue?.length ||
            ['[object Number]', '[object Date]'].includes(
              Object.prototype.toString.call(selectValue)
            ) ||
            displayType === '8'
          ) {
            // 只有存在有效筛选值时才继续组装查询条件，文本条件允许空值参与判断。
            let result = forMatterValue(
              +displayType,
              selectValue,
              timeGranularity,
              timeGranularityMultiple
            )
            const operator = getOperator(
              displayType,
              multiple,
              conditionType,
              defaultConditionValueOperatorF,
              defaultConditionValueF,
              defaultConditionValueOperatorS,
              defaultConditionValueS,
              conditionValueOperatorF,
              conditionValueF,
              conditionValueOperatorS,
              conditionValueS,
              firstLoad
            )
            if (result?.length) {
              let fieldId = item.checkedFieldsMap[curComponentId]
              if (isTree) {
                // 树形筛选需要根据层级映射重新计算字段 ID 和结果值。
                const [i, r] = getFieldId(treeFieldList, result, relationshipChartIndex, ids)
                fieldId = i
                result = r
              }
              let parametersFilter = duplicateRemoval(
                parameters.reduce((pre, next) => {
                  if (next.id === fieldId && !pre.length) {
                    pre.push(next)
                  }
                  return pre
                }, [])
              )

              if (item.checkedFieldsMapArr?.[curComponentId]?.length) {
                // 时间范围绑定两个字段时，为结束字段额外生成 END 条件。
                const endTimeFieldId = item.checkedFieldsMapArr?.[curComponentId].find(
                  element => element !== fieldId
                )
                const resultEnd = Array(2).fill(
                  endTimeFieldId === item.checkedFieldsMapEnd[curComponentId]
                    ? result[1]
                    : result[0]
                )
                result = Array(2).fill(
                  endTimeFieldId === item.checkedFieldsMapEnd[curComponentId]
                    ? result[0]
                    : result[1]
                )
                parametersFilter = duplicateRemoval(
                  item.parametersArr[curComponentId].filter(e => e.id === fieldId)
                )

                const parametersFilterEnd = duplicateRemoval(
                  item.parametersArr[curComponentId].filter(e => e.id === endTimeFieldId)
                )
                filter.push({
                  filterId: id,
                  componentId: ele.id,
                  fieldId: endTimeFieldId,
                  arrayType: 'END',
                  operator,
                  value: resultEnd,
                  parameters: parametersFilterEnd,
                  isTree
                })
              }

              if (item.checkedFieldsMapArrNum?.[curComponentId]?.length) {
                // 数值范围绑定两个字段时，同样拆成起始字段和结束字段两个条件。
                const endTimeFieldId = item.checkedFieldsMapArrNum?.[curComponentId].find(
                  element => element !== fieldId
                )
                const resultEnd = Array(2).fill(
                  endTimeFieldId === item.checkedFieldsMapEndNum[curComponentId]
                    ? result[1]
                    : result[0]
                )
                result = Array(2).fill(
                  endTimeFieldId === item.checkedFieldsMapEndNum[curComponentId]
                    ? result[0]
                    : result[1]
                )
                parametersFilter = duplicateRemoval(
                  item.parametersArr[curComponentId].filter(e => e.id === fieldId)
                )

                const parametersFilterEnd = duplicateRemoval(
                  item.parametersArr[curComponentId].filter(e => e.id === endTimeFieldId)
                )
                filter.push({
                  filterId: id,
                  componentId: ele.id,
                  fieldId: endTimeFieldId,
                  arrayType: 'END',
                  operator,
                  value: resultEnd,
                  parameters: parametersFilterEnd,
                  isTree
                })
              }

              // 主筛选条件最后写入，保证 END 条件先被后端识别为范围补充。
              filter.push({
                filterId: id,
                componentId: ele.id,
                fieldId,
                operator,
                value: result,
                parameters: parametersFilter,
                isTree
              })
            }
          }
        }
      })
    }
  })
}
