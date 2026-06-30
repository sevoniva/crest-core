import { deepCopy } from './utils'
import { divide, multiply } from 'mathjs'
import { dvMainStoreWithOut } from '@/store/modules/data-visualization/dvMain'
import { storeToRefs } from 'pinia'
import { groupItemStyleAdaptor, groupSizeStyleAdaptor } from '@/utils/style'
import { nextTick } from 'vue'

const dvMainStore = dvMainStoreWithOut()
const { componentData, curComponentIndex, canvasStyleData } = storeToRefs(dvMainStore)

const needToChangeAttrs = [
  'top',
  'left',
  'width',
  'height',
  'fontSize',
  'activeFontSize',
  'letterSpacing'
]
const needToChangeDirectionAttrs = {
  width: ['left', 'width', 'fontSize', 'activeFontSize', 'letterSpacing'],
  height: ['top', 'height']
}

// 兼容旧调用入口，将缩放适配委托给统一缩放逻辑
export function changeSizeWithScaleAdaptor(scale) {
  return changeComponentsSizeWithScale(scale)
}

// 根据新的画布缩放比例调整组件尺寸
export function changeSizeWithScale(scale) {
  return changeComponentsSizeWithScale(scale)
}

// 递归调整组件、分组和页签内部组件的缩放尺寸
function changeComponentsSizeWithScaleCircle(componentDataCopy, scale) {
  if (!componentDataCopy || !Array.isArray(componentDataCopy)) {
    return
  }
  componentDataCopy?.forEach(component => {
    if (component.style) {
      Object.keys(component.style)?.forEach(key => {
        if (needToChangeDirectionAttrs.width.includes(key)) {
          // 根据原来的比例获取样式原来的尺寸
          // 再用原来的尺寸 * 现在的比例得出新的尺寸
          if (!!component.style[key]) {
            component.style[key] = format(
              getOriginStyle(component.style[key], canvasStyleData.value.scale),
              scale
            )
          }
        } else if (needToChangeDirectionAttrs.height.includes(key)) {
          // 根据原来的比例获取样式原来的尺寸
          // 再用原来的尺寸 * 现在的比例得出新的尺寸
          component.style[key] = format(
            getOriginStyle(component.style[key], canvasStyleData.value.scaleHeight),
            scale
          )
        }
      })
    }
    if (['Group'].includes(component.component)) {
      groupSizeStyleAdaptor(component)
      const parentStyle = component.style
      component.propValue.forEach(componentInner => {
        if (['Tabs'].includes(componentInner.component)) {
          componentInner.propValue.forEach(tabItem => {
            changeComponentsSizeWithScaleCircle(tabItem.componentData, scale)
          })
        } else {
          changeComponentsSizeWithScaleCircle(componentInner.propValue, scale)
          groupItemStyleAdaptor(componentInner, parentStyle)
        }
      })
    } else if (['Tabs'].includes(component.component)) {
      component.propValue.forEach(tabItem => {
        changeComponentsSizeWithScaleCircle(tabItem.componentData, scale)
      })
    }
    // 如果是分组组件 则要进行分组内部组件groupStyle进行深度计算
    // 计算逻辑 Group 中样式 * groupComponent.groupStyle[sonKey].
    if (['Group', 'Tabs'].includes(component.component)) {
      try {
        nextTick(() => groupSizeStyleAdaptor(component))
      } catch (e) {
        // 旧Group适配
        console.error('group adaptor error:' + e)
      }
    }
  })
}

// 克隆当前画布组件并按比例更新样式尺寸
function changeComponentsSizeWithScale(scale) {
  const componentDataCopy = deepCopy(componentData.value)
  changeComponentsSizeWithScaleCircle(componentDataCopy, scale)
  dvMainStore.setComponentData(componentDataCopy)
  // 更新画布数组后，需要重新设置当前组件，否则在改变比例后，直接拖动圆点改变组件大小不会生效
  dvMainStore.setCurComponent({
    component: componentData.value[curComponentIndex.value],
    index: curComponentIndex.value
  })

  // 分开保存初始化宽高比例
  dvMainStore.setCanvasStyle({
    ...canvasStyleData.value,
    scaleWidth: scale,
    scaleHeight: scale,
    scale
  })
}
// 根据外部传入的组件引用和画布样式同步缩放尺寸
export function changeRefComponentsSizeWithScale(componentDataRef, canvasStyleDataRef, scale) {
  componentDataRef.forEach(component => {
    Object.keys(component.style).forEach(key => {
      if (needToChangeAttrs.includes(key)) {
        if (key === 'fontSize' && component.style[key] === '') return
        // 根据原来的比例获取样式原来的尺寸
        // 再用原来的尺寸 * 现在的比例得出新的尺寸
        component.style[key] = format(
          getOriginStyle(component.style[key], canvasStyleDataRef.scale),
          scale
        )
      }
    })
  })
  canvasStyleDataRef.scale = scale
}

// 按宽高两个方向递归调整引用组件的尺寸
export function changeRefComponentsSizeWithScalePointCircle(
  componentDataRef,
  canvasStyleDataRef,
  scaleWidth,
  scaleHeight,
  outScale
) {
  componentDataRef.forEach(component => {
    Object.keys(component.style)?.forEach(key => {
      if (needToChangeDirectionAttrs.width.includes(key)) {
        // 根据原来的比例获取样式原来的尺寸
        // 再用原来的尺寸 * 现在的比例得出新的尺寸
        component.style[key] = format(
          getOriginStyle(component.style[key], canvasStyleDataRef.scale || outScale),
          scaleWidth
        )
      } else if (needToChangeDirectionAttrs.height.includes(key)) {
        // 根据原来的比例获取样式原来的尺寸
        // 再用原来的尺寸 * 现在的比例得出新的尺寸
        component.style[key] = format(
          getOriginStyle(component.style[key], canvasStyleDataRef.scaleHeight || outScale),
          scaleHeight
        )
      }
    })
    if (component.component === 'Group') {
      changeRefComponentsSizeWithScalePointCircle(
        component.propValue,
        canvasStyleDataRef,
        scaleWidth,
        scaleHeight,
        outScale
      )
    }
  })
}

// 根据独立宽高缩放比例更新引用组件和画布缩放状态
export function changeRefComponentsSizeWithScalePoint(
  componentDataRef,
  canvasStyleDataRef,
  scaleWidth,
  scaleHeight,
  outScale
) {
  changeRefComponentsSizeWithScalePointCircle(
    componentDataRef,
    canvasStyleDataRef,
    scaleWidth,
    scaleHeight,
    outScale
  )
  canvasStyleDataRef.scale = scaleWidth
  canvasStyleDataRef.scaleWidth = scaleWidth
  canvasStyleDataRef.scaleHeight = scaleHeight
}

const needToChangeAttrs2 = ['width', 'height', 'fontSize']
// 按当前画布比例调整单个组件的基础尺寸样式
export function changeComponentSizeWithScale(component, scale = canvasStyleData.value.scale) {
  Object.keys(component.style).forEach(key => {
    if (needToChangeAttrs2.includes(key)) {
      if (key === 'fontSize' && component.style[key] === '') return
      component.style[key] = format(component.style[key], scale)
    }
  })
}

// 将原始数值按目标缩放比例转换为展示数值
function format(value, scale) {
  return multiply(value, divide(parseFloat(scale), 100))
}

// 根据当前缩放比例反推组件样式的原始数值
function getOriginStyle(value = 0, scale) {
  if (!value) {
    value = 0
  }
  return divide(value, divide(parseFloat(scale), 100))
}
