import request from '@/config/axios'

// 查询当前用户可挂载的组织列表，keyword 用于组织名称检索
export const mountedOrg = (keyword?: string) =>
  request.post({ url: '/org/mounted', data: { keyword } })

// 切换当前登录用户的组织上下文
export const switchOrg = (id: number | string) => request.post({ url: `/user/switch/${id}` })

// 获取当前登录用户的基础信息和权限上下文
export const userInfo = () => request.get({ url: '/user/info' })

// 按关键字查询角色列表
export const searchRoleApi = (keyword: string) =>
  request.post({ url: '/role/list', data: { keyword } })

// 查询可分配给角色的用户候选项
export const userOptionForRoleApi = data => request.post({ url: '/user/role/option', data })

// 分页查询指定角色已选择的用户
export const userSelectedForRoleApi = (page: number, limit: number, data) =>
  request.post({ url: `/user/role/selected/${page}/${limit}`, data })

// 分页查询用户管理列表
export const userPageApi = (page: number, limit: number, data) =>
  request.post({ url: `/user/page/${page}/${limit}`, data })

// 查询指定用户的个人系统变量信息
export const personSysVariableInfoApi = uid =>
  request.get({ url: `/user/person-sys-variable-info/${uid}` })

// 创建用户
export const userCreateApi = data => request.post({ url: '/user', data })

// 编辑用户
export const userEditApi = data => request.put({ url: '/user', data })

// 当前用户编辑个人资料
export const personEditApi = data => request.post({ url: '/user/person-edit', data })

// 查询可分配给用户的角色候选项
export const roleOptionForUserApi = data => request.post({ url: '/role/user/option', data })

// 删除指定用户
export const userDelApi = uid => request.delete({ url: `/user/${uid}` })

// 查询用户详情表单数据
export const queryFormApi = uid => request.get({ url: `/user/detail/${uid}` })

// 查询当前用户个人信息
export const personInfoApi = () => request.get({ url: `/user/person-info` })

// 查询当前用户登录 IP 相关信息
export const ipInfoApi = () => request.get({ url: `/user/ip-info` })

// 创建角色
export const roleCreateApi = data => request.post({ url: '/role', data })

// 编辑角色
export const roleEditApi = data => request.put({ url: '/role', data })

// 查询角色详情
export const roleDetailApi = rid => request.get({ url: `/role/detail/${rid}` })

// 删除角色
export const roleDelApi = rid => request.delete({ url: `/role/${rid}` })

// 查询角色卸载前的影响信息
export const beforeUnmountInfoApi = data => request.post({ url: '/role/unmount-info', data })

// 从角色中批量移除用户
export const unMountUserApi = data => request.post({ url: '/role/users/unmount', data })

// 向角色批量挂载用户
export const mountUserApi = data => request.post({ url: '/role/users', data })

// 按关键字查询外部用户
export const searchExternalUserApi = keyword =>
  request.get({ url: '/role/external-users/search/' + keyword })

// 向角色挂载外部用户
export const mountExternalUserApi = data => request.post({ url: '/role/external-users', data })

// 切换当前用户界面语言
export const switchLangApi = data => request.post({ url: '/user/switch-language', data })

// 下载用户导入模板
export const downExcelTemplateApi = () =>
  request.post({ url: '/user/excel-template', responseType: 'blob' })

// 批量导入用户，使用 multipart 表单上传文件
export const importUserApi = data =>
  request.post({
    url: '/user/batch-import',
    headersType: 'multipart/form-data',
    data
  })

// 下载用户导入错误记录
export const downErrorRecordApi = (key: string) =>
  request.get({ url: `/user/error-record/${key}`, responseType: 'blob' })

// 清理指定导入批次的错误记录
export const clearErrorApi = (key: string) => {
  request.get({ url: `/user/clear-error-record/${key}` })
}

// 批量删除用户
export const batchDelApi = data => request.delete({ url: '/user/batch', data })

// 查询系统默认密码策略或默认密码值
export const defaultPwdApi = () => request.get({ url: '/user/default-password' })

// 重置指定用户密码
export const resetPwdApi = uid => request.post({ url: `/user/reset-password/${uid}` })

// 启用或禁用用户
export const switchEnableApi = data => request.post({ url: '/user/enable', data })
