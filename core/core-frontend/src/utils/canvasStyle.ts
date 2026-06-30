import { cos, sin } from '@/utils/translate'
import {
  CHART_FONT_FAMILY_MAP_TRANS,
  DEFAULT_COLOR_CASE,
  DEFAULT_COLOR_CASE_DARK
} from '@/views/chart/components/editor/util/chart'

import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { useEmitt } from '@/hooks/web/useEmitt'
import { defaultTo, merge } from 'lodash-es'
import { formatterViewInfo } from '@/views/chart/components/js/formatter'
/** 主画布仓库用于读取全局主题、组件列表和视图配置 */
const dvMainStore = dvMainStoreWithOut()

/** 浅色主题主文本色 */
export const LIGHT_THEME_COLOR_MAIN = '#000000'
/** 浅色主题辅助线和次级文本色 */
export const LIGHT_THEME_COLOR_SLAVE1 = '#CCCCCC'
/** 浅色主题仪表板默认背景色 */
export const LIGHT_THEME_DASHBOARD_BACKGROUND = '#f5f6f7'
/** 浅色主题组件默认背景色 */
export const LIGHT_THEME_COMPONENT_BACKGROUND = '#FFFFFF'

/** 深色主题主文本色 */
export const DARK_THEME_COLOR_MAIN = '#FFFFFF'
/** 深色主题辅助线和次级文本色 */
export const DARK_THEME_COLOR_SLAVE1 = '#858383'
/** 深色主题大屏默认背景色 */
export const DARK_THEME_DASHBOARD_BACKGROUND = '#030B2E'
/** 深色主题组件默认背景色 */
export const DARK_THEME_COMPONENT_BACKGROUND = '#131E42'
/** 深色主题组件反色背景，用于提示层等特殊区域 */
export const DARK_THEME_COMPONENT_BACKGROUND_BACK = '#5a5c62'

/** 将组件样式转换为可绑定的 CSS 样式对象 */
export function getStyle(style, filter = []) {
  const needUnit = [
    'fontSize',
    'width',
    'height',
    'top',
    'left',
    'borderWidth',
    'letterSpacing',
    'borderRadius',
    'margin',
    'padding'
  ]

  const result = {}
  Object.keys(style).forEach(key => {
    if (!filter.includes(key)) {
      if (key !== 'rotate') {
        result[key] = style[key]
        if (key) {
          if (key === 'backgroundColor') {
            result[key] = colorRgb(style[key], style.opacity)
          }
          if (key === 'fontSize' && result[key] < 12) {
            result[key] = 12
          }
          if (needUnit.includes(key)) {
            result[key] += 'px'
          }
        }
      } else {
        result['transform'] = key + '(' + style[key] + 'deg)'
      }
    }
  })
  if (result['backgroundColor'] && (result['opacity'] || result['opacity'] === 0)) {
    delete result['opacity']
  }
  return result
}

// 获取一个组件旋转 rotate 后的样式
/** 计算组件旋转后的外接矩形边界 */
export function getComponentRotatedStyle(style) {
  style = { ...style }
  if (style.rotate !== 0) {
    const newWidth = style.width * cos(style.rotate) + style.height * sin(style.rotate)
    const diffX = (style.width - newWidth) / 2 // 旋转后范围变小是正值，变大是负值
    style.left += diffX
    style.right = style.left + newWidth

    const newHeight = style.height * cos(style.rotate) + style.width * sin(style.rotate)
    const diffY = (newHeight - style.height) / 2 // 始终是正
    style.top -= diffY
    style.bottom = style.top + newHeight

    style.width = newWidth
    style.height = newHeight
  } else {
    style.bottom = style.top + style.height
    style.right = style.left + style.width
  }

  return style
}

/** 将十六进制颜色和透明度转换为 rgba 表达式 */
export function colorRgb(color, opacity) {
  const reg = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/
  let sColor = color
  if (sColor && reg.test(sColor)) {
    sColor = sColor.toLowerCase()
    if (sColor.length === 4) {
      let sColorNew = '#'
      for (let i = 1; i < 4; i += 1) {
        sColorNew += sColor.slice(i, i + 1).concat(sColor.slice(i, i + 1))
      }
      sColor = sColorNew
    }
    // 处理六位的颜色值
    const sColorChange = []
    for (let i = 1; i < 7; i += 2) {
      sColorChange.push(parseInt('0x' + sColor.slice(i, i + 2)))
    }
    if (opacity || opacity === 0) {
      return 'rgba(' + sColorChange.join(',') + ',' + opacity + ')'
    } else {
      return 'rgba(' + sColorChange.join(',') + ')'
    }
  } else {
    return sColor
  }
}

/** 图表属性中需要随组件缩放调整的字段映射 */
export const customAttrTrans = {
  basicStyle: [
    'barWidth',
    'lineWidth',
    'lineSymbolSize',
    'leftLineWidth',
    'leftLineSymbolSize',
    'tableColumnWidth',
    'tableRowHeaderWidth'
  ],
  tableHeader: [
    'tableTitleFontSize',
    'tableTitleColFontSize',
    'tableTitleCornerFontSize',
    'tableTitleHeight'
  ],
  tableCell: ['tableItemFontSize', 'tableItemHeight'],
  misc: [
    'nameFontSize',
    'valueFontSize',
    'spaceSplit', // 间隔
    'scatterSymbolSize', // 气泡大小，散点图
    'radarSize', // 雷达占比
    'wordSizeRange',
    'wordSpacing'
  ],
  label: {
    fontSize: '',
    seriesLabelFormatter: ['fontSize'],
    proportionSeriesFormatter: ['fontSize']
  },
  tooltip: {
    fontSize: '',
    seriesTooltipFormatter: ['fontSize']
  },
  indicator: ['fontSize', 'suffixFontSize'],
  indicatorName: ['fontSize', 'nameValueSpacing']
}
/** 图表样式中需要随组件缩放调整的字段映射 */
export const customStyleTrans = {
  text: ['fontSize'],
  legend: ['fontSize'],
  xAxis: {
    fontSize: 'fontSize',
    axisLabel: ['fontSize'],
    splitLine: {
      lineStyle: ['width']
    },
    axisLine: {
      lineStyle: ['width']
    }
  },
  yAxis: {
    fontSize: 'fontSize',
    axisLabel: ['fontSize'],
    splitLine: {
      lineStyle: ['width']
    },
    axisLine: {
      lineStyle: ['width']
    }
  },
  yAxisExt: {
    fontSize: 'fontSize',
    axisLabel: ['fontSize'],
    splitLine: {
      lineStyle: ['width']
    },
    axisLine: {
      lineStyle: ['width']
    }
  },
  misc: {
    fontSize: 'fontSize',
    axisLine: {
      lineStyle: ['width']
    },
    axisTick: {
      lineStyle: ['width']
    },
    axisLabel: ['margin', 'fontSize'],
    splitLine: {
      lineStyle: ['width']
    }
  }
}

/** 主题背景相关的样式颜色映射 */
export const THEME_STYLE_TRANS_MAIN_BACK = {
  legend: {
    textStyle: ['color']
  },
  xAxis: {
    nameTextStyle: ['color'],
    axisLabel: ['color'],
    splitLine: {
      lineStyle: ['color']
    }
  },
  yAxis: {
    nameTextStyle: ['color'],
    axisLabel: ['color'],
    splitLine: {
      lineStyle: ['color']
    }
  },
  yAxisExt: {
    nameTextStyle: ['color'],
    axisLabel: ['color'],
    splitLine: {
      lineStyle: ['color']
    }
  },
  split: {
    name: ['color'],
    axisLine: {
      lineStyle: ['color']
    },
    axisTick: {
      lineStyle: ['color']
    },
    axisLabel: ['color'],
    splitLine: {
      lineStyle: ['color']
    }
  }
}

/** 主题主色相关的图表样式映射 */
export const THEME_STYLE_TRANS_MAIN = {
  legend: ['color'],
  xAxis: {
    // 一级属性直接字符串
    color: 'color',
    axisLabel: ['color']
  },
  yAxis: {
    color: '',
    axisLabel: ['color']
  },
  yAxisExt: {
    color: '',
    axisLabel: ['color']
  },
  misc: {
    color: 'color',
    axisTick: {
      lineStyle: ['color']
    },
    axisLabel: ['color']
  }
}

/** 主题辅助色相关的图表样式映射 */
export const THEME_STYLE_TRANS_SLAVE1 = {
  xAxis: {
    splitLine: {
      lineStyle: ['color']
    }
  },
  yAxis: {
    splitLine: {
      lineStyle: ['color']
    }
  },
  yAxisExt: {
    splitLine: {
      lineStyle: ['color']
    }
  },
  misc: {
    splitLine: {
      lineStyle: ['color']
    },
    axisLine: {
      lineStyle: ['color']
    }
  }
}

/** 主题主色相关的图表属性映射 */
export const THEME_ATTR_TRANS_MAIN = {
  label: {
    color: 'color',
    proportionSeriesFormatter: ['color']
  },
  tooltip: ['color'],
  misc: {
    bullet: {
      bar: {
        target: ['fill']
      }
    }
  }
}

/** 主题主色相关的符号类属性映射 */
export const THEME_ATTR_TRANS_MAIN_SYMBOL = {
  label: ['color']
}

/** 主题背景色相关的图表属性映射 */
export const THEME_ATTR_TRANS_SLAVE1_BACKGROUND = {
  tooltip: ['backgroundColor']
}

// 移动端特殊属性
/** 移动端固定值属性，避免线宽和折点随缩放过度变化 */
export const mobileSpecialProps = {
  lineWidth: 2, // 线宽固定值
  lineSymbolSize: 8 // 折点固定值
}

/** 按比例缩放数值或数值数组，并保证结果不低于 1 */
export function getScaleValue(propValue, scale) {
  if (propValue instanceof Array) {
    propValue.forEach((v, i) => {
      const val = Math.round(v * scale)
      propValue[i] = val > 1 ? val : 1
    })
    return propValue
  }
  const propValueTemp = Math.round(propValue * scale)
  return propValueTemp > 1 ? propValueTemp : 1
}
/** 数组型主题属性映射，用于标识需要逐项处理的系列配置 */
export const THEME_ATTR_TRANS_ARR_MAIN = {
  label: {
    seriesLabelFormatter: {
      isArray: []
    }
  }
}

/** 批量设置系列标签和提示框的颜色 */
export function seriesAdaptor(template, color) {
  template.label?.seriesLabelFormatter?.forEach(series => {
    series['color'] = color
  })

  template.label?.seriesTooltipFormatter?.forEach(series => {
    series['color'] = color
  })
}

/** 递归缩放图表属性和样式模板中的数值字段 */
export function recursionTransObj(template, infoObj, scale, terminal) {
  for (const templateKey in template) {
    // 如果是数组 进行赋值计算
    if (template[templateKey] instanceof Array) {
      // 词云图的大小区间，不需要缩放
      template[templateKey]
        .filter(field => field !== 'wordSizeRange')
        .forEach(templateProp => {
          if (
            infoObj[templateKey] &&
            (infoObj[templateKey][templateProp] || infoObj[templateKey].length)
          ) {
            // 移动端特殊属性值设置
            if (terminal === 'mobile' && mobileSpecialProps[templateProp] !== undefined) {
              infoObj[templateKey][templateProp] = mobileSpecialProps[templateProp]
            } else {
              // 数组依次设置
              if (infoObj[templateKey] instanceof Array) {
                infoObj[templateKey].forEach(v => {
                  v[templateProp] = getScaleValue(v[templateProp], scale)
                })
              } else {
                infoObj[templateKey][templateProp] = getScaleValue(
                  infoObj[templateKey][templateProp],
                  scale
                )
              }
            }
          }
        })
    } else if (typeof template[templateKey] === 'string') {
      // 一级字段为字符串直接赋值
      infoObj[templateKey] = getScaleValue(infoObj[templateKey], scale)
    } else {
      // 如果是对象 继续进行递归
      if (infoObj[templateKey]) {
        recursionTransObj(template[templateKey], infoObj[templateKey], scale, terminal)
      }
    }
  }
}

/** 递归把主题颜色写入图表属性和样式模板 */
export function recursionThemTransObj(template, infoObj, color) {
  for (const templateKey in template) {
    // 如果是数组 进行赋值计算
    if (template[templateKey] instanceof Array) {
      template[templateKey].forEach(templateProp => {
        if (infoObj[templateKey]) {
          infoObj[templateKey][templateProp] = color
        }
      })
    } else if (typeof template[templateKey] === 'string') {
      // 一级字段为字符串直接赋值
      infoObj[templateKey] = color
    } else {
      // 如果是对象 继续进行递归
      if (infoObj[templateKey]) {
        recursionThemTransObj(template[templateKey], infoObj[templateKey], color)
      }
    }
  }
}

/** 根据组件高宽缩放比例统一缩放图表属性和样式 */
export function componentScalePublic(chartInfo, heightScale, widthScale) {
  const scale = Math.min(heightScale, widthScale)
  // attr 缩放转换
  recursionTransObj(this.customAttrTrans, chartInfo.customAttr, scale, null)
  // style 缩放转换
  recursionTransObj(this.customStyleTrans, chartInfo.customStyle, scale, null)
  return chartInfo
}

/** 按当前画布主题同步图表样式、属性和默认配色 */
export function adaptCurTheme(customStyle, customAttr) {
  const canvasStyle = dvMainStore.canvasStyleData
  const themeColor = canvasStyle.dashboard.themeColor
  if (themeColor === 'light') {
    recursionThemTransObj(THEME_STYLE_TRANS_MAIN, customStyle, LIGHT_THEME_COLOR_MAIN)
    recursionThemTransObj(THEME_STYLE_TRANS_SLAVE1, customStyle, LIGHT_THEME_COLOR_SLAVE1)
    recursionThemTransObj(THEME_ATTR_TRANS_MAIN, customAttr, LIGHT_THEME_COLOR_MAIN)
    recursionThemTransObj(
      THEME_ATTR_TRANS_SLAVE1_BACKGROUND,
      customAttr,
      LIGHT_THEME_COMPONENT_BACKGROUND
    )
    seriesAdaptor(customAttr, LIGHT_THEME_COLOR_MAIN)
    merge(customAttr, DEFAULT_COLOR_CASE, canvasStyle.component.chartColor)
  } else {
    recursionThemTransObj(THEME_STYLE_TRANS_MAIN, customStyle, DARK_THEME_COLOR_MAIN)
    recursionThemTransObj(THEME_STYLE_TRANS_SLAVE1, customStyle, DARK_THEME_COLOR_SLAVE1)
    recursionThemTransObj(THEME_ATTR_TRANS_MAIN, customAttr, DARK_THEME_COLOR_MAIN)
    recursionThemTransObj(
      THEME_ATTR_TRANS_SLAVE1_BACKGROUND,
      customAttr,
      DARK_THEME_COMPONENT_BACKGROUND_BACK
    )
    seriesAdaptor(customAttr, DARK_THEME_COLOR_MAIN)
    merge(customAttr, DEFAULT_COLOR_CASE_DARK, canvasStyle.component.chartColor)
  }
  customStyle['text'] = {
    ...canvasStyle.component.chartTitle,
    title: customStyle['text']['title'],
    show: customStyle['text']['show'],
    remarkShow: customStyle['text']['remarkShow'],
    remark: customStyle['text']['remark']
  }
  const chartColor = canvasStyle.component.chartColor
  if (chartColor) {
    const labelSetting = chartColor.label
    if (labelSetting) {
      const label = customAttr.label
      if (label) {
        label.color = labelSetting.color
        label.fontSize = labelSetting.fontSize
      }
      const labelFormatter = customAttr.label?.seriesLabelFormatter
      if (labelFormatter && Array.isArray(labelFormatter)) {
        labelFormatter.forEach(item => {
          item.color = labelSetting.color
          item.fontSize = labelSetting.fontSize
        })
      }
    }
    const tooltipSetting = chartColor.tooltip
    if (tooltipSetting) {
      const tooltip = customAttr.tooltip
      if (tooltip) {
        tooltip.color = tooltipSetting.color
        tooltip.fontSize = tooltipSetting.fontSize
        tooltip.backgroundColor = tooltipSetting.backgroundColor
      }
      const tooltipFormatter = customAttr.tooltip?.seriesTooltipFormatter
      if (tooltipFormatter && Array.isArray(tooltipFormatter)) {
        tooltipFormatter.forEach(item => {
          item.color = tooltipSetting.color
          item.fontSize = tooltipSetting.fontSize
          item.backgroundColor = tooltipSetting.backgroundColor
        })
      }
    }
  }
}

/** 将全局字体映射到图表标题和指标卡字体配置 */
export function adaptTitleFontFamily(fontFamily, viewInfo) {
  if (viewInfo) {
    const _fontFamily = defaultTo(CHART_FONT_FAMILY_MAP_TRANS[fontFamily], fontFamily)
    viewInfo.customStyle['text']['fontFamily'] = _fontFamily
    // 针对指标卡同步数值和后缀字体
    if (viewInfo.type === 'indicator') {
      viewInfo.customAttr['indicator']['fontFamily'] = fontFamily
      viewInfo.customAttr['indicator']['suffixFontFamily'] = fontFamily
      viewInfo.customAttr['indicatorName']['fontFamily'] = fontFamily
    }
  }
}

/** 遍历所有图表组件，同步标题字体并触发重渲染 */
export function adaptTitleFontFamilyAll(fontFamily) {
  const componentData = dvMainStore.componentData
  componentData.forEach(item => {
    if (item.component === 'UserView') {
      const viewDetails = dvMainStore.canvasViewInfo[item.id]
      adaptTitleFontFamily(fontFamily, viewDetails)
      useEmitt().emitter.emit('renderChart-' + item.id, viewDetails)
    } else if (item.component === 'Group') {
      item.propValue.forEach(groupItem => {
        if (groupItem.component === 'UserView') {
          const viewDetails = dvMainStore.canvasViewInfo[groupItem.id]
          adaptTitleFontFamily(fontFamily, viewDetails)
          useEmitt().emitter.emit('renderChart-' + groupItem.id, viewDetails)
        }
      })
    } else if (item.component === 'Tabs') {
      item.propValue?.forEach(tabItem => {
        tabItem.componentData?.forEach(tabComponent => {
          if (tabComponent.component === 'UserView') {
            const viewDetails = dvMainStore.canvasViewInfo[tabComponent.id]
            adaptTitleFontFamily(fontFamily, viewDetails)
            useEmitt().emitter.emit('renderChart-' + tabComponent.id, viewDetails)
          }
        })
      })
    }
  })
}

/** 按当前主题同步单个组件的通用背景、文字颜色和图表配色 */
export function adaptCurThemeCommonStyle(component) {
  if (['Tabs'].includes(component.component)) {
    component.commonBackground['innerPadding'] = 0
  }
  // 大屏指定组件不套用统一背景，避免覆盖其自身视觉配置
  if (
    dvMainStore.dvInfo.type === 'dataV' &&
    [
      'CanvasBoard',
      'CanvasIcon',
      'Picture',
      'Group',
      'SvgTriangle',
      'SvgStar',
      'RectShape',
      'CircleShape',
      'Decoration',
      'DynamicBackground'
    ].includes(component.component)
  ) {
    component.commonBackground['backgroundColorSelect'] = false
    component.commonBackground['innerPadding'] = 0
  } else {
    const commonStyle = dvMainStore.canvasStyleData.component.chartCommonStyle
    for (const key in commonStyle) {
      component.commonBackground[key] = commonStyle[key]
    }
  }
  // 通用文字颜色按当前主题切换
  if (component.style.color) {
    if (dvMainStore.canvasStyleData.dashboard.themeColor === 'light') {
      component.style.color = LIGHT_THEME_COLOR_MAIN
    } else {
      component.style.color = DARK_THEME_COLOR_MAIN
    }
  }
  if (component.component === 'UserView') {
    // 图表组件需要同步主题、格式化规则并触发重渲染
    const curViewInfo = dvMainStore.canvasViewInfo[component.id]
    adaptCurTheme(curViewInfo.customStyle, curViewInfo.customAttr)
    formatterViewInfo(curViewInfo, dvMainStore.canvasStyleData.component.formatterItem)
    useEmitt().emitter.emit('renderChart-' + component.id, curViewInfo)
  } else if (component.component === 'Group') {
    component.propValue.forEach(groupItem => {
      adaptCurThemeCommonStyle(groupItem)
    })
  } else if (['Tabs', 'Screen'].includes(component.component)) {
    if (dvMainStore.canvasStyleData.dashboard.themeColor === 'light') {
      component.style.headFontColor = LIGHT_THEME_COLOR_MAIN
      component.style.headFontActiveColor = LIGHT_THEME_COLOR_MAIN
    } else {
      component.style.headFontColor = DARK_THEME_COLOR_MAIN
      component.style.headFontActiveColor = DARK_THEME_COLOR_MAIN
    }
    component.propValue?.forEach(tabItem => {
      tabItem.componentData?.forEach(tabComponent => {
        adaptCurThemeCommonStyle(tabComponent)
      })
    })
  } else if (component.component === 'VQuery') {
    const viewInfo = dvMainStore.canvasViewInfo[component.id]
    if (viewInfo) {
      adaptCurThemeFilterStyleAllKeyComponent(viewInfo)
    }
  }

  return component
}

/** 遍历画布组件并同步当前主题的通用样式 */
export function adaptCurThemeCommonStyleAll() {
  const componentData = dvMainStore.componentData
  componentData.forEach(item => {
    adaptCurThemeCommonStyle(item)
  })
}

/** 过滤组件主题同步时只需要的视图信息结构 */
interface CanvasViewInfo {
  type: string
  customStyle: {
    component: object
  }
}

/** 筛选器样式中需要同步主题色的字段 */
const colors = ['labelColor', 'borderColor', 'text', 'bgColor']
/** 与颜色字段一一对应的启用开关字段 */
const colorsSwitch = ['borderShow', 'textColorShow', 'bgColorShow']

/** 将当前主题筛选器样式同步到单个查询组件 */
export function adaptCurThemeFilterStyleAllKeyComponent(component) {
  if (isFilterComponent(component.type)) {
    const filterStyle = dvMainStore.canvasStyleData.component.filterStyle
    colors.forEach(styleKey => {
      component.customStyle.component[styleKey] = filterStyle[styleKey]
      const index = colors.indexOf(styleKey)
      if (index !== -1) {
        component.customStyle.component[colorsSwitch[index]] = true
      }
    })
  }
}

/** 将指定筛选器样式字段同步到所有查询组件 */
export function adaptCurThemeFilterStyleAll(styleKey) {
  const componentViewData = Object.values(dvMainStore.canvasViewInfo) as CanvasViewInfo[]
  const filterStyle = dvMainStore.canvasStyleData.component.filterStyle
  componentViewData.forEach(item => {
    if (isFilterComponent(item.type)) {
      item.customStyle.component[styleKey] = filterStyle[styleKey]
      const index = colors.indexOf(styleKey)
      if (index !== -1) {
        item.customStyle.component[colorsSwitch[index]] = true
      }
    }
  })
}

/** 判断组件类型是否为查询筛选组件 */
export function isFilterComponent(component) {
  return ['VQuery'].includes(component)
}

/** 判断组件类型是否为 Tabs 组件 */
export function isTabComponent(component) {
  return ['Tabs'].includes(component)
}
