import type { App } from 'vue'
import { createPinia } from 'pinia'

const store = createPinia()

// 更新当前配置并同步相关状态
export const setupStore = (app: App<Element>) => {
  app.use(store)
}

export { store }
