import { defineStore } from 'pinia'
import { routes } from '@/router'

import { generateRoutesFn2 } from '@/router/establish'
import { store } from '../index'
import { cloneDeep } from 'lodash-es'

/**
 * 兜底 404 页面，作为动态路由匹配失败时的最终入口
 */
const NotFoundPage = () => import('@/views/404/index.vue')

/**
 * 权限路由模块在 Pinia 中维护的状态结构
 */
export interface PermissionState {
  routers: AppRouteRecordRaw[]
  addRouters: AppRouteRecordRaw[]
  isAddRouters: boolean
  currentPath: string
}

/**
 * 权限路由状态仓库，负责保存动态路由、菜单路由和当前访问路径
 */
export const usePermissionStore = defineStore('permission', {
  state: (): PermissionState => ({
    routers: [],
    addRouters: [],
    isAddRouters: false,
    currentPath: ''
  }),
  getters: {
    getRouters(): AppRouteRecordRaw[] {
      return this.routers
    },
    getRoutersNotHidden(): AppRouteRecordRaw[] {
      return this.routers.filter(ele => !ele.hidden)
    },
    getAddRouters(): AppRouteRecordRaw[] {
      return cloneDeep(this.addRouters)
    },
    getIsAddRouters(): boolean {
      return this.isAddRouters
    },
    getCurrentPath(): boolean {
      return this.currentPath
    }
  },
  actions: {
    clear() {
      this.routers = cloneDeep(routes)
      this.addRouters = []
      this.isAddRouters = false
      this.currentPath = ''
    },
    generateRoutes(routers?: AppCustomRouteRecordRaw[] | string[]): Promise<unknown> {
      return new Promise<void>(resolve => {
        let routerMap: AppRouteRecordRaw[] = []
        routerMap = generateRoutesFn2(routers as AppCustomRouteRecordRaw[]) || []

        this.addRouters = routerMap.concat([
          {
            path: '/:catchAll(.*)',
            component: NotFoundPage,
            meta: {
              hidden: true
            }
          }
        ])
        // 渲染菜单的所有路由
        this.routers = cloneDeep(routes).concat(routerMap)
        resolve()
      })
    },
    setCurrentPath(currentPath: string): void {
      this.currentPath = currentPath
    },
    setIsAddRouters(state: boolean): void {
      this.isAddRouters = state
    }
  }
})

/**
 * 在组件 setup 外部获取权限路由仓库实例
 */
export const usePermissionStoreWithOut = () => {
  return usePermissionStore(store)
}

/**
 * 校验给定路径是否存在于当前权限路由树中
 */
export const pathValid = path => {
  path = String(path || '').split('?')[0]
  if (path?.startsWith('/dataset-form')) {
    path = '/data/dataset'
  }
  const permissionStore = usePermissionStore(store)
  const routers = permissionStore.getRouters
  const temp = path.startsWith('/') ? path.substr(1) : path
  const locations = temp.split('/')
  if (locations.length === 0) {
    return false
  }

  return hasCurrentRouter(locations, routers, 0)
}
/**
 * 按层级递归校验路径片段是否能匹配到路由节点
 */
const hasCurrentRouter = (locations, routers, index) => {
  if (!routers?.length) {
    return false
  }
  const location = locations[index]
  let kids = []
  const isvalid = routers.some(router => {
    kids = router.children
    return router.path === location || '/' + location === router.path
  })

  if (isvalid && index < locations.length - 1) {
    return hasCurrentRouter(locations, kids, index + 1)
  }
  return isvalid
}

/**
 * 获取当前权限下第一个可访问菜单路径
 */
export const getFirstAuthMenu = () => {
  const permissionStore = usePermissionStore(store)
  const routers = permissionStore.getRouters
  const nodePathArray = []
  getPathway(routers, nodePathArray)
  if (nodePathArray.length) {
    nodePathArray.reverse()
    return nodePathArray.join('/')
  }
  return null
}

/**
 * 深度优先查找第一个未隐藏的叶子菜单并记录路径
 */
const getPathway = (tree, nodePathArray) => {
  for (let index = 0; index < tree.length; index++) {
    if (tree[index].children) {
      const endRecursiveLoop = getPathway(tree[index].children, nodePathArray)
      if (endRecursiveLoop) {
        nodePathArray.push(tree[index].path)
        return true
      }
    }
    if (!tree[index].children?.length && !tree[index].hidden) {
      nodePathArray.push(tree[index].path)
      return true
    }
  }
}
