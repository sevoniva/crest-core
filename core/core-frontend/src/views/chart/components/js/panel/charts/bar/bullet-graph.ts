import type {
  Bullet as G2Bullet,
  BulletOptions as G2BulletOptions
} from '@antv/g2plot/esm/plots/bullet'
import {
  G2PlotChartView,
  G2PlotDrawOptions
} from '@/views/chart/components/js/panel/types/impl/g2plot'
import {
  BAR_AXIS_TYPE,
  BAR_EDITOR_PROPERTY,
  BAR_EDITOR_PROPERTY_INNER
} from '@/views/chart/components/js/panel/charts/bar/common'
import { useI18n } from '@/hooks/web/useI18n'
import { flow, parseJson } from '@/views/chart/components/js/util'
import { BulletOptions } from '@antv/g2plot'
import { defaults, isEmpty } from 'lodash-es'
import {
  configPlotTooltipEvent,
  configXAxisLengthLimit,
  getPadding,
  getTooltipContainer,
  TOOLTIP_TPL
} from '@/views/chart/components/js/panel/common/common_antv'
import { valueFormatter } from '@/views/chart/components/js/formatter'

const { t } = useI18n()

/**
 * 子弹图
 */
export class BulletGraph extends G2PlotChartView<G2BulletOptions, G2Bullet> {
  constructor() {
    super('bullet-graph', [])
  }

  // 子弹图沿用柱状图轴配置，并额外声明目标值和背景区间字段。
  axis: AxisType[] = [...BAR_AXIS_TYPE, 'yAxisExt', 'extBubble']
  axisConfig = {
    ...this['axisConfig'],
    xAxis: { name: `${t('chart.form_type')} / ${t('chart.dimension')}`, type: 'd', limit: 1 },
    yAxis: { name: `${t('chart.progress_current')} / ${t('chart.quota')}`, type: 'q', limit: 1 },
    yAxisExt: { name: `${t('chart.progress_target')} / ${t('chart.quota')}`, type: 'q', limit: 1 },
    extBubble: {
      name: `${t('chart.range_bg')} / ${t('chart.quota')}`,
      type: 'q',
      allowEmpty: true,
      limit: 1
    }
  }
  // 子弹图隐藏函数、辅助线和阈值配置，保留专属的区间与目标值设置面板。
  properties: EditorProperty[] = [
    ...BAR_EDITOR_PROPERTY.filter(
      item => !['function-cfg', 'assist-line', 'threshold'].includes(item)
    ),
    'bullet-graph-selector'
  ]
  // 内层属性只暴露子弹图支持的样式项，避免编辑器出现无效配置入口。
  propertyInner = {
    'basic-style-selector': ['radiusColumnBar', 'layout'],
    'label-selector': ['hPosition', 'fontSize', 'color', 'labelFormatter'],
    'tooltip-selector': ['fontSize', 'color', 'backgroundColor', 'seriesTooltipFormatter', 'show'],
    'x-axis-selector': [
      ...BAR_EDITOR_PROPERTY_INNER['x-axis-selector'].filter(item => item != 'position'),
      'showLengthLimit'
    ],
    'y-axis-selector': [
      ...BAR_EDITOR_PROPERTY_INNER['y-axis-selector'].filter(
        item => item !== 'axisValue' && item !== 'position'
      ),
      'axisLabelFormatter'
    ],
    'legend-selector': ['showRange', 'orient', 'fontSize', 'color', 'hPosition', 'vPosition']
  }

  async drawChart(drawOption: G2PlotDrawOptions<G2Bullet>): Promise<G2Bullet> {
    const { chart, container, action } = drawOption
    if (!chart.data?.data?.length) return
    const result = mergeBulletData(chart)
    // 背景区间支持固定值和动态指标两种来源，固定值会覆盖数据集中的区间指标。
    const { bullet } = parseJson(chart.customAttr).misc
    if (bullet.bar.ranges.showType === 'fixed') {
      const customRange = bullet.bar.ranges.fixedRange?.map(item => item.fixedRangeValue) || [0]
      result.forEach(item => (item.ranges = customRange))
    } else {
      result.forEach(item => (item.ranges = item.originalRanges))
    }
    // 目标值同样支持固定配置，动态模式则使用 yAxisExt 指标汇总结果。
    if (bullet.bar.target.showType === 'fixed') {
      const customTarget = bullet.bar.target.value || 0
      result.forEach(item => (item.target = customTarget))
    } else {
      result.forEach(item => (item.target = item.originalTarget))
    }
    const initialOptions: BulletOptions = {
      appendPadding: getPadding(chart),
      data: result.reverse(),
      measureField: 'measures',
      rangeField: 'ranges',
      targetField: 'target',
      xField: 'title',
      meta: {
        title: {
          type: 'cat'
        }
      },
      interactions: [
        {
          type: 'active-region',
          cfg: {
            start: [{ trigger: 'element:mousemove', action: 'active-region:show' }],
            end: [{ trigger: 'element:mouseleave', action: 'active-region:hide' }]
          }
        }
      ]
    }
    const options = this.setupOptions(chart, initialOptions)
    let newChart = null
    const { Bullet: BulletClass } = await import('@antv/g2plot/esm/plots/bullet')
    newChart = new BulletClass(container, options)
    newChart.on('element:click', ev => {
      // 下钻参数需要带回原始维度列表，供联动和跳转动作继续使用。
      const pointData = ev?.data?.data
      const dimensionList = options.data.find(item => item.title === pointData.title)?.dimensionList
      const actionParams = {
        x: ev.x,
        y: ev.y,
        data: {
          data: {
            ...pointData,
            dimensionList
          }
        }
      }
      action(actionParams)
    })
    configPlotTooltipEvent(chart, newChart)
    configXAxisLengthLimit(chart, newChart)
    return newChart
  }

  protected configBasicStyle(chart: Chart, options: BulletOptions): BulletOptions {
    const basicStyle = parseJson(chart.customAttr).basicStyle
    const { radiusColumnBar, columnBarRightAngleRadius, layout } = basicStyle
    let radiusValue = 0
    let rangeLength = 1
    // 多段背景区间只在首尾段设置圆角，中间段保持平直以避免视觉断裂。
    if (radiusColumnBar === 'roundAngle' || radiusColumnBar === 'topRoundAngle') {
      radiusValue = columnBarRightAngleRadius
      rangeLength = options.data[0]?.ranges?.length
    }
    const barRadiusStyle = { radius: Array(2).fill(radiusValue) }
    const baseRadius = [...barRadiusStyle.radius, ...barRadiusStyle.radius]
    options = {
      ...options,
      bulletStyle: {
        range: datum => {
          if (!datum.rKey) return { fill: 'rgba(0, 0, 0, 0)' }
          if (rangeLength === 1) {
            return {
              radius:
                radiusColumnBar === 'topRoundAngle' ? [...barRadiusStyle.radius, 0, 0] : baseRadius
            }
          }
          if (rangeLength > 1 && datum.rKey === 'ranges_0') {
            return {
              radius: radiusColumnBar === 'topRoundAngle' ? [] : [0, 0, ...barRadiusStyle.radius]
            }
          }
          if (rangeLength > 1 && datum.rKey === 'ranges_' + (rangeLength - 1)) {
            return { radius: [...barRadiusStyle.radius, 0, 0] }
          }
        },
        measure: datum => {
          if (datum.measures) {
            return {
              radius:
                radiusColumnBar === 'topRoundAngle' ? [...barRadiusStyle.radius, 0, 0] : baseRadius
            }
          } else {
            return undefined
          }
        },
        target: datum => (datum.tKey === 'target' ? { lineWidth: 2 } : undefined)
      }
    }
    if (layout === 'vertical') options = { ...options, layout: 'vertical' }
    return options
  }

  protected configMisc(chart: Chart, options: BulletOptions): BulletOptions {
    const { bullet } = parseJson(chart.customAttr).misc
    const isDynamic = bullet.bar.ranges.showType === 'dynamic'
    // 固定背景区间按阈值升序取色，保证颜色顺序和区间大小一致。
    const rangeColor = isDynamic
      ? bullet.bar.ranges.fill
      : bullet.bar.ranges.fixedRange
          ?.sort((a, b) => (a.fixedRangeValue ?? 0) - (b.fixedRangeValue ?? 0))
          .map(item => item.fill) || []
    return {
      ...options,
      color: {
        measure: [].concat(bullet.bar.measures.fill),
        range: [].concat(rangeColor),
        target: [].concat(bullet.bar.target.fill)
      },
      size: {
        measure: bullet.bar.measures.size,
        range: bullet.bar.ranges.size,
        target: bullet.bar.target.size
      }
    }
  }

  protected configXAxis(chart: Chart, options: BulletOptions): BulletOptions {
    const tmpOptions = super.configXAxis(chart, options)
    if (!tmpOptions.xAxis || !tmpOptions.xAxis.label) return tmpOptions

    const { layout, xAxis } = tmpOptions
    const position = xAxis.position
    const style: any = { ...xAxis.label.style }

    // G2Plot 横向和纵向布局的轴文本基线不同，需要分别修正对齐方式。
    if (layout === 'vertical') {
      style.textAlign = 'center'
      style.textBaseline = position === 'bottom' ? 'top' : 'bottom'
    } else {
      style.textAlign = position === 'bottom' ? 'end' : 'start'
      style.textBaseline = 'middle'
    }

    xAxis.label.style = style
    if (tmpOptions.xAxis.label) {
      const x = parseJson(chart.customStyle).xAxis
      const { lengthLimit } = x.axisLabel
      // 类目轴支持长度限制，过长文本在轴上截断，完整值仍保留在数据中。
      defaults(tmpOptions.xAxis.label, {
        formatter: value => {
          return value?.length > lengthLimit ? value.substring(0, lengthLimit) + '...' : value
        }
      })
    }
    return tmpOptions
  }

  protected configYAxis(chart: Chart, options: BulletOptions): BulletOptions {
    const tmpOptions = super.configYAxis(chart, options)
    if (!tmpOptions.yAxis || !tmpOptions.yAxis.label) return tmpOptions

    const yAxis = parseJson(chart.customStyle).yAxis
    tmpOptions.yAxis.label.formatter = value => valueFormatter(value, yAxis.axisLabelFormatter)

    const { layout, yAxis: yAxisConfig } = tmpOptions
    const position = yAxisConfig.position
    const style: any = { ...yAxisConfig.label.style }

    // y 轴标签同样按布局方向调整文本基线，避免竖向模式下标签贴近轴线。
    if (layout === 'vertical') {
      style.textAlign = position === 'left' ? 'end' : 'start'
      style.textBaseline = 'middle'
    } else {
      style.textAlign = 'center'
      style.textBaseline = position === 'left' ? 'top' : 'bottom'
    }
    tmpOptions.yAxis.nice = false
    return tmpOptions
  }

  protected configLabel(chart: Chart, options: BulletOptions): BulletOptions {
    const tmpOptions = super.configLabel(chart, options)
    if (!tmpOptions.label) return tmpOptions

    const labelAttr = parseJson(chart.customAttr).label
    // 子弹图只在实际值条上显示标签，背景区间和目标线不显示重复数值。
    const label: any = {
      ...tmpOptions.label,
      formatter: param =>
        param.mKey === 'measures'
          ? valueFormatter(param.measures, labelAttr.labelFormatter)
          : undefined
    }
    return { ...tmpOptions, label: { measure: label } }
  }

  protected configLegend(chart: Chart, options: BulletOptions): BulletOptions {
    const baseLegend = super.configLegend(chart, options).legend
    if (!baseLegend) return options

    const { bullet } = parseJson(chart.customAttr).misc
    const customStyleLegend = parseJson(chart.customStyle).legend
    const items = []

    // 创建 G2Plot 自定义图例项，marker 样式直接复用子弹图颜色配置。
    const createLegendItem = (value, name, symbol, fill, size = 4) => ({
      value,
      name,
      marker: { symbol, style: { fill, stroke: value === 'measure' ? '' : fill, r: size } }
    })

    if (customStyleLegend.showRange) {
      if (bullet.bar.ranges.showType === 'dynamic') {
        if (chart.extBubble.length) {
          // 动态区间只展示一个区间图例，名称优先使用字段展示名。
          const rangeName = chart.extBubble[0]?.chartShowName || bullet.bar.ranges.name
          items.push(
            createLegendItem(
              'dynamic',
              rangeName || chart.extBubble[0]?.name,
              bullet.bar.ranges.symbol,
              [].concat(bullet.bar.ranges.fill)[0],
              bullet.bar.ranges.symbolSize
            )
          )
        }
      } else {
        // 固定区间逐项生成图例，便于用户区分每个背景阈值段。
        bullet.bar.ranges.fixedRange?.forEach(item => {
          items.push(
            createLegendItem(
              item.name,
              item.name,
              bullet.bar.ranges.symbol,
              item.fill,
              bullet.bar.ranges.symbolSize
            )
          )
        })
      }
    }

    // 目标值和实际值始终写入图例，确保用户能识别目标线和进度条含义。
    const targetName = chart.yAxisExt[0]?.chartShowName || bullet.bar.target.name
    items.push(
      createLegendItem(
        'target',
        targetName || chart.yAxisExt[0]?.name,
        'line',
        [].concat(bullet.bar.target.fill)[0],
        bullet.bar.ranges.symbolSize
      )
    )

    const measureName = chart.yAxis[0]?.chartShowName || bullet.bar.measures.name
    items.push(
      createLegendItem(
        'measure',
        measureName || chart.yAxis[0]?.name,
        bullet.bar.ranges.symbol,
        [].concat(bullet.bar.measures.fill)[0],
        bullet.bar.ranges.symbolSize
      )
    )

    const legendSize = bullet.bar.ranges.symbolSize ?? customStyleLegend.size ?? 4
    return {
      ...options,
      legend: {
        ...baseLegend,
        custom: true,
        position: baseLegend.position,
        layout: baseLegend.layout,
        items,
        itemHeight:
          (customStyleLegend.fontSize > legendSize * 2
            ? customStyleLegend.fontSize
            : legendSize * 2) + 4,
        pageNavigator: {
          marker: {
            style: {
              fill: 'rgba(0,0,0,0.65)',
              size: legendSize * 2
            }
          },
          text: {
            style: {
              fill: customStyleLegend.color,
              fontSize: customStyleLegend.fontSize
            }
          }
        }
      }
    }
  }

  protected configTooltip(chart: Chart, options: BulletOptions): BulletOptions {
    const customAttr: DeepPartial<ChartAttr> = parseJson(chart.customAttr)
    const tooltipAttr = customAttr.tooltip
    const { bullet } = parseJson(chart.customAttr).misc
    if (!tooltipAttr.show) return { ...options, tooltip: false }
    const customStyleLegend = parseJson(chart.customStyle).legend
    // Tooltip 展示项由系列格式化配置控制，按轴类型映射到子弹图三类数据。
    const formatterMap = tooltipAttr.seriesTooltipFormatter
      ?.filter(
        i => i.show && ['-yAxis', '-yAxisExt', 'extBubble'].some(k => i.seriesId.includes(k))
      )
      .reduce((pre, next, _index) => {
        switch (next.axisType) {
          case 'yAxis':
            pre['measures'] = next
            return pre
          case 'yAxisExt':
            pre['target'] = next
            return pre
          case 'extBubble':
            pre['ranges'] = next
            return pre
          default:
            return pre
        }
      }, {}) as Record<string, SeriesFormatter>
    const tooltip = {
      shared: true,
      showMarkers: true,
      customItems(originalItems) {
        if (!tooltipAttr.seriesTooltipFormatter?.length) return originalItems
        const isDynamic = bullet.bar.ranges.showType === 'dynamic'
        const rangeFormatter = chart.extBubble[0]
        const result = []
        const data = options.data.find(item => item.title === originalItems[0].title)
        // 先写入实际值、目标值和动态区间字段，顺序由 formatterMap 控制。
        Object.keys(formatterMap).forEach((key, _index) => {
          const formatter = formatterMap[key]
          if (formatter) {
            let name = isEmpty(formatter.chartShowName) ? formatter.name : formatter.chartShowName
            let value = valueFormatter(parseFloat(data[key] as string), formatter.formatterCfg)
            let color = bullet.bar[key]?.fill ?? 'grey'
            if (key === 'ranges') {
              if (!isDynamic && rangeFormatter) {
                name = isEmpty(rangeFormatter.chartShowName)
                  ? rangeFormatter.name
                  : rangeFormatter.chartShowName
                value = valueFormatter(parseFloat(data.minRanges[0]), rangeFormatter.formatterCfg)
                color = 'grey'
              } else {
                return
              }
            }
            result.push({
              color,
              name,
              value
            })
          }
        })
        const ranges = data.ranges
        const rangeFormatterCfg =
          formatterMap['ranges']?.formatterCfg ?? rangeFormatter?.formatterCfg
        const shouldShowRanges = isDynamic
          ? Boolean(formatterMap['ranges'])
          : customStyleLegend.showRange
        if (shouldShowRanges) {
          // 固定区间展示每个阈值段，动态区间展示对应指标格式化后的值。
          ranges.forEach((range, index) => {
            const value = isDynamic
              ? valueFormatter(parseFloat(data.minRanges[0]), rangeFormatterCfg)
              : (range as string)
            let name = ''
            let color: string | string[]
            if (bullet.bar.ranges.showType === 'dynamic') {
              name = isEmpty(rangeFormatter.chartShowName)
                ? rangeFormatter.name
                : rangeFormatter.chartShowName
              color = bullet.bar['ranges'].fill
            } else {
              const customRange = bullet.bar.ranges.fixedRange[index].name
              name = customRange
                ? customRange
                : isEmpty(rangeFormatter.chartShowName)
                ? rangeFormatter.name
                : rangeFormatter.chartShowName
              color = bullet.bar['ranges'].fixedRange[index].fill
            }
            result.push({ ...originalItems[0], color, name, value })
          })
        }
        const dynamicTooltipValue = chart.data.data.find(
          d => d.field === originalItems[0]['title']
        )?.dynamicTooltipValue
        if (dynamicTooltipValue.length > 0) {
          // 动态 tooltip 字段不参与绘图，只在悬浮提示中追加展示。
          dynamicTooltipValue.forEach(dy => {
            const q = tooltipAttr.seriesTooltipFormatter.filter(i => i.id === dy.fieldId)
            if (q && q.length > 0) {
              const value = valueFormatter(parseFloat(dy.value as string), q[0].formatterCfg)
              const name = isEmpty(q[0].chartShowName) ? q[0].name : q[0].chartShowName
              result.push({ color: 'grey', name, value })
            }
          })
        }
        // 灰色的补充项放在末尾，主要指标保持在 tooltip 前部。
        result.sort((a, b) => (a.color === 'grey' ? 1 : b.color === 'grey' ? -1 : 0))
        return result
      },
      container: getTooltipContainer(`tooltip-${chart.id}`, chart.container),
      itemTpl: TOOLTIP_TPL,
      enterable: true
    }
    return { ...options, tooltip }
  }

  setupDefaultOptions(chart: ChartObj): ChartObj {
    // 默认将实际值标签放在进度条中部，并关闭 y 轴分割线以突出区间背景。
    chart.customAttr.label.position = 'middle'
    chart.customStyle.yAxis.splitLine.show = false
    return super.setupDefaultOptions(chart)
  }

  protected setupOptions(chart: Chart, options: BulletOptions): BulletOptions {
    return flow(
      this.configTheme,
      this.configBasicStyle,
      this.configMisc,
      this.configXAxis,
      this.configYAxis,
      this.configLabel,
      this.configLegend,
      this.configTooltip
    )(chart, options, {}, this)
  }
}

/**
 * 组装子弹图数据
 * @param chart
 */
function mergeBulletData(chart): any[] {
  // 先根据维度分组，再按指标字段组装成 G2Plot 子弹图需要的数据结构。
  const groupedData = chart.data.data.reduce((acc, item) => {
    const field = item.field
    if (!acc[field]) {
      acc[field] = []
    }
    acc[field].push(item)
    return acc
  }, {})
  const result = []
  // 每个维度对应一条子弹图记录，实际值、目标值、区间值分别累加。
  Object.keys(groupedData).forEach(field => {
    const items = groupedData[field]
    // 初始化子弹图条目结构
    const entry = {
      title: field,
      ranges: [],
      measures: [],
      target: [],
      dimensionList: items[0].dimensionList,
      quotaList: []
    }

    // 指标相同时仍按轴字段 id 分类，避免空值或重复指标造成数据错位。
    items.forEach(item => {
      const quotaId = item.quotaList[0]?.id
      const v = item.value || 0
      if (quotaId === chart.yAxis[0]?.id) {
        entry.measures.push(v)
      }
      if (quotaId === chart.yAxisExt[0]?.id) {
        entry.target.push(v)
      }
      if (quotaId === chart.extBubble[0]?.id) {
        entry.ranges.push(v)
      }
      entry.quotaList.push(item.quotaList[0])
    })
    // 多条明细按子弹图字段累加为单值数组，保留原始区间和目标值供模式切换使用。
    const ranges = chart.extBubble[0]?.id
      ? [].concat(entry.ranges?.reduce((acc, curr) => acc + curr, 0))
      : []
    const target = [].concat(entry.target?.reduce((acc, curr) => acc + curr, 0))
    const measures = [].concat(entry.measures?.reduce((acc, curr) => acc + curr, 0))
    const bulletData = {
      ...entry,
      measures: measures,
      target: target,
      ranges: ranges,
      quotaList: [...entry.quotaList],
      minRanges: ranges,
      originalRanges: ranges,
      originalTarget: target
    }
    result.push(bulletData)
  })
  return result
}
