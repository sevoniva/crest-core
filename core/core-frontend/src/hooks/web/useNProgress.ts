import { nextTick, unref } from 'vue'
import type { NProgressOptions } from 'nprogress'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useCssVar } from '@vueuse/core'

const primaryColor = useCssVar('--el-color-primary', document.documentElement)

// 封装可复用的组合式状态和操作
export const useNProgress = () => {
  NProgress.configure({ showSpinner: false } as NProgressOptions)

  // 计算颜色配置并返回样式结果
  const initColor = async () => {
    await nextTick()
    const bar = document.getElementById('nprogress')?.getElementsByClassName('bar')[0] as ElRef
    if (bar) {
      bar.style.background = unref(primaryColor.value)
    }
  }

  initColor()

  // 封装可复用的组合式状态和操作
  const start = () => {
    NProgress.start()
  }

  // 封装可复用的组合式状态和操作
  const done = () => {
    NProgress.done()
  }

  return {
    start,
    done
  }
}
