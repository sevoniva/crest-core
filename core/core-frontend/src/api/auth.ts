import request from '@/config/axios'

// 查询当前组织用户
export const queryUserApi = data => request.post({ url: '/user/by-current-org', data })
// 查询当前组织用户选项
export const queryUserOptionsApi = () => request.get({ url: '/user/org/option' })
// 查询当前组织角色
export const queryRoleApi = data => request.post({ url: '/role/by-current-org', data })

// 查询业务资源树
export const resourceTreeApi = (flag: string) =>
  request.get({ url: '/auth/business-resources/' + flag })

// 查询菜单资源树
export const menuTreeApi = () => request.get({ url: '/auth/menu-resources' })

// 查询业务资源权限
export const resourcePerApi = data => request.post({ url: '/auth/business-permissions', data })

// 查询菜单权限
export const menuPerApi = data => request.post({ url: '/auth/menu-permissions', data })

// 保存业务资源权限
export const busiPerSaveApi = data => request.put({ url: '/auth/business-permissions', data })
// 保存菜单权限
export const menuPerSaveApi = data => request.put({ url: '/auth/menu-permissions', data })

// 查询业务资源目标权限
export const resourceTargetPerApi = data =>
  request.post({ url: '/auth/business-target-permissions', data })

// 查询菜单目标权限
export const menuTargetPerApi = data => request.post({ url: '/auth/menu-target-permissions', data })

// 保存业务资源目标权限
export const busiTargetPerSaveApi = data =>
  request.put({ url: '/auth/business-target-permissions', data })
// 保存菜单目标权限
export const menuTargetPerSaveApi = data =>
  request.put({ url: '/auth/menu-target-permissions', data })
