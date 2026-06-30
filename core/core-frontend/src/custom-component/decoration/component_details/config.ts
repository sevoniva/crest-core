import DecorationBoard1 from '@/custom-component/decoration/component_details/DecorationBoard1.vue'
import DecorationBoard2 from '@/custom-component/decoration/component_details/DecorationBoard2.vue'
import DecorationBoard3 from '@/custom-component/decoration/component_details/DecorationBoard3.vue'
import DecorationBoard4 from '@/custom-component/decoration/component_details/DecorationBoard4.vue'
import DecorationBoard5 from '@/custom-component/decoration/component_details/DecorationBoard5.vue'
import DecorationBoard6 from '@/custom-component/decoration/component_details/DecorationBoard6.vue'
import DecorationBoard7 from '@/custom-component/decoration/component_details/DecorationBoard7.vue'
import DecorationBoard8 from '@/custom-component/decoration/component_details/DecorationBoard8.vue'
import DecorationBoard9 from '@/custom-component/decoration/component_details/DecorationBoard9.vue'
import DecorationBoard10 from '@/custom-component/decoration/component_details/DecorationBoard10.vue'
import Decoration1 from '@/custom-component/decoration/component_details/Decoration1.vue'
import Decoration2 from '@/custom-component/decoration/component_details/Decoration2.vue'
import Decoration3 from '@/custom-component/decoration/component_details/Decoration3.vue'
import Decoration4 from '@/custom-component/decoration/component_details/Decoration4.vue'
import Decoration5 from '@/custom-component/decoration/component_details/Decoration5.vue'

const boardInfoMap = {
  DecorationBoard1: DecorationBoard1,
  DecorationBoard2: DecorationBoard2,
  DecorationBoard3: DecorationBoard3,
  DecorationBoard4: DecorationBoard4,
  DecorationBoard5: DecorationBoard5,
  DecorationBoard6: DecorationBoard6,
  DecorationBoard7: DecorationBoard7,
  DecorationBoard8: DecorationBoard8,
  DecorationBoard9: DecorationBoard9,
  DecorationBoard10: DecorationBoard10,
  Decoration1: Decoration1,
  Decoration2: Decoration2,
  Decoration3: Decoration3,
  Decoration4: Decoration4,
  Decoration5: Decoration5
}

// 衔接当前组件交互和状态同步
export const findDecoration = name => {
  return boardInfoMap[name]
}

// 衔接当前组件交互和状态同步
export const calcTwoPointDistance = (pointA, pointB) => {
  const minusX = Math.abs(pointA[0] - pointB[0])
  const minusY = Math.abs(pointA[1] - pointB[1])

  return Math.sqrt(Math.pow(minusX, 2) + Math.pow(minusY, 2))
}

/**
 * @description 获取多个点，每个点之间的距离
 * @param {Point[]} points
 * @return {number[]}
 */
export function getPointDistances(points) {
  return new Array(points.length - 1)
    .fill(0)
    .map((_, i) => calcTwoPointDistance(points[i], points[i + 1]))
}

// 计算颜色配置并返回样式结果
export function customMergeColor(defaultColor: string[], newColor: string[] = []) {
  return defaultColor.map((defaultVal, index) => {
    return newColor && newColor[index] !== null ? newColor[index] : defaultVal
  })
}
