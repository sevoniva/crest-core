import { defineStore } from 'pinia'
import { store } from '@/store/index'

interface ShareState {
  shareDisable: boolean
  sharePeRequire: boolean
}

export const useShareStore = defineStore('shareStore', {
  state: (): ShareState => {
    return {
      shareDisable: false,
      sharePeRequire: false
    }
  },
  getters: {
    getShareDisable(): boolean {
      return this.shareDisable
    },
    getSharePeRequire(): boolean {
      return this.sharePeRequire
    }
  },
  actions: {
    setData(data: ShareState) {
      this.shareDisable = data.shareDisable
      this.sharePeRequire = data.sharePeRequire
    }
  }
})

// 更新状态仓库中的业务数据
export const useShareStoreWithOut = () => {
  return useShareStore(store)
}
