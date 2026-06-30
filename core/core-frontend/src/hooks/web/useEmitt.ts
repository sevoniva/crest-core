import mitt from 'mitt'
import { onBeforeUnmount } from 'vue'

interface Option {
  name: string // 事件名称
  callback: Fn // 回调
}

const emitter = mitt()

// 封装可复用的组合式状态和操作
export const useEmitt = (option?: Option) => {
  if (option) {
    emitter.on(option.name, option.callback)

    onBeforeUnmount(() => {
      emitter.off(option.name, option.callback)
    })
  }

  return {
    emitter
  }
}
