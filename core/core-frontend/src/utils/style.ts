import { sin, cos, toPercent } from '@/utils/translate'
import { hexColorToRGBA } from '@/views/chart/components/js/util'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { isMainCanvas, isTabCanvas } from '@/utils/canvasUtils'
import { setBackgroundImageStyle } from '@/utils/backgroundStyleUtils'
/** 主画布仓库提供画布模式、移动端状态和主题配置 */
const dvMainStore = dvMainStoreWithOut()

/** 清理背景相关字段，避免移动端自定义背景继承 PC 端背景 */
function clearBackgroundStyle(style) {
  delete style.background
  delete style.backgroundColor
  delete style['background-color']
  delete style.backgroundImage
  delete style.backgroundRepeat
  delete style.backgroundPosition
  delete style.backgroundSize
}

/** 生成基础形状在画布上的定位和旋转样式 */
export function getShapeStyle(style) {
  const result = {}
  ;['width', 'height', 'top', 'left', 'rotate'].forEach(attr => {
    if (attr != 'rotate') {
      result[attr] = style[attr] + 'px'
    } else {
      result['transform'] = 'rotate(' + style[attr] + 'deg)'
    }
  })

  return result
}

/** 根据仪表板栅格或大屏自由布局生成组件容器样式 */
export function getShapeItemStyle(
  item,
  { dvModel, cellWidth, cellHeight, curGap, showPosition = 'preview' }
) {
  let result = {}
  if (dvModel === 'dashboard' && !item['isPlayer']) {
    result = {
      padding: curGap + 'px!important',
      width: cellWidth * item.sizeX + 'px',
      height: cellHeight * item.sizeY + 'px',
      left: cellWidth * (item.x - 1) + 'px',
      top: cellHeight * (item.y - 1) + 'px'
    }
  } else if (dvModel === 'dataV' && isTabCanvas(item.canvasId) && showPosition === 'preview') {
    result = {
      padding: curGap + 'px!important',
      width: toPercent(item.groupStyle.width),
      height: toPercent(item.groupStyle.height),
      top: toPercent(item.groupStyle.top),
      left: toPercent(item.groupStyle.left)
    }
  } else {
    result = {
      padding: curGap + 'px!important',
      width: item.style.width + 'px',
      height: item.style.height + 'px',
      left: item.style.left + 'px',
      top: item.style.top + 'px'
    }
  }

  return result
}

/** 将仪表板栅格位置同步回组件的像素样式 */
export function syncShapeItemStyle(item, cellWidth, cellHeight) {
  item.style.left = cellWidth * (item.x - 1)
  item.style.top = cellHeight * (item.y - 1)
  item.style.width = cellWidth * item.sizeX
  item.style.height = cellHeight * item.sizeY
}

/** 需要追加像素单位的样式字段集合 */
const needUnit = [
  'fontSize',
  'width',
  'height',
  'top',
  'left',
  'borderWidth',
  'letterSpacing',
  'borderRadius'
]

/** 生成 SVG 类组件可直接绑定的样式对象 */
export function getSVGStyle(style, filter = []) {
  const result = {}

  ;[
    'opacity',
    'width',
    'height',
    'top',
    'left',
    'rotate',
    'fontSize',
    'fontWeight',
    'lineHeight',
    'letterSpacing',
    'textAlign',
    'color'
  ].forEach(key => {
    if (!filter.includes(key)) {
      if (key != 'rotate') {
        if (style[key] !== '') {
          result[key] = style[key]

          if (needUnit.includes(key)) {
            result[key] += 'px'
          }
        }
      } else {
        result['transform'] = key + '(' + style[key] + 'deg)'
      }
    }
  })

  return result
}

/** 汇总组件基础样式和通用背景样式 */
export function getItemAllStyle(item, filter = []) {
  const style = item.style
  const commonBackground = item.commonBackground
  const result = {}
  Object.keys(style).forEach(key => {
    if (!filter.includes(key)) {
      if (key != 'rotate') {
        if (style[key] !== '') {
          result[key] = style[key]

          if (needUnit.includes(key)) {
            result[key] += 'px'
          }
        }
      } else {
        result['transform'] = key + '(' + style[key] + 'deg)'
      }
    }

    if (commonBackground) {
      // 附加背景样式
      let colorRGBA = ''
      if (commonBackground.backgroundColorSelect) {
        colorRGBA = hexColorToRGBA(commonBackground.backgroundColor, commonBackground.alpha)
      }
      if (colorRGBA) {
        result['backgroundColor'] = colorRGBA
      }
      if (
        commonBackground.backgroundImageEnable &&
        commonBackground.backgroundType === 'outerImage' &&
        typeof commonBackground.outerImage === 'string'
      ) {
        setBackgroundImageStyle(result, commonBackground.outerImage)
      }
    }
  })

  return result
}

/** 生成普通组件可直接绑定的样式对象 */
export function getStyle(style, filter = []) {
  const result = {}
  Object.keys(style).forEach(key => {
    if (!filter.includes(key)) {
      if (key != 'rotate') {
        if (style[key] !== '') {
          result[key] = style[key]

          if (needUnit.includes(key)) {
            result[key] += 'px'
          }
        }
      } else {
        result['transform'] = key + '(' + style[key] + 'deg)'
      }
    }
  })
  return result
}

// 获取一个组件旋转 rotate 后的样式
/** 计算组件旋转后的外接矩形位置和尺寸 */
export function getComponentRotatedStyle(style) {
  style = { ...style }
  if (style.rotate != 0) {
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

/** 根据画布背景、移动端配置和字体生成画布容器样式 */
export function getCanvasStyle(canvasStyleData, canvasId = 'canvas-main') {
  const {
    backgroundColorSelect,
    background,
    backgroundColor,
    backgroundImageEnable,
    fontSize,
    mobileSetting,
    fontFamily
  } = canvasStyleData
  const style = { fontSize: fontSize + 'px', color: canvasStyleData.color }
  if (isMainCanvas(canvasId)) {
    // 仪表板默认色#f5f6f7 大屏默认配色 #1a1a1a
    let colorRGBA = dvMainStore.dvInfo.type === 'dashboard' ? '#f5f6f7' : '#1a1a1a'
    if (backgroundColorSelect && backgroundColor) {
      colorRGBA = backgroundColor
    }
    if (colorRGBA) {
      style['backgroundColor'] = colorRGBA
    }
    if (backgroundImageEnable && typeof background === 'string') {
      setBackgroundImageStyle(style, background)
    }

    if (dvMainStore.mobileInPc && mobileSetting?.customSetting) {
      const { backgroundColorSelect, color, backgroundImageEnable, background } = mobileSetting
      clearBackgroundStyle(style)
      if (backgroundColorSelect && color) {
        style['backgroundColor'] = color
      }
      if (backgroundImageEnable && typeof background === 'string') {
        setBackgroundImageStyle(style, background)
      }
    }
    style['font-family'] = fontFamily + '!important'
  }

  return style
}

/** 建立分组内部组件相对父级的比例坐标，并转换为组内局部坐标 */
export function createGroupStyle(groupComponent) {
  const parentStyle = groupComponent.style
  groupComponent.propValue.forEach(component => {
    // 分组计算逻辑
    // 1.groupStyle记录left top width height 在出现分组缩放的时候进行等比例变更（缩放来源有两种a.整个大屏的缩放 b.分组尺寸的调整）
    // 2.component 内部进行位移或者尺寸的变更 要同步到这个比例中
    const style = { ...component.style }
    component.groupStyle.left = (style.left - parentStyle.left) / parentStyle.width
    component.groupStyle.top = (style.top - parentStyle.top) / parentStyle.height
    component.groupStyle.width = style.width / parentStyle.width
    component.groupStyle.height = style.height / parentStyle.height

    component.style.left = component.style.left - parentStyle.left
    component.style.top = component.style.top - parentStyle.top
  })
}

/** 根据 Tabs 可视区域尺寸还原内部组件样式 */
function dataVTabSizeStyleAdaptor(tabComponent) {
  const parentStyleAdaptor = { ...tabComponent.style }
  const offset = parentStyleAdaptor.showTabTitle ? 46 : 0
  const domId =
    dvMainStore.editMode === 'edit'
      ? 'component' + tabComponent.id
      : 'enlarge-inner-content' + tabComponent.id
  const tabDom = document.getElementById(domId)
  if (tabDom) {
    parentStyleAdaptor.height = tabDom.clientHeight - offset
    parentStyleAdaptor.width = tabDom.clientWidth
  } else {
    parentStyleAdaptor.height = parentStyleAdaptor.height - offset
  }

  tabComponent.propValue?.forEach(tabItem => {
    tabItem.componentData?.forEach(tabComponent => {
      groupItemStyleAdaptor(tabComponent, parentStyleAdaptor)
      if (['Group'].includes(tabComponent.component)) {
        groupSizeStyleAdaptor(tabComponent)
      }
    })
  })
}

/** 按父级尺寸和比例坐标还原分组内部组件的像素样式 */
export function groupItemStyleAdaptor(component, parentStyle) {
  // 分组还原逻辑
  // 当发上分组缩放是，要将内部组件按照比例转换
  const styleScale = component.groupStyle
  component.style.left = parentStyle.width * styleScale.left
  component.style.top = parentStyle.height * styleScale.top
  component.style.width = parentStyle.width * styleScale.width
  component.style.height = parentStyle.height * styleScale.height
}

/** 批量刷新 Tabs 内部组件的分组比例坐标 */
export function groupStyleRevertBatch(groupComponent, parentStyle) {
  if (groupComponent.component === 'Tabs') {
    groupComponent.propValue?.forEach(tabItem => {
      tabItem.componentData?.forEach(tabComponent => {
        groupStyleRevert(tabComponent, parentStyle)
      })
    })
  }
}

/** Tabs 容器尺寸变化时重新计算各页内部组件比例坐标 */
export function tabInnerStyleRevert(tabOuterComponent) {
  const parentStyle = {
    width: tabOuterComponent.style.width,
    height: tabOuterComponent.style.height - (tabOuterComponent.style.showTabTitle ? 46 : 0)
  }
  tabOuterComponent.propValue?.forEach(tabItem => {
    tabItem.componentData?.forEach(tabComponent => {
      groupStyleRevert(tabComponent, parentStyle)
    })
  })
}

/** 将内部组件当前像素样式转换为相对父级的比例样式 */
export function groupStyleRevert(innerComponent, parentStyle) {
  const innerStyle = { ...innerComponent.style }
  innerComponent.groupStyle.left = innerStyle.left / parentStyle.width
  innerComponent.groupStyle.top = innerStyle.top / parentStyle.height
  innerComponent.groupStyle.width = innerStyle.width / parentStyle.width
  innerComponent.groupStyle.height = innerStyle.height / parentStyle.height
}

/** 分组或 Tabs 尺寸变化时刷新内部组件样式 */
export function groupSizeStyleAdaptor(groupComponent) {
  if (groupComponent.component === 'Group') {
    const parentStyle = groupComponent.style
    groupComponent.propValue.forEach(component => {
      groupItemStyleAdaptor(component, parentStyle)
    })
  } else {
    dataVTabSizeStyleAdaptor(groupComponent)
  }
}

/** 新增组件到大屏 Tabs 时初始化位置并记录比例坐标 */
export function dataVTabComponentAdd(innerComponent, parentComponent) {
  // 新增到 Tabs 的组件先贴齐内容区域左上角
  innerComponent.style.top = 0
  innerComponent.style.left = 0
  const parentStyleAdaptor = { ...parentComponent.style }
  // 去掉tab头部高度
  parentStyleAdaptor.height = parentStyleAdaptor.height - (parentComponent.showTabTitle ? 46 : 0)
  groupStyleRevert(innerComponent, parentStyleAdaptor)
}
