import {
  G2PlotChartView,
  G2PlotDrawOptions
} from '@/views/chart/components/js/panel/types/impl/g2plot'
import { parseJson } from '@/views/chart/components/js/util'

const DEFAULT_COLORS = ['#2563eb', '#0891b2', '#16a34a', '#f59e0b', '#f97316', '#e11d48', '#7c3aed']

// 转义 SVG 文本节点中的特殊字符，防止字段名进入 innerHTML 时破坏 SVG 结构。
const escapeSvgText = (value: unknown) =>
  String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

// 转义 SVG 属性值中的特殊字符，供 aria-label、title 等属性安全使用。
const escapeSvgAttribute = (value: unknown) =>
  escapeSvgText(value).replace(/"/g, '&quot;').replace(/'/g, '&#39;')

// 将数值限制在指定区间内
const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value))

// 格式化阶段指标展示值，大数使用千分位，小数保留一位便于阶段内短文本展示。
const formatMetricValue = (value: unknown) => {
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) {
    return String(value ?? '-')
  }
  if (Math.abs(numberValue) >= 1000) {
    return numberValue.toLocaleString('zh-CN', { maximumFractionDigits: 0 })
  }
  return numberValue.toLocaleString('zh-CN', { maximumFractionDigits: 1 })
}

// 将十六进制颜色转换为 RGB 通道
const hexToRgb = (hex: string) => {
  const clean = hex.replace('#', '')
  if (!/^[0-9a-fA-F]{6}$/.test(clean)) {
    return null
  }
  return {
    r: parseInt(clean.slice(0, 2), 16),
    g: parseInt(clean.slice(2, 4), 16),
    b: parseInt(clean.slice(4, 6), 16)
  }
}

// 对十六进制颜色进行明暗调整
const adjustColor = (hex: string, amount: number) => {
  const rgb = hexToRgb(hex)
  if (!rgb) {
    return hex
  }
  // 按通道调整颜色并限制在合法范围内
  const channel = (value: number) => clamp(value + amount, 0, 255)
  return `rgb(${channel(rgb.r)}, ${channel(rgb.g)}, ${channel(rgb.b)})`
}

// 将十六进制颜色转换为带透明度的 RGBA 表达式
const colorWithAlpha = (hex: string, alpha: number) => {
  const rgb = hexToRgb(hex)
  if (!rgb) {
    return hex
  }
  return `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, ${alpha})`
}

// 获取阶段漏斗使用的维度轴字段
const getDimensionAxis = (chart: Chart) => chart.xAxis?.[0] || chart['xaxis']?.[0]
// 获取阶段漏斗使用的指标轴字段
const getQuotaAxis = (chart: Chart) => chart.yAxis?.[0] || chart['yaxis']?.[0]

class StageFunnelDomChart {
  private rendered = false

  constructor(
    private readonly containerId: string,
    private readonly chart: Chart,
    private readonly action?: (...args: any[]) => any
  ) {}

  private getContainer() {
    return document.getElementById(this.containerId)
  }

  private getSortedData() {
    const data = [...((this.chart.data?.data || []) as any[])]
    const customSort = getDimensionAxis(this.chart)?.customSort
    // 优先按维度自定义排序展示阶段，否则使用数据返回顺序作为阶段顺序。
    const stageOrder = Array.isArray(customSort)
      ? customSort
      : data.map(item => item.field || item.name).filter(Boolean)

    return data.sort((left, right) => {
      const leftIndex = stageOrder.indexOf(left.field || left.name)
      const rightIndex = stageOrder.indexOf(right.field || right.name)
      const leftOrder = leftIndex === -1 ? Number.MAX_SAFE_INTEGER : leftIndex
      const rightOrder = rightIndex === -1 ? Number.MAX_SAFE_INTEGER : rightIndex
      return (
        leftOrder - rightOrder || String(left.field || '').localeCompare(String(right.field || ''))
      )
    })
  }

  private getStageFunnelConfig() {
    const customAttr: DeepPartial<ChartAttr> = parseJson(this.chart.customAttr)
    const basicStyle = customAttr.basicStyle as any
    const metricLimit = Number(basicStyle?.stageFunnel?.metricLimit)
    // 阶段漏斗的扩展配置挂在 basicStyle.stageFunnel，缺省值保证旧图表可直接渲染。
    return {
      colors: basicStyle?.colors?.length ? basicStyle.colors : DEFAULT_COLORS,
      periodLabel: basicStyle?.stageFunnel?.periodLabel || '时间周期',
      periodColor: basicStyle?.stageFunnel?.periodColor || '#416fd1',
      metricTitle: basicStyle?.stageFunnel?.metricTitle || '各阶段显示：数量 / 平均耗时',
      metricLimit: Number.isFinite(metricLimit) ? metricLimit : 3,
      metricAliases: basicStyle?.stageFunnel?.metricAliases || {},
      noteTitleColor: basicStyle?.stageFunnel?.noteTitleColor || '#f3fbff',
      noteTextColor: basicStyle?.stageFunnel?.noteTextColor || 'rgba(226,245,255,.82)'
    }
  }

  private buildStageMetrics(item: any, metricLimit: number, metricAliases: Record<string, string>) {
    const quotaName = getQuotaAxis(this.chart)?.name || '数量'
    const quotaAlias = metricAliases[quotaName] || quotaName
    const metrics = [`${quotaAlias}${formatMetricValue(item.value)}`]
    const tooltipFields = this.chart.extTooltip || []
    // 扩展 tooltip 字段复用为阶段副指标，数量受 metricLimit 控制以避免文本拥挤。
    tooltipFields.slice(0, Math.max(0, metricLimit - 1)).forEach((field, index) => {
      const dynamicValue = item.dynamicTooltipValue?.[index]
      const tooltipValue = dynamicValue?.stringValue ?? dynamicValue?.value
      if (field?.name) {
        const alias = metricAliases[field.name] || field.name
        metrics.push(`${alias}${formatMetricValue(tooltipValue)}`)
      }
    })
    return metrics
  }

  private buildStageSubtitle(
    item: any,
    metricLimit: number,
    metricAliases: Record<string, string>
  ) {
    return this.buildStageMetrics(item, metricLimit, metricAliases).join('  ')
  }

  private buildStageTitle(item: any) {
    const quotaName = getQuotaAxis(this.chart)?.name || '数量'
    // SVG title 提供原始阶段、主指标和扩展指标，补足图内省略信息。
    const tooltipLines = (this.chart.extTooltip || [])
      .map((field, index) => {
        const tooltipValue = item.dynamicTooltipValue?.[index]?.value
        return field?.name ? `${field.name}: ${formatMetricValue(tooltipValue)}` : ''
      })
      .filter(Boolean)
    return [
      item.field || item.name,
      `${quotaName}: ${formatMetricValue(item.value)}`,
      ...tooltipLines
    ].join('\n')
  }

  private getVisualMetric(item: any) {
    const numberValue = Number(item.value)
    if (!Number.isFinite(numberValue)) {
      return '-'
    }
    return `${numberValue.toLocaleString('zh-CN', { maximumFractionDigits: 0 })}`
  }

  private buildMetricNoteLines(metricTitle: string) {
    const [title, metrics] = metricTitle.split(/[：:]/)
    // 说明文本支持用中英文冒号拆成两行，适配右侧窄说明区域。
    if (!metrics) {
      return [metricTitle]
    }
    return [title, metrics.trim()]
  }

  private paint() {
    const container = this.getContainer()
    if (!container) {
      return
    }
    const data = this.getSortedData()
    if (!data.length) {
      container.innerHTML = ''
      this.rendered = true
      return
    }

    const {
      colors,
      periodLabel,
      periodColor,
      metricTitle,
      metricLimit,
      metricAliases,
      noteTitleColor,
      noteTextColor
    } = this.getStageFunnelConfig()
    // 根据容器尺寸动态收缩左右说明区，窄容器只保留核心漏斗主体。
    const width = Math.max(container.clientWidth || 0, 320)
    const height = Math.max(container.clientHeight || 0, 160)
    const sideVisible = width >= 620 && height >= 190
    const leftWidth = sideVisible ? 116 : 0
    const rightWidth = sideVisible ? 198 : 0
    const stageAreaWidth = Math.max(width - leftWidth - rightWidth - 34, 220)
    const centerX = leftWidth + stageAreaWidth / 2 + 8
    const top = clamp(height * 0.045, 8, 18)
    const gap = clamp(((height - top * 2) / data.length) * 0.08, 2, 6)
    const bodyHeight = clamp((height - top * 2 - gap * (data.length - 1)) / data.length, 26, 48)
    const ellipseHeight = clamp(bodyHeight * 0.64, 16, 28)
    const maxWidth = clamp(stageAreaWidth * 0.9, 230, stageAreaWidth)
    const minWidth = clamp(stageAreaWidth * 0.48, 138, maxWidth)
    const widthStep = data.length > 1 ? (maxWidth - minWidth) / (data.length - 1) : 0
    const labelFontSize = clamp(bodyHeight * 0.4, 12, 18)
    const metricFontSize = clamp(bodyHeight * 0.28, 9, 12)
    const metricVisible = bodyHeight >= 34 && maxWidth >= 260
    const gradientIdPrefix = `stage-funnel-${this.chart.id}`

    // 每个阶段使用独立渐变，保证多阶段颜色和立体椭圆效果互不串扰。
    const defs = data
      .map((item, index) => {
        const color = colors[index % colors.length]
        return `<linearGradient id="${gradientIdPrefix}-body-${index}" x1="0" x2="1" y1="0" y2="0">
          <stop offset="0%" stop-color="${adjustColor(color, -46)}"/>
          <stop offset="18%" stop-color="${adjustColor(color, -10)}"/>
          <stop offset="52%" stop-color="${adjustColor(color, 38)}"/>
          <stop offset="82%" stop-color="${color}"/>
          <stop offset="100%" stop-color="${adjustColor(color, -42)}"/>
        </linearGradient>
        <radialGradient id="${gradientIdPrefix}-top-${index}" cx="36%" cy="18%" r="76%">
          <stop offset="0%" stop-color="${adjustColor(color, 58)}"/>
          <stop offset="48%" stop-color="${adjustColor(color, 12)}"/>
          <stop offset="100%" stop-color="${adjustColor(color, -36)}"/>
        </radialGradient>`
      })
      .join('')

    const stages = data
      .map((item, index) => {
        // 阶段宽度逐级递减形成漏斗视觉，具体高度由容器和阶段数量共同决定。
        const stageWidth = maxWidth - widthStep * index
        const x = centerX - stageWidth / 2
        const y = top + index * (bodyHeight + gap)
        const color = colors[index % colors.length]
        const stageName = escapeSvgText(item.field || item.name)
        const subtitle = escapeSvgText(this.buildStageSubtitle(item, metricLimit, metricAliases))
        const visualMetric = escapeSvgText(this.getVisualMetric(item))
        const stageTitle = escapeSvgText(this.buildStageTitle(item))
        const bodyPath = [
          `M ${x} ${y + ellipseHeight / 2}`,
          `C ${x} ${y} ${x + stageWidth} ${y} ${x + stageWidth} ${y + ellipseHeight / 2}`,
          `L ${x + stageWidth} ${y + bodyHeight - ellipseHeight / 2}`,
          `C ${x + stageWidth} ${y + bodyHeight} ${x} ${y + bodyHeight} ${x} ${
            y + bodyHeight - ellipseHeight / 2
          }`,
          'Z'
        ].join(' ')
        // 前缘路径模拟底部椭圆，可在纯 SVG 中形成轻量立体效果。
        const frontRimPath = [
          `M ${x} ${y + bodyHeight - ellipseHeight / 2}`,
          `C ${x} ${y + bodyHeight} ${x + stageWidth} ${y + bodyHeight} ${x + stageWidth} ${
            y + bodyHeight - ellipseHeight / 2
          }`,
          `C ${x + stageWidth} ${y + bodyHeight - ellipseHeight * 0.1} ${x} ${
            y + bodyHeight - ellipseHeight * 0.1
          } ${x} ${y + bodyHeight - ellipseHeight / 2}`,
          'Z'
        ].join(' ')

        return `<g class="stage-funnel-segment" data-index="${index}" style="cursor:pointer">
          <title>${stageTitle}</title>
          <path class="stage-funnel-shadow" d="${bodyPath}" fill="${colorWithAlpha(color, 0.32)}"
            transform="translate(0 4)"/>
          <path class="stage-funnel-body" d="${bodyPath}" fill="url(#${gradientIdPrefix}-body-${index})"
            opacity="0.98" stroke="${colorWithAlpha('#ffffff', 0.24)}" stroke-width="1"/>
          <ellipse cx="${centerX}" cy="${y + ellipseHeight / 2}" rx="${stageWidth / 2}" ry="${
          ellipseHeight / 2
        }"
            fill="url(#${gradientIdPrefix}-top-${index})" opacity="0.98"
            stroke="${colorWithAlpha('#ffffff', 0.34)}" stroke-width="1"/>
          <path d="${frontRimPath}" fill="${colorWithAlpha(color, 0.28)}"/>
          <ellipse cx="${centerX}" cy="${y + bodyHeight - ellipseHeight / 2}" rx="${
          stageWidth / 2
        }" ry="${ellipseHeight / 2}"
            fill="none" stroke="${colorWithAlpha('#ffffff', 0.2)}" stroke-width="1"/>
          <text class="stage-funnel-value" x="${centerX - stageWidth / 2 + 22}" y="${
          y + bodyHeight * 0.56
        }"
            text-anchor="middle" font-size="${
              metricFontSize + 1
            }" font-weight="800" fill="rgba(255,255,255,.9)">${visualMetric}</text>
          <text class="stage-funnel-label" x="${centerX}" y="${
          y + bodyHeight * (metricVisible ? 0.43 : 0.58)
        }"
            text-anchor="middle" font-size="${labelFontSize}" font-weight="800" fill="#f8fdff">${stageName}</text>
          ${
            metricVisible
              ? `<text class="stage-funnel-subtitle" x="${centerX}" y="${
                  y + bodyHeight * 0.76
                }" text-anchor="middle"
            font-size="${metricFontSize}" fill="rgba(238,249,255,.88)">${subtitle}</text>`
              : ''
          }
        </g>`
      })
      .join('')

    const periodBlock = sideVisible
      ? // 宽屏展示左侧周期块，窄屏隐藏以保证阶段主体可读。
        `<g>
          <rect x="12" y="${height / 2 - 28}" width="94" height="46" rx="10" fill="${colorWithAlpha(
          periodColor,
          0.18
        )}"
            stroke="${colorWithAlpha(periodColor, 0.82)}" stroke-width="1.4"/>
          <rect x="18" y="${
            height / 2 - 22
          }" width="82" height="34" rx="8" fill="${periodColor}" opacity="0.9"/>
          <text x="59" y="${
            height / 2
          }" text-anchor="middle" font-size="18" font-weight="800" fill="#f8fbff">
            ${escapeSvgText(periodLabel)}
          </text>
        </g>`
      : ''
    const noteLines = this.buildMetricNoteLines(metricTitle)
    const noteBlock = sideVisible
      ? // 右侧指标说明只在空间足够时展示，避免压缩阶段标签。
        `<g>
          ${noteLines
            .map(
              (line, index) =>
                `<text x="${width - rightWidth + 6}" y="${height / 2 - 22 + index * 24}"
                  font-size="${index === 0 ? 15 : 13}" font-weight="${index === 0 ? 800 : 500}"
                  fill="${index === 0 ? noteTitleColor : noteTextColor}">${escapeSvgText(
                  line
                )}</text>`
            )
            .join('')}
        </g>`
      : ''

    container.innerHTML = `<svg width="100%" height="100%" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none"
      xmlns="http://www.w3.org/2000/svg" role="img" aria-label="${escapeSvgAttribute(
        this.chart.title
      )}">
      <defs>${defs}</defs>
      <style>
        .stage-funnel-segment .stage-funnel-body,
        .stage-funnel-segment .stage-funnel-label,
        .stage-funnel-segment .stage-funnel-subtitle,
        .stage-funnel-segment .stage-funnel-value { transition: opacity .16s ease, filter .16s ease; }
        .stage-funnel-segment:hover .stage-funnel-body { filter: brightness(1.12); }
        .stage-funnel-segment:hover .stage-funnel-label,
        .stage-funnel-segment:hover .stage-funnel-subtitle,
        .stage-funnel-segment:hover .stage-funnel-value { filter: drop-shadow(0 0 5px rgba(255,255,255,.45)); }
      </style>
      ${periodBlock}
      ${stages}
      ${noteBlock}
    </svg>`

    container.querySelectorAll<SVGGElement>('.stage-funnel-segment').forEach(segment => {
      segment.addEventListener('click', event => {
        const index = Number(segment.dataset.index)
        const item = data[index]
        if (!item || !this.action) {
          return
        }
        // 点击事件对齐 G2Plot 图表的 action 参数结构，便于联动和跳转复用。
        this.action({
          x: (event as MouseEvent).offsetX,
          y: (event as MouseEvent).offsetY,
          data: {
            data: item
          }
        })
      })
    })
    this.rendered = true
  }

  render = () => {
    this.paint()
  }

  destroy = () => {
    const container = this.getContainer()
    if (container) {
      container.innerHTML = ''
    }
    this.rendered = false
  }

  setState = () => undefined
}

/**
 * 阶段漏斗
 */
export class StageFunnel extends G2PlotChartView<any, any> {
  // 阶段漏斗是 DOM/SVG 自绘图表，只开放通用背景、边框、标题、提示和联动配置。
  properties: EditorProperty[] = [
    'background-overall-component',
    'border-style',
    'basic-style-selector',
    'label-selector',
    'tooltip-selector',
    'title-selector',
    'jump-set',
    'linkage'
  ]
  propertyInner: EditorPropertyInner = {
    'background-overall-component': ['all'],
    'border-style': ['all'],
    'basic-style-selector': ['colors', 'alpha'],
    'label-selector': ['fontSize', 'color'],
    'tooltip-selector': ['color', 'fontSize', 'backgroundColor', 'seriesTooltipFormatter', 'show'],
    'title-selector': [
      'show',
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
    ]
  }
  // 仅声明一个阶段维度和一个主指标，扩展 tooltip 字段用于展示副指标。
  axis: AxisType[] = ['xAxis', 'yAxis', 'filter', 'drill', 'extTooltip']
  axisConfig: AxisConfig = {
    xAxis: {
      name: '阶段 / 维度',
      type: 'd'
    },
    yAxis: {
      name: '阶段指标 / 数值',
      type: 'q',
      limit: 1
    }
  }

  async drawChart(drawOptions: G2PlotDrawOptions<any>): Promise<any> {
    const { chart, container, action } = drawOptions
    return new StageFunnelDomChart(container, chart, action)
  }

  protected setupOptions(chart: Chart, options: any): any {
    return options
  }

  constructor() {
    super('stage-funnel', [])
  }
}
