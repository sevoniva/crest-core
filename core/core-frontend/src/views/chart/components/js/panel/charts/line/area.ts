import {
  G2PlotChartView,
  G2PlotDrawOptions
} from '@/views/chart/components/js/panel/types/impl/g2plot'
import type { Area as G2Area, AreaOptions } from '@antv/g2plot/esm/plots/area'
import {
  configPlotTooltipEvent,
  getPadding,
  getTooltipContainer,
  setGradientColor,
  TOOLTIP_TPL
} from '@/views/chart/components/js/panel/common/common_antv'
import { cloneDeep } from 'lodash-es'
import {
  flow,
  getLineConditions,
  getLineLabelColorByCondition,
  hexColorToRGBA,
  parseJson,
  setUpStackSeriesColor
} from '@/views/chart/components/js/util'
import {
  calcNiceMinValue,
  listenYAxisNiceMinEvents,
  valueFormatter
} from '@/views/chart/components/js/formatter'
import {
  LINE_AXIS_TYPE,
  LINE_EDITOR_PROPERTY,
  LINE_EDITOR_PROPERTY_INNER
} from '@/views/chart/components/js/panel/charts/line/common'
import { Label } from '@antv/g2plot/lib/types/label'
import { Datum } from '@antv/g2plot/esm/types/common'
import { useI18n } from '@/hooks/web/useI18n'
import { DEFAULT_LABEL } from '@/views/chart/components/editor/util/chart'
import { clearExtremum, extremumEvt } from '@/views/chart/components/js/extremumUitl'
import { Group } from '@antv/g-canvas'

const { t } = useI18n()
// 面积图无数据时复用同一个空数组，避免渲染配置里产生无意义的新引用
const DEFAULT_DATA = []

// 判断当前面积图是否启用了累计流逻辑，兼容图表类型和历史自定义属性两种来源
const isCumulativeFlowChart = (chart: Chart) => {
  const customAttr: DeepPartial<ChartAttr> = parseJson(chart.customAttr)
  return chart.type === 'cumulative-flow' || Boolean((customAttr.basicStyle as any)?.cumulativeFlow)
}

// 读取累计流阶段顺序，优先使用用户自定义排序，缺省时按数据出现顺序推导
const getCumulativeFlowStageOrder = (chart: Chart, data: any[] = []) => {
  const customSort = (chart.extStack?.[0] as any)?.customSort
  if (Array.isArray(customSort) && customSort.length) {
    return customSort
  }

  return data.reduce((stages, item) => {
    if (item.category && !stages.includes(item.category)) {
      stages.push(item.category)
    }
    return stages
  }, [] as string[])
}

// 获取阶段在累计流排序中的位置，未知阶段排到末尾以保持可预期展示
const getStageIndex = (stageOrder: string[], stage: string) => {
  const index = stageOrder.indexOf(stage)
  return index === -1 ? Number.MAX_SAFE_INTEGER : index
}

// 比较两个累计流阶段在配置顺序中的先后关系
const compareCumulativeFlowStage = (stageOrder: string[], left: string, right: string) => {
  return getStageIndex(stageOrder, left) - getStageIndex(stageOrder, right)
}

// 累计流数据先按阶段排序，再按维度字段兜底排序，保证堆叠区域稳定
const cumulativeFlowSort = (left, right, stageOrder: string[]) => {
  const stageCompare =
    compareCumulativeFlowStage(stageOrder, left.category, right.category) ||
    `${left.category}`.localeCompare(`${right.category}`)
  if (stageCompare !== 0) {
    return stageCompare
  }
  return `${left.field}`.localeCompare(`${right.field}`)
}

// 面积图视图封装 G2Plot 的渲染、样式配置和交互事件
export class Area extends G2PlotChartView<AreaOptions, G2Area> {
  // 面积图在编辑器中开放的属性面板集合
  properties = LINE_EDITOR_PROPERTY
  // 面积图对通用折线属性面板的增量扩展
  propertyInner = {
    ...LINE_EDITOR_PROPERTY_INNER,
    'basic-style-selector': [
      ...LINE_EDITOR_PROPERTY_INNER['basic-style-selector'],
      'gradient',
      'seriesColor'
    ],
    'label-selector': ['seriesLabelVPosition', 'seriesLabelFormatter', 'showExtremum'],
    'tooltip-selector': [
      ...LINE_EDITOR_PROPERTY_INNER['tooltip-selector'],
      'seriesTooltipFormatter',
      'carousel'
    ]
  }
  // 面积图支持的字段轴类型，沿用折线图的维度和指标轴配置
  axis: AxisType[] = [...LINE_AXIS_TYPE]
  // 轴字段拖拽区域的名称和字段类型限制
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
  // G2Plot 面积图的基础配置，业务数据和样式会在渲染前合并进去
  baseOptions: AreaOptions = {
    data: [],
    xField: 'field',
    yField: 'value',
    seriesField: 'category',
    isStack: false,
    interactions: [
      {
        type: 'legend-active',
        cfg: {
          start: [{ trigger: 'legend-item:mouseenter', action: ['element-active:reset'] }],
          end: [{ trigger: 'legend-item:mouseleave', action: ['element-active:reset'] }]
        }
      },
      {
        type: 'legend-filter',
        cfg: {
          start: [
            {
              trigger: 'legend-item:click',
              action: [
                'list-unchecked:toggle',
                'data-filter:filter',
                'element-active:reset',
                'element-highlight:reset'
              ]
            }
          ]
        }
      },
      {
        type: 'active-region',
        cfg: {
          start: [{ trigger: 'element:mousemove', action: 'active-region:show' }],
          end: [{ trigger: 'element:mouseleave', action: 'active-region:hide' }]
        }
      }
    ]
  }

  // 根据图表数据和编辑器配置创建面积图实例
  async drawChart(drawOptions: G2PlotDrawOptions<G2Area>): Promise<G2Area> {
    const { chart, container, action } = drawOptions
    chart.container = container
    if (!chart.data?.data?.length) {
      clearExtremum(chart)
      return
    }
    // data
    const data = cloneDeep(chart.data.data)
    if (isCumulativeFlowChart(chart)) {
      const stageOrder = getCumulativeFlowStageOrder(chart, data)
      data.sort((left, right) => cumulativeFlowSort(left, right, stageOrder))
    }

    const initOptions: AreaOptions = {
      ...this.baseOptions,
      data,
      appendPadding: getPadding(chart)
    }
    // options
    const options = this.setupOptions(chart, initOptions)
    const { Area: G2Area } = await import('@antv/g2plot/esm/plots/area')
    // 开始渲染
    const newChart = new G2Area(container, options)

    newChart.on('point:click', action)
    extremumEvt(newChart, chart, options, container)
    configPlotTooltipEvent(chart, newChart)
    listenYAxisNiceMinEvents(chart, newChart)
    return newChart
  }

  // 配置面积图标签内容、位置和条件颜色
  protected configLabel(chart: Chart, options: AreaOptions): AreaOptions {
    const tmpOptions = super.configLabel(chart, options)
    if (!tmpOptions.label) {
      return {
        ...tmpOptions,
        label: false
      }
    }
    const { label: labelAttr, basicStyle } = parseJson(chart.customAttr)
    const conditions = getLineConditions(chart)
    const formatterMap = labelAttr.seriesLabelFormatter?.reduce((pre, next) => {
      pre[next.id] = next
      return pre
    }, {})
    tmpOptions.label.style.fill = DEFAULT_LABEL.color
    const label = {
      fields: [],
      ...tmpOptions.label,
      layout: labelAttr.fullDisplay ? [{ type: 'limit-in-plot' }] : tmpOptions.label.layout,
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
        const position =
          labelCfg.position === 'top'
            ? -2 - basicStyle.lineSymbolSize
            : 10 + basicStyle.lineSymbolSize
        const value = valueFormatter(data.value, labelCfg.formatterCfg)
        const color =
          getLineLabelColorByCondition(conditions, data.value, data.quotaList[0].id) ||
          labelCfg.color
        const group = new Group({})
        group.addShape({
          type: 'text',
          attrs: {
            x: 0,
            y: position,
            text: value,
            textAlign: 'start',
            textBaseline: 'top',
            fontSize: labelCfg.fontSize,
            fontFamily: chart.fontFamily,
            fill: color
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

  protected configBasicStyle(chart: Chart, options: AreaOptions): AreaOptions {
    // size
    const customAttr: DeepPartial<ChartAttr> = parseJson(chart.customAttr)
    const s: DeepPartial<ChartBasicStyle> = JSON.parse(JSON.stringify(customAttr.basicStyle))
    const smooth = s.lineSmooth
    const point = {
      size: s.lineSymbolSize,
      shape: s.lineSymbol
    }
    const line = {
      style: {
        lineWidth: s.lineWidth
      }
    }
    // custom color
    const { colors, alpha } = customAttr.basicStyle
    const areaColors = [...colors, ...colors]
    let areaStyle
    if (customAttr.basicStyle.gradient) {
      const colorMap = new Map()
      const yAxis = parseJson(chart.customStyle).yAxis
      const axisValue = yAxis.axisValue
      const start =
        !axisValue?.auto && axisValue.min && axisValue.max ? axisValue.min / axisValue.max : 0
      areaStyle = item => {
        let ele: string
        const key = `${item.field}-${item.category}`
        if (colorMap.has(key)) {
          ele = colorMap.get(key)
        } else {
          ele = areaColors.shift()
          colorMap.set(key, ele)
        }
        if (ele) {
          return {
            fill: setGradientColor(hexColorToRGBA(ele, alpha), true, 270, start)
          }
        }
        return {
          fill: 'rgba(255,255,255,0)'
        }
      }
    }
    return {
      ...options,
      smooth,
      line,
      point,
      areaStyle
    }
  }

  protected configYAxis(chart: Chart, options: AreaOptions): AreaOptions {
    const tmpOptions = super.configYAxis(chart, options)
    if (!tmpOptions.yAxis) {
      return tmpOptions
    }
    const yAxis = parseJson(chart.customStyle).yAxis
    if (tmpOptions.yAxis.label) {
      tmpOptions.yAxis.label.formatter = value => {
        return valueFormatter(value, yAxis.axisLabelFormatter)
      }
    }
    const axisValue = yAxis.axisValue
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
      return { ...tmpOptions, ...axis }
    }
    if (axisValue?.auto) {
      return calcNiceMinValue(chart, options, tmpOptions)
    }
    return tmpOptions
  }

  protected configTooltip(chart: Chart, options: AreaOptions): AreaOptions {
    return super.configMultiSeriesTooltip(chart, options)
  }

  protected setupOptions(chart: Chart, options: AreaOptions): AreaOptions {
    return flow(
      this.configTheme,
      this.configEmptyDataStrategy,
      this.configColor,
      this.configLabel,
      this.configTooltip,
      this.configBasicStyle,
      this.configLegend,
      this.configXAxis,
      this.configYAxis,
      this.configSlider,
      this.configAnalyse,
      this.configConditions
    )(chart, options, {}, this)
  }

  constructor(name = 'area') {
    super(name, DEFAULT_DATA)
  }
}

/**
 * 堆叠面积图
 */
export class StackArea extends Area {
  propertyInner = {
    ...this['propertyInner'],
    'label-selector': ['vPosition', 'fontSize', 'color', 'labelFormatter'],
    'tooltip-selector': ['fontSize', 'color', 'tooltipFormatter', 'show', 'carousel']
  }
  axisConfig = {
    ...this['axisConfig'],
    extStack: {
      name: `${t('chart.stack_item')} / ${t('chart.dimension')}`,
      type: 'd',
      limit: 1,
      allowEmpty: true
    }
  }
  protected configLabel(chart: Chart, options: AreaOptions): AreaOptions {
    const { label: labelAttr, basicStyle } = parseJson(chart.customAttr)
    if (!labelAttr?.show) {
      return {
        ...options,
        label: false
      }
    }
    const layout = []
    if (!labelAttr.fullDisplay) {
      const tmpOptions = super.configLabel(chart, options)
      layout.push(...(((tmpOptions.label as Record<string, any>)?.layout || []) as any[]))
    } else {
      layout.push({ type: 'limit-in-plot' })
    }
    const position =
      labelAttr.position === 'top' ? -2 - basicStyle.lineSymbolSize : 8 + basicStyle.lineSymbolSize
    const label: Label = {
      position: labelAttr.position as any,
      offsetY: position,
      layout,
      style: {
        fill: labelAttr.color,
        fontSize: labelAttr.fontSize
      },
      formatter: function (param: Datum) {
        return valueFormatter(param.value, labelAttr.labelFormatter)
      }
    }
    return { ...options, label }
  }

  public setupDefaultOptions(chart: ChartObj): ChartObj {
    chart.senior.functionCfg.emptyDataStrategy = 'ignoreData'
    return chart
  }

  protected configColor(chart: Chart, options: AreaOptions): AreaOptions {
    return this.configStackColor(chart, options)
  }
  public setupSeriesColor(chart: ChartObj, data?: any[]): ChartBasicStyle['seriesColor'] {
    return setUpStackSeriesColor(chart, data)
  }
  protected configBasicStyle(chart: Chart, options: AreaOptions): AreaOptions {
    if (!isCumulativeFlowChart(chart)) {
      return super.configBasicStyle(chart, options)
    }

    const customAttr: DeepPartial<ChartAttr> = parseJson(chart.customAttr)
    const colors = customAttr.basicStyle?.colors || []
    const alpha = customAttr.basicStyle?.alpha ?? 88
    const stageOrder = getCumulativeFlowStageOrder(chart, options.data)
    const firstStage = stageOrder[0]
    const lastStage = stageOrder[stageOrder.length - 1]
    const colorByStage = new Map<string, string>()
    stageOrder.forEach((stage, index) => {
      colorByStage.set(stage, colors[index] || colors[index % colors.length] || '#38bdf8')
    })

    return {
      ...options,
      smooth: true,
      point: false as any,
      line: {
        style: ({ category }) => ({
          stroke: hexColorToRGBA(colorByStage.get(category) || '#dff7ff', 92),
          lineWidth: category === lastStage ? 2 : 1,
          shadowBlur: category === lastStage ? 8 : 0,
          shadowColor: category === lastStage ? 'rgba(102,217,194,.45)' : 'rgba(0,0,0,0)'
        })
      },
      areaStyle: ({ category }) => {
        const color = colorByStage.get(category) || '#38bdf8'
        return {
          fill: setGradientColor(hexColorToRGBA(color, alpha), true, 270, 0),
          fillOpacity: 0.92,
          stroke: hexColorToRGBA(color, 28),
          lineWidth: 0.4,
          shadowBlur: category === firstStage ? 10 : 0,
          shadowColor: category === firstStage ? hexColorToRGBA(color, 16) : 'rgba(0,0,0,0)'
        }
      }
    }
  }

  protected configTooltip(chart: Chart, options: AreaOptions): AreaOptions {
    const customAttr: DeepPartial<ChartAttr> = parseJson(chart.customAttr)
    const tooltipAttr = customAttr.tooltip
    if (!tooltipAttr.show) {
      return {
        ...options,
        tooltip: false
      }
    }
    const cumulativeFlow = isCumulativeFlowChart(chart)
    const stageOrder = getCumulativeFlowStageOrder(chart, options.data)
    const tooltip = {
      shared: cumulativeFlow,
      showMarkers: !cumulativeFlow,
      customItems: cumulativeFlow
        ? items =>
            items.sort((left, right) => {
              return (
                compareCumulativeFlowStage(stageOrder, left.name, right.name) ||
                `${left.name}`.localeCompare(`${right.name}`)
              )
            })
        : undefined,
      formatter: function (param: Datum) {
        const obj = {
          name: param.category,
          value: valueFormatter(param.value, tooltipAttr.tooltipFormatter)
        }
        return obj
      },
      container: getTooltipContainer(`tooltip-${chart.id}`, chart.container),
      itemTpl: TOOLTIP_TPL,
      enterable: true
    }
    return { ...options, tooltip }
  }

  constructor(name = 'area-stack') {
    super(name)
    this.baseOptions = {
      ...this.baseOptions,
      isStack: true
    }
    delete this.propertyInner.threshold
    this.properties = this.properties.filter(item => item !== 'threshold')
    this.axis.push('extStack')
  }
}

/**
 * 累积流图
 */
export class CumulativeFlow extends StackArea {
  constructor() {
    super('cumulative-flow')
  }
}
