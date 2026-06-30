import { ElLoading } from 'element-plus-secondary'
let loadingInstance = null

// 标记异步操作的加载状态
export const useLoading = (customClass = '', text = '') => {
  // 打开对应弹窗或操作入口
  const open = () => {
    loadingInstance = ElLoading.service({
      fullscreen: true,
      customClass,
      text
    })
  }

  // 关闭对应弹窗并清理临时状态
  const close = () => {
    loadingInstance?.close()
  }

  return {
    open,
    close
  }
}
