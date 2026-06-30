import { createRouter, createWebHashHistory } from 'vue-router_2'
import type { RouteRecordRaw } from 'vue-router_2'
import type { App } from 'vue'

export const routes: AppRouteRecordRaw[] = []
const router = createRouter({
  history: createWebHashHistory(),
  routes: routes as RouteRecordRaw[]
})

// 更新当前配置并同步相关状态
export const setupRouter = (app: App<Element>) => {
  app.use(router)
}

export default router
