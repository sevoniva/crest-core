// 兼容不同元数据结构中字段名的取值位置
const fieldName = item => item?.field ?? item?.engineFieldName

// 判断字段是否为指标字段，指标字段之后不再参与维度合并
const isQuotaField = (field, fieldsMap) => fieldsMap[field]?.groupType === 'q'

// 将字段配置转为以 engineFieldName 为键的快速查询表
const createFieldMap = fields =>
  (fields || []).reduce((pre, cur) => {
    pre[cur.engineFieldName] = cur
    return pre
  }, {})

// 在指定行范围内生成同值单元格的合并信息
const buildCellsForRange = ({ data, field, colIndex, start, end }) => {
  const mergeCells = []
  const nextRanges = []
  let lastVal = data[start]?.[field]
  let lastIndex = start

  for (let index = start; index <= end; index++) {
    const curVal = data[index]?.[field]
    if (curVal !== lastVal || index === end) {
      const curRange = index - lastIndex
      if (curRange > 1 || (index === end && curRange === 1 && lastVal === curVal)) {
        const tmpMergeCells = []
        let textIndex = curRange % 2 === 1 ? (curRange - 1) / 2 : curRange / 2 - 1
        if (index === end && lastVal === curVal) {
          textIndex = curRange % 2 === 1 ? (curRange - 1) / 2 : curRange / 2
        }
        for (let j = 0; j < curRange; j++) {
          tmpMergeCells.push({
            colIndex,
            rowIndex: lastIndex + j,
            showText: j === textIndex
          })
        }
        if (index === end && lastVal === curVal) {
          tmpMergeCells.push({
            colIndex,
            rowIndex: index,
            showText: false
          })
        }
        mergeCells.push(tmpMergeCells)
        nextRanges.push([lastIndex, index === end && lastVal === curVal ? index : index - 1])
      }
      lastVal = curVal
      lastIndex = index
    }
  }

  return { mergeCells, nextRanges }
}

// 根据前置维度字段切分上下文范围，避免跨父级维度合并
const buildContextRanges = (data, contextFields) => {
  if (!contextFields.length) {
    return [[0, data.length - 1]]
  }

  const ranges = []
  let start = 0
  for (let index = 1; index < data.length; index++) {
    const changed = contextFields.some(field => data[index]?.[field] !== data[index - 1]?.[field])
    if (changed) {
      ranges.push([start, index - 1])
      start = index
    }
  }
  ranges.push([start, data.length - 1])
  return ranges
}

// 获取表格中位于指标字段之前、可供用户选择合并的维度字段
export const getMergeableFieldOptions = axis => {
  const options = []
  for (const item of axis || []) {
    if (item?.groupType === 'q') {
      break
    }
    options.push({
      id: item.engineFieldName,
      label: item.chartShowName ?? item.name
    })
  }
  return options
}

// 构建透视表维度列的合并单元格坐标信息
export const buildMergeCellsInfo = ({ fields, meta, data, showIndex = false, mergeFields }) => {
  const tableData = data || []
  if (!tableData.length) {
    return []
  }

  const fieldsMap = createFieldMap(fields)
  const quotaIndex = (meta || []).findIndex(item => isQuotaField(fieldName(item), fieldsMap))
  if (quotaIndex === 0) {
    return []
  }

  const mergeableColumns = (meta || [])
    .map((item, index) => ({
      field: fieldName(item),
      colIndex: showIndex ? index + 1 : index,
      axisIndex: index
    }))
    .filter((_, index) => index < quotaIndex || quotaIndex === -1)

  const isCustomMerge = Array.isArray(mergeFields)
  const selectedFields = isCustomMerge ? new Set(mergeFields) : null
  const columnsToMerge = isCustomMerge
    ? mergeableColumns.filter(item => selectedFields.has(item.field))
    : mergeableColumns

  if (!columnsToMerge.length) {
    return []
  }

  const mergedCellsInfo = []

  if (isCustomMerge) {
    columnsToMerge.forEach(column => {
      const contextFields = mergeableColumns
        .filter(item => item.axisIndex < column.axisIndex)
        .map(item => item.field)
      const ranges = buildContextRanges(tableData, contextFields)
      ranges.forEach(([start, end]) => {
        const result = buildCellsForRange({
          data: tableData,
          field: column.field,
          colIndex: column.colIndex,
          start,
          end
        })
        mergedCellsInfo.push(...result.mergeCells)
      })
    })
    return mergedCellsInfo
  }

  let ranges = [[0, tableData.length - 1]]

  columnsToMerge.forEach(column => {
    const nextRanges = []
    ranges.forEach(([start, end]) => {
      const result = buildCellsForRange({
        data: tableData,
        field: column.field,
        colIndex: column.colIndex,
        start,
        end
      })
      mergedCellsInfo.push(...result.mergeCells)
      nextRanges.push(...result.nextRanges)
    })
    ranges = nextRanges
  })

  if (showIndex && mergedCellsInfo.length) {
    const firstMergedColIndex = mergedCellsInfo[0][0].colIndex
    const indexMergedCells = mergedCellsInfo.filter(
      cells => cells[0].colIndex === firstMergedColIndex
    )
    indexMergedCells.reverse().forEach(cells => {
      const tmpCells = cells.map(cell => ({
        ...cell,
        colIndex: 0
      }))
      mergedCellsInfo.unshift(tmpCells)
    })
  }

  return mergedCellsInfo
}
