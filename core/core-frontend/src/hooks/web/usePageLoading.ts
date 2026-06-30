import { useAppStoreWithOut } from '@/store/modules/app'

const appStore = useAppStoreWithOut()

// 标记异步操作的加载状态
export const usePageLoading = () => {
  const loadStart = () => {
    appStore.setPageLoading(true)
  }

  const loadDone = () => {
    appStore.setPageLoading(false)
  }

  return {
    loadStart,
    loadDone
  }
}
