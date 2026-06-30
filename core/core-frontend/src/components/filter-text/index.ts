import FilterText from './src/FilterText.vue'

export { FilterText }

// 根据字段名查找筛选字段展示名称
const fieldText = (field, options) => {
  for (let index = 0; index < options.length; index++) {
    const element = options[index]
    if (field === element.field) {
      return element.title
    }
  }
}
// 从树形选项中查找筛选值展示名称
const valueTextFormTree = (val, options) => {
  let result = null
  const stack = [...options]
  while (stack.length) {
    const item = stack.pop()
    if (item.value === val) {
      result = item.label
      break
    }
    if (item.children?.length) {
      item.children.forEach(kid => stack.push(kid))
    }
  }
  return result || val
}
// 将时间戳格式化为本地时间字符串
const timestampFormatDate = value => {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString()
}
// 根据字段配置将筛选值转换为展示文案
const valueText = (field, val, options) => {
  for (let index = 0; index < options.length; index++) {
    const element = options[index]
    if (field === element.field) {
      let isTree = false
      const selectOption = element.option
      for (let i = 0; i < selectOption.length; i++) {
        const item = selectOption[i]
        if (item.hasOwnProperty('children')) {
          isTree = true
          break
        }
        if (item.id === val || item.value === val) {
          return item.name || item.label
        }
      }
      if (element.type === 'time') {
        return timestampFormatDate(val)
      }
      if (isTree) {
        return valueTextFormTree(val, selectOption)
      }
    }
  }
  return val
}
// 将筛选条件数组转换为可读的文本列表
export const convertFilterText = (conditions, options) => {
  const result = []
  conditions.forEach(condition => {
    const field = condition.field
    const ope = condition.operator
    const value = condition.value
    const fieldName = fieldText(field, options)
    const textOpe = ':'
    let separator = '，'
    if (ope === 'in') {
      separator = '、'
    }
    if (ope === 'between') {
      separator = '~'
    }
    let valText = value

    if (value) {
      if (Array.isArray(value)) {
        const valTextArray = value.map(val => valueText(field, val, options))
        valText = valTextArray.join(separator)
      }
      if (valText?.length) {
        const obj = fieldName + textOpe + valText
        result.push(obj)
      }
    }
  })
  return result
}
