// 表格序号列使用的内部字段名
export const TABLE_SERIES_NUMBER_FIELD = '$$series_number$$'

// 根据表头配置写入表格序号列选项
export function configureTableSeriesNumber(options, tableHeader = {}) {
  const enable = Boolean(tableHeader.showIndex)
  options.showSeriesNumber = enable
  options.seriesNumber = {
    ...(options.seriesNumber || {}),
    enable,
    text: tableHeader.indexLabel || ''
  }
  return options
}

// 判断字段名是否为表格序号列字段
export function isTableSeriesNumberField(field) {
  return field === TABLE_SERIES_NUMBER_FIELD
}

// 判断表格元数据是否对应序号列单元格
export function isTableSeriesNumberCell(meta) {
  return isTableSeriesNumberField(meta?.valueField)
}

// 判断布局节点是否为序号列表头节点
export function isTableSeriesNumberNode(node) {
  return isTableSeriesNumberField(node?.field)
}

// 根据分页信息和行下标计算跨页序号
export function getPageSeriesNumber(pageInfo = {}, rowIndex = 0) {
  const pageSize = Number(pageInfo.pageSize || 0)
  const currentPage = Math.max(Number(pageInfo.currentPage || 1), 1)
  const offset = pageSize ? pageSize * (currentPage - 1) : 0
  return offset + rowIndex + 1
}

// 将序号列表头扩展到完整表头高度
export function spanSeriesNumberHeader(layoutResult) {
  const headerHeight = Number(layoutResult?.colsHierarchy?.height || 0)
  if (!headerHeight) {
    return false
  }
  let changed = false
  ;(layoutResult?.colNodes || []).forEach(node => {
    if (!isTableSeriesNumberNode(node)) {
      return
    }
    node.y = 0
    node.height = headerHeight
    changed = true
  })
  return changed
}
