import { useI18n } from '@/hooks/web/useI18n'
import { formatterItem, valueFormatter } from '@/views/chart/components/js/formatter'
import {
  bindTableCopyEvents,
  configAuxiliaryHeaderLayout,
  configEmptyDataStyle,
  configTableCopyInteraction,
  CustomDataCell,
  getSummaryRow,
  SortTooltip,
  SummaryCell,
  getLeafNodes,
  getColumns,
  summaryRowStyle,
  calcTreeWidth,
  getStartPosition,
  isNumeric,
  CustomTableColCell
} from '@/views/chart/components/js/panel/common/common_table'
import { buildS2HeaderColumns } from '@/views/chart/components/js/panel/common/tableAuxiliaryHeader.mjs'
import { S2ChartView, S2DrawOptions } from '@/views/chart/components/js/panel/types/impl/s2'
import { parseJson } from '@/views/chart/components/js/util'
import {
  type LayoutResult,
  S2DataConfig,
  S2Event,
  S2Options,
  S2Theme,
  ScrollbarPositionType,
  TableColCell,
  TableSeriesNumberCell,
  TableSheet,
  ViewMeta
} from '@antv/s2'
import { cloneDeep, isEqual, merge } from 'lodash-es'
import { TABLE_EDITOR_PROPERTY, TABLE_EDITOR_PROPERTY_INNER } from './common'
import {
  configureTableSeriesNumber,
  getPageSeriesNumber,
  isTableSeriesNumberCell,
  isTableSeriesNumberNode
} from '@/views/chart/components/js/panel/common/tableSeriesNumber.mjs'

const { t } = useI18n()
/**
 * 汇总表
 */
export class TableNormal extends S2ChartView<TableSheet> {
  properties = TABLE_EDITOR_PROPERTY
  propertyInner: EditorPropertyInner = {
    ...TABLE_EDITOR_PROPERTY_INNER,
    'table-header-selector': [
      ...TABLE_EDITOR_PROPERTY_INNER['table-header-selector'],
      'tableHeaderSort',
      'showTableHeader',
      'headerGroup',
      'auxiliaryHeader'
    ],
    'basic-style-selector': [
      ...TABLE_EDITOR_PROPERTY_INNER['basic-style-selector'],
      'tablePageMode',
      'showHoverStyle'
    ],
    'table-cell-selector': [
      ...TABLE_EDITOR_PROPERTY_INNER['table-cell-selector'],
      'tableFreeze',
      'tableColumnFreezeHead',
      'tableRowFreezeHead'
    ],
    'summary-selector': ['showSummary', 'summaryLabel']
  }
  axis: AxisType[] = ['xAxis', 'yAxis', 'drill', 'filter']
  axisConfig: AxisConfig = {
    xAxis: {
      name: `${t('chart.drag_block_table_data_column')} / ${t('chart.dimension')}`,
      type: 'd'
    },
    yAxis: {
      name: `${t('chart.drag_block_table_data_column')} / ${t('chart.quota')}`,
      type: 'q'
    }
  }

  setupDefaultOptions(chart: ChartObj): ChartObj {
    chart.xAxis = []
    const customAttr = parseJson(chart.customAttr)
    if (customAttr.basicStyle.tableColumnMode === 'colAdapt') {
      customAttr.basicStyle.tableColumnMode = 'adapt'
    }
    return chart
  }

  drawChart(drawOption: S2DrawOptions<TableSheet>): TableSheet {
    const { container, chart, action, pageInfo, resizeAction } = drawOption
    const containerDom = document.getElementById(container)
    if (!containerDom) return

    // fields
    let fields = chart.data.fields

    const columns = []
    const meta = []
    const drillFieldMap = {}
    if (chart.drill) {
      // 下钻过滤字段
      const filterFields = chart.drillFilters.map(i => i.fieldId)
      // 下钻入口的字段下标
      const drillFieldId = chart.drillFields[0].id
      const drillFieldIndex = chart.xAxis.findIndex(ele => ele.id === drillFieldId)
      // 当前下钻字段
      const curDrillFieldId = chart.drillFields[filterFields.length].id
      const curDrillField = fields.find(ele => ele.id === curDrillFieldId)
      filterFields.push(curDrillFieldId)
      // 移除下钻字段，把当前下钻字段插入到下钻入口位置
      fields = fields.filter(ele => {
        return !filterFields.includes(ele.id)
      })
      drillFieldMap[curDrillField.engineFieldName] = chart.drillFields[0].engineFieldName
      fields.splice(drillFieldIndex, 0, curDrillField)
    }
    const axisMap = [...chart.xAxis, ...chart.yAxis].reduce((pre, cur) => {
      pre[cur.engineFieldName] = cur
      return pre
    }, {})
    // add drill list
    fields.forEach(ele => {
      const f = axisMap[ele.engineFieldName]
      if (f?.hide === true) {
        return
      }
      columns.push(ele.engineFieldName)
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
          if (![2, 3, 4].includes(f.fieldType) || !isNumeric(value)) {
            return value
          }
          let formatCfg = f.formatterCfg
          if (!formatCfg) {
            formatCfg = formatterItem
          }
          return valueFormatter(value, formatCfg)
        },
        id: ele.id
      })
    })
    const { basicStyle, tableCell, tableHeader, tooltip } = parseJson(chart.customAttr)
    // 表头分组
    const { headerGroup, showTableHeader } = tableHeader
    let headerGroupMeta = []
    if (headerGroup && showTableHeader !== false) {
      const { headerGroupConfig } = tableHeader
      if (headerGroupConfig?.columns?.length) {
        const headerGroupColumns = cloneDeep(headerGroupConfig.columns)
        const allKeys = columns.map(c => drillFieldMap[c] || c)
        const leafNodes = getLeafNodes(headerGroupColumns as ColumnNode[])
        const leafKeys = leafNodes.map(c => c.key)
        if (isEqual(leafKeys, allKeys)) {
          if (Object.keys(drillFieldMap).length) {
            const originField = Object.values(drillFieldMap)[0]
            const drillField = Object.keys(drillFieldMap)[0]
            const [drillCol] = getColumns([originField], headerGroupColumns as ColumnNode[])
            drillCol.key = drillField
          }
          columns.splice(0, columns.length, ...headerGroupColumns)
          headerGroupMeta = headerGroupConfig.meta || []
          meta.push(...headerGroupMeta)
        }
      }
    }
    const fieldNameMap = meta.reduce((pre, cur) => {
      pre[cur.field] = cur.name
      return pre
    }, {})
    columns.splice(
      0,
      columns.length,
      ...buildS2HeaderColumns(columns, headerGroupMeta, fieldNameMap, tableHeader.auxiliaryHeader)
    )
    // 空值处理
    const newData = this.configEmptyDataStrategy(chart)
    // data config
    const s2DataConfig: S2DataConfig = {
      fields: {
        columns: columns
      },
      meta: meta,
      data: newData
    }

    // options
    const s2Options: S2Options = {
      width: containerDom.getBoundingClientRect().width,
      height: containerDom.offsetHeight,
      conditions: this.configConditions(chart),
      tooltip: {
        getContainer: () => containerDom,
        ...({ renderTooltip: sheet => new SortTooltip(sheet) } as Record<string, any>)
      },
      interaction: {
        hoverHighlight: !(basicStyle.showHoverStyle === false),
        scrollbarPosition: newData.length
          ? ScrollbarPositionType.CONTENT
          : ScrollbarPositionType.CANVAS,
        hoverFocus: false
      }
    }
    configureTableSeriesNumber(s2Options, tableHeader)
    configTableCopyInteraction(s2Options, {
      visibleTable: true,
      showSeriesNumber: tableHeader.showIndex,
      indexLabel: tableHeader.indexLabel,
      showTableHeader: tableHeader.showTableHeader,
      pageInfo,
      headerColumns: cloneDeep(columns)
    })
    // 列宽设置
    s2Options.style = this.configStyle(chart, s2DataConfig)
    // 行列冻结
    if (tableCell.tableFreeze) {
      s2Options.frozenColCount = tableCell.tableColumnFreezeHead ?? 0
      s2Options.frozenRowCount = tableCell.tableRowFreezeHead ?? 0
    }
    // tooltip
    this.configTooltip(chart, s2Options)
    // 隐藏表头，保留顶部的分割线, 禁用表头横向 resize
    if (tableHeader.showTableHeader === false) {
      s2Options.style.colCfg.height = 1
      if (tableCell.showHorizonBorder === false) {
        s2Options.style.colCfg.height = 0
      }
      s2Options.interaction.resize = {
        colCellVertical: false
      }
      s2Options.colCell = (node, sheet, config) => {
        node.label = ' '
        return new TableColCell(node, sheet, config)
      }
    } else {
      // header interaction
      chart.container = container
      this.configHeaderInteraction(chart, s2Options)
      s2Options.colCell = (node, sheet, config) => {
        return new CustomTableColCell(node, sheet, config)
      }
    }
    // 配置总计和序号列
    this.configSummaryRowAndIndex(chart, pageInfo, s2Options, s2DataConfig)
    // 开始渲染
    const newChart = new TableSheet(containerDom, s2DataConfig, s2Options)
    // 总计紧贴在单元格后面
    summaryRowStyle(newChart, newData, tableCell, tableHeader, basicStyle.showSummary)
    // 自适应铺满
    if (basicStyle.tableColumnMode === 'adapt') {
      newChart.on(S2Event.LAYOUT_RESIZE_COL_WIDTH, () => {
        newChart.store.set('lastLayoutResult', (newChart.facet as Record<string, any>).layoutResult)
      })
      newChart.on(S2Event.LAYOUT_AFTER_HEADER_LAYOUT, (ev: LayoutResult) => {
        const lastLayoutResult = newChart.store.get('lastLayoutResult') as LayoutResult
        if (lastLayoutResult) {
          // 拖动表头 resize
          const widthByFieldValue = newChart.options.style?.colCfg?.widthByFieldValue
          const lastLayoutWidthMap: Record<string, number> =
            lastLayoutResult?.colLeafNodes.reduce((p, n) => {
              p[n.value] = widthByFieldValue?.[n.value] ?? n.width
              return p
            }, {}) || {}
          const totalWidth = ev.colLeafNodes.reduce((p, n) => {
            n.width = lastLayoutWidthMap[n.value] || n.width
            n.x = p
            return p + n.width
          }, 0)
          // 处理分组的单元格，宽度为所有叶子节点之和
          ev.colNodes.forEach(n => {
            if (n.colIndex === -1) {
              n.width = calcTreeWidth(n)
              n.x = getStartPosition(n)
            }
          })
          ev.colsHierarchy.width = totalWidth
          newChart.store.set('lastLayoutResult', undefined)
          return
        }
        const containerWidth = containerDom.getBoundingClientRect().width
        const seriesNumberWidth = ev.colLeafNodes.reduce((p, n) => {
          return p + (isTableSeriesNumberNode(n) ? Math.round(n.width) : 0)
        }, 0)
        const dataWidth = ev.colsHierarchy.width - seriesNumberWidth
        if (dataWidth <= 0) {
          ev.colLeafNodes.reduce((p, n) => {
            n.width = Math.round(n.width)
            n.x = p
            return p + n.width
          }, 0)
          return
        }
        const scale = (containerWidth - seriesNumberWidth) / dataWidth
        if (scale <= 1) {
          // 图库计算的布局宽度已经大于等于容器宽度，不需要再扩大，但是需要处理非整数宽度值，不然会出现透明细线
          ev.colLeafNodes.reduce((p, n) => {
            n.width = Math.round(n.width)
            n.x = p
            return p + n.width
          }, 0)
          return
        }
        const totalWidth = ev.colLeafNodes.reduce((p, n) => {
          n.width = isTableSeriesNumberNode(n) ? Math.round(n.width) : Math.round(n.width * scale)
          n.x = p
          return p + n.width
        }, 0)
        // 处理分组的单元格，宽度为所有叶子节点之和
        ev.colNodes.forEach(n => {
          if (n.colIndex === -1) {
            n.width = calcTreeWidth(n)
            n.x = getStartPosition(n)
          }
        })
        if (totalWidth > containerWidth) {
          // 从最后一列减掉
          ev.colLeafNodes[ev.colLeafNodes.length - 1].width -= totalWidth - containerWidth
        }
        ev.colsHierarchy.width = containerWidth
      })
    }
    configAuxiliaryHeaderLayout(newChart, tableHeader)
    configEmptyDataStyle(newChart, basicStyle, newData, container)
    // click
    newChart.on(S2Event.DATA_CELL_CLICK, ev => {
      const cell = newChart.getCell(ev.target)
      const meta = cell.getMeta() as ViewMeta
      const nameIdMap = fields.reduce((pre, next) => {
        pre[next['engineFieldName']] = next['id']
        return pre
      }, {})

      const rowData = newChart.dataSet.getRowData(meta)
      const dimensionList = []
      for (const key in rowData) {
        if (nameIdMap[key]) {
          dimensionList.push({ id: nameIdMap[key], value: rowData[key] })
        }
      }
      const param = {
        x: ev.x,
        y: ev.y,
        data: {
          dimensionList,
          name: nameIdMap[meta.valueField],
          sourceType: 'table-normal',
          quotaList: []
        }
      }
      action(param)
    })
    // tooltip
    const { show } = tooltip
    if (show) {
      newChart.on(S2Event.COL_CELL_HOVER, event => this.showTooltip(newChart, event, meta))
      newChart.on(S2Event.DATA_CELL_HOVER, event => this.showTooltip(newChart, event, meta))
      // touch
      this.configTouchEvent(newChart, drawOption, meta)
    }
    // header resize
    newChart.on(S2Event.LAYOUT_RESIZE_COL_WIDTH, ev => resizeAction(ev))
    bindTableCopyEvents(newChart, meta)
    // theme
    const customTheme = this.configTheme(chart)
    newChart.setThemeCfg({ theme: customTheme })

    return newChart
  }

  protected configTheme(chart: Chart): S2Theme {
    const theme = super.configTheme(chart)
    const { tableHeader, tableCell } = parseJson(chart.customAttr)
    if (tableCell.tableItemAlign === 'custom') {
      const { alignConfig } = tableCell
      const alignMap = alignConfig.reduce((p, n) => {
        p[n.id] = n.align
        return p
      }, {})
      merge(theme, {
        dataCellAlignConfig: alignMap
      })
    }
    if (tableHeader.tableHeaderAlign === 'custom') {
      const { alignConfig } = tableHeader
      const alignMap = alignConfig.reduce((p, n) => {
        p[n.id] = n.align
        return p
      }, {})
      merge(theme, {
        colCellAlignConfig: alignMap
      })
    }
    return theme
  }

  protected configSummaryRowAndIndex(
    chart: Chart,
    pageInfo: PageInfo,
    s2Options: S2Options,
    s2DataConfig: S2DataConfig
  ) {
    const { tableHeader, basicStyle } = parseJson(chart.customAttr)
    const { showSummary, summaryLabel } = basicStyle
    const data = s2DataConfig.data
    const { xAxis, yAxis } = chart
    if (showSummary && data?.length) {
      // 设置汇总行高度和表头一致
      const heightByField = {}
      heightByField[data.length] = tableHeader.tableTitleHeight
      s2Options.style.rowCfg = { heightByField }
      // 计算汇总加入到数据里，冻结最后一行
      s2Options.frozenTrailingRowCount = 1
      const summaryObj = getSummaryRow(
        data,
        yAxis,
        basicStyle.seriesSummary,
        chart.data.customSumResult
      ) as any
      data.push(summaryObj)
    }
    s2Options.dataCell = (viewMeta, spreadsheet) => {
      const sheet = spreadsheet || viewMeta?.spreadsheet
      const isSeriesNumberCell = isTableSeriesNumberCell(viewMeta)
      // 总计行处理
      if (showSummary && viewMeta.rowIndex === data.length - 1) {
        if (
          isSeriesNumberCell ||
          (!tableHeader.showIndex && viewMeta.colIndex === 0 && xAxis?.length)
        ) {
          viewMeta.fieldValue = summaryLabel ?? t('chart.total_show')
          viewMeta.isSummaryLabel = true
        }
        return new SummaryCell(viewMeta, sheet)
      }
      if (isSeriesNumberCell) {
        viewMeta.fieldValue = getPageSeriesNumber(pageInfo, viewMeta.rowIndex)
        return new TableSeriesNumberCell(viewMeta, sheet)
      }
      return new CustomDataCell(viewMeta, sheet)
    }
  }

  constructor() {
    super('table-normal', [])
  }
}
