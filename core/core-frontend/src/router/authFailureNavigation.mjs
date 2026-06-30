const loginPaths = new Set(['/login', '/admin-login'])

// 根据当前数据计算界面可用状态
export const isLoginPath = path => {
  return loginPaths.has(path)
}

// 衔接当前组件交互和状态同步
export const resolveAuthFailureNavigation = to => {
  if (isLoginPath(to.path)) {
    return {
      type: 'allow'
    }
  }
  return {
    type: 'redirect',
    location: {
      path: '/login',
      query: {
        redirect: to.fullPath || to.path
      }
    }
  }
}
