import { EnumValue, enumValueObj } from '@/api/dataset'
import {
  joinTreeLevelParts,
  normalizeTreeLevelValue,
  splitTreeLevelValue,
  TREE_LEVEL_SEPARATOR
} from '@/utils/treeLevelSeparator'

/**
 * 查询组件字段值到实际查询值的映射缓存
 */
let filterEnumMap = {}

/**
 * 预留的字段 ID 与名称映射缓存
 */
const filterIdNameEnumMap = {}

/**
 * 查询并构建展示值到查询值的枚举映射
 */
const findFilterEnum = async (val: EnumValue) => {
  const queryId = val.queryId
  const displayId = val.displayId
  const arr = await enumValueObj({ queryId: queryId, displayId: displayId, searchText: '' })
  return arr?.reduce((acc, item) => {
    acc[item[displayId]] = item[queryId]
    return acc
  }, {})
}

/**
 * 将筛选参数中的展示值转换为真实查询值
 */
export const filterEnumParams = (queryParams, fieldId: string) => {
  const resultMap = filterEnumMap[fieldId]
  if (resultMap) {
    const resultParams = []
    queryParams.forEach(param => {
      resultParams.push(resultMap[param] || param)
    })
    return resultParams
  } else {
    return queryParams
  }
}

/**
 * 将筛选参数中的真实查询值反向转换为展示值
 */
export const filterEnumParamsReduce = (queryParams, fieldId: string) => {
  const resultMap = filterEnumMap[fieldId]
  if (resultMap) {
    const resultMapReduce = Object.fromEntries(
      Object.entries(resultMap).map(([key, value]) => [value, key])
    )
    const resultParams = []
    queryParams.forEach(param => {
      resultParams.push(resultMapReduce[param] || param)
    })
    return resultParams
  } else {
    return queryParams
  }
}

/**
 * 同步查询组件中所有需要枚举映射的字段缓存
 */
export const filterEnumMapSync = async componentData => {
  filterEnumMap = {}
  for (const element of componentData) {
    if (element.component === 'VQuery') {
      for (const filterItem of element.propValue) {
        const { optionValueSource, field, displayId } = filterItem
        if (optionValueSource === 1 && field.id) {
          filterEnumMap[field.id] = await findFilterEnum({
            queryId: field.id,
            displayId,
            searchText: ''
          })
        }
      }
    }
  }
}

/**
 * 根据参数选项过滤当前参数值，支持层级参数的父子级匹配
 */
export function filterParamsOptions(params, paramsOption: string[]) {
  // 如果 params 为空，直接返回 null
  if (!params || (Array.isArray(params) && params.length === 0)) {
    return null
  }
  // 如果 paramsOption 为空，直接返回 null
  if (!paramsOption || paramsOption.length === 0) {
    return null
  }
  // 创建 paramsOption 集合和前缀集合用于快速查找
  const optionSet = new Set<string>(paramsOption)
  const normalizedOptionSet = new Set<string>(paramsOption.map(normalizeTreeLevelValue))
  const prefixSet = new Set<string>()
  // 收集所有可能的父级前缀
  paramsOption.forEach(option => {
    const normalizedOption = normalizeTreeLevelValue(option)
    if (normalizedOption.includes(TREE_LEVEL_SEPARATOR)) {
      const parts = splitTreeLevelValue(normalizedOption)
      // 收集所有前缀：父级、祖父级等
      for (let i = 1; i < parts.length; i++) {
        const prefix = joinTreeLevelParts(parts.slice(0, i))
        prefixSet.add(prefix)
      }
    }
  })

  // 检查一个值是否在 paramsOption 中存在（考虑层级关系）
  /**
   * 判断单个参数值是否存在于可选项或其层级关系中
   */
  function checkValueExists(value: string) {
    const normalizedValue = normalizeTreeLevelValue(value)
    // 直接存在
    if (optionSet.has(value) || normalizedOptionSet.has(normalizedValue)) {
      return true
    }
    // 如果是层级结构，检查所有父级前缀
    if (normalizedValue.includes(TREE_LEVEL_SEPARATOR)) {
      const parts = splitTreeLevelValue(normalizedValue)

      // 检查所有可能的父级前缀
      for (let i = 1; i < parts.length; i++) {
        const prefix = joinTreeLevelParts(parts.slice(0, i))
        if (normalizedOptionSet.has(prefix)) {
          return true
        }
      }
    }

    // 检查该值是否是某个选项的父级
    // 如：paramsOption 中有 "香橙店 > 浓郁椰奶"，传入 "香橙店" 也应该匹配
    if (
      Array.from(normalizedOptionSet).some(
        option =>
          option.startsWith(normalizedValue + TREE_LEVEL_SEPARATOR) || option === normalizedValue
      )
    ) {
      return true
    }
    // 检查该值是否是某个选项的前缀（通过 prefixSet）
    if (prefixSet.has(normalizedValue)) {
      return true
    }
    return false
  }
  // 处理单值情况（字符串）
  if (typeof params === 'string') {
    return checkValueExists(params) ? params : null
  }
  // 处理数组情况
  if (Array.isArray(params)) {
    // 过滤出存在的值
    const filtered = params.filter(value => typeof value === 'string' && checkValueExists(value))
    // 如果过滤后为空，返回 null，否则返回过滤后的数组
    return filtered.length > 0 ? filtered : null
  }
  // 其他类型返回 null
  return null
}
