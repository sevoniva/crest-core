import { useI18n } from '@/hooks/web/useI18n'
import { deepCopy } from '@/utils/utils'
import { formatterItem, isEnLocal } from '@/views/chart/components/js/formatter-default'
const { t } = useI18n()

// 默认调色板会被图表实例、主题切换和样式面板共同引用，字段名需要保持和 ChartAttr 持久化结构一致。
export const DEFAULT_COLOR_CASE: DeepPartial<ChartAttr> = {
  basicStyle: {
    colorScheme: 'default',
    colors: [
      '#1E90FF',
      '#90EE90',
      '#00CED1',
      '#E2BD84',
      '#7A90E0',
      '#3BA272',
      '#2BE7FF',
      '#0A8ADA',
      '#FFD700'
    ],
    alpha: 100,
    gradient: false,
    mapStyle: 'normal',
    areaBaseColor: '#FFFFFF',
    areaBorderColor: '#303133',
    gaugeStyle: 'default',
    tableBorderColor: '#E6E7E4',
    tableScrollBarColor: 'rgba(0, 0, 0, 0.15)',
    zoomButtonColor: '#aaa',
    zoomBackground: '#fff'
  },
  misc: {
    flowMapConfig: {
      lineConfig: {
        mapLineAnimate: true,
        mapLineGradient: false,
        mapLineSourceColor: '#1E90FF',
        mapLineTargetColor: '#90EE90'
      }
    },
    nameFontColor: '#000000',
    valueFontColor: '#5470c6'
  },
  tableHeader: {
    tableHeaderBgColor: '#1E90FF',
    tableHeaderCornerBgColor: '#1E90FF',
    tableHeaderColBgColor: '#1E90FF',
    tableHeaderFontColor: '#000000',
    tableHeaderCornerFontColor: '#000000',
    tableHeaderColFontColor: '#000000'
  },
  tableCell: {
    tableItemBgColor: '#FFFFFF',
    tableFontColor: '#000000',
    tableItemSubBgColor: '#EEEEEE'
  },
  label: {
    color: '#000000',
    fontSize: 12
  },
  tooltip: {
    color: '#000000',
    fontSize: 12,
    backgroundColor: '#FFFFFF'
  }
}

// 浅色主题只覆盖和主题强相关的颜色项，其他结构沿用通用默认值，避免保存后的图表配置出现字段缺口。
export const DEFAULT_COLOR_CASE_LIGHT: DeepPartial<ChartAttr> = {
  basicStyle: {
    colorScheme: 'default',
    colors: [
      '#1E90FF',
      '#90EE90',
      '#00CED1',
      '#E2BD84',
      '#7A90E0',
      '#3BA272',
      '#2BE7FF',
      '#0A8ADA',
      '#FFD700'
    ],
    alpha: 100,
    gradient: false,
    mapStyle: 'normal',
    areaBaseColor: '#FFFFFF',
    areaBorderColor: '#303133',
    gaugeStyle: 'default',
    tableBorderColor: '#E6E7E4',
    tableScrollBarColor: 'rgba(0, 0, 0, 0.15)',
    zoomButtonColor: '#aaa',
    zoomBackground: '#fff'
  },
  misc: {
    flowMapConfig: {
      lineConfig: {
        mapLineAnimate: true,
        mapLineGradient: false,
        mapLineSourceColor: '#146C94',
        mapLineTargetColor: '#576CBC'
      }
    },
    nameFontColor: '#000000',
    valueFontColor: '#5470c6'
  },
  tableHeader: {
    tableHeaderBgColor: '#1E90FF',
    tableHeaderCornerBgColor: '#1E90FF',
    tableHeaderColBgColor: '#1E90FF',
    tableHeaderFontColor: '#000000',
    tableHeaderCornerFontColor: '#000000',
    tableHeaderColFontColor: '#000000'
  },
  tableCell: {
    tableItemBgColor: '#FFFFFF',
    tableFontColor: '#000000',
    tableItemSubBgColor: '#1E90FF'
  },
  label: {
    color: '#000000',
    fontSize: 12
  },
  tooltip: {
    color: '#000000',
    fontSize: 12,
    backgroundColor: '#FFFFFF'
  }
}

// 深色主题默认值面向大屏和暗色仪表板，表格、地图和提示框颜色需要同时兼顾编辑态与发布态。
export const DEFAULT_COLOR_CASE_DARK: DeepPartial<ChartAttr> = {
  basicStyle: {
    colorScheme: 'default',
    colors: [
      '#1E90FF',
      '#90EE90',
      '#00CED1',
      '#E2BD84',
      '#7A90E0',
      '#3BA272',
      '#2BE7FF',
      '#0A8ADA',
      '#FFD700'
    ],
    alpha: 100,
    gradient: false,
    mapStyle: 'darkblue',
    areaBaseColor: '#5470C6',
    areaBorderColor: '#EBEEF5',
    gaugeStyle: 'default',
    tableBorderColor: '#CCCCCC',
    tableScrollBarColor: 'rgba(255, 255, 255, 0.5)',
    zoomButtonColor: '#fff',
    zoomBackground: '#000'
  },
  misc: {
    flowMapConfig: {
      lineConfig: {
        mapLineGradient: false,
        mapLineSourceColor: '#146C94',
        mapLineTargetColor: '#576CBC'
      }
    },
    nameFontColor: '#ffffff',
    valueFontColor: '#5470c6'
  },
  tableHeader: {
    tableHeaderBgColor: '#1E90FF',
    tableHeaderCornerBgColor: '#1E90FF',
    tableHeaderColBgColor: '#1E90FF',
    tableHeaderFontColor: '#FFFFFF',
    tableHeaderCornerFontColor: '#FFFFFF',
    tableHeaderColFontColor: '#FFFFFF'
  },
  tableCell: {
    tableItemBgColor: '#131E42',
    tableFontColor: '#ffffff',
    tableItemSubBgColor: '#1E90FF'
  },
  label: {
    color: '#FFFFFF',
    fontSize: 12
  },
  tooltip: {
    color: '#FFFFFF',
    fontSize: 12,
    backgroundColor: '#5A5C62'
  }
}

// 标签页样式拆成基础值和明暗主题值，供选项卡组件和图表样式面板复用。
export const TAB_COMMON_STYLE_BASE = {
  headPosition: 'left'
}
export const TAB_COMMON_STYLE_LIGHT = {
  ...TAB_COMMON_STYLE_BASE,
  headFontColor: '#000000',
  headFontActiveColor: '#000000',
  headBorderColor: '#ffffff',
  headBorderActiveColor: '#ffffff'
}
export const TAB_COMMON_STYLE_DARK = {
  ...TAB_COMMON_STYLE_BASE,
  headFontColor: '#ffffff',
  headFontActiveColor: '#ffffff',
  headBorderColor: '#000000',
  headBorderActiveColor: '#000000'
}

// 高级交互图标颜色跟随主题切换，字段名与 senior 配置中的样式读取逻辑保持一致。
export const SENIOR_STYLE_SETTING_LIGHT = {
  linkageIconColor: '#a6a6a6',
  drillLayerColor: '#a6a6a6',
  pagerColor: '#a6a6a6',
  pagerSize: 14
}

export const SENIOR_STYLE_SETTING_DARK = {
  linkageIconColor: '#ffffff',
  drillLayerColor: '#ffffff',
  pagerColor: '#ffffff',
  pagerSize: 14
}

// 查询组件默认样式覆盖筛选器标题、标签和值域文本，作为画布组件新建时的统一外观入口。
export const FILTER_COMMON_STYLE_BASE = {
  layout: 'horizontal',
  titleLayout: 'left'
}

export const FILTER_COMMON_STYLE_LIGHT = {
  ...FILTER_COMMON_STYLE_BASE,
  labelColor: '#1f2329',
  titleColor: '#1f2329',
  color: '#1f2329',
  borderColor: '#bbbfc4',
  text: '#1f2329',
  bgColor: '#FFFFFF'
}

export const FILTER_COMMON_STYLE_DARK = {
  ...FILTER_COMMON_STYLE_BASE,
  labelColor: '#ffffff',
  titleColor: '#ffffff',
  color: '#FFFFFF',
  borderColor: '#484747',
  text: '#AFAFAF',
  bgColor: '#131C42'
}

// 选项卡色彩配置保留旧字段名，兼容历史图表在 customAttr 中保存的 tab 样式结构。
export const DEFAULT_TAB_COLOR_CASE_BASE = {
  headPosition: 'left'
}

// 暗色选项卡默认融入深色画布，边框色沿用历史字段以兼容旧组件读取。
export const DEFAULT_TAB_COLOR_CASE_DARK = {
  ...DEFAULT_TAB_COLOR_CASE_BASE,
  headFontColor: '#FFFFFF',
  headFontActiveColor: '#FFFFFF',
  headBorderColor: '#131E42',
  headBorderActiveColor: '#131E42'
}

// 浅色选项卡字段结构与暗色配置保持一致，便于主题切换时整块替换。
export const DEFAULT_TAB_COLOR_CASE_LIGHT = {
  ...DEFAULT_TAB_COLOR_CASE_BASE,
  headFontColor: '#OOOOOO',
  headFontActiveColor: '#OOOOOO',
  headBorderColor: '#OOOOOO',
  headBorderActiveColor: '#OOOOOO'
}

// 杂项配置承载非坐标轴类图表的尺寸、范围和专属行为，新图表类型优先在对应子对象下扩展。
export const DEFAULT_MISC: ChartMiscAttr = {
  // 半径和角度类字段覆盖饼图、仪表盘和液态图，默认值尽量保证新建图表可直接渲染。
  pieInnerRadius: 0,
  pieOuterRadius: 80,
  radarShape: 'polygon',
  radarSize: 80,
  gaugeMinType: 'fix',
  gaugeMinField: {
    id: '',
    summary: ''
  },
  gaugeMin: 0,
  gaugeMaxType: 'dynamic',
  gaugeMaxField: {
    id: '',
    summary: ''
  },
  gaugeMax: undefined,
  gaugeStartAngle: 225,
  gaugeEndAngle: -45,
  nameFontSize: 18,
  valueFontSize: 18,
  nameValueSpace: 10,
  valueFontColor: '#5470c6',
  valueFontFamily: 'Microsoft YaHei',
  valueFontIsBolder: false,
  valueFontIsItalic: false,
  valueLetterSpace: 0,
  valueFontShadow: false,
  showName: true,
  nameFontColor: '#000000',
  nameFontFamily: 'Microsoft YaHei',
  nameFontIsBolder: false,
  nameFontIsItalic: false,
  nameLetterSpace: '0',
  nameFontShadow: false,
  treemapWidth: 80,
  treemapHeight: 80,
  liquidMax: undefined,
  liquidMaxType: 'dynamic',
  liquidMaxField: {
    id: '',
    summary: ''
  },
  liquidSize: 80,
  liquidShape: 'circle',
  hPosition: 'center',
  vPosition: 'center',
  mapPitch: 0,
  wordSizeRange: [8, 32],
  wordSpacing: 6,
  mapAutoLegend: true,
  mapLegendMax: 0,
  mapLegendMin: 0,
  mapLegendNumber: 9,
  mapLegendRangeType: 'quantize',
  mapLegendCustomRange: [],
  // 流向地图的线和点分开配置，便于只调整线路动画或点位样式时不影响另一类图元。
  flowMapConfig: {
    lineConfig: {
      mapLineAnimate: true,
      mapLineType: 'arc',
      mapLineWidth: 1,
      mapLineAnimateDuration: 3,
      mapLineGradient: false,
      mapLineSourceColor: '#1E90FF',
      mapLineTargetColor: '#90EE90',
      alpha: 100
    },
    pointConfig: {
      text: {
        color: '#146C94',
        fontSize: 10
      },
      point: {
        color: '#146C94',
        size: 4,
        animate: false,
        speed: 0.01
      }
    }
  },
  wordCloudAxisValueRange: {
    auto: true,
    min: 0,
    max: 0,
    fieldId: undefined
  },
  // 子弹图保留 ranges、measures、target 三段结构，和渲染层的语义分区保持一致。
  bullet: {
    bar: {
      ranges: {
        fill: ['rgba(0,128,255,0.3)'],
        size: 20,
        showType: 'dynamic',
        fixedRangeNumber: 3,
        symbol: 'circle',
        symbolSize: 4
      },
      measures: {
        fill: ['rgba(0,128,255,1)'],
        size: 15,
        symbol: 'circle',
        symbolSize: 4
      },
      target: {
        fill: 'rgb(0,0,0)',
        size: 20,
        showType: 'dynamic',
        value: 0,
        symbol: 'line',
        symbolSize: 4
      }
    }
  },
  liquidShowBorder: false,
  liquidBorderWidth: 4,
  liquidBorderDistance: 8
}

// 标记线默认只保存条件容器，具体规则由阈值编辑器和后端图表计算流程共同补齐。
export const DEFAULT_MARK = {
  fieldId: '',
  conditions: []
}
// 标签配置同时服务普通标签、系列标签、占比标签和转化率标签，默认关闭高成本展示项。
export const DEFAULT_LABEL: ChartLabelAttr = {
  show: false,
  childrenShow: true,
  position: 'top',
  color: '#909399',
  fontSize: 12,
  formatter: '',
  labelLine: {
    show: true
  },
  labelFormatter: formatterItem,
  reserveDecimalCount: 2,
  labelShadow: false,
  labelBgColor: '',
  labelShadowColor: '',
  quotaLabelFormatter: formatterItem,
  showDimension: true,
  showQuota: false,
  showProportion: true,
  seriesLabelFormatter: [],
  conversionTag: {
    show: false,
    precision: 2,
    text: t('chart.conversion_rate')
  },
  showTotal: false,
  totalFontSize: 12,
  totalColor: '#FFF',
  totalFormatter: formatterItem,
  showStackQuota: false,
  fullDisplay: false,
  proportionSeriesFormatter: {
    show: false,
    color: '#000',
    fontSize: 12,
    formatterCfg: {
      decimalCount: 2
    }
  }
}
// 提示框配置需要覆盖静态 hover 和轮播提示两种场景，默认使用组件内置格式化器。
export const DEFAULT_TOOLTIP: ChartTooltipAttr = {
  show: true,
  trigger: 'item',
  confine: true,
  fontSize: 12,
  color: '#909399',
  tooltipFormatter: formatterItem,
  backgroundColor: '#ffffff',
  seriesTooltipFormatter: [],
  carousel: {
    enable: false,
    stayTime: 3,
    intervalTime: 0
  }
}
// 透视表汇总配置区分行列方向，字段结构与后端导出汇总逻辑保持同名映射。
export const DEFAULT_TABLE_TOTAL: ChartTableTotalAttr = {
  row: {
    showGrandTotals: true,
    showSubTotals: true,
    reverseLayout: false,
    reverseSubLayout: false,
    label: t('chart.total_show'),
    subLabel: t('chart.sub_total_show'),
    subTotalsDimensions: [],
    subTotalsDimensionsNew: true,
    calcTotals: {
      aggregation: 'SUM',
      cfg: []
    },
    calcSubTotals: {
      aggregation: 'SUM',
      cfg: []
    },
    totalSort: 'none',
    totalSortField: ''
  },
  col: {
    showGrandTotals: true,
    showSubTotals: true,
    reverseLayout: false,
    reverseSubLayout: false,
    label: t('chart.total_show'),
    subLabel: t('chart.sub_total_show'),
    subTotalsDimensions: [],
    calcTotals: {
      aggregation: 'SUM',
      cfg: []
    },
    calcSubTotals: {
      aggregation: 'SUM',
      cfg: []
    },
    totalSort: 'none', // 透视表列汇总排序方向，可选升序、降序或不排序。
    totalSortField: ''
  }
}
// 表头配置包含普通表头、分组表头和辅助说明表头，默认值要兼容旧版明细表和透视表。
export const DEFAULT_TABLE_HEADER: ChartTableHeaderAttr = {
  // 序号列表头使用国际化文案，避免导出时出现固定中文列名。
  indexLabel: t('relation.index'),
  showIndex: false,
  tableHeaderAlign: 'left',
  tableHeaderCornerAlign: 'left',
  tableHeaderColAlign: 'left',
  tableHeaderBgColor: '#1E90FF',
  tableHeaderCornerBgColor: '#1E90FF',
  tableHeaderColBgColor: '#1E90FF',
  tableHeaderFontColor: '#000000',
  tableHeaderCornerFontColor: '#000000',
  tableHeaderColFontColor: '#000000',
  tableTitleFontSize: 12,
  tableTitleCornerFontSize: 12,
  tableTitleColFontSize: 12,
  tableTitleHeight: 36,
  tableHeaderSort: false,
  showColTooltip: false,
  showRowTooltip: false,
  showTableHeader: true,
  showHorizonBorder: true,
  showVerticalBorder: true,
  isItalic: false,
  isCornerItalic: false,
  isColItalic: false,
  isBolder: true,
  isCornerBolder: true,
  isColBolder: true,
  headerGroup: false,
  headerGroupConfig: {
    // columns 描述树形表头结构，meta 保存字段级补充信息，二者由配置弹窗共同维护。
    columns: [],
    meta: []
  },
  auxiliaryHeader: {
    // 辅助表头默认关闭，开启后按字段维度保存说明文本和单独样式。
    enabled: false,
    rowHeight: 120,
    backgroundColor: '#FFFFFF',
    fontColor: '#1F2329',
    fontSize: 14,
    align: 'center',
    descriptions: []
  },
  rowHeaderFreeze: true,
  alignConfig: []
}
// 单元格配置控制表格明细渲染、冻结列行和相邻单元格合并，默认保持明细表可读性优先。
export const DEFAULT_TABLE_CELL: ChartTableCellAttr = {
  tableFontColor: '#000000',
  tableItemAlign: 'right',
  tableItemBgColor: '#FFFFFF',
  tableItemFontSize: 12,
  tableItemHeight: 36,
  enableTableCrossBG: false,
  tableItemSubBgColor: '#EEEEEE',
  showTooltip: false,
  showHorizonBorder: true,
  showVerticalBorder: true,
  isItalic: false,
  isBolder: false,
  tableFreeze: false,
  // 冻结行列数量默认从 0 开始，避免新建表格出现不可预期的固定区域。
  tableColumnFreezeHead: 0,
  tableRowFreezeHead: 0,
  mergeCells: true,
  alignConfig: []
}
// 图表标题样式作为画布标题和图表标题的共用结构，备注字段用于发布态提示说明。
export const DEFAULT_TITLE_STYLE: ChartTextStyle = {
  show: true,
  fontSize: 16,
  color: '#ffffff',
  hPosition: 'left',
  vPosition: 'top',
  isItalic: false,
  isBolder: true,
  remarkShow: false,
  remark: '',
  remarkBackgroundColor: '#ffffff',
  fontFamily: '',
  letterSpace: '0',
  fontShadow: false
}

// 指标值样式包含主值和后缀两套字体配置，默认保证数字卡片在暗色画布上可直接展示。
export const DEFAULT_INDICATOR_STYLE: ChartIndicatorStyle = {
  show: true,
  fontSize: 20,
  color: '#5470C6ff',
  hPosition: 'center',
  vPosition: 'center',
  isItalic: false,
  isBolder: true,
  fontFamily: 'Microsoft YaHei',
  letterSpace: 0,
  fontShadow: false,
  backgroundColor: '',

  suffixEnable: true,
  suffix: '',
  suffixFontSize: 14,
  suffixColor: '#5470C6ff',
  suffixIsItalic: false,
  suffixIsBolder: true,
  suffixFontFamily: 'Microsoft YaHei',
  suffixLetterSpace: 0,
  suffixFontShadow: false
}
// 指标名称样式独立于指标值，便于名称位置和数值字号分别调整。
export const DEFAULT_INDICATOR_NAME_STYLE: ChartIndicatorNameStyle = {
  show: true,
  fontSize: 18,
  color: '#ffffffff',
  isItalic: false,
  isBolder: true,
  fontFamily: 'Microsoft YaHei',
  letterSpace: 0,
  fontShadow: false,
  nameValueSpacing: 0,
  namePosition: 'bottom'
}

// 标题基础样式不携带主题色，明暗主题只覆盖颜色，减少主题切换时的字段差异。
export const DEFAULT_TITLE_STYLE_BASE: ChartTextStyle = {
  show: true,
  fontSize: 16,
  hPosition: 'left',
  vPosition: 'top',
  isItalic: false,
  isBolder: true,
  remarkShow: false,
  remark: '',
  fontFamily: '',
  letterSpace: '0',
  fontShadow: false,
  color: '',
  remarkBackgroundColor: ''
}

export const DEFAULT_TITLE_STYLE_LIGHT = {
  // 浅色主题标题默认使用深色文字，备注弹层保持白底以贴合编辑器表单视觉。
  ...DEFAULT_TITLE_STYLE_BASE,
  color: '#000000',
  remarkBackgroundColor: '#ffffff'
}

export const DEFAULT_TITLE_STYLE_DARK = {
  // 深色主题标题默认使用白色文字，备注背景与暗色提示框保持一致。
  ...DEFAULT_TITLE_STYLE_BASE,
  color: '#FFFFFF',
  remarkBackgroundColor: '#5A5C62'
}

export const DEFAULT_LEGEND_STYLE_BASE: ChartLegendStyle = {
  // 图例基础结构不区分明暗主题，主题配置只覆盖文字颜色等视觉字段。
  show: true,
  hPosition: 'center',
  vPosition: 'bottom',
  orient: 'horizontal',
  icon: 'circle',
  color: '#333333',
  fontSize: 12,
  size: 4,
  showRange: true,
  sort: 'none',
  customSort: []
}

// 兼容旧图表的通用图例默认值，新图表主题初始化优先使用明暗主题版本。
export const DEFAULT_LEGEND_STYLE: ChartLegendStyle = {
  show: true,
  hPosition: 'center',
  vPosition: 'bottom',
  orient: 'horizontal',
  icon: 'circle',
  color: '#333333',
  fontSize: 12,
  size: 4,
  showRange: true,
  sort: 'none',
  customSort: []
}

// 浅色图例强调正文可读性，保留基础布局和排序配置。
export const DEFAULT_LEGEND_STYLE_LIGHT: ChartLegendStyle = {
  ...DEFAULT_LEGEND_STYLE_BASE,
  color: '#333333',
  fontSize: 12
}

// 深色图例只覆盖文字颜色，避免主题切换影响图例位置和交互排序。
export const DEFAULT_LEGEND_STYLE_DARK: ChartLegendStyle = {
  ...DEFAULT_LEGEND_STYLE_BASE,
  color: '#ffffff',
  fontSize: 12
}

// 图表边距默认由组件自动计算，手动边距值仅在用户切换到固定模式后参与渲染。
export const DEFAULT_MARGIN_STYLE = {
  marginModel: 'auto',
  marginTop: 40,
  marginBottom: 44,
  marginLeft: 15,
  marginRight: 10
}

// X 轴默认展示类目标签，数值格式化配置保留给混合图和横向图表复用。
export const DEFAULT_XAXIS_STYLE: ChartAxisStyle = {
  show: true,
  position: 'bottom',
  nameShow: false,
  name: '',
  color: '#333333',
  fontSize: 12,
  axisLabel: {
    show: true,
    color: '#333333',
    fontSize: 12,
    rotate: 0,
    formatter: '{value}',
    lengthLimit: 10
  },
  axisLine: {
    show: true,
    lineStyle: {
      color: '#cccccc',
      width: 1,
      style: 'solid'
    }
  },
  splitLine: {
    show: false,
    lineStyle: {
      color: '#cccccc',
      width: 1,
      style: 'solid'
    }
  },
  axisValue: {
    auto: true,
    min: 10,
    max: 100,
    split: 10,
    splitCount: 10
  },
  axisLabelFormatter: {
    // 单位语言随当前语言环境初始化，后续由格式化面板按用户选择持久化。
    type: 'auto',
    unitLanguage: isEnLocal ? 'en' : 'ch',
    unit: 1,
    suffix: '',
    decimalCount: 2,
    thousandSeparator: true
  }
}
// Y 轴默认展示分割线并隐藏轴线，适配大多数指标对比图的阅读习惯。
export const DEFAULT_YAXIS_STYLE: ChartAxisStyle = {
  show: true,
  position: 'left',
  nameShow: false,
  name: '',
  color: '#333333',
  fontSize: 12,
  axisLabel: {
    show: true,
    color: '#333333',
    fontSize: 12,
    rotate: 0,
    formatter: '{value}',
    lengthLimit: 10
  },
  axisLine: {
    show: false,
    lineStyle: {
      color: '#cccccc',
      width: 1,
      style: 'solid'
    }
  },
  splitLine: {
    show: true,
    lineStyle: {
      color: '#cccccc',
      width: 1,
      style: 'solid'
    }
  },
  axisValue: {
    auto: true,
    min: 10,
    max: 100,
    split: 10,
    splitCount: 10
  },
  axisLabelFormatter: {
    // Y 轴默认开启千分位，适配指标类图表的常见数值展示。
    type: 'auto',
    unitLanguage: isEnLocal ? 'en' : 'ch',
    unit: 1,
    suffix: '',
    decimalCount: 2,
    thousandSeparator: true
  }
}
// 扩展 Y 轴用于双轴和混合图，默认关闭分割线，避免和主轴网格重复。
export const DEFAULT_YAXIS_EXT_STYLE: ChartAxisStyle = {
  show: true,
  position: 'right',
  name: '',
  color: '#333333',
  fontSize: 12,
  axisLabel: {
    show: true,
    color: '#333333',
    fontSize: 12,
    rotate: 0,
    formatter: '{value}'
  },
  axisLine: {
    show: false,
    lineStyle: {
      color: '#cccccc',
      width: 1,
      style: 'solid'
    }
  },
  splitLine: {
    show: false,
    lineStyle: {
      color: '#cccccc',
      width: 1,
      style: 'solid'
    }
  },
  axisValue: {
    auto: true,
    min: 10,
    max: 100,
    split: 10,
    splitCount: 10
  },
  axisLabelFormatter: {
    // 扩展轴保留独立格式化配置，混合图左右轴可以使用不同单位。
    type: 'auto',
    unitLanguage: isEnLocal ? 'en' : 'ch',
    unit: 1,
    suffix: '',
    decimalCount: 2,
    thousandSeparator: true
  }
}
// 背景默认透明，颜色字段只在用户显式开启背景或边框圆角时参与渲染。
export const DEFAULT_BACKGROUND_COLOR = {
  color: '#ffffff',
  alpha: 0,
  borderRadius: 0
}
// 雷达、仪表等特殊图表的轴线样式集中在 miscStyle，避免污染普通 X/Y 轴配置。
export const DEFAULT_MISC_STYLE: ChartMiscStyle = {
  showName: false,
  color: '#999',
  fontSize: 12,
  axisColor: '#999',
  splitNumber: 5,
  axisLine: {
    show: true,
    lineStyle: {
      color: '#999999',
      width: 1,
      type: 'solid'
    }
  },
  axisTick: {
    show: false,
    length: 5,
    lineStyle: {
      color: '#999999',
      width: 1,
      type: 'solid'
    }
  },
  axisLabel: {
    show: false,
    rotate: 0,
    margin: 8,
    color: '#999999',
    fontSize: '12',
    formatter: '{value}'
  },
  splitLine: {
    show: true,
    lineStyle: {
      color: '#999999',
      width: 1,
      type: 'solid'
    }
  },
  splitArea: {
    show: true
  },
  axisValue: {
    auto: true,
    min: 10,
    max: 100,
    split: 10,
    splitCount: 10
  }
}
// 功能配置承载缩略轴、空值策略等运行时行为，默认关闭会改变数据形态的能力。
export const DEFAULT_FUNCTION_CFG: ChartFunctionCfg = {
  sliderShow: false,
  sliderRange: [0, 10],
  sliderBg: '#FFFFFF',
  sliderFillBg: '#BCD6F1',
  sliderTextColor: '#999999',
  emptyDataStrategy: 'breakLine',
  emptyDataCustomValue: '',
  // 字段级空值策略只在用户显式配置后生效，默认不改变任何字段数据。
  emptyDataFieldCtrl: []
}
// 辅助线默认只保留容器，具体线条由编辑器按字段和固定值规则追加。
export const DEFAULT_ASSIST_LINE_CFG: ChartAssistLineCfg = {
  enable: false,
  assistLine: []
}
// 阈值配置按图表族拆分，避免仪表盘、液态图、表格和文本标签之间互相解释错误。
export const DEFAULT_THRESHOLD: ChartThreshold = {
  enable: false,
  gaugeThreshold: '',
  liquidThreshold: '',
  labelThreshold: [],
  tableThreshold: [],
  textLabelThreshold: [],
  lineLabelThreshold: []
}
// 自动滚动默认关闭，开启后由渲染组件按行数、间隔和步长控制列表类图表滚动。
export const DEFAULT_SCROLL: ScrollCfg = {
  open: false,
  row: 1,
  interval: 2000,
  step: 50
}

// 气泡动画只描述展示效果，不改变地图或散点图的原始数据。
export const DEFAULT_BUBBLE_ANIMATE: BubbleCfg = {
  enable: false,
  speed: 1,
  rings: 1,
  type: 'wave'
}

// 象限图默认四区透明，只提供分割线和标签容器，具体业务文案由用户在样式面板录入。
export const DEFAULT_QUADRANT_STYLE: QuadrantAttr = {
  lineStyle: {
    stroke: '#aaa',
    lineWidth: 1,
    opacity: 0.5
  },
  regionStyle: [
    {
      fill: '#fdfcfc',
      fillOpacity: 0
    },
    {
      fill: '#fafdfa',
      fillOpacity: 0
    },
    {
      fill: '#fdfcfc',
      fillOpacity: 0
    },
    {
      fill: '#fafdfa',
      fillOpacity: 0
    }
  ],
  labels: [
    {
      content: '',
      style: {
        fill: '#000000',
        fillOpacity: 0.5,
        fontSize: 14
      }
    },
    {
      content: '',
      style: {
        fill: '#000000',
        fillOpacity: 0.5,
        fontSize: 14
      }
    },
    {
      content: '',
      style: {
        fill: '#000000',
        fillOpacity: 0.5,
        fontSize: 14
      }
    },
    {
      content: '',
      style: {
        fill: '#000000',
        fillOpacity: 0.5,
        fontSize: 14
      }
    }
  ]
}

// 颜色面板提供单色选择的快捷候选，和主题色板分离，避免影响已保存的系列配色。
export const COLOR_PANEL = [
  '#FF4500',
  '#FF8C00',
  '#FFD700',
  '#71AE46',
  '#00CED1',
  '#1E90FF',
  '#C71585',
  '#999999',
  '#000000',
  '#FFFFFF'
]

// 图表色板注册表用于样式面板展示和默认系列配色，value 是持久化标识，不应随文案调整。
export const COLOR_CASES = [
  {
    // default 是历史默认色板标识，新增色板不能复用该 value。
    name: t('chart.color_default'),
    value: 'default',
    colors: [
      '#1E90FF',
      '#90EE90',
      '#00CED1',
      '#E2BD84',
      '#7A90E0',
      '#3BA272',
      '#2BE7FF',
      '#0A8ADA',
      '#FFD700'
    ]
  },
  {
    // 复古色板用于对比度更高的经营类图表，保留既有顺序以避免系列颜色漂移。
    name: t('chart.color_retro'),
    value: 'retro',
    colors: [
      '#0780cf',
      '#765005',
      '#fa6d1d',
      '#0e2c82',
      '#b6b51f',
      '#da1f18',
      '#701866',
      '#f47a75',
      '#009db2'
    ]
  },
  {
    name: t('chart.color_elegant'),
    value: 'elegant',
    colors: [
      '#95a2ff',
      '#fa8080',
      '#ffc076',
      '#fae768',
      '#87e885',
      '#3cb9fc',
      '#73abf5',
      '#cb9bff',
      '#434348'
    ]
  },
  {
    // future 色板提供较柔和的多系列配色，适合浅色仪表板中的密集指标。
    name: t('chart.color_future'),
    value: 'future',
    colors: [
      '#63b2ee',
      '#76da91',
      '#f8cb7f',
      '#f89588',
      '#7cd6cf',
      '#9192ab',
      '#7898e1',
      '#efa666',
      '#eddd86'
    ]
  },
  {
    name: t('chart.color_gradual'),
    value: 'gradual',
    colors: [
      '#71ae46',
      '#96b744',
      '#c4cc38',
      '#ebe12a',
      '#eab026',
      '#e3852b',
      '#d85d2a',
      '#ce2626',
      '#ac2026'
    ]
  },
  {
    name: t('chart.color_simple'),
    value: 'simple',
    colors: [
      '#929fff',
      '#9de0ff',
      '#ffa897',
      '#af87fe',
      '#7dc3fe',
      '#bb60b2',
      '#433e7c',
      '#f47a75',
      '#009db2'
    ]
  },
  {
    name: t('chart.color_business'),
    value: 'business',
    colors: [
      '#194f97',
      '#555555',
      '#bd6b08',
      '#00686b',
      '#c82d31',
      '#625ba1',
      '#898989',
      '#9c9800',
      '#007f54'
    ]
  },
  {
    name: t('chart.color_gentle'),
    value: 'gentle',
    colors: [
      '#5b9bd5',
      '#ed7d31',
      '#70ad47',
      '#ffc000',
      '#4472c4',
      '#91d024',
      '#b235e6',
      '#02ae75',
      '#5b9bd5'
    ]
  },
  {
    // 科技色板主要服务暗色大屏，前几项颜色需要保持高亮度和高饱和度。
    name: t('chart.color_technology'),
    value: 'technology',
    colors: [
      '#05f8d6',
      '#0082fc',
      '#fdd845',
      '#22ed7c',
      '#09b0d3',
      '#1d27c9',
      '#f9e264',
      '#f47a75',
      '#009db2'
    ]
  },
  {
    name: t('chart.color_light'),
    value: 'light',
    colors: [
      '#884898',
      '#808080',
      '#82ae46',
      '#00a3af',
      '#ef8b07',
      '#007bbb',
      '#9d775f',
      '#fae800',
      '#5f9b3c'
    ]
  },
  {
    name: t('chart.color_classical'),
    value: 'classical',
    colors: [
      '#007bbb',
      '#ffdb4f',
      '#dd4b4b',
      '#2ca9e1',
      '#ef8b07',
      '#4a488e',
      '#82ae46',
      '#dd4b4b',
      '#bb9581'
    ]
  },
  {
    name: t('chart.color_fresh'),
    value: 'fresh',
    colors: [
      '#5f9b3c',
      '#75c24b',
      '#83d65f',
      '#aacf53',
      '#c7dc68',
      '#d8e698',
      '#e0ebaf',
      '#bbc8e6',
      '#e5e5e5'
    ]
  },
  {
    name: t('chart.color_energy'),
    value: 'energy',
    colors: [
      '#ef8b07',
      '#2a83a2',
      '#f07474',
      '#c55784',
      '#274a78',
      '#7058a3',
      '#0095d9',
      '#75c24b',
      '#808080'
    ]
  },
  {
    name: t('chart.color_red'),
    value: 'red',
    colors: [
      '#ff0000',
      '#ef8b07',
      '#4c6cb3',
      '#f8e944',
      '#69821b',
      '#9c5ec3',
      '#00ccdf',
      '#f07474',
      '#bb9581'
    ]
  },
  {
    name: t('chart.color_fast'),
    value: 'fast',
    colors: [
      '#fae800',
      '#00c039',
      '#0482dc',
      '#bb9581',
      '#ff7701',
      '#9c5ec3',
      '#00ccdf',
      '#00c039',
      '#ff7701'
    ]
  },
  {
    name: t('chart.color_spiritual'),
    value: 'spiritual',
    colors: [
      '#00a3af',
      '#4da798',
      '#57baaa',
      '#62d0bd',
      '#6ee4d0',
      '#86e7d6',
      '#aeede1',
      '#bde1e6',
      '#e5e5e5'
    ]
  }
]

// ECharts 选中态只提供轻量阴影，避免和各图表自定义 hover 样式冲突。
export const BASE_ECHARTS_SELECT = {
  itemStyle: {
    shadowBlur: 2
  }
}

// 原始字体值保留完整 fallback，用于读取旧配置和跨平台字体回显。
export const CHART_FONT_FAMILY_ORIGIN = [
  { name: t('chart.font_family_ya_hei'), value: 'Microsoft YaHei' },
  { name: t('chart.font_family_song_ti'), value: 'SimSun, "Songti SC", STSong' },
  { name: t('chart.font_family_hei_ti'), value: 'SimHei, Helvetica' },
  { name: t('chart.font_family_kai_ti'), value: 'KaiTi, "Kaiti SC", STKaiti' }
]

// 历史字体值向当前短名称映射，避免旧图表打开后样式面板无法选中。
export const CHART_FONT_FAMILY_MAP_TRANS = {
  'Microsoft YaHei': 'Microsoft YaHei',
  'SimSun, "Songti SC", STSong': 'SimSun',
  'SimHei, Helvetica': 'SimHei',
  'KaiTi, "Kaiti SC", STKaiti': 'KaiTi'
}

// 当前字体下拉只保存短名称，再由渲染层映射为平台兼容字体族。
export const CHART_FONT_FAMILY = [
  { name: t('chart.font_family_ya_hei'), value: 'Microsoft YaHei' },
  { name: t('chart.font_family_song_ti'), value: 'SimSun' },
  { name: t('chart.font_family_hei_ti'), value: 'SimHei' },
  { name: t('chart.font_family_kai_ti'), value: 'KaiTi' }
]

// 渲染字体映射补齐中文字体 fallback，降低不同操作系统下的字体缺失概率。
export const CHART_FONT_FAMILY_MAP = {
  'Microsoft YaHei': 'Microsoft YaHei',
  SimSun: 'SimSun, "Songti SC", STSong',
  SimHei: 'SimHei, Helvetica',
  KaiTi: 'KaiTi, "Kaiti SC", STKaiti'
}

// 字间距候选保持有限枚举，避免自由输入导致标题和指标文本在画布上不可控溢出。
export const CHART_FONT_LETTER_SPACE = [
  { name: '0px', value: 0 },
  { name: '1px', value: 1 },
  { name: '2px', value: 2 },
  { name: '3px', value: 3 },
  { name: '4px', value: 4 },
  { name: '5px', value: 5 },
  { name: '6px', value: 6 },
  { name: '7px', value: 7 },
  { name: '8px', value: 8 },
  { name: '9px', value: 9 },
  { name: '10px', value: 10 }
]

// 以下数据源暂不支持分页查询，图表查询需要走非分页或引擎侧兼容路径。
export const NOT_SUPPORT_PAGE_DATASET = [
  // 这些数据源的分页语义或驱动能力不一致，前端分页开关需避开它们。
  'kylin',
  'sqlServer',
  'es',
  'presto',
  'ds_doris',
  'StarRocks',
  'impala'
]

// 年、年月、年月日粒度支持同比环比计算，其余日期粒度需要先在字段层完成转换。
export const SUPPORT_Y_M = ['y', 'y_M', 'y_M_d']

// 地图线路默认配置供迁徙图和流向图复用，动画参数仅影响前端展示。
export const DEFAULT_MAP = {
  mapPitch: 0,
  lineType: 'line',
  lineWidth: 1,
  lineAnimate: true,
  lineAnimateDuration: 4,
  lineAnimateInterval: 0.5,
  lineAnimateTrailLength: 0.1
}

// 图表类型注册表决定左侧图表选择器、默认渲染器和图标入口，value 需要和后端图表处理器匹配。
export const CHART_TYPE_CONFIGS = [
  {
    category: 'quota',
    title: t('chart.chart_type_quota'),
    display: 'show',
    details: [
      {
        render: 'antv',
        category: 'quota',
        value: 'gauge',
        title: t('chart.chart_gauge'),
        icon: 'gauge'
      },
      {
        render: 'antv',
        category: 'quota',
        value: 'liquid',
        title: t('chart.chart_liquid'),
        icon: 'liquid'
      },
      {
        render: 'custom',
        category: 'quota',
        value: 'indicator',
        title: t('chart.chart_indicator'),
        icon: 'indicator'
      }
    ]
  },
  {
    category: 'table',
    title: t('chart.chart_type_table'),
    display: 'show',
    details: [
      {
        render: 'antv',
        category: 'table',
        value: 'table-info',
        title: t('chart.chart_table_info'),
        icon: 'table-info'
      },
      {
        render: 'antv',
        category: 'table',
        value: 'table-normal',
        title: t('chart.chart_table_normal'),
        icon: 'table-normal'
      },
      {
        render: 'antv',
        category: 'table',
        value: 'table-pivot',
        title: t('chart.chart_table_pivot'),
        icon: 'table-pivot'
      },
      {
        render: 'antv',
        category: 'table',
        value: 't-heatmap',
        title: t('chart.chart_table_heatmap'),
        icon: 't-heatmap'
      }
    ]
  },
  {
    category: 'trend',
    title: t('chart.chart_type_trend'),
    display: 'show',
    details: [
      {
        render: 'antv',
        category: 'trend',
        value: 'line',
        title: t('chart.chart_line'),
        icon: 'line'
      },
      {
        render: 'antv',
        category: 'trend',
        value: 'area',
        title: t('chart.chart_area'),
        icon: 'area'
      },
      {
        render: 'antv',
        category: 'trend',
        value: 'area-stack',
        title: t('chart.chart_area_stack'),
        icon: 'area-stack'
      },
      {
        render: 'antv',
        category: 'trend',
        value: 'cumulative-flow',
        title: '累积流图',
        icon: 'area-stack'
      }
    ]
  },
  {
    category: 'compare',
    title: t('chart.chart_type_compare'),
    display: 'show',
    details: [
      {
        render: 'antv',
        category: 'compare',
        value: 'bar',
        title: t('chart.chart_bar'),
        icon: 'bar'
      },
      {
        render: 'antv',
        category: 'compare',
        value: 'bar-stack',
        title: t('chart.chart_bar_stack'),
        icon: 'bar-stack'
      },
      {
        render: 'antv',
        category: 'compare',
        value: 'percentage-bar-stack',
        title: t('chart.chart_percentage_bar_stack'),
        icon: 'percentage-bar-stack'
      },
      {
        render: 'antv',
        category: 'compare',
        value: 'bar-group',
        title: t('chart.chart_bar_group'),
        icon: 'bar-group'
      },
      {
        render: 'antv',
        category: 'compare',
        value: 'bar-group-stack',
        title: t('chart.chart_bar_group_stack'),
        icon: 'bar-group-stack'
      },
      {
        render: 'antv',
        category: 'compare',
        value: 'waterfall',
        title: t('chart.chart_waterfall'),
        icon: 'waterfall'
      },
      {
        render: 'antv',
        category: 'compare',
        value: 'bar-horizontal',
        title: t('chart.chart_bar_horizontal'),
        icon: 'bar-horizontal'
      },
      {
        render: 'antv',
        category: 'compare',
        value: 'bar-stack-horizontal',
        title: t('chart.chart_bar_stack_horizontal'),
        icon: 'bar-stack-horizontal'
      },
      {
        render: 'antv',
        category: 'compare',
        value: 'percentage-bar-stack-horizontal',
        title: t('chart.chart_percentage_bar_stack_horizontal'),
        icon: 'percentage-bar-stack-horizontal'
      },
      {
        render: 'antv',
        category: 'compare',
        value: 'bar-range',
        title: t('chart.chart_bar_range'),
        icon: 'bar-range'
      },
      {
        render: 'antv',
        category: 'compare',
        value: 'bidirectional-bar',
        title: t('chart.chart_bidirectional_bar'),
        icon: 'bidirectional-bar'
      },
      {
        render: 'antv',
        category: 'compare',
        value: 'progress-bar',
        title: t('chart.chart_progress_bar'),
        icon: 'progress-bar'
      },
      {
        render: 'antv',
        category: 'trend',
        value: 'stock-line',
        title: t('chart.chart_stock_line'),
        icon: 'stock-line'
      },
      {
        render: 'antv',
        category: 'compare',
        value: 'bullet-graph',
        title: t('chart.bullet_chart'),
        icon: 'bullet-graph'
      }
    ]
  },
  {
    category: 'distribute',
    title: t('chart.chart_type_distribute'),
    display: 'show',
    details: [
      {
        render: 'antv',
        category: 'distribute',
        value: 'pie',
        title: t('chart.chart_pie'),
        icon: 'pie'
      },
      {
        render: 'antv',
        category: 'distribute',
        value: 'pie-donut',
        title: t('chart.chart_pie_donut'),
        icon: 'pie-donut'
      },
      {
        render: 'antv',
        category: 'distribute',
        value: 'pie-rose',
        title: t('chart.chart_pie_rose'),
        icon: 'pie-rose'
      },
      {
        render: 'antv',
        category: 'distribute',
        value: 'pie-donut-rose',
        title: t('chart.chart_pie_donut_rose'),
        icon: 'pie-donut-rose'
      },
      {
        render: 'antv',
        category: 'chart.chart_type_distribute',
        value: 'radar',
        title: t('chart.chart_radar'),
        icon: 'radar'
      },
      {
        render: 'antv',
        category: 'distribute',
        value: 'treemap',
        title: t('chart.chart_treemap'),
        icon: 'treemap'
      },
      {
        render: 'antv',
        category: 'distribute',
        value: 'word-cloud',
        title: t('chart.chart_word_cloud'),
        icon: 'word-cloud'
      }
    ]
  },
  {
    category: 'relation',
    title: t('chart.chart_type_relation'),
    display: 'show',
    details: [
      {
        render: 'antv',
        category: 'distribute',
        value: 'scatter',
        title: t('chart.chart_scatter'),
        icon: 'scatter'
      },
      {
        render: 'antv',
        category: 'distribute',
        value: 'quadrant',
        title: t('chart.chart_quadrant'),
        icon: 'quadrant'
      },
      {
        render: 'antv',
        category: 'distribute',
        value: 'funnel',
        title: t('chart.chart_funnel'),
        icon: 'funnel'
      },
      {
        render: 'antv',
        category: 'relation',
        value: 'stage-funnel',
        title: '阶段漏斗',
        icon: 'funnel'
      },
      {
        render: 'antv',
        category: 'relation',
        value: 'sankey',
        title: t('chart.chart_sankey'),
        icon: 'sankey'
      },
      {
        render: 'antv',
        category: 'distribute',
        value: 'circle-packing',
        title: t('chart.chart_circle_packing'),
        icon: 'circle-packing'
      },
      {
        render: 'antv',
        category: 'distribute',
        value: 'multi-scatter',
        title: '多维散点图',
        icon: 'multi-scatter'
      },
      {
        render: 'antv',
        category: 'relation',
        value: 'metric-matrix',
        title: '指标矩阵',
        icon: 'multi-scatter'
      }
    ]
  },
  {
    category: 'dual_axes',
    title: t('chart.chart_type_dual_axes'),
    display: 'show',
    details: [
      {
        render: 'antv',
        category: 'dual_axes',
        value: 'chart-mix',
        title: t('chart.chart_mix'),
        icon: 'chart-mix'
      },
      {
        render: 'antv',
        category: 'dual_axes',
        value: 'chart-mix-group',
        title: t('chart.chart_mix_group_column'),
        icon: 'chart-mix-group'
      },
      {
        render: 'antv',
        category: 'dual_axes',
        value: 'chart-mix-stack',
        title: t('chart.chart_mix_stack_column'),
        icon: 'chart-mix-stack'
      },
      {
        render: 'antv',
        category: 'dual_axes',
        value: 'chart-mix-dual-line',
        title: t('chart.chart_mix_dual_line'),
        icon: 'chart-mix-dual-line'
      }
    ]
  },
  {
    category: 'other',
    title: t('datasource.other'),
    display: 'hidden',
    details: [
      {
        render: 'custom',
        category: 'quota',
        value: 'rich-text',
        title: t('visualization.rich_text'),
        icon: 'rich-text'
      },
      {
        render: 'custom',
        category: 'quota',
        value: 'picture-group',
        title: t('visualization.picture_group'),
        icon: 'picture-group'
      }
    ]
  }
]

// 基础样式是新建图表的主默认值，包含表格、地图、柱线点面等多类图表的共同样式字段。
export const DEFAULT_BASIC_STYLE: ChartBasicStyle = {
  alpha: 100,
  tableBorderColor: '#CCCCCC',
  tableScrollBarColor: '#1f23294d',
  tableColumnMode: 'adapt',
  tableColumnWidth: 100,
  tableFieldWidth: [],
  tablePageMode: 'page',
  tablePageStyle: 'simple',
  tablePageSize: 20,
  gaugeStyle: 'default',
  colorScheme: 'default',
  colors: [
    '#5470c6',
    '#91cc75',
    '#fac858',
    '#ee6666',
    '#73c0de',
    '#3ba272',
    '#fc8452',
    '#9a60b4',
    '#ea7ccc'
  ],
  mapVendor: 'amap',
  gradient: false,
  lineWidth: 2,
  lineSymbol: 'circle',
  lineSymbolSize: 4,
  lineSmooth: true,
  barDefault: true,
  radiusColumnBar: 'rightAngle',
  columnBarRightAngleRadius: 20,
  columnWidthRatio: 60,
  barWidth: 40,
  barGap: 0.4,
  lineType: 'solid',
  scatterSymbol: 'circle',
  scatterSymbolSize: 8,
  radarShape: 'polygon',
  mapStyle: 'normal',
  heatMapType: 'heatmap',
  heatMapIntensity: 2,
  heatMapRadius: 20,
  areaBorderColor: '#EBEEF5',
  areaBaseColor: '#ffffff',
  mapSymbolOpacity: 0.7,
  mapSymbolStrokeWidth: 2,
  mapSymbol: 'circle',
  mapSymbolSize: 6,
  radius: 80,
  innerRadius: 60,
  showZoom: true,
  zoomButtonColor: '#aaa',
  zoomBackground: '#fff',
  tableLayoutMode: 'grid',
  defaultExpandLevel: 1,
  calcTopN: false,
  topN: 5,
  topNLabel: t('datasource.other'),
  gaugeAxisLine: true,
  gaugePercentLabel: true,
  showSummary: false,
  summaryLabel: t('chart.total_show'),
  seriesColor: [],
  layout: 'horizontal',
  mapSymbolSizeMin: 4,
  mapSymbolSizeMax: 30,
  showLabel: true,
  mapStyleUrl: '',
  autoFit: true,
  mapCenter: {
    longitude: 117.232,
    latitude: 39.354
  },
  zoomLevel: 7,
  customIcon: '',
  showHoverStyle: true,
  autoWrap: false,
  maxLines: 3,
  radarShowPoint: true,
  radarPointSize: 4,
  radarAreaColor: true,
  circleBorderColor: '#fff',
  circleBorderWidth: 0,
  circlePadding: 0,
  quotaPosition: 'col',
  quotaColLabel: t('dataset.value'),
  tableRowHeaderMode: 'adapt',
  tableRowHeaderWidth: 120,
  tableRowHeaderWidthPercent: 20
}

// 基础视图配置是新建图表的完整数据模型，字段结构需要同时满足编辑器、查询接口和发布态渲染。
export const BASE_VIEW_CONFIG = {
  id: '', // 图表主键，新建视图保存前保持为空。
  title: t('data_set.view'),
  sceneId: 0, // 所属仪表板或大屏场景标识。
  tableId: '', // 绑定数据集标识，取数前由编辑器写入。
  type: 'bar',
  render: 'antv',
  resultCount: 1000,
  resultMode: 'custom',
  refreshViewEnable: false,
  refreshTime: 5,
  refreshUnit: 'minute',
  // 轴字段、钻取字段和扩展字段按渲染器约定分组保存，避免不同图表族互相污染配置。
  xAxis: [],
  xAxisExt: [],
  yAxis: [],
  yAxisExt: [],
  extStack: [],
  drillFields: [],
  viewFields: [],
  extBubble: [],
  extLabel: [],
  extTooltip: [],
  customFilter: {},
  sortPriority: [],
  // 自定义属性承载样式、表格和指标卡配置，字段需要和样式面板的表单模型保持一致。
  customAttr: {
    basicStyle: DEFAULT_BASIC_STYLE,
    misc: DEFAULT_MISC,
    label: DEFAULT_LABEL,
    tooltip: DEFAULT_TOOLTIP,
    tableTotal: DEFAULT_TABLE_TOTAL,
    tableHeader: DEFAULT_TABLE_HEADER,
    tableCell: DEFAULT_TABLE_CELL,
    indicator: DEFAULT_INDICATOR_STYLE,
    indicatorName: DEFAULT_INDICATOR_NAME_STYLE,
    map: {
      id: '',
      level: 'world'
    }
  },
  // 自定义样式主要描述标题、图例、坐标轴和特殊轴线，不直接参与取数。
  customStyle: {
    text: DEFAULT_TITLE_STYLE,
    legend: DEFAULT_LEGEND_STYLE,
    xAxis: DEFAULT_XAXIS_STYLE,
    yAxis: DEFAULT_YAXIS_STYLE,
    yAxisExt: DEFAULT_YAXIS_EXT_STYLE,
    misc: DEFAULT_MISC_STYLE
  },
  // 高级配置承载运行态行为，默认关闭会改变数据展示节奏或数据含义的能力。
  senior: {
    functionCfg: DEFAULT_FUNCTION_CFG,
    assistLineCfg: DEFAULT_ASSIST_LINE_CFG,
    threshold: DEFAULT_THRESHOLD,
    scrollCfg: DEFAULT_SCROLL,
    areaMapping: {},
    bubbleCfg: DEFAULT_BUBBLE_ANIMATE
  },
  flowMapStartName: [],
  flowMapEndName: []
}

// 按画布缩放比例计算最小交互尺寸，避免组件在缩小时出现 0 宽或 0 高。
export function getScaleValue(propValue, scale) {
  const propValueTemp = Math.round(propValue * scale)
  return propValueTemp > 1 ? propValueTemp : 1
}

// 根据图表类型标识读取注册配置副本，调用方可以安全地修改返回对象。
export function getViewConfig(name) {
  let viewConfigResult = null
  CHART_TYPE_CONFIGS.forEach(category => {
    category.details.forEach(viewConfig => {
      if (viewConfig.value === name) {
        viewConfigResult = deepCopy(viewConfig)
      }
    })
  })
  return viewConfigResult
}
