import router from './router'
import { useUserStoreWithOut } from '@/store/modules/user'
import { useAppStoreWithOut } from '@/store/modules/app'
import type { RouteRecordRaw } from 'vue-router_2'
import { getDefaultSettings } from '@/api/common'
import { useNProgress } from '@/hooks/web/useNProgress'
import { usePermissionStoreWithOut, pathValid, getFirstAuthMenu } from '@/store/modules/permission'
import { usePageLoading } from '@/hooks/web/usePageLoading'
import { getRoleRouters } from '@/api/common'
import { useCache } from '@/hooks/web/useCache'
import { checkPlatform } from '@/utils/utils'
import { interactiveStoreWithOut } from '@/store/modules/interactive'
import { useAppearanceStoreWithOut } from '@/store/modules/appearance'
import { useEmbedded } from '@/store/modules/embedded'
import { useLoading } from '@/hooks/web/useLoading'
import { ElMessageBox } from 'element-plus-secondary'
import { clearCache } from '@/utils/cacheUtil'
import { resolveAuthFailureNavigation } from '@/router/authFailureNavigation.mjs'
import { isReadOnlyRoute, shouldBypassAuthenticatedMenuGuard } from '@/router/routeAccessPolicy.mjs'
// 外观设置仓库用于路由切换前加载主题与字体
const appearanceStore = useAppearanceStoreWithOut()
// 浏览器缓存访问对象
const { wsCache } = useCache()
// 权限路由仓库
const permissionStore = usePermissionStoreWithOut()
// 交互状态仓库
const interactiveStore = interactiveStoreWithOut()
// 用户状态仓库
const userStore = useUserStoreWithOut()
// 应用状态仓库
const appStore = useAppStoreWithOut()

// 顶部进度条控制函数
const { start, done } = useNProgress()
// 页面级加载遮罩
const { open } = useLoading()
// 路由切换加载状态
const { loadStart, loadDone } = usePageLoading()

const whiteList = ['/login', '/sso/callback', '/chart-view', '/admin-login', '/401'] // 不重定向白名单
// 嵌入窗口可直接访问的路径
const embeddedWindowWhiteList = ['/dvCanvas', '/dashboard', '/preview', '/dataset-embedded-form']
// 嵌入式路由白名单
const embeddedRouteWhiteList = ['/dataset-embedded', '/dataset-form', '/dataset-embedded-form']
// 门户首页路径
const portalHome = '/portal'
// 后台首页路径
const backendHome = '/workbranch/index'

// 判断是否为门户路由
const isPortalRoute = (path: string) => path === portalHome || path.startsWith(`${portalHome}/`)
// 判断是否为账号中心路由
const isAccountRoute = (path: string) => path.startsWith('/modify-pwd')
// 判断是否为需要后台权限的路由
const isBackendRoute = (path: string, name?: string | symbol) =>
  !isPortalRoute(path) &&
  !isReadOnlyRoute(path, name) &&
  !isAccountRoute(path) &&
  !whiteList.includes(path) &&
  !embeddedRouteWhiteList.includes(path)

// 解析 redirect 参数中的路径和查询参数
const parseRedirectLocation = (redirectPath: string) => {
  let target = redirectPath || portalHome
  try {
    for (let i = 0; i < 5; i++) {
      const nextTarget = decodeURIComponent(target)
      if (nextTarget === target) {
        break
      }
      target = nextTarget
    }
  } catch {
    // 解码失败时保留原始路径
  }
  const [path, search = ''] = target.split('?')
  const query: Record<string, string> = {}
  new URLSearchParams(search).forEach((value, key) => {
    query[key] = value
  })
  return {
    path,
    query
  }
}

// 全局前置路由守卫，负责登录态、权限路由和嵌入式访问控制
router.beforeEach(async (to, from, next) => {
  if (['/chart-view'].includes(to.path)) {
    open()
  }
  start()
  loadStart()
  const platform = checkPlatform()
  let isDesktop = wsCache.get('app.desktop')
  if (isDesktop === null) {
    await appStore.setAppModel()
    isDesktop = appStore.getDesktop
  }
  await appearanceStore.setAppearance()
  await appearanceStore.setFontList()
  const defaultSort = await getDefaultSettings()
  wsCache.set('TreeSort-backend', defaultSort?.['basic.defaultSort'] ?? '1')
  wsCache.set('open-backend', defaultSort?.['basic.defaultOpen'] ?? '0')
  if (to.path === '/sso/callback') {
    permissionStore.setCurrentPath(to.path)
    next()
    return
  }
  if (wsCache.get('user.token') || isDesktop) {
    if (!userStore.getUid) {
      try {
        await userStore.setUser()
      } catch {
        clearCache()
        userStore.clear()
        const navigation = resolveAuthFailureNavigation(to)
        if (navigation.type === 'allow') {
          permissionStore.setCurrentPath(to.path)
          next()
        } else {
          permissionStore.setCurrentPath('/login')
          next(navigation.location)
        }
        return
      }
    }
    if (to.path === '/login') {
      next({ path: userStore.getBackendAccess ? backendHome : portalHome })
    } else if (to.path === '/admin-login') {
      next({ path: userStore.getBackendAccess ? backendHome : portalHome })
    } else if (isPortalRoute(to.path)) {
      permissionStore.setCurrentPath(to.path)
      next()
    } else if (shouldBypassAuthenticatedMenuGuard(to)) {
      permissionStore.setCurrentPath(to.path)
      next()
    } else if (!userStore.getBackendAccess && isBackendRoute(to.path, to.name)) {
      permissionStore.setCurrentPath(portalHome)
      next({ path: portalHome })
    } else {
      permissionStore.setCurrentPath(to.path)
      if (permissionStore.getIsAddRouters) {
        let str = ''
        if (
          !Object.keys(to.query || {}).length &&
          ((from.query.redirect as string) || '?').split('?')[0] === to.path
        ) {
          str = ((window.location.hash as string) || '?').split('?').reverse()[0]
          if (str.includes('redirect=')) {
            str = ''
          }
        }
        if (str) {
          to.fullPath += '?' + str
          to.query = str.split('&').reduce((pre, itx) => {
            const [key, val] = itx.split('=')
            pre[key] = val
            return pre
          }, {})
        }
        if (!pathValid(to.path) && to.path !== '/404') {
          if (to.path.startsWith('/sys-setting')) {
            await noAdminPermission()
          }
          const firstPath = getFirstAuthMenu()
          next({ path: firstPath || '/404' })
          return
        }
        next()
        return
      }

      let roleRouters = (await getRoleRouters()) || []
      if (isDesktop) {
        roleRouters = roleRouters.filter(item => item.name !== 'system')
      }
      const routers: any[] = roleRouters as AppCustomRouteRecordRaw[]
      routers.forEach(item => (item['top'] = true))
      await permissionStore.generateRoutes(routers as AppCustomRouteRecordRaw[])

      permissionStore.getAddRouters.forEach(route => {
        router.addRoute(route as unknown as RouteRecordRaw) // 动态添加可访问路由表
      })

      const redirectTarget = parseRedirectLocation(
        (from.query.redirect as string) || to.fullPath || to.path
      )
      const nextData =
        to.path === redirectTarget.path
          ? {
              ...to,
              query: Object.keys(to.query).length ? to.query : redirectTarget.query,
              replace: true
            }
          : redirectTarget

      permissionStore.setIsAddRouters(true)
      await interactiveStore.initInteractive(true)

      if (!pathValid(to.path) && to.path !== '/404') {
        if (to.path.startsWith('/sys-setting')) {
          await noAdminPermission()
        }
        const firstPath = getFirstAuthMenu()
        next({ path: firstPath || '/404' })
        return
      }
      next(nextData)
    }
  } else {
    const embeddedStore = useEmbedded()
    if (
      embeddedStore.getToken &&
      appStore.getIsIframe &&
      embeddedRouteWhiteList.includes(to.path)
    ) {
      if (to.path.includes('/dataset-form')) {
        next({ path: '/dataset-embedded-form', query: to.query })
        return
      }
      permissionStore.setCurrentPath(to.path)
      next()
    } else if (
      to.name === 'link' ||
      to.path.startsWith('/link/') ||
      (!platform && embeddedWindowWhiteList.includes(to.path)) ||
      whiteList.includes(to.path)
    ) {
      await appearanceStore.setFontList()
      permissionStore.setCurrentPath(to.path)
      next()
    } else {
      next(`/login?redirect=${to.fullPath || to.path}`) // 否则全部重定向到登录页
    }
  }
})
// 无管理员权限访问系统设置时提示并等待确认
const noAdminPermission = async () => {
  const promise = new Promise<void>((resolve, reject) => {
    ElMessageBox.confirm('当前页面仅对 admin 开放, 即将跳转首页', {
      confirmButtonType: 'primary',
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '',
      autofocus: false,
      showCancelButton: false,
      showClose: false
    })
      .then(() => {
        resolve()
      })
      .catch(() => {
        reject()
      })
  })
  return Promise.race([
    promise,
    new Promise<void>(resolve => {
      setTimeout(() => {
        ElMessageBox.close()
        resolve()
      }, 3000)
    })
  ])
}
router.afterEach(() => {
  done()
  loadDone()
})
