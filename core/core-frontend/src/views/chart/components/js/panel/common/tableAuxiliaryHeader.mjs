// 标记 S2 列是否为辅助表头描述列
export const AUXILIARY_HEADER_FLAG = 'crestAuxiliaryHeader'

// 辅助表头默认配置
export const DEFAULT_AUXILIARY_HEADER = {
  enabled: false,
  rowHeight: 120,
  backgroundColor: '#FFFFFF',
  fontColor: '#1F2329',
  fontSize: 14,
  align: 'center',
  descriptions: []
}

const GENERATED_GROUP_PREFIX = '__crest_aux_header_group__'

// 将空值安全转换为文本
function toText(value) {
  if (value === null || value === undefined) {
    return ''
  }
  return String(value)
}

// 从列配置中读取字段标识
function getColumnField(column) {
  if (typeof column === 'string') {
    return column
  }
  return column?.key ?? column?.field ?? ''
}

// 解析列标题，优先使用列配置和分组名称
function getColumnTitle(column, groupNameMap, fieldNameMap) {
  if (typeof column === 'string') {
    return fieldNameMap[column] ?? column
  }
  const field = getColumnField(column)
  return column?.title ?? groupNameMap[field] ?? fieldNameMap[field] ?? field
}

// 归一化辅助表头描述列表
function normalizeDescriptions(descriptions) {
  if (!Array.isArray(descriptions)) {
    return []
  }
  return descriptions
    .filter(item => item && item.field)
    .map(item => ({
      field: String(item.field),
      text: toText(item.text)
    }))
}

// 合并辅助表头配置并补齐默认值
export function normalizeAuxiliaryHeader(auxiliaryHeader) {
  return {
    ...DEFAULT_AUXILIARY_HEADER,
    ...(auxiliaryHeader || {}),
    rowHeight: Number(auxiliaryHeader?.rowHeight) || DEFAULT_AUXILIARY_HEADER.rowHeight,
    fontSize: Number(auxiliaryHeader?.fontSize) || DEFAULT_AUXILIARY_HEADER.fontSize,
    descriptions: normalizeDescriptions(auxiliaryHeader?.descriptions)
  }
}

// 判断辅助表头是否启用
export function isAuxiliaryHeaderEnabled(auxiliaryHeader) {
  return normalizeAuxiliaryHeader(auxiliaryHeader).enabled === true
}

// 构建字段到辅助描述的映射
export function getAuxiliaryDescriptionMap(auxiliaryHeader) {
  return normalizeAuxiliaryHeader(auxiliaryHeader).descriptions.reduce((pre, cur) => {
    pre[cur.field] = cur.text
    return pre
  }, {})
}

// 按字段列表生成稳定顺序的辅助描述
export function normalizeAuxiliaryDescriptions(auxiliaryHeader, fields) {
  const descMap = getAuxiliaryDescriptionMap(auxiliaryHeader)
  return fields.map(field => ({
    field,
    text: descMap[field] ?? ''
  }))
}

// 判断字段列表中是否存在有效辅助描述
export function hasAuxiliaryDescription(auxiliaryHeader, fields) {
  const descMap = getAuxiliaryDescriptionMap(auxiliaryHeader)
  return fields.some(field => toText(descMap[field]).trim().length > 0)
}

// 收集列树中的叶子字段
export function getLeafColumnFields(columns) {
  const result = []
  // 深度遍历列树以保留展示顺序
  const walk = list => {
    ;(list || []).forEach(column => {
      if (typeof column !== 'string' && column?.children?.length) {
        walk(column.children)
        return
      }
      const field = getColumnField(column)
      if (field) {
        result.push(field)
      }
    })
  }
  walk(columns)
  return result
}

// 判断指定字段是否为辅助表头描述列
export function isAuxiliaryHeaderField(columns, field) {
  const targetField = toText(field)
  if (!targetField) {
    return false
  }
  let matched = false
  // 深度遍历叶子列并在命中后短路
  const walk = list => {
    ;(list || []).forEach(column => {
      if (matched) {
        return
      }
      const children = typeof column === 'string' ? [] : column?.children || []
      if (children.length) {
        walk(children)
        return
      }
      if (getColumnField(column) === targetField) {
        matched = Boolean(column?.[AUXILIARY_HEADER_FLAG])
      }
    })
  }
  walk(columns)
  return matched
}

// 构建叶子字段到完整表头路径的映射
function getLeafColumnPaths(columns, fieldNameMap = {}) {
  const result = {}
  // 递归记录每个叶子字段经过的表头层级
  const walk = (list, path) => {
    ;(list || []).forEach(column => {
      const field = getColumnField(column)
      const title = getColumnTitle(column, {}, fieldNameMap)
      const nextPath = [...path, title]
      const children = typeof column === 'string' ? [] : column?.children || []
      if (children.length) {
        walk(children, nextPath)
        return
      }
      if (field) {
        result[field] = nextPath.length ? nextPath : [fieldNameMap[field] ?? field]
      }
    })
  }
  walk(columns, [])
  return result
}

// 将表头路径补齐到统一深度
function alignHeaderPath(path, depth) {
  if (!depth) {
    return []
  }
  if (!path.length) {
    return Array.from({ length: depth }, () => '')
  }
  if (path.length >= depth) {
    return path.slice(0, depth)
  }
  const [first] = path
  return [...Array.from({ length: depth - path.length }, () => first), ...path]
}

// 构建复制表头时使用的二维行数据
export function buildHeaderCopyRows(columns, fields, fieldNameMap = {}) {
  const leafPaths = getLeafColumnPaths(columns, fieldNameMap)
  const paths = (fields || []).map(field => {
    const normalizedField = toText(field)
    return leafPaths[normalizedField] || [fieldNameMap[normalizedField] ?? normalizedField]
  })
  const depth = Math.max(0, ...paths.map(path => path.length))
  if (!depth) {
    return []
  }
  const alignedPaths = paths.map(path => alignHeaderPath(path, depth))
  return Array.from({ length: depth }, (_, rowIndex) =>
    alignedPaths.map(path => path[rowIndex] ?? '')
  )
}

// 创建 S2 列配置对象
function createS2Column(field, title, children, extra = {}) {
  const column = {
    key: field,
    field,
    title: toText(title)
  }
  if (children?.length) {
    column.children = children
  }
  Object.assign(column, extra)
  return column
}

// 创建辅助表头描述叶子列
function createAuxiliaryLeaf(field, description) {
  return createS2Column(field, description, undefined, {
    [AUXILIARY_HEADER_FLAG]: true
  })
}

// 根据辅助表头配置构建 S2 可渲染列树
export function buildS2HeaderColumns(columns, meta = [], fieldNameMap = {}, auxiliaryHeader) {
  const groupNameMap = meta.reduce((pre, cur) => {
    if (cur?.field) {
      pre[cur.field] = cur.name
    }
    return pre
  }, {})
  const auxiliaryEnabled = isAuxiliaryHeaderEnabled(auxiliaryHeader)
  const descriptionMap = getAuxiliaryDescriptionMap(auxiliaryHeader)
  const sourceColumns = columns || []

  if (!auxiliaryEnabled && sourceColumns.every(column => typeof column === 'string')) {
    return [...sourceColumns]
  }

  // 递归转换原始列树并插入辅助描述列
  const walk = (column, isRoot) => {
    const field = getColumnField(column)
    const title = getColumnTitle(column, groupNameMap, fieldNameMap)
    const children = typeof column === 'string' ? [] : column?.children || []

    if (!children.length) {
      if (!auxiliaryEnabled) {
        return createS2Column(field, title)
      }

      const leaf = createAuxiliaryLeaf(field, descriptionMap[field] ?? '')
      if (!isRoot) {
        return leaf
      }
      return createS2Column(`${GENERATED_GROUP_PREFIX}${field}`, title, [leaf], {
        generatedAuxiliaryGroup: true,
        sourceField: field
      })
    }

    return createS2Column(
      field,
      title,
      children.map(child => walk(child, false))
    )
  }

  return sourceColumns.map(column => walk(column, true))
}
