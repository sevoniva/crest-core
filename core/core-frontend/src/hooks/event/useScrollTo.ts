import { ref, unref } from 'vue'

// 滚动动画入参定义，支持横向或纵向滚动位置
export interface ScrollToParams {
  el: HTMLElement
  to: number
  position: string
  duration?: number
  callback?: () => void
}

// 二次缓入缓出函数，用于让滚动过程更平滑
const easeInOutQuad = (t: number, b: number, c: number, d: number) => {
  t /= d / 2
  if (t < 1) {
    return (c / 2) * t * t + b
  }
  t--
  return (-c / 2) * (t * (t - 2) - 1) + b
}
// 写入目标元素的滚动位置属性
const move = (el: HTMLElement, position: string, amount: number) => {
  el[position] = amount
}

// 创建可启动和停止的滚动动画控制器
export function useScrollTo({
  el,
  position = 'scrollLeft',
  to,
  duration = 500,
  callback
}: ScrollToParams) {
  // 记录当前选中项和交互焦点
  const isActiveRef = ref(false)
  const start = el[position]
  const change = to - start
  const increment = 20
  let currentTime = 0

  // 单帧滚动推进，直到达到目标位置或被停止
  function animateScroll() {
    if (!unref(isActiveRef)) {
      return
    }
    currentTime += increment
    const val = easeInOutQuad(currentTime, start, change, duration)
    move(el, position, val)
    if (currentTime < duration && unref(isActiveRef)) {
      requestAnimationFrame(animateScroll)
    } else {
      if (callback) {
        callback()
      }
    }
  }

  // 启动滚动动画
  function run() {
    isActiveRef.value = true
    animateScroll()
  }

  // 停止当前滚动动画
  function stop() {
    isActiveRef.value = false
  }

  return { start: run, stop }
}
