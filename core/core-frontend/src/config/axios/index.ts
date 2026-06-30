import { service } from './service'

import { config } from './config'

const { default_headers } = config

// 衔接当前组件交互和状态同步
const request = (option: any) => {
  const { url, method, params, data, headersType, responseType, loading } = option
  return service({
    url: url,
    method,
    loading,
    params,
    data,
    responseType: responseType,
    headers: {
      'Content-Type': headersType || default_headers
    }
  })
}

export default {
  get: <T = any>(option: any) => {
    return request({ method: 'get', ...option }) as unknown as T
  },
  post: <T = any>(option: any) => {
    return request({ method: 'post', ...option }) as unknown as T
  },
  delete: <T = any>(option: any) => {
    return request({ method: 'delete', ...option }) as unknown as T
  },
  put: <T = any>(option: any) => {
    return request({ method: 'put', ...option }) as unknown as T
  }
}
