import {
  G2PlotChartView,
  G2PlotDrawOptions
} from '@/views/chart/components/js/panel/types/impl/g2plot'
import type { Line as G2Line, LineOptions } from '@antv/g2plot/esm/plots/line'
import {
  configPlotTooltipEvent,
  getPadding,
  getTooltipContainer,
  TOOLTIP_TPL
} from '../../common/common_antv'
import {
  convertToAlphaColor,
  flow,
  getLineConditions,
  getLineLabelColorByCondition,
  hexColorToRGBA,
  isAlphaColor,
  parseJson,
  setUpGroupSeriesColor
} from '@/views/chart/components/js/util'
import { cloneDeep, defaults, isEmpty } from 'lodash-es'
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
import type { Datum } from '@antv/g2plot/esm/types/common'
import { useI18n } from '@/hooks/web/useI18n'
import { DEFAULT_LABEL, DEFAULT_LEGEND_STYLE } from '@/views/chart/components/editor/util/chart'
import { clearExtremum, extremumEvt } from '@/views/chart/components/js/extremumUitl'
import { Group } from '@antv/g-canvas'

const { t } = useI18n()
const DEFAULT_DATA = []

/**
 * 折线图渲染器，支持分组系列、极值标记、条件色、轮播提示和自定义图例排序。
 */
export class Line extends G2PlotChartView<LineOptions, G2Line> {
  // 折线图复用通用编辑面板，并额外开放系列配色、极值和图例排序能力。
  properties = LINE_EDITOR_PROPERTY
  propertyInner = {
    ...LINE_EDITOR_PROPERTY_INNER,
    'basic-style-selector': [...LINE_EDITOR_PROPERTY_INNER['basic-style-selector'], 'seriesColor'],
    'label-selector': ['seriesLabelVPosition', 'seriesLabelFormatter', 'showExtremum'],
    'tooltip-selector': [
      ...LINE_EDITOR_PROPERTY_INNER['tooltip-selector'],
      'seriesTooltipFormatter',
      'carousel'
    ],
    'legend-selector': [...LINE_EDITOR_PROPERTY_INNER['legend-selector'], 'legendSort']
  }
  // xAxisExt 作为分组维度，存在时用于生成多系列折线和图例项。
  axis: AxisType[] = [...LINE_AXIS_TYPE, 'xAxisExt']
  axisConfig = {
    ...this['axisConfig'],
    xAxis: {
      name: `${t('chart.drag_block_type_axis')} / ${t('chart.dimension')}`,
      type: 'd'
    },
    xAxisExt: {
      name: `${t('chart.chart_group')} / ${t('chart.dimension')}`,
      type: 'd',
      limit: 1,
      allowEmpty: true
    },
    yAxis: {
      name: `${t('chart.drag_block_value_axis')} / ${t('chart.quota')}`,
      type: 'q'
    }
  }

  async drawChart(drawOptions: G2PlotDrawOptions<G2Line>): Promise<G2Line> {
    const { chart, action, container } = drawOptions
    chart.container = container
    if (!chart.data?.data?.length) {
      // 空数据时同步清理极值辅助图层，避免旧图形残留。
      clearExtremum(chart)
      return
    }
    const data = cloneDeep(chart.data.data)
    // 基础配色先写入初始化配置，后续系列色和透明度配置可继续覆盖。
    const customAttr = parseJson(chart.customAttr)
    const color = customAttr.basicStyle.colors
    // 初始化配置建立字段映射和图例交互，样式细节在 setupOptions 中叠加。
    const initOptions: LineOptions = {
      data,
      xField: 'field',
      yField: 'value',
      seriesField: 'category',
      appendPadding: getPadding(chart),
      meta: {
        category: {
          type: 'cat'
        }
      },
      color,
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
    const options = this.setupOptions(chart, initOptions)
    const { Line: G2Line } = await import('@antv/g2plot/esm/plots/line')
    // 创建图表实例后绑定点击、极值和 tooltip 事件。
    const newChart = new G2Line(container, options)

    newChart.on('point:click', action)
    extremumEvt(newChart, chart, options, container)
    configPlotTooltipEvent(chart, newChart)
    listenYAxisNiceMinEvents(chart, newChart)
    return newChart
  }

  protected configLabel(chart: Chart, options: LineOptions): LineOptions {
    const tmpOptions = super.configLabel(chart, options)
    if (!tmpOptions.label) {
      return {
        ...tmpOptions,
        label: false
      }
    }
    const { label: labelAttr, basicStyle } = parseJson(chart.customAttr)
    const conditions = getLineConditions(chart)
    // 系列标签格式化按指标 id 索引，支持每条指标独立开关和样式。
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
        // 极值辅助点只用于绘制标记，不参与普通标签输出。
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
        // 条件色优先于标签自定义色，保证异常阈值在标签上也能被识别。
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

  protected configBasicStyle(chart: Chart, options: LineOptions): LineOptions {
    // 基础样式控制折线平滑、点形状和线宽，直接映射到 G2Plot line/point 配置。
    const customAttr: DeepPartial<ChartAttr> = parseJson(chart.customAttr)
    const s = JSON.parse(JSON.stringify(customAttr.basicStyle))
    const smooth = s.lineSmooth
    const point = {
      size: s.lineSymbolSize,
      shape: s.lineSymbol
    }
    const lineStyle = {
      lineWidth: s.lineWidth
    }
    return {
      ...options,
      smooth,
      point,
      lineStyle
    }
  }

  protected configCustomColors(chart: Chart, options: LineOptions): LineOptions {
    const basicStyle = parseJson(chart.customAttr).basicStyle
    // 自定义颜色统一叠加透明度，避免基础色和系列色表现不一致。
    const color = basicStyle.colors.map(item => hexColorToRGBA(item, basicStyle.alpha))
    return {
      ...options,
      color
    }
  }

  protected configYAxis(chart: Chart, options: LineOptions): LineOptions {
    const tmpOptions = super.configYAxis(chart, options)
    if (!tmpOptions.yAxis) {
      return tmpOptions
    }
    const yAxis = parseJson(chart.customStyle).yAxis
    if (tmpOptions.yAxis.label) {
      // Y 轴标签复用数值格式化配置，保持轴刻度和 tooltip 的格式一致。
      tmpOptions.yAxis.label.formatter = value => {
        return valueFormatter(value, yAxis.axisLabelFormatter)
      }
    }
    const axisValue = yAxis.axisValue
    if (!axisValue?.auto) {
      // 固定轴范围同时写入 min/max/limit，避免自动 nice 值覆盖用户设置。
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
      // 自动轴范围使用 nice 最小值算法，并监听渲染事件同步计算结果。
      return calcNiceMinValue(chart, options, tmpOptions)
    }
    return tmpOptions
  }

  protected configTooltip(chart: Chart, options: LineOptions): LineOptions {
    const customAttr: DeepPartial<ChartAttr> = parseJson(chart.customAttr)
    const tooltipAttr = customAttr.tooltip
    if (!tooltipAttr.show) {
      return {
        ...options,
        tooltip: false
      }
    }
    const xAxisExt = chart.xAxisExt
    const formatterMap = tooltipAttr.seriesTooltipFormatter
      ?.filter(i => i.show)
      .reduce((pre, next) => {
        pre[next.id] = next
        return pre
      }, {}) as Record<string, SeriesFormatter>
    const tooltip: LineOptions['tooltip'] = {
      showTitle: true,
      customItems(originalItems) {
        if (!tooltipAttr.seriesTooltipFormatter?.length) {
          return originalItems
        }
        const head = originalItems[0]
        // G2Plot 生成的辅助数据没有 quotaList，直接保留默认提示内容。
        if (!head.data.quotaList) {
          return originalItems
        }
        const result = []
        originalItems
          .filter(item => formatterMap[item.data.quotaList[0].id])
          .forEach(item => {
            // 分组维度存在时，tooltip 名称优先展示系列名称而不是指标名称。
            const formatter = formatterMap[item.data.quotaList[0].id]
            const value = valueFormatter(parseFloat(item.value as string), formatter.formatterCfg)
            let name = isEmpty(formatter.chartShowName) ? formatter.name : formatter.chartShowName
            if (xAxisExt?.length > 0) {
              name = item.data.category
            }
            result.push({ ...item, name, value })
          })
        head.data.dynamicTooltipValue?.forEach(item => {
          // 扩展 tooltip 指标追加到末尾，颜色置灰以区别于折线系列。
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

  public setupSeriesColor(chart: ChartObj, data?: any[]): ChartBasicStyle['seriesColor'] {
    // 分组系列色根据当前数据生成，确保新增分组也能获得可编辑颜色项。
    return setUpGroupSeriesColor(chart, data)
  }

  protected configLegend(chart: Chart, options: LineOptions): LineOptions {
    const optionTmp = super.configLegend(chart, options)
    if (!optionTmp.legend) {
      return optionTmp
    }
    const xAxisExt = chart.xAxisExt[0]
    if (xAxisExt?.customSort?.length > 0) {
      // 图例自定义排序同时影响 category 元数据，保证图例和折线绘制顺序一致。
      const sort = xAxisExt.customSort ?? []
      if (sort?.length) {
        // 新数据可能不在历史排序中，需要追加到排序末尾，避免系列被过滤掉。
        const data = optionTmp.data
        const cats =
          data?.reduce((p, n) => {
            const cat = n['category']
            if (cat && !p.includes(cat)) {
              p.push(cat)
            }
            return p
          }, []) || []
        const values = sort.reduce((p, n) => {
          if (cats.includes(n)) {
            const index = cats.indexOf(n)
            if (index !== -1) {
              cats.splice(index, 1)
            }
            p.push(n)
          }
          return p
        }, [])
        cats.length > 0 && values.push(...(cats as any[]))
        ;(optionTmp as Record<string, any>).meta = {
          ...(optionTmp as Record<string, any>).meta,
          category: {
            type: 'cat',
            values
          }
        }
      }
    }

    const customStyle = parseJson(chart.customStyle)
    let size
    if (customStyle && customStyle.legend) {
      size = defaults(JSON.parse(JSON.stringify(customStyle.legend)), DEFAULT_LEGEND_STYLE).size
    } else {
      size = DEFAULT_LEGEND_STYLE.size
    }

    // 图例 marker 半径跟随图例配置，颜色使用折线 stroke 保持识别一致。
    ;((optionTmp.legend as Record<string, any>).marker as Record<string, any>).style = style => {
      return {
        r: size,
        fill: style.stroke
      }
    }
    const { sort, customSort, icon } = customStyle.legend
    if (sort && sort !== 'none' && chart.xAxisExt.length) {
      // 排序开启时手工生成图例项，才能同时控制顺序、图标和透明度。
      const customAttr = parseJson(chart.customAttr)
      const { basicStyle } = customAttr
      const seriesMap =
        basicStyle.seriesColor?.reduce((p, n) => {
          p[n.id] = n
          return p
        }, {}) || {}
      const dupCheck = new Set()
      const items = optionTmp.data?.reduce((arr, item) => {
        if (!dupCheck.has(item.category)) {
          const fill =
            seriesMap[item.category]?.color ??
            optionTmp.color[dupCheck.size % optionTmp.color.length]
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
        // 默认排序按系列名称升降序排列。
        items.sort((a, b) => {
          return sort !== 'desc' ? a.name.localeCompare(b.name) : b.name.localeCompare(a.name)
        })
      } else {
        // 自定义排序先放置命中的系列，未命中的新增系列保留在后面。
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
      ;(optionTmp.legend as Record<string, any>).items = items
      if (xAxisExt?.customSort?.length > 0) {
        // 自定义图例项已承载顺序，移除 meta 排序避免二次排序影响显示。
        delete (optionTmp as Record<string, any>).meta?.category.values
      }
    }
    return optionTmp
  }

  protected setupOptions(chart: Chart, options: LineOptions): LineOptions {
    // 配置顺序从主题、空值、颜色、标签、提示到坐标轴和分析能力逐层叠加。
    return flow(
      this.configTheme,
      this.configEmptyDataStrategy,
      this.configGroupColor,
      this.configLabel,
      this.configTooltip,
      this.configBasicStyle,
      this.configCustomColors,
      this.configLegend,
      this.configXAxis,
      this.configYAxis,
      this.configSlider,
      this.configAnalyse,
      this.configConditions
    )(chart, options)
  }

  constructor(name = 'line') {
    super(name, DEFAULT_DATA)
  }
}
