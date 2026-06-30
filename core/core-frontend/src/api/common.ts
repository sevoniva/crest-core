import request from '@/config/axios'

const defaultSettingsFallback = {
  'basic.defaultSort': '1',
  'basic.defaultOpen': '0'
}

// 获取权限路由
export const getRoleRouters = async (): Promise<Array<AppCustomRouteRecordRaw>> => {
  return request.get({ url: '/menu/list' }).then(res => {
    return res?.data
  })
}

// 获取默认排序
export const getDefaultSettings = async (): Promise<IResponse> => {
  return request
    .get({ url: '/sys-parameter/default-settings' })
    .then(res => (res?.data || defaultSettingsFallback) as IResponse)
    .catch(() => defaultSettingsFallback as unknown as IResponse)
}
