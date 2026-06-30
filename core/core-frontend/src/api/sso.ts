import request from '@/config/axios'

// 封装接口调用参数并返回请求结果
export const ssoConfigApi = () => request.get({ url: '/sso/config' })

// 校验并保存当前配置
export const saveSsoConfigApi = data => request.post({ url: '/sso/config', data })

// 根据当前数据计算界面可用状态
export const validateSsoConfigApi = data => request.post({ url: '/sso/validate', data })
