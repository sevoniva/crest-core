import type { Column, ColumnOptions } from '@antv/g2plot/esm/plots/column'
import { cloneDeep, defaults, each, groupBy, isEmpty, merge } from 'lodash-es'
import {
  G2PlotChartView,
  G2PlotDrawOptions
} from '@/views/chart/components/js/panel/types/impl/g2plot'
import {
  convertToAlphaColor,
  flow,
  hexColorToRGBA,
  isAlphaColor,
  parseJson,
  setUpGroupSeriesColor,
  setUpStackSeriesColor
} from '@/views/chart/components/js/util'
import type { Datum } from '@antv/g2plot'
import { formatterItem, valueFormatter } from '@/views/chart/components/js/formatter'
import {
  BAR_AXIS_TYPE,
  BAR_EDITOR_PROPERTY,
  BAR_EDITOR_PROPERTY_INNER
} from '@/views/chart/components/js/panel/charts/bar/common'
import {
  configPlotTooltipEvent,
  configRoundAngle,
  configXAxisLengthLimit,
  getLabel,
  getPadding,
  getTooltipContainer,
  setGradientColor,
  TOOLTIP_TPL
} from '@/views/chart/components/js/panel/common/common_antv'
import { useI18n } from '@/hooks/web/useI18n'
import {
  DEFAULT_BASIC_STYLE,
  DEFAULT_LABEL,
  DEFAULT_LEGEND_STYLE
} from '@/views/chart/components/editor/util/chart'
import { clearExtremum, extremumEvt } from '@/views/chart/components/js/extremumUitl'
import { Group } from '@antv/g-canvas'
import { getItemsOfView } from '@antv/g2/lib/interaction/action/active-region'

const { t } = useI18n()
const DEFAULT_DATA: any[] = []
/**
 * 柱状图渲染器，负责把编辑器中的轴、样式、标签和交互配置转换为 G2Plot Column 配置。
 */
export class Bar extends G2PlotChartView<ColumnOptions, Column> {
  // 编辑面板的一级能力列表，和 propertyInner 一起决定右侧配置面板可见项。
  properties = BAR_EDITOR_PROPERTY
  // 柱状图只开放本图表族支持的配置项，避免通用面板暴露无效字段。
  propertyInner = {
    ...BAR_EDITOR_PROPERTY_INNER,
    'x-axis-selector': [...BAR_EDITOR_PROPERTY_INNER['x-axis-selector'], 'showLengthLimit'],
    'basic-style-selector': [...BAR_EDITOR_PROPERTY_INNER['basic-style-selector'], 'seriesColor'],
    'label-selector': ['vPosition', 'seriesLabelFormatter', 'showExtremum'],
    'tooltip-selector': [
      'fontSize',
      'color',
      'backgroundColor',
      'seriesTooltipFormatter',
      'show',
      'carousel'
    ],
    'y-axis-selector': [...BAR_EDITOR_PROPERTY_INNER['y-axis-selector'], 'axisLabelFormatter']
  }
  // Column 的基础字段映射固定为 field/value/category，后续配置函数只追加行为和样式。
  protected baseOptions: ColumnOptions = {
    xField: 'field',
    yField: 'value',
    seriesField: 'category',
    isGroup: true,
    data: []
  }

  // 轴槽位定义同时驱动拖拽区域和后端查询字段分组。
  axis: AxisType[] = [...BAR_AXIS_TYPE]
  // 轴配置中的文案和类型限制会展示在字段拖拽面板。
  axisConfig = {
    ...this['axisConfig'],
    xAxis: {
      name: `${t('chart.drag_block_type_axis')} / ${t('chart.dimension')}`,
      type: 'd'
    },
    yAxis: {
      name: `${t('chart.drag_block_value_axis')} / ${t('chart.quota')}`,
      type: 'q'
    }
  }

  // 绘制流程先构造 G2Plot 实例，再挂接点击、极值和 tooltip 相关事件。
  async drawChart(drawOptions: G2PlotDrawOptions<Column>): Promise<Column> {
    const { chart, container, action } = drawOptions
    chart.container = container
    if (!chart?.data?.data?.length) {
      clearExtremum(chart)
      return
    }
    const isGroup = 'bar-group' === this.name && chart.xAxisExt?.length > 0
    const isStack =
      ['bar-stack', 'bar-group-stack'].includes(this.name) && chart.extStack?.length > 0
    const data = cloneDeep(drawOptions.chart.data?.data)
    const initOptions: ColumnOptions = {
      ...this.baseOptions,
      appendPadding: getPadding(chart),
      data
    }
    const options: ColumnOptions = this.setupOptions(chart, initOptions)
    let newChart = null
    const { Column: ColumnClass } = await import('@antv/g2plot/esm/plots/column')
    newChart = new ColumnClass(container, options)
    newChart.on('interval:click', action)
    // 只处理普通柱状图的背景点击，分组和堆叠阴影区域无法稳定反推出子维度。
    if (this.name === 'bar' && options.tooltip) {
      newChart.on('plot:click', e => {
        if (e.target?.cfg?.renderer !== 'canvas') {
          return
        }
        const activeRegion = e.view.backgroundGroup.cfg.children.find(
          i => i.cfg.name === 'active-region'
        )
        if (activeRegion?.cfg.visible) {
          const items = getItemsOfView(
            e.view,
            { x: e.x, y: e.y },
            e.view.getController('tooltip').getTooltipCfg()
          )
          if (items?.length) {
            const datum = items[0].data
            if (datum && datum.field) {
              action({
                x: e.x,
                y: e.y,
                data: {
                  data: datum
                }
              })
            }
          }
        }
      })
    }
    extremumEvt(newChart, chart, options, container)
    configPlotTooltipEvent(chart, newChart)
    configXAxisLengthLimit(chart, newChart)
    return newChart
  }

  // 标签配置支持按指标系列分别设置格式；极值辅助点不参与普通标签展示。
  protected configLabel(chart: Chart, options: ColumnOptions): ColumnOptions {
    const tmpOptions = super.configLabel(chart, options)
    if (!tmpOptions.label) {
      return {
        ...tmpOptions,
        label: false
      }
    }
    const { label: labelAttr } = parseJson(chart.customAttr)
    const formatterMap = labelAttr.seriesLabelFormatter?.reduce((pre, next) => {
      pre[next.id] = next
      return pre
    }, {})
    // 默认是灰色
    tmpOptions.label.style.fill = DEFAULT_LABEL.color
    const label = {
      fields: [],
      ...tmpOptions.label,
      formatter: (data: Datum) => {
        if (data.EXTREME) {
          return ''
        }
        if (!labelAttr.seriesLabelFormatter?.length) {
          return data.value
        }
        const labelCfg = formatterMap?.[data.quotaList[0].id] as SeriesFormatter
        if (!labelCfg) {
          return data.value
        }
        if (!labelCfg.show) {
          return
        }
        const value = valueFormatter(data.value, labelCfg.formatterCfg)
        const group = new Group({})
        group.addShape({
          type: 'text',
          attrs: {
            x: 0,
            y: 0,
            text: value,
            textAlign: 'start',
            textBaseline: 'top',
            fontSize: labelCfg.fontSize,
            fontFamily: chart.fontFamily,
            fill: labelCfg.color
          }
        })
        return group
      },
      position: data => {
        const labelPosition = (tmpOptions.label as any)?.position
        if (data.value < 0) {
          if (labelPosition === 'top') {
            return 'bottom'
          }
          if (labelPosition === 'bottom') {
            return 'top'
          }
        }
        return labelPosition
      }
    }
    return {
      ...tmpOptions,
      label
    }
  }

  // 多系列 tooltip 复用父类统一逻辑，保证指标格式化和轮播提示行为一致。
  protected configTooltip(chart: Chart, options: ColumnOptions): ColumnOptions {
    return super.configMultiSeriesTooltip(chart, options)
  }

  // 基础样式负责渐变、圆角和柱宽，输入值会被限制在 G2Plot 可接受范围内。
  protected configBasicStyle(chart: Chart, options: ColumnOptions): ColumnOptions {
    const basicStyle = parseJson(chart.customAttr).basicStyle
    if (basicStyle.gradient) {
      let color = basicStyle.colors
      color = color.map(ele => {
        const tmp = hexColorToRGBA(ele, basicStyle.alpha)
        return setGradientColor(tmp, true, 270)
      })
      options = {
        ...options,
        color
      }
    }
    options = {
      ...options,
      ...configRoundAngle(chart, 'columnStyle')
    }
    let columnWidthRatio
    const _v = basicStyle.columnWidthRatio ?? DEFAULT_BASIC_STYLE.columnWidthRatio
    // 配置面板按百分比保存柱宽，渲染层需要转换为 0 到 1 的比例。
    if (_v >= 1 && _v <= 100) {
      columnWidthRatio = _v / 100.0
    } else if (_v < 1) {
      columnWidthRatio = 1 / 100.0
    } else if (_v > 100) {
      columnWidthRatio = 1
    }
    if (columnWidthRatio) {
      ;(options as any).columnWidthRatio = columnWidthRatio
    }

    return options
  }

  // X 轴标签长度限制只在轴标签存在时追加，避免覆盖上游图表类型关闭轴标签的配置。
  protected configXAxis(chart: Chart, options: ColumnOptions): ColumnOptions {
    const tmpOptions = super.configXAxis(chart, options)
    if (!tmpOptions.xAxis) {
      return tmpOptions
    }
    const xAxis = parseJson(chart.customStyle).xAxis
    if (tmpOptions.xAxis.label) {
      const { lengthLimit } = xAxis.axisLabel
      defaults(tmpOptions.xAxis.label, {
        formatter: value => {
          return value?.length > lengthLimit ? value.substring(0, lengthLimit) + '...' : value
        }
      })
    }
    return tmpOptions
  }

  // Y 轴格式化和固定范围在同一层处理；固定范围会过滤低于最小值的数据点。
  protected configYAxis(chart: Chart, options: ColumnOptions): ColumnOptions {
    const tmpOptions = super.configYAxis(chart, options)
    if (!tmpOptions.yAxis) {
      return tmpOptions
    }
    const yAxis = parseJson(chart.customStyle).yAxis
    const axisValue = yAxis.axisValue
    if (tmpOptions.yAxis.label) {
      tmpOptions.yAxis.label.formatter = value => {
        return valueFormatter(value, yAxis.axisLabelFormatter)
      }
    }
    if (!axisValue?.auto) {
      const axis = {
        yAxis: {
          ...tmpOptions.yAxis,
          min: axisValue.min,
          max: axisValue.max,
          minLimit: axisValue.min,
          maxLimit: axisValue.max,
          tickCount: axisValue.splitCount
        }
      }
      // G2Plot 固定最小值不会自动隐藏越界柱体，这里同步裁剪数据，避免图形越过坐标轴边界。
      const { data } = options
      const newData = data.filter(item => item.value >= axisValue.min)
      return { ...tmpOptions, data: newData, ...axis }
    }
    return tmpOptions
  }

  // 配置管线按数据、主题、样式、交互的顺序执行，后续步骤可以覆盖前一步的默认值。
  protected setupOptions(chart: Chart, options: ColumnOptions): ColumnOptions {
    return flow(
      this.addConditionsStyleColorToData,
      this.configTheme,
      this.configEmptyDataStrategy,
      this.configColor,
      this.configBasicStyle,
      this.configLabel,
      this.configTooltip,
      this.configLegend,
      this.configXAxis,
      this.configYAxis,
      this.configSlider,
      this.configAnalyse,
      this.configBarConditions
    )(chart, options, {}, this)
  }

  // 柱状图默认忽略空值，避免断点策略在离散类目上生成误导性的零值柱。
  setupDefaultOptions(chart: ChartObj): ChartObj {
    chart.senior.functionCfg.emptyDataStrategy = 'ignoreData'
    return chart
  }

  constructor(name = 'bar', defaultData = DEFAULT_DATA) {
    super(name, defaultData)
  }
}

/**
 * 堆叠柱状图在普通柱状图基础上增加子维度堆叠、汇总标签和排序图例。
 */
export class StackBar extends Bar {
  properties: EditorProperty[] = BAR_EDITOR_PROPERTY.filter(ele => ele !== 'threshold')
  propertyInner = {
    ...this['propertyInner'],
    'label-selector': [
      ...BAR_EDITOR_PROPERTY_INNER['label-selector'],
      'vPosition',
      'showTotal',
      'totalColor',
      'totalFontSize',
      'totalFormatter',
      'showStackQuota'
    ],
    'tooltip-selector': [
      'fontSize',
      'color',
      'backgroundColor',
      'tooltipFormatter',
      'show',
      'carousel'
    ],
    'legend-selector': [...BAR_EDITOR_PROPERTY_INNER['legend-selector'], 'legendSort']
  }
  // 堆叠标签支持显示每段数值，也支持在柱顶追加分组汇总。
  protected configLabel(chart: Chart, options: ColumnOptions): ColumnOptions {
    let label = getLabel(chart)
    if (!label) {
      return options
    }
    options = { ...options, label }
    const { label: labelAttr } = parseJson(chart.customAttr)
    if (labelAttr.showStackQuota || labelAttr.showStackQuota === undefined) {
      label.style.fill = labelAttr.color
      label = {
        ...label,
        formatter: function (param: Datum) {
          return valueFormatter(param.value, labelAttr.labelFormatter)
        }
      }
    } else {
      label = false
    }
    if (labelAttr.showTotal) {
      const formatterCfg = labelAttr.labelFormatter ?? formatterItem
      // 按主维度汇总堆叠段数值，使用注解实现柱顶总计，避免修改原始数据结构。
      each(groupBy(options.data, 'field'), (values, key) => {
        const total = values.reduce((a, b) => a + b.value, 0)
        const value = valueFormatter(total, formatterCfg)
        if (!options.annotations) {
          options = {
            ...options,
            annotations: []
          }
        }
        options.annotations.push({
          type: 'text',
          position: [key, total],
          content: `${value}`,
          style: {
            textAlign: 'center',
            fontSize: labelAttr.fontSize,
            fill: labelAttr.color
          },
          offsetY: -(parseInt(labelAttr.fontSize as unknown as string) / 2)
        })
      })
    }
    return {
      ...options,
      label
    }
  }

  // 堆叠 tooltip 使用子维度名称作为展示名，空子维度时回退到主维度名称。
  protected configTooltip(chart: Chart, options: ColumnOptions): ColumnOptions {
    const tooltipAttr = parseJson(chart.customAttr).tooltip
    if (!tooltipAttr.show) {
      return {
        ...options,
        tooltip: false
      }
    }
    const tooltip = {
      formatter: (param: Datum) => {
        const name = isEmpty(param.category) ? param.field : param.category
        const obj = { name, value: param.value }
        const res = valueFormatter(param.value, tooltipAttr.tooltipFormatter)
        obj.value = res ?? ''
        return obj
      },
      container: getTooltipContainer(`tooltip-${chart.id}`, chart.container),
      itemTpl: TOOLTIP_TPL,
      enterable: true
    }
    return {
      ...options,
      tooltip
    }
  }

  // 图例和堆叠顺序需要同步，否则用户自定义排序后颜色、图例和柱体顺序会不一致。
  protected configColor(chart: Chart, options: ColumnOptions): ColumnOptions {
    const customStyle = parseJson(chart.customStyle)
    const { sort } = customStyle.legend
    const extStack = chart.extStack[0]
    if ((!sort || sort === 'none') && extStack?.customSort?.length > 0) {
      // 图例未单独排序时，优先使用子维度自定义顺序作为堆叠顺序。
      const sort = extStack.customSort ?? []
      if (sort?.length) {
        // 新数据可能不在历史自定义顺序中，需要追加到末尾，避免数据被隐藏。
        const data = options.data
        const cats: any[] =
          (data as any[])?.reduce((p, n) => {
            const cat = n['category']
            if (cat && !p.includes(cat)) {
              p.push(cat)
            }
            return p
          }, [] as any[]) || []
        const values: any[] = sort.reduce((p, n) => {
          if (cats.includes(n)) {
            const index = cats.indexOf(n)
            if (index !== -1) {
              cats.splice(index, 1)
            }
            p.push(n)
          }
          return p
        }, [] as any[])
        cats.length > 0 && values.push(...cats)
        ;(options as any).meta = {
          ...options.meta,
          category: {
            type: 'cat',
            values
          }
        }
      }
    }
    return this.configStackColor(chart, options)
  }

  // 未设置主维度或子维度排序时，允许按指标汇总值调整主维度展示顺序。
  protected configData(chart: Chart, options: ColumnOptions): ColumnOptions {
    const { xAxis, extStack, yAxis } = chart
    const mainSort = xAxis.some(axis => axis.sort !== 'none')
    const subSort = extStack.some(axis => axis.sort !== 'none')
    if (mainSort || subSort) {
      return options
    }
    const quotaSort = yAxis?.[0].sort !== 'none'
    if (!quotaSort || !extStack.length || !yAxis.length) {
      return options
    }
    const { data } = options
    const mainAxisValueMap = data.reduce((p, n) => {
      p[n.field] = p[n.field] ? p[n.field] + n.value : n.value || 0
      return p
    }, {})
    const sort = yAxis[0].sort
    data.sort((p, n) => {
      if (sort === 'asc') {
        return mainAxisValueMap[p.field] - mainAxisValueMap[n.field]
      } else {
        return mainAxisValueMap[n.field] - mainAxisValueMap[p.field]
      }
    })
    return options
  }

  // 排序图例需要显式生成 items，才能同时控制顺序、颜色、透明度和图标形状。
  protected configSortedLegend(chart: Chart, options: ColumnOptions): ColumnOptions {
    const optionTmp = super.configLegend(chart, options)
    if (!optionTmp.legend) {
      return optionTmp
    }
    const extStack = chart.extStack[0]
    const customStyle = parseJson(chart.customStyle)
    let size
    if (customStyle && customStyle.legend) {
      size = defaults(JSON.parse(JSON.stringify(customStyle.legend)), DEFAULT_LEGEND_STYLE).size
    } else {
      size = DEFAULT_LEGEND_STYLE.size
    }

    ;(optionTmp.legend.marker as any).style = style => {
      return {
        r: size,
        fill: style.fill
      }
    }
    const { sort, customSort, icon } = customStyle.legend
    if (sort && sort !== 'none' && chart.extStack.length) {
      const customAttr = parseJson(chart.customAttr)
      const { basicStyle } = customAttr
      const seriesMap =
        basicStyle.seriesColor?.reduce((p, n) => {
          p[n.id] = n
          return p
        }, {}) || {}
      const dupCheck = new Set()
      const colors = optionTmp.color ?? (optionTmp.theme as any).styleSheet.paletteQualitative10
      const items = optionTmp.data?.reduce((arr, item) => {
        if (!dupCheck.has(item.category)) {
          const fill = seriesMap[item.category]?.color ?? colors[dupCheck.size % colors.length]
          dupCheck.add(item.category)
          arr.push({
            name: item.category,
            value: item.category,
            marker: {
              symbol: icon,
              style: {
                r: size,
                fill: isAlphaColor(fill) ? fill : convertToAlphaColor(fill, basicStyle.alpha)
              }
            }
          })
        }
        return arr
      }, [])
      if (sort !== 'custom') {
        items.sort((a, b) => {
          return sort !== 'desc' ? a.name.localeCompare(b.name) : b.name.localeCompare(a.name)
        })
      } else {
        const tmp = []
        ;(customSort || []).forEach(item => {
          const index = items.findIndex(i => i.name === item)
          if (index !== -1) {
            tmp.push(items[index])
            items.splice(index, 1)
          }
        })
        items.unshift(...tmp)
      }
      ;(optionTmp.legend as any).items = items
      if (extStack?.customSort?.length > 0) {
        delete optionTmp.meta?.category.values
      }
    }
    return optionTmp
  }

  // 堆叠图按子维度生成系列颜色，保证同一子维度在不同主维度下颜色一致。
  public setupSeriesColor(chart: ChartObj, data?: any[]): ChartBasicStyle['seriesColor'] {
    return setUpStackSeriesColor(chart, data)
  }

  // 堆叠图默认显示分段值，保持和历史图表的标签展示行为一致。
  setupDefaultOptions(chart: ChartObj): ChartObj {
    const chartTmp = super.setupDefaultOptions(chart)
    chartTmp.customAttr.label.showStackQuota = true
    return chartTmp
  }

  // 堆叠图先处理排序和配色，再进入通用轴、缩略轴和分析线配置。
  protected setupOptions(chart: Chart, options: ColumnOptions): ColumnOptions {
    return flow(
      this.configTheme,
      this.configEmptyDataStrategy,
      this.configData,
      this.configColor,
      this.configBasicStyle,
      this.configLabel,
      this.configTooltip,
      this.configSortedLegend,
      this.configXAxis,
      this.configYAxis,
      this.configSlider,
      this.configAnalyse
    )(chart, options, {}, this)
  }

  constructor(name = 'bar-stack') {
    super(name)
    this.baseOptions = {
      ...this.baseOptions,
      isStack: true,
      isGroup: false,
      meta: {
        category: {
          type: 'cat'
        }
      }
    }
    this.axis = [...this.axis, 'extStack']
  }
}

/**
 * 分组柱状图增加第二个类目轴，适合在同一主维度下比较多个子维度。
 */
export class GroupBar extends StackBar {
  properties = BAR_EDITOR_PROPERTY
  propertyInner = {
    ...this['propertyInner'],
    'label-selector': [...BAR_EDITOR_PROPERTY_INNER['label-selector'], 'vPosition', 'showExtremum'],
    'legend-selector': BAR_EDITOR_PROPERTY_INNER['legend-selector']
  }
  axisConfig = {
    ...this['axisConfig'],
    yAxis: {
      name: `${t('chart.drag_block_value_axis')} / ${t('chart.quota')}`,
      type: 'q',
      limit: 1
    }
  }

  // 分组图在 G2Plot 映射前调整组内顺序，确保指标排序只影响同一主维度内的柱子。
  async drawChart(drawOptions: G2PlotDrawOptions<Column>): Promise<Column> {
    const plot = await super.drawChart(drawOptions)
    if (!plot) {
      return plot
    }
    const { chart } = drawOptions
    const { xAxis, xAxisExt, yAxis } = chart
    let innerSort = !!(xAxis.length && xAxisExt.length && yAxis.length)
    if (innerSort && yAxis[0].sort === 'none') {
      innerSort = false
    }
    if (innerSort && xAxisExt[0].sort !== 'none') {
      const sortPriority = chart.sortPriority ?? []
      const yAxisIndex = sortPriority?.findIndex(e => e.id === yAxis[0].id)
      const xAxisExtIndex = sortPriority?.findIndex(e => e.id === xAxisExt[0].id)
      if (xAxisExtIndex <= yAxisIndex) {
        innerSort = false
      }
    }
    if (!innerSort) {
      return plot
    }
    plot.chart.once('beforepaint', () => {
      const geo = plot.chart.geometries?.[0] as any
      if (typeof geo?.beforeMapping !== 'function' || typeof geo?.getXScale !== 'function') {
        return
      }
      const originMapping = geo.beforeMapping.bind(geo)
      geo.beforeMapping = originData => {
        const values = geo.getXScale().values
        const valueMap = values.reduce((p, n) => {
          if (!p?.[n]) {
            p[n] = {
              fieldArr: [],
              indexArr: [],
              dataArr: []
            }
          }
          originData.forEach((arr, arrIndex) => {
            arr.forEach((item, index) => {
              if (item._origin.field === n) {
                p[n].fieldArr.push(item.field)
                p[n].indexArr.push([arrIndex, index])
                p[n].dataArr.push(item)
              }
            })
          })
          return p
        }, {})
        values.forEach(v => {
          const item = valueMap[v]
          item.dataArr.sort((a, b) => {
            if (yAxis[0].sort === 'asc') {
              return a.value - b.value
            }
            if (yAxis[0].sort === 'desc') {
              return b.value - a.value
            }
            return 0
          })
          item.indexArr.forEach((index, i) => {
            item.dataArr[i].field = item.fieldArr[i]
            originData[index[0]][index[1]] = item.dataArr[i]
          })
        })
        return originMapping(originData)
      }
    })
    return plot
  }

  // 分组标签遵循 childrenShow 开关，支持只展示极值或完全隐藏子柱标签。
  protected configLabel(chart: Chart, options: ColumnOptions): ColumnOptions {
    const tmpLabel = getLabel(chart)
    if (!tmpLabel) {
      return options
    }
    const baseOptions = { ...options, label: tmpLabel }
    const { label: labelAttr } = parseJson(chart.customAttr)
    baseOptions.label.style.fill = labelAttr.color
    const label = {
      ...baseOptions.label,
      formatter: function (param: Datum) {
        if (param.EXTREME) {
          return ''
        }
        const value = valueFormatter(param.value, labelAttr.labelFormatter)
        return labelAttr.childrenShow ? value : null
      }
    }
    return {
      ...baseOptions,
      label
    }
  }

  // 分组图按子分组生成系列颜色，避免和堆叠图的子维度配色逻辑混用。
  protected configColor(chart: Chart, options: ColumnOptions): ColumnOptions {
    return this.configGroupColor(chart, options)
  }

  // 分组图的系列颜色来源于分组字段，数据缺失时由通用色板兜底。
  public setupSeriesColor(chart: ChartObj, data?: any[]): ChartBasicStyle['seriesColor'] {
    return setUpGroupSeriesColor(chart, data)
  }

  // 分组图保留普通柱状图的条件色和极值能力，再叠加组内排序。
  protected setupOptions(chart: Chart, options: ColumnOptions): ColumnOptions {
    return flow(
      this.addConditionsStyleColorToData,
      this.configTheme,
      this.configEmptyDataStrategy,
      this.configColor,
      this.configBasicStyle,
      this.configLabel,
      this.configTooltip,
      this.configLegend,
      this.configXAxis,
      this.configYAxis,
      this.configSlider,
      this.configAnalyse,
      this.configBarConditions
    )(chart, options, {}, this)
  }

  constructor(name = 'bar-group') {
    super(name)
    this.baseOptions = {
      ...this.baseOptions,
      marginRatio: 0,
      isGroup: true,
      isStack: false,
      meta: {
        category: {
          type: 'cat'
        }
      }
    }
    this.axis = [...BAR_AXIS_TYPE, 'xAxisExt']
  }
}

/**
 * 分组堆叠柱状图同时使用分组字段和堆叠字段，配置缺失时会降级为分组或堆叠展示。
 */
export class GroupStackBar extends StackBar {
  propertyInner = {
    ...this['propertyInner'],
    'label-selector': [...BAR_EDITOR_PROPERTY_INNER['label-selector'], 'vPosition'],
    'legend-selector': BAR_EDITOR_PROPERTY_INNER['legend-selector']
  }
  // 分组堆叠的内部标签需要居中贴近柱体，避免堆叠段较小时文字脱离图形。
  protected configTheme(chart: Chart, options: ColumnOptions): ColumnOptions {
    const baseOptions = super.configTheme(chart, options)
    const baseTheme = baseOptions.theme as object
    const theme = {
      ...baseTheme,
      innerLabels: {
        offset: 0
      }
    }
    return {
      ...options,
      theme
    }
  }

  // 分组堆叠标签直接使用当前段值，汇总值由堆叠图的总计逻辑单独处理。
  protected configLabel(chart: Chart, options: ColumnOptions): ColumnOptions {
    const tmpLabel = getLabel(chart)
    if (!tmpLabel) {
      return options
    }
    const baseOptions = { ...options, label: tmpLabel }
    const { label: labelAttr } = parseJson(chart.customAttr)
    baseOptions.label.style.fill = labelAttr.color
    const label = {
      ...baseOptions.label,
      formatter: function (param: Datum) {
        return valueFormatter(param.value, labelAttr.labelFormatter)
      }
    }
    return {
      ...baseOptions,
      label
    }
  }

  // tooltip 同时展示堆叠子维度和分组维度，帮助用户定位具体柱段。
  protected configTooltip(chart: Chart, options: ColumnOptions): ColumnOptions {
    const tooltipAttr = parseJson(chart.customAttr).tooltip
    if (!tooltipAttr.show) {
      return {
        ...options,
        tooltip: false
      }
    }
    const tooltip = {
      fields: [],
      formatter: (param: Datum) => {
        const obj = { name: `${param.category} - ${param.group}`, value: param.value }
        obj.value = valueFormatter(param.value, tooltipAttr.tooltipFormatter)
        return obj
      },
      container: getTooltipContainer(`tooltip-${chart.id}`, chart.container),
      itemTpl: TOOLTIP_TPL,
      enterable: true
    }
    return {
      ...options,
      tooltip
    }
  }

  // 当分组轴或堆叠轴缺失时降级渲染，避免空字段导致 G2Plot 初始化失败。
  protected configData(chart: Chart, options: ColumnOptions): ColumnOptions {
    if (!chart.xAxisExt?.length) {
      ;(options as any).isGroup = false
    }
    if (!chart.extStack?.length) {
      ;(options as any).isStack = false
      ;(options as any).groupField = 'category'
    }
    return options
  }

  // 分组堆叠先完成降级判断，再执行样式和交互配置。
  protected setupOptions(chart: Chart, options: ColumnOptions): ColumnOptions {
    return flow(
      this.configData,
      this.configTheme,
      this.configEmptyDataStrategy,
      this.configColor,
      this.configBasicStyle,
      this.configLabel,
      this.configTooltip,
      this.configLegend,
      this.configXAxis,
      this.configYAxis,
      this.configSlider,
      this.configAnalyse
    )(chart, options, {}, this)
  }

  constructor(name = 'bar-group-stack') {
    super(name)
    this.baseOptions = {
      ...this.baseOptions,
      isGroup: true,
      groupField: 'group'
    }
    this.axis = [...this.axis, 'xAxisExt', 'extStack']
  }
}

/**
 * 百分比堆叠柱状图展示各堆叠段占比，数据值由上游转换为 0 到 1 的比例。
 */
export class PercentageStackBar extends GroupStackBar {
  propertyInner = {
    ...this['propertyInner'],
    'label-selector': ['color', 'fontSize', 'vPosition', 'reserveDecimalCount'],
    'tooltip-selector': ['color', 'fontSize', 'backgroundColor', 'show', 'carousel']
  }
  // 百分比标签只格式化比例，不再套用普通数值单位，避免出现“万元%”等错误展示。
  protected configLabel(chart: Chart, options: ColumnOptions): ColumnOptions {
    const baseOptions = super.configLabel(chart, options)
    if (!baseOptions.label) {
      return baseOptions
    }
    const { customAttr } = chart
    const l = parseJson(customAttr).label
    const label = {
      ...baseOptions.label,
      formatter: function (param: Datum) {
        if (!param.value) {
          return '0%'
        }
        return (Math.round(param.value * 10000) / 100).toFixed(l.reserveDecimalCount) + '%'
      }
    }
    return {
      ...baseOptions,
      label
    }
  }

  // 百分比 tooltip 和标签共用精度设置，关闭 tooltip 时保留空配置以避免默认 tooltip 回退。
  protected configTooltip(chart: Chart, options: ColumnOptions): ColumnOptions {
    const tooltipAttr = parseJson(chart.customAttr).tooltip
    if (!tooltipAttr.show) {
      return {
        ...options,
        tooltip: {
          showContent: false
        }
      }
    }
    const { customAttr } = chart
    const l = parseJson(customAttr).label
    const tooltip = {
      formatter: (param: Datum) => {
        const obj = { name: param.category, value: param.value }
        obj.value = (Math.round(param.value * 10000) / 100).toFixed(l.reserveDecimalCount) + '%'
        return obj
      },
      container: getTooltipContainer(`tooltip-${chart.id}`, chart.container),
      itemTpl: TOOLTIP_TPL,
      enterable: true
    }
    return {
      ...options,
      tooltip
    }
  }
  protected setupOptions(chart: Chart, options: ColumnOptions): ColumnOptions {
    return flow(
      this.configTheme,
      this.configEmptyDataStrategy,
      this.configColor,
      this.configBasicStyle,
      this.configLabel,
      this.configTooltip,
      this.configLegend,
      this.configXAxis,
      this.configYAxis,
      this.configSlider,
      this.configAnalyse
    )(chart, options, {}, this)
  }
  constructor() {
    super('percentage-bar-stack')
    this.baseOptions = {
      ...this.baseOptions,
      isStack: true,
      isPercent: true,
      isGroup: false,
      groupField: undefined,
      meta: {
        category: {
          type: 'cat'
        }
      }
    }
    this.axis = [...BAR_AXIS_TYPE, 'extStack']
  }
}
