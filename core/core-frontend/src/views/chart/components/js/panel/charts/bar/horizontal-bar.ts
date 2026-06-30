import {
  G2PlotChartView,
  G2PlotDrawOptions
} from '@/views/chart/components/js/panel/types/impl/g2plot'
import type { Bar, BarOptions } from '@antv/g2plot/esm/plots/bar'
import {
  configAxisLabelLengthLimit,
  configPlotTooltipEvent,
  configRoundAngle,
  getPadding,
  getLabel,
  getTooltipContainer,
  setGradientColor,
  TOOLTIP_TPL
} from '@/views/chart/components/js/panel/common/common_antv'
import { cloneDeep, defaults, each, groupBy } from 'lodash-es'
import {
  convertToAlphaColor,
  flow,
  hexColorToRGBA,
  isAlphaColor,
  parseJson,
  setUpStackSeriesColor
} from '@/views/chart/components/js/util'
import { formatterItem, valueFormatter } from '@/views/chart/components/js/formatter'
import {
  BAR_AXIS_TYPE,
  BAR_EDITOR_PROPERTY,
  BAR_EDITOR_PROPERTY_INNER
} from '@/views/chart/components/js/panel/charts/bar/common'
import type { Datum } from '@antv/g2plot/esm/types/common'
import { useI18n } from '@/hooks/web/useI18n'
import {
  DEFAULT_BASIC_STYLE,
  DEFAULT_LABEL,
  DEFAULT_LEGEND_STYLE
} from '@/views/chart/components/editor/util/chart'
import { Group } from '@antv/g-canvas'
import { getItemsOfView } from '@antv/g2/lib/interaction/action/active-region'

const { t } = useI18n()
const DEFAULT_DATA = []

/**
 * 条形图渲染器，将维度映射到纵向分类轴、指标映射到横向数值轴。
 */
export class HorizontalBar extends G2PlotChartView<BarOptions, Bar> {
  // 条形图只需要维度轴和指标轴，横纵轴在渲染阶段会按 G2Plot 的 bar 坐标系转换。
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
  properties = BAR_EDITOR_PROPERTY
  // 横向条形图额外开放系列色和数值轴范围配置。
  propertyInner = {
    ...BAR_EDITOR_PROPERTY_INNER,
    'basic-style-selector': [...BAR_EDITOR_PROPERTY_INNER['basic-style-selector'], 'seriesColor'],
    'label-selector': ['hPosition', 'seriesLabelFormatter'],
    'tooltip-selector': ['fontSize', 'color', 'backgroundColor', 'seriesTooltipFormatter', 'show'],
    'x-axis-selector': [
      ...BAR_EDITOR_PROPERTY_INNER['x-axis-selector'],
      'axisLabelFormatter',
      'axisValue'
    ],
    'y-axis-selector': [
      'name',
      'color',
      'fontSize',
      'axisLine',
      'splitLine',
      'axisForm',
      'axisLabel',
      'position',
      'showLengthLimit'
    ]
  }
  axis: AxisType[] = [...BAR_AXIS_TYPE]
  // 基础配置固定为分组条形图，堆叠类型在子类构造函数中覆盖。
  protected baseOptions: BarOptions = {
    data: [],
    xField: 'value',
    yField: 'field',
    seriesField: 'category',
    isGroup: true
  }

  async drawChart(drawOptions: G2PlotDrawOptions<Bar>): Promise<Bar> {
    const { chart, container, action } = drawOptions
    if (!chart.data?.data?.length) {
      return
    }
    // 图表数据直接来自后端聚合结果，克隆后交由配置流水线处理。
    const data = cloneDeep(chart.data.data)

    // 初始化配置只保留字段映射和内边距，样式、轴和交互在 setupOptions 中叠加。
    const initOptions: BarOptions = {
      ...this.baseOptions,
      appendPadding: getPadding(chart),
      data
    }

    const options = this.setupOptions(chart, initOptions)

    const { Bar } = await import('@antv/g2plot/esm/plots/bar')
    // 创建图表实例后绑定条形和标签点击，确保联动参数一致。
    const newChart = new Bar(container, options)

    newChart.on('interval:click', action)
    if (options.label) {
      newChart.on('label:click', e => {
        action({
          x: e.x,
          y: e.y,
          data: {
            data: e.target.attrs.data
          }
        })
      })
    }
    // 仅普通条形图通过 active-region 兜底触发点击，分组和堆叠阴影缺少子维度信息。
    if (this.name === 'bar-horizontal' && options.tooltip) {
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
    configPlotTooltipEvent(chart, newChart as any)
    configAxisLabelLengthLimit(chart, newChart)
    return newChart
  }

  protected configXAxis(chart: Chart, options: BarOptions): BarOptions {
    const tmpOptions = super.configXAxis(chart, options)
    if (!tmpOptions.xAxis) {
      return tmpOptions
    }
    const xAxis = parseJson(chart.customStyle).xAxis
    const axisValue = xAxis.axisValue
    if (tmpOptions.xAxis.label) {
      // 数值轴标签使用统一格式化配置，支持千分位、百分比和小数位设置。
      tmpOptions.xAxis.label.formatter = value => {
        return valueFormatter(value, xAxis.axisLabelFormatter)
      }
    }
    if (tmpOptions.xAxis.position === 'top') {
      // 条形图坐标系旋转后，编辑器的上方位置对应左侧数值轴。
      tmpOptions.xAxis.position = 'left'
    }
    if (tmpOptions.xAxis.position === 'bottom') {
      // 条形图坐标系旋转后，编辑器的底部位置对应右侧数值轴。
      tmpOptions.xAxis.position = 'right'
    }
    if (!axisValue?.auto) {
      // 固定数值轴范围同时写入 min/max/limit，避免自动范围覆盖用户配置。
      const axis = {
        xAxis: {
          ...tmpOptions.xAxis,
          min: axisValue.min,
          max: axisValue.max,
          minLimit: axisValue.min,
          maxLimit: axisValue.max,
          tickCount: axisValue.splitCount
        }
      }
      // 条形图固定最小值后过滤小于范围的数据，避免柱形反向穿出坐标轴。
      const { data } = options
      const newData = data.filter(item => item.value >= axisValue.min)
      return { ...tmpOptions, data: newData, ...axis }
    }
    return tmpOptions
  }

  protected configTooltip(chart: Chart, options: BarOptions): BarOptions {
    return super.configMultiSeriesTooltip(chart, options)
  }

  protected configBasicStyle(chart: Chart, options: BarOptions): BarOptions {
    const basicStyle = parseJson(chart.customAttr).basicStyle
    if (basicStyle.gradient) {
      // 渐变色按基础配色和透明度生成，保持条形方向上的视觉一致性。
      let color = basicStyle.colors
      color = color.map(ele => {
        const tmp = hexColorToRGBA(ele, basicStyle.alpha)
        return setGradientColor(tmp, true)
      })
      options = {
        ...options,
        color
      }
    }
    options = {
      ...options,
      ...configRoundAngle(chart, 'barStyle')
    }

    let barWidthRatio
    const _v = basicStyle.columnWidthRatio ?? DEFAULT_BASIC_STYLE.columnWidthRatio
    // G2Plot 使用 0 到 1 的柱宽比例，编辑器中以 1 到 100 的百分比展示。
    if (_v >= 1 && _v <= 100) {
      barWidthRatio = _v / 100.0
    } else if (_v < 1) {
      barWidthRatio = 1 / 100.0
    } else if (_v > 100) {
      barWidthRatio = 1
    }
    if (barWidthRatio) {
      ;(options as any).barWidthRatio = barWidthRatio
    }

    return options
  }

  setupDefaultOptions(chart: ChartObj): ChartObj {
    const { customAttr, senior } = chart
    const { label } = customAttr
    // 历史配置中的非法标签位置回退到中间，保证条形图首次渲染稳定。
    if (!['left', 'middle', 'right'].includes(label.position)) {
      label.position = 'middle'
    }
    // 条形图默认忽略空数据，避免空分类撑开横向空间。
    senior.functionCfg.emptyDataStrategy = 'ignoreData'
    return chart
  }

  protected configLabel(chart: Chart, options: BarOptions): BarOptions {
    const tmpOptions = super.configLabel(chart, options)
    if (!tmpOptions.label) {
      return {
        ...tmpOptions,
        label: false
      }
    }
    const labelAttr = parseJson(chart.customAttr).label
    const formatterMap = labelAttr.seriesLabelFormatter?.reduce((pre, next) => {
      pre[next.id] = next
      return pre
    }, {})
    // 默认灰色只作为兜底值，系列标签配置存在时会覆盖颜色和字号。
    tmpOptions.label.style.fill = DEFAULT_LABEL.color
    const label = {
      fields: [],
      ...tmpOptions.label,
      formatter: (data: Datum) => {
        // 没有独立系列标签配置时直接展示原始指标值。
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
        // 返回自定义 Group 以便把原始 data 挂到标签上，支持标签点击联动。
        const value = valueFormatter(data.value, labelCfg.formatterCfg)
        const group = new Group({})
        group.addShape({
          type: 'text',
          attrs: {
            x: 0,
            y: 0,
            data,
            text: value,
            textAlign: 'start',
            textBaseline: 'top',
            fontSize: labelCfg.fontSize,
            fontFamily: chart.fontFamily,
            fill: labelCfg.color
          }
        })
        return group
      }
    }
    return {
      ...tmpOptions,
      label
    }
  }

  protected configYAxis(chart: Chart, options: BarOptions): BarOptions {
    const tmpOptions = super.configYAxis(chart, options)
    if (!tmpOptions.yAxis) {
      return tmpOptions
    }
    if (tmpOptions.yAxis.position === 'left') {
      // 横向条形图的分类轴位置需要从左右语义转换为上下语义。
      tmpOptions.yAxis.position = 'bottom'
    }
    if (tmpOptions.yAxis.position === 'right') {
      // 编辑器右侧位置对应 G2Plot 横向条形图的顶部分类轴。
      tmpOptions.yAxis.position = 'top'
    }
    return tmpOptions
  }

  protected setupOptions(chart: Chart, options: BarOptions): BarOptions {
    // 配置顺序先处理数据和样式，再叠加标签、提示、坐标轴、缩略轴和分析能力。
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
      this.configAnalyseHorizontal,
      this.configBarConditions
    )(chart, options, {}, this)
  }

  constructor(name = 'bar-horizontal') {
    super(name, DEFAULT_DATA)
  }
}

/**
 * 堆叠条形图渲染器，在普通条形图基础上增加堆叠维度和图例排序能力。
 */
export class HorizontalStackBar extends HorizontalBar {
  properties = BAR_EDITOR_PROPERTY.filter(ele => ele !== 'threshold')
  axisConfig = {
    ...this['axisConfig'],
    extStack: {
      name: `${t('chart.stack_item')} / ${t('chart.dimension')}`,
      type: 'd',
      limit: 1,
      allowEmpty: true
    }
  }
  propertyInner = {
    ...this['propertyInner'],
    'label-selector': [
      'color',
      'fontSize',
      'hPosition',
      'labelFormatter',
      'showTotal',
      'showStackQuota'
    ],
    'tooltip-selector': ['fontSize', 'color', 'backgroundColor', 'tooltipFormatter', 'show'],
    'legend-selector': [...BAR_EDITOR_PROPERTY_INNER['legend-selector'], 'legendSort']
  }
  protected configLabel(chart: Chart, options: BarOptions): BarOptions {
    let label = getLabel(chart)
    if (!label) {
      return { ...options, label }
    }
    options = { ...options, label }
    const { label: labelAttr } = parseJson(chart.customAttr)
    if (labelAttr.showStackQuota || labelAttr.showStackQuota === undefined) {
      // showStackQuota 控制堆叠段标签，未配置时按旧版本默认展示。
      ;(options.label as any).style.fill = labelAttr.color
      label = {
        ...options.label,
        formatter: function (data: Datum) {
          const value = valueFormatter(data.value, labelAttr.labelFormatter)
          const group = new Group({})
          group.addShape({
            type: 'text',
            attrs: {
              x: 0,
              y: 0,
              data,
              text: value,
              textAlign: 'start',
              textBaseline: 'top',
              fontSize: labelAttr.fontSize,
              fontFamily: chart.fontFamily,
              fill: labelAttr.color
            }
          })
          return group
        }
      }
    } else {
      label = false
    }
    if (labelAttr.showTotal) {
      // 总计标签按主维度聚合堆叠值，并以 annotation 形式绘制在柱尾。
      const formatterCfg = labelAttr.labelFormatter ?? formatterItem
      each(groupBy(options.data, 'field'), (values, key) => {
        const total = values.reduce((a, b) => a + b.value, 0)
        const value = valueFormatter(total, formatterCfg)
        if (!options.annotations) {
          ;(options as any).annotations = []
        }
        options.annotations.push({
          type: 'text',
          position: [key, total],
          content: `${value}`,
          style: {
            textAlign: 'start',
            fontSize: labelAttr.fontSize,
            fill: labelAttr.color
          },
          offsetX: parseInt(labelAttr.fontSize as unknown as string) / 2
        })
      })
    }
    return {
      ...options,
      label
    }
  }

  protected configTooltip(chart: Chart, options: BarOptions): BarOptions {
    const tooltipAttr = parseJson(chart.customAttr).tooltip
    if (!tooltipAttr.show) {
      return {
        ...options,
        tooltip: false
      }
    }
    const tooltip = {
      formatter: (param: Datum) => {
        const obj = { name: param.category, value: param.value }
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
  protected configColor(chart: Chart, options: BarOptions): BarOptions {
    const customStyle = parseJson(chart.customStyle)
    const { sort } = customStyle.legend
    const extStack = chart.extStack[0]
    if ((!sort || sort === 'none') && extStack?.customSort?.length > 0) {
      // 堆叠维度的自定义排序写入 category 元数据，影响堆叠顺序和图例顺序。
      const sort = extStack.customSort ?? []
      if (sort?.length) {
        // 新数据可能不在历史排序中，需要追加到末尾，避免新增系列被隐藏。
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
  public setupSeriesColor(chart: ChartObj, data?: any[]): ChartBasicStyle['seriesColor'] {
    // 堆叠系列色按 extStack 维度生成，供图例和系列色面板共同使用。
    return setUpStackSeriesColor(chart, data)
  }

  protected configData(chart: Chart, options: BarOptions): BarOptions {
    const { xAxis, extStack, yAxis } = chart
    const mainSort = xAxis.some(axis => axis.sort !== 'none')
    const subSort = extStack.some(axis => axis.sort !== 'none')
    if (mainSort || subSort) {
      // 维度排序优先级高于指标排序，避免多重排序互相覆盖。
      return options
    }
    const quotaSort = yAxis?.[0]?.sort !== 'none'
    if (!quotaSort || !extStack.length || !yAxis.length) {
      return options
    }
    const { data } = options
    // 指标排序按每个主维度的堆叠合计值排序，保持整根条形的顺序稳定。
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

  protected configLegend(chart: Chart, options: BarOptions): BarOptions {
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

    // 图例 marker 半径跟随图例设置，颜色保持与堆叠系列一致。
    ;(optionTmp.legend.marker as any).style = style => {
      return {
        r: size,
        fill: style.fill
      }
    }
    const { sort, customSort, icon } = customStyle.legend
    if (sort && sort !== 'none' && chart.extStack.length) {
      // 图例排序开启时手工生成图例项，才能同时控制顺序、图标和透明度。
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
        // 默认排序按图例名称升降序排列。
        items.sort((a, b) => {
          return sort !== 'desc' ? a.name.localeCompare(b.name) : b.name.localeCompare(a.name)
        })
      } else {
        // 自定义排序命中的项提前展示，其余新增项保留在后面。
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
        // 已手工生成图例项后移除 meta 排序，避免二次排序影响图例显示。
        delete optionTmp.meta?.category.values
      }
    }
    return optionTmp
  }

  setupDefaultOptions(chart: ChartObj): ChartObj {
    const chartTmp = super.setupDefaultOptions(chart)
    // 堆叠条形图默认展示堆叠段指标标签，延续旧版可视化行为。
    chartTmp.customAttr.label.showStackQuota = true
    return chartTmp
  }

  protected setupOptions(chart: Chart, options: BarOptions): BarOptions {
    // 堆叠图先完成数据排序和系列色，再应用样式、标签、提示和坐标轴。
    return flow(
      this.configTheme,
      this.configEmptyDataStrategy,
      this.configData,
      this.configColor,
      this.configBasicStyle,
      this.configLabel,
      this.configTooltip,
      this.configLegend,
      this.configXAxis,
      this.configYAxis,
      this.configSlider,
      this.configAnalyseHorizontal
    )(chart, options, {}, this)
  }

  constructor(name = 'bar-stack-horizontal') {
    super(name)
    this.baseOptions = {
      ...this.baseOptions,
      isGroup: false,
      isStack: true,
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
 * 百分比堆叠条形图渲染器，将堆叠值归一化后按百分比展示。
 */
export class HorizontalPercentageStackBar extends HorizontalStackBar {
  propertyInner = {
    ...this['propertyInner'],
    'label-selector': ['color', 'fontSize', 'hPosition', 'reserveDecimalCount'],
    'tooltip-selector': ['color', 'fontSize', 'backgroundColor', 'show']
  }
  protected configLabel(chart: Chart, options: BarOptions): BarOptions {
    const baseLabel = getLabel(chart)
    if (!baseLabel) {
      return { ...options, label: baseLabel }
    }
    const { customAttr } = chart
    const l = parseJson(customAttr).label
    const label = {
      ...baseLabel,
      formatter: function (data: Datum) {
        // 百分比堆叠图的 data.value 已是比例值，需要按百分比格式化。
        let value = data.value
        if (value) {
          value = (Math.round(value * 10000) / 100).toFixed(l.reserveDecimalCount) + '%'
        } else {
          value = '0%'
        }
        const group = new Group({})
        group.addShape({
          type: 'text',
          attrs: {
            x: 0,
            y: 0,
            data,
            text: value,
            textAlign: 'start',
            textBaseline: 'top',
            fontSize: l.fontSize,
            fontFamily: chart.fontFamily,
            fill: l.color
          }
        })
        return group
      }
    }
    return {
      ...options,
      label
    }
  }

  protected configTooltip(chart: Chart, options: BarOptions): BarOptions {
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
        // tooltip 与标签使用同一小数位配置，保证百分比展示一致。
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
  protected setupOptions(chart: Chart, options: BarOptions): BarOptions {
    // 百分比堆叠图复用堆叠配置链，但不执行普通堆叠的数据排序逻辑。
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
      this.configAnalyseHorizontal
    )(chart, options, {}, this)
  }

  constructor() {
    super('percentage-bar-stack-horizontal')
    this.baseOptions = {
      ...this.baseOptions,
      isPercent: true
    }
  }
}
