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
  getTooltipContainer,
  setGradientColor,
  TOOLTIP_TPL
} from '@/views/chart/components/js/panel/common/common_antv'
import { cloneDeep, find } from 'lodash-es'
import { flow, hexColorToRGBA, parseJson } from '@/views/chart/components/js/util'
import { valueFormatter } from '@/views/chart/components/js/formatter'
import {
  BAR_AXIS_TYPE,
  BAR_RANGE_EDITOR_PROPERTY,
  BAR_EDITOR_PROPERTY_INNER
} from '@/views/chart/components/js/panel/charts/bar/common'
import { Datum } from '@antv/g2plot/esm/types/common'
import { useI18n } from '@/hooks/web/useI18n'
import { DEFAULT_BASIC_STYLE } from '@/views/chart/components/editor/util/chart'
import { Group } from '@antv/g-canvas'

const { t } = useI18n()
const DEFAULT_DATA = []

/**
 * 区间条形图渲染器，支持数值区间和时间区间两种范围展示。
 */
export class RangeBar extends G2PlotChartView<BarOptions, Bar> {
  // 区间条形图使用一个分类维度和两个起止值字段。
  axisConfig = {
    xAxis: {
      name: `${t('chart.drag_block_type_axis')} / ${t('chart.dimension')}`,
      type: 'd'
    },
    yAxis: {
      name: `${t('chart.drag_block_value_start')} / ${t('chart.time_dimension_or_quota')}`,
      limit: 1,
      type: 'q'
    },
    yAxisExt: {
      name: `${t('chart.drag_block_value_end')} / ${t('chart.time_dimension_or_quota')}`,
      limit: 1,
      type: 'q'
    }
  }
  properties = BAR_RANGE_EDITOR_PROPERTY.filter(p => p !== 'threshold')
  // 区间图只开放范围标签、范围 tooltip 和坐标轴格式化相关配置。
  propertyInner = {
    ...BAR_EDITOR_PROPERTY_INNER,
    'label-selector': ['hPosition', 'color', 'fontSize', 'labelFormatter', 'showGap'],
    'tooltip-selector': [
      'fontSize',
      'color',
      'backgroundColor',
      'tooltipFormatter',
      'showGap',
      'show'
    ],
    'x-axis-selector': [...BAR_EDITOR_PROPERTY_INNER['x-axis-selector'], 'axisLabelFormatter'],
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
  axis: AxisType[] = [...BAR_AXIS_TYPE, 'yAxisExt']
  // G2Plot range bar 使用 values 数组作为区间字段。
  protected baseOptions: any = {
    data: [],
    xField: 'values',
    yField: 'field',
    colorField: 'category',
    isGroup: true
  }

  async drawChart(drawOptions: G2PlotDrawOptions<Bar>): Promise<Bar> {
    const { chart, container, action } = drawOptions
    if (!chart.data?.data?.length) {
      return
    }
    // 克隆数据后补充临时唯一键，避免相同区间值导致 G2Plot 元数据合并。
    const data: Array<any> = cloneDeep(chart.data.data)

    data.forEach(d => {
      d.tempId = (Math.random() * 10000000).toString()
    })

    const ifAggregate = !!chart.aggregate

    // 日期区间和数值区间在 meta、分组模式和格式化方式上不同。
    const isDate = !!chart.data.isDate

    const axis = chart.yAxis ?? chart.yAxisExt ?? []
    let dateFormat: string
    const dateSplit = axis[0]?.datePattern === 'date_split' ? '/' : '-'
    // 时间轴 mask 根据字段日期粒度生成，保证坐标轴与数据字段格式一致。
    switch (axis[0]?.dateStyle) {
      case 'y':
        dateFormat = 'YYYY'
        break
      case 'y_M':
        dateFormat = 'YYYY' + dateSplit + 'MM'
        break
      case 'y_M_d':
        dateFormat = 'YYYY' + dateSplit + 'MM' + dateSplit + 'DD'
        break
      // case 'H_m_s':
      //   dateFormat = 'HH:mm:ss'
      //   break
      case 'y_M_d_H':
        dateFormat = 'YYYY' + dateSplit + 'MM' + dateSplit + 'DD' + ' HH'
        break
      case 'y_M_d_H_m':
        dateFormat = 'YYYY' + dateSplit + 'MM' + dateSplit + 'DD' + ' HH:mm'
        break
      case 'y_M_d_H_m_s':
        dateFormat = 'YYYY' + dateSplit + 'MM' + dateSplit + 'DD' + ' HH:mm:ss'
        break
      default:
        dateFormat = 'YYYY-MM-dd HH:mm:ss'
    }

    const minTime = chart.data.minTime
    const maxTime = chart.data.maxTime

    const minNumber = chart.data.min
    const maxNumber = chart.data.max

    // 日期明细模式按分类堆叠展示，聚合模式和数值模式使用普通区间条。
    const initOptions: BarOptions = {
      ...this.baseOptions,
      appendPadding: getPadding(chart),
      data,
      seriesField: isDate ? (ifAggregate ? 'category' : undefined) : 'category',
      isGroup: isDate ? !ifAggregate : false,
      isStack: isDate ? !ifAggregate : false,
      meta: (isDate
        ? {
            values: {
              type: 'time',
              min: minTime,
              max: maxTime,
              mask: dateFormat
            },
            tempId: {
              key: true
            }
          }
        : {
            values: {
              min: minNumber,
              max: maxNumber,
              mask: dateFormat
            },
            tempId: {
              key: true
            }
          }) as any
    }

    const options = this.setupOptions(chart, initOptions)

    const { Bar: BarClass } = await import('@antv/g2plot/esm/plots/bar')
    // 创建图表实例后绑定条形和标签点击，保证联动能拿到原始区间数据。
    const newChart = new BarClass(container, options)

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
    const isDate = !!chart.data.isDate
    if (tmpOptions.xAxis.label) {
      // 日期轴由 G2Plot time mask 处理，数值轴才使用自定义数值格式化。
      tmpOptions.xAxis.label.formatter = value => {
        if (isDate) {
          return value
        }
        return valueFormatter(value, xAxis.axisLabelFormatter)
      }
    }
    if (tmpOptions.xAxis.position === 'top') {
      // 横向区间条形图的数值轴位置需要从上下转换为左右。
      tmpOptions.xAxis.position = 'left'
    }
    if (tmpOptions.xAxis.position === 'bottom') {
      // 编辑器底部位置对应横向区间条形图的右侧轴。
      tmpOptions.xAxis.position = 'right'
    }
    if (!axisValue?.auto) {
      // 固定数值轴范围时写入 min/max/limit，避免自动范围覆盖用户配置。
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
      return { ...tmpOptions, ...axis }
    }
    return tmpOptions
  }

  protected configTooltip(chart: Chart, options: BarOptions): BarOptions {
    const isDate = !!chart.data.isDate
    let tooltip
    let customAttr: DeepPartial<ChartAttr>
    if (chart.customAttr) {
      customAttr = parseJson(chart.customAttr)
      // tooltip 支持展示起止值，也可以按配置展示区间差值。
      if (customAttr.tooltip) {
        const t = JSON.parse(JSON.stringify(customAttr.tooltip))
        if (t.show) {
          tooltip = {
            fields: ['values', 'field', 'gap'],
            formatter: function (param: Datum) {
              let res
              if (isDate) {
                // 日期区间已由后端按粒度格式化，直接拼接展示。
                res = param.values[0] + ' ~ ' + param.values[1]
                if (t.showGap) {
                  res = res + ' (' + param.gap + ')'
                }
              } else {
                // 数值区间复用 tooltip 数值格式化配置。
                res =
                  valueFormatter(param.values[0], t.tooltipFormatter) +
                  ' ~ ' +
                  valueFormatter(param.values[1], t.tooltipFormatter)
                if (t.showGap) {
                  res = res + ' (' + valueFormatter(param.gap, t.tooltipFormatter) + ')'
                }
              }
              return { value: res, values: param.values, name: param.field }
            },
            container: getTooltipContainer(`tooltip-${chart.id}`, chart.container),
            itemTpl: TOOLTIP_TPL,
            enterable: true
          }
        } else {
          tooltip = false
        }
      }
    }
    return { ...options, tooltip }
  }

  protected configBasicStyle(chart: Chart, options: BarOptions): BarOptions {
    const isDate = !!chart.data.isDate
    const ifAggregate = !!chart.aggregate
    const basicStyle = parseJson(chart.customAttr).basicStyle

    if (isDate && !ifAggregate) {
      // 日期明细模式按分类字段生成颜色，避免同一分类下多段区间颜色不一致。
      const customColors = []
      const groups = []
      for (let i = 0; i < chart.data.data.length; i++) {
        const name = chart.data.data[i].field
        if (groups.indexOf(name) < 0) {
          groups.push(name)
        }
      }
      for (let i = 0; i < groups.length; i++) {
        const s = groups[i]
        customColors.push({
          name: s,
          color: basicStyle.colors[i % basicStyle.colors.length],
          isCustom: false
        })
      }
      // 颜色函数按当前条形所属分类返回透明度和渐变后的颜色。
      const color = obj => {
        const colorObj = find(customColors, o => {
          return o.name === obj.field
        })
        if (colorObj === undefined) {
          return undefined
        }
        const color = hexColorToRGBA(colorObj.color, basicStyle.alpha)
        if (basicStyle.gradient) {
          return setGradientColor(color, true)
        } else {
          return color
        }
      }

      options = {
        ...options,
        color
      }
    } else {
      if (basicStyle.gradient) {
        // 聚合和数值模式使用基础配色数组，并按透明度转换为渐变色。
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
    }

    options = {
      ...options,
      ...configRoundAngle(chart, 'barStyle')
    }
    let barWidthRatio
    const _v = basicStyle.columnWidthRatio ?? DEFAULT_BASIC_STYLE.columnWidthRatio
    // 编辑器按百分比维护柱宽，渲染层转换为 G2Plot 的 0 到 1 比例。
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
    // 非法标签位置回退到中间，保证区间图首次加载可见且稳定。
    if (!['left', 'middle', 'right'].includes(label.position)) {
      label.position = 'middle'
    }
    // 区间图默认忽略空数据，避免缺失起止值生成异常区间。
    senior.functionCfg.emptyDataStrategy = 'ignoreData'
    return chart
  }

  protected configLabel(chart: Chart, options: BarOptions): BarOptions {
    const isDate = !!chart.data.isDate
    const ifAggregate = !!chart.aggregate

    const tmpOptions = super.configLabel(chart, options)
    if (!tmpOptions.label) {
      return {
        ...tmpOptions,
        label: false
      }
    }
    const labelAttr = parseJson(chart.customAttr).label

    if (isDate && !ifAggregate) {
      // 日期明细模式区间可能密集，启用防重叠和绘图区裁剪。
      if (!tmpOptions.label.layout) {
        ;(tmpOptions.label as any).layout = []
      }
      ;(tmpOptions.label.layout as any[]).push({ type: 'interval-hide-overlap' })
      ;(tmpOptions.label.layout as any[]).push({ type: 'limit-in-plot', cfg: { action: 'hide' } })
    }

    const label = {
      fields: [],
      ...tmpOptions.label,
      formatter: (param: Datum) => {
        let res
        if (isDate) {
          // 日期标签可在完整起止范围和区间差值之间切换。
          if (labelAttr.showGap) {
            res = param.gap
          } else {
            res = param.values[0] + ' ~ ' + param.values[1]
          }
        } else {
          // 数值标签复用标签格式化配置，确保起止值和差值显示一致。
          if (labelAttr.showGap) {
            res = valueFormatter(param.gap, labelAttr.labelFormatter)
          } else {
            res =
              valueFormatter(param.values[0], labelAttr.labelFormatter) +
              ' ~ ' +
              valueFormatter(param.values[1], labelAttr.labelFormatter)
          }
        }
        const group = new Group({})
        group.addShape({
          type: 'text',
          attrs: {
            x: 0,
            y: 0,
            data: param,
            text: res,
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
      // 横向区间条形图的分类轴位置需要从左右语义转换为上下语义。
      tmpOptions.yAxis.position = 'bottom'
    }
    if (tmpOptions.yAxis.position === 'right') {
      // 编辑器右侧位置对应 G2Plot 横向条形图的顶部分类轴。
      tmpOptions.yAxis.position = 'top'
    }
    return tmpOptions
  }

  protected setupOptions(chart: Chart, options: BarOptions): BarOptions {
    // 配置链按主题、基础样式、标签、提示、图例和坐标轴顺序叠加。
    return flow(
      this.configTheme,
      this.configBasicStyle,
      this.configLabel,
      this.configTooltip,
      this.configLegend,
      this.configXAxis,
      this.configYAxis,
      this.configSlider,
      this.configEmptyDataStrategy
    )(chart, options)
  }

  constructor(name = 'bar-range') {
    super(name, DEFAULT_DATA)
  }
}
