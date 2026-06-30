import { useCache } from '@/hooks/web/useCache'
import { refreshApi } from '@/api/login'
import { useUserStoreWithOut } from '@/store/modules/user'
import { useRequestStoreWithOut } from '@/store/modules/request'

// 浏览器缓存访问对象
const { wsCache } = useCache()
// 用户状态仓库，用于写入刷新后的令牌信息
const userStore = useUserStoreWithOut()
// 请求缓存仓库，用于刷新令牌期间暂存待重试请求
const requestStore = useRequestStoreWithOut()
// 刷新令牌接口路径，用于避免递归刷新
const refreshUrl = '/login/refresh'

// 无登录时间记录时，令牌过期前的刷新窗口
const expConstants = 10000

// 有登录时间记录时，距离上次刷新超过该时间即触发刷新
const expTimeConstants = 90000

// 判断当前令牌是否需要刷新
const isExpired = () => {
  const exp = wsCache.get('user.exp')
  if (!exp) {
    return false
  }
  const time = wsCache.get('user.time')
  if (!time) {
    return exp - Date.now() < expConstants
  }
  return Date.now() - time > expTimeConstants
}

// 使用新令牌执行刷新期间缓存的请求
const delayExecute = (token: string) => {
  const cachedRequestList = requestStore.getRequestList
  cachedRequestList.forEach(cb => {
    cb(token)
  })
  requestStore.cleanCacheRequest()
}

// 获取全局刷新状态，避免并发请求重复刷新令牌
const getRefreshStatus = () => {
  return wsCache.get('crest-global-refresh') || false
}
// 设置短期全局刷新状态
const setRefreshStatus = (status: boolean) => {
  wsCache.set('crest-global-refresh', status, { exp: 5 })
}

// 缓存等待令牌刷新的请求回调
const cacheRequest = cb => {
  requestStore.addCacheRequest(cb)
}

// Axios 请求拦截器配置，负责附加令牌并在临近过期时刷新
export const configHandler = config => {
  const desktop = wsCache.get('app.desktop')
  if (desktop) {
    return config
  }
  if (wsCache.get('user.token')) {
    config.headers['X-CREST-TOKEN'] = wsCache.get('user.token')
    const expired = isExpired()
    const requestUrl = String(config.url || '')
    if (expired && !requestUrl.includes(refreshUrl)) {
      if (!getRefreshStatus()) {
        setRefreshStatus(true)
        refreshApi(Date.now())
          .then(res => {
            if (res?.data?.token) {
              userStore.setToken(res.data.token)
              userStore.setExp(res.data.exp)
              userStore.setTime(Date.now())
              config.headers['X-CREST-TOKEN'] = res.data.token
              delayExecute(res.data.token)
            } else {
              delayExecute(null)
            }
          })
          .catch(e => {
            console.error(e)
          })
          .finally(() => {
            setRefreshStatus(false)
          })
      }
      const retry = new Promise(resolve => {
        cacheRequest(token => {
          config.headers['X-CREST-TOKEN'] = token
          resolve(config)
        })
      })
      return retry
    } else {
      return config
    }
  }
  return config
}
