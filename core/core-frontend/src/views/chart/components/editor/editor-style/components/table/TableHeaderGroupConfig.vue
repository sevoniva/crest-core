<template>
  <div :id="containerId" class="table-container" :class="{ dark: themes === 'dark' }"></div>
  <div class="button-group">
    <el-button :effect="themes" @click="onCancelConfig">{{ t('chart.cancel') }}</el-button>
    <el-button type="primary" @click="onConfigChange">{{ t('chart.confirm') }}</el-button>
  </div>
  <div :id="menuGroupId" class="group-menu"></div>
</template>

<script setup lang="ts">
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { formatterItem, valueFormatter } from '@/views/chart/components/js/formatter'
import {
  BaseTooltip,
  S2DataConfig,
  S2Event,
  S2Options,
  TableSheet,
  TooltipShowOptions,
  ColCell,
  LayoutResult,
  TableDataCell,
  TableColCell
} from '@antv/s2'
import { ElMessageBox } from 'element-plus-secondary'
import { cloneDeep, debounce, isEqual, isNumber } from 'lodash-es'
import { computed, nextTick, onMounted, onUnmounted, PropType } from 'vue'
import { v4 as uuidV4 } from 'uuid'
import { useI18n } from '@/hooks/web/useI18n'
import {
  getColumns,
  getCustomTheme,
  getLeafNodes
} from '@/views/chart/components/js/panel/common/common_table'

const { t } = useI18n()
const dvMainStore = dvMainStoreWithOut()
type ColumnNode = any
// 表头分组配置基于当前图表字段结构生成可交互预览。
const props = defineProps({
  chart: {
    type: Object as PropType<ChartObj>,
    required: true
  },
  themes: {
    type: String as PropType<EditorTheme>,
    default: 'dark'
  },
  propertyInner: {
    type: Array<string>
  }
})
// 父组件负责保存分组结果或关闭弹窗。
const emits = defineEmits(['onConfigChange', 'onCancelConfig'])
// 取消编辑时不回写当前 S2 临时状态。
const onCancelConfig = () => {
  emits('onCancelConfig')
}
// 明细表只使用维度轴，普通表需要把指标列追加到可分组字段中。
const allAxis = computed(() => {
  const axis = [...props.chart.xAxis]
  if (props.chart.type === 'table-normal') {
    axis.push(...props.chart.yAxis)
  }
  return axis
})
// 提交时只保留分组节点的 meta，真实字段 meta 会在渲染时按字段信息补齐。
const onConfigChange = () => {
  const showAxis = allAxis.value
    ?.map(axis => axis.hide !== true && axis.engineFieldName)
    .filter(i => i)
  const { fields, meta } = s2.dataCfg
  const groupMeta = meta.filter(item => !showAxis.includes(item.field))
  emits('onConfigChange', { columns: fields.columns, meta: groupMeta })
}

// 初始化表头分组配置并渲染预览表格
const init = () => {
  const chart = cloneDeep(props.chart)
  const { headerGroupConfig } = chart.customAttr.tableHeader
  const showColumns = []
  allAxis.value?.forEach(axis => {
    axis.hide !== true && showColumns.push({ key: axis.engineFieldName })
  })
  if (!showColumns.length) {
    return
  }
  if (headerGroupConfig?.columns?.length) {
    const allAxis = showColumns.map(item => item.key)
    const leafNodes = getLeafNodes(headerGroupConfig.columns)
    const leafKeys = leafNodes.map(item => item.key)
    if (!isEqual(allAxis, leafKeys)) {
      // 字段顺序或显隐变化后，旧分组叶子不再可信，直接回退为当前可见字段。
      const { columns, meta } = headerGroupConfig
      columns.splice(0, columns.length, ...showColumns)
      meta.splice(0, meta.length)
    }
  } else {
    chart.customAttr.tableHeader.headerGroupConfig = {
      columns: [...showColumns],
      meta: []
    }
  }
  nextTick(() => {
    renderTable(chart)
  })
}
// 生成右键菜单容器的唯一 DOM 标识
const menuGroupId = computed(() => {
  return 'menu-group-' + props.chart.id
})
// 生成表格预览容器的唯一 DOM 标识
const containerId = computed(() => {
  return 'table-container-' + props.chart.id
})
let s2: any
// 数据单元格读取主题上的字段级对齐配置，兼容表格主体的自定义对齐。
class CustomDataCell extends TableDataCell {
  protected getTextStyle(): any {
    const textStyle = super.getTextStyle() as any
    const dataCellAlignConfig = this.theme.dataCellAlignConfig
    if (dataCellAlignConfig) {
      const align = dataCellAlignConfig[this.meta.valueField]
      if (align) {
        textStyle.textAlign = align
      }
    }
    if ((textStyle.textAlign as string) === 'custom') {
      textStyle.textAlign = 'left'
    }
    return textStyle
  }
}
// 表头单元格需要区分分组节点和叶子字段，分组节点统一居中以强化层级。
class CustomColCell extends TableColCell {
  protected getTextStyle(): any {
    const textStyle = super.getTextStyle() as any
    const colCellAlignConfig = this.theme.colCellAlignConfig
    if (colCellAlignConfig) {
      // 分组节点使用居中对齐，叶子字段才读取字段级对齐配置。
      if (this.meta.children?.length) {
        textStyle.textAlign = 'center'
        return textStyle
      }
      const align = colCellAlignConfig[this.meta.field]
      if (align) {
        textStyle.textAlign = align
      }
    }
    if ((textStyle.textAlign as string) === 'custom') {
      textStyle.textAlign = 'left'
    }
    return textStyle
  }
}
// 根据当前图表配置渲染可交互的表头分组预览表格
const renderTable = (chart: ChartObj) => {
  const data = dvMainStore.getViewDataDetails(chart.id)
  const containerDom = document.getElementById(containerId.value)
  let realData = []
  if (data?.tableRow?.length) {
    realData = data.tableRow.slice(0, 10)
  }
  const { headerGroupConfig } = chart.customAttr.tableHeader
  const meta: any[] = [...headerGroupConfig.meta]
  const columns = headerGroupConfig.columns
  const axisMap = allAxis.value.reduce((pre, cur) => {
    pre[cur.engineFieldName] = cur
    return pre
  }, {})
  if (data?.fields?.length) {
    // 有真实数据字段时优先沿用后端字段元信息，保证展示名和数值格式化与预览一致。
    data.fields.forEach(ele => {
      const f = axisMap[ele.engineFieldName]
      if (f?.hide === true) {
        return
      }
      meta.push({
        field: ele.engineFieldName,
        name: ele.chartShowName ?? ele.name,
        formatter: function (value) {
          if (!f) {
            return value
          }
          if (value === null || value === undefined) {
            return value
          }
          if (![2, 3].includes(f.fieldType) || !isNumber(value)) {
            return value
          }
          let formatCfg = f.formatterCfg
          if (!formatCfg) {
            formatCfg = formatterItem
          }
          return valueFormatter(value, formatCfg)
        }
      })
    })
  } else {
    // 无预览数据时退回图表轴字段，仍允许用户配置表头分组结构。
    allAxis.value?.forEach(axis => {
      if (axis.hide !== true) {
        meta.push({
          field: axis.engineFieldName,
          name: axis.chartShowName ?? axis.name
        })
      }
    })
  }
  // S2 数据配置直接使用当前表头树，预览数据只取前 10 行降低弹窗渲染成本。
  const s2DataConfig: S2DataConfig = {
    fields: {
      columns
    },
    meta,
    data: realData
  } as any
  // options
  const s2Options: S2Options = {
    width: containerDom.getBoundingClientRect().width,
    height: containerDom.offsetHeight,
    tooltip: {
      autoAdjustBoundary: null,
      adjustPosition(positionInfo) {
        const {
          position: { x, y }
        } = positionInfo
        const scrollWidth = containerDom.scrollLeft
        const groupMenuContainer = document.getElementById(menuGroupId.value)
        const menuWidth = groupMenuContainer?.offsetWidth || 120
        const containerWidth = containerDom.offsetWidth
        if (x - scrollWidth + menuWidth > containerWidth) {
          return { x: x - menuWidth, y: y + 10 }
        }
        return { x: x, y: y + 10 }
      },
      getContainer: () => containerDom,
      renderTooltip: sheet => new GroupMenu(sheet),
      style: {
        position: 'absolute',
        borderRadius: '4px'
      }
    },
    interaction: {
      rangeSelection: false,
      resize: {
        colCellHorizontal: false,
        colCellVertical: false,
        rowCellVertical: false
      }
    },
    dataCell: (meta, sheet, config) => {
      return new CustomDataCell(meta, sheet, config)
    },
    colCell: (meta, sheet, config) => {
      return new CustomColCell(meta, sheet, config)
    }
  } as any
  s2 = new TableSheet(containerDom, s2DataConfig, s2Options)
  const { tableHeader, tableCell } = chart.customAttr
  const theme = getCustomTheme(chart) as any
  if (tableHeader.tableHeaderAlign === 'custom') {
    // S2 主题扩展字段级对齐映射，供自定义表头单元格读取。
    theme.colCellAlignConfig =
      tableHeader.alignConfig?.reduce((pre, cur) => {
        pre[cur.id] = cur.align
        return pre
      }, {}) || {}
  }
  if (tableCell.tableItemAlign === 'custom') {
    // 数据单元格对齐独立于表头配置，避免表头分组影响表体展示。
    theme.dataCellAlignConfig =
      tableCell.alignConfig?.reduce((pre, cur) => {
        pre[cur.id] = cur.align
        return pre
      }, {}) || {}
  }
  s2.setTheme(theme)
  const groupMenuContainer = document.getElementById(menuGroupId.value)
  s2.on(S2Event.COL_CELL_CONTEXT_MENU, e => {
    e.preventDefault()
    const curColumns = s2.dataCfg.fields.columns as Array<ColumnNode>
    const curMeta = s2.dataCfg.meta
    const activeCells = s2.interaction.getActiveCells()
    const colKeys = activeCells?.map(cell => cell.getMeta().field)
    const activeColumns = getColumns(colKeys, curColumns)
    const curCell = s2.getCell(e.target)
    groupMenuContainer.innerText = ''
    // 右键菜单基于当前 S2 选区生成，所有操作都直接修改内存中的 columns 和 meta。
    // 右键目标不在当前选区内时清空选区，避免对非选中列执行分组操作。
    if (activeColumns?.length) {
      const index = activeColumns.findIndex(cell => cell.key === curCell.getMeta().field)
      if (index === -1) {
        s2.interaction.clearState()
        s2.hideTooltip()
        return
      }
    }
    // 单个分组节点只提供取消分组、取消全部分组和重命名操作。
    if (activeColumns?.length === 1 && curCell.getMeta().colIndex === -1) {
      s2.interaction.clearState()
      s2.interaction.selectHeaderCell({ cell: curCell })
      const cancelBtn = document.createElement('span')
      groupMenuContainer.appendChild(cancelBtn)
      cancelBtn.innerText = t('chart.cancel_group')
      cancelBtn.onclick = () => {
        s2.hideTooltip()
        const parent = curCell.getMeta().parent
        if (parent?.id === 'root') {
          const startIndex = curColumns.findIndex(cell => cell.key === curCell.getMeta().field)
          const [curCol] = getColumns([curCell.getMeta().field], curColumns)
          if (startIndex === -1 || !curCol?.children) return
          curColumns.splice(startIndex, 1, ...curCol.children)
          const index = curMeta.findIndex(meta => meta.field === curCell.getMeta().field)
          if (index !== -1) {
            curMeta.splice(index, 1)
          }
          s2.setDataCfg({
            fields: {
              columns: curColumns
            },
            meta: curMeta
          })
          s2.render(true)
        } else {
          const [parentColumn] = getColumns([parent.field], curColumns)
          if (parentColumn) {
            const startIndex = parentColumn.children?.findIndex(
              cell => cell.key === curCell.getMeta().field
            )
            const [curCol] = getColumns([curCell.getMeta().field], parentColumn.children)
            if (startIndex === undefined || startIndex === -1 || !curCol?.children) return
            parentColumn.children?.splice(startIndex, 1, ...curCol.children)
            const index = curMeta.findIndex(meta => meta.field === curCell.getMeta().field)
            if (index !== -1) {
              curMeta.splice(index, 1)
            }
            s2.setDataCfg({
              fields: {
                columns: curColumns
              },
              meta: curMeta
            })
            s2.render(true)
          }
        }
        s2.interaction.clearState()
      }
      const cancelAllBtn = document.createElement('span')
      groupMenuContainer.appendChild(cancelAllBtn)
      cancelAllBtn.innerText = t('chart.cancel_all_group')
      cancelAllBtn.onclick = () => {
        s2.hideTooltip()
        const parent = curCell.getMeta().parent
        if (parent?.id === 'root') {
          const [curCol] = getColumns([curCell.getMeta().field], curColumns)
          const leafNodes = getLeafNodes(curCol.children)
          const startIndex = curColumns.findIndex(cell => cell.key === curCell.getMeta().field)
          curColumns.splice(startIndex, 1, ...leafNodes)
          const noneLeafNodes = getNonLeafNodes([curCol])
          const newMeta = curMeta.filter(meta => !noneLeafNodes.includes(meta.field))
          s2.setDataCfg({
            fields: {
              columns: curColumns
            },
            meta: newMeta
          })
          s2.render(true)
        } else {
          const [parentColumn] = getColumns([parent.field], curColumns)
          if (parentColumn) {
            const [curCol] = getColumns([curCell.getMeta().field], parentColumn.children)
            const leafNodes = getLeafNodes(curCol.children)
            const startIndex = parentColumn.children?.findIndex(
              cell => cell.key === curCell.getMeta().field
            )
            parentColumn.children?.splice(startIndex, 1, ...leafNodes)
            const noneLeafNodes = getNonLeafNodes([curCol])
            const newMeta = curMeta.filter(meta => !noneLeafNodes.includes(meta.field))
            s2.setDataCfg({
              fields: {
                columns: curColumns
              },
              meta: newMeta
            })
            s2.render(true)
          }
        }
        s2.interaction.clearState()
      }
      const renameBtn = document.createElement('span')
      groupMenuContainer.appendChild(renameBtn)
      renameBtn.innerText = t('chart.rename')
      renameBtn.onclick = () => {
        s2.hideTooltip()
        const cellField = curCell.getMeta().field
        const cellMeta = curMeta.find(meta => meta.field === cellField)
        const inputValue = cellMeta?.name || curCell.getMeta().label || cellField
        ElMessageBox.prompt('', t('chart.group_name'), {
          confirmButtonText: t('chart.confirm'),
          cancelButtonText: t('chart.cancel'),
          showClose: false,
          showInput: true,
          inputPlaceholder: t('chart.group_name_edit_tip'),
          inputValue,
          inputErrorMessage: t('chart.group_name_error_tip'),
          // 分组名称长度限制为 1 到 50，保持和后端字段展示名约束一致。
          inputValidator: val => {
            if (val?.length < 1 || val?.length > 50) {
              return t('chart.group_name_error_tip')
            }
            return true
          }
        })
          .then(res => {
            if (cellMeta) {
              cellMeta.name = res.value
            } else {
              curMeta.push({
                field: cellField,
                name: res.value
              })
            }
            s2.setDataCfg({
              meta: curMeta
            })
            s2.render(true)
          })
          .catch(() => {
            // 用户取消重命名时保留当前分组名称。
          })
      }
      s2.showTooltip({
        position: {
          x: e.x,
          y: e.y
        },
        content: groupMenuContainer
      })
      return
    }
    // 多个同层级且同父节点的表头单元格才允许合并为一个分组。
    if (activeColumns?.length > 1) {
      const sameParent = activeCells.every(
        cell => cell.getMeta().parent.id === curCell.getMeta().parent.id
      )
      if (!sameParent) {
        return
      }
      let upDepth = -1
      let tmpCell = curCell
      // 计算当前节点到根节点的深度，用于限制新分组后的总层级。
      while (tmpCell?.getMeta?.()?.parent || tmpCell?.parent) {
        upDepth++
        tmpCell = tmpCell?.getMeta?.()?.parent || tmpCell?.parent
      }
      let startIndex = -1
      let endIndex = -1
      const parent = curCell.getMeta().parent
      // 分组节点和叶子节点的 children 结构不同，需要分别计算选区边界。
      if (parent.colIndex !== -1) {
        activeColumns.forEach(cell => {
          const index = parent.children.findIndex(item => item.getMeta().field === cell.key)
          if (index < startIndex || startIndex === -1) {
            startIndex = index
          }
          if (index > endIndex || endIndex === -1) {
            endIndex = index
          }
        })
      } else {
        activeColumns.forEach(cell => {
          const index = parent.children.findIndex(item => item.key === cell.key)
          if (index < startIndex || startIndex === -1) {
            startIndex = index
          }
          if (index > endIndex || endIndex === -1) {
            endIndex = index
          }
        })
      }
      const totalColumns = []
      if (parent?.id === 'root') {
        totalColumns.push(...curColumns.slice(startIndex, endIndex + 1))
      } else {
        const [parentColumn] = getColumns([parent.field], curColumns)
        totalColumns.push(...parentColumn.children?.slice(startIndex, endIndex + 1))
      }
      const chiildDepth = getTreesMaxDepth(totalColumns)
      // 表头最多保留三层，避免 S2 表头高度过大影响可读性。
      if (chiildDepth + upDepth > 1) {
        return
      }
      const mergeBtn = document.createElement('span')
      groupMenuContainer.appendChild(mergeBtn)
      mergeBtn.innerText = t('chart.merge_group')
      mergeBtn.onclick = () => {
        s2.hideTooltip()
        ElMessageBox.prompt('', t('chart.group_name'), {
          confirmButtonText: t('chart.confirm'),
          cancelButtonText: t('chart.cancel'),
          showClose: false,
          showInput: true,
          inputPlaceholder: t('chart.group_name_edit_tip'),
          inputErrorMessage: t('chart.group_name_error_tip'),
          inputValue: t('chart.group'),
          // 新分组名称长度限制为 1 到 50。
          inputValidator: val => {
            if (val?.length < 1 || val?.length > 50) {
              return t('chart.group_name_error_tip')
            }
            return true
          }
        })
          .then(res => {
            if (parent?.id === 'root') {
              const newKey = uuidV4()
              curColumns?.splice(startIndex, endIndex - startIndex + 1, {
                key: newKey,
                children: totalColumns
              })
              curMeta.push({
                field: newKey,
                name: res.value
              })
              s2.setDataCfg({
                fields: {
                  columns: curColumns
                },
                meta: curMeta
              })
              s2.render(true)
            } else {
              const [parentColumn] = getColumns([parent.field], curColumns)
              const newKey = uuidV4()
              parentColumn.children?.splice(startIndex, endIndex - startIndex + 1, {
                key: newKey,
                children: totalColumns
              })
              curMeta.push({
                field: newKey,
                name: res.value
              })
              s2.setDataCfg({
                fields: {
                  columns: curColumns
                },
                meta: curMeta
              })
              s2.render(true)
            }
            s2.interaction.clearState()
          })
          .catch(() => {
            // 用户取消合并时不修改当前表头树。
          })
      }
      s2.showTooltip({
        position: {
          x: e.x,
          y: e.y
        },
        content: groupMenuContainer
      })
      return
    }
  })
  s2.on(S2Event.COL_CELL_CLICK, e => {
    const lastCell = s2.store.get('lastClickedCell') as ColCell
    const originEvent = e.originalEvent as MouseEvent
    if (!lastCell || !(originEvent?.ctrlKey || originEvent?.metaKey || originEvent?.shiftKey)) {
      // 普通点击只记录起点，组合键点击时再进入连续选择逻辑。
      const cell = s2.getCell(e.target)
      s2.store.set('lastClickedCell', cell)
      return
    }
    if (originEvent?.shiftKey) {
      if (!lastCell) {
        const cell = s2.getCell(e.target)
        s2.store.set('lastClickedCell', cell)
        return
      }
      const curCell = s2.getCell(e.target)
      const lastMeta = lastCell.getMeta()
      const curMeta = curCell.getMeta()
      if (
        lastMeta.key === curMeta.key ||
        lastMeta.level !== curMeta.level ||
        lastMeta.parent !== curMeta.parent
      ) {
        return
      }
      const parent = curMeta.parent as any
      const lastIndex = parent.children.findIndex(item => item.key === lastMeta.key)
      const curIndex = parent.children.findIndex(item => item.key === curMeta.key)
      const startIndex = Math.min(lastIndex, curIndex)
      const endIndex = Math.max(lastIndex, curIndex)
      const activeCells = parent.children.slice(startIndex, endIndex + 1)
      // Shift 选择会补齐同父节点内的连续表头，保证合并分组后的字段顺序稳定。
      s2.interaction.clearState()
      activeCells.forEach(cell => {
        s2.interaction.selectHeaderCell({ cell: cell.belongsCell, isMultiSelection: true })
      })
    }
  })
  s2.once(S2Event.LAYOUT_AFTER_HEADER_LAYOUT, (e: LayoutResult) => {
    const initialized = s2.store.get('initialized')
    if (!initialized) {
      // 首次布局完成后按实际表头宽高修正容器尺寸，避免预览区出现多余空白。
      s2.store.set('initialized', true)
      const colsHierarchy = e?.colsHierarchy
      s2.changeSheetSize(colsHierarchy?.width || containerDom.getBoundingClientRect().width)
      const length = s2.dataCfg.data?.length || 0
      const headerHeight = colsHierarchy?.height || s2.options.style?.colCfg?.height || 36
      const rowHeight = s2.options.style?.cellCfg?.height || 32
      const totalHeight = headerHeight + rowHeight * length
      if (containerDom.offsetHeight > totalHeight) {
        containerDom.style.height = totalHeight + 'px'
      }
      s2.render(false)
    }
  })
  s2.render()
}

const getNonLeafNodes = (tree: Array<ColumnNode>): string[] => {
  const result: string[] = []

  // 深度遍历表头树并收集所有非叶子节点，用于清理分组 meta。
  const inorderTraversal = (node: ColumnNode) => {
    // 存在子节点的表头即为分组节点。
    if (node.children?.length > 0) {
      result.push(node.key)

      // 递归向下清理多层分组节点。
      for (let i = 0; i < node.children.length; i++) {
        inorderTraversal(node.children[i] as ColumnNode)
      }
    }
  }

  // 支持一次传入多棵根节点子树。
  tree.forEach(node => inorderTraversal(node))

  return result
}

const getTreesMaxDepth = (nodes: Array<ColumnNode>): number => {
  if (!nodes?.length) {
    return 0
  }

  // 获取单个节点的最大子树深度，叶子节点深度记为 0。
  const getNodeMaxDepth = (node: ColumnNode): number => {
    if (!node.children || node.children.length === 0) {
      return 0
    }
    const childrenDepths = node.children.map(child => getNodeMaxDepth(child as ColumnNode))
    return Math.max(...childrenDepths) + 1
  }

  // 多列选区可能包含多棵子树，需要取最大深度。
  const rootDepths = nodes.map(node => getNodeMaxDepth(node))
  return Math.max(...rootDepths)
}

const resize = debounce(height => {
  if (s2) {
    // 外层弹窗高度变化时只调整表格高度，宽度由 S2 首次布局结果控制。
    const tableHeight = s2.container.cfg.height
    if (height > tableHeight) {
      const dom = document.getElementById(containerId.value)
      dom.style.height = tableHeight + 'px'
    }
    s2.changeSheetSize(undefined, height)
    s2.render(false)
  }
}, 500)
const preSize = [0, 0]
const TOLERANCE = 1
let resizeObserver: ResizeObserver
onMounted(() => {
  init()
  resizeObserver = new ResizeObserver(([entry] = []) => {
    const [size] = entry.borderBoxSize || []
    // 拖动容器时仅在高度变化超过容差后重绘，降低 ResizeObserver 触发频率。
    if (!(preSize[0] || preSize[1])) {
      preSize[0] = size.inlineSize
      preSize[1] = size.blockSize
    }
    const heightOffset = Math.abs(size.blockSize - preSize[1])
    if (heightOffset < TOLERANCE) {
      return
    }
    preSize[0] = size.inlineSize
    preSize[1] = size.blockSize
    resize(Math.round(size.blockSize))
  })
  resizeObserver.observe(document.getElementById(containerId.value))
})
onUnmounted(() => {
  // 弹窗销毁时释放 ResizeObserver，避免重新打开后重复监听同一容器。
  resizeObserver?.disconnect()
})
class GroupMenu extends BaseTooltip {
  show<T = string | Element>(showOptions: TooltipShowOptions<T>): void {
    super.show(showOptions)
    this.container.style.display = 'flex'
  }
  hide(): void {
    if (this.container) {
      this.container.style.display = 'none'
    }
  }
}
</script>

<style scoped lang="less">
.table-container {
  position: relative;
  width: 100%;
  height: 40vh;
  overflow-x: auto;
  overflow-y: hidden;
  &.dark {
    scrollbar-color: #3a3a3a #1a1a1a;
  }
}

.group-menu {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: space-between;
  color: black;
  font-size: 14px;
  :deep(span) {
    cursor: pointer;
    padding: 5px 10px;
    word-break: keep-all;
    &:hover {
      background-color: var(--ed-fill-color-light);
    }
  }
}
.button-group {
  display: flex;
  justify-content: end;
  margin-top: 4vh;
}
</style>
