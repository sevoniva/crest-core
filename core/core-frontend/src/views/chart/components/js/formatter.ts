import { find, merge } from 'lodash-es'
import { useI18n } from '@/hooks/web/useI18n'
import { parseJson } from '@/views/chart/components/js/util'
import { formatterItem, isEnLocal } from '@/views/chart/components/js/formatter-default'

export { formatterItem, isEnLocal }

/**
 * 国际化翻译函数
 */
const { t } = useI18n()

// 单位列表
export const unitType = [
  { name: t('chart.unit_none'), value: 1 },
  { name: t('chart.unit_thousand'), value: 1000 },
  { name: t('chart.unit_ten_thousand'), value: 10000 },
  { name: t('chart.unit_million'), value: 1000000 },
  { name: t('chart.unit_hundred_million'), value: 100000000 }
]
/**
 * 英文单位列表
 */
export const unitEnType = [
  { name: 'None', value: 1 },
  { name: 'Thousand (K)', value: 1000 },
  { name: 'Million (M)', value: 1000000 },
  { name: 'Billion (B)', value: 1000000000 }
]

/**
 * 按语言获取单位列表
 */
export function getUnitTypeList(lang) {
  if (isEnLocal) {
    return unitEnType
  }
  if (lang === 'ch') {
    return unitType
  }
  return unitEnType
}

/**
 * 校验并返回当前语言可用的单位值
 */
export function getUnitTypeValue(lang, value) {
  const list = getUnitTypeList(lang)
  const item = find(list, l => l.value === value)
  if (item) {
    return value
  }
  return 1
}

/**
 * 初始化格式化配置中的单位语言
 */
export function initFormatCfgUnit(cfg) {
  if (cfg && cfg.unitLanguage === undefined) {
    cfg.unitLanguage = 'ch'
  }
  if (cfg && isEnLocal) {
    cfg.unitLanguage = 'en'
  }
  onChangeFormatCfgUnitLanguage(cfg, cfg.unitLanguage)
}

/**
 * 切换格式化配置的单位语言
 */
export function onChangeFormatCfgUnitLanguage(cfg, lang) {
  cfg.unit = getUnitTypeValue(lang, cfg.unit)
}

// 格式化方式
export const formatterType = [
  { name: 'value_formatter_auto', value: 'auto' },
  { name: 'value_formatter_value', value: 'value' },
  { name: 'value_formatter_percent', value: 'percent' }
]

/**
 * 按格式化配置处理数值显示
 */
export function valueFormatter(value, formatter) {
  if (value === null || value === undefined) {
    return null
  }
  // 1.unit 2.decimal 3.thousand separator and suffix
  let result
  if (formatter.type === 'auto') {
    result = transSeparatorAndSuffix(transUnit(value, formatter), formatter)
  } else if (formatter.type === 'value') {
    result = transSeparatorAndSuffix(
      transDecimal(transUnit(value, formatter), formatter),
      formatter
    )
  } else if (formatter.type === 'percent') {
    value = value * 100
    result = transSeparatorAndSuffix(transDecimal(value, formatter), formatter)
  } else {
    result = value
  }
  return result
}

/**
 * 按单位缩放数值
 */
function transUnit(value, formatter) {
  initFormatCfgUnit(formatter)
  return value / formatter.unit
}

/**
 * 按小数位配置保留数值
 */
function transDecimal(value, formatter) {
  const resultV = retain(value, formatter.decimalCount) as string
  if (Object.is(parseFloat(resultV), -0)) {
    return resultV.slice(1)
  }
  return resultV
}

/**
 * 四舍五入并补齐指定小数位
 */
function retain(value, n) {
  if (!n) return Math.round(value)
  const tran = Math.round(value * Math.pow(10, n)) / Math.pow(10, n)
  let tranV = tran.toString()
  const newVal = tranV.indexOf('.')
  if (newVal < 0) {
    tranV += '.'
  }
  for (let i = tranV.length - tranV.indexOf('.'); i <= n; i++) {
    tranV += '0'
  }
  return tranV
}

/**
 * 添加千分位分隔符和单位后缀
 */
function transSeparatorAndSuffix(value, formatter) {
  let str = value + ''
  if (str.match(/^(\d)(\.\d)?e-(\d)/)) {
    str = value.toFixed(18).replace(/\.?0+$/, '')
  }
  if (formatter.thousandSeparator) {
    const thousandsReg = /(\d)(?=(\d{3})+$)/g
    const numArr = str.split('.')
    numArr[0] = numArr[0].replace(thousandsReg, '$1,')
    str = numArr.join('.')
  }
  if (formatter.type === 'percent') {
    str += '%'
    //百分比没有后缀，直接返回
    return str
  } else {
    const unit = formatter.unit

    if (formatter.unitLanguage === 'ch') {
      if (unit === 1000) {
        str += t('chart.unit_thousand')
      } else if (unit === 10000) {
        str += t('chart.unit_ten_thousand')
      } else if (unit === 1000000) {
        str += t('chart.unit_million')
      } else if (unit === 100000000) {
        str += t('chart.unit_hundred_million')
      }
    } else {
      if (unit === 1000) {
        str += 'K'
      } else if (unit === 1000000) {
        str += 'M'
      } else if (unit === 1000000000) {
        str += 'B'
      }
    }
  }
  return str + formatter.suffix.replace(/(^\s*)|(\s*$)/g, '')
}

/**
 * 根据最小值、最大值max和刻度数量tickCount
 * 计算一个nice最小刻度值
 * @param min
 * @param max
 * @param tickCount
 */
function niceMin(min, max, tickCount = 5) {
  // 数据的总跨度
  const range = max - min
  // 将范围均分为 tickCount-1 份， 得到每份的粗略步长
  const roughStep = range / (tickCount - 1)
  // 确定步长的数量级
  // 取步长的 10 为底的对数，向下取整，得到步长的数量级
  const exponent = Math.floor(Math.log10(roughStep))
  // 将步长取整到nice的倍数（如 1, 2, 5, 10, 20, 50...）
  const power = Math.pow(10, exponent)
  // 从中找出第一个大于等于粗略步长的nice步长
  const niceStep = [1, 2, 5, 10].map(mult => mult * power).find(step => step >= roughStep)
  // 将 min 向下取整到 niceStep 的整数倍，得到一个更整齐的起始值
  return Math.floor(min / niceStep) * niceStep
}

/**
 * 监听图例、缩略轴事件，计算y轴的nice最小刻度值
 * @param chart
 * @param newChart
 */
export const listenYAxisNiceMinEvents = (chart: Chart, newChart) => {
  const yAxis = parseJson(chart.customStyle).yAxis
  if (yAxis.axisValue?.auto) {
    newChart.on('legend-item-group:click', e => {
      if (e.view?.options?.scales) {
        const values = e.view.filteredData
          .map(d => d.value)
          ?.filter(v => v !== null && v !== undefined)
        const min = Math.min(...values)
        const max = Math.max(...values)
        e.view.options.scales.value.min = niceMin(min, max)
        e.view.render(true)
      }
    })
    newChart.on('slider:valuechanged', ev => {
      const values = ev.view.filteredData
        .map(d => d.value)
        ?.filter(v => v !== null && v !== undefined)
      const min = Math.min(...values)
      const max = Math.max(...values)
      if (max !== min) {
        ev.view.options.scales.value.min = niceMin(min, max)
      }
    })
  }
}

/**
 * 计算数据min值的nice值
 * @param chart
 * @param options
 * @param tmpOptions
 */
export const calcNiceMinValue = (chart, options, tmpOptions) => {
  let filteredData
  let cfg
  const senior = parseJson(chart.senior)
  if (senior.functionCfg && senior.functionCfg.sliderShow) {
    cfg = {
      start: senior.functionCfg.sliderRange[0] / 100,
      end: senior.functionCfg.sliderRange[1] / 100
    }
  }
  const data = options.data || []
  filteredData = data
  // 如果有缩略轴，则取缩略轴范围
  if (cfg && cfg.start !== undefined && cfg.end !== undefined) {
    const startIndex = Math.floor(cfg.start * data.length)
    const endIndex = Math.ceil(cfg.end * data.length)
    filteredData = data.slice(startIndex, endIndex)
  }
  const values = filteredData.map(d => d.value)?.filter(v => v !== null && v !== undefined)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const niceMinValue = niceMin(min, max)
  if (isNaN(niceMinValue)) {
    return tmpOptions
  }
  const axis = {
    yAxis: {
      ...tmpOptions.yAxis,
      min: niceMinValue
    }
  }
  return { ...tmpOptions, ...axis }
}

const unShowTooltipsFormatter = [
  'table-info',
  'table-normal',
  'table-pivot',
  'stock-line',
  'bullet-graph',
  'percentage-bar-stack-horizontal'
]
/**
 * 适配图表数字格式化属性
 * @param viewInfo
 * @param value
 */
export const formatterViewInfo = (viewInfo, value) => {
  viewInfo.xAxis.forEach(function (item) {
    item['formatterCfg'] = merge(item['formatterCfg'], value)
  })
  viewInfo.xAxisExt.forEach(function (item) {
    item['formatterCfg'] = merge(item['formatterCfg'], value)
  })
  viewInfo.yAxis.forEach(function (item) {
    item['formatterCfg'] = merge(item['formatterCfg'], value)
  })
  viewInfo.extStack.forEach(function (item) {
    item['formatterCfg'] = merge(item['formatterCfg'], value)
  })
  viewInfo.extBubble.forEach(function (item) {
    item['formatterCfg'] = merge(item['formatterCfg'], value)
  })
  viewInfo.extLabel.forEach(function (item) {
    item['formatterCfg'] = merge(item['formatterCfg'], value)
  })
  viewInfo.extTooltip.forEach(function (item) {
    item['formatterCfg'] = merge(item['formatterCfg'], value)
  })
  //customAttr
  viewInfo['customAttr']['label']['labelFormatter'] = merge(
    viewInfo['customAttr']['label']['labelFormatter'],
    value
  )
  viewInfo['customAttr']['label']['quotaLabelFormatter'] = merge(
    viewInfo['customAttr']['label']['quotaLabelFormatter'],
    value
  )
  viewInfo['customAttr']['label']['seriesLabelFormatter'].forEach(function (item) {
    item['formatterCfg'] = merge(item['formatterCfg'], value)
  })
  viewInfo['customAttr']['label']['totalFormatter'] = merge(
    viewInfo['customAttr']['label']['totalFormatter'],
    value
  )
  if (!unShowTooltipsFormatter.includes(viewInfo.type)) {
    viewInfo['customAttr']['tooltip']['tooltipFormatter'] = merge(
      viewInfo['customAttr']['tooltip']['tooltipFormatter'],
      value
    )
    viewInfo['customAttr']['tooltip']['seriesTooltipFormatter'].forEach(function (item) {
      item['formatterCfg'] = merge(item['formatterCfg'], value)
    })
  }

  //customStyle
  viewInfo['customStyle']['xAxis']['axisLabelFormatter'] = merge(
    viewInfo['customStyle']['xAxis']['axisLabelFormatter'],
    value
  )
  viewInfo['customStyle']['yAxis']['axisLabelFormatter'] = merge(
    viewInfo['customStyle']['yAxis']['axisLabelFormatter'],
    value
  )
  viewInfo['customStyle']['yAxisExt']['axisLabelFormatter'] = merge(
    viewInfo['customStyle']['yAxisExt']['axisLabelFormatter'],
    value
  )
}

// 维护表单数据和校验规则
export const mergeTooltipFormat = (item, type, value) => {
  if (!unShowTooltipsFormatter.includes(type)) {
    item['formatterCfg'] = merge(item['formatterCfg'], value)
  }
}
