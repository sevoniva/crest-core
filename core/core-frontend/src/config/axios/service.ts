import axios, {
  AxiosInstance,
  AxiosRequestHeaders,
  InternalAxiosRequestConfig,
  AxiosResponse,
  AxiosError,
  AxiosRequestConfig,
  AxiosHeaders
} from 'axios'
import { tryShowLoading, tryHideLoading } from '@/utils/loading'
import qs from 'qs'
import { usePermissionStoreWithOut } from '@/store/modules/permission'
import { useEmbedded } from '@/store/modules/embedded'
import { useLinkStoreWithOut } from '@/store/modules/link'
import { config } from './config'
import { configHandler } from './refresh'
import { isMobile, getLocale } from '@/utils/utils'
import { useRequestStoreWithOut } from '@/store/modules/request'
import { clearCache } from '@/utils/cacheUtil'
import { securityConfig } from './hmac'
import { isLoginPath } from '@/router/authFailureNavigation.mjs'
import { shouldAttachLinkToken } from './linkTokenPolicy.mjs'

type AxiosErrorWidthLoading<T> = T & {
  config: {
    loading?: boolean
  }
}

type InternalAxiosRequestConfigWidthLoading<T> = T & {
  loading?: boolean
}

import { ElMessage, ElMessageBox } from 'element-plus-secondary'
import router from '@/router'

const { result_code } = config
import { useCache } from '@/hooks/web/useCache'
const { wsCache } = useCache()
const requestStore = useRequestStoreWithOut()
const embeddedStore = useEmbedded()
const basePath = import.meta.env.VITE_API_BASEPATH

const embeddedBasePath =
  basePath.startsWith('./') && basePath.length > 2 ? basePath.substring(2) : basePath
export const PATH_URL = embeddedStore.baseUrl ? embeddedStore?.baseUrl + embeddedBasePath : basePath

export interface AxiosInstanceWithLoading extends AxiosInstance {
  <T = any, R = AxiosResponse<T>, D = any>(
    config: AxiosRequestConfig<D> & { loading?: boolean }
  ): Promise<R>
}

const getTimeOut = () => {
  let time = 100
  const url = PATH_URL + '/sys-parameter/request-timeout'
  const xhr = new XMLHttpRequest()
  xhr.onreadystatechange = () => {
    if (xhr.readyState === 4 && xhr.status === 200) {
      if (xhr.responseText) {
        try {
          const response = JSON.parse(xhr.responseText)
          if (response.code === 0) {
            time = response.data
          } else {
            ElMessage.error('系统异常，请联系管理员')
          }
        } catch (e) {
          ElMessage.error('系统异常，请联系管理员')
        }
      } else {
        ElMessage.error('网络异常，请联系网管')
      }
    }
  }

  xhr.open('get', url, false)
  xhr.send()
  return time
}

// 创建axios实例
const time = getTimeOut()
window._crest_get_time_out = time
const service: AxiosInstanceWithLoading = axios.create({
  baseURL: PATH_URL, // api 的 base_url
  timeout: time ? time * 1000 : config.request_timeout // 请求超时时间
})
const mapping = {
  'zh-CN': 'zh-CN',
  en: 'en-US',
  tw: 'zh-TW'
}
const permissionStore = usePermissionStoreWithOut()
const linkStore = useLinkStoreWithOut()
const CancelToken = axios.CancelToken
const cancelMap = {}

// request拦截器
service.interceptors.request.use(
  async (c: InternalAxiosRequestConfigWidthLoading<InternalAxiosRequestConfig>) => {
    let config = configHandler(c)
    if (config instanceof Promise) {
      config = await config
    }
    await securityConfig(config, service.getUri(config))
    if (
      config.method === 'post' &&
      (config.headers as AxiosRequestHeaders)['Content-Type'] ===
        'application/x-www-form-urlencoded'
    ) {
      config.data = qs.stringify(config.data)
    }
    if (embeddedStore.baseUrl) {
      config.baseURL = PATH_URL
    }

    if (isMobile()) {
      ;(config.headers as AxiosRequestHeaders)['X-CREST-MOBILE'] = true
    }
    if (linkStore.getLinkToken && shouldAttachLinkToken(config.url, window.location.hash)) {
      ;(config.headers as AxiosRequestHeaders)['X-CREST-LINK-TOKEN'] = linkStore.getLinkToken
    } else if (embeddedStore.token) {
      ;(config.headers as AxiosRequestHeaders)['X-CREST-EMBEDDED-TOKEN'] = embeddedStore.token
    }
    const locale = getLocale()
    if (locale) {
      const val = mapping[locale] || locale
      ;(config.headers as AxiosRequestHeaders)['Accept-Language'] = val
    }

    if (config.method === 'get' && config.params) {
      let url = config.url as string
      url += '?'
      const keys = Object.keys(config.params)
      for (const key of keys) {
        if (config.params[key] !== void 0 && config.params[key] !== null) {
          url += `${key}=${encodeURIComponent(config.params[key])}&`
        }
      }
      url = url.substring(0, url.length - 1)
      config.params = {}
      config.url = url
    }

    const requestUrl = String(config.url || '')
    if (requestUrl.endsWith('/chart-data/data') || requestUrl.endsWith('chartData/data')) {
      const chartId = (config.data as any)?.id
      config.cancelToken = new CancelToken(function executor(c) {
        cancelMap[requestUrl] = c
        cancelMap[`/chart-data/data/${chartId}`] = c
        cancelMap[`chartData/data/${chartId}`] = c
      })
    } else {
      config.cancelToken = new CancelToken(function executor(c) {
        cancelMap[requestUrl] = c
      })
    }

    config.loading && tryShowLoading(permissionStore.getCurrentPath)
    return config
  },
  (error: AxiosErrorWidthLoading<AxiosError>) => {
    error.config.loading && tryHideLoading(permissionStore.getCurrentPath)
    Promise.reject(error)
  }
)

// response 拦截器
service.interceptors.response.use(
  (
    response: AxiosResponse<any> & { config: InternalAxiosRequestConfig & { loading?: boolean } }
  ) => {
    executeVersionHandler(response)
    /* if (response.headers['x-crest-refresh-token']) {
      wsCache.set('user.token', response.headers['x-crest-refresh-token'])
      wsCache.set('user.exp', new Date().getTime() + 90000)
    } */
    if (response.headers['x-crest-link-token']) {
      linkStore.setLinkToken(response.headers['x-crest-link-token'])
    }
    response.config.loading && tryHideLoading(permissionStore.getCurrentPath)
    const responseUrl = String(response.config.url || '')

    if (response.config.responseType === 'blob') {
      // 文件流响应保持原始结构，交给调用方处理下载。
      return response
    } else if (response.data.code === result_code || response.data.code === 50002) {
      return response.data
    } else if (responseUrl.match(/^\/map|geo\/\d{3}\/\d+\.json$/)) {
      // 地图静态资源不使用统一业务响应结构。
      return response
    } else if (responseUrl.includes('/i18n/custom_')) {
      return response
    } else {
      if (response?.data?.code !== 60003) {
        ElMessage({
          type: 'error',
          message: response.data.msg,
          showClose: true
        })
        if (response.data.code === 80001) {
          clearCache()
          let queryRedirectPath = '/workbranch/index'
          if (router.currentRoute.value.fullPath) {
            queryRedirectPath = router.currentRoute.value.fullPath as string
          }
          router.push(`/login?redirect=${queryRedirectPath}`)
        }
      }

      return Promise.reject(response.data.msg)
    }
  },
  (error: AxiosErrorWidthLoading<AxiosError>) => {
    if (axios.isCancel(error)) {
      error.config?.loading && tryHideLoading(permissionStore.getCurrentPath)
      return Promise.resolve({
        code: 499,
        data: null,
        msg: 'request canceled',
        canceled: true
      })
    }
    const errorMessage = String(error.message || '')
    if (errorMessage.includes('timeout of')) {
      requestStore.resetLoadingMap()
      ElMessage({
        type: 'error',
        message: '请求超时，请稍后再试',
        showClose: true
      })
    }

    if (!error?.response) {
      return Promise.reject(error)
    }

    if (error?.response.status === 413) {
      ElMessage({
        type: 'error',
        message: '文件大小超出限制, 请修改相关配置文件',
        showClose: true
      })
      return
    }
    const header = error.response?.headers as AxiosHeaders
    const responseData = error.response?.data as Record<string, any> | undefined
    if (!header.has('CREST-FORBIDDEN-FLAG') && !header.has('CREST-GATEWAY-FLAG')) {
      ElMessage({
        type: 'error',
        message: responseData?.msg ? responseData.msg : errorMessage,
        showClose: true
      })
    }

    error.config.loading && tryHideLoading(permissionStore.getCurrentPath)
    if (header.has('CREST-GATEWAY-FLAG')) {
      const userToken = wsCache.get('user.token')
      const inPlatformClient = !!wsCache.get('crest-platform-client')
      const currentPath = router.currentRoute.value.path
      clearCache()
      if (isLoginPath(currentPath)) {
        return Promise.reject(error)
      }
      if (!(userToken && inPlatformClient)) {
        const flag = header.get('CREST-GATEWAY-FLAG')
        localStorage.setItem('CREST-GATEWAY-FLAG', flag.toString())
      }
      let queryRedirectPath = '/workbranch/index'
      if (router.currentRoute.value.fullPath) {
        queryRedirectPath = router.currentRoute.value.fullPath as string
      }
      router.push(`/login?redirect=${queryRedirectPath}`)
    }
    if (header.has('CREST-FORBIDDEN-FLAG')) {
      showMsg('当前权限不允许访问，请联系管理员', '-changed-')
    }
    /* if ([400, 401].includes(error?.response.status)) {
      return Promise.reject(error)
    } */
    if (error?.response.status === 400) {
      return Promise.reject(error)
    }

    return Promise.resolve()
  }
)

// 控制弹窗、面板或区域的展示状态
const showMsg = (msg: string, id: string) => {
  if (window['cross-permission-' + id]) {
    return
  }
  window['cross-permission-' + id] = ElMessageBox.confirm(msg, {
    confirmButtonType: 'primary',
    type: 'warning',
    confirmButtonText: '刷新',
    cancelButtonText: '取消',
    autofocus: false,
    showClose: false
  })
    .then(() => {
      window['cross-permission-' + id]
      window.location.reload()
    })
    .catch(() => {
      window['cross-permission-' + id] = null
    })
}

// 衔接当前组件交互和状态同步
const executeVersionHandler = (response: AxiosResponse) => {
  const key = 'x-crest-execute-version'
  const executeVersion = response.headers[key]
  const cacheVal = wsCache.get(key)
  if (!cacheVal) {
    wsCache.set(key, executeVersion)
    return
  }
  if (executeVersion && executeVersion !== cacheVal) {
    wsCache.set(key, executeVersion)
    showMsg('系统有升级，请点击刷新页面', '-sys-upgrade-')
  }
}

// 根据当前数据计算界面可用状态
const cancelRequestBatch = cancelKey => {
  if (cancelKey) {
    if (cancelKey.indexOf('/**') > -1) {
      const cancelKeyPre = cancelKey.split('/**')[0]
      Object.keys(cancelMap).forEach(key => {
        if (key.indexOf(cancelKeyPre) > -1) {
          cancelMap[key]?.(() => {
            console.warn('Operation canceled by the user,url:' + key)
          })
        }
      })
    } else {
      cancelMap[cancelKey]?.(() => {
        console.warn('Operation canceled by the user,url:' + cancelKey)
      })
    }
  }
}
export { service, cancelMap, cancelRequestBatch }
