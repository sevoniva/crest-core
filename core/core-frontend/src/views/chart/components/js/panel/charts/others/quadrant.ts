import {
  G2PlotChartView,
  G2PlotDrawOptions
} from '@/views/chart/components/js/panel/types/impl/g2plot'
import type { ScatterOptions, Scatter as G2Scatter } from '@antv/g2plot/esm/plots/scatter'
import { flow, parseJson, setUpSingleDimensionSeriesColor } from '../../../util'
import { valueFormatter } from '@/views/chart/components/js/formatter'
import { useI18n } from '@/hooks/web/useI18n'
import { defaults, isEmpty, map } from 'lodash-es'
import { cloneDeep, defaultTo } from 'lodash-es'
import {
  configAxisLabelLengthLimit,
  configPlotTooltipEvent,
  configYaxisTitleLengthLimit,
  getTooltipContainer,
  TOOLTIP_TPL,
  getPadding
} from '../../common/common_antv'
import { DEFAULT_LEGEND_STYLE } from '@/views/chart/components/editor/util/chart'

const { t } = useI18n()
/**
 * 象限图渲染器，将三个度量序列合成为散点坐标、气泡大小和象限基准线。
 */
export class Quadrant extends G2PlotChartView<ScatterOptions, G2Scatter> {
  // 暴露象限图支持的编辑面板，确保通用图表能力和象限专属配置同时可用。
  properties: EditorProperty[] = [
    'background-overall-component',
    'border-style',
    'basic-style-selector',
    'x-axis-selector',
    'y-axis-selector',
    'title-selector',
    'label-selector',
    'tooltip-selector',
    'legend-selector',
    'jump-set',
    'linkage',
    'quadrant-selector'
  ]
  // 限定每个编辑面板可调整的字段，避免在象限图中展示不适用的样式项。
  propertyInner: EditorPropertyInner = {
    'basic-style-selector': [
      'colors',
      'alpha',
      'scatterSymbol',
      'scatterSymbolSize',
      'seriesColor'
    ],
    'label-selector': ['fontSize', 'color'],
    'tooltip-selector': ['fontSize', 'color', 'backgroundColor', 'seriesTooltipFormatter', 'show'],
    'x-axis-selector': [
      'position',
      'name',
      'color',
      'fontSize',
      'axisLine',
      'axisValue',
      'splitLine',
      'axisForm',
      'axisLabel',
      'axisLabelFormatter'
    ],
    'y-axis-selector': [
      'position',
      'name',
      'color',
      'fontSize',
      'axisValue',
      'axisLine',
      'splitLine',
      'axisForm',
      'axisLabel',
      'axisLabelFormatter'
    ],
    'title-selector': [
      'title',
      'fontSize',
      'color',
      'hPosition',
      'isItalic',
      'isBolder',
      'remarkShow',
      'fontFamily',
      'letterSpace',
      'fontShadow'
    ],
    'legend-selector': ['icon', 'orient', 'color', 'fontSize', 'hPosition', 'vPosition'],
    'quadrant-selector': ['regionStyle', 'label', 'lineStyle']
  }
  // 象限图使用维度、X 值、Y 值、气泡大小和扩展提示字段共同完成图形映射。
  axis: AxisType[] = [
    'xAxis',
    'yAxis',
    'yAxisExt',
    'extBubble',
    'filter',
    'drill',
    'extLabel',
    'extTooltip'
  ]
  // 轴槽位配置控制字段类型、数量上限和是否允许空值，是拖拽字段时的约束来源。
  axisConfig: AxisConfig = {
    extBubble: {
      name: `${t('chart.bubble_size')} / ${t('chart.quota')}`,
      type: 'q',
      limit: 1,
      allowEmpty: true
    },
    xAxis: {
      name: `${t('chart.form_type')} / ${t('chart.dimension')}`,
      type: 'd',
      limit: 1
    },
    yAxis: {
      name: `${t('chart.x_axis')} / ${t('chart.quota')}`,
      type: 'q',
      limit: 1
    },
    yAxisExt: {
      name: `${t('chart.y_axis')} / ${t('chart.quota')}`,
      type: 'q',
      limit: 1
    }
  }

  public getFieldObject(chart: Chart) {
    // 汇总颜色、大小、X 值和 Y 值字段，供配置面板识别象限图的字段语义。
    const colorFieldObj = { id: chart.xAxis[0]?.id, name: chart.xAxis[0]?.['originName'] }
    const sizeFieldObj = { id: chart.extBubble[0]?.id, name: chart.extBubble[0]?.['originName'] }
    const xFieldObj = { id: chart.yAxis[0]?.id, name: chart.yAxis[0]?.['originName'] }
    const yFieldObj = { id: chart.yAxisExt[0]?.id, name: chart.yAxisExt[0]?.['originName'] }
    return { colorFieldObj, sizeFieldObj, xFieldObj, yFieldObj }
  }
  public getUniqueObjects<T>(arr: T[]): T[] {
    // 字段元数据以普通对象传递，序列化去重可保留原对象结构并移除重复项。
    return [...new Set(arr.map(JSON.stringify))].map(JSON.parse) as T[]
  }

  async drawChart(drawOptions: G2PlotDrawOptions<G2Scatter>): Promise<G2Scatter> {
    const { chart, container, action, quadrantDefaultBaseline } = drawOptions
    if (!chart.data?.data) {
      return
    }
    // 后端按三组指标返回数据，这里分别映射为 X 值、Y 值和气泡大小。
    const sourceData: Array<any> = cloneDeep(chart.data.data)
    const data1 = defaultTo(sourceData[0]?.data, [])
    const data2 = defaultTo(sourceData[1]?.data, [])
    const data3 = defaultTo(sourceData[2]?.data, [])
    const xData = data1.map(item => {
      return {
        ...item,
        id: item.quotaList[0]?.id,
        field: item.field,
        value: item.value
      }
    })
    const yData = data2.map(item => {
      return {
        ...item,
        id: item.quotaList[0]?.id,
        field: item.field,
        value: item.value
      }
    })
    const eData = data3.map(item => {
      return {
        ...item,
        id: item.quotaList[0]?.id,
        field: item.field,
        value: item.value
      }
    })
    // 新建图表默认将 X 轴基准线放在当前数据范围的中点。
    const xValues = xData.map(item => item.value)
    const xBaseline = ((Math.max(...xValues) + Math.min(...xValues)) / 2).toFixed()
    // Y 轴基准线同样取数据范围中点，后续用户修改后不再覆盖。
    const yValues = yData.map(item => item.value)
    const yBaseline = ((Math.max(...yValues) + Math.min(...yValues)) / 2).toFixed()
    const defaultBaselineQuadrant = {
      ...chart.customAttr['quadrant']
    }
    // 仅在没有保存过基准线时写入默认值，避免重新渲染覆盖用户配置。
    if (defaultBaselineQuadrant.xBaseline === undefined) {
      defaultBaselineQuadrant.xBaseline = xBaseline
      defaultBaselineQuadrant.yBaseline = yBaseline
    }
    // 合并气泡大小和 Y 轴指标的 quotaList，保证 tooltip 能同时展示相关指标。
    const getQuotaList = d => {
      const eQuotaList = eData.find(item => item.field === d.field)?.quotaList
      const yQuotaList = yData.find(item => item.field === d.field)?.quotaList
      if (JSON.stringify(eQuotaList) === JSON.stringify(yQuotaList)) {
        return yQuotaList
      }
      return [...(eQuotaList || []), ...(yQuotaList || [])]
    }
    const data = map(defaultTo(xData, []), d => {
      return {
        ...d,
        yAxis: d.value,
        quotaList: getQuotaList(d),
        yAxisExt: yData.find(item => item.field === d.field)?.value,
        extBubble: eData.find(item => item.field === d.field)?.value
      }
    })
    const baseOptions: ScatterOptions = {
      colorField: 'field',
      meta: {
        field: {
          type: 'cat'
        }
      },
      quadrant: {
        ...defaultBaselineQuadrant
      },
      data: data,
      xField: 'yAxis',
      yField: 'yAxisExt',
      appendPadding: getPadding(chart),
      pointStyle: {
        fillOpacity: 0.8,
        stroke: '#bbb'
      }
    }
    chart.container = container
    const options = this.setupOptions(chart, baseOptions)
    const { Scatter: G2Scatter } = await import('@antv/g2plot/esm/plots/scatter')
    const newChart = new G2Scatter(container, options)
    newChart.on('point:click', action)
    if (options.label) {
      newChart.on('label:click', action)
    }
    newChart.on('click', () => quadrantDefaultBaseline(defaultBaselineQuadrant))
    newChart.on('afterrender', () => quadrantDefaultBaseline(defaultBaselineQuadrant))
    const yAxis = parseJson(chart.customStyle).yAxis
    if (yAxis?.name) {
      configYaxisTitleLengthLimit(chart, newChart)
      configAxisLabelLengthLimit(chart, newChart, 'axis-title')
    }
    configPlotTooltipEvent(chart, newChart)
    return newChart
  }

  protected configBasicStyle(chart: Chart, options: ScatterOptions): ScatterOptions {
    const customAttr = parseJson(chart.customAttr)
    const basicStyle = customAttr.basicStyle
    // 有气泡大小字段时使用 sizeField 映射，否则使用固定散点尺寸。
    if (chart.extBubble?.length) {
      return {
        ...options,
        size: [4, 30],
        sizeField: 'extBubble',
        shape: basicStyle.scatterSymbol
      }
    }
    return {
      ...options,
      size: basicStyle.scatterSymbolSize,
      shape: basicStyle.scatterSymbol
    }
  }

  protected configXAxis(chart: Chart, options: ScatterOptions): ScatterOptions {
    const tmpOptions = super.configXAxis(chart, options)
    if (!tmpOptions.xAxis) {
      return tmpOptions
    }
    const xAxis = parseJson(chart.customStyle).xAxis
    if (tmpOptions.xAxis.label) {
      // 轴标签格式化复用通用数值格式配置，保持编辑器和渲染层一致。
      tmpOptions.xAxis.label.formatter = value => {
        return valueFormatter(value, xAxis.axisLabelFormatter)
      }
    }
    const axisValue = xAxis.axisValue
    if (!axisValue?.auto) {
      // 固定轴范围同时写入 min/max 和 limit，避免 G2Plot 自动范围覆盖用户配置。
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

  protected configYAxis(chart: Chart, options: ScatterOptions): ScatterOptions {
    const tmpOptions = super.configYAxis(chart, options)
    if (!tmpOptions.yAxis) {
      return tmpOptions
    }
    const yAxis = parseJson(chart.customStyle).yAxis
    if (tmpOptions.yAxis.label) {
      // Y 轴格式化跟随轴标签配置，支持百分比、千分位等展示规则。
      tmpOptions.yAxis.label.formatter = value => {
        return valueFormatter(value, yAxis.axisLabelFormatter)
      }
    }
    const axisValue = yAxis.axisValue
    if (!axisValue?.auto) {
      // 固定轴范围同时写入 min/max 和 limit，避免自动缩放影响象限线判断。
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
    return tmpOptions
  }

  protected configLabel(chart: Chart, options: ScatterOptions): ScatterOptions {
    let label
    let customAttr: DeepPartial<ChartAttr>
    if (chart.customAttr) {
      customAttr = parseJson(chart.customAttr)
      // 标签只展示维度名称，fullDisplay 关闭时启用防重叠布局。
      if (customAttr.label) {
        const l = customAttr.label
        if (l.show) {
          const layout = []
          if (!l.fullDisplay) {
            layout.push({ type: 'hide-overlap' })
            layout.push({ type: 'limit-in-shape' })
          }
          label = {
            offset: 0,
            style: {
              fill: l.color,
              fontSize: l.fontSize
            },
            content: datum => {
              return datum['name']
            },
            layout
          }
        } else {
          label = false
        }
      }
    }
    return { ...options, label }
  }

  protected configTooltip(chart: Chart, options: ScatterOptions): ScatterOptions {
    const customAttr: DeepPartial<ChartAttr> = parseJson(chart.customAttr)
    const tooltipAttr = customAttr.tooltip
    const xAxisTitle = chart.xAxis[0]
    const yAxisTitle = chart.yAxis[0]
    const yAxisExtTitle = chart.yAxisExt[0]
    // 没有可展示字段或用户关闭提示时，直接关闭 tooltip。
    if (!tooltipAttr.show || (!xAxisTitle && !yAxisTitle && !yAxisExtTitle)) {
      return {
        ...options,
        tooltip: false
      }
    }
    const formatterMap = tooltipAttr.seriesTooltipFormatter
      ?.filter(i => i.show)
      .reduce((pre, next) => {
        pre[next['seriesId']] = next
        return pre
      }, {}) as Record<string, SeriesFormatter>
    const optionsData = cloneDeep(options.data)
    // tooltip 同时展示坐标值、气泡值和动态提示指标，并按系列格式化配置输出。
    const tooltip: ScatterOptions['tooltip'] = {
      showTitle: true,
      title: (_title, datum) => {
        return datum?.['name']
      },
      customItems(originalItems) {
        if (!tooltipAttr.seriesTooltipFormatter?.length) {
          return originalItems
        }
        const result = []
        originalItems.forEach(item => {
          Object.keys(formatterMap).forEach(key => {
            if (key.endsWith(item.name)) {
              const formatter = formatterMap[key]
              if (formatter) {
                const value =
                  formatter.groupType === 'q'
                    ? valueFormatter(parseFloat(item.value as string), formatter.formatterCfg)
                    : item.value
                const name = isEmpty(formatter.chartShowName)
                  ? formatter.name
                  : formatter.chartShowName
                result.push({ color: item.color, name, value })
              }
            }
          })
        })
        const dynamicTooltipValue = optionsData.find(
          d => d.field === originalItems[0]['title']
        )?.dynamicTooltipValue
        if (dynamicTooltipValue.length > 0) {
          dynamicTooltipValue.forEach(dy => {
            const q = tooltipAttr.seriesTooltipFormatter.filter(i => i.id === dy.fieldId)
            if (q && q.length > 0) {
              const value = valueFormatter(parseFloat(dy.value as string), q[0].formatterCfg)
              const name = isEmpty(q[0].chartShowName) ? q[0].name : q[0].chartShowName
              result.push({ color: 'grey', name, value })
            }
          })
        }
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

  setupDefaultOptions(chart: ChartObj): ChartObj {
    // 象限图默认显示坐标轴并隐藏普通分割线，突出四象限基准线。
    chart.customStyle.yAxis.splitLine = {
      ...chart.customStyle.yAxis.splitLine,
      show: false
    }
    chart.customStyle.yAxisExt.splitLine = {
      ...chart.customStyle.yAxisExt.splitLine,
      show: false
    }
    chart.customStyle.yAxis.axisLine = {
      ...chart.customStyle.yAxis.axisLine,
      show: true
    }
    chart.customStyle.yAxisExt.axisLine = {
      ...chart.customStyle.yAxisExt.axisLine,
      show: true
    }
    return chart
  }

  protected configColor(chart: Chart, options: ScatterOptions): ScatterOptions {
    const { xAxis, yAxis, yAxisExt } = chart
    // 颜色维度、X 值和 Y 值齐全后才应用单维度系列配色。
    if (!(xAxis?.length && yAxis?.length && yAxisExt?.length)) {
      return options
    }
    return this.configSingleDimensionColor(chart, options)
  }

  public setupSeriesColor(chart: ChartObj, data?: any[]): ChartBasicStyle['seriesColor'] {
    const { xAxis, yAxis, yAxisExt } = chart
    // 字段未配置完整时不生成系列色，避免缓存无效颜色项。
    if (!(xAxis?.length && yAxis?.length && yAxisExt?.length)) {
      return []
    }
    const tmp = data?.[0]?.data
    return setUpSingleDimensionSeriesColor(chart, tmp)
  }

  protected configLegend(chart: Chart, options: ScatterOptions): ScatterOptions {
    const optionTmp = super.configLegend(chart, options)
    if (!optionTmp.legend) {
      return optionTmp
    }
    const customStyle = parseJson(chart.customStyle)
    let size
    if (customStyle && customStyle.legend) {
      size = defaults(JSON.parse(JSON.stringify(customStyle.legend)), DEFAULT_LEGEND_STYLE).size
    } else {
      size = DEFAULT_LEGEND_STYLE.size
    }
    // 图例 marker 半径跟随图例配置，避免散点尺寸影响图例识别。
    ;((optionTmp.legend as Record<string, any>).marker as Record<string, any>).style = style => {
      return {
        r: size,
        fill: style.fill
      }
    }
    return optionTmp
  }

  protected setupOptions(chart: Chart, options: ScatterOptions) {
    // 配置流水线按主题、字段映射、交互和基础样式顺序叠加，后置项可覆盖前置默认值。
    return flow(
      this.configTheme,
      this.configColor,
      this.configLabel,
      this.configTooltip,
      this.configLegend,
      this.configXAxis,
      this.configYAxis,
      this.configSlider,
      this.configBasicStyle
    )(chart, options, {}, this)
  }

  constructor() {
    super('quadrant', [])
  }
}
