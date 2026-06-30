import {
  G2PlotChartView,
  G2PlotDrawOptions
} from '@/views/chart/components/js/panel/types/impl/g2plot'
import type { DualAxes, DualAxesOptions } from '@antv/g2plot/esm/plots/dual-axes'
import {
  configRoundAngle,
  configPlotTooltipEvent,
  getAnalyse,
  getLabel,
  getPadding,
  getTooltipContainer,
  getYAxis,
  getYAxisExt,
  setGradientColor,
  TOOLTIP_TPL
} from '../../common/common_antv'
import { flow, hexColorToRGBA, parseJson } from '@/views/chart/components/js/util'
import {
  cloneDeep,
  isEmpty,
  defaultTo,
  map,
  filter,
  union,
  defaultsDeep,
  defaults
} from 'lodash-es'
import { valueFormatter } from '@/views/chart/components/js/formatter'
import {
  CHART_MIX_AXIS_TYPE,
  CHART_MIX_DEFAULT_BASIC_STYLE,
  CHART_MIX_EDITOR_PROPERTY,
  CHART_MIX_EDITOR_PROPERTY_INNER,
  MixChartBasicStyle
} from './chart-mix-common'
import type { Datum } from '@antv/g2plot/esm/types/common'
import { useI18n } from '@/hooks/web/useI18n'
import {
  DEFAULT_BASIC_STYLE,
  DEFAULT_LABEL,
  DEFAULT_LEGEND_STYLE
} from '@/views/chart/components/editor/util/chart'
import type { Options } from '@antv/g2plot/esm'
import { Group } from '@antv/g-canvas'
import { extremumEvt } from '@/views/chart/components/js/extremumUitl'

const { t } = useI18n()
const DEFAULT_DATA = []

/**
 * 柱线混合图渲染器，负责把左右轴数据分别映射到 DualAxes 的两套 geometry。
 */
export class ColumnLineMix extends G2PlotChartView<any, any> {
  // 混合图开放的编辑能力来自公共配置，标签和 tooltip 额外支持左右轴系列格式化。
  properties = CHART_MIX_EDITOR_PROPERTY
  propertyInner = {
    ...CHART_MIX_EDITOR_PROPERTY_INNER,
    'label-selector': ['vPosition', 'seriesLabelFormatter'],
    'tooltip-selector': [
      ...CHART_MIX_EDITOR_PROPERTY_INNER['tooltip-selector'],
      'seriesTooltipFormatter',
      'carousel'
    ]
  }
  // 右轴分类字段复用 extBubble，右轴指标使用 yAxisExt，字段名称需要和查询结果组装逻辑保持一致。
  axis: AxisType[] = [...CHART_MIX_AXIS_TYPE, 'xAxisExtRight', 'yAxisExt']
  axisConfig = {
    xAxis: {
      name: `${t('chart.drag_block_type_axis')} / ${t('chart.dimension')}`,
      type: 'd'
    },
    yAxis: {
      name: `${t('chart.drag_block_value_axis_left')} / ${t('chart.column_quota')}`,
      limit: 1,
      type: 'q'
    },
    extBubble: {
      // 复用气泡字段槽位保存右轴分类，避免新增轴类型影响已有查询协议。
      name: `${t('chart.drag_block_type_axis_right')} / ${t('chart.dimension')}`,
      limit: 1,
      type: 'd',
      allowEmpty: true
    },
    yAxisExt: {
      name: `${t('chart.drag_block_value_axis_right')} / ${t('chart.line_quota')}`,
      limit: 1,
      type: 'q',
      allowEmpty: true
    }
  }

  protected getLeftType(): string {
    return 'column'
  }
  protected getRightType(): string {
    return 'line'
  }

  // 绘制时把左右轴数据分开传入 DualAxes，并为柱、点两类图元分别挂接点击事件。
  async drawChart(drawOptions: G2PlotDrawOptions<any>): Promise<any> {
    const { chart, action, container } = drawOptions
    chart.container = container
    if (!chart.data?.left?.data?.length && !chart.data?.right?.data?.length) {
      return
    }
    const left = cloneDeep(chart.data?.left?.data)
    const right = cloneDeep(chart.data?.right?.data)

    // const data1Type = (left[0]?.type === 'bar' ? 'column' : left[0]?.type) ?? 'column'
    // const data2Type = (right[0]?.type === 'bar' ? 'column' : right[0]?.type) ?? 'column'
    const data1Type = this.getLeftType()
    const data2Type = this.getRightType()

    const isGroup = this.name === 'chart-mix-group' && chart.xAxisExt?.length > 0
    const isStack = this.name === 'chart-mix-stack' && chart.extStack?.length > 0
    const seriesField = 'category'
    const seriesField2 = 'category'

    const data1 = defaultTo(left[0]?.data, [])
    const data2 = map(defaultTo(right[0]?.data, []), d => {
      return {
        ...d,
        valueExt: d.value
      }
    })
    // 右轴数据改写为 valueExt，避免 DualAxes 同时使用 value 时无法区分左右轴度量。
    const initOptions: DualAxesOptions = {
      data: [data1, data2],
      xField: 'field',
      yField: ['value', 'valueExt'], // 左右轴度量字段必须不同，G2Plot 才能分别绑定坐标轴。
      appendPadding: getPadding(chart),
      geometryOptions: [
        {
          geometry: data1Type,
          marginRatio: 0,
          color: [],
          isGroup: isGroup,
          isStack: isStack,
          seriesField: seriesField
        },
        {
          geometry: data2Type,
          color: [],
          seriesField: seriesField2
        }
      ],
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
          type: 'active-region'
        }
      ]
    }
    const options = this.setupOptions(chart, initOptions)
    const { DualAxes } = await import('@antv/g2plot/esm/plots/dual-axes')
    // 动态加载图表类减少编辑器首屏体积，实例创建后再绑定交互事件。
    const newChart: any = new DualAxes(container, options)

    newChart.on('point:click', action)
    newChart.on('interval:click', action)
    extremumEvt(newChart, chart, options, container)
    configPlotTooltipEvent(chart, newChart)
    return newChart
  }

  // 标签按左右轴分别匹配系列格式化配置，避免左轴指标格式误用于右轴线条。
  protected configLabel(chart: Chart, options: any): any {
    const tempLabel = getLabel(chart)
    const tmpOption = { ...options }
    if (!tempLabel) {
      if (tmpOption.geometryOptions) {
        tmpOption.geometryOptions[0].label = false
        tmpOption.geometryOptions[1].label = false
      }
      return tmpOption
    }

    const labelAttr = parseJson(chart.customAttr).label
    const axisFormatterMap = {}
    labelAttr.seriesLabelFormatter?.forEach(attr => {
      if (!axisFormatterMap[attr.axisType]) {
        axisFormatterMap[attr.axisType] = []
      }
      axisFormatterMap[attr.axisType].push(attr)
    })
    const axisTypes = ['yAxis', 'yAxisExt']
    axisTypes.forEach(axisType => {
      const formatterMap = axisFormatterMap[axisType]?.reduce((pre, next) => {
        pre[next.id] = next
        return pre
      }, {})
      const textBaseline =
        this.getLeftType() === 'line' ? 'bottom' : axisType === 'yAxis' ? 'top' : 'bottom'
      tempLabel.style.fill = DEFAULT_LABEL.color
      const label = {
        fields: [],
        ...tempLabel,
        formatter: (data: Datum) => {
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
          // 返回 G Canvas 文本组，可以让不同系列使用独立字号、字体和颜色。
          group.addShape({
            type: 'text',
            attrs: {
              x: 0,
              y: 0,
              text: value,
              textAlign: 'start',
              textBaseline,
              fontSize: labelCfg.fontSize,
              fontFamily: chart.fontFamily,
              fill: labelCfg.color
            }
          })
          return group
        }
      }
      if (tmpOption.geometryOptions) {
        if (axisType === 'yAxis') {
          tmpOption.geometryOptions[0].label = label
        } else if (axisType === 'yAxisExt') {
          tmpOption.geometryOptions[1].label = label
        }
      }
    })

    return tmpOption
  }

  // 基础样式同时覆盖柱体圆角、柱宽、左右线条点样式和折线平滑度。
  protected configBasicStyle(chart: Chart, options: any): any {
    const customAttr: DeepPartial<ChartAttr> = parseJson(chart.customAttr)
    const s = defaultsDeep(
      JSON.parse(JSON.stringify(customAttr.basicStyle)),
      CHART_MIX_DEFAULT_BASIC_STYLE
    )
    const smooth = s.lineSmooth
    const point = {
      size: s.lineSymbolSize,
      shape: s.lineSymbol,
      style: {
        stroke: hexColorToRGBA('#FFFFFF', s.subAlpha)
      }
    }
    const lineStyle = {
      lineWidth: s.lineWidth
    }
    const leftSmooth = s.leftLineSmooth
    const leftPoint = {
      size: s.leftLineSymbolSize,
      shape: s.leftLineSymbol,
      style: {
        stroke: hexColorToRGBA('#FFFFFF', s.alpha)
      }
    }
    const leftLineStyle = {
      lineWidth: s.leftLineWidth
    }
    const tempOption = {
      ...options,
      smooth,
      point,
      lineStyle
    }
    if (tempOption.geometryOptions) {
      tempOption.geometryOptions[0].smooth = leftSmooth
      tempOption.geometryOptions[0].point = leftPoint
      tempOption.geometryOptions[0].lineStyle = leftLineStyle

      tempOption.geometryOptions[1].smooth = smooth
      tempOption.geometryOptions[1].point = point
      tempOption.geometryOptions[1].lineStyle = lineStyle
      tempOption.geometryOptions[0] = {
        ...tempOption.geometryOptions[0],
        ...configRoundAngle(chart, 'columnStyle')
      }
    }

    let columnWidthRatio
    const _v = s.columnWidthRatio ?? DEFAULT_BASIC_STYLE.columnWidthRatio
    // 配置面板按百分比保存柱宽，G2Plot 需要 0 到 1 的比例值。
    if (_v >= 1 && _v <= 100) {
      columnWidthRatio = _v / 100.0
    } else if (_v < 1) {
      columnWidthRatio = 1 / 100.0
    } else if (_v > 100) {
      columnWidthRatio = 1
    }
    if (columnWidthRatio) {
      tempOption.geometryOptions[0].columnWidthRatio = columnWidthRatio
    }

    if (this.name !== 'chart-mix-dual-line') {
      // 双线图没有柱体，柱体 padding 只对包含柱形 geometry 的混合图生效。
      tempOption.geometryOptions[0].appendPadding = getPadding(chart)
    }

    return tempOption
  }

  // 混合图默认用断线策略处理空值，避免忽略空点后左右轴类目对齐发生偏移。
  setupDefaultOptions(chart: ChartObj): ChartObj {
    const { senior } = chart
    if (
      senior.functionCfg.emptyDataStrategy == undefined ||
      senior.functionCfg.emptyDataStrategy === 'ignoreData'
    ) {
      senior.functionCfg.emptyDataStrategy = 'breakLine'
    }
    return chart
  }

  // 左轴系列色先吸收用户的单系列配色，再按透明度和渐变设置转换为渲染颜色。
  protected configCustomColors(chart: Chart, options: any): any {
    const tempOption = {
      ...options
    }
    const basicStyle = parseJson(chart.customAttr).basicStyle as MixChartBasicStyle

    const { seriesColor } = basicStyle
    if (seriesColor?.length) {
      const seriesMap = seriesColor.reduce((p, n) => {
        p[n.id] = n
        return p
      }, {})
      const { yAxis } = chart
      yAxis?.forEach((axis, index) => {
        const curAxisColor = seriesMap[axis.id]
        if (curAxisColor) {
          if (index + 1 > basicStyle.colors.length) {
            basicStyle.colors.push(curAxisColor.color)
          } else {
            basicStyle.colors[index] = curAxisColor.color
          }
        }
      })
    }
    // 左轴颜色应用到第一套 geometry，柱体可按主题配置开启纵向渐变。
    const color = basicStyle.colors.map(ele => {
      const tmp = hexColorToRGBA(ele, basicStyle.alpha)
      if (basicStyle.gradient) {
        return setGradientColor(tmp, true, 270)
      } else {
        return tmp
      }
    })
    tempOption.geometryOptions[0].color = color

    return tempOption
  }

  // 右轴系列色按右轴分类或右轴指标生成，保证右轴线条和 tooltip 使用同一颜色来源。
  protected configSubCustomColors(chart: Chart, options: any): any {
    const tempOption = {
      ...options
    }
    const basicStyle = defaultsDeep(
      parseJson(chart.customAttr).basicStyle as MixChartBasicStyle,
      cloneDeep(CHART_MIX_DEFAULT_BASIC_STYLE)
    )
    // 右轴分类存在时按分类配色，否则按右轴指标配色。
    const { subSeriesColor } = basicStyle
    if (subSeriesColor?.length) {
      const { yAxisExt, extBubble } = chart
      const seriesMap = subSeriesColor.reduce((p, n) => {
        p[n.id] = n
        return p
      }, {})
      const { data } = options as unknown as Options
      if (extBubble?.length) {
        const seriesSet = new Set()
        data[1]?.forEach(d => d.category !== null && seriesSet.add(d.category))
        const tmp = [...seriesSet]
        tmp.forEach((c, i) => {
          const curAxisColor = seriesMap[c as string]
          if (curAxisColor) {
            if (i + 1 > basicStyle.subColors.length) {
              basicStyle.subColors.push(curAxisColor.color)
            } else {
              basicStyle.subColors[i] = curAxisColor.color
            }
          }
        })
      } else {
        yAxisExt?.forEach((axis, index) => {
          const curAxisColor = seriesMap[axis.id]
          if (curAxisColor) {
            if (index + 1 > basicStyle.subColors.length) {
              basicStyle.subColors.push(curAxisColor.color)
            } else {
              basicStyle.subColors[index] = curAxisColor.color
            }
          }
        })
      }
    }
    const subColor = basicStyle.subColors.map(c => {
      const cc = hexColorToRGBA(c, basicStyle.subAlpha)
      return cc
    })
    tempOption.geometryOptions[1].color = subColor

    return tempOption
  }

  // 右轴系列色初始化时优先按右轴分类生成，没有分类时按右轴指标生成。
  public setupSubSeriesColor(chart: ChartObj, data?: any[]): ChartBasicStyle['seriesColor'] {
    const result: ChartBasicStyle['seriesColor'] = []
    const seriesSet = new Set<string>()
    const colors = chart.customAttr.basicStyle.subColors ?? CHART_MIX_DEFAULT_BASIC_STYLE.subColors
    const { yAxisExt, extBubble } = chart
    if (extBubble?.length) {
      data?.forEach(d => {
        if (d.value === null || d.category === null || seriesSet.has(d.category)) {
          return
        }
        seriesSet.add(d.category)
        result.push({
          id: d.category,
          name: d.category,
          color: colors[(seriesSet.size - 1) % colors.length]
        })
      })
    } else {
      yAxisExt?.forEach(axis => {
        if (seriesSet.has(axis.id)) {
          return
        }
        seriesSet.add(axis.id)
        result.push({
          id: axis.id,
          name: axis.chartShowName ?? axis.name,
          color: colors[(seriesSet.size - 1) % colors.length]
        })
      })
    }
    return result
  }

  // 左右 Y 轴分别绑定 value 和 valueExt，并独立应用固定刻度、格式化和文字对齐。
  protected configYAxis(chart: Chart, options: any): any {
    const yAxis = getYAxis(chart)
    const yAxisExt = getYAxisExt(chart)

    const tempOption = {
      ...options
    }

    tempOption.yAxis = {}
    if (!yAxis) {
      // 左轴无配置时隐藏 value 轴，避免 G2Plot 使用默认坐标轴。
      tempOption.yAxis.value = false
    } else {
      tempOption.yAxis.value = undefined
      yAxis.position = 'left'

      const yAxisTmp = parseJson(chart.customStyle).yAxis
      if (yAxis.label) {
        yAxis.label.style.textAlign = 'end'
        yAxis.label.formatter = value => {
          return valueFormatter(value, yAxisTmp.axisLabelFormatter)
        }
      }
      const axisValue = yAxisTmp.axisValue
      if (!axisValue?.auto) {
        tempOption.yAxis.value = {
          ...yAxis,
          min: axisValue.min,
          max: axisValue.max,
          minLimit: axisValue.min,
          maxLimit: axisValue.max,
          tickCount: axisValue.splitCount
        }
      } else {
        tempOption.yAxis.value = yAxis
      }
    }

    if (!yAxisExt) {
      // 右轴无配置时隐藏 valueExt 轴，避免空右轴占用画布空间。
      tempOption.yAxis.valueExt = false
    } else {
      tempOption.yAxis.valueExt = undefined
      yAxisExt.position = 'right'

      const yAxisExtTmp = parseJson(chart.customStyle).yAxisExt
      if (yAxisExt.label) {
        yAxisExt.label.style.textAlign = 'start'
        yAxisExt.label.formatter = value => {
          return valueFormatter(value, yAxisExtTmp.axisLabelFormatter)
        }
      }
      const axisExtValue = yAxisExtTmp.axisValue
      if (!axisExtValue?.auto) {
        tempOption.yAxis.valueExt = {
          ...yAxisExt,
          min: axisExtValue.min,
          max: axisExtValue.max,
          minLimit: axisExtValue.min,
          maxLimit: axisExtValue.max,
          tickCount: axisExtValue.splitCount
        }
      } else {
        tempOption.yAxis.valueExt = yAxisExt
      }
    }

    return tempOption
  }

  // tooltip 支持按系列格式化，并把后端动态 tooltip 指标追加到同一浮层。
  protected configTooltip(chart: Chart, options: any): any {
    const customAttr: DeepPartial<ChartAttr> = parseJson(chart.customAttr)
    const tooltipAttr = customAttr.tooltip
    if (!tooltipAttr.show) {
      return {
        ...options,
        tooltip: false
      }
    }
    const formatterMap = tooltipAttr.seriesTooltipFormatter
      ?.filter(i => i.show)
      .reduce((pre, next) => {
        pre[next.id] = next
        return pre
      }, {}) as Record<string, SeriesFormatter>
    const tooltip: DualAxesOptions['tooltip'] = {
      shared: true,
      showTitle: true,
      customItems(originalItems) {
        if (!tooltipAttr.seriesTooltipFormatter?.length) {
          return originalItems
        }
        const head = originalItems[0]
        // 非原始数据没有 quotaList，保持 G2Plot 原始 tooltip，避免访问空字段。
        if (!head.data.quotaList) {
          return originalItems
        }
        const result = []
        originalItems
          .filter(item => formatterMap[item.data.quotaList[0].id])
          .forEach(item => {
            const formatter = formatterMap[item.data.quotaList[0].id]
            const value = valueFormatter(parseFloat(item.value as string), formatter.formatterCfg)
            const name = item.data.category

            result.push({ ...item, name, value })
          })
        head.data.dynamicTooltipValue?.forEach(item => {
          const formatter = formatterMap[item.fieldId]
          if (formatter) {
            const value = valueFormatter(parseFloat(item.value), formatter.formatterCfg)
            const name = isEmpty(formatter.chartShowName) ? formatter.name : formatter.chartShowName
            result.push({ color: 'grey', name, value })
          }
        })
        return result
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

  // 图例需要把左右轴 value/valueExt 映射回真实指标名，并处理重复图例 id。
  protected configLegend(chart: Chart, options: any): any {
    const o = super.configLegend(chart, options)
    if (o.legend) {
      const left = cloneDeep(chart.data?.left?.data)
      const right = cloneDeep(chart.data?.right?.data)

      o.legend.itemName.formatter = (text: string, item: any, index: number) => {
        let name = undefined
        if (item.viewId === 'left-axes-view' && text === 'value') {
          name = left[0]?.categories[0]
        } else if (item.viewId === 'right-axes-view' && text === 'valueExt') {
          name = right[0]?.categories[0]
        }
        // 左右轴可能生成相同图例 id，追加序号可避免 G2Plot 图例过滤互相串扰。
        item.id = item.id + '__' + index
        if (name === undefined) {
          return text
        } else {
          return name
        }
      }

      const customStyle = parseJson(chart.customStyle)
      let size
      if (customStyle && customStyle.legend) {
        size = defaults(JSON.parse(JSON.stringify(customStyle.legend)), DEFAULT_LEGEND_STYLE).size
      } else {
        size = DEFAULT_LEGEND_STYLE.size
      }

      o.legend.marker.style = style => {
        const fill = style.fill ?? style.stroke
        return {
          r: size,
          fill
        }
      }
    }
    return o
  }

  // 辅助线按左右轴拆分到不同 yField，保证左轴和右轴的阈值线落在正确坐标系。
  protected configAnalyse(chart: Chart, options: any): any {
    chart.data.dynamicAssistLines = union(
      defaultTo(chart.data?.left?.dynamicAssistLines, []),
      defaultTo(chart.data?.right?.dynamicAssistLines, [])
    )
    const list = getAnalyse(chart)
    const annotations = {
      value: filter(list, l => l.yAxisType === 'left'),
      valueExt: filter(list, l => l.yAxisType === 'right')
    }
    return { ...options, annotations }
  }

  // 混合图配置管线先处理视觉和交互，再绑定轴和辅助线，最后应用空值策略。
  protected setupOptions(chart: Chart, options: any): any {
    return flow(
      this.configTheme,
      this.configLabel,
      this.configTooltip,
      this.configBasicStyle,
      this.configCustomColors,
      this.configSubCustomColors,
      this.configLegend,
      this.configXAxis,
      this.configYAxis,
      this.configAnalyse,
      this.configEmptyDataStrategy
    )(chart, options, {}, this)
  }

  constructor(name = 'chart-mix') {
    super(name, DEFAULT_DATA)
  }
}

export class GroupColumnLineMix extends ColumnLineMix {
  // 分组混合图通过左轴扩展维度拆分柱形系列，右轴仍沿用父类的线形度量。
  axis: AxisType[] = [...this['axis'], 'xAxisExt']
  propertyInner = {
    ...CHART_MIX_EDITOR_PROPERTY_INNER,
    'label-selector': ['vPosition', 'seriesLabelFormatter'],
    'tooltip-selector': [
      ...CHART_MIX_EDITOR_PROPERTY_INNER['tooltip-selector'],
      'seriesTooltipFormatter',
      'carousel'
    ]
  }
  axisConfig = {
    ...this['axisConfig'],
    xAxisExt: {
      name: `${t('chart.chart_group')} / ${t('chart.dimension')}`,
      type: 'd',
      limit: 1,
      allowEmpty: true
    }
  }

  protected configCustomColors(chart: Chart, options: any): any {
    const tempOption = {
      ...options
    }
    const basicStyle = parseJson(chart.customAttr).basicStyle as MixChartBasicStyle

    const { seriesColor } = basicStyle
    if (seriesColor?.length) {
      const seriesMap = seriesColor.reduce((p, n) => {
        p[n.id] = n
        return p
      }, {})
      const { yAxis, xAxisExt } = chart
      const { data } = options as unknown as Options
      if (xAxisExt?.length) {
        const seriesSet = new Set()
        data[0]?.forEach(d => d.category !== null && seriesSet.add(d.category))
        const tmp = [...seriesSet]
        tmp.forEach((c, i) => {
          const curAxisColor = seriesMap[c as string]
          if (curAxisColor) {
            if (i + 1 > basicStyle.colors.length) {
              basicStyle.colors.push(curAxisColor.color)
            } else {
              basicStyle.colors[i] = curAxisColor.color
            }
          }
        })
      } else {
        yAxis?.forEach((axis, index) => {
          const curAxisColor = seriesMap[axis.id]
          if (curAxisColor) {
            if (index + 1 > basicStyle.colors.length) {
              basicStyle.colors.push(curAxisColor.color)
            } else {
              basicStyle.colors[index] = curAxisColor.color
            }
          }
        })
      }
    }
    // 左轴颜色优先按分组维度分配，缺少分组时退回按左轴指标分配。
    const color = basicStyle.colors.map(ele => {
      const tmp = hexColorToRGBA(ele, basicStyle.alpha)
      if (basicStyle.gradient) {
        return setGradientColor(tmp, true, 270)
      } else {
        return tmp
      }
    })
    tempOption.geometryOptions[0].color = color

    return tempOption
  }

  public setupSeriesColor(chart: ChartObj, data?: any[]): ChartBasicStyle['seriesColor'] {
    const result: ChartBasicStyle['seriesColor'] = []
    const seriesSet = new Set<string>()
    const colors = chart.customAttr.basicStyle.colors
    const { yAxis, xAxisExt } = chart
    if (xAxisExt?.length) {
      // 存在分组字段时，系列来自查询结果中的 category，而不是指标字段本身。
      data?.forEach(d => {
        if (d.value === null || d.category === null || seriesSet.has(d.category)) {
          return
        }
        seriesSet.add(d.category)
        result.push({
          id: d.category,
          name: d.category,
          color: colors[(seriesSet.size - 1) % colors.length]
        })
      })
    } else {
      // 未配置分组字段时，系列列表仍按左轴指标生成，保持普通混合图语义。
      yAxis?.forEach(axis => {
        if (seriesSet.has(axis.id)) {
          return
        }
        seriesSet.add(axis.id)
        result.push({
          id: axis.id,
          name: axis.chartShowName ?? axis.name,
          color: colors[(seriesSet.size - 1) % colors.length]
        })
      })
    }
    return result
  }

  constructor(name = 'chart-mix-group') {
    super(name)
  }
}
export class StackColumnLineMix extends ColumnLineMix {
  // 堆叠混合图额外启用堆叠维度，父类会在绘制阶段把柱体切换为 stack 模式。
  axis: AxisType[] = [...this['axis'], 'extStack']
  propertyInner = {
    ...CHART_MIX_EDITOR_PROPERTY_INNER,
    'label-selector': ['vPosition', 'seriesLabelFormatter'],
    'tooltip-selector': [
      ...CHART_MIX_EDITOR_PROPERTY_INNER['tooltip-selector'],
      'seriesTooltipFormatter',
      'carousel'
    ]
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

  protected configCustomColors(chart: Chart, options: any): any {
    const tempOption = {
      ...options
    }
    const basicStyle = parseJson(chart.customAttr).basicStyle as MixChartBasicStyle

    const { seriesColor } = basicStyle
    if (seriesColor?.length) {
      const seriesMap = seriesColor.reduce((p, n) => {
        p[n.id] = n
        return p
      }, {})
      const { yAxis, extStack } = chart
      const { data } = options as unknown as Options
      if (extStack?.length) {
        const seriesSet = new Set()
        data[0]?.forEach(d => d.category !== null && seriesSet.add(d.category))
        const tmp = [...seriesSet]
        tmp.forEach((c, i) => {
          const curAxisColor = seriesMap[c as string]
          if (curAxisColor) {
            if (i + 1 > basicStyle.colors.length) {
              basicStyle.colors.push(curAxisColor.color)
            } else {
              basicStyle.colors[i] = curAxisColor.color
            }
          }
        })
      } else {
        yAxis?.forEach((axis, index) => {
          const curAxisColor = seriesMap[axis.id]
          if (curAxisColor) {
            if (index + 1 > basicStyle.colors.length) {
              basicStyle.colors.push(curAxisColor.color)
            } else {
              basicStyle.colors[index] = curAxisColor.color
            }
          }
        })
      }
    }
    // 左轴颜色优先按堆叠项分配，缺少堆叠字段时退回按左轴指标分配。
    const color = basicStyle.colors.map(ele => {
      const tmp = hexColorToRGBA(ele, basicStyle.alpha)
      if (basicStyle.gradient) {
        return setGradientColor(tmp, true, 270)
      } else {
        return tmp
      }
    })
    tempOption.geometryOptions[0].color = color

    return tempOption
  }

  public setupSeriesColor(chart: ChartObj, data?: any[]): ChartBasicStyle['seriesColor'] {
    const result: ChartBasicStyle['seriesColor'] = []
    const seriesSet = new Set<string>()
    const colors = chart.customAttr.basicStyle.colors
    const { yAxis, extStack } = chart
    if (extStack?.length) {
      // 堆叠图的系列来源是堆叠维度值，需要从结果数据中去重生成。
      data?.forEach(d => {
        if (d.value === null || d.category === null || seriesSet.has(d.category)) {
          return
        }
        seriesSet.add(d.category)
        result.push({
          id: d.category,
          name: d.category,
          color: colors[(seriesSet.size - 1) % colors.length]
        })
      })
    } else {
      // 无堆叠字段时保持按左轴指标生成系列色，避免空堆叠配置改变展示。
      yAxis?.forEach(axis => {
        if (seriesSet.has(axis.id)) {
          return
        }
        seriesSet.add(axis.id)
        result.push({
          id: axis.id,
          name: axis.chartShowName ?? axis.name,
          color: colors[(seriesSet.size - 1) % colors.length]
        })
      })
    }
    return result
  }

  constructor(name = 'chart-mix-stack') {
    super(name)
  }
}

export class DualLineMix extends ColumnLineMix {
  // 双线图把左侧 geometry 从柱形切换为折线，左右轴都以线形系列呈现。
  axis: AxisType[] = [...this['axis'], 'xAxisExt']
  propertyInner = {
    ...CHART_MIX_EDITOR_PROPERTY_INNER,
    'label-selector': ['seriesLabelFormatter'],
    'tooltip-selector': [
      ...CHART_MIX_EDITOR_PROPERTY_INNER['tooltip-selector'],
      'seriesTooltipFormatter',
      'carousel'
    ]
  }
  axisConfig = {
    ...this['axisConfig'],
    xAxisExt: {
      name: `${t('chart.drag_block_type_axis_left')} / ${t('chart.dimension')}`,
      type: 'd',
      limit: 1,
      allowEmpty: true
    }
  }

  protected getLeftType(): string {
    return 'line'
  }

  protected configCustomColors(chart: Chart, options: any): any {
    const tempOption = {
      ...options
    }
    const basicStyle = parseJson(chart.customAttr).basicStyle as MixChartBasicStyle

    const { seriesColor } = basicStyle
    if (seriesColor?.length) {
      const seriesMap = seriesColor.reduce((p, n) => {
        p[n.id] = n
        return p
      }, {})
      const { yAxis, xAxisExt } = chart
      const { data } = options as unknown as Options
      if (xAxisExt?.length) {
        const seriesSet = new Set()
        data[0]?.forEach(d => d.category !== null && seriesSet.add(d.category))
        const tmp = [...seriesSet]
        tmp.forEach((c, i) => {
          const curAxisColor = seriesMap[c as string]
          if (curAxisColor) {
            if (i + 1 > basicStyle.colors.length) {
              basicStyle.colors.push(curAxisColor.color)
            } else {
              basicStyle.colors[i] = curAxisColor.color
            }
          }
        })
      } else {
        yAxis?.forEach((axis, index) => {
          const curAxisColor = seriesMap[axis.id]
          if (curAxisColor) {
            if (index + 1 > basicStyle.colors.length) {
              basicStyle.colors.push(curAxisColor.color)
            } else {
              basicStyle.colors[index] = curAxisColor.color
            }
          }
        })
      }
    }
    // 左轴折线颜色优先按扩展维度分配，缺少扩展维度时退回按左轴指标分配。
    const color = basicStyle.colors.map(ele => {
      const tmp = hexColorToRGBA(ele, basicStyle.alpha)
      if (basicStyle.gradient) {
        return setGradientColor(tmp, true, 270)
      } else {
        return tmp
      }
    })
    tempOption.geometryOptions[0].color = color

    return tempOption
  }

  public setupSeriesColor(chart: ChartObj, data?: any[]): ChartBasicStyle['seriesColor'] {
    const result: ChartBasicStyle['seriesColor'] = []
    const seriesSet = new Set<string>()
    const colors = chart.customAttr.basicStyle.colors
    const { yAxis, xAxisExt } = chart
    if (xAxisExt?.length) {
      // 双线图左轴存在分类扩展时，按分类值生成系列色以匹配折线图例。
      data?.forEach(d => {
        if (d.value === null || d.category === null || seriesSet.has(d.category)) {
          return
        }
        seriesSet.add(d.category)
        result.push({
          id: d.category,
          name: d.category,
          color: colors[(seriesSet.size - 1) % colors.length]
        })
      })
    } else {
      // 未配置分类扩展时按左轴指标生成系列色，与普通折线图保持一致。
      yAxis?.forEach(axis => {
        if (seriesSet.has(axis.id)) {
          return
        }
        seriesSet.add(axis.id)
        result.push({
          id: axis.id,
          name: axis.chartShowName ?? axis.name,
          color: colors[(seriesSet.size - 1) % colors.length]
        })
      })
    }
    return result
  }

  constructor(name = 'chart-mix-dual-line') {
    super(name)
  }
}
