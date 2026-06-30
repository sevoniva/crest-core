import request from '@/config/axios'

// 使用本地账号密码登录
export const loginApi = data => request.post({ url: '/login/local-login', data })

// 查询登录加密所需的公钥
export const queryPublicKey = () => request.get({ url: 'public-key' })

// 查询登录加密所需的对称密钥
export const querySymmetricKey = () => request.get({ url: 'symmetric-key' })

// 查询当前平台模型配置
export const modelApi = () => request.get({ url: 'model' })

// 使用指定来源发起平台登录
export const platformLoginApi = origin => request.post({ url: '/login/platform-login/' + origin })

// 退出当前登录会话
export const logoutApi = () => request.get({ url: '/logout' })

// 刷新登录状态或会话有效期
export const refreshApi = (time?: any) => request.get({ url: '/login/refresh', params: { time } })

// 加载 UI 全局参数配置
export const uiLoadApi = () => request.get({ url: '/sys-parameter/ui' })

// 查询默认登录方式配置
export const loginCategoryApi = () => request.get({ url: '/sys-parameter/default-login' })

// 查询单点登录公开状态
export const ssoStatusApi = () => request.get({ url: '/sso/public/status' })

// 根据 SSO 票据换取登录令牌
export const ssoTokenApi = (ticket: string) => request.get({ url: `/sso/token/${ticket}` })
